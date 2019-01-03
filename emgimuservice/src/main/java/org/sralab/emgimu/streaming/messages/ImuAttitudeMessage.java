package org.sralab.emgimu.streaming.messages;

public class ImuAttitudeMessage {

    public final String MSG = "ImuAttitude";
    public String bluetoothMac;
    public long timestamp;
    public float [] data;

    public ImuAttitudeMessage(String bluetoothMac, long timestamp, float [] data) {
        this.bluetoothMac = bluetoothMac;
        this.timestamp = timestamp;
        this.data = data;
    }
}
