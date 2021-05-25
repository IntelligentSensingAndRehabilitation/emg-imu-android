package org.sralab.emgimu.service;

parcelable EmgPwrData;

oneway interface IEmgImuPwrDataCallback {
    void handleData(in BluetoothDevice device, in EmgPwrData data);
}