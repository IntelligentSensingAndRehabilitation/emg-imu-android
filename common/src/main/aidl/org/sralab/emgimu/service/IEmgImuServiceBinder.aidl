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
    void registerEmgStreamObserver(String regDevice, IEmgImuStreamDataCallback callback);
    void unregisterEmgStreamObserver(String regDevice, IEmgImuStreamDataCallback callback);
    void registerEmgPwrObserver(String regDevice, IEmgImuPwrDataCallback callback);
    void unregisterEmgPwrObserver(String regDevice, IEmgImuPwrDataCallback callback);

    // For receiving IMU data
    void registerImuAccelObserver(String regDevice, IEmgImuSenseCallback callback);
    void unregisterImuAccelObserver(String regDevice, IEmgImuSenseCallback callback);
    void registerImuGyroObserver(String regDevice, IEmgImuSenseCallback callback);
    void unregisterImuGyroObserver(String regDevice, IEmgImuSenseCallback callback);
    void registerImuMagObserver(String regDevice, IEmgImuSenseCallback callback);
    void unregisterImuMagObserver(String regDevice, IEmgImuSenseCallback callback);
    void registerImuQuatObserver(String regDevice, IEmgImuQuatCallback callback);
    void unregisterImuQuatObserver(String regDevice, IEmgImuQuatCallback callback);

    // For receiving battery data
    void registerBatObserver(String regDevice, IEmgImuBatCallback callback);
    void unregisterBatObserver(String regDevice, IEmgImuBatCallback callback);
}
