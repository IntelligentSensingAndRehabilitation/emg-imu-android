package org.sralab.emgimu.unity_bindings;

//import needed packages / classes.
import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.nfc.Tag;
import android.os.Debug;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.sralab.emgimu.service.EmgPwrData;
import org.sralab.emgimu.service.EmgStreamData;
import org.sralab.emgimu.service.IEmgImuPwrDataCallback;
import org.sralab.emgimu.service.IEmgImuServiceBinder;
import org.sralab.emgimu.service.IEmgImuStreamDataCallback;

import java.util.Arrays;
import java.util.Date;

public class Bridge extends Application
{
    public static final String TAG = Bridge.class.getSimpleName();

    /** Unity registers the callback to receive messages from android */
    private PluginCallback callback;

    private final IEmgImuPwrDataCallback.Stub pwrObserver = new IEmgImuPwrDataCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, EmgPwrData data) throws RemoteException {
            callback.sendDeviceList(Arrays.toString(service.getManagedDevices().toArray()));
            // stick the code logic here
            if (callback != null) {
                callback.onSuccess(Integer.toString(data.power[0]));
            }
        }
    };

    private final IEmgImuStreamDataCallback.Stub streamObserver  = new IEmgImuStreamDataCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, EmgStreamData data) {}
    };

    private IEmgImuServiceBinder service;
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @SuppressWarnings("unchecked")
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder binder) {
            service = IEmgImuServiceBinder.Stub.asInterface(binder);
            try {
                //callback.sendDeviceList(service.getManagedDevices().toString());
                Log.d(TAG, "Managed Devices: " + service.getManagedDevices().toString());
                // stream data from all sensors
                service.registerEmgPwrObserver(pwrObserver);
                service.registerEmgStreamObserver(streamObserver);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            service = null;
        }
    };

    // These methods get called from Unity
    long startTime;
    String gameName;
    String gameLog;
    public void logTrial(String roundInfo) {
        // expects to receive something that can be added to a list, which can
        // be serialized to JSON
        if (gameLog == null || gameLog.length() == 0) {
            gameLog = "[" + roundInfo + "]";
        } else {
            gameLog = gameLog.substring(0, gameLog.length() - 1) + ", " + roundInfo + "]";
        }

        try {
            service.storeGameplayRecord(gameName, startTime, gameLog);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void connectService(final Context ctx, final PluginCallback callback) {
        // This method gets called from Unity, which created the connection
        Log.d(TAG, "connectService");
        final Intent service = new Intent();
        service.setComponent(new ComponentName("org.sralab.emgimu", "org.sralab.emgimu.service.EmgImuService"));
        ctx.bindService(service, mServiceConnection, Context.BIND_AUTO_CREATE);
        this.callback = callback;  // avoid dead links

        startTime = new Date().getTime();
        gameName = ctx.getPackageName();
    }

    protected void disconnectService(final Context ctx) {
        Log.d(TAG, "disconnectService");
        ctx.unbindService(mServiceConnection);
        service = null;
        callback = null;
    }
}