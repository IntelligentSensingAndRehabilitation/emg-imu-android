package org.sralab.emgimu.mve;

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
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

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

    Map<BluetoothDevice, Device> deviceMap;

    // Use mediator so we can connect observer at construction but
    // provide real data source (via transformation) when connected
    MediatorLiveData<List<Device>> devicesLiveData = new MediatorLiveData<>();
    public LiveData<List<Device>> getDevicesLiveData() { return devicesLiveData; }

    MutableLiveData<Integer> range = new MutableLiveData<>(20000);
    public void setRange(Integer range) { this.range.postValue(range); }
    public LiveData<Integer> getRange() { return range; }

    private IEmgImuServiceBinder service;

    public DeviceViewModel(Application app) {
        super(app);

        Log.d(TAG, "Created");

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
            throw new RuntimeException(e);
        }
        this.app.getApplicationContext().unbindService(serviceConnection);
    }

    private final IEmgImuDataCallback.Stub pwrObserver = new IEmgImuDataCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, long ts, DataParcel data) {
            Device dev = deviceMap.get(device);
            dev.setPower(data.readVal());
        }
    };

    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder binder) {
            service = IEmgImuServiceBinder.Stub.asInterface(binder);

            try {
                service.registerEmgPwrObserver(pwrObserver);
            } catch (RemoteException e) {
                e.printStackTrace();
            }

            // This accesses methods outside the AIDL scope
            EmgImuService.EmgImuBinder fullBinding = (EmgImuService.EmgImuBinder) service;
            LiveData<List<BluetoothDevice>> liveDevices = fullBinding.getLiveDevices();

            deviceMap.clear();

            LiveData<List<Device>> transformed = Transformations.map(liveDevices, input -> {

                ArrayList<Device> output = new ArrayList<>();

                for (final BluetoothDevice d : input) {
                    Log.d(TAG, "Adding device: " + d.getAddress());

                    Device dev = new Device();
                    dev.setAddress(d.getAddress());
                    output.add(dev);
                    deviceMap.put(d, dev);
                }

                return output;
            });

            devicesLiveData.addSource(transformed, value -> devicesLiveData.setValue(value));
        }

        @Override
        public void onServiceDisconnected(final ComponentName name) {
            service = null;
        }
    };

    public void reset() {
        for (final Device d : devicesLiveData.getValue()) { d.reset(); }
    }

}
