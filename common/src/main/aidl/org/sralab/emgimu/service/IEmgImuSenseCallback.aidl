package org.sralab.emgimu.service;

parcelable ImuData;

oneway interface IEmgImuSenseCallback {
    void handleData(in BluetoothDevice device, in ImuData data);
}
