package org.sralab.emgimu.mve;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.achartengine.model.TimeSeries;

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
    public void setPower(int power)
    {
        // Smooth inputs
        final double LPF_ALPHA = 0.1;
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
