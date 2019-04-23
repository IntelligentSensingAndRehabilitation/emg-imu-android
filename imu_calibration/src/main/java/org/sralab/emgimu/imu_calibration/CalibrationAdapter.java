package org.sralab.emgimu.imu_calibration;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import org.sralab.emgimu.EmgImuAdapterActivity;
import org.sralab.emgimu.service.EmgImuManager;

import java.util.List;

public class CalibrationAdapter extends EmgImuAdapterActivity.DeviceAdapter {

    private final String TAG = CalibrationAdapter.class.getSimpleName();

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            final ViewGroup parent, final int viewType) {

        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_calibration, parent, false);
        return new MyViewHolder(view);
    }

    @Override
    public void onDeviceReady(BluetoothDevice device) {
        Log.d(TAG, "onDeviceReady");
        super.onDeviceReady(device);
        notifyDataSetChanged();
    }

    class MyViewHolder extends EmgImuAdapterActivity.DeviceAdapter.ViewHolder {

        private TextView status;
        private ImageView calibrationIm;
        private Button startButton;
        private Button finishButton;
        private TextView sensorId;

        @Override
        public void bind(BluetoothDevice device) {
            super.bind(device);
            Log.d(TAG, "Binding: " + device);
            if (device != null) {
                boolean connected = getService().isReady(device);
                startButton.setEnabled(connected);
                sensorId.setText("Sensor: " + getDevice().getAddress());
            } else {
                finishButton.setEnabled(false);
                startButton.setEnabled(false);
                status.setText("");
                sensorId.setText("Sensor: Connecting...");
            }
        }



        MyViewHolder(final View itemView) {
            super(itemView);

            status = itemView.findViewById(R.id.calibration_status);
            calibrationIm = itemView.findViewById(R.id.calibration_image);

            sensorId = itemView.findViewById(R.id.sensor_id);

            startButton = itemView.findViewById(R.id.start_calibration_button);
            startButton.setOnClickListener(v -> {

                // TODO: could add state awareness to toggle between enabled or not
                // but really the view holder should not have any state information
                // as it could be recycled and have this be inaccurate.

                BluetoothDevice dev =  getDevice();
                Log.d(TAG, "My device is " + dev);

                status.setText("Zeroing initial calibration...");

                getService().startCalibration(dev, new EmgImuManager.CalibrationListener() {
                    @Override
                    public void onUploading() {

                    }

                    @Override
                    public void onComputing() {

                    }

                    @Override
                    public void onReceivedCal(List<Float> Ainv, List<Float> b, float len_var, List<Float> angles) {

                    }

                    @Override
                    public void onReceivedIm(Bitmap im) {

                    }

                    @Override
                    public void onSent() {
                        Log.d(TAG, "Device zeroed");
                        status.setText("Collecting. Please rotate sensor.");
                        getService().enableImu(getDevice());
                        finishButton.setEnabled(true);
                        startButton.setEnabled(false);
                    }

                    @Override
                    public void onError(String msg) {

                    }
                });

            });

            finishButton = itemView.findViewById(R.id.finish_calibration_button);
            finishButton.setOnClickListener(v -> {

                finishButton.setEnabled(false);

                BluetoothDevice dev =  getDevice();
                Log.d(TAG, "My device is " + dev);

                EmgImuManager.CalibrationListener listener = new EmgImuManager.CalibrationListener() {
                    @Override
                    public void onUploading() {
                        status.setText("Uploading...");
                        getService().disableImu(dev); // Can stop updates
                    }

                    @Override
                    public void onComputing() {
                        status.setText("Computing...");
                    }

                    @Override
                    public void onReceivedCal(List<Float> Ainv, List<Float> b, float len_var, List<Float> angles) {
                        status.setText(String.format("Completed. Length error %.1f%%. Angles <%.1f, %.1f, %.1f>",
                                100 * Math.sqrt(len_var),
                                angles.get(0), angles.get(1), angles.get(2)));
                    }

                    @Override
                    public void onReceivedIm(Bitmap im) {
                        calibrationIm.setImageBitmap(im);
                    }

                    @Override
                    public void onSent() {
                        String msg = status.getText().toString();
                        msg = msg + ". Saved to device.";
                        status.setText(msg);
                        bind(dev); // Update UI based on state
                    }

                    @Override
                    public void onError(String err) {
                        status.setText(err);
                        bind(dev); // Update UI based on state
                    }

                };

                getService().finishCalibration(dev, listener);
            });

        }

    }
}
