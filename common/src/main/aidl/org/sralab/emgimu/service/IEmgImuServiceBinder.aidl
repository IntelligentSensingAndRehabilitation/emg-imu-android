// IEmgImuServiceBinder.aidl
package org.sralab.emgimu.service;

// Declare any non-default types here with import statements

//import IEmgImuDataCallback;

parcelable DataParcel;

oneway interface IEmgImuDataCallback {
    void handleData(in BluetoothDevice device, long ts, in DataParcel data);
}

interface IEmgImuServiceBinder  {

    List<BluetoothDevice> getManagedDevices();
    void connect(in BluetoothDevice device);
    void disconnect(in BluetoothDevice device);
    boolean isConnected(in BluetoothDevice device);
    boolean isReady(in BluetoothDevice device);
    int getConnectionState(in BluetoothDevice device);
    void setActivityIsChangingConfiguration(boolean changing);

    int getLoggerProfileTitle();

    void setPwrRange(in BluetoothDevice device, float min, float max);
    void setClickThreshold(in BluetoothDevice device, float min, float max);
    float getClickThreshold(in BluetoothDevice device);
    float getMinPwr(in BluetoothDevice device);
    float getMaxPwr(in BluetoothDevice device);
    int getEmgPwrValue(in BluetoothDevice device);
    double getBattery(in BluetoothDevice device);
    void streamPwr(in BluetoothDevice device);
    String getUser();

    void updateSavedDevices();

    void registerEmgStreamObserver(IEmgImuDataCallback callback);
    void unregisterEmgStreamObserver(IEmgImuDataCallback callback);
    void registerEmgPwrObserver(IEmgImuDataCallback callback);
    void unregisterEmgPwrObserver(IEmgImuDataCallback callback);

}
