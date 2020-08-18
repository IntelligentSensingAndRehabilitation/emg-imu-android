package org.sralab.emgimu;

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
import org.sralab.emgimu.service.EmgStreamData;
import org.sralab.emgimu.service.IEmgImuDevicesUpdatedCallback;
import org.sralab.emgimu.service.IEmgImuPwrDataCallback;
import org.sralab.emgimu.service.IEmgImuQuatCallback;
import org.sralab.emgimu.service.IEmgImuSenseCallback;
import org.sralab.emgimu.service.IEmgImuServiceBinder;
import org.sralab.emgimu.service.IEmgImuStreamDataCallback;
import org.sralab.emgimu.service.ImuData;
import org.sralab.emgimu.service.ImuQuatData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class EmgImuViewModel <T> extends AndroidViewModel {

    private final static String TAG = EmgImuViewModel.class.getSimpleName();

    Application app;

    IEmgImuServiceBinder service;

    public boolean getObservePwr() { return false; }
    public boolean getObserveStream() { return false; }
    public boolean getObserveAccel() { return false; }
    public boolean getObserveGyro() { return false; }
    public boolean getObserveMag() { return false; }
    public boolean getObserveQuat() { return false; }

    Map<BluetoothDevice, T> deviceMap;
    MutableLiveData<List<T>> devicesLiveData = new MutableLiveData<>();
    public LiveData<List<T>> getDevicesLiveData() { return devicesLiveData; }

    public EmgImuViewModel(Application app) {
        super(app);

        deviceMap = new HashMap<>();
        devicesLiveData.setValue(new ArrayList<>());

        final Intent service = new Intent();
        service.setComponent(new ComponentName("org.sralab.emgimu", "org.sralab.emgimu.service.EmgImuService"));
        app.getApplicationContext().bindService(service, serviceConnection, Context.BIND_AUTO_CREATE);
        this.app = app;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        try {
            service.unregisterDevicesObserver(deviceListObserver);
            if (getObservePwr()) service.unregisterEmgPwrObserver(pwrObserver);
            if (getObserveStream()) service.unregisterEmgStreamObserver(streamObserver);
            if (getObserveAccel()) service.unregisterImuAccelObserver(accelObserver);
            if (getObserveGyro()) service.unregisterImuGyroObserver(gyroObserver);
            if (getObserveMag()) service.unregisterImuMagObserver(magObserver);
            if (getObserveQuat()) service.unregisterImuQuatObserver(quatObserver);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }

        this.app.getApplicationContext().unbindService(serviceConnection);
    }

    public abstract T getDev(BluetoothDevice d);

    List<T> mapDev(List<BluetoothDevice> bluetoothDevices) {
        LinkedHashMap map = new LinkedHashMap<>();
        for (final BluetoothDevice d : bluetoothDevices) {
            map.put(d, getDev(d));
        }
        deviceMap = map;
        return new ArrayList<>(deviceMap.values());
    }

    public void onDeviceListUpdated() {
        Log.d(TAG, "onDeviceListUpdated fired");
        try {
            List<BluetoothDevice> devices = service.getManagedDevices();
            devicesLiveData.setValue(mapDev(devices));
         } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder binder) {
            service = IEmgImuServiceBinder.Stub.asInterface(binder);

            try {
                service.registerDevicesObserver(deviceListObserver);

                if (getObservePwr()) service.registerEmgPwrObserver(pwrObserver);
                if (getObserveStream()) service.registerEmgStreamObserver(streamObserver);
                if (getObserveAccel()) service.registerImuAccelObserver(accelObserver);
                if (getObserveGyro()) service.registerImuGyroObserver(gyroObserver);
                if (getObserveMag()) service.registerImuMagObserver(magObserver);
                if (getObserveQuat()) service.registerImuQuatObserver(quatObserver);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }

            onDeviceListUpdated();
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            service = null;
        }
    };

    public IEmgImuServiceBinder getService() {
        return service;
    }

    public Map<BluetoothDevice, T> getDeviceMap() {
        return deviceMap;
    }

    private IEmgImuDevicesUpdatedCallback.Stub deviceListObserver = new IEmgImuDevicesUpdatedCallback.Stub() {
        public void onDeviceListUpdated() { EmgImuViewModel.this.onDeviceListUpdated(); }
    };

    // Set of callbacks that can easily be used
    public void emgPwrUpdated(T dev, EmgPwrData data) { }
    private final IEmgImuPwrDataCallback.Stub pwrObserver = new IEmgImuPwrDataCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, EmgPwrData data) {
            T dev = deviceMap.get(device);
            emgPwrUpdated(dev, data);
        }
    };

    public void emgStreamUpdated(T dev, EmgStreamData data) { }
    private final IEmgImuStreamDataCallback.Stub streamObserver = new IEmgImuStreamDataCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, EmgStreamData data) {
            T dev = deviceMap.get(device);
            emgStreamUpdated(dev, data);
        }
    };

    public void imuAccelUpdated(T dev, ImuData accel) { }
    private final IEmgImuSenseCallback.Stub accelObserver = new IEmgImuSenseCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, ImuData data) throws RemoteException {
            imuAccelUpdated(deviceMap.get(device), data);
        }
    };

    public void imuGyroUpdated(T dev, ImuData gyro) { }
    private final IEmgImuSenseCallback.Stub gyroObserver = new IEmgImuSenseCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, ImuData data) throws RemoteException {
            imuGyroUpdated(deviceMap.get(device), data);
        }
    };

    public void imuMagUpdated(T dev, ImuData mag) { }
    private final IEmgImuSenseCallback.Stub magObserver = new IEmgImuSenseCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, ImuData data) throws RemoteException {
            imuMagUpdated(deviceMap.get(device), data);
        }
    };

    public void imuQuatUpdated(T dev, ImuQuatData quat) { }
    private final IEmgImuQuatCallback.Stub quatObserver = new IEmgImuQuatCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, ImuQuatData data) throws RemoteException {
            imuQuatUpdated(deviceMap.get(device), data);
        }
    };
}