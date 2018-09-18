package org.sralab.emgimu.logging;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.gson.Gson;

import org.sralab.emgimu.service.EmgImuManager;
import org.sralab.emgimu.streaming.messages.EmgPwrMessage;
import org.sralab.emgimu.streaming.messages.EmgRawMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.security.InvalidParameterException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import no.nordicsemi.android.log.LogContract;

public class FirebaseStreamLogger {

    private String TAG = FirebaseStreamLogger.class.getSimpleName();

    private FirebaseUser mUser;

    /**
     * Manages the set of EMG Logs that will be synchronized to the database. These are
     * split into hour long document chunks, which can be missing any arbitrary subsets.
     * At the hour boundary a new document is created. A log should not be added to the
     * database until it is complete (i.e. an hour has elapsed) to avoid erroneous values.
     */

    /*
     * A comment on timing:
     * The wearable sensor contains a RTC module and logging is referenced against that.
     * The logs use a circular buffer and the timestamp of the "0" element is maintained
     * internally. If the sensor has been just powered on and has not synchronized to
     * android, then the RTC will be off and stored as a 0. It will require a communication
     * before the log can be downloaded, and then will back-annotate the timestamp for the
     * first element. From then on, data will timestamps will use the same long format
     * as on Android. Specifically, milliseconds since January 1, 1970, 00:00:00 GMT.
     *
     * In the individual log entries, there will be a T0 stored -- the timestamp of the
     * first sample in the buffer (log entries will be named by their nearest hour). And
     * subsequently the timestamps will be seconds since T0 as a double precision.
     */

    private EmgImuManager mManager;
    private String mDeviceMac;
    private String dateName;
    private StorageReference storageRef;


    private PipedOutputStream dataStream;
    private PipedInputStream logStream;
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

        dataStream = new PipedOutputStream();
        try {
            logStream = new PipedInputStream(dataStream);
            dataStream.write("[".getBytes());
            firstEntry = true;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        Log.d(TAG, "Creating upload task for " + getReference());

        UploadTask uploadTask = storageRef.putStream(logStream);
        uploadTask.addOnFailureListener(exception -> {
            Log.e(TAG, "Failed to upload", exception);
            mManager.log(LogContract.Log.Level.ERROR, "Failed to upload: " + exception.getMessage() + "\\" + exception.getStackTrace().toString());
        }).addOnSuccessListener(taskSnapshot -> {
            Log.d(TAG, "Upload of log succeeded " + taskSnapshot.toString());
            mManager.log(LogContract.Log.Level.DEBUG, "Upload of log succeeded " + taskSnapshot.toString());
        });
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
            if (firstEntry) {
                dataStream.write(json.getBytes());
                firstEntry = false;
            } else
                dataStream.write((",\n" + json).getBytes());
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

}