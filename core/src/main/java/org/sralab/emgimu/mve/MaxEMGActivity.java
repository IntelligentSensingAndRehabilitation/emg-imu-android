package org.sralab.emgimu.mve;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.sralab.emgimu.config.R;

import no.nordicsemi.android.nrftoolbox.widget.DividerItemDecoration;

public class MaxEMGActivity extends AppCompatActivity {

    private final static String TAG = MaxEMGActivity.class.getSimpleName();

    RecyclerView recyclerView;
    DeviceAdapter deviceAdapter;
    DeviceViewModel dvm;
    
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_max_emg);
        recyclerView = findViewById(R.id.recycler_view);

        dvm = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())).get(DeviceViewModel.class);

        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));

        recyclerView.setAdapter(deviceAdapter = new DeviceAdapter(this, dvm));

        Button mClearMaxButton = findViewById(R.id.clear_max_button);
        mClearMaxButton.setOnClickListener(view -> dvm.reset());

        EditText maxScaleInput = findViewById(R.id.emg_max_scale);
        maxScaleInput.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

            @Override
            public void afterTextChanged(Editable editable) {
                try {
                    Float range = Float.parseFloat(editable.toString());
                    dvm.setRange(range.intValue());
                } catch (NumberFormatException e) {
                    // Do nothing until valid number entered
                }
            }

        });


        /*
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
            ///try {
            //    // TODO: mService.setPwrRange(mDevice, min, max);
            //} catch (RemoteException e) {
            //    e.printStackTrace();
            //}/

            float threshold_high = mPwrView.getThreshold();
            float threshold_low = min + (threshold_high - min) * 0.5f;

            Log.d(TAG, "Saving thresholds " + threshold_low + " threshold " + threshold_high);
            //try {
            //    // TODO: mService.setClickThreshold(mDevice, threshold_low, threshold_high);
            //} catch (RemoteException e) {
            //    e.printStackTrace();
            //}
        });


        try {
            mRange = Float.parseFloat(maxScaleInput.getText().toString());
            mPwrView.setMaxRange(mRange);
            Log.d(TAG, "Range change to: " + mRange);
        } catch (NumberFormatException e) {
            // Do nothing until valid number entered
        }
        */
    }

    /*
    @Override
    //! Callback from the view when the max is changed
    public void onMaxChanged(float newMax) {
        Log.d(TAG, "View changed max: " + newMax);
        mMax = newMax;
    }
    */
}
