package org.sralab.emgimu.powerhammer;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;

import org.sralab.emgimu.R;
import org.sralab.emgimu.logging.FirebaseGameLogger;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.EmgImuServiceHolder;

import java.util.Date;

import io.fabric.sdk.android.Fabric;

public class DrivingActivity extends UnityPlayerActivity
{
    private static final String TAG = PowerHammerActivity.class.getSimpleName();

    private EmgImuServiceHolder mServiceHolder;

    // Variables for logging
    private FirebaseGameLogger mGameLogger;

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
        mUnityPlayer.UnitySendMessage("SceneLoader", "loadScene", "5");
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
        super.unbindService(conn);
    }

    boolean exiting = false;

    // Pause Unity
    @Override protected void onPause()
    {
        if (mGameLogger != null) {
            Log.d(TAG, "writing log");
            mGameLogger.finalize(0.0, "");
        }

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
        }

        @Override
        public void onEmgClick(BluetoothDevice device) {
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
            mGameLogger = new FirebaseGameLogger(binder, getString(R.string.title_activity_driving), startTime);
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
                mService.enableDecoder(emgDecodedListener);
            }
        }
    };

    EmgImuService.OnEmgDecodedListener emgDecodedListener = decoded -> {
        if (exiting)
            return;

        // Note here we map (0,1) -> (-1,1). Also in screen space 1 is the bottom
        // but for driving we want top to be forward.
        String decoded_s = String.join(",",
                Float.toString(2 * (decoded[0] - 0.5f)),
                Float.toString(-2 * (decoded[1] - 0.5f)));
        mUnityPlayer.UnitySendMessage("EventSystem", "OnJavaEmgDecodedReceived", decoded_s);
    };

}
