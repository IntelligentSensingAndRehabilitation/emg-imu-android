package org.sralab.emgimu.imu_calibration;

import android.bluetooth.BluetoothDevice;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.sralab.emgimu.EmgImuAdapterActivity;

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

    class MyViewHolder extends EmgImuAdapterActivity.DeviceAdapter.ViewHolder {
        private ViewGroup mLayoutView;

        MyViewHolder(final View itemView) {
            super(itemView);

            //mLayoutView = itemView.findViewById(R.id.view_calibration);
        }

        private void bind(final BluetoothDevice device) {
        }
    }
}
