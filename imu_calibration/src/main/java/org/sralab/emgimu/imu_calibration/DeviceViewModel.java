package org.sralab.emgimu.imu_calibration;

import android.app.Application;
import android.bluetooth.BluetoothDevice;

import org.sralab.emgimu.EmgImuViewModel;
import org.sralab.emgimu.service.EmgImuService;

public class DeviceViewModel extends EmgImuViewModel<Device> {

    private final static String TAG = DeviceViewModel.class.getSimpleName();

    public DeviceViewModel(Application app) {
        super(app);
    }

    @Override
    public Device getDev(BluetoothDevice d) {
        EmgImuService.EmgImuBinder fullBinding = (EmgImuService.EmgImuBinder) getService();
        Device dev = new Device(d, fullBinding);
        dev.setConnectionState(fullBinding.getConnectionLiveState(d));
        return dev;
    }

}
