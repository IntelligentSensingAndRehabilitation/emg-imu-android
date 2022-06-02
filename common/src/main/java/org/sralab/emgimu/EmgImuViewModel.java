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
import org.sralab.emgimu.service.IEmgImuBatCallback;
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
    public boolean getObserveBat() { return false; }

    Map<BluetoothDevice, T> deviceMap;
    MutableLiveData<List<T>> devicesLiveData = new MutableLiveData<>();
    public LiveData<List<T>> getDevicesLiveData() { return devicesLiveData; }

    public EmgImuViewModel(Application app) {
        super(app);
        this.app = app;

        deviceMap = new HashMap<>();
        devicesLiveData.setValue(new ArrayList<>());
    }

    public void onResume() {
        final Intent service = new Intent();
        service.setComponent(new ComponentName("org.sralab.emgimu", "org.sralab.emgimu.service.EmgImuService"));
        app.getApplicationContext().bindService(service, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public void onPause() {
        try {
            if (service == null) {
                throw new RuntimeException("Service disconnected before unregistering.");
            }
            service.unregisterDevicesObserver(deviceListObserver);
            if (getObservePwr()) service.unregisterEmgPwrObserver(null, pwrObserver);
            if (getObserveStream()) service.unregisterEmgStreamObserver(null, streamObserver);
            if (getObserveAccel()) service.unregisterImuAccelObserver(null, accelObserver);
            if (getObserveGyro()) service.unregisterImuGyroObserver(null, gyroObserver);
            if (getObserveMag()) service.unregisterImuMagObserver(null, magObserver);
            if (getObserveQuat()) service.unregisterImuQuatObserver(null, quatObserver);
            if (getObserveBat()) service.unregisterBatObserver(null, batObserver);
            service = null;
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        this.app.getApplicationContext().unbindService(serviceConnection);
    }

    @Override
    public void onCleared() {
        super.onCleared();
        if (service != null) {
            throw new RuntimeException("EmgImuViewModel life cycle not respected. Please handle onPause event.");
        }
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
        try {
            // Check to see if we have any connected devices
            List<BluetoothDevice> devices = service.getManagedDevices();
            devicesLiveData.setValue(mapDev(devices));
            if (!deviceMap.isEmpty()) {
                if (getObservePwr()) { service.registerEmgPwrObserver(null, pwrObserver); }
                if (getObserveStream()) { service.registerEmgStreamObserver(null, streamObserver); }
                if (getObserveAccel()) service.registerImuAccelObserver(null, accelObserver);
                if (getObserveGyro()) service.registerImuGyroObserver(null, gyroObserver);
                if (getObserveMag()) service.registerImuMagObserver(null, magObserver);
                if (getObserveQuat()) service.registerImuQuatObserver(null, quatObserver);
                if (getObserveBat()) service.registerBatObserver(null, batObserver);
            }
         } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public void onServiceConnected() { }

    public void onServiceDisconnected() { }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(final ComponentName name, final IBinder binder) {
            service = IEmgImuServiceBinder.Stub.asInterface(binder);
            try {
                service.registerDevicesObserver(deviceListObserver);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
            onDeviceListUpdated();
            EmgImuViewModel.this.onServiceConnected();
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            EmgImuViewModel.this.onServiceDisconnected();
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

    public void emgPwrUpdated(T dev, EmgPwrData data) { }
    private final IEmgImuPwrDataCallback.Stub pwrObserver = new IEmgImuPwrDataCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, EmgPwrData data) {
            T dev = deviceMap.get(device);
            if (dev == null) {
                Log.w(TAG, "Dropping pwrObserver as no matching device found. Likely during a disconnection");
                return;
            }
            emgPwrUpdated(dev, data);
        }
    };

    public void emgStreamUpdated(T dev, EmgStreamData data) { }
    private final IEmgImuStreamDataCallback.Stub streamObserver = new IEmgImuStreamDataCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, EmgStreamData data) {
            T dev = deviceMap.get(device);
            if (dev == null) {
                Log.w(TAG, "Dropping streamObserver as no matching device found. Likely during a disconnection");
                return;
            }
            emgStreamUpdated(dev, data);
        }
    };

    public void imuAccelUpdated(T dev, ImuData accel) { }
    private final IEmgImuSenseCallback.Stub accelObserver = new IEmgImuSenseCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, ImuData data) throws RemoteException {
            T dev = deviceMap.get(device);
            if (dev == null) {
                Log.w(TAG, "Dropping accelObserver as no matching device found. Likely during a disconnection");
                return;
            }
            imuAccelUpdated(dev, data);
        }
    };

    public void imuGyroUpdated(T dev, ImuData gyro) { }
    private final IEmgImuSenseCallback.Stub gyroObserver = new IEmgImuSenseCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, ImuData data) throws RemoteException {
            T dev = deviceMap.get(device);
            if (dev == null) {
                Log.w(TAG, "Dropping gyroObserver as no matching device found. Likely during a disconnection");
                return;
            }
            imuGyroUpdated(dev, data);
        }
    };

    public void imuMagUpdated(T dev, ImuData mag) { }
    private final IEmgImuSenseCallback.Stub magObserver = new IEmgImuSenseCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, ImuData data) throws RemoteException {
            T dev = deviceMap.get(device);
            if (dev == null) {
                Log.w(TAG, "Dropping magObserver as no matching device found. Likely during a disconnection");
                return;
            }
            imuMagUpdated(dev, data);
        }
    };

    public void imuQuatUpdated(T dev, ImuQuatData quat) { }
    private final IEmgImuQuatCallback.Stub quatObserver = new IEmgImuQuatCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, ImuQuatData data) throws RemoteException {
            T dev = deviceMap.get(device);
            if (dev == null) {
                Log.w(TAG, "Dropping quatObserver as no matching device found. Likely during a disconnection");
                return;
            }
            imuQuatUpdated(dev, data);
        }
    };

    public void batUpdated(T dev, float bat) { }
    private final IEmgImuBatCallback.Stub batObserver = new IEmgImuBatCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, float bat) throws RemoteException {
            T dev = deviceMap.get(device);
            if (dev == null) {
                Log.w(TAG, "Dropping batObserver as no matching device found. Likely during a disconnection");
                return;
            }
            batUpdated(dev, bat);
        }
    };

    /**
     * Registers emgPwr callback - assumes that it is initially unregistered.
     */
    public void registerEmgPwrObserver() throws RemoteException {
        service.registerEmgPwrObserver(null, pwrObserver);
    }

}