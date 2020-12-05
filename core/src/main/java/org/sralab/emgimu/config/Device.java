package org.sralab.emgimu.config;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.sralab.emgimu.visualization.GraphData;

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

    private GraphData power = new GraphData(500, 1);
    public LiveData<GraphData.Data> getPwr() { return power.getData(); }

    public void addPower(long ts, Integer power) {
        this.power.addSample((float) ts, (float) power);
    }

    public Device() {
        power.setScale(1.0f / 5000.0f);
        power.setPositive(true);
    }

}
