package org.sralab.emgimu.gaitvideoimu.stream_visualization;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.sralab.emgimu.visualization.GraphData;

import java.util.ArrayList;
import java.util.List;

public class Device {

    private final static String TAG = Device.class.getSimpleName();

    private long t0 = 0;

    private MutableLiveData<float[]> liveQuat = new MutableLiveData<>();
    public LiveData<float []> getQuat() { return liveQuat; }
    public void setQuat(float [] q) { liveQuat.postValue(q); }

    private MutableLiveData<Boolean> connected = new MutableLiveData<>();
    public LiveData<Boolean> getConnected() { return connected; };

    private MutableLiveData<Float> battery = new MutableLiveData<>();
    public LiveData<Float> getBattery() { return battery; };
    public void setBattery(float bat) { battery.postValue(bat); }

    GraphData gyro;
    public LiveData<GraphData.Data> getGyro() { return gyro.getData(); }

    public void addGyro(double [] timestamps, double [][] data) {

        if (t0 == 0) {
            t0 = (long) timestamps[0];
        }
        final int samples = data[0].length;
        for (int s = 0; s < samples; s++) {
            timestamps[s] = timestamps[s] - t0;
        }

        gyro.addSamples(timestamps, data);

        connected.postValue(true);
    }

    public Device() {

        final int channels = 3;

        gyro = new GraphData(10000, channels);
        gyro.setScale(1.0f / 500.0f);

    }

}
