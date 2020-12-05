package org.sralab.emgimu.visualization;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Arrays;

/**
 * Data interface for GraphView timeseries data. Provides commands for adding
 * samples to a rolling buffer and exposes the data through a LiveData object.
 * Can be held by EmgImu DeviceViewModels to store data coming in to be used
 * for graphing.
 */
public class GraphData {

    // The data to graph that is exposed via the LiveData
    public class Data {
        public int idx = 0;
        public int numSamples;
        public int numChannels;
        public float[][] values;
        public float [] timestamps;
        public float scale = 1.0f;
        public float [][] color;
        public boolean positive;
    }

    private final Data data = new Data();
    private final MutableLiveData<Data> observedData = new MutableLiveData<>();

    public GraphData(int numSamples, int numChannels) {

        this.data.numChannels = numChannels;
        this.data.numSamples = numSamples;
        this.data.positive = false;

        // Initialize new data array
        data.values = new float[numChannels][];
        for (int i = 0; i < numChannels; i++) {
            data.values[i] = new float[numSamples];
        }
        data.timestamps = new float[numSamples];
        for (int i = 0; i < numSamples; i++)
            data.timestamps[i] = Float.NaN;

        data.color = new float[numChannels][];
        for (int i = 0; i < numChannels; i++) {
            data.color[i] = new float[4];
            data.color[i][3] = 1.0f;  // All are mostly opaque
        }

        if (numChannels > 0) {
            data.color[0][0] = 230.0f / 256.0f;
            data.color[0][1] = 159.0f / 256.0f;
            data.color[0][2] = 0.0f;
        }

        if (numChannels > 1) {
            data.color[1][0] = 86.0f / 256.0f;
            data.color[1][1] = 180.0f / 256.0f;
            data.color[1][2] = 233.0f / 256.0f;
        }

        if (numChannels > 2) {
            data.color[2][0] = 240.0f / 256.0f;
            data.color[2][1] = 228.0f / 256.0f;
            data.color[2][2] = 66.0f / 256.0f;
        }

    }

    public void setScale(float scale) {
        data.scale = scale;
    }
    public void setPositive(boolean positive) { this.data.positive = positive; }

    public void setColor(int ch, float r, float g, float b, float alpha) {
        assert ch < data.numChannels;
        data.color[ch][0] = r;
        data.color[ch][1] = g;
        data.color[ch][2] = b;
        data.color[ch][3] = alpha;
    }

    // Provide addSamples of different dimensionalities to cover various
    // uses. Adding an entire array reduces the number of post events.
    public synchronized void addSample(float ts, float sample) {

        synchronized (data) {
            assert data.numChannels == 1;
            data.values[0][data.idx] = sample;
            data.timestamps[data.idx] = ts;
            data.idx = (data.idx + 1) % data.numSamples;
        }

        observedData.postValue(data);
    }

    public void addSamples(float ts, float[] samples) {

        //
        synchronized (data) {
            assert samples.length == data.numChannels;
            for (int i = 0; i < data.numChannels; i++) {
                data.values[i][data.idx] = samples[i];
            }
            data.timestamps[data.idx] = ts;
            data.idx = (data.idx + 1) % data.numSamples;
        }

        observedData.postValue(data);

    }

    public synchronized void addSamples(double [] ts, double [][] samples) {

        synchronized (data) {
            // Check all samples is a valid matrix dimension
            assert samples.length == data.numChannels;
            int updateSamples = ts.length;
            for (int ch = 0; ch < data.numChannels; ch++)
                assert samples[ch].length == updateSamples;

            // Update the data array
            for (int s = 0; s < updateSamples; s++) {
                for (int ch = 0; ch < data.numChannels; ch++) {
                    data.values[ch][data.idx] = (float) samples[ch][s];
                }
                data.timestamps[data.idx] = (float) ts[s];
                data.idx = (data.idx + 1) % data.numSamples;
            }
        }

        observedData.postValue(data);
    }

    public LiveData<Data> getData() {
        return observedData;
    }


}
