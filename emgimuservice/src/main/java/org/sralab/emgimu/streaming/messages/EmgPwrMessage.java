package org.sralab.emgimu.streaming.messages;


public class EmgPwrMessage {

    public final String MSG = "EmgPwr";
    public String bluetoothMac;
    public long timestamp;
    public long sensor_timestamp;
    public int sensor_counter;
    public int [] data;

    public EmgPwrMessage(String bluetoothMac, long timestamp,
                         long sensor_timestamp, int sensor_counter, int [] data) {
        this.bluetoothMac = bluetoothMac;
        this.timestamp = timestamp;
        this.data = data;
        this.sensor_timestamp = sensor_timestamp;
        this.sensor_counter = sensor_counter;
    }
}
