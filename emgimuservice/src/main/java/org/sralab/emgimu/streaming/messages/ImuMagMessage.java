package org.sralab.emgimu.streaming.messages;

public class ImuMagMessage {

    public final String MSG = "ImuMag";
    public String bluetoothMac;
    public long timestamp;
    public float [][] data;

    public ImuMagMessage(String bluetoothMac, long timestamp, float [][] data) {
        this.bluetoothMac = bluetoothMac;
        this.timestamp = timestamp;
        this.data = data;
    }
}
