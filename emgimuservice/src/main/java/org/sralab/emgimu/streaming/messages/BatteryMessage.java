package org.sralab.emgimu.streaming.messages;


public class BatteryMessage {

    public final String MSG = "Battery";
    public String bluetoothMac;
    public long timestamp;
    public double data;

    public BatteryMessage(String bluetoothMac, long timestamp, double data) {
        this.bluetoothMac = bluetoothMac;
        this.timestamp = timestamp;
        this.data = data;
    }
}
