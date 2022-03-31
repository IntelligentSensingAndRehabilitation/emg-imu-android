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

package org.sralab.emgimu.mve;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.sralab.emgimu.config.R;

import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.ViewHolder> {

    private static final String TAG = DeviceAdapter.class.getSimpleName();

    private LifecycleOwner context;
	private final LiveData<List<Device>> devices;
	private DeviceViewModel dvm;
	private String deviceAndChannelName = new String();
	private int channelNumber;
    public String getDeviceAndChannelName() {
        return deviceAndChannelName;
    }
    public void setDeviceAndChannelName(String name) { this.deviceAndChannelName = name;}

    public DeviceAdapter(LifecycleOwner context, DeviceViewModel dvm) {
	    this.dvm = dvm;
        dvm.getDevicesLiveData().observe(context, devices -> notifyDataSetChanged());

        devices = dvm.getDevicesLiveData();
        this.context = context;
    }

	@NonNull
    @Override
	public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
		final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_max_emg_item, parent, false);
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

    /**
     * @brief   This method binds each channel of each device to the View.
     * @details When the user first opens the Max Activity menu,
     *          This method will show, device 0, channel 0; thus, only a single view is created.
     *          When the user swipes right-to-left, each subsequent View is created.
     * from left-to-right, device
     * @param holder
     * @param position
     */
	@Override
	public void onBindViewHolder(final ViewHolder holder, final int position) {
	    holder.bind(devices.getValue().get(position / 2), position % 2);
	}

    /**
     * @brief       This method tells the RecyclerView how many items it should be able to display.
     * @details     This method is required for RecyclerView to function properly. How many potential
     *              items it should display, but it may make less ViewHolders then that number.
     * @return      Number of devices times number of channels, connected to the app.
     */
    @Override
	public int getItemCount() {
        int numberOfChannels = 2;
		return devices.getValue().size() * numberOfChannels;
	}


	public class ViewHolder extends RecyclerView.ViewHolder {

	    EmgPowerView power;
	    TextView deviceAndChannelNameWidget;

        public ViewHolder(final View itemView) {
			super(itemView);
			power = itemView.findViewById(R.id.emg_power_view);
			deviceAndChannelNameWidget = itemView.findViewById(R.id.sensorNameAndChannel);

			Button mClearMaxButton = itemView.findViewById(R.id.clear_max_button);
            mClearMaxButton.setOnClickListener(view -> dvm.reset());

            Button mSaveMaxButton = itemView.findViewById(R.id.save_max_button);
            mSaveMaxButton.setOnClickListener(view -> {
                final Device device = devices.getValue().get(getAdapterPosition() / 2);
                final int channelNumber = getAdapterPosition() % 2; // uses number of devices in the list

                Log.d(TAG, "adapter, called saveMvc() | channelNumber =  " + channelNumber);
                Log.d(TAG, "adapter, called saveMvc() | device =  " + device.getAddress());
                dvm.saveMvc(device, channelNumber);
                dvm.reset();
            });

        }

        /**
         * This method is responsible for displaying the actual data for each RecyclerView
         * @param device    Class that represents the BLE device.
         * @param channel   Represents the channel of the EMG power data streaming.
         */
		private void bind(final Device device, int channel) {
		    String deviceAddress = device.getAddress();
		    String deviceAbbreviatedAddress = deviceAddress.substring(0, 2);
		    // channel is zero-indexed; however, for the user - it appears at one-indexed to
            // eliminate confusion and maintain consistency with the wire harness
		    String deviceAndChannelName = deviceAbbreviatedAddress + " ch-" + (channel + 1);

            // Below mtds: the first argument, "context" is the UI activity
            // Below mtds: the second argument, "value ->..." is the code that updates the UI

            // Notes on lambda expression:
            // before -> operator: "value", in this case, is the parameter
            // after  -> operator: "power.setCurrentPower(value), in this case, is the action
            // So, instead of sending in the object with some action, we're sending in
            // the action itself. Single action can be on just one line.
            // We're using the lambda to pass in the implementation.
            // Gives you the ability to make lambda implementations into objects like any other,
            // that can be saved into variables and passed into methods as parameters.

            // We call observe on the live data and pass in the UI (1st arg), lambda exp to update UI

            device.getPowerTwoChannel()[channel].observe(context, value -> power.setCurrentPower(value));
            device.getPowerTwoChannel()[channel].observe(context, value -> Log.d(TAG, "DeviceAdapter, device = " + deviceAddress + "| ch = " + channel + "| pwr = " + value));
            device.getMaximumTwoChannel()[channel].observe(context, value -> power.setMaxPower(value));
            device.getMinimumTwoChannel()[channel].observe(context, value -> power.setMinPower(value));
            dvm.getRange().observe(context, value -> power.setMaxRange(value));
            deviceAndChannelNameWidget.setText(deviceAndChannelName);
            Log.d(TAG, "DeviceAdapter, device: " + device.getAddress()
                    + " | ch0:" + device.getPowerTwoChannel()[0].getValue()
                    + " | ch1:" + device.getPowerTwoChannel()[1].getValue());
            Log.d(TAG, "DeviceAdapter, calling bind method for device = " + device.getAddress() + ", channel = " +channel);
            channelNumber = channel;
        }
	}
}
