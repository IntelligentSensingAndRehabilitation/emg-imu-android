package org.sralab.emgimu.config;

import org.achartengine.model.TimeSeries;

public class Device {

    private final static String TAG = Device.class.getSimpleName();

    private String address;
    private Float battery;

    private TimeSeries series = new TimeSeries("EMG Power");

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Float getBattery() {
        return battery;
    }

    public void setBattery(Float battery) {
        this.battery = battery;
    }

    public TimeSeries getSeries() {
        return series;
    }

    public void addPower(Integer power) {
        final int N = 100;

        if (series.getItemCount() == 0)
            series.add(0, power);
        else
            series.add(series.getMaxX() + 1, power);

        if (series.getItemCount() > N)
            series.remove(0);
    }

}
