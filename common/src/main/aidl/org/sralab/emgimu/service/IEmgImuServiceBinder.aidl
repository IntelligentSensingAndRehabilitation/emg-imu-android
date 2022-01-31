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
    void registerEmgStreamObserver(IEmgImuStreamDataCallback callback);
    void unregisterEmgStreamObserver(IEmgImuStreamDataCallback callback);
    void registerEmgPwrObserver(String regDevice, IEmgImuPwrDataCallback callback);
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

    // For receiving battery data
    void registerBatObserver(IEmgImuBatCallback callback);
    void unregisterBatObserver(IEmgImuBatCallback callback);
}
