package org.sralab.emgimu.streaming;

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
import androidx.lifecycle.Transformations;

import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.EmgPwrData;
import org.sralab.emgimu.service.EmgStreamData;
import org.sralab.emgimu.service.IEmgImuPwrDataCallback;
import org.sralab.emgimu.service.IEmgImuServiceBinder;
import org.sralab.emgimu.service.IEmgImuStreamDataCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class DeviceViewModel extends AndroidViewModel {

    private final static String TAG = DeviceViewModel.class.getSimpleName();

    private Application app;
    Map<BluetoothDevice, Device> deviceMap;

    public boolean isFiltering() {
        return filtering;
    }

    public void setFiltering(boolean filtering) {
        this.filtering = filtering;
        for (Device d : deviceMap.values())
            d.setFiltering(filtering);
    }

    private boolean filtering;

    // Use mediator so we can connect observer at construction but
    // provide real data source (via transformation) when connected
    MediatorLiveData<List<Device>> devicesLiveData = new MediatorLiveData<>();
    public LiveData<List<Device>> getDevicesLiveData() { return devicesLiveData; }

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
            service.unregisterEmgStreamObserver(pwrObserver);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
        this.app.getApplicationContext().unbindService(serviceConnection);
    }

    private final IEmgImuStreamDataCallback.Stub pwrObserver = new IEmgImuStreamDataCallback.Stub() {
        @Override
        public void handleData(BluetoothDevice device, EmgStreamData data) {
            Device dev = deviceMap.get(device);

            double [][] voltage = IntStream.range(0, data.channels)
                    .mapToObj(i -> Arrays.copyOfRange(data.voltage, i * data.samples, (i + 1) * data.samples))
                    .toArray(double[][]::new);

            double ts = (double) data.ts;
            for (int c = 0; c < data.channels; c++) {
                for (int s = 0; s < data.samples; s++) {
                    dev.addVoltage(c, ts + s * 1000.0 / data.Fs, voltage[c][s]);
                }
            }
        }
    };

    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(final ComponentName name, final IBinder binder) {
            service = IEmgImuServiceBinder.Stub.asInterface(binder);

            try {
                service.registerEmgStreamObserver(pwrObserver);
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

                    Device dev = new Device(2);
                    output.add(dev);
                    dev.setFiltering(filtering);
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

}
