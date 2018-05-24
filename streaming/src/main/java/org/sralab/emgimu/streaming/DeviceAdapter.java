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
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.visualization.LineGraphView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {

    private final String TAG = DeviceAdapter.class.getSimpleName();

	private final EmgImuService.EmgImuBinder mService;
	private final List<BluetoothDevice> mDevices;
    private final Map<Pair<BluetoothDevice, Integer>, LineGraphView> mDeviceLineGraphMap = new HashMap<>();
    private final Integer CHANNELS = 8; // TODO: this should be adjustable

	public DeviceAdapter(final EmgImuService.EmgImuBinder binder) {
		mService = binder;
		mDevices = mService.getManagedDevices();
	}

	public int attachedViews = 0;

	@Override
	public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {

        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.emg_activity, parent, false);
        int height = parent.getMeasuredHeight() / CHANNELS;

        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = height;
        view.setLayoutParams(params);

        return new ViewHolder(view);
	}

    public class DefaultItemAnimatorNoChange extends DefaultItemAnimator {
        public DefaultItemAnimatorNoChange() {
            setSupportsChangeAnimations(false);
        }
    }

	@Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        // This is important to make sure there are not two overlapping views for the
        // same element that the RecycleView tries to use for smoother updates. It
        // breaks the animation coming from the graph element
        recyclerView.setItemAnimator(new DefaultItemAnimatorNoChange());
    }

	@Override
	public void onBindViewHolder(final ViewHolder holder, final int position) {
		holder.bind(mDevices.get(position / CHANNELS), position % CHANNELS);
	}

	@Override
    public void onViewRecycled(final ViewHolder holder) {
        // When a view is recycled, we must dettach the graph from it's
        // view so it can be used again later
        holder.mLayoutView.removeAllViews();
        holder.mLineGraph = null;
    }

    @Override
	public int getItemCount() {
	    return mDevices.size() * CHANNELS;
	}

	public void onDeviceAdded(final BluetoothDevice device) {
        final int position = mDevices.indexOf(device) * CHANNELS;
		if (position == -1) {
			notifyItemInserted(mDevices.size() - 1);
		} else {
			// This may happen when Bluetooth adapter was switched off and on again
			// while there were devices on the list.
			notifyItemChanged(position);
		}
	}

	public void onDeviceRemoved(final BluetoothDevice device) {
        notifyDataSetChanged(); // we don't have position of the removed device here

        // Remove the graphing elements from our local cache
        mDeviceLineGraphMap.remove(device);
	}

	public void onDeviceStateChanged(final BluetoothDevice device) {
		final int position = mDevices.indexOf(device) * CHANNELS;
        if (position >= 0)
            notifyItemChanged(position);
	}

    public void onDeviceReady(final BluetoothDevice device) {
        Log.d("DeviceAdapter", "Device added. Requested streaming: " + device);
        mService.streamBuffered(device);
    }

    private double mRange = 5e5; // default range in graphs
    public void setRange(double newRange) {
        mRange = newRange;
        for (LineGraphView l : mDeviceLineGraphMap.values()) {
            l.setRange(mRange);
        }
    }

    public void onPwrValueReceived(final BluetoothDevice device) {

	    for (int channel = 0; channel < CHANNELS; channel++) {
            // If graph exists for this device, update it with new data
            LineGraphView mLineGraph = mDeviceLineGraphMap.get(new Pair<>(device, channel));
            if (mLineGraph != null) {
                final int pwrValue = mService.getEmgPwrValue(device);
                mLineGraph.addValue(pwrValue);
            }

            final int position = mDevices.indexOf(device) * CHANNELS + channel;
            if (position >= 0)
                notifyItemChanged(position);
        }
    }


    int updateCounter = 0;
    public void onBuffValueReceived(final BluetoothDevice device) {

        final double[][] bufferedValues = mService.getEmgBuffValue(device);
        final int MAX_CHANNELS = bufferedValues.length;

        updateCounter ++;
        for (int channel = 0; channel < CHANNELS & channel < MAX_CHANNELS; channel++) {
            // If graph exists for this device, update it with new data
            LineGraphView mLineGraph = mDeviceLineGraphMap.get(new Pair<>(device, channel));
            if (mLineGraph != null) {
                final double[] buffValue = mService.getEmgBuffValue(device)[channel];
                for (int i = 0; i < buffValue.length; i++)
                    mLineGraph.addValue(buffValue[i]);
            }

        }

        if (updateCounter % 5 == 0) {
            for(int channel = 0; channel < CHANNELS; channel++) {
                final int position = mDevices.indexOf(device) * CHANNELS + channel;
                if (position >= 0)
                    notifyItemChanged(position);
            }
        }
    }

	public class ViewHolder extends RecyclerView.ViewHolder {
        private ViewGroup mLayoutView;

        private LineGraphView mLineGraph;


        public ViewHolder(final View itemView) {
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
                    mLineGraph.enableFiltering(true);
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
