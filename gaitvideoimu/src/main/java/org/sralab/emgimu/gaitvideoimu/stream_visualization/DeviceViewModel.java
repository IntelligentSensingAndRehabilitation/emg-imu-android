package org.sralab.emgimu.gaitvideoimu.stream_visualization;

import android.app.Application;
import android.bluetooth.BluetoothDevice;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.sralab.emgimu.EmgImuViewModel;
import org.sralab.emgimu.service.EmgPwrData;
import org.sralab.emgimu.service.EmgStreamData;
import org.sralab.emgimu.service.IEmgImuServiceBinder;
import org.sralab.emgimu.service.ImuData;
import org.sralab.emgimu.service.ImuQuatData;

import java.util.Arrays;
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

    @Override
    public boolean getObserveStream() { return false; }

    @Override
    public boolean getObservePwr() { return false; }

    @Override
    public boolean getObserveQuat() { return true; }

    @Override
    public boolean getObserveGyro() { return true; }

    @Override
    public boolean getObserveAccel() { return true; }

    public DeviceViewModel(Application app) {
        super(app);
    }

    @Override
    public void imuQuatUpdated(Device dev, ImuQuatData quat) {
        float [] q = {(float) quat.q0, (float) quat.q1, (float) quat.q2, (float) quat.q3};
        dev.setQuat(q);
    }

    @Override
    public void imuGyroUpdated(Device dev, ImuData data) {

        float Fs = 500;

        double [] timestamp = new double[data.samples];
        double [][] samples = new double[3][data.samples];

        for (int i = 0; i < data.samples; i++) {
            timestamp[i] = i * 1000.0 / Fs + data.ts;
            samples[0][i] = data.x[i];
            samples[1][i] = data.y[i];
            samples[2][i] = data.z[i];
        }

        dev.addGyro(timestamp, samples);
    }

    @Override
    public Device getDev(BluetoothDevice d) {
        Device dev = new Device();
        return dev;
    }

}
