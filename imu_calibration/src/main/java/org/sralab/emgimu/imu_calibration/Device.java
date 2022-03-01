package org.sralab.emgimu.imu_calibration;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.graphics.Bitmap;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.sralab.emgimu.service.EmgImuManager;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.service.IEmgImuSenseCallback;
import org.sralab.emgimu.service.ImuData;

import java.util.List;

public class Device implements EmgImuManager.CalibrationListener{

    private final static String TAG = Device.class.getSimpleName();

    private EmgImuService.EmgImuBinder service;
    private BluetoothDevice dev;

    public String getAddress() {
        return dev.getAddress();
    }

    private MutableLiveData<Bitmap> image;
    public void setImage(Bitmap image) { this.image.postValue(image); }
    public LiveData<Bitmap> getImage() { return this.image; }

    public MutableLiveData<String> status = new MutableLiveData<>("");
    public void setStatus(String status) { this.status.postValue(status); }
    public LiveData<String> getStatus() { return status; }

    private LiveData<Integer> connectionState = new MutableLiveData<>(new Integer(0));
    public LiveData<Integer> getConnectionState() { return connectionState; }
    public void setConnectionState(LiveData<Integer> connectionState) {
        this.connectionState = connectionState;
        if (connectionState.getValue() == BluetoothGatt.STATE_CONNECTED)
            calibrationStatus.postValue(CalibrationStatus.DISCONNECTED);
        else
            calibrationStatus.postValue(CalibrationStatus.IDLE);
    }

    enum CalibrationStatus {DISCONNECTED, IDLE, STARTED, FINISHED};
    private MutableLiveData<CalibrationStatus> calibrationStatus = new MutableLiveData<>(CalibrationStatus.DISCONNECTED);
    public LiveData<CalibrationStatus> getCalibrationStatus() { return calibrationStatus; }

    public void startCalibration() {
        setStatus("Zeroing initial calibration...");
        calibrationStatus.postValue(CalibrationStatus.STARTED);
        service.startCalibration(dev, this);
    }

    public void finishCalibration() {
        service.unregisterImuAccelObserver(null, accelHandler);
        service.unregisterImuMagObserver(null, magHandler);
        calibrationStatus.postValue(CalibrationStatus.FINISHED);
        service.finishCalibration(dev, this);
    }

    public Device (BluetoothDevice dev, EmgImuService.EmgImuBinder service)
    {
        this.service = service;
        this.dev = dev;
        image = new MutableLiveData<>(Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888));
    }

    @Override
    public void onUploading() {
        if (calibrationStatus.getValue() == CalibrationStatus.FINISHED) {
            setStatus("Uploading...");
        }
    }

    @Override
    public void onComputing() {
        if (calibrationStatus.getValue() == CalibrationStatus.FINISHED)
            setStatus("Computing...");
    }

    @Override
    public void onReceivedCal(List<Float> Ainv, List<Float> b, float len_var, List<Float> angles) {
        if (calibrationStatus.getValue() == CalibrationStatus.FINISHED)
            setStatus(String.format("Completed. Length error %.1f%%. Angles <%.1f, %.1f, %.1f>",
                100 * Math.sqrt(len_var),
                angles.get(0), angles.get(1), angles.get(2)));
    }

    @Override
    public void onReceivedIm(Bitmap im) {
        if (calibrationStatus.getValue() == CalibrationStatus.FINISHED) {
            setImage(im);
            calibrationStatus.postValue(CalibrationStatus.IDLE);
        }
    }

    @Override
    public void onSent() {
        // Zeroed calibration sent to device. Proceed with calibration.
        if (calibrationStatus.getValue() == CalibrationStatus.STARTED) {
            setStatus("Collecting. Please rotate sensor.");
            service.registerImuAccelObserver(null, accelHandler);
            service.registerImuMagObserver(null, magHandler);
        }
    }

    @Override
    public void onError(String msg) {
        setStatus("Error during calibration.");
    }

    static class CallbackHandler extends IEmgImuSenseCallback.Stub {

        String handler;
        public CallbackHandler(String s)
        {
            handler = s;
        }

        @Override
        public void handleData(BluetoothDevice device, ImuData data)  {
            Log.v(TAG, handler + " received " + data);
        }
    }

    private final CallbackHandler magHandler = new CallbackHandler("Mag");
    private final CallbackHandler accelHandler = new CallbackHandler("Accel");
}
