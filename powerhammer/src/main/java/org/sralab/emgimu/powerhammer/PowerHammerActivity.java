package org.sralab.emgimu.powerhammer;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
import com.google.gson.Gson;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import org.sralab.emgimu.logging.FirebaseGameLogger;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.EmgImuServiceHolder;

import java.util.ArrayList;
import java.util.Date;

import io.fabric.sdk.android.Fabric;

public class PowerHammerActivity extends UnityPlayerActivity
{
    private static final String TAG = PowerHammerActivity.class.getSimpleName();

    private EmgImuServiceHolder mServiceHolder;

    // Difficulty scaling the EMG to (0,1) scale
    private double difficulty = 1e4; // TODO: fetch based on calibration

    // Variables for logging
    private FirebaseGameLogger mGameLogger;
    private ArrayList<Float> roundPwr = new ArrayList<>();

    // Setup activity layout
    @Override protected void onCreate(Bundle savedInstanceState)
    {
        CrashlyticsCore crashlyticsCore = new CrashlyticsCore.Builder()
                .disabled(BuildConfig.DEBUG)
                .build();
        Fabric.with(this, new Crashlytics.Builder().core(crashlyticsCore).build());

        // Create the service holder
        Log.d(TAG, "Creating service holder");
        mServiceHolder = new EmgImuServiceHolder(this);
        mServiceHolder.onCreate();
        mServiceHolder.setCallbacks(mEmgImuCallbacks);

        super.onCreate(savedInstanceState);
        mUnityPlayer.UnitySendMessage("SceneLoader", "loadScene", "1");
    }

    @Override
    public boolean bindService(Intent service, ServiceConnection conn, int flags) {
        if (service.getAction() != null &&
                service.getAction().equals("com.google.android.gms.ads.identifier.service.START")) {
            Log.d(TAG, "Dropping intent to google as unbind is not happening");
            return false;
        }
        boolean retval = super.bindService(service, conn, flags);
        Log.d(TAG, "bindService: " + service + " " + conn + " = " + retval, new RuntimeException(""));
        return retval;
    }

    @Override
    public void unbindService(ServiceConnection conn) {
        Log.d(TAG, "unbindService: " + conn, new RuntimeException(""));
        super.unbindService(conn);
    }

    boolean exiting = false;

    // Pause Unity
    @Override protected void onPause()
    {
        if (exiting)
            safePause();
        else
            super.onPause();
        mServiceHolder.onPause();
    }

    // Resume Unity
    @Override protected void onResume()
    {
        super.onResume();
        mServiceHolder.onResume();
    }

    @Override protected void onDestroy()
    {
        Log.d(TAG, "Destroy service holder");
        super.onDestroy();;
        mServiceHolder.onDestroy();
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "onBackPressed");
        exiting = true;
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            Log.d(TAG, "onKeyDown KEYCODE_BACK");
            onBackPressed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private final EmgImuServiceHolder.Callbacks mEmgImuCallbacks = new EmgImuServiceHolder.Callbacks() {

        private EmgImuService.EmgImuBinder mService = null;

        @Override
        public void onEmgPwrReceived(BluetoothDevice device, int value) {
            // The standard mUnityPlayer.injectEvent(event) does not provide
            // flexibility to create custom events such as the EMG power
            // but we can call custom methods in Unity that do expose this

            String val = Float.toString(value / (float) difficulty);
            // takes in Object, Function Mame, Parameters
            mUnityPlayer.UnitySendMessage("Player", "OnJavaEmgPowerReceived", val);
        }

        @Override
        public void onEmgClick(BluetoothDevice device) {
            //mUnityPlayer.UnitySendMessage("Player", "OnJavaClickReceived", "");
        }

        @Override
        public void onImuAccelReceived(BluetoothDevice device, float[][] accel) {

        }

        @Override
        public void onImuAttitudeReceived(BluetoothDevice device, float[] quaternion) {

        }

        @Override
        public void onServiceBinded(EmgImuService.EmgImuBinder binder) {
            Log.d(TAG, "Service bound");
            long startTime = new Date().getTime();
            mGameLogger = new FirebaseGameLogger(binder, getString(R.string.powerhammer_name), startTime);
            mService = binder;
        }

        @Override
        public void onServiceUnbinded() {
            mGameLogger = null;
            mService = null;
            Log.d(TAG, "Service unbound");
        }

        @Override
        public void onDeviceReady(BluetoothDevice device) {
            if (mService != null) {
                mService.streamPwr(device);
                difficulty = mService.getMaxPwr(device);
            }
       }
    };

    //! Data format logged to Firebase
    private class Details {
        ArrayList<Float> roundPower;
        double difficulty;
    };

    //! Called from Unity when a round ends to store the power
    public void LogRound(String data) {

        Log.i("TAG", "The data was "+data);

        float score = Float.valueOf(data);
        roundPwr.add(score);

        // If exiting before service is bound then do not try
        // and save
        if (mGameLogger == null)
            return;

        Gson gson = new Gson();

        Details d = new Details();
        d.roundPower = roundPwr;
        d.difficulty = difficulty;
        String json = gson.toJson(d);

        double p = 0;
        for(Float pwr : roundPwr)
            p += pwr;
        p /= roundPwr.size();

        Log.d(TAG, "Updating with: " + json);
        mGameLogger.finalize(p, json);
    }

}
