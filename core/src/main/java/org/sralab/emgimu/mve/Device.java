package org.sralab.emgimu.mve;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

public class Device {

    private final static String TAG = Device.class.getSimpleName();

    private String address;
    public String getAddress() {
        return address;
    }
    public void setAddress(String address) {
        this.address = address;
    }

    MutableLiveData<Double> minimum = new MutableLiveData<>(Double.MAX_VALUE);
    LiveData<Double> getMinimum() { return minimum; }
    MutableLiveData<Double> maximum = new MutableLiveData<>(Double.MIN_VALUE);
    LiveData<Double> getMaximum() { return maximum; }

    private MutableLiveData<Double>  power = new MutableLiveData<>(0.0);
    public LiveData<Double> getPower() {
        return power;
    }

    private List<MutableLiveData<Double>> powerList = new ArrayList<>();
    public List<MutableLiveData<Double>> getPowerList() { return powerList; }
    private List<MutableLiveData<Double>> minimumList = new ArrayList<>();
    public List<MutableLiveData<Double>> getMinimumList() { return minimumList; }
    private List<MutableLiveData<Double>> maximumList = new ArrayList<>();
    public List<MutableLiveData<Double>> getMaximumList() { return maximumList; }
    private int channelCount;

    public void setPowerList(int[] powerData) {
        // initial conditions
        powerList.clear();
        minimumList.clear();
        maximumList.clear();
        for (int i = 0; i < powerData.length; i++) {
            powerList.add(power);
            minimumList.add(minimum);
            maximumList.add(maximum);
        }
        Log.d(TAG, "Device powerList.size() = " + powerList.size());
        // Smooth inputs
        final double LPF_ALPHA = 0.025;
        channelCount = powerData.length;
        Log.d(TAG, "Device powerData.length = " + powerData.length);
        for (int i = 0; i < powerData.length; i++) {
            double smooth_power = this.powerList.get(i).getValue() * (1-LPF_ALPHA) + ((double) powerData[i]) * LPF_ALPHA;
            this.powerList.get(i).postValue(smooth_power);
            if (smooth_power > maximumList.get(i).getValue())
                maximumList.get(i).postValue(smooth_power);
            if (smooth_power < minimumList.get(i).getValue())
                minimumList.get(i).postValue(smooth_power);
        }
    }

    public void resetList(int channelCount) {
        for (int i = 0; i < channelCount; i++) {
            minimumList.get(i).postValue(Double.MAX_VALUE);
            maximumList.get(i).postValue(Double.MIN_VALUE);
        }
    }

    public void setPower(int power)
    {
        // Smooth inputs
        final double LPF_ALPHA = 0.025;
        double smooth_power = this.power.getValue() * (1-LPF_ALPHA) + ((double) power) * LPF_ALPHA;

        this.power.postValue(smooth_power);
        if (smooth_power > maximum.getValue())
            maximum.postValue(smooth_power);
        if (smooth_power < minimum.getValue())
            minimum.postValue(smooth_power);
    }

    public void reset() {
        minimum.postValue(Double.MAX_VALUE);
        maximum.postValue(Double.MIN_VALUE);
    }
}
