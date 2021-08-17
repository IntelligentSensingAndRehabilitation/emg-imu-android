package org.sralab.emgimu.streaming.messages;

public class ImuGyroMessage {

    public final String MSG = "ImuGyro";
    public String bluetoothMac;
    public long timestamp;
    public long sensor_timestamp;
    public int sensor_counter;
    public float [][] data;

    public ImuGyroMessage(String bluetoothMac, long timestamp,
                          long sensor_timestamp, int sensor_counter, float [][] data) {
        this.bluetoothMac = bluetoothMac;
        this.timestamp = timestamp;
        this.data = data;
        this.sensor_timestamp = sensor_timestamp;
        this.sensor_counter = sensor_counter;
    }
}
