package org.sralab.emgimu.streaming.messages;

import java.util.Arrays;

public class EmgRawMessage {
    public final String MSG = "EmgRaw";
    public String bluetoothMac;
    public long timestamp;
    public int channels;
    public int samples;
    double [][] data;

    private static double[][] arrayCopy(double[][] src) {
        double[][] dst = new double[src.length][];
        for (int i = 0; i < src.length; i++) {
            dst[i] = Arrays.copyOf(src[i], src[i].length);
        }
        return dst;
    }

    public EmgRawMessage(String bluetoothMac, long timestamp, int channels, int samples, double [][] data) {
        this.bluetoothMac = bluetoothMac;
        this.timestamp = timestamp;
        this.channels = channels;
        this.samples = samples;
        this.data = arrayCopy(data); // avoid data being modified before serialized
    }
}
