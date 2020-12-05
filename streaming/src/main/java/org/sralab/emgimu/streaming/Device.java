package org.sralab.emgimu.streaming;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.achartengine.model.TimeSeries;
import org.sralab.emgimu.visualization.GraphData;

import java.util.ArrayList;
import java.util.List;

public class Device {

    private final static String TAG = Device.class.getSimpleName();

    private boolean filtering;
    public void setFiltering(boolean filtering) {
        this.filtering = filtering;
    }

    GraphData emg;
    public LiveData<GraphData.Data> getEmg() { return emg.getData(); }
    public void setRange(float range) {
        emg.setScale(1.0f / range);
    }

    List<Filter> filter;

    private class Filter {
        /**
         * Optimize for 2000 Hz sampling
         * <p>
         * import numpy as np
         * import scipy.signal as sig
         * import matplotlib.pyplot as plt
         * <p>
         * FS = 2000
         * fcl = 40.0
         * fch = 125.0
         * w_pb = [fcl/(FS/2), fch/(FS/2)]
         * <p>
         * b,a = sig.cheby1(2, 3, w_pb, btype='bandpass')
         */

        private final int FIR_ORDER = 5;
        private final double[] A = {1., -3.70211638, 5.25666722, -3.39474815, 0.84242058};
        private final double[] B = {0.00822449, 0., -0.01644898, 0., 0.00822449};
        private final double[] inputs = new double[FIR_ORDER - 1];  // stores history of inputs with most recent at the end
        private final double[] outputs = new double[FIR_ORDER - 1]; // stores history of outputs with most recent at the end

        final double update(double val) {
            double new_output = val * B[0];

            for (int i = 1; i < FIR_ORDER; i++) {
                int j = FIR_ORDER - 1 - i;
                new_output += B[i] * inputs[j] - A[i] * outputs[j];
            }

            // Shift values in buffer and add new values
            for (int i = 0; i < FIR_ORDER - 1; i++) {
                inputs[i] = (i == (FIR_ORDER - 2)) ? val : inputs[i + 1];
                outputs[i] = (i == (FIR_ORDER - 2)) ? new_output : outputs[i + 1];
            }

            return new_output;
        }
    }

    public void addVoltage(double [] timestamp, double [][] voltage) {
        if (filtering) {

            final int channels = voltage.length;
            double [][] filteredVoltage = new double[channels][];

            for (int ch = 0; ch < channels; ch++) {
                final int samples = voltage[ch].length;
                filteredVoltage[ch] = new double[samples];
                for (int s = 0; s < samples; s++) {
                    filteredVoltage[ch][s] = filter.get(ch).update(voltage[ch][s]);
                }
            }

            emg.addSamples(timestamp, filteredVoltage);
        }
        else
            emg.addSamples(timestamp, voltage);
    }

    public Device(int channels) {

        emg = new GraphData(10000, channels);
        emg.setScale(1.0f/20000.0f);

        filter = new ArrayList<>();
        for (int ch = 0; ch < channels; ch++) {
            filter.add(new Filter());
        }

    }

}
