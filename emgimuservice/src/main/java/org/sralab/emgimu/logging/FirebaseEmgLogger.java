package org.sralab.emgimu.logging;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.security.InvalidParameterException;

public class FirebaseEmgLogger {

    private String TAG = FirebaseEmgLogger.class.getName();

    private FirebaseAuth mAuth;

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

    private String mSensorName;

    public interface FirebaseLogProducerCallbacks {
        void firebaseLogReady(FirebaseEmgLogger logger);
    }

    private FirebaseLogProducerCallbacks mProducer;

    public FirebaseEmgLogger(String sensorName, FirebaseLogProducerCallbacks producer) {
        mSensorName = sensorName;
        mProducer = producer;

        mAuth = FirebaseAuth.getInstance();

        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        Log.d(TAG, "User ID: " + currentUser);
        if (currentUser == null) {
            mAuth.signInAnonymously()
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            FirebaseUser user = mAuth.getCurrentUser();
                            Log.d(TAG, "signInAnonymously:success. UID:" + user.getUid());
                        } else {
                            Log.e(TAG, "signInAnonymously:failure", task.getException());
                        }
                    }
                });
        } else {
            Log.d(TAG, "Prior logged in user: " + currentUser.getUid());
        }
    }

    public void updateDb() {
        if (log == null) {
            Log.e(TAG, "Someone is trying to update a log that isn't valid");
            return;
        }

        String DN = log.DocumentName();

        mAuth = FirebaseAuth.getInstance();

        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "Should have a user assigned here");
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Log.d(TAG, "Writing entry for User ID: " + currentUser + " Document: " + DN);

        // Add a new document with a generated ID
        db.collection("emgLogs").document(currentUser.getUid()).collection(mSensorName).document(DN).set(log)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "Document successfully added");
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error adding document", e);
                        FirebaseCrash.report(new Exception("Failed to store log"));
                    }
                });
    }

    public void prepareLog(long timestamp) {
        // this is the first sample, which means we likely need to load the prior
        // log from the database and begin appending to it.

        if (log == null) {
            Log.d(TAG, "Log already prepared. Doing nothing.");
        }
        String DN = FirebaseEmgLogEntry.FilenameFromTimestamp(timestamp);

        Log.d(TAG, "Looking for document named: " + DN);

        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "Should have a user assigned here");
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("emgLogs").document(currentUser.getUid()).collection(mSensorName).document(DN).get()
            .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                @Override
                public void onSuccess(DocumentSnapshot documentSnapshot) {
                    if (documentSnapshot.exists()) {
                        Log.d(TAG, "Loaded previous document");
                        log = documentSnapshot.toObject(FirebaseEmgLogEntry.class);
                    } else {
                        Log.d(TAG, "No document found. Creating new one.");
                        log = new FirebaseEmgLogEntry();
                    }
                    mProducer.firebaseLogReady(FirebaseEmgLogger.this);

                }
            })
            .addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.d(TAG, "Unable to load previous log");
                    log = new FirebaseEmgLogEntry();
                    mProducer.firebaseLogReady(FirebaseEmgLogger.this);
                }
            });

    }

    public void addSample(long timestamp, double emgPower) {

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
                FirebaseCrash.report(e);
                // This should never happen
                e.printStackTrace();
            }
        }
    }
}
