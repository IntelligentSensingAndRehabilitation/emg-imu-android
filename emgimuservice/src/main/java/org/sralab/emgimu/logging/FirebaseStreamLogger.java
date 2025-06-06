package org.sralab.emgimu.logging;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;

import org.sralab.emgimu.service.EmgImuManager;
import org.sralab.emgimu.streaming.messages.EmgPwrMessage;
import org.sralab.emgimu.streaming.messages.EmgRawMessage;
import org.sralab.emgimu.streaming.messages.ImuAccelMessage;
import org.sralab.emgimu.streaming.messages.BatteryMessage;
import org.sralab.emgimu.streaming.messages.ImuAttitudeMessage;
import org.sralab.emgimu.streaming.messages.ImuGyroMessage;
import org.sralab.emgimu.streaming.messages.ImuMagMessage;
import org.sralab.emgimu.streaming.messages.ForceMessage;
import org.sralab.emgimu.streaming.messages.TimestampSyncMessage;
import org.sralab.emgimu.streaming.messages.FileVersionMessage;

import java.util.Observable;
import java.util.Observer;

public class FirebaseStreamLogger extends Observable {

    private String TAG = FirebaseStreamLogger.class.getSimpleName();

    private static final String fileVersion = "2.0.0";

    private EmgImuManager mManager;
    private String mDeviceMac;
    private FirebaseWriter firebaseWriter;

    public FirebaseStreamLogger(EmgImuManager manager, Context context) {
        mManager = manager;
        mDeviceMac = manager.getAddress();

        firebaseWriter = new FirebaseWriter(context, "", "streams", mDeviceMac);

        AddFileVersion(fileVersion);
    }

    public String getReference() {
        return firebaseWriter.getReference();
    }

    public void close() {
        Log.d(TAG, "close");
        firebaseWriter.addObserver((observable, o) -> {
            Log.d(TAG, "notified");
            synchronized (FirebaseStreamLogger.this) {
                FirebaseStreamLogger.this.notify();
            }
            setChanged();
            notifyObservers();
        });
        firebaseWriter.close();
    }

    private void addJson(String json) {
        firebaseWriter.addJson(json);
    }

    public void addStreamSample(long time, long androidElapsedNanos, long sensor_timestamp, long raw_sensor_timestamp, int sensor_counter, int channels, int samples, double[][] data) {
        Gson gson = new Gson();
        EmgRawMessage msg = new EmgRawMessage(mDeviceMac, time, androidElapsedNanos, sensor_timestamp, raw_sensor_timestamp, sensor_counter, channels, samples, data);
        addJson(gson.toJson(msg));
    }

    public void addPwrSample(long time, long androidElapsedNanos, long sensor_timestamp, long raw_sensor_timestamp, int sensor_counter, int[] data) {
        Gson gson = new Gson();
        EmgPwrMessage msg = new EmgPwrMessage(mDeviceMac, time, androidElapsedNanos, sensor_timestamp, raw_sensor_timestamp, sensor_counter, data);
        addJson(gson.toJson(msg));
    }

    public void addAttitudeSample(long time, long androidElapsedNanos, long sensor_timestamp, long raw_sensor_timestamp, int sensor_counter, float[] data) {
        Gson gson = new Gson();
        ImuAttitudeMessage msg = new ImuAttitudeMessage(mDeviceMac, time, androidElapsedNanos, sensor_timestamp, raw_sensor_timestamp, sensor_counter, data);
        addJson(gson.toJson(msg));
    }

    public void addAccelSample(long time, long androidElapsedNanos, long sensor_timestamp, long raw_sensor_timestamp, int sensor_counter, float[][] data) {
        Gson gson = new Gson();
        ImuAccelMessage msg = new ImuAccelMessage(mDeviceMac, time, androidElapsedNanos, sensor_timestamp, raw_sensor_timestamp, sensor_counter, data);
        addJson(gson.toJson(msg));
    }

    public void addBatterySample(long time, double data) {
        // method to add battery message
        Gson gson = new Gson();
        BatteryMessage msg = new BatteryMessage(mDeviceMac, time, data);
        addJson(gson.toJson(msg));
    }

    public void addGyroSample(long time, long androidElapsedNanos, long sensor_timestamp, long raw_sensor_timestamp, int sensor_counter, float[][] data) {
        Gson gson = new Gson();
        ImuGyroMessage msg = new ImuGyroMessage(mDeviceMac, time, androidElapsedNanos, sensor_timestamp, raw_sensor_timestamp, sensor_counter, data);
        addJson(gson.toJson(msg));
    }

    public void addMagSample(long time, long androidElapsedNanos, long sensor_timestamp, long raw_sensor_timestamp, int sensor_counter, float[][] data) {
        Gson gson = new Gson();
        ImuMagMessage msg = new ImuMagMessage(mDeviceMac, time, androidElapsedNanos, sensor_timestamp, raw_sensor_timestamp, sensor_counter, data);
        addJson(gson.toJson(msg));
    }

    public void addForceSample(long time, double[] data) {
        Gson gson = new Gson();
        ForceMessage msg = new ForceMessage(mDeviceMac, time, data);
        addJson(gson.toJson(msg));
        Log.d(TAG, gson.toJson(msg));
    }

    public void addTimestampSync(long android_time, long timestamp_sync_milliseconds) {
        Gson gson = new Gson();
        TimestampSyncMessage msg = new TimestampSyncMessage(mDeviceMac, android_time, timestamp_sync_milliseconds);
        addJson(gson.toJson(msg));
    }

    public void AddFileVersion(String version) {
        Gson gson = new Gson();
        FileVersionMessage msg = new FileVersionMessage(version);
        addJson(gson.toJson(msg));
    }
}