package org.sralab.emgimu.imu_calibration;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;

import org.sralab.emgimu.EmgImuAdapterActivity;
import org.sralab.emgimu.logging.FirebaseGameLogger;
import org.sralab.emgimu.service.EmgImuService;

import java.util.Date;
import java.util.List;

import no.nordicsemi.android.nrftoolbox.widget.DividerItemDecoration;

public class ImuCalibration extends AppCompatActivity {

    static final private String TAG = ImuCalibration.class.getSimpleName();

    CalibrationAdapter calibrationAdapater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_imu_calibration);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final RecyclerView recyclerView = findViewById(R.id.imu_calibration_list);

        DeviceViewModel dvm = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())).get(DeviceViewModel.class);

        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));

        recyclerView.setAdapter(calibrationAdapater = new CalibrationAdapter(this, dvm));
    }
    
}
