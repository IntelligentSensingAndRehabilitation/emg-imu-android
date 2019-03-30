/*
 * Copyright (c) 2016, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sralab.emgimu.streaming;

import android.bluetooth.BluetoothDevice;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.sralab.emgimu.EmgImuAdapterActivity;
import org.sralab.emgimu.visualization.LineGraphView;

import java.util.HashMap;
import java.util.Map;

public class StreamingAdapter extends EmgImuAdapterActivity.DeviceAdapter {

    private final String TAG = StreamingAdapter.class.getSimpleName();

    private final Map<Pair<BluetoothDevice, Integer>, LineGraphView> mDeviceLineGraphMap = new HashMap<>();
    private final Integer DISPLAY_CHANNELS = 4; // TODO: this should be adjustable

    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {

        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.emg_activity, parent, false);
        int height = parent.getMeasuredHeight() / DISPLAY_CHANNELS;

        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = height;
        view.setLayoutParams(params);

        return new StreamingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        // Find the device and channel index that corresponds to a row
        int deviceIndex = 0;
        for (deviceIndex = 0; deviceIndex < getDevices().size(); deviceIndex++) {
            final BluetoothDevice dev = getDevices().get(deviceIndex);
            int devLastPos = getPosition(dev) +  getService().getChannelCount(dev);
            if (devLastPos >= position) {
                break;
            }
        }

        final BluetoothDevice d = getDevices().get(deviceIndex);
        int channel = position - getPosition(d);
        ((StreamingViewHolder) holder).bind(d, channel);
	}

	@Override
    public void onViewRecycled(final ViewHolder holder) {
        // When a view is recycled, we must dettach the graph from it's
        // view so it can be used again later

        final StreamingViewHolder streamingHolder = (StreamingViewHolder) holder;
        streamingHolder.mLayoutView.removeAllViews();
        streamingHolder.mLineGraph = null;
    }

    private boolean mFiltering = true;

    public void toggleFiltering(boolean filter) {
        mFiltering = filter;
        for (LineGraphView l : mDeviceLineGraphMap.values()) {
            l.enableFiltering(mFiltering);
        }
    }

    @Override
	public int getItemCount() {
	    int items = 0;
	    for (final BluetoothDevice device : getDevices())
	        items += getService().getChannelCount(device);
	    return items;
	}

    //! Get the row in the adapters for the first channel of this device
	private int getPosition(final BluetoothDevice device) {
        int items = 0;
        for (final BluetoothDevice d : getDevices()) {
            if (device == d)
                break;
            items += getService().getChannelCount(d);
        }
        return items;
    }

    private double mRange = 5e5; // default range in graphs
    void setRange(double newRange) {
        mRange = newRange;
        for (LineGraphView l : mDeviceLineGraphMap.values()) {
            l.setRange(mRange);
        }
    }

    @Override
    public void onDeviceReady(BluetoothDevice device) {
        super.onDeviceReady(device);

        getService().streamBuffered(device);
    }

    // Used to only update graphically for a subset of new data
    private int updateCounter = 0;
    public void onEmgBuffReceived(final BluetoothDevice device, int count, final double[][] data) {

        int channels = getService().getChannelCount(device);

        if (BuildConfig.DEBUG && data.length != channels) {
            throw new RuntimeException("Channel count does not match expected data size");
        }

        updateCounter ++;
        for (int channel = 0; channel < channels; channel++) {
            // If graph exists for this device, update it with new data
            LineGraphView mLineGraph = mDeviceLineGraphMap.get(new Pair<>(device, channel));
            if (mLineGraph != null) {
                for (double buffValue : data[channel]) {
                    mLineGraph.addValue(buffValue);
                }

            }

        }

        if (updateCounter % 5 == 0) {
            for(int channel = 0; channel < channels; channel++) {
                final int position = getPosition(device) + channel;
                if (position >= 0)
                    notifyItemChanged(position);
            }
        }
    }

	class StreamingViewHolder extends EmgImuAdapterActivity.DeviceAdapter.ViewHolder {
        private ViewGroup mLayoutView;

        private LineGraphView mLineGraph;

        StreamingViewHolder(final View itemView) {
			super(itemView);

            mLayoutView = itemView.findViewById(R.id.emg_activity);

            if (mLayoutView == null) {
                throw new RuntimeException("Cannot find view for list of emgs");
            }

            // Graphing elements will be assigned once we have a position
            mLineGraph = null;
        }

        // We want one line graph and graph view per device. The ViewHolder constructor
        // can be created multiple times in the line cycle of the view so cannot create there
        private void createGraphIfMissing(final BluetoothDevice device, int channel) {

            if (mLineGraph == null) {
                mLineGraph = mDeviceLineGraphMap.get(device);

                // See if graph has been cached and if so use this
                if (mLineGraph == null) {
                    mLineGraph = new LineGraphView(mLayoutView.getContext(), mLayoutView);
                    mLineGraph.setWindowSize(250);
                    mLineGraph.setRange(mRange);
                    mLineGraph.enableFiltering(mFiltering);
                    mDeviceLineGraphMap.put(new Pair<>(device, channel), mLineGraph);
                }
            }
        }

		private void bind(final BluetoothDevice device, int channel) {
            // Update the graph
            createGraphIfMissing(device, channel);
            mLineGraph.repaint();
		}
	}
}
