package org.sralab.emgimu.streaming;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.achartengine.model.TimeSeries;

public class Device {

    private final static String TAG = Device.class.getSimpleName();

    private TimeSeries series = new TimeSeries("EMG Power");
    private MutableLiveData<TimeSeries> liveSeries = new MutableLiveData<>();
    public LiveData<TimeSeries> getSeries() {
        return liveSeries;
    }
    public void addPower(long ts, Integer power) {
        final int N = 100;

        series.add(ts, power);

        if (series.getItemCount() > N)
            series.remove(0);

        liveSeries.postValue(series);
    }

    public Device() {
        liveSeries.postValue(series);
    }

}
