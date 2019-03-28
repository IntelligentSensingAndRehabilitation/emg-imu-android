package org.sralab.emgimu.streaming.messages;

import java.nio.ByteBuffer;
import java.util.Arrays;
import android.util.Base64;

public class EmgRawMessage {
    public final String MSG = "EmgRaw";
    public String bluetoothMac;
    public long timestamp;
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

    public EmgRawMessage(String bluetoothMac, long timestamp, int channels, int samples, double [][] data) {
        this.bluetoothMac = bluetoothMac;
        this.timestamp = timestamp;
        this.channels = channels;
        this.samples = samples;

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
