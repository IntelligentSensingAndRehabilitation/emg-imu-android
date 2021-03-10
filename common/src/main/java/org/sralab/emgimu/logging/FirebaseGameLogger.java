package org.sralab.emgimu.logging;

import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.Timestamp;

import org.sralab.emgimu.service.IEmgImuServiceBinder;

import java.security.InvalidParameterException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

public class FirebaseGameLogger {
    /** Logger can be used in two ways.
     *
     *  1) original approach where it handles the log file and is told to finalize it when done
     *  2) also simply writing a game play record that is passed
     *
     *  Both use a consistent naming format to save in memory.
     */

    private String TAG = FirebaseGameLogger.class.getSimpleName();

    private FirebaseUser mUser;
    private FirebaseFirestore mDb;
    private IEmgImuServiceBinder mService;

    private GamePlayRecord record;

    void configureService(IEmgImuServiceBinder service) {
        mService = service;

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
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
    }

    public FirebaseGameLogger(IEmgImuServiceBinder service) {
        configureService(service);
    }

    public FirebaseGameLogger(IEmgImuServiceBinder service, String game, long startTime) {

        configureService(service);

        record = new GamePlayRecord();
        record.startTime = new Timestamp(new Date(startTime));
        record.stopTime = null;
        record.name = game;
        record.performance = 0;
        try {
            record.logReference = mService.getLoggingReferences();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

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
        try {
            record.logReference = mService.getLoggingReferences();
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        save();
    }

    public void writeRecord(GamePlayRecord record) {
        this.record = record;
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
