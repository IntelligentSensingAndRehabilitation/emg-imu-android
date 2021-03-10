package org.sralab.emgimu.unity_bindings;

//import needed packages / classes.
import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.sralab.emgimu.service.EmgPwrData;
import org.sralab.emgimu.service.IEmgImuPwrDataCallback;
import org.sralab.emgimu.service.IEmgImuServiceBinder;

import java.util.Date;

public class Bridge extends Application
{
    public static final String TAG = Bridge.class.getSimpleName();

    /** Unity registers the callback to receive messages from android */
    private PluginCallback callback;

    private final IEmgImuPwrDataCallback.Stub pwrObserver = new IEmgImuPwrDataCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, EmgPwrData data) throws RemoteException {
            if (callback != null) {
                callback.onSuccess(Integer.toString(data.power[0]));
            }
        }
    };

    private IEmgImuServiceBinder service;
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @SuppressWarnings("unchecked")
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder binder) {
            service = IEmgImuServiceBinder.Stub.asInterface(binder);
            try {
                service.registerEmgPwrObserver(pwrObserver);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            service = null;
        }
    };

    long startTime;
    String gameName;
    String gameLog;
    public void logTrial(String roundInfo) {
        // expects to receive something that can be added to a list, which can
        // be serialized to JSON
        if (gameLog == null || gameLog.length() == 0) {
            gameLog = "[" + roundInfo + "]";
        } else {
            gameLog = gameLog.substring(0, gameLog.length() - 2) + ", " + roundInfo + "]";
        }

        try {
            service.storeGameplayRecord(gameName, startTime, gameLog);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void connectService(final Context ctx, final PluginCallback callback) {
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