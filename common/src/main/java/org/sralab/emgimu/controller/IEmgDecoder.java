package org.sralab.emgimu.controller;

import android.content.Context;

public interface IEmgDecoder {
    int getChannels();
    boolean initialize(Context context);
    boolean decode(float[] data, float[] coordinates);
}
