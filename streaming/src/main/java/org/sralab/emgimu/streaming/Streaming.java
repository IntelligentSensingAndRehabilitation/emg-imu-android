package org.sralab.emgimu.streaming;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.core.CrashlyticsCore;

import org.sralab.emgimu.EmgImuAdapterActivity;
import org.sralab.emgimu.service.EmgImuService;

import io.fabric.sdk.android.Fabric;

public class Streaming extends EmgImuAdapterActivity {
    private static final String TAG = Streaming.class.getSimpleName();

    private EditText mRangeText;
    private CheckBox enableFilter;

    private StreamingAdapter mStreamingAdapter;

    @Override
    protected void onCreateView(final Bundle savedInstanceState) {

        CrashlyticsCore crashlyticsCore = new CrashlyticsCore.Builder()
                .disabled(BuildConfig.DEBUG)
                .build();
        Fabric.with(this, new Crashlytics.Builder().core(crashlyticsCore).build());

        setContentView(R.layout.activity_streaming);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mStreamingAdapter = new StreamingAdapter();
        final RecyclerView recyclerView = findViewById(R.id.emg_list);
        super.onCreateView(mStreamingAdapter, recyclerView);

        mRangeText = findViewById(R.id.rangeText);
        mRangeText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (mStreamingAdapter == null)
                    return;

                try {
                    double range = Double.parseDouble(charSequence.toString());
                    mStreamingAdapter.setRange(range);
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
            if (mStreamingAdapter != null)
                mStreamingAdapter.toggleFiltering(b);
        });

    }

    @Override
    protected void onServiceBinded(EmgImuService.EmgImuBinder binder) {
        super.onServiceBinded(binder);

        mStreamingAdapter.toggleFiltering(enableFilter.isChecked());
        try {
            double range = Double.parseDouble(mRangeText.getText().toString());
            mStreamingAdapter.setRange(range);
            Log.d(TAG, "Range set to: " + range);
        } catch (NumberFormatException e) {
            // Do nothing if not valid
        }
    }

}
