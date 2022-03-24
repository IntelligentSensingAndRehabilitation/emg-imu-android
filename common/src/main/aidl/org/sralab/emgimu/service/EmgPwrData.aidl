package org.sralab.emgimu.service;

parcelable EmgPwrData {
    long ts;
    int channels;
    int [] power;
    String firmwareVersion;
    double batteryVoltage;
}