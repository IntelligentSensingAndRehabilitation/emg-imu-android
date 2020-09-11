package org.sralab.emgimu.spasticitymonitor;

import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.sralab.emgimu.service.EmgPwrData;
import org.sralab.emgimu.service.IEmgImuPwrDataCallback;
import org.sralab.emgimu.service.IEmgImuServiceBinder;

public class SpasticityViewModel extends AndroidViewModel {

    private final static String TAG = SpasticityViewModel.class.getSimpleName();

    private Application app;

    //private LiveEmgPwr emgPwr;
    private final MutableLiveData <Integer> emgPwr = new MutableLiveData<>();

    public LiveData<Integer> getEmgPwr() {
        return emgPwr;
    }

    public SpasticityViewModel(Application app) {
        super(app);

        final Intent service = new Intent();
        service.setComponent(new ComponentName("org.sralab.emgimu", "org.sralab.emgimu.service.EmgImuService"));
        app.getApplicationContext().bindService(service, mServiceConnection, Context.BIND_AUTO_CREATE);
        this.app = app;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        try {
            mService.unregisterEmgPwrObserver(pwrObserver);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to unregister.", e);
        }
        this.app.getApplicationContext().unbindService(mServiceConnection);
    }

    private final IEmgImuPwrDataCallback.Stub pwrObserver = new IEmgImuPwrDataCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, EmgPwrData data) {
            emgPwr.postValue(data.power[0]);
        }
    };

    private IEmgImuServiceBinder mService;
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @SuppressWarnings("unchecked")
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            mService = IEmgImuServiceBinder.Stub.asInterface(service);
            Log.d(TAG, "connected");

            try {
                mService.registerEmgPwrObserver(pwrObserver);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            mService = null;
        }
    };

}
