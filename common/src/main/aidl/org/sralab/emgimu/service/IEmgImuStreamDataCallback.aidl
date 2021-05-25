package org.sralab.emgimu.service;

parcelable EmgStreamData;

oneway interface IEmgImuStreamDataCallback {
    void handleData(in BluetoothDevice device, in EmgStreamData data);
}
