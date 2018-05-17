package org.sralab.emgimu.mve;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.app.Activity;

import org.sralab.emgimu.EmgImuBaseActivity;
import org.sralab.emgimu.config.R;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.EmgLogRecord;

public class MaxEMGActivity extends EmgImuBaseActivity {

    @Override
    protected void onCreateView(final Bundle savedInstanceState) {
        setContentView(R.layout.activity_max_emg);
    }

    @Override
    protected void onServiceBinded(EmgImuService.EmgImuBinder binder) {

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
}
