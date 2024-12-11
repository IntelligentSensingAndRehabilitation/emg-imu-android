package org.sralab.emgimu.streaming.messages;

public class ImuGyroMessage {

    public final String MSG = "ImuGyro";
    public String bluetoothMac;
    public long timestamp;
    public long android_elapsed_nanos;
    public long sensor_timestamp;
    public long raw_sensor_timestamp;
    public int sensor_counter;
    public float [][] data;

    public ImuGyroMessage(String bluetoothMac, long timestamp, long android_elapsed_nanos,
                          long sensor_timestamp, long raw_sensor_timestamp,
                          int sensor_counter, float [][] data) {
        this.bluetoothMac = bluetoothMac;
        this.timestamp = timestamp;
        this.android_elapsed_nanos = android_elapsed_nanos;
        this.sensor_timestamp = sensor_timestamp;
        this.raw_sensor_timestamp = raw_sensor_timestamp;
        this.sensor_counter = sensor_counter;
        this.data = data;
    }
}
