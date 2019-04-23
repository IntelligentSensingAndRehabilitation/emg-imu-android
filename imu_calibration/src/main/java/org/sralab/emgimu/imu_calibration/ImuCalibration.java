package org.sralab.emgimu.imu_calibration;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.view.WindowManager;

import org.sralab.emgimu.EmgImuAdapterActivity;

public class ImuCalibration extends EmgImuAdapterActivity {

    static final private String TAG = ImuCalibration.class.getSimpleName();

    @Override
    protected void onCreateView(Bundle savedInstanceState) {

        setContentView(R.layout.activity_imu_calibration);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final RecyclerView recyclerView = findViewById(R.id.imu_calibration_list);
        super.onCreateView(new CalibrationAdapter(), recyclerView);
    }

    @Override
    public void onImuMagReceived(BluetoothDevice device, float[][] mag) {

    }
}
