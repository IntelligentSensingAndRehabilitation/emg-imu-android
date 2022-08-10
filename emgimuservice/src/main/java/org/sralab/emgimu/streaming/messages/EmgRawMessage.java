package org.sralab.emgimu.streaming.messages;

import java.nio.ByteBuffer;
import java.util.Arrays;
import android.util.Base64;

public class EmgRawMessage {
    public final String MSG = "EmgRaw";
    public String bluetoothMac;
    public long timestamp;
    public long android_elapsed_nanos;
    public long sensor_timestamp;
    public int sensor_counter;
    public int channels;
    public int samples;
    String data;

    private static double[][] arrayCopy(double[][] src) {
        double[][] dst = new double[src.length][];
        for (int i = 0; i < src.length; i++) {
            dst[i] = Arrays.copyOf(src[i], src[i].length);
        }
        return dst;
    }

    public EmgRawMessage(String bluetoothMac, long timestamp, long android_elapsed_nanos,
                         long sensor_timestamp, int sensor_counter,
                         int channels, int samples, double [][] data) {
        this.bluetoothMac = bluetoothMac;
        this.timestamp = timestamp;
        this.android_elapsed_nanos = android_elapsed_nanos;
        this.channels = channels;
        this.samples = samples;
        this.sensor_timestamp = sensor_timestamp;
        this.sensor_counter = sensor_counter;

        float [] float_data = new float[channels * samples];
        for(int i = 0; i < channels; i++)
            for (int j = 0; j < samples; j++)
                float_data[i * samples + j] = (float) data[i][j];

        ByteBuffer buf = ByteBuffer.allocate(Float.SIZE / Byte.SIZE * float_data.length);
        buf.asFloatBuffer().put(float_data);
        String base64_data = Base64.encodeToString(buf.array(), Base64.NO_WRAP);
        this.data = base64_data;
    }
}
