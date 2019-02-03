package org.sralab.emgimu.logging;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.Timestamp;

import org.sralab.emgimu.service.EmgImuService;

import java.security.InvalidParameterException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

class GamePlayRecord {
    String name;
    List<String> logReference;
    Timestamp startTime;
    Timestamp stopTime;
    double performance;
    String details;

    public String getName()
    {
        return name;
    }

    public Date getStartTime()
    {
        return startTime.toDate();
    }

    public Date getStopTime()
    {
        if (stopTime == null)
            return getStartTime();

        return stopTime.toDate();
    }

    public long getDuration() {
        if (stopTime == null)
            return 0;

        return stopTime.toDate().getTime() - startTime.toDate().getTime();
    }

    public List<String> getLogReference() {
        if (logReference == null) {
            return new ArrayList<String>();
        }

        return logReference;
    }

    public double getPerformance() {
        return performance;
    }

    public String getDetails() {
        if (details == null) {
            return "{}";
        }

        return details;
    }

}

public class FirebaseGameLogger {

    private String TAG = FirebaseGameLogger.class.getSimpleName();

    private FirebaseUser mUser;
    private FirebaseFirestore mDb;

    private GamePlayRecord record;

    public FirebaseGameLogger(EmgImuService.EmgImuBinder service, String game, long startTime) {

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        mUser = mAuth.getCurrentUser(); // Log in performed by main service

        if (mUser == null) {
            Log.e(TAG, "Should have a user assigned here");
            throw new InvalidParameterException("No FirebaseUser");
        }

        mDb = FirebaseFirestore.getInstance();
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setTimestampsInSnapshotsEnabled(true)
                .build();
        mDb.setFirestoreSettings(settings);
        if (mDb == null) {
            Log.e(TAG, "Unable to get Firestore DB");
            throw new InvalidParameterException("No Firestore DB");
        }

        record = new GamePlayRecord();
        record.startTime = new Timestamp(new Date(startTime));
        record.stopTime = null;
        record.name = game;
        record.performance = 0;
        record.logReference = service.getLoggingReferences();

        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(()-> {
            DocumentReference doc = getDocument();
            doc.get().addOnSuccessListener(documentSnapshot -> {
                if (documentSnapshot.exists()) {
                    Log.d(TAG, "Loaded previous document: " + getDocument().getPath());
                    record = documentSnapshot.toObject(GamePlayRecord.class);
                } else {
                    Log.d(TAG, "Game record did not exist. Creating new one.");
                    save();
                }
            });
        });
    }

    public void finalize(double performance, String details) {
        record.stopTime = Timestamp.now();
        record.performance = performance;
        record.details = details;

        save();
    }

    private DocumentReference getDocument() {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyyMMdd-HHmmss'Z'"); // Quoted "Z" to indicate UTC, no timezone offset
        df.setTimeZone(tz);

        Calendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(record.startTime.toDate().getTime());
        Date D = calendar.getTime();
        String FN = df.format(D);

        DocumentReference doc = mDb.collection("gamePlay").document(mUser.getUid()).collection(record.name).document(FN);
        Log.d(TAG, "File path: " + doc.getPath());

        return doc;
    }


    private void save() {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(()-> {
            DocumentReference doc = getDocument();
            doc.set(record)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Wrote: " + doc.getPath() + " successfully"))
                    .addOnFailureListener(e -> Log.e(TAG, "Unable to save log: " + e.getMessage()));
        });
    }

}
