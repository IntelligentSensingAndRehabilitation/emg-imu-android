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

public class MaxEMGActivity extends EmgImuBaseActivity implements EmgPowerView.OnMaxChangedEventListener {

    private final static String TAG = MaxEMGActivity.class.getSimpleName();
    private EmgImuService.EmgImuBinder mService;

    private EmgPowerView mPwrView;

    private double mMax = Double.NaN;
    private double mMin = Double.NaN;

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
            double thresh = mPwrView.getThreshold();
            double min = mPwrView.getMin();

            min = min + (thresh - min) * 0.5;

            Log.d(TAG, "Saving threshold " + thresh + " and minimum " + min);

            mService.setThreshold(mDevice, min, thresh);
        });

        EditText maxScaleInput = findViewById(R.id.emg_max_scale);
        maxScaleInput.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void afterTextChanged(Editable editable) {
                double range = Double.parseDouble(editable.toString());
                mPwrView.setMaxRange(range);
                Log.d(TAG, "Range change to: " + range);
            }

        });
        double range = Double.parseDouble(maxScaleInput.getText().toString());
        mPwrView.setMaxRange(range);
        Log.d(TAG, "Range change to: " + range);
    }

    //! Clear the prior max by setting to zero
    void clearMax() {
        updateMax(0);
    }

    void updateMax(double newMax) {
        mMax = newMax;
        mPwrView.setMaxPower(mMax);
    }

    void clearMin() {
        updateMin(Short.MAX_VALUE);
    }

    void updateMin(double newMin) {
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

    int discardCounter;

    @Override
    public void onDeviceReady(BluetoothDevice device) {
        // TODO: add dropdown to allow selecting device
        Log.d("DeviceAdapter", "Device added. Requested streaming: " + device);
        mService.streamPwr(device);

        discardCounter = 20;

        mDevice = device;

        double thresh = mService.getMaxThreshold(device);
        Log.d(TAG, "Threshold loaded: " + thresh);
        mPwrView.setThreshold(thresh);
    }

    double mLpfValue = Double.NaN;
    final double LPF_ALPHA = 0.1;

    @Override
    public void onEmgPwrReceived(final BluetoothDevice device, int value) {

        // Let ADC warm up on chip
        if (discardCounter > 0) {
            discardCounter--;
            return;
        }

        // Initialize with first sample
        if (Double.isNaN(mLpfValue)) {
            mLpfValue = value;
            Log.d(TAG, "First value is: " + value);
        }

        mLpfValue = mLpfValue * (1-LPF_ALPHA) + value * LPF_ALPHA;

        mPwrView.setCurrentPower(mLpfValue);

        if (mLpfValue > mMax || Double.isNaN(mMax)) {
            updateMax(mLpfValue);
        }

        if (mLpfValue < mMin || Double.isNaN(mMin)) {
            updateMin(mLpfValue);
        }
    }

    @Override
    //! Callback from the view when the max is changed
    public void onMaxChanged(double newMax) {
        Log.d(TAG, "View changed max: " + newMax);
        mMax = newMax;
    }
}
