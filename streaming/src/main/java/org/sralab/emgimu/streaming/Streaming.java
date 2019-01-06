package org.sralab.emgimu.streaming;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;

import org.sralab.emgimu.EmgImuBaseActivity;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.EmgLogRecord;

import java.text.NumberFormat;

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

        CrashlyticsCore crashlyticsCore = new CrashlyticsCore.Builder()
                .disabled(BuildConfig.DEBUG)
                .build();
        Fabric.with(this, new Crashlytics.Builder().core(crashlyticsCore).build());

        setContentView(R.layout.activity_streaming);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

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
                if (mAdapter == null)
                    return;

                try {
                    double range = Double.parseDouble(charSequence.toString());
                    mAdapter.setRange(range);
                    Log.d(TAG, "Range change to: " + range);
                } catch (NumberFormatException e) {
                    // Do nothing until valid number entered
                }

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

        try {
            double range = Double.parseDouble(mRangeText.getText().toString());
            mAdapter.setRange(range);
            Log.d(TAG, "Range set to: " + range);
        } catch (NumberFormatException e) {
            // Do nothing if not valid
        }
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
    public void onBondingFailed(@NonNull BluetoothDevice device) {

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
    public void onLinkLossOccurred(@NonNull BluetoothDevice device) {

    }

    @Override
    public void onDeviceNotSupported(final BluetoothDevice device) {
        super.onDeviceNotSupported(device);
        if (mAdapter != null)
            mAdapter.onDeviceRemoved(device);
    }

    @Override
    public void onEmgPwrReceived(final BluetoothDevice device, int value) {
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
    public void onEmgBuffReceived(BluetoothDevice device, int count, double[][] data) {
        if (mAdapter != null) {
            mAdapter.onBuffValueReceived(device, count, data);
        }
    }

}
