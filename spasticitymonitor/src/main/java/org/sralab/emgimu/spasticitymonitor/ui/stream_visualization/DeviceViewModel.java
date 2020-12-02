package org.sralab.emgimu.spasticitymonitor.ui.stream_visualization;

import android.app.Application;
import android.bluetooth.BluetoothDevice;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.sralab.emgimu.EmgImuViewModel;
import org.sralab.emgimu.service.EmgStreamData;
import org.sralab.emgimu.service.ImuQuatData;

import java.util.Arrays;
import java.util.stream.IntStream;

public class DeviceViewModel extends EmgImuViewModel<Device> {

    private final static String TAG = DeviceViewModel.class.getSimpleName();

    @Override
    public boolean getObserveStream() { return true; }

    @Override
    public boolean getObserveQuat() { return true; }

    // We don't do anything with this data in the visualization but important to record
    // for spasticity monitoring.
    @Override
    public boolean getObserveGyro() { return true; }

    public DeviceViewModel(Application app) {
        super(app);
    }

    private boolean filtering = true;
    public void setFiltering(boolean filtering) {
        this.filtering = filtering;
        for (Device d : getDeviceMap().values())
            d.setFiltering(filtering);
    }

    MutableLiveData<Float> range = new MutableLiveData<>(200.0f);
    public void setRange(float range) {
        this.range.postValue(range);
    }
    public LiveData<Float> getRange() {
        return range;
    }

    @Override
    public void emgStreamUpdated(Device dev, EmgStreamData data) {
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

    @Override
    public void imuQuatUpdated(Device dev, ImuQuatData quat) {
        float [] q = {(float) quat.q0, (float) quat.q1, (float) quat.q2, (float) quat.q3};
        dev.setQuat(q);
    }

    @Override
    public Device getDev(BluetoothDevice d) {
        Device dev = new Device(2);
        dev.setFiltering(filtering);
        return dev;
    }

}
