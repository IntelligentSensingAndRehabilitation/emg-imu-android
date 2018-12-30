package org.sralab.emgimu.powerhammer;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;
import com.unity3d.player.*;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;

import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.EmgImuServiceHolder;

import io.fabric.sdk.android.Fabric;

public class UnityPlayerActivity extends Activity
{
    private static final String TAG = UnityPlayerActivity.class.getSimpleName();

    protected UnityPlayer mUnityPlayer; // don't change the name of this variable; referenced from native code

    private EmgImuServiceHolder mServiceHolder;

    // Setup activity layout
    @Override protected void onCreate(Bundle savedInstanceState)
    {
        CrashlyticsCore crashlyticsCore = new CrashlyticsCore.Builder()
                .disabled(BuildConfig.DEBUG)
                .build();
        Fabric.with(this, new Crashlytics.Builder().core(crashlyticsCore).build());

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        mUnityPlayer = new UnityPlayer(this);
        setContentView(mUnityPlayer);
        mUnityPlayer.requestFocus();

        // Create the service holder
        Log.d(TAG, "Creating service holder");
        mServiceHolder = new EmgImuServiceHolder(this);
        mServiceHolder.onCreate();
        mServiceHolder.setCallbacks(mEmgImuCallbacks);
    }

    @Override protected void onNewIntent(Intent intent)
    {
        // To support deep linking, we need to make sure that the client can get access to
        // the last sent intent. The clients access this through a JNI api that allows them
        // to get the intent set on launch. To update that after launch we have to manually
        // replace the intent with the one caught here.
        setIntent(intent);
    }

    // Quit Unity
    @Override protected void onDestroy ()
    {
        mUnityPlayer.destroy();
        super.onDestroy();
    }

    // Pause Unity
    @Override protected void onPause()
    {
        super.onPause();
        mUnityPlayer.pause();
        mServiceHolder.onPause();
    }

    // Resume Unity
    @Override protected void onResume()
    {
        super.onResume();
        mUnityPlayer.resume();
        mServiceHolder.onResume();
    }

    @Override protected void onStart()
    {
        super.onStart();
        mUnityPlayer.start();
    }

    @Override protected void onStop()
    {
        super.onStop();
        mUnityPlayer.stop();
    }

    // Low Memory Unity
    @Override public void onLowMemory()
    {
        super.onLowMemory();
        mUnityPlayer.lowMemory();
    }

    // Trim Memory Unity
    @Override public void onTrimMemory(int level)
    {
        super.onTrimMemory(level);
        if (level == TRIM_MEMORY_RUNNING_CRITICAL)
        {
            mUnityPlayer.lowMemory();
        }
    }

    // This ensures the layout will be correct.
    @Override public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        mUnityPlayer.configurationChanged(newConfig);
    }

    // Notify Unity of the focus change.
    @Override public void onWindowFocusChanged(boolean hasFocus)
    {
        super.onWindowFocusChanged(hasFocus);
        mUnityPlayer.windowFocusChanged(hasFocus);
    }

    // For some reason the multiple keyevent type is not supported by the ndk.
    // Force event injection by overriding dispatchKeyEvent().
    @Override public boolean dispatchKeyEvent(KeyEvent event)
    {
        if (event.getAction() == KeyEvent.ACTION_MULTIPLE)
            return mUnityPlayer.injectEvent(event);
        return super.dispatchKeyEvent(event);
    }

    // Pass any events not handled by (unfocused) views straight to UnityPlayer
    @Override public boolean onKeyUp(int keyCode, KeyEvent event)     {
        if(keyCode == KeyEvent.KEYCODE_BACK) {
            // TODO: figure out how to properly exit this
            //finish();
            //onDestroy();
            //mUnityPlayer.quit();
            //onBackPressed();
            return true;
        }
        return mUnityPlayer.injectEvent(event);
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event)   { return mUnityPlayer.injectEvent(event); }
    @Override public boolean onTouchEvent(MotionEvent event)          { return mUnityPlayer.injectEvent(event); }
    /*API12*/ public boolean onGenericMotionEvent(MotionEvent event)  { return mUnityPlayer.injectEvent(event); }

    private final EmgImuServiceHolder.Callbacks mEmgImuCallbacks = new EmgImuServiceHolder.Callbacks() {

        @Override
        public void onEmgPwrReceived(BluetoothDevice device, int value) {

        }

        @Override
        public void onEmgClick(BluetoothDevice device) {
            MotionEvent event = MotionEvent.obtain(SystemClock.uptimeMillis(),
                    SystemClock.uptimeMillis(), MotionEvent.ACTION_BUTTON_PRESS,
                    0, 0, 0);
            Log.d(TAG, "Received click, injecting an event: " + event);
            mUnityPlayer.injectEvent(event);
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
