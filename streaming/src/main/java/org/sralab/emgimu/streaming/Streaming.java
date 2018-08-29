package org.sralab.emgimu.streaming;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.EditText;

import com.crashlytics.android.Crashlytics;

import org.sralab.emgimu.EmgImuBaseActivity;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.EmgLogRecord;

import io.fabric.sdk.android.Fabric;
import no.nordicsemi.android.nrftoolbox.widget.DividerItemDecoration;

public class Streaming extends EmgImuBaseActivity {
    private static final String TAG = Streaming.class.getSimpleName();

    private EmgImuService.EmgImuBinder mService;

    private RecyclerView mDevicesView;
    private DeviceAdapter mAdapter;
    private EditText mRangeText;
    private CheckBox enableFilter;

    @Override
    protected void onCreateView(final Bundle savedInstanceState) {
        Fabric.with(this, new Crashlytics());

        setContentView(R.layout.activity_streaming);

        final RecyclerView recyclerView = mDevicesView = findViewById(R.id.emg_list);
        if (recyclerView == null)
            throw new RuntimeException("No emg list");
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));

        mRangeText = findViewById(R.id.rangeText);
        mRangeText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                double range = Double.parseDouble(charSequence.toString());
                if (mAdapter != null)
                    mAdapter.setRange(range);
                Log.d(TAG, "Range change to: " + range);
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        enableFilter = findViewById(R.id.filteringCb);
        enableFilter.setOnCheckedChangeListener((compoundButton, b) -> {
            if (mAdapter != null)
                mAdapter.toggleFiltering(b);
        });

    }

    @Override
    protected void onServiceBinded(EmgImuService.EmgImuBinder binder) {
        mService = binder;
        mDevicesView.setAdapter(mAdapter = new DeviceAdapter(mService));
        Log.d(TAG, "onServiceBinded");
        Log.d(TAG, "Managed devices: " + mService.getManagedDevices());

        mAdapter.toggleFiltering(enableFilter.isChecked());

        double range = Double.parseDouble(mRangeText.getText().toString());
        mAdapter.setRange(range);
        Log.d(TAG, "Range set to: " + range);
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

        // Is previously connected device might be ready and this event won't fire
        if (mService != null && mService.isReady(device)) {
            onDeviceReady(device);
        } else if (mService == null) {
            Log.w(TAG, "Probable race condition");
        }
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
    public void onEmgBuffReceived(BluetoothDevice device, int count, double[][] data) {
        if (mAdapter != null) {
            mAdapter.onBuffValueReceived(device, count, data);
        }
    }

}
