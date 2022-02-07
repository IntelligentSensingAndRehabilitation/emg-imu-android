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

    private MutableLiveData<Double>[] minimumTwoChannel = new MutableLiveData[] {new MutableLiveData(Double.MAX_VALUE), new MutableLiveData(Double.MAX_VALUE)};
    public LiveData<Double>[] getMinimumTwoChannel() { return minimumTwoChannel; }
    private MutableLiveData<Double>[] maximumTwoChannel = new MutableLiveData[] {new MutableLiveData(Double.MIN_VALUE), new MutableLiveData(Double.MIN_VALUE)};
    public LiveData<Double>[] getMaximumTwoChannel() { return maximumTwoChannel; }
    private MutableLiveData<Double>[] powerTwoChannel = new MutableLiveData[] {new MutableLiveData(0.0), new MutableLiveData(0.0)};
    public LiveData<Double>[] getPowerTwoChannel() { return powerTwoChannel; }

    public void setPower(int[] power)
    {
        final double LPF_ALPHA = 0.025;
        for (int channelIndex=0; channelIndex<power.length; channelIndex++) {
            // Smooth inputs
            double smooth_power = this.powerTwoChannel[channelIndex].getValue() * (1-LPF_ALPHA) + ((double) power[channelIndex]) * LPF_ALPHA;

            this.powerTwoChannel[channelIndex].postValue(smooth_power);
            if (smooth_power > maximumTwoChannel[channelIndex].getValue())
                maximumTwoChannel[channelIndex].postValue(smooth_power);
            if (smooth_power < minimumTwoChannel[channelIndex].getValue())
                minimumTwoChannel[channelIndex].postValue(smooth_power);
        }
    }

    public void reset() {
        for (int channelIndex=0; channelIndex<minimumTwoChannel.length; channelIndex++) {
            minimumTwoChannel[channelIndex].postValue(Double.MAX_VALUE);
            maximumTwoChannel[channelIndex].postValue(Double.MIN_VALUE);
        }
    }
}
