// IEmgImuServiceBinder.aidl
package org.sralab.emgimu.service;

// Declare any non-default types here with import statements

parcelable EmgStreamData {
    long ts;
    int channels;
    int samples;
    int Fs;
    double [] voltage;
}

oneway interface IEmgImuStreamDataCallback {
    void handleData(in BluetoothDevice device, in EmgStreamData data);
}

parcelable EmgPwrData {
    long ts;
    int channels;
    int [] power;
}

oneway interface IEmgImuPwrDataCallback {
    void handleData(in BluetoothDevice device, in EmgPwrData data);
}

interface IEmgImuServiceBinder  {

    // User management
    String getUser();

    // Device management
    List<BluetoothDevice> getManagedDevices();
    void connectDevice(in BluetoothDevice device);
    void disconnectDevice(in BluetoothDevice device);
    int getConnectionState(in BluetoothDevice device);
    // boolean isConnected(in BluetoothDevice device);
    // boolean isReady(in BluetoothDevice device);

    // For receiving data
    void registerEmgStreamObserver(IEmgImuStreamDataCallback callback);
    void unregisterEmgStreamObserver(IEmgImuStreamDataCallback callback);
    void registerEmgPwrObserver(IEmgImuPwrDataCallback callback);
    void unregisterEmgPwrObserver(IEmgImuPwrDataCallback callback);

}
