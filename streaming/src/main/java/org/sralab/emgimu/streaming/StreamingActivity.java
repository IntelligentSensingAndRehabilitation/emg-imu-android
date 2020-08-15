package org.sralab.emgimu.streaming;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import no.nordicsemi.android.nrftoolbox.widget.DividerItemDecoration;

public class StreamingActivity extends AppCompatActivity {
    private static final String TAG = StreamingActivity.class.getSimpleName();

    private EditText mRangeText;
    private CheckBox enableFilter;

    private DeviceViewModel dvm;
    private StreamingAdapter streamingAdapter;

    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // TODO: this may need to be reverted but testing for now.
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
            }
        }
        setContentView(R.layout.activity_streaming);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        /*
        CrashlyticsCore crashlyticsCore = new CrashlyticsCore.Builder()
                .disabled(BuildConfig.DEBUG)
                .build();
        Fabric.with(this, new Crashlytics.Builder().core(crashlyticsCore).build());

        mRangeText = findViewById(R.id.rangeText);
        mRangeText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (streamingAdapter == null)
                    return;

                try {
                    double range = Double.parseDouble(charSequence.toString());
                    // TODO: streamingAdapter.setRange(range);
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
            Log.d(TAG, "Toggle streaming filter");
        });
        */

        final RecyclerView recyclerView = findViewById(R.id.emg_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));

        dvm = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())).get(DeviceViewModel.class);
        dvm.getDevicesLiveData().observe(this, devices -> streamingAdapter.notifyDataSetChanged());
        recyclerView.setAdapter(streamingAdapter = new StreamingAdapter(this, dvm));
    }

}
