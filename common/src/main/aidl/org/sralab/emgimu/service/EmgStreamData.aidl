package org.sralab.emgimu.service;

parcelable EmgStreamData {
    long ts;
    int channels;
    int samples;
    int Fs;
    double [] voltage;
    double batteryVoltage;
}