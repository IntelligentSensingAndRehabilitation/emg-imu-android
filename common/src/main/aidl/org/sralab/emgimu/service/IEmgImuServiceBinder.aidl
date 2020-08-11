// IEmgImuServiceBinder.aidl
package org.sralab.emgimu.service;

// Declare any non-default types here with import statements

//import IEmgImuDataCallback;

parcelable DataParcel;

oneway interface IEmgImuDataCallback {
    void handleData(in BluetoothDevice device, long ts, in DataParcel data);
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
    void registerEmgStreamObserver(IEmgImuDataCallback callback);
    void unregisterEmgStreamObserver(IEmgImuDataCallback callback);
    void registerEmgPwrObserver(IEmgImuDataCallback callback);
    void unregisterEmgPwrObserver(IEmgImuDataCallback callback);

}
