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

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.RemoteException;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.sralab.emgimu.EmgImuBaseActivity;
import org.sralab.emgimu.service.EmgImuManager;
import org.sralab.emgimu.service.IEmgImuServiceBinder;

import java.util.UUID;

import no.nordicsemi.android.nrftoolbox.scanner.ScannerFragment;
import no.nordicsemi.android.nrftoolbox.widget.DividerItemDecoration;

public class ConfigActivity extends EmgImuBaseActivity implements ScannerFragment.OnDeviceSelectedListener {

	private static final String TAG = "ConfigActivity";

	private RecyclerView mDevicesView;
	private DeviceAdapter mAdapter;
	private IEmgImuServiceBinder mService;
	private DeviceViewModel dvm;

	@Override
	protected void onCreateView(final Bundle savedInstanceState) {
		setContentView(R.layout.activity_feature_emgimu);
		setGUI();

		// TODO: this may need to be reverted but testing for now.
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			Intent intent = new Intent();
			String packageName = getPackageName();
			PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
			if (!pm.isIgnoringBatteryOptimizations(packageName)) {
				intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
				intent.setData(Uri.parse("package:" + packageName));
				startActivity(intent);
			}
		}

		dvm = new ViewModelProvider.AndroidViewModelFactory(getApplication()).create(DeviceViewModel.class);
		dvm.getDevicesLiveData().observe(this, devices -> mAdapter.notifyDataSetChanged());

		mDevicesView.setAdapter(mAdapter = new DeviceAdapter(dvm));
	}

	private void setGUI() {
		final RecyclerView recyclerView = mDevicesView = findViewById(android.R.id.list);
		recyclerView.setLayoutManager(new LinearLayoutManager(this));
		recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
	}

	@Override
	protected void onServiceBinded(final IEmgImuServiceBinder binder) {
        mService = binder;
		String user = null;
		try {
			user = mService.getUser();
			TextView userView = findViewById(R.id.user_id);
			userView.setText("User: " + user);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void onServiceUnbinded() {

	}

	protected UUID getFilterUUID() {
		return EmgImuManager.EMG_SERVICE_UUID;
	}

	@Override
	protected int getAboutTextId() {
		return R.string.emgimu_about_text;
	}

	@Override
	public void onBatteryReceived(BluetoothDevice device, float battery) {

	}

	public void onEmgBuffReceived(BluetoothDevice device, long ts_ms, double[][] data) {
	}

	@Override
	public void onImuAccelReceived(BluetoothDevice device, float[][] accel) {

	}

	@Override
	public void onImuGyroReceived(BluetoothDevice device, float[][] gyro) {

	}

	@Override
	public void onImuMagReceived(BluetoothDevice device, float[][] mag) {

	}

	@Override
	public void onImuAttitudeReceived(BluetoothDevice device, float[] quaternion) {

	}

	/**
	 * Called when user press ADD DEVICE button. See layout files -> onClick attribute.
	 */
	public void onAddDeviceClicked(final View view) {
		showDeviceScanningDialog(getFilterUUID());
	}

	public void onDeviceSelected(final BluetoothDevice device, final String name) {
		try {
			mService.connect(device);
			getService().updateSavedDevices();
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onDialogCanceled() {

	}

	/**
	 * Shows the scanner fragment.
	 *
	 * @param filter               the UUID filter used to filter out available devices. The fragment will always show all bonded devices as there is no information about their
	 *                             services
	 * @see #getFilterUUID()
	 */
	private void showDeviceScanningDialog(final UUID filter) {
		final ScannerFragment dialog = ScannerFragment.getInstance(filter);
		dialog.show(getSupportFragmentManager(), "scan_fragment");
	}

}
