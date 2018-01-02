package org.sralab.emgimu.logging;

import android.support.annotation.NonNull;
import android.util.Log;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crash.FirebaseCrash;
import com.google.firebase.firestore.FirebaseFirestore;

public class EmgLogManager {

    private String TAG = EmgLogManager.class.getName();

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

    private EmgLogEntry log = new EmgLogEntry();

    public EmgLogManager() {
    }

    public void updateDb(EmgLogEntry l) {
        String DN = l.DocumentName();

        mAuth = FirebaseAuth.getInstance();

        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "Should have a user assigned here");
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Log.d(TAG, "Writing entry for User ID: " + currentUser + " Document: " + DN);

        // Add a new document with a generated ID
        db.collection("emgLogs").document(currentUser.getUid()).collection("hourly").document(DN).set(l)
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

    public void addSample(long timestamp, double emgPower) {
        try {
            log.addSample(timestamp, emgPower);
            // TODO: need a timer or something to expire and ensure logs eventually dumped.
            // Will defer this until sync structure to device is worked out as this will
            // likely be a periodic task and can trigger this.
        } catch (EmgLogEntry.LogFull logFull) {
            Log.d(TAG, "Buffer full. Creating a new one. Dumping: " + log.DocumentName());
            // Write to DB before replacing with new log
            updateDb(log);

            // Create a new log and try again
            log = new EmgLogEntry();
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
