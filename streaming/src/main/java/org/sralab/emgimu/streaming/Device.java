package org.sralab.emgimu.streaming;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.achartengine.model.TimeSeries;

import java.util.ArrayList;
import java.util.List;

public class Device {

    private final static String TAG = Device.class.getSimpleName();

    private boolean filtering;
    public void setFiltering(boolean filtering) {
        this.filtering = filtering;
    }

    List<Filter> filter;
    List<TimeSeries> series;

    private MutableLiveData<List<TimeSeries>> liveSeries = new MutableLiveData<>();
    public LiveData<List<TimeSeries>> getSeries() {
        return liveSeries;
    }
    public void addVoltage(int ch, double ts, double power) {
        final int N = 1000;

        TimeSeries series = this.series.get(ch);
        Filter filter = this.filter.get(ch);

        if (filtering)
            power = filter.update(power);

        //series.add(ts, power);
        if (series.getItemCount() == 0)
            series.add(0, power);
        else
            series.add(series.getItemCount() + 1, power);

        if (series.getItemCount() > N)
            series.remove(0);

        liveSeries.postValue(this.series);
    }

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


    public Device(int channels) {

        filter = new ArrayList<>();
        series = new ArrayList<>();
        for (int ch = 0; ch < channels; ch++) {
            filter.add(new Filter());
            series.add(new TimeSeries(Integer.toString(ch)));
        }

        liveSeries.postValue(series);
    }

}
