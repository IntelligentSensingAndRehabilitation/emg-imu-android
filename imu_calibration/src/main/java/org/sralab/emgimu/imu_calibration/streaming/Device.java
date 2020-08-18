package org.sralab.emgimu.imu_calibration.streaming;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.achartengine.model.TimeSeries;

import java.util.ArrayList;
import java.util.List;

public class Device {

    private final static String TAG = Device.class.getSimpleName();

    private boolean filtering;
    public void setFiltering(boolean filtering) {
        this.filtering = filtering;
    }

    List<TimeSeries> accel;
    List<TimeSeries> gyro;
    List<TimeSeries> mag;

    private MutableLiveData<List<TimeSeries>> liveAccelSeries = new MutableLiveData<>();
    public LiveData<List<TimeSeries>> getAccel() {
        return liveAccelSeries;
    }

    private MutableLiveData<List<TimeSeries>> liveGyroSeries = new MutableLiveData<>();
    public LiveData<List<TimeSeries>> getGyro() {
        return liveGyroSeries;
    }

    private MutableLiveData<List<TimeSeries>> liveMagSeries = new MutableLiveData<>();
    public LiveData<List<TimeSeries>> getMag() {
        return liveMagSeries;
    }

    private void addValue(TimeSeries series, double ts, double val)
    {
        int N = 200;
        if (series.getItemCount() == 0)
            series.add(0, val);
        else
            series.add(series.getMaxX() + 1, val);

        if (series.getItemCount() > N)
            series.remove(0);
    }

    public void addAccel(double ts, double x, double y, double z)
    {
        addValue(accel.get(0), ts, x);
        addValue(accel.get(1), ts, y);
        addValue(accel.get(2), ts, z);
        update();
    }

    public void addGyro(double ts, double x, double y, double z)
    {
        addValue(gyro.get(0), ts, x);
        addValue(gyro.get(1), ts, y);
        addValue(gyro.get(2), ts, z);
        update();
    }

    public void addMag(double ts, double x, double y, double z)
    {
        addValue(mag.get(0), ts, x);
        addValue(mag.get(1), ts, y);
        addValue(mag.get(2), ts, z);
        update();
    }

    private int N = 0;
    void update()
    {
        N = N + 1;
        if (N > 21) {
            N = 0;
            liveAccelSeries.postValue(accel);
            liveGyroSeries.postValue(gyro);
            liveMagSeries.postValue(mag);
        }
    }

    public Device() {
        accel = new ArrayList<>();
        gyro = new ArrayList<>();
        mag = new ArrayList<>();

        String [] labels = new String[]{"X", "Y", "Z"};

        for (int ch = 0; ch < 3; ch++) {
            accel.add(new TimeSeries(labels[ch]));
            gyro.add(new TimeSeries(labels[ch]));
            mag.add(new TimeSeries(labels[ch]));
        }

    }

}
