package org.sralab.emgimu.config;

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

    private LiveData<Double> battery = new MutableLiveData<>(0.0);
    public LiveData<Double> getBattery() {
        return battery;
    }
    public void setBattery(LiveData<Double> battery) { this.battery = battery; }

    private LiveData<Integer> connectionState = new MutableLiveData<>(new Integer(0));
    public LiveData<Integer> getConnectionState() { return connectionState; }
    public void setConnectionState(LiveData<Integer> connectionState) { this.connectionState = connectionState; }

    private TimeSeries series = new TimeSeries("EMG Power");
    private MutableLiveData<TimeSeries> liveSeries = new MutableLiveData<>();
    public LiveData<TimeSeries> getSeries() {
        return liveSeries;
    }
    public void addPower(long ts, Integer power) {
        final int N = 100;

        if (series.getItemCount() == 0)
            series.add(ts, power);
        else
            series.add(ts, power);

        if (series.getItemCount() > N)
            series.remove(0);

        liveSeries.postValue(series);
    }

    public Device() {
        liveSeries.postValue(series);
    }

}
