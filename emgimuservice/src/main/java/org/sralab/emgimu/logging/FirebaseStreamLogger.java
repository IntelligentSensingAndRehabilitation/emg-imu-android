package org.sralab.emgimu.logging;

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
import org.sralab.emgimu.streaming.messages.ImuGyroMessage;
import org.sralab.emgimu.streaming.messages.ImuAttitudeMessage;
import org.sralab.emgimu.streaming.messages.ImuMagMessage;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.security.InvalidParameterException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import no.nordicsemi.android.log.LogContract;

public class FirebaseStreamLogger {

    private String TAG = FirebaseStreamLogger.class.getSimpleName();

    private FirebaseUser mUser;

    private EmgImuManager mManager;
    private String mDeviceMac;
    private String dateName;
    private StorageReference storageRef;


    private OutputStream dataStream;
    private boolean firstEntry;

    public FirebaseStreamLogger(EmgImuManager manager) {
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


        PipedOutputStream pos = new PipedOutputStream();
        PipedInputStream pis = new PipedInputStream();
        // Create stream objects and use buffers to prevent deadlocks
        dataStream = new BufferedOutputStream(pos, (int) 10e6);
        try {
            // Connect streams
            pis.connect(pos);
            dataStream.write("[".getBytes());
            firstEntry = true;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }


        Thread t = new Thread() {
            public void run() {
                InputStream logStream = pis;

                Log.d(TAG, "Creating upload task for " + getReference());

                UploadTask uploadTask = storageRef.putStream(logStream);
                uploadTask.addOnFailureListener(exception -> {
                    Log.e(TAG, "Failed to upload", exception);
                    mManager.log(LogContract.Log.Level.ERROR, "Failed to upload: " + exception.getMessage() + "\\" + exception.getStackTrace().toString());
                    synchronized (this) {
                        this.notify();
                    }
                }).addOnSuccessListener(taskSnapshot -> {
                    Log.d(TAG, "Upload of log succeeded " + taskSnapshot.toString());
                    mManager.log(LogContract.Log.Level.DEBUG, "Upload of log succeeded " + taskSnapshot.toString());
                    synchronized (this) {
                        this.notify();
                    }
                });

                // Using thread notification as wait to alert when complete
                while (!uploadTask.isComplete()) {
                    synchronized (this) {
                        try {
                            this.wait(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                Log.d(TAG, "uploadTask thread ended");

            }
        };

        t.start();
    }

    public String getReference() {
        return storageRef.getPath();
    }

    public void close() {
        try {
            dataStream.write("]".getBytes());
            dataStream.close();
            Log.d(TAG, "Closing PipedOutputStream");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getFilename() {
        return "streams/" + mUser.getUid() + "/" + mDeviceMac + "/" + dateName + ".json";
    }

    synchronized
    private void addJson(String json) {
        try {
            long t1 = System.nanoTime();
            if (firstEntry) {
                dataStream.write(json.getBytes());
                firstEntry = false;
            } else
                dataStream.write((",\n" + json).getBytes());

            long t2 = System.nanoTime();
            double duration_ms = (t2-t1) / 1e6;
            if (duration_ms > 3)
                Log.d(TAG, "Write took " + duration_ms + " ms");
        } catch (IOException e) {
            Log.e(TAG, "Error writing to stream");
        }
    }

    public void addRawSample(long time, int channels, int samples, double [][] data) {
        Gson gson = new Gson();
        EmgRawMessage msg = new EmgRawMessage(mDeviceMac, time, channels, samples, data);
        addJson(gson.toJson(msg));
    }

    public void addPwrSample(long time, double [] data) {
        Gson gson = new Gson();
        EmgPwrMessage msg = new EmgPwrMessage(mDeviceMac, time, data);
        addJson(gson.toJson(msg));
    }

    public void addAttitudeSample(long time, float [] data) {
        Gson gson = new Gson();
        ImuAttitudeMessage msg = new ImuAttitudeMessage(mDeviceMac, time, data);
        addJson(gson.toJson(msg));
    }

    public void addAccelSample(long time, float [][] data) {
        Gson gson = new Gson();
        ImuAccelMessage msg = new ImuAccelMessage(mDeviceMac, time, data);
        addJson(gson.toJson(msg));
    }

    public void addGyroSample(long time, float [][] data) {
        Gson gson = new Gson();
        ImuGyroMessage msg = new ImuGyroMessage(mDeviceMac, time, data);
        addJson(gson.toJson(msg));
    }

    public void addMagSample(long time, float [][] data) {
        Gson gson = new Gson();
        ImuMagMessage msg = new ImuMagMessage(mDeviceMac, time, data);
        addJson(gson.toJson(msg));
    }
}