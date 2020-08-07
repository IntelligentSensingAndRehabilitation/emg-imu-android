package org.sralab.emgimu;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.IEmgImuServiceBinder;

import no.nordicsemi.android.nrftoolbox.profile.multiconnect.BleMulticonnectProfileService;

public abstract class EmgImuBaseActivity extends AppCompatActivity {

    /**
     * Called when activity binds to the service. The parameter is the object returned in {@link Service#onBind(Intent)} method in your service.
     * It is safe to obtain managed devices now.
     */
    protected abstract void onServiceBinded(IEmgImuServiceBinder binder);

    private IEmgImuServiceBinder mService;
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @SuppressWarnings("unchecked")
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            mService = IEmgImuServiceBinder.Stub.asInterface(service);
            onServiceBinded(mService);
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            mService = null;
        }
    };

    @Override
    public void onResume() {
        Log.d("EmgImuBaseActivity", "Starting service");
        // Start the service here because we want it to be sticky for at least
        // a short while when changing between activities to avoid constantly
        // rebinding the BLE device.
        final Intent service = new Intent(this, getServiceClass());
        startService(service);
        bindService(service, mServiceConnection, Context.BIND_AUTO_CREATE);
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        unbindService(mServiceConnection);
        mService = null;

        onServiceUnbinded();
    }

    /**
     * Returns the service interface that may be used to communicate with the sensor. This will return <code>null</code> if the device is disconnected from the
     * sensor.
     *
     * @return the service binder or <code>null</code>
     */
    protected IEmgImuServiceBinder getService() {
        return mService;
    }

    /**
    * Called when activity unbinds from the service. You may no longer use this binder methods.
    */
    protected abstract void onServiceUnbinded();

    protected Class<? extends BleMulticonnectProfileService> getServiceClass() {
        return EmgImuService.class;
    }

}
