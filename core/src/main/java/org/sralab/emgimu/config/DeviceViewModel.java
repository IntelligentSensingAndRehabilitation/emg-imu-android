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
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import org.sralab.emgimu.service.DataParcel;
import org.sralab.emgimu.service.EmgImuService;
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
    MediatorLiveData<Double> batteryMediator = new MediatorLiveData<>();

    public LiveData<List<Device>> getDevicesLiveData() {
        return devicesLiveData;
    }

    public DeviceViewModel(Application app) {
        super(app);

        Log.d(TAG, "Created");

        devicesLiveData = new MutableLiveData<>();
        deviceMap = new HashMap<>();
        devicesLiveData.setValue(new ArrayList<>(deviceMap.values()));

        final Intent service = new Intent();
        service.setComponent(new ComponentName("org.sralab.emgimu", "org.sralab.emgimu.service.EmgImuService"));
        app.getApplicationContext().bindService(service, serviceConnection, Context.BIND_AUTO_CREATE);
        this.app = app;
    }

    @Override
    protected void onCleared() {
        Log.d(TAG, "onCleared");
        super.onCleared();
        try {
            service.unregisterEmgPwrObserver(pwrObserver);
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to unregister.", e);
        }
        this.app.getApplicationContext().unbindService(serviceConnection);
    }

    private void addDevice(final BluetoothDevice d) {
        Device dev = new Device();
        dev.setAddress(d.getAddress());

        deviceMap.put(d, dev);
        devicesLiveData.postValue(new ArrayList<>(deviceMap.values()));
    }

    private final IEmgImuDataCallback.Stub pwrObserver = new IEmgImuDataCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, long ts, DataParcel data) {
            deviceMap.get(device).addPower(data.readVal());
        }
    };

    private IEmgImuServiceBinder service;
    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder binder) {
            service = IEmgImuServiceBinder.Stub.asInterface(binder);

            EmgImuService.EmgImuBinder fullBinding = (EmgImuService.EmgImuBinder) binder;

            Log.d(TAG, "connected");

            deviceMap.clear();

            final Handler handler = new Handler();
            handler.post(() -> {

                try {
                    service.registerEmgPwrObserver(pwrObserver);

                    List<BluetoothDevice> devs = service.getManagedDevices();
                    Log.d(TAG, "Delayed handler found: " +  devs.toString() + " devices");
                    for (final BluetoothDevice d : devs) {
                        addDevice(d);

                        deviceMap.get(d).setBattery(fullBinding.getBattery(d));
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            });

        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            service = null;
        }
    };

    public void removeDeviceFromService(final Device device) {
        Log.d(TAG, "Removing device: " + device.getAddress());
        for (final BluetoothDevice d : deviceMap.keySet()) {
            if (device.getAddress().matches(d.getAddress())) {
                try {
                    Log.d(TAG, "Found and telling service");
                    service.disconnectDevice(d);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}
