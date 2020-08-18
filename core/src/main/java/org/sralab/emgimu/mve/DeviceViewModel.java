package org.sralab.emgimu.mve;

import android.app.Application;
import android.bluetooth.BluetoothDevice;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.sralab.emgimu.EmgImuViewModel;
import org.sralab.emgimu.service.EmgPwrData;

public class DeviceViewModel extends EmgImuViewModel<Device> {

    private final static String TAG = DeviceViewModel.class.getSimpleName();

    @Override
    public boolean getObservePwr() { return true; }

    MutableLiveData<Integer> range = new MutableLiveData<>(20000);
    public void setRange(Integer range) { this.range.postValue(range); }
    public LiveData<Integer> getRange() { return range; }

    public DeviceViewModel(Application app) {
        super(app);
    }

    @Override
    public void emgPwrUpdated(Device dev, EmgPwrData data) {
        dev.setPower(data.power[0]);
    }

    @Override
    public Device getDev(BluetoothDevice d) {
        Device dev = new Device();
        dev.setAddress(d.getAddress());
        return dev;
    }

    public void reset() {
        for (final Device d : getDevicesLiveData().getValue()) { d.reset(); }
    }

}
