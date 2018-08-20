package org.sralab.emgimu.streaming.messages;


public class EmgPwrMessage {

    public final String MSG = "EmgPwr";
    public String bluetoothMac;
    public long timestamp;
    public double data;

    public EmgPwrMessage(String bluetoothMac, long timestamp, double data) {
        this.bluetoothMac = bluetoothMac;
        this.timestamp = timestamp;
        this.data = data;
    }
}
