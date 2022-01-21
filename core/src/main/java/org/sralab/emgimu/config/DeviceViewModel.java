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

    public boolean getObservePwr() {
        //Log.d(TAG, " emgPwr -- > getObservePwr() got called | getObservePwr() = ");
        return true;
    }

    @Override
    public Device getDev(BluetoothDevice d) {
        EmgImuService.EmgImuBinder fullBinding = (EmgImuService.EmgImuBinder) getService();

        Device dev = new Device();
        dev.setAddress(d.getAddress());
        dev.setBattery(fullBinding.getBattery(d));
        dev.setConnectionState(fullBinding.getConnectionLiveState(d));
/*        if(getObservePwr()) {
            Log.d(TAG, "emgPwr - got getObservePwr()!");
        }*/
        //Log.d(TAG, "emgPwr --> getObservePwr()=" + getObservePwr());
        return dev;
    }

    public int counter = 0;
    @Override
    public void emgPwrUpdated(Device dev, EmgPwrData data) {
        dev.addPower(data.ts, data.power[0]);
        while (counter < 1) {
            //Log.d(TAG, "emgPwrCbs enabled (4) | data.ts=" + data.ts + " | data.power[0]=" + data.power[0] );
            counter++;
        }
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
