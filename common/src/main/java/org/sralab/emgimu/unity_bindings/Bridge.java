package org.sralab.emgimu.unity_bindings;

//import needed packages / classes.
import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import org.sralab.emgimu.service.EmgPwrData;
import org.sralab.emgimu.service.IEmgImuPwrDataCallback;
import org.sralab.emgimu.service.IEmgImuServiceBinder;

public class Bridge extends Application
{
    public static final String TAG = Bridge.class.getSimpleName();

    // our native method, which will be called from Unity3D
    public void PrintString( final Context ctx, final String message )
    {
        //create / show an android toast, with message and short duration.
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private PluginCallback callback;

    public PluginCallback getCallback() {
        return callback;
    }

    public void RegisterCallback( final Context ctx, PluginCallback callback) {
        callback.onSuccess("received");
        this.callback = callback;
    }

    private final IEmgImuPwrDataCallback.Stub pwrObserver = new IEmgImuPwrDataCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, EmgPwrData data) throws RemoteException {
            if (getCallback() != null) {
                getCallback().onSuccess(Integer.toString(data.power[0]));
            }
        }
    };

    private IEmgImuServiceBinder mService;
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @SuppressWarnings("unchecked")
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            mService = IEmgImuServiceBinder.Stub.asInterface(service);
            try {
                mService.registerEmgPwrObserver(pwrObserver);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            mService = null;
        }
    };

    public void onResume(final Context ctx) {
        Log.d(TAG, "onResume");
        final Intent service = new Intent();
        service.setComponent(new ComponentName("org.sralab.emgimu", "org.sralab.emgimu.service.EmgImuService"));
        ctx.bindService(service, mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    protected void onPause(final Context ctx) {
        ctx.unbindService(mServiceConnection);
        mService = null;
    }
}