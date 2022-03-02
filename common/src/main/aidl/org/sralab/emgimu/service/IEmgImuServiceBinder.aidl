// IEmgImuServiceBinder.aidl
package org.sralab.emgimu.service;


import org.sralab.emgimu.service.IEmgImuDevicesUpdatedCallback;
import org.sralab.emgimu.service.IEmgImuStreamDataCallback;
import org.sralab.emgimu.service.IEmgImuPwrDataCallback;
import org.sralab.emgimu.service.IEmgImuSenseCallback;
import org.sralab.emgimu.service.IEmgImuQuatCallback;
import org.sralab.emgimu.service.IEmgImuBatCallback;

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
    void storeGameplayRecord(String name, long startTime, in String details);
    List<String> getLoggingReferences();
    String getAuthToken();
    // boolean isConnected(in BluetoothDevice device);

    // For receiving EMG data
    void registerEmgStreamObserver(in BluetoothDevice regDevice, IEmgImuStreamDataCallback callback);
    void unregisterEmgStreamObserver(in BluetoothDevice regDevice, IEmgImuStreamDataCallback callback);
    void registerEmgPwrObserver(in BluetoothDevice regDevice, IEmgImuPwrDataCallback callback);
    void unregisterEmgPwrObserver(in BluetoothDevice regDevice, IEmgImuPwrDataCallback callback);

    // For receiving IMU data
    void registerImuAccelObserver(in BluetoothDevice regDevice, IEmgImuSenseCallback callback);
    void unregisterImuAccelObserver(in BluetoothDevice regDevice, IEmgImuSenseCallback callback);
    void registerImuGyroObserver(in BluetoothDevice regDevice, IEmgImuSenseCallback callback);
    void unregisterImuGyroObserver(in BluetoothDevice regDevice, IEmgImuSenseCallback callback);
    void registerImuMagObserver(in BluetoothDevice regDevice, IEmgImuSenseCallback callback);
    void unregisterImuMagObserver(in BluetoothDevice regDevice, IEmgImuSenseCallback callback);
    void registerImuQuatObserver(in BluetoothDevice regDevice, IEmgImuQuatCallback callback);
    void unregisterImuQuatObserver(in BluetoothDevice regDevice, IEmgImuQuatCallback callback);

    // For receiving battery data
    void registerBatObserver(in BluetoothDevice regDevice, IEmgImuBatCallback callback);
    void unregisterBatObserver(in BluetoothDevice regDevice, IEmgImuBatCallback callback);
}
