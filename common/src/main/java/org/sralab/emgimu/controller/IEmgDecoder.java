package org.sralab.emgimu.controller;

public interface IEmgDecoder {
    int getChannels();
    boolean decode(float[] data, float[] coordinates);
}
