package org.sralab.emgimu.streaming;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

import org.sralab.emgimu.EmgImuBaseActivity;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.EmgLogRecord;

import io.fabric.sdk.android.Fabric;
import no.nordicsemi.android.nrftoolbox.widget.DividerItemDecoration;

public class Streaming extends EmgImuBaseActivity {
    private static final String TAG = Streaming.class.getSimpleName();

    private EmgImuService.EmgImuBinder mBinder;

    private RecyclerView mDevicesView;
    private DeviceAdapter mAdapter;

    @Override
    protected void onCreateView(final Bundle savedInstanceState) {
        Fabric.with(this, new Crashlytics());

        setContentView(R.layout.activity_streaming);

        final RecyclerView recyclerView = mDevicesView = (RecyclerView) findViewById(R.id.emg_list);
        if (recyclerView == null)
            throw new RuntimeException("No emg list");
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
    }

    @Override
    protected void onServiceBinded(EmgImuService.EmgImuBinder binder) {
        mBinder = binder;
        mDevicesView.setAdapter(mAdapter = new DeviceAdapter(binder));
        Log.d(TAG, "onServiceBinded");
        Log.d(TAG, "Managed devices: " + binder.getManagedDevices());
    }

    @Override
    protected void onServiceUnbinded() {
        mDevicesView.setAdapter(mAdapter = null);
    }

    @Override
    protected int getAboutTextId() {
        return 0;
    }

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
    public void onDeviceConnecting(final BluetoothDevice device) {
        super.onDeviceConnecting(device);
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
        Log.d(TAG, "onEmgPwrReceived.");
        if (mAdapter != null)
            mAdapter.onPwrValueReceived(device); // Adapter will access value directly from service
    }

    @Override
    public void onEmgBuffReceived(BluetoothDevice device, int count, int[][] data) {
        if (mAdapter != null) {
            mAdapter.onBuffValueReceived(device);
        }
    }

}
