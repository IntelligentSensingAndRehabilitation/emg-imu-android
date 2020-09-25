// IEmgImuServiceBinder.aidl
package org.sralab.emgimu.service;

// Declare any non-default types here with import statements

oneway interface IEmgImuDevicesUpdatedCallback {
    void onDeviceListUpdated();
}

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

parcelable ImuData {
    long ts;
    float [] x;
    float [] y;
    float [] z;
    int samples;
}

oneway interface IEmgImuSenseCallback {
    void handleData(in BluetoothDevice device, in ImuData data);
}

parcelable ImuQuatData {
    long ts;
    double q0;
    double q1;
    double q2;
    double q3;
}

oneway interface IEmgImuQuatCallback {
    void handleData(in BluetoothDevice device, in ImuQuatData data);
}

interface IEmgImuServiceBinder  {

    // User management
    String getUser();

    // Device management
    List<BluetoothDevice> getManagedDevices();
    void connectDevice(in BluetoothDevice device);
    void disconnectDevice(in BluetoothDevice device);
    int getConnectionState(in BluetoothDevice device);
    void registerDevicesObserver(IEmgImuDevicesUpdatedCallback callback);
    void unregisterDevicesObserver(IEmgImuDevicesUpdatedCallback callback);
    List<String> getLoggingReferences();
    String getAuthToken();
    // boolean isConnected(in BluetoothDevice device);
    // boolean isReady(in BluetoothDevice device);

    // For receiving EMG data
    void registerEmgStreamObserver(IEmgImuStreamDataCallback callback);
    void unregisterEmgStreamObserver(IEmgImuStreamDataCallback callback);
    void registerEmgPwrObserver(IEmgImuPwrDataCallback callback);
    void unregisterEmgPwrObserver(IEmgImuPwrDataCallback callback);

    // For receiving IMU data
    void registerImuAccelObserver(IEmgImuSenseCallback callback);
    void unregisterImuAccelObserver(IEmgImuSenseCallback callback);
    void registerImuGyroObserver(IEmgImuSenseCallback callback);
    void unregisterImuGyroObserver(IEmgImuSenseCallback callback);
    void registerImuMagObserver(IEmgImuSenseCallback callback);
    void unregisterImuMagObserver(IEmgImuSenseCallback callback);
    void registerImuQuatObserver(IEmgImuQuatCallback callback);
    void unregisterImuQuatObserver(IEmgImuQuatCallback callback);
}
