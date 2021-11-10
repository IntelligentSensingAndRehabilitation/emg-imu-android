package org.sralab.emgimu.streaming.messages;

public class ForceMessage {

    public final String MSG = "DynamometerForce";
    public String bluetoothMac;
    public long timestamp;
    public double [] data;

    public ForceMessage(String bluetoothMac, long timestamp, double [] data) {
        this.bluetoothMac = bluetoothMac;
        this.timestamp = timestamp;
        this.data = data;
    }
}
