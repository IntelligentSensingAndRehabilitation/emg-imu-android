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

package org.sralab.emgimu.config;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.res.ColorStateList;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageButton;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.visualization.LineGraphView;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {
	private final EmgImuService.EmgImuBinder mService;
	private final List<BluetoothDevice> mDevices;
    private final Map<BluetoothDevice, LineGraphView> mDeviceLineGraphMap = new HashMap<BluetoothDevice, LineGraphView>();

	public DeviceAdapter(final EmgImuService.EmgImuBinder binder) {
		mService = binder;
		mDevices = mService.getManagedDevices();
	}

	public int attachedViews = 0;

	@Override
	public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
		final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_feature_emgimu_item, parent, false);
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
		holder.bind(mDevices.get(position));
	}

	@Override
    public void onViewRecycled(final ViewHolder holder) {
        // When a view is recycled, we must dettach the graph from it's
        // view so it can be used again later
        holder.layoutView.removeAllViews();
        holder.mLineGraph = null;
    }

    @Override
	public int getItemCount() {
		return mDevices.size();
	}

	public void onDeviceAdded(final BluetoothDevice device) {
        final int position = mDevices.indexOf(device);
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
		final int position = mDevices.indexOf(device);
        if (position >= 0)
            notifyItemChanged(position);
	}

    public void onDeviceReady(final BluetoothDevice device) {
        Log.d("DeviceAdapter", "Device added. Requested streaming: " + device);
        mService.streamPwr(device);
    }
	public void onPwrValueReceived(final BluetoothDevice device) {

        // If graph exists for this device, update it with new data
        LineGraphView mLineGraph = mDeviceLineGraphMap.get(device);
        if (mLineGraph != null) {
            final int pwrValue = mService.getEmgPwrValue(device);
            mLineGraph.addValue(pwrValue);
        }

        final int position = mDevices.indexOf(device);
        if (position >= 0)
            notifyItemChanged(position);
    }


    public void onBuffValueReceived(final BluetoothDevice device) {

        // If graph exists for this device, update it with new data
        LineGraphView mLineGraph = mDeviceLineGraphMap.get(device);
        if (mLineGraph != null) {
            final double [] buffValue = mService.getEmgBuffValue(device)[0];
            for (int i = 0; i < buffValue.length; i++)
                mLineGraph.addValue(buffValue[i]);
        }

        final int position = mDevices.indexOf(device);
        if (position >= 0)
            notifyItemChanged(position);
    }

	public class ViewHolder extends RecyclerView.ViewHolder {
		private TextView nameView;
		private TextView addressView;
        private ViewGroup layoutView;
        private ImageButton downloadMode;
        private ImageButton actionDisconnect;

        private LineGraphView mLineGraph;

        ColorStateList connectedColor;
        ColorStateList disconnectedColor;

        public ViewHolder(final View itemView) {
			super(itemView);

			nameView = (TextView) itemView.findViewById(R.id.name);
			addressView = (TextView) itemView.findViewById(R.id.address);
            layoutView =  (ViewGroup) itemView.findViewById(R.id.graph_pwr);
            downloadMode = (ImageButton) itemView.findViewById(R.id.action_pwr_buffered);
            actionDisconnect = (ImageButton) itemView.findViewById(R.id.action_disconnect);

            // Graphing elements will be assigned once we have a position
            mLineGraph = null;

            connectedColor = ContextCompat.getColorStateList(itemView.getContext(), R.color.orange);
            disconnectedColor = ContextCompat.getColorStateList(itemView.getContext(), R.color.darkGray);

            // Configure Disconnect button
            actionDisconnect.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(final View v) {
                    final int position = getAdapterPosition();
                    final BluetoothDevice device = mDevices.get(position);
					mService.disconnect(device);
					// The device might have not been connected, so there will be no callback
					onDeviceRemoved(device);
				}
			});

            downloadMode.setOnClickListener(v -> {
                final int position = getAdapterPosition();
                final BluetoothDevice device = mDevices.get(position);

                // Catch race condition where device is not connected but button is pressed
                boolean connected = mService.isConnected(device);
                if (!connected)
                    return;

                switch(mService.getStreamingMode(device)) {
                    case STREAMING_BUFFERED:
                        mService.streamPwr(device);
                        downloadMode.setImageResource(R.drawable.ic_action_download_normal);
                        break;
                    case STREAMINNG_POWER:
                        mService.streamBuffered(device);
                        downloadMode.setImageResource(R.drawable.ic_action_download_pressed);
                        break;
                }
            });
        }

        // We want one line graph and graph view per device. The ViewHolder constructor
        // can be created multiple times in the line cycle of the view so cannot create there
        private void createGraphIfMissing(final BluetoothDevice device) {

            if (mLineGraph == null) {
                mLineGraph = mDeviceLineGraphMap.get(device);

                // See if graph has been cached and if so use this
                if (mLineGraph == null) {
                    mLineGraph = new LineGraphView(itemView.getContext(), layoutView);
                    mDeviceLineGraphMap.put(device, mLineGraph);
                }
            }
        }

		private void bind(final BluetoothDevice device) {
			final int state = mService.getConnectionState(device);

			String name = device.getName();
			if (TextUtils.isEmpty(name))
				name = nameView.getResources().getString(R.string.emgimu_default_device_name);
			nameView.setText(name);
			addressView.setText(device.getAddress());

            // Color of disconnect button should indicate connection status
            final ColorStateList color = state == BluetoothGatt.STATE_CONNECTED ? connectedColor : disconnectedColor;
            actionDisconnect.setBackgroundTintList(color);

            // Update the graph
            createGraphIfMissing(device);
            mLineGraph.repaint();
		}
	}
}
