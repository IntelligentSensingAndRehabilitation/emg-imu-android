package org.sralab.emgimu.powerhammer;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
import com.google.gson.Gson;
import com.unity3d.player.UnityPlayer;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;

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

    private LinearLayout layout;

    // Setup activity layout
    @Override protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        CrashlyticsCore crashlyticsCore = new CrashlyticsCore.Builder()
                .disabled(BuildConfig.DEBUG)
                .build();
        Fabric.with(this, new Crashlytics.Builder().core(crashlyticsCore).build());

        // Create the service holder
        Log.d(TAG, "Creating service holder");
        mServiceHolder = new EmgImuServiceHolder(this);
        mServiceHolder.onCreate();
        mServiceHolder.setCallbacks(mEmgImuCallbacks);
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
        super.onDestroy();;
        Log.d(TAG, "Destroy service holder");
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
        public void onServiceBinded(EmgImuService.EmgImuBinder binder) {
            Log.d(TAG, "Service bound");
            long startTime = new Date().getTime();
            mGameLogger = new FirebaseGameLogger(binder, getString(R.string.powerhammer_name), startTime);
        }

        @Override
        public void onServiceUnbinded() {
            Log.d(TAG, "Service unbound");
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
