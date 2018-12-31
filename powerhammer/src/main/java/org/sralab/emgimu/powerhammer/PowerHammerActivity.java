package org.sralab.emgimu.powerhammer;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.util.Log;

import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.EmgImuServiceHolder;

import io.fabric.sdk.android.Fabric;

public class PowerHammerActivity extends UnityPlayerActivity
{
    private static final String TAG = PowerHammerActivity.class.getSimpleName();

    private EmgImuServiceHolder mServiceHolder;

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

    // Pause Unity
    @Override protected void onPause()
    {
        super.onPause();
        mServiceHolder.onPause();
    }

    // Resume Unity
    @Override protected void onResume()
    {
        super.onResume();
        mServiceHolder.onResume();
    }
    private final EmgImuServiceHolder.Callbacks mEmgImuCallbacks = new EmgImuServiceHolder.Callbacks() {

        @Override
        public void onEmgPwrReceived(BluetoothDevice device, int value) {
            // The standard mUnityPlayer.injectEvent(event) does not provide
            // flexibility to create custom events such as the EMG power
            // but we can call custom methods in Unity that do expose this

            String val = Float.toString(value / 10000.0f);
            Log.d(TAG, "Sending: " + val);
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
        }

        @Override
        public void onServiceUnbinded() {
            Log.d(TAG, "Service unbound");
        }
    };
}
