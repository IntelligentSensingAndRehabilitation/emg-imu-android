package org.sralab.emgimu.mve;

import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.os.RemoteException;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;

import org.sralab.emgimu.EmgImuViewModel;
import org.sralab.emgimu.logging.FirebaseGameLogger;
import org.sralab.emgimu.logging.GamePlayRecord;
import org.sralab.emgimu.service.EmgPwrData;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Date;

public class DeviceViewModel extends EmgImuViewModel<Device> {

    private final static String TAG = DeviceViewModel.class.getSimpleName();
    private FirebaseGameLogger gameLogger;

    @Override
    public boolean getObservePwr() { return true; }

    @Override
    public boolean getObserveStream() { return true; }

    MutableLiveData<Integer> range = new MutableLiveData<>(1000);
    public void setRange(Integer range) {
        Log.d("Kevin", "Setting range to: " + range);
        this.range.postValue(range);
    }
    public LiveData<Integer> getRange() { return range; }
    private class MvcSensor {
        String address;
        int channel;
        float maximum;
        float minimum;
    }

    private class MvcTrial {
        long timestamp;
        ArrayList<MvcSensor> sensors;
    }

    ArrayList <MvcTrial> trials;

    GamePlayRecord gameRecord;

    public DeviceViewModel(Application app) {
        super(app);

        trials = new ArrayList<>();
    }

    @Override
//    public void emgPwrUpdated(Device dev, EmgPwrData data) {
//        dev.setPower(data.power[0]);
//    }
    public void emgPwrUpdated(Device dev, EmgPwrData data) {
        dev.setPower(data.power);
/*        Log.d(TAG, "DeviceViewModel, emgPwrUpdated --> data.power[0] = " + data.power[0]
                + "| data.power[1] = " + data.power[1]);
        Log.d(TAG, "DeviceViewModel, data.power.length=" + data.power.length);*/
    }


    @Override
    public Device getDev(BluetoothDevice d) {
        Device dev = new Device();
        dev.setAddress(d.getAddress());
        return dev;
    }

    public void reset() {
        for (final Device d : getDevicesLiveData().getValue()) { d.reset(); }
        // Reset the range in DeviceViewModel after resetting all devices
        setRange(1000); // Replace 1000 with the desired default range value
    }

    public void saveMvc(Device d, int channel) {

/*        Log.d(TAG, "dvm, Save MVC button was pressed");
        Log.d(TAG, "dvm, called saveMvc() | channelNumber =  " + channel);
        Log.d(TAG, "dvm, called saveMvc() | device =  " + d.getAddress());*/
        MvcTrial trial = new MvcTrial();
        trial.timestamp = new Date().getTime();
        trial.sensors = new ArrayList<>();

        MvcSensor sensor = new MvcSensor();

        sensor.address = d.getAddress();
/*        Log.d(TAG, "dvm, sensor.address = " + sensor.address);*/
        sensor.channel = channel;
        sensor.maximum = d.getMaximumTwoChannel()[channel].getValue().floatValue();
        sensor.minimum = d.getMinimumTwoChannel()[channel].getValue().floatValue();
        trial.sensors.add(sensor);
/*        Log.d(TAG, "dvm, MVC Trial Data -->"
                                    + " address=" + sensor.address
                                    + " | channel=" + sensor.channel
                                    + " | max=" + sensor.maximum
                                    + " | min=" + sensor.maximum);*/

        trials.add(trial);
        Gson gson = new Gson();
        String json = gson.toJson(trials);

        gameRecord.setDetails(json);
        gameRecord.setStopTime(new Date().getTime());
        gameRecord.setPerformance(gameRecord.getPerformance() + 1);
        gameLogger.writeRecord(gameRecord);

        try {
            gameRecord.setLogReference(getService().getLoggingReferences());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        gameLogger = new FirebaseGameLogger(getService());

        gameRecord = new GamePlayRecord();
        gameRecord.setName("MaxEMGActivation");
        gameRecord.setStartTime(new Date().getTime());

    }
}
