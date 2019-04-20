package org.sralab.emgimu.imu_calibration;

import android.bluetooth.BluetoothDevice;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.sralab.emgimu.EmgImuAdapterActivity;

import java.security.InvalidParameterException;

public class CalibrationAdapter extends EmgImuAdapterActivity.DeviceAdapter {

    private final String TAG = CalibrationAdapter.class.getSimpleName();

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            final ViewGroup parent, final int viewType) {

        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_calibration, parent, false);
        int height = parent.getMeasuredHeight();

        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = height;
        view.setLayoutParams(params);

        return new MyViewHolder(view);
    }

    @Override
    public void onDeviceReady(BluetoothDevice device) {
        super.onDeviceReady(device);
    }

    class MyViewHolder extends EmgImuAdapterActivity.DeviceAdapter.ViewHolder {


        MyViewHolder(final View itemView) {
            super(itemView);

            Button calibrateButton = itemView.findViewById(R.id.calibrate_button);
            calibrateButton.setOnClickListener(v -> {

                // TODO: could add state awareness to toggle between enabled or not
                // but really the view holder should not have any state information
                // as it could be recycled and have this be inaccurate.

                BluetoothDevice dev =  getDevice();
                Log.d(TAG, "My device is " + dev);

                getService().enableImu(dev);

                Log.d(TAG, "Enabled IMU streaming");
            });

            Button testButton = itemView.findViewById(R.id.test_button);
            testButton.setOnClickListener(v -> {

                BluetoothDevice dev =  getDevice();
                Log.d(TAG, "My device is " + dev);

                getService().finishCalibration(dev);
            });
        }

    }
}
