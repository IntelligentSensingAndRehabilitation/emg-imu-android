package org.sralab.emgimu.imu_calibration;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.WindowManager;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;

import org.sralab.emgimu.EmgImuBaseActivity;
import org.sralab.emgimu.service.EmgImuService;

import io.fabric.sdk.android.Fabric;
import no.nordicsemi.android.nrftoolbox.widget.DividerItemDecoration;

public class ImuCalibration extends EmgImuBaseActivity {

    static final private String TAG = ImuCalibration.class.getSimpleName();

    private EmgImuService.EmgImuBinder mService;
    private RecyclerView mDevicesView;
    private DeviceAdapter mAdapter;

    @Override
    protected void onCreateView(Bundle savedInstanceState) {
        CrashlyticsCore crashlyticsCore = new CrashlyticsCore.Builder()
                .disabled(BuildConfig.DEBUG)
                .build();
        Fabric.with(this, new Crashlytics.Builder().core(crashlyticsCore).build());

        Log.d(TAG, "About to create layout");
        setContentView(R.layout.activity_imu_calibration);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final RecyclerView recyclerView = mDevicesView = findViewById(R.id.imu_calibration_list);
        if (recyclerView == null)
            throw new RuntimeException("No IMU list");
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));

    }


    @Override
    protected void onServiceBinded(EmgImuService.EmgImuBinder binder) {
        mService = binder;
        mDevicesView.setAdapter(mAdapter = new DeviceAdapter(mService));
    }

    @Override
    protected void onServiceUnbinded() {
        mDevicesView.setAdapter(mAdapter = null);
    }

    @Override
    public void onDeviceConnecting(final BluetoothDevice device) {
        super.onDeviceConnecting(device);
        if (mAdapter != null)
            mAdapter.onDeviceAdded(device);
    }

    @Override
    public void onDeviceConnected(final BluetoothDevice device) {
        if (mAdapter != null)
            mAdapter.onDeviceStateChanged(device);

        // Is previously connected device might be ready and this event won't fire
        if (mService != null && mService.isReady(device)) {
            onDeviceReady(device);
        } else if (mService == null) {
            Log.w(TAG, "Probable race condition");
        }
    }

    @Override
    public void onDeviceReady(final BluetoothDevice device) {
        super.onDeviceReady(device);
        if (mAdapter != null)
            mAdapter.onDeviceReady(device);
    }

    @Override
    public void onBondingFailed(@NonNull BluetoothDevice device) {

    }

    @Override
    public void onDeviceDisconnecting(final BluetoothDevice device) {
        super.onDeviceDisconnecting(device);
        if (mAdapter != null)
            mAdapter.onDeviceStateChanged(device);
    }

    @Override
    public void onDeviceDisconnected(final BluetoothDevice device) {
        if (mAdapter != null)
            mAdapter.onDeviceRemoved(device);
    }

    @Override
    public void onLinkLossOccurred(@NonNull BluetoothDevice device) {
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
    public void onBatteryReceived(BluetoothDevice device, float battery) {

    }

    @Override
    public void onEmgBuffReceived(BluetoothDevice device, int count, double[][] data) {

    }

    @Override
    public void onImuAccelReceived(BluetoothDevice device, float[][] accel) {

    }

    @Override
    public void onImuGyroReceived(BluetoothDevice device, float[][] gyro) {

    }

    @Override
    public void onImuAttitudeReceived(BluetoothDevice device, float[] quaternion) {

    }

    @Override
    protected int getAboutTextId() {
        return 0;
    }

}
