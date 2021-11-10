package org.sralab.emgimu.logging;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.gson.Gson;

import org.sralab.emgimu.service.EmgImuManager;
import org.sralab.emgimu.streaming.messages.EmgPwrMessage;
import org.sralab.emgimu.streaming.messages.EmgRawMessage;
import org.sralab.emgimu.streaming.messages.ImuAccelMessage;
import org.sralab.emgimu.streaming.messages.ImuAttitudeMessage;
import org.sralab.emgimu.streaming.messages.ImuGyroMessage;
import org.sralab.emgimu.streaming.messages.ImuMagMessage;
import org.sralab.emgimu.streaming.messages.ForceMessage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.security.InvalidParameterException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Observable;
import java.util.TimeZone;
import java.util.zip.GZIPOutputStream;

import no.nordicsemi.android.log.LogContract;

public class FirebaseStreamLogger extends Observable {

    private String TAG = FirebaseStreamLogger.class.getSimpleName();

    private FirebaseUser mUser;

    private EmgImuManager mManager;
    private String mDeviceMac;
    private String dateName;
    private StorageReference storageRef;

    private OutputStream localWriter;
    private OutputStream dataStream;
    private boolean firstEntry;
    private Context context;
    private Handler handler;

    public FirebaseStreamLogger(EmgImuManager manager, Context context) {
        mManager = manager;
        mDeviceMac = manager.getAddress();

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        mUser = mAuth.getCurrentUser(); // Log in performed by main service

        if (mUser == null) {
            Log.e(TAG, "Should have a user assigned here");
            throw new InvalidParameterException("No FirebaseUser");
        }

        // File name is UTC
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyyMMdd-HHmmss'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
        df.setTimeZone(tz);
        dateName = df.format(new Date());

        FirebaseStorage storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference().child(getFilename());
        
        // Create stream objects and use buffers to prevent deadlocks
        PipedOutputStream pos = new PipedOutputStream();
        PipedInputStream pis = new PipedInputStream();

        try {
            // Connect streams
            pis.connect(pos);

            // Use in stream compression
            dataStream = new GZIPOutputStream(pos);
            dataStream.write("[".getBytes());
            firstEntry = true;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }


        HandlerThread t = new HandlerThread("Logging") {};
        t.start();
        handler = new Handler(t.getLooper());

        InputStream logStream = pis;

        Log.d(TAG, "Creating upload task for " + getReference());

        UploadTask uploadTask = storageRef.putStream(logStream);
        uploadTask.addOnFailureListener(exception -> {
            Log.e(TAG, "Failed to upload: " + getReference(), exception);
            mManager.log(LogContract.Log.Level.ERROR, "Failed to upload: " + exception.getMessage() + "\\" + exception.getStackTrace().toString());
            synchronized (this) {
                this.notify();
            }
        }).addOnSuccessListener(taskSnapshot -> {
            Log.d(TAG, "Upload of log succeeded " + getReference() + " " + taskSnapshot.toString());
            mManager.log(LogContract.Log.Level.DEBUG, "Upload of log succeeded " + taskSnapshot.toString());
            synchronized (this) {
                this.notify();
            }
            setChanged();
            notifyObservers();
        });

        try {
            File file = new File(context.getExternalFilesDir("stream_logs"), getLocalFilename());
            String fileName = file.getAbsolutePath();
            localWriter = new FileOutputStream(fileName);
            Log.d(TAG, "Opened: " + fileName);

            localWriter = new GZIPOutputStream(localWriter);
            localWriter.write("[".getBytes());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getReference() {
        return storageRef.getPath();
    }

    public void close() {
        Log.d(TAG, "Closing PipedOutputStream");
        handler.post(() -> {
            try {
                Log.d(TAG, "Close occurred");
                dataStream.write("]".getBytes());
                dataStream.close();

                localWriter.write("]".getBytes());
                localWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private String getFilename() {
        return "streams/" + mUser.getUid() + "/" + mDeviceMac + "/" + dateName + ".json.gz";
    }

    private String getLocalFilename() {
        return mDeviceMac.replace(":", "") + "_" + dateName + ".json.gz";
    }

    public class MsgWriteRunnable implements Runnable {

        private String msg;

        public MsgWriteRunnable(String msg) {
            this.msg = msg;
        }

        @Override
        public void run() {
            try {
                dataStream.write(msg.getBytes());
                localWriter.write(msg.getBytes());
            } catch (IOException e) {
                Log.e(TAG, "Error writing to stream.", e);
            }
        }
    }

    synchronized
    private void addJson(String json) {
        if (firstEntry) {
            handler.post(new MsgWriteRunnable(json));
            firstEntry = false;
        } else
            handler.post(new MsgWriteRunnable(",\n" + json));
    }

    public void addStreamSample(long time, long sensor_timestamp, int sensor_counter, int channels, int samples, double [][] data) {
        Gson gson = new Gson();
        EmgRawMessage msg = new EmgRawMessage(mDeviceMac, time, sensor_timestamp, sensor_counter, channels, samples, data);
        addJson(gson.toJson(msg));
    }

    public void addPwrSample(long time, long sensor_timestamp, int sensor_counter, double [] data) {
        Gson gson = new Gson();
        EmgPwrMessage msg = new EmgPwrMessage(mDeviceMac, time, sensor_timestamp, sensor_counter, data);
        addJson(gson.toJson(msg));
    }

    public void addAttitudeSample(long time, long sensor_timestamp, int sensor_counter, float [] data) {
        Gson gson = new Gson();
        ImuAttitudeMessage msg = new ImuAttitudeMessage(mDeviceMac, time, sensor_timestamp, sensor_counter, data);
        addJson(gson.toJson(msg));
    }

    public void addAccelSample(long time, long sensor_timestamp, int sensor_counter, float [][] data) {
        Gson gson = new Gson();
        ImuAccelMessage msg = new ImuAccelMessage(mDeviceMac, time, sensor_timestamp, sensor_counter, data);
        addJson(gson.toJson(msg));
    }

    public void addGyroSample(long time, long sensor_timestamp, int sensor_counter, float [][] data) {
        Gson gson = new Gson();
        ImuGyroMessage msg = new ImuGyroMessage(mDeviceMac, time, sensor_timestamp, sensor_counter, data);
        addJson(gson.toJson(msg));
    }

    public void addMagSample(long time, long sensor_timestamp, int sensor_counter, float [][] data) {
        Gson gson = new Gson();
        ImuMagMessage msg = new ImuMagMessage(mDeviceMac, time, sensor_timestamp, sensor_counter, data);
        addJson(gson.toJson(msg));
    }

    public void addForceSample(long time, double [] data) {
        Gson gson = new Gson();
        ForceMessage msg = new ForceMessage(mDeviceMac, time, data);
        addJson(gson.toJson(msg));
        Log.d(TAG, gson.toJson(msg));
    }
}