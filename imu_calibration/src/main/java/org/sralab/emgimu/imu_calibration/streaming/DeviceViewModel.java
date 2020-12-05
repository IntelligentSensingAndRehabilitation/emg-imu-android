package org.sralab.emgimu.imu_calibration.streaming;

import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

import org.sralab.emgimu.EmgImuViewModel;
import org.sralab.emgimu.service.ImuData;
import org.sralab.emgimu.service.ImuQuatData;

import java.util.Date;

public class DeviceViewModel extends EmgImuViewModel<Device> {

    private final static String TAG = DeviceViewModel.class.getSimpleName();

    @Override
    public boolean getObserveAccel() { return true; }
    @Override
    public boolean getObserveGyro() { return true; }
    @Override
    public boolean getObserveMag() { return true; }
    @Override
    public boolean getObserveQuat() { return true; }

    long t0 = new Date().getTime();
    public DeviceViewModel(Application app) {
        super(app);
    }

    private boolean filtering = true;
    public void setFiltering(boolean filtering) {
        this.filtering = filtering;
        for (Device d : getDeviceMap().values())
            d.setFiltering(filtering);
    }

    final float Fs = 500.0f;

    @Override
    public void imuAccelUpdated(Device dev, ImuData accel) {
        for (int s = 0; s < accel.samples; s++)
            dev.addAccel(accel.ts - t0 - (accel.samples - s) / Fs, accel.x[s], accel.y[s], accel.z[s]);
    }

    @Override
    public void imuGyroUpdated(Device dev, ImuData gyro) {
        for (int s = 0; s < gyro.samples; s++)
            dev.addGyro(gyro.ts - t0 - (gyro.samples - s) / Fs, gyro.x[s], gyro.y[s], gyro.z[s]);
    }

    @Override
    public void imuMagUpdated(Device dev, ImuData mag) {
        for (int s = 0; s < mag.samples; s++)
            dev.addMag(mag.ts - t0 - (mag.samples - s) / Fs, mag.x[s], mag.y[s], mag.z[s]);
    }

    @Override
    public void imuQuatUpdated(Device dev, ImuQuatData quat) {
        float [] q = {(float) quat.q0, (float) quat.q1, (float) quat.q2, (float) quat.q3};
        dev.setQuat(q);
    }

    @Override
    public Device getDev(BluetoothDevice d) {
        return new Device();
    }

}
