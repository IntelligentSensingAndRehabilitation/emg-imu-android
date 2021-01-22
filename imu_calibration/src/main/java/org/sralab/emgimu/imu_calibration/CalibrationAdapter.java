package org.sralab.emgimu.imu_calibration;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.RecyclerView;

import org.sralab.emgimu.EmgImuAdapterActivity;
import org.sralab.emgimu.service.EmgImuManager;

import java.util.List;

public class CalibrationAdapter extends RecyclerView.Adapter<CalibrationAdapter.ViewHolder> {

    private final String TAG = CalibrationAdapter.class.getSimpleName();

    private LifecycleOwner context;
    private final LiveData<List<Device>> devices;
    private DeviceViewModel dvm;

    public CalibrationAdapter(LifecycleOwner context, DeviceViewModel dvm) {
        this.dvm = dvm;
        dvm.getDevicesLiveData().observe(context, devices -> notifyDataSetChanged());

        devices = dvm.getDevicesLiveData();
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_calibration, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        holder.bind(devices.getValue().get(position));
    }

    @Override
    public int getItemCount() {
        return devices.getValue().size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private TextView status;
        private ImageView calibrationIm;
        private Button startButton;
        private Button finishButton;
        private TextView sensorId;

        private void updateConnection(Device.CalibrationStatus connection) {
            if (connection == Device.CalibrationStatus.DISCONNECTED) {
                startButton.setEnabled(false);
                finishButton.setEnabled(false);
            }
            else if (connection == Device.CalibrationStatus.IDLE) {
                startButton.setEnabled(true);
                finishButton.setEnabled(false);
            }
            else if (connection == Device.CalibrationStatus.STARTED) {
                startButton.setEnabled(false);
                finishButton.setEnabled(true);
            }
            else if (connection == Device.CalibrationStatus.FINISHED) {
                startButton.setEnabled(false);
                finishButton.setEnabled(false);
            };
        }

        public void bind(Device device) {
            sensorId.setText("Sensor: " + device.getAddress());

            device.getImage().observe(context, im -> calibrationIm.setImageBitmap(im));
            device.getStatus().observe(context, s -> status.setText(s));

            startButton.setOnClickListener(v -> device.startCalibration());
            finishButton.setOnClickListener(v -> device.finishCalibration());

            device.getCalibrationStatus().observe(context, value -> updateConnection(value));
        }

        ViewHolder(final View itemView) {
            super(itemView);

            status = itemView.findViewById(R.id.calibration_status);
            calibrationIm = itemView.findViewById(R.id.calibration_image);

            sensorId = itemView.findViewById(R.id.sensor_id);

            startButton = itemView.findViewById(R.id.start_calibration_button);

            /*startButton.setOnClickListener(v -> {

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

            });*/

            finishButton = itemView.findViewById(R.id.finish_calibration_button);
            /*finishButton.setOnClickListener(v -> {

                finishButton.setEnabled(false);

                BluetoothDevice dev =  getDevice();
                Log.d(TAG, "My device is " + dev);

                EmgImuManager.CalibrationListener listener = new EmgImuManager.CalibrationListener() {
                    @Override
                    public void onUploading() {
                        status.setText("Uploading...");
                        getService().disableImu(getDevice()); // Can stop updates
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
            });*/

        }

    }
}
