package org.sralab.emgimu.service;

parcelable ImuQuatData;

oneway interface IEmgImuQuatCallback {
    void handleData(in BluetoothDevice device, in ImuQuatData data);
}