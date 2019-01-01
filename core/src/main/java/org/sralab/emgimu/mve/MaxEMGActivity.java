package org.sralab.emgimu.mve;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import org.sralab.emgimu.EmgImuBaseActivity;
import org.sralab.emgimu.config.R;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.EmgLogRecord;

import java.text.NumberFormat;

public class MaxEMGActivity extends EmgImuBaseActivity implements EmgPowerView.OnMaxChangedEventListener {

    private final static String TAG = MaxEMGActivity.class.getSimpleName();
    private EmgImuService.EmgImuBinder mService;

    private EmgPowerView mPwrView;
    private EditText maxScaleInput;

    private float mMax = Float.NaN;
    private float mMin = Float.NaN;
    private float mRange = Float.NaN;

    private BluetoothDevice mDevice;
    
    @Override
    protected void onCreateView(final Bundle savedInstanceState) {
        setContentView(R.layout.activity_max_emg);

        mPwrView = findViewById(R.id.emg_power_view);
        mPwrView.setOnMaxChangedEventListener(this);

        Button mClearMaxButton = findViewById(R.id.clear_max_button);
        mClearMaxButton.setOnClickListener(view -> {
            clearMax();
            clearMin();
        });

        Button mSaveMaxButton = findViewById(R.id.save_max_button);
        mSaveMaxButton.setOnClickListener(view -> {
            // TODO: save this trial to firebase
            Log.e(TAG, "Saving threshold is not implemented");
            clearMax();
            clearMin();
        });

        Button mSaveThreshold = findViewById(R.id.save_threshold_button);
        mSaveThreshold.setOnClickListener(view -> {
            float min = mPwrView.getMin();
            float max = mPwrView.getMax();

            Log.d(TAG, "Saving range: " + min + " " + max);
            mService.setPwrRange(mDevice, min, max);

            float threshold_high = mPwrView.getThreshold();
            float threshold_low = min + (threshold_high - min) * 0.5f;

            Log.d(TAG, "Saving thresholds " + threshold_low + " threshold " + threshold_high);
            mService.setClickThreshold(mDevice, threshold_low, threshold_high);
        });

        maxScaleInput = findViewById(R.id.emg_max_scale);
        maxScaleInput.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void afterTextChanged(Editable editable) {
                try {
                    mRange = Float.parseFloat(editable.toString());
                    mPwrView.setMaxRange(mRange);
                    Log.d(TAG, "Range change to: " + mRange);
                } catch (NumberFormatException e) {
                    // Do nothing until valid number entered
                }
            }

        });
        try {
            mRange = Float.parseFloat(maxScaleInput.getText().toString());
            mPwrView.setMaxRange(mRange);
            Log.d(TAG, "Range change to: " + mRange);
        } catch (NumberFormatException e) {
            // Do nothing until valid number entered
        }
    }

    //! Clear the prior max by setting to zero
    void clearMax() {
        updateMax(0);
    }

    void updateMax(float newMax) {
        mMax = newMax;
        mPwrView.setMaxPower(mMax);
    }

    void clearMin() {
        updateMin(Short.MAX_VALUE);
    }

    void updateMin(float newMin) {
        mMin = newMin;
        mPwrView.setMinPower(mMin);
    }

    @Override
    protected void onServiceBinded(EmgImuService.EmgImuBinder binder) {
        mService = binder;
    }

    @Override
    protected void onServiceUnbinded() {
        mService = null;
    }

    @Override
    protected int getAboutTextId() {
        return 0;
    }

    @Override
    public void onEmgBuffReceived(BluetoothDevice device, int count, double[][] data) {

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
        Log.d(TAG, "Device connected: " + device);

        // Is previously connected device might be ready and this event won't fire
        if (mService != null && mService.isReady(device)) {
            Log.d(TAG, "Device is already ready. Must have previously connected.");
            onDeviceReady(device);
        } else if (mService == null) {
            Log.w(TAG, "Probable race condition");
        }
    }

    @Override
    public void onDeviceDisconnected(BluetoothDevice device) {

    }

    @Override
    public void onDeviceReady(BluetoothDevice device) {
        // TODO: add dropdown to allow selecting device
        Log.d("DeviceAdapter", "Device added. Requested streaming: " + device);
        mService.streamPwr(device);

        mDevice = device;

        mPwrView.setThreshold(mService.getClickThreshold(device));
        mPwrView.setMinPower(mService.getMinPwr(device));
        mPwrView.setMaxPower(mService.getMaxPwr(device));
    }

    float mLpfValue = Float.NaN;
    final float LPF_ALPHA = 0.1f;

    @Override
    public void onEmgPwrReceived(final BluetoothDevice device, int value) {

        // Initialize with first sample
        if (Float.isNaN(mLpfValue)) {
            mLpfValue = value;
            Log.d(TAG, "First value is: " + value);
        }

        mLpfValue = mLpfValue * (1-LPF_ALPHA) + value * LPF_ALPHA;

        mPwrView.setCurrentPower(mLpfValue);

        if (mLpfValue > mRange) {
            maxScaleInput.setText(Float.toString(mLpfValue));
        }

        if (mLpfValue > mMax || Double.isNaN(mMax)) {
            updateMax(mLpfValue);
        }

        if (mLpfValue < mMin || Double.isNaN(mMin)) {
            updateMin(mLpfValue);
        }
    }

    @Override
    public void onImuAttitudeReceived(BluetoothDevice device, float[] quaternion) {

    }

    @Override
    //! Callback from the view when the max is changed
    public void onMaxChanged(float newMax) {
        Log.d(TAG, "View changed max: " + newMax);
        mMax = newMax;
    }
}
