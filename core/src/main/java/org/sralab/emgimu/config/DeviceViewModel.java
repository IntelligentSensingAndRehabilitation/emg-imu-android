package org.sralab.emgimu.config;

import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.os.RemoteException;
import android.util.Log;

import org.sralab.emgimu.EmgImuViewModel;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.EmgPwrData;

public class DeviceViewModel extends EmgImuViewModel<Device> {

    private final static String TAG = DeviceViewModel.class.getSimpleName();

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
        dev.addPower(data.ts, data.power[0]);
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
