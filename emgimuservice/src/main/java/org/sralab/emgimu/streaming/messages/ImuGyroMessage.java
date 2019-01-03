package org.sralab.emgimu.streaming.messages;

public class ImuGyroMessage {

    public final String MSG = "ImuAttitude";
    public String bluetoothMac;
    public long timestamp;
    public float [][] data;

    public ImuGyroMessage(String bluetoothMac, long timestamp, float [][] data) {
        this.bluetoothMac = bluetoothMac;
        this.timestamp = timestamp;
        this.data = data;
    }
}
