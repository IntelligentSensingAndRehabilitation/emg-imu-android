package org.sralab.emgimu.imu_calibration.streaming;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.sralab.emgimu.visualization.GraphData;

public class Device {

    private final static String TAG = Device.class.getSimpleName();

    private boolean filtering;
    public void setFiltering(boolean filtering) {
        this.filtering = filtering;
    }

    final int SAMPLES = 10000;
    GraphData accel = new GraphData(SAMPLES, 3);
    GraphData gyro = new GraphData(SAMPLES, 3);
    GraphData mag = new GraphData(SAMPLES, 3);

    public LiveData<GraphData.Data> getAccel() { return accel.getData(); }
    public LiveData<GraphData.Data> getGyro() {
        return gyro.getData();
    }
    public LiveData<GraphData.Data> getMag() {
        return mag.getData();
    }

    private MutableLiveData<float[]> liveQuat = new MutableLiveData<>();
    public LiveData<float []> getQuat() { return liveQuat; }
    public void setQuat(float [] q) { liveQuat.setValue(q); }

    public void addAccel(double ts, double x, double y, double z)
    {
        float [] update = new float[]{ (float) x, (float) y, (float) z};
        accel.addSamples((float) ts, update);
    }

    public void addGyro(double ts, double x, double y, double z)
    {
        float [] update = new float[]{ (float) x, (float) y, (float) z};
        gyro.addSamples((float) ts, update);
    }

    public void addMag(double ts, double x, double y, double z)
    {
        float [] update = new float[]{ (float) x, (float) y, (float) z};
        mag.addSamples((float) ts, update);
    }

    public Device() {
        String [] labels = new String[]{"X", "Y", "Z"};
        accel.setScale(1.0f/(9.81f * 2.0f));
        gyro.setScale(1.0f/500.0f);
        mag.setScale(1.0f/1600.0f);
    }

}
