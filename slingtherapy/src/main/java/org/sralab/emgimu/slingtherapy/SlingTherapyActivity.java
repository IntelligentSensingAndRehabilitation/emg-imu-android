package org.sralab.emgimu.slingtherapy;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.atomic.AtomicReference;

import no.nordicsemi.android.nrftoolbox.widget.DividerItemDecoration;

public class SlingTherapyActivity extends AppCompatActivity {

    private final String TAG = SlingTherapyActivity.class.getSimpleName();

    private RecyclerView mDevicesView;
    private DeviceAdapter mAdapter;
    private DeviceViewModel dvm;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sling_therapy);
        setGUI();

        /*
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
        */


        dvm = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())).get(DeviceViewModel.class);
        dvm.getDevicesLiveData().observe(this, devices -> mAdapter.notifyDataSetChanged());

        mDevicesView.setAdapter(mAdapter = new DeviceAdapter(this, dvm));
    }

    public void onDestroy()
    {
        dvm.onStop();
        super.onDestroy();
    }

    private void setGUI() {
        final RecyclerView recyclerView = mDevicesView = findViewById(R.id.device_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));
    }
}
