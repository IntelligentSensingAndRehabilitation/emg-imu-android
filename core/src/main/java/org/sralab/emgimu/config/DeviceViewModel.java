package org.sralab.emgimu.config;

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

import org.sralab.emgimu.EmgImuViewModel;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.EmgPwrData;
import org.sralab.emgimu.service.IEmgImuPwrDataCallback;
import org.sralab.emgimu.service.IEmgImuServiceBinder;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceViewModel extends EmgImuViewModel<Device> {

    private final static String TAG = DeviceViewModel.class.getSimpleName();
    long t0 = new Date().getTime();

    public DeviceViewModel(Application app) {
        super(app);
    }

    public boolean getObservePwr() { return true; }

    @Override
    public Device getDev(BluetoothDevice d) {
        EmgImuService.EmgImuBinder fullBinding = (EmgImuService.EmgImuBinder) getService();

        Device dev = new Device();
        dev.setAddress(d.getAddress());
        dev.setBattery(fullBinding.getBattery(d));
        dev.setConnectionState(fullBinding.getConnectionLiveState(d));

        return dev;
    }

    @Override
    public void emgPwrUpdated(Device dev, EmgPwrData data) {
        dev.addPower(data.ts - t0, data.power[0]);
    }

    public void removeDeviceFromService(final Device device) {
        Log.d(TAG, "Removing device: " + device.getAddress());
        for (final BluetoothDevice d : getDeviceMap().keySet()) {
            if (device.getAddress().matches(d.getAddress())) {
                try {
                    Log.d(TAG, "Found and telling service");
                    getService().disconnectDevice(d);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
