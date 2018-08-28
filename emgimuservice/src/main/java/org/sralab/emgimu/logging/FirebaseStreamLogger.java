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

import org.sralab.emgimu.service.EmgImuManager;

import java.security.InvalidParameterException;

import no.nordicsemi.android.log.LogContract;

public class FirebaseStreamLogger {

    private String TAG = FirebaseStreamLogger.class.getSimpleName();

    private FirebaseUser mUser;
    private FirebaseAuth mAuth;
    private FirebaseFirestore mDb;

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

    private FirebaseStreamEntry log;
    private EmgImuManager mManager;
    private String mDeviceMac;

    public FirebaseStreamLogger(EmgImuManager manager) {
        mManager = manager;
        mDeviceMac = manager.getAddress();

        mAuth = FirebaseAuth.getInstance();
        mUser = mAuth.getCurrentUser(); // Log in performed by main service

        if (mUser == null) {
            Log.e(TAG, "Should have a user assigned here");
            throw new InvalidParameterException("No FirebaseUser");
        }

        mDb = FirebaseFirestore.getInstance();
        if (mDb == null) {
            Log.e(TAG, "Unable to get Firestore DB");
            throw new InvalidParameterException("No Firestore DB");
        }

        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setTimestampsInSnapshotsEnabled(true)
                .build();
        mDb.setFirestoreSettings(settings);

        log = new FirebaseStreamEntry();
    }

    private DocumentReference getDocument(String DN) {
        mManager.log(LogContract.Log.Level.DEBUG, "getDocument(" + DN + ") for address " + mDeviceMac);
        return mDb.collection("streams").document(mUser.getUid()).collection(mDeviceMac).document(DN);
    }

    public void addRawSample(long timestamp, double sample) {
        if (log == null) {
            throw new InvalidParameterException("Need to prepare log");
        }

        boolean full = log.addRawSample(timestamp, sample);

        if (full)
            flushLog();
    }

    public void addPwrSample(long timestamp, double sample) {
        if (log == null) {
            throw new InvalidParameterException("Need to prepare log");
        }

        boolean full = log.addPwrSample(timestamp, sample);
        if (full)
            flushLog();
    }

    public void flushLog() {
        write();
        log = new FirebaseStreamEntry();
    }

    public void write() {

        if (log == null) {
            Log.e(TAG, "Someone is trying to update a log that isn't valid");
            throw new InvalidParameterException("Trying to update a null log");
        }

        if (log.getRawSamples().size() == 0 && log.getPwrSamples().size() == 0) {
            Log.d(TAG, "Not writing log as no samples recorded");
            return;
        }

        // Need to make a copy of this object because it will be replaced immediately after
        // and this thread may not post for some time
        FirebaseStreamEntry mLog = new FirebaseStreamEntry(log);
        mLog.setHardwareRevision(mManager.getHardwareRevision());
        mLog.setFirmwareRevision(mManager.getFirmwareRevision());

        String DN = mLog.DocumentName();

        Log.d(TAG, "Writing " + getDocument(DN).getPath()  + " " + log.getRawSamples().size() + " raw samples and " + log.getPwrSamples().size() + " power samples");
        mManager.log(LogContract.Log.Level.INFO, "Writing stream " + getDocument(DN).getPath() + " "  + log.getRawSamples().size() + " raw samples and " + log.getPwrSamples().size() + " power samples");

        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(()-> {
            // Add a new document with a generated ID
            getDocument(DN).set(mLog)
                    .addOnSuccessListener(aVoid -> {
                        mManager.log(LogContract.Log.Level.DEBUG, mDeviceMac + " Document " + DN + " successfully saved");
                        Log.d(TAG, "Successfully wrote " + getDocument(DN).getPath());
                    })
                    .addOnFailureListener(e -> {
                        mManager.log(LogContract.Log.Level.ERROR, "Error adding document " + DN + " Error: " + e.toString());
                        Log.d(TAG, "Failed writing " + DN, e);
                    })
                    .addOnCompleteListener(task -> {
                        mManager.log(LogContract.Log.Level.DEBUG, mDeviceMac + " Document " + DN + " write completed");
                        Log.d(TAG, "Write completed " + getDocument(DN).getPath());
                    });
        });
    }
}