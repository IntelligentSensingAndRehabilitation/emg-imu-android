package org.sralab.emgimu.service;

oneway interface IEmgImuBatCallback {
    void handleData(in BluetoothDevice device, in float battery);
}