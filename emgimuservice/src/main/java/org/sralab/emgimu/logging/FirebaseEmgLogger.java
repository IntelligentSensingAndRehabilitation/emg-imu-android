package org.sralab.emgimu.logging;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

import org.sralab.emgimu.service.EmgImuManager;

import java.security.InvalidParameterException;
import java.util.concurrent.ExecutionException;

import no.nordicsemi.android.log.LogContract;

public class FirebaseEmgLogger {

    private String TAG = FirebaseEmgLogger.class.getSimpleName();

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

    private FirebaseEmgLogEntry log = null;
    private EmgImuManager mManager;
    private String mDeviceMac;

    public FirebaseEmgLogger(EmgImuManager manager) {
        mManager = manager;
        mDeviceMac = manager.getAddress();

        mAuth = FirebaseAuth.getInstance();
        mUser = mAuth.getCurrentUser(); // Log in performed by main service

        if (mUser == null) {
            Log.e(TAG, "Should have a user assigned here");
            throw new InvalidParameterException("No FirebaseUser");
        }

        mDb = FirebaseFirestore.getInstance();
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder().build();
        mDb.setFirestoreSettings(settings);
        if (mDb == null) {
            Log.e(TAG, "Unable to get Firestore DB");
            throw new InvalidParameterException("No Firestore DB");
        }
    }

    private DocumentReference getDocument(String DN) {
        mManager.log(LogContract.Log.Level.DEBUG, "getDocument(" + DN + ") for address " + mDeviceMac);
        return mDb.collection("emgLogs").document(mUser.getUid()).collection(mDeviceMac).document(DN);
    }

    private DocumentReference getDocument() {
        String DN = log.DocumentName();
        return getDocument(DN);
    }

    public void updateDb() {

        if (log == null) {
            Log.e(TAG, "Someone is trying to update a log that isn't valid");
            throw new InvalidParameterException("Trying to update a null log");
        }

        // Need to make a copy of this object because it will be replaced immediately after
        // and this thread may not post for some time
        FirebaseEmgLogEntry mLog = new FirebaseEmgLogEntry(log);
        mLog.setHardwareRevision(mManager.getHardwareRevision());
        mLog.setFirmwareRevision(mManager.getFirmwareRevision());
        mLog.setBatteryVoltage(mManager.getBatteryVoltage());

        String DN = mLog.DocumentName();

        mManager.log(LogContract.Log.Level.INFO, "Writing to Firestore: " + DN);

        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(()-> {
            // Add a new document with a generated ID
            getDocument(DN).set(mLog)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            mManager.log(LogContract.Log.Level.DEBUG, mDeviceMac + " Document " + DN + " successfully saved");
                            Log.d(TAG, mDeviceMac + " Document " + getDocument(DN).getPath() + " successfully saved");
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            mManager.log(LogContract.Log.Level.ERROR, "Error adding document " + getDocument(DN).getPath() + " Error: " + e.toString());
                            Log.d(TAG, "Unable to save log");
                        }
                    });
        });
    }

    public void prepareLog(long timestamp) {
        // this is the first sample, which means we likely need to load the prior
        // log from the database and begin appending to it.

        if (log != null) {
            Log.d(TAG, "Log already prepared (" + log.DocumentName() + ". Doing nothing.");
            mManager.firebaseLogReady(FirebaseEmgLogger.this);
            return;
        }

        String DN = FirebaseEmgLogEntry.FilenameFromTimestamp(timestamp);
        Log.d(TAG, "Fetching log for " + DN + " Sensor: " + mDeviceMac + " PID: " + android.os.Process.myPid() + " TID " + android.os.Process.myTid() + " UID " + android.os.Process.myUid());

        mManager.log(LogContract.Log.Level.INFO, "Preparing Firestore DB: " + getDocument(DN).getPath());

        // Run query on main thread to avoid database lock issues
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(()->{
            Task<DocumentSnapshot> task = getDocument(DN).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            Log.d(TAG, "Loaded previous document: " + getDocument(DN).getPath());
                            log = documentSnapshot.toObject(FirebaseEmgLogEntry.class);
                        } else {
                            Log.d(TAG, "No document found. Creating new one: " + DN);
                            log = new FirebaseEmgLogEntry();
                        }
                        mManager.firebaseLogReady(FirebaseEmgLogger.this);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Unable to load previous log: " + DN);
                        log = new FirebaseEmgLogEntry();
                        mManager.firebaseLogReady(FirebaseEmgLogger.this);
                    });
        });
    }

    public void addSample(long timestamp, double emgPower) throws InvalidParameterException {

        if (log == null) {
            throw new InvalidParameterException("Need to prepare log");
        }

        try {
            log.addSample(timestamp, emgPower);
            // TODO: need a timer or something to expire and ensure logs eventually dumped.
            // Will defer this until sync structure to device is worked out as this will
            // likely be a periodic task and can trigger this.
        } catch (FirebaseEmgLogEntry.LogFull logFull) {
            Log.d(TAG, "Buffer full. Creating a new one. Dumping: " + log.DocumentName());
            // Write to DB before replacing with new log
            updateDb();

            // Create a new log and try again
            log = new FirebaseEmgLogEntry();
            try {
                addSample(timestamp, emgPower);
            } catch (Exception e) {
                Log.e(TAG, "Failed adding sample to log:", e);
            }
        }
    }
}
