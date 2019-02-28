package org.sralab.emgimu.streaming.messages;

public class ImuAccelMessage {

    public final String MSG = "ImuAccel";
    public String bluetoothMac;
    public long timestamp;
    public float [][] data;

    public ImuAccelMessage(String bluetoothMac, long timestamp, float [][] data) {
        this.bluetoothMac = bluetoothMac;
        this.timestamp = timestamp;
        this.data = data;
    }
}
