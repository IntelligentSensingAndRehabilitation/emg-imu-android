package org.sralab.emgimu.streaming;

import android.bluetooth.BluetoothDevice;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import org.sralab.emgimu.EmgImuBaseActivity;
import org.sralab.emgimu.service.EmgImuManager;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.EmgLogRecord;

public class Streaming extends EmgImuBaseActivity {
    private static final String TAG = Streaming.class.getSimpleName();

    private EmgImuService.EmgImuBinder mBinder;

    @Override
    protected void onCreateView(final Bundle savedInstanceState) {
        setContentView(R.layout.activity_streaming);
    }

    @Override
    protected void onServiceBinded(EmgImuService.EmgImuBinder binder) {
        mBinder = binder;
        Log.d(TAG, "onServiceBinded");
        Log.d(TAG, "Managed devices: " + binder.getManagedDevices());
    }

    @Override
    protected void onServiceUnbinded() {

    }

    @Override
    protected int getAboutTextId() {
        return 0;
    }

    @Override
    public void onEmgBuffReceived(BluetoothDevice device, int count, int[][] data) {
        Log.d(TAG, "onEmgBuffReceived: " + count);
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
    public void onDeviceConnected(BluetoothDevice device) {

    }

    @Override
    public void onDeviceDisconnected(BluetoothDevice device) {

    }

    @Override
    public void onDeviceReady(BluetoothDevice device) {
        mBinder.streamBuffered(device);
    }
}
