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
package org.sralab.emgimu.config;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;

import org.sralab.emgimu.EmgImuBaseActivity;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.EmgLogRecord;

import no.nordicsemi.android.nrftoolbox.widget.DividerItemDecoration;

import org.sralab.emgimu.config.R;

public class EmgImuActivity extends EmgImuBaseActivity {
	private static final String TAG = "EmgImuActivity";

	private RecyclerView mDevicesView;
	private DeviceAdapter mAdapter;

	@Override
	protected void onCreateView(final Bundle savedInstanceState) {
		setContentView(R.layout.activity_feature_emgimu);
		setGUI();
	}

	private void setGUI() {
		final RecyclerView recyclerView = mDevicesView = (RecyclerView) findViewById(android.R.id.list);
		recyclerView.setLayoutManager(new LinearLayoutManager(this));
		recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
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
	protected int getAboutTextId() {
		return R.string.emgimu_about_text;
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
			mAdapter.onDeviceReady(device);
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

    @Override
    public void onEmgPwrReceived(final BluetoothDevice device, int value) {
        if (mAdapter != null)
            mAdapter.onPwrValueReceived(device); // Adapter will access value directly from service
    }

	@Override
	public void onEmgBuffReceived(BluetoothDevice device, int count, int[][] data) {
		if (mAdapter != null)
			mAdapter.onBuffValueReceived(device);
	}

	/**** These callbacks are related to handling the RACP endpoints ****/
	@Override
	public void onEmgLogRecordReceived(BluetoothDevice device, EmgLogRecord record) {

	}

	@Override
	public void onOperationStarted(BluetoothDevice device) {

	}

	@Override
	public void onOperationCompleted(BluetoothDevice device) {

	}

	@Override
	public void onOperationFailed(BluetoothDevice device) {

	}

	@Override
	public void onOperationAborted(BluetoothDevice device) {

	}

	@Override
	public void onOperationNotSupported(BluetoothDevice device) {

	}

	@Override
	public void onDatasetClear(BluetoothDevice device) {

	}

	@Override
	public void onNumberOfRecordsRequested(BluetoothDevice device, int value) {

	}

    @Override
    public void onDeviceSelected(final BluetoothDevice device, final String name) {
	    super.onDeviceSelected(device, name);
	    getService().updateSavedDevices();
    }

}
