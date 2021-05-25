package org.sralab.emgimu.service;

parcelable ImuData {
    long ts;
    float [] x;
    float [] y;
    float [] z;
    int samples;
}