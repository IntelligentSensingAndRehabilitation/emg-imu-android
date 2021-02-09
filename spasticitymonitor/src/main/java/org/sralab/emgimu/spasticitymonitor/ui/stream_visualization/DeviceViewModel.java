package org.sralab.emgimu.spasticitymonitor.ui.stream_visualization;

import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.sralab.emgimu.EmgImuViewModel;
import org.sralab.emgimu.service.EmgPwrData;
import org.sralab.emgimu.service.EmgStreamData;
import org.sralab.emgimu.service.IEmgImuServiceBinder;
import org.sralab.emgimu.service.ImuQuatData;

import java.util.Arrays;
import java.util.Date;
import java.util.stream.IntStream;

public class DeviceViewModel extends EmgImuViewModel<Device> {

    private final static String TAG = DeviceViewModel.class.getSimpleName();

    MutableLiveData<IEmgImuServiceBinder> serviceLiveData = new MutableLiveData<>();
    public MutableLiveData<IEmgImuServiceBinder> getServiceLiveData() { return serviceLiveData; }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        serviceLiveData.postValue(getService());
    }

    @Override
    public void onServiceDisconnected() {
        super.onServiceDisconnected();
        serviceLiveData.postValue(getService());
    }

    private boolean STREAM_RAW_EMG = true;
    @Override
    public boolean getObserveStream() { return STREAM_RAW_EMG; }

    @Override
    public boolean getObservePwr() { return true; }

    @Override
    public boolean getObserveQuat() { return true; }

    // We don't do anything with this data in the visualization but important to record
    // for spasticity monitoring.
    @Override
    public boolean getObserveGyro() { return true; }


    public DeviceViewModel(Application app) {
        super(app);
    }

    MutableLiveData<Float> range = new MutableLiveData<>(200.0f);
    public void setRange(float range) {
        this.range.postValue(range);
    }
    public LiveData<Float> getRange() {
        return range;
    }

    // Called when receiving stream
    @Override
    public void emgStreamUpdated(Device dev, EmgStreamData data) {

        double [][] voltage = IntStream.range(0, data.channels)
                .mapToObj(i -> Arrays.copyOfRange(data.voltage, i * data.samples, (i + 1) * data.samples))
                .toArray(double[][]::new);

        double [] timestamp = new double[data.samples];
        for (int i = 0; i < data.samples; i++)
            timestamp[i] = i * 1000.0 / data.Fs + data.ts;

        dev.addVoltage(timestamp, voltage);
    }

    // Called when receiving power
    @Override
    public void emgPwrUpdated(Device dev, EmgPwrData data) {

        if (!STREAM_RAW_EMG) {
            dev.addPower(data.ts, data.power[0]);
        }
    }

    @Override
    public void imuQuatUpdated(Device dev, ImuQuatData quat) {
        float [] q = {(float) quat.q0, (float) quat.q1, (float) quat.q2, (float) quat.q3};
        dev.setQuat(q);
    }

    @Override
    public Device getDev(BluetoothDevice d) {
        Device dev = new Device(STREAM_RAW_EMG);
        return dev;
    }

}
