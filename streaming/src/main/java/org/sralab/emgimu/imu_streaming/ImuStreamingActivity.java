package org.sralab.emgimu.imu_streaming;

import android.os.Bundle;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import org.sralab.emgimu.streaming.R;
import no.nordicsemi.android.nrftoolbox.widget.DividerItemDecoration;

public class ImuStreamingActivity extends AppCompatActivity {
    private static final String TAG = ImuStreamingActivity.class.getSimpleName();

    private ImuStreamingAdapter streamingAdapter;
    private DeviceViewModel dvm;

    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_imu_streaming);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
        crashlytics.setCrashlyticsCollectionEnabled(!org.sralab.emgimu.service.BuildConfig.DEBUG);

        final RecyclerView recyclerView = findViewById(R.id.emg_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));

        dvm = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())).get(DeviceViewModel.class);
        recyclerView.setAdapter(streamingAdapter = new ImuStreamingAdapter(this, dvm));
    }

    @Override
    public void onPause() {
        super.onPause();
        dvm.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        dvm.onResume();
    }

}
