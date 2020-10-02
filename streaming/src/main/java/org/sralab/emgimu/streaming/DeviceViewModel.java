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
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import org.sralab.emgimu.EmgImuViewModel;
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

public class DeviceViewModel extends EmgImuViewModel<Device> {

    private final static String TAG = DeviceViewModel.class.getSimpleName();

    @Override
    public boolean getObserveStream() { return true; }

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
    public Device getDev(BluetoothDevice d) {
        Device dev = new Device(2);
        dev.setFiltering(filtering);
        return dev;
    }

}
