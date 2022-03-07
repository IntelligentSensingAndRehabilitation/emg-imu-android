package org.sralab.emgimu.streaming;

import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.sralab.emgimu.EmgImuViewModel;
import org.sralab.emgimu.service.EmgStreamData;

import java.util.Arrays;
import java.util.stream.IntStream;

public class DeviceViewModel extends EmgImuViewModel<Device> {

    private final static String TAG = DeviceViewModel.class.getSimpleName();

    @Override
    public boolean getObserveStream() {
        Log.e(TAG, "GETTING TRUE HERE!!!");
        return true; }

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

        double [] timestamp = new double[data.samples];
        for (int i = 0; i < data.samples; i++)
            timestamp[i] = i * 1000.0 / data.Fs + data.ts;

        //Log.d(TAG, "TS: " + data.ts + " " + Arrays.toString(timestamp));

        dev.addVoltage(timestamp, voltage);
    }

    @Override
    public Device getDev(BluetoothDevice d) {
        Device dev = new Device(2);
        dev.setFiltering(filtering);
        return dev;
    }

}
