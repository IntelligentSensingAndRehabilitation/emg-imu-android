package org.sralab.emgimu.slingtherapy;

import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCanceledListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

import org.sralab.emgimu.EmgImuViewModel;
import org.sralab.emgimu.logging.FirebaseGameLogger;
import org.sralab.emgimu.service.EmgPwrData;
import org.sralab.emgimu.service.IEmgImuServiceBinder;
import org.sralab.emgimu.service.ImuQuatData;

import java.util.Date;

public class DeviceViewModel extends EmgImuViewModel<Device> {

    private final static String TAG = DeviceViewModel.class.getSimpleName();

    public DeviceViewModel(Application app) {
        super(app);
    }

    @Override
    public boolean getObservePwr() { return false; }

    @Override
    public boolean getObserveAccel() { return false; }
    @Override
    public boolean getObserveGyro() { return false; }
    @Override
    public boolean getObserveMag() { return false; }
    @Override
    public boolean getObserveQuat() { return true; }

    /*
    @Override
    public void imuAccelUpdated(Device dev, ImuData accel) {
        for (int s = 0; s < accel.samples; s++)
            dev.addAccel(accel.ts, accel.x[s], accel.y[s], accel.z[s]);
    }

    @Override
    public void imuGyroUpdated(Device dev, ImuData gyro) {
        for (int s = 0; s < gyro.samples; s++)
            dev.addGyro(gyro.ts, gyro.x[s], gyro.y[s], gyro.z[s]);
    }

    @Override
    public void imuMagUpdated(Device dev, ImuData mag) {
        for (int s = 0; s < mag.samples; s++)
            dev.addMag(mag.ts, mag.x[s], mag.y[s], mag.z[s]);
    }
    */

    @Override
    public void imuQuatUpdated(Device dev, ImuQuatData quat) {
        float [] q = {(float) quat.q0, (float) quat.q1, (float) quat.q2, (float) quat.q3};
        dev.setQuat(q);
    }

    @Override
    public void emgPwrUpdated(Device dev, EmgPwrData data) {
        dev.setPower(data.ts, data.power[0]);
    }

    @Override
    public Device getDev(BluetoothDevice d) {

        Device dev = new Device();
        dev.setAddress(d.getAddress());
        //dev.setBattery(fullBinding.getBattery(d));
        //dev.setConnectionState(fullBinding.getConnectionLiveState(d));

        return dev;
    }

    private FirebaseGameLogger gameLogger;

    @Override
    public void onServiceConnected() {
        long startTime = new Date().getTime();

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        try {
            mAuth.signInWithCustomToken(getService().getAuthToken())
                    .addOnCanceledListener(() -> Log.d(TAG, "Cancelled"))
                    .addOnCompleteListener(task -> Log.d(TAG, "Completed"));
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        gameLogger = new FirebaseGameLogger(getService(), "Sling Therapy", startTime);
    }

    @Override
    public void onServiceDisconnected() {
        gameLogger.finalize(1.0, "");
    }

    public void onStop() {
        gameLogger.finalize(1.0, "");
    }
}
