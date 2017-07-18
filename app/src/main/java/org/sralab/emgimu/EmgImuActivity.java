/*
 * Copyright (c) 2015, Nordic Semiconductor
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
package org.sralab.emgimu;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import java.util.UUID;

import no.nordicsemi.android.nrftoolbox.profile.multiconnect.BleMulticonnectProfileService;
import no.nordicsemi.android.nrftoolbox.profile.multiconnect.BleMulticonnectProfileServiceReadyActivity;
import no.nordicsemi.android.nrftoolbox.widget.DividerItemDecoration;

public class EmgImuActivity extends BleMulticonnectProfileServiceReadyActivity<EmgImuService.EmgImuBinder> implements EmgImuManagerCallbacks {
	private static final String TAG = "EmgImuActivity";

	private RecyclerView mDevicesView;
	private DeviceAdapter mAdapter;

	private final BroadcastReceiver mCommonBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			final BluetoothDevice bluetoothDevice = intent.getParcelableExtra(EmgImuService.EXTRA_DEVICE);
			final String action = intent.getAction();
			switch (action) {
				case EmgImuService.BROADCAST_EMG_RAW: {
					final int value = intent.getIntExtra(EmgImuService.EXTRA_EMG_RAW, -1);
					if (value > 0)
                        onEmgRawReceived(bluetoothDevice, value);
					break;
				}
                case EmgImuService.BROADCAST_EMG_PWR: {
                    final int value = intent.getIntExtra(EmgImuService.EXTRA_EMG_PWR, -1);
                    if (value > 0)
                        onEmgPwrReceived(bluetoothDevice, value);
                    break;
                }
                case EmgImuService.BROADCAST_EMG_BUFF: {
                    final int [] value = intent.getIntArrayExtra(EmgImuService.EXTRA_EMG_BUFF);
                    if (value != null)
                        onEmgBuffReceived(bluetoothDevice, value);
                    break;
                }
			}
		}
	};

	@Override
	protected void onCreateView(final Bundle savedInstanceState) {
		setContentView(R.layout.activity_feature_emgimu);
		setGUI();
        LocalBroadcastManager.getInstance(this).registerReceiver(mCommonBroadcastReceiver, makeIntentFilter());
	}

    @Override
    protected void onDestroy() {
        super.onDestroy();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mCommonBroadcastReceiver);
    }

    private static IntentFilter makeIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(EmgImuService.BROADCAST_EMG_RAW);
        intentFilter.addAction(EmgImuService.BROADCAST_EMG_PWR);
        intentFilter.addAction(EmgImuService.BROADCAST_EMG_BUFF);
        return intentFilter;
    }

	private void setGUI() {
		final RecyclerView recyclerView = mDevicesView = (RecyclerView) findViewById(android.R.id.list);
		recyclerView.setLayoutManager(new LinearLayoutManager(this));
		recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
	}

	@Override
	protected int getLoggerProfileTitle() {
		if (getService() != null)
		    return getService().getLoggerProfileTitle();
        else
            return 0;
	}

	@Override
	protected void onServiceBinded(final EmgImuService.EmgImuBinder binder) {
		mDevicesView.setAdapter(mAdapter = new DeviceAdapter(binder));
	}

	@Override
	protected void onServiceUnbinded() {
		mDevicesView.setAdapter(mAdapter = null);
	}

	@Override
	protected Class<? extends BleMulticonnectProfileService> getServiceClass() {
		return EmgImuService.class;
	}

	@Override
	protected int getAboutTextId() {
		return R.string.emgimu_about_text;
	}

	@Override
	protected UUID getFilterUUID() {
		return EmgImuManager.EMG_SERVICE_UUID;
	}

	@Override
	public void onDeviceConnecting(final BluetoothDevice device) {
		if (mAdapter != null)
			mAdapter.onDeviceAdded(device);
	}

	@Override
	public void onDeviceConnected(final BluetoothDevice device) {
		if (mAdapter != null)
			mAdapter.onDeviceStateChanged(device);
	}

	@Override
	public void onDeviceReady(final BluetoothDevice device) {
		if (mAdapter != null)
			mAdapter.onDeviceStateChanged(device);
	}

	@Override
	public void onDeviceDisconnecting(final BluetoothDevice device) {
		if (mAdapter != null)
			mAdapter.onDeviceStateChanged(device);
	}

	@Override
	public void onDeviceDisconnected(final BluetoothDevice device) {
		if (mAdapter != null)
			mAdapter.onDeviceRemoved(device);
	}

	@Override
	public void onDeviceNotSupported(final BluetoothDevice device) {
		super.onDeviceNotSupported(device);
		if (mAdapter != null)
			mAdapter.onDeviceRemoved(device);
	}

	@Override
	public void onLinklossOccur(final BluetoothDevice device) {
		if (mAdapter != null)
			mAdapter.onDeviceStateChanged(device);

		// The link loss may also be called when Bluetooth adapter was disabled
		if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
			// Do nothing. We could notify the user here.
		}
	}

    /**
     * Note that this is called via the LocalBroadcastManager intents from the service
     */
    @Override
    public void onEmgRawReceived(final BluetoothDevice device, int value) {
        // Do nothing
    }

    @Override
    public void onEmgPwrReceived(final BluetoothDevice device, int value) {
        if (mAdapter != null)
            mAdapter.onPwrValueReceived(device); // Adapter will access value directly from service
    }

    @Override
    public void onEmgBuffReceived(final BluetoothDevice device, int [] value) {
        if (mAdapter != null)
            mAdapter.onBuffValueReceived(device);
    }
}
