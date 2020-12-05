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

import android.bluetooth.BluetoothGatt;
import android.content.res.ColorStateList;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.sralab.emgimu.visualization.GraphView;

import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {

    private static final String TAG = DeviceAdapter.class.getSimpleName();

    private LifecycleOwner context;
	private final LiveData<List<Device>> devices;
	private final DeviceViewModel deviceViewModel;

	public DeviceAdapter(LifecycleOwner context, DeviceViewModel dvm) {
        devices = dvm.getDevicesLiveData();
        deviceViewModel = dvm;
        this.context = context;
    }

	@NonNull
    @Override
	public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
		final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_config_emgimu_item, parent, false);
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
	    holder.bind(devices.getValue().get(position));
	}

    @Override
	public int getItemCount() {
		return devices.getValue().size();
	}


	public class ViewHolder extends RecyclerView.ViewHolder {
		private TextView batteryView;
        private TextView addressView;
        private GraphView graphView;
        private ImageButton actionDisconnect;

        ColorStateList connectedColor;
        ColorStateList disconnectedColor;

        public ViewHolder(final View itemView) {
			super(itemView);

			addressView = itemView.findViewById(R.id.address);
            batteryView = itemView.findViewById(R.id.battery);
            graphView = itemView.findViewById(R.id.graph_pwr);
            actionDisconnect = itemView.findViewById(R.id.action_disconnect);

            connectedColor = ContextCompat.getColorStateList(itemView.getContext(), R.color.sral_red);
            disconnectedColor = ContextCompat.getColorStateList(itemView.getContext(), R.color.sral_orange);

            // Configure Disconnect button
            actionDisconnect.setOnClickListener(v -> {
                Log.d(TAG, "Disconnect button pressed");
                final int position = getAdapterPosition();
                final Device device = devices.getValue().get(position);
                deviceViewModel.removeDeviceFromService(device);
            });

        }

        private void updateConnection(int state) {
            final ColorStateList color = state == BluetoothGatt.STATE_CONNECTED ? connectedColor : disconnectedColor;
            actionDisconnect.setBackgroundTintList(color);
        }

		private void bind(final Device device) {

            addressView.setText(device.getAddress());

            device.getPwr().observe(context, graphData -> graphView.updateGraphData(graphData));

            batteryView.setText(String.format("%.2fV", device.getBattery().getValue()));
            device.getBattery().observe(context, value -> batteryView.setText(String.format("%.2fV", value)));

            updateConnection(device.getConnectionState().getValue());
            device.getConnectionState().observe(context, value -> updateConnection(value));
        }
	}
}
