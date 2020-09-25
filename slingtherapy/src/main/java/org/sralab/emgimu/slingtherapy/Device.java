package org.sralab.emgimu.slingtherapy;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class Device {

    private final static String TAG = Device.class.getSimpleName();

    private String address;
    public String getAddress() {
        return address;
    }
    public void setAddress(String address) {
        this.address = address;
    }

    private MutableLiveData<float[]> liveQuat = new MutableLiveData<>();
    public LiveData<float []> getQuat() { return liveQuat; }
    public void setQuat(float [] q) { liveQuat.postValue(q); }

    private MutableLiveData<Integer> livePwr = new MutableLiveData<Integer>();
    public void setPower(long ts, Integer power) {
        livePwr.postValue(power);
    }

    public Device() {

    }

}
