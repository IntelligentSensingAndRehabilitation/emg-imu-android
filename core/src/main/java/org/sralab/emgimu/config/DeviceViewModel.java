package org.sralab.emgimu.config;

import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import org.sralab.emgimu.service.DataParcel;
import org.sralab.emgimu.service.IEmgImuDataCallback;
import org.sralab.emgimu.service.IEmgImuServiceBinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceViewModel extends AndroidViewModel {

    private final static String TAG = DeviceViewModel.class.getSimpleName();

    private Application app;

    MutableLiveData<List<Device>> devicesLiveData;
    Map<BluetoothDevice, Device> deviceMap;


    public MutableLiveData<List<Device>> getDevicesLiveData() {
        return devicesLiveData;
    }

    public DeviceViewModel(Application app) {
        super(app);

        devicesLiveData = new MutableLiveData<>();
        deviceMap = new HashMap<>();
        devicesLiveData.setValue(new ArrayList<>(deviceMap.values()));

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

    private void addDevice(final BluetoothDevice d) {
        Device dev = new Device();
        dev.setAddress(d.getAddress());

        deviceMap.put(d, dev);
    }

    private final IEmgImuDataCallback.Stub pwrObserver = new IEmgImuDataCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, long ts, DataParcel data) {
            deviceMap.get(device).addPower(data.readVal());
            devicesLiveData.postValue(new ArrayList<>(deviceMap.values()));
        }
    };

    private IEmgImuServiceBinder mService;
    private ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder service) {
            mService = IEmgImuServiceBinder.Stub.asInterface(service);
            Log.d(TAG, "connected");

            deviceMap.clear();

            final Handler handler = new Handler();
            handler.postDelayed(() -> {

                try {
                    mService.registerEmgPwrObserver(pwrObserver);

                    List<BluetoothDevice> devs = mService.getManagedDevices();
                    Log.d(TAG, "Delayed handler found: " +  devs.toString() + " devices");
                    for (final BluetoothDevice d : devs) {
                        addDevice(d);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }, 2000);
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            mService = null;
        }
    };

    public void removeDeviceFromService(final Device device) {
        Log.d(TAG, "Removing device: " + device.getAddress());
        for (final BluetoothDevice d : deviceMap.keySet()) {
            if (device.getAddress().matches(d.getAddress())) {
                try {
                    Log.d(TAG, "Found and telling service");
                    mService.disconnectDevice(d);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}
