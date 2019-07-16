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

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;
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

    private static final String TAG = DeviceAdapter.class.getSimpleName();

	private final EmgImuService.EmgImuBinder mService;
	private final List<BluetoothDevice> mDevices;
    private final Map<BluetoothDevice, LineGraphView> mDeviceLineGraphMap = new HashMap<BluetoothDevice, LineGraphView>();

	public DeviceAdapter(final EmgImuService.EmgImuBinder binder) {
		mService = binder;
		mDevices = mService.getManagedDevices();
	}

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
    }

    @Override
	public int getItemCount() {
		return mDevices.size();
	}

	public void onBatteryValueReceived(final BluetoothDevice device) {
        final int position = mDevices.indexOf(device);
        notifyItemChanged(position);
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
        //mDeviceLineGraphMap.remove(device);

        // HACK: delete all graphs. Because a view gets recycled we need to
        // add the graph to a different layout at this point. Clearing all
        // graphs forces this to happen.
        mDeviceLineGraphMap.clear();
	}

	public void onDeviceStateChanged(final BluetoothDevice device) {
		final int position = mDevices.indexOf(device);

		Log.d(TAG, "Device updated: " + position);
        if (position >= 0)
            notifyItemChanged(position);
        else
            // In principle we should know which device was updated. However
            // if the service removes a device from the list when it goes to
            // disconnected, then the device might be dropped.
            notifyDataSetChanged(); // we don't have position of the removed device here
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
        } else {
            Log.w(TAG, "Graph missing");
        }

        final int position = mDevices.indexOf(device);
        if (position >= 0) {
            notifyItemChanged(position);
        }else
            Log.e(TAG, "Device missing");

        //notifyDataSetChanged();
    }

	public class ViewHolder extends RecyclerView.ViewHolder {
		private TextView batteryView;
        private TextView addressView;
        private ViewGroup layoutView;
        private ImageButton actionDisconnect;

        ColorStateList connectedColor;
        ColorStateList disconnectedColor;

        public ViewHolder(final View itemView) {
			super(itemView);

			addressView = itemView.findViewById(R.id.address);
            batteryView = itemView.findViewById(R.id.battery);
            layoutView = itemView.findViewById(R.id.graph_pwr);
            actionDisconnect = itemView.findViewById(R.id.action_disconnect);

            connectedColor = ContextCompat.getColorStateList(itemView.getContext(), R.color.sral_red);
            disconnectedColor = ContextCompat.getColorStateList(itemView.getContext(), R.color.sral_orange);

            // Configure Disconnect button
            actionDisconnect.setOnClickListener(v -> {
                final int position = getAdapterPosition();
                final BluetoothDevice device = mDevices.get(position);
                mService.disconnect(device);
                // The device might have not been connected, so there will be no callback
                onDeviceRemoved(device);
            });

        }

        // We want one line graph and graph view per device. The ViewHolder constructor
        // can be created multiple times in the life cycle of the view so cannot create there
        private LineGraphView getGraph(final BluetoothDevice device) {


            LineGraphView mLineGraph = mDeviceLineGraphMap.get(device);

            // See if graph has been cached and if so use this
            if (mLineGraph == null) {
                Log.d(TAG, "Creating graph");
                mLineGraph = new LineGraphView(itemView.getContext(), layoutView);
                mDeviceLineGraphMap.put(device, mLineGraph);
            }

            return mLineGraph;
        }

		private void bind(final BluetoothDevice device) {
			final int state = mService.getConnectionState(device);

			addressView.setText(device.getAddress());

			final double voltage = mService.getBattery(device);
			if (voltage > 0) {
                batteryView.setText(String.format("%.2fV", voltage));
            }

            // Color of disconnect button should indicate connection status
            final ColorStateList color = state == BluetoothGatt.STATE_CONNECTED ? connectedColor : disconnectedColor;
            actionDisconnect.setBackgroundTintList(color);

            // Update the graph
            getGraph(device).repaint();
		}
	}
}
