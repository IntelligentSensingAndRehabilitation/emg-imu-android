package org.sralab.emgimu.controller;

import android.content.Context;

//! Helper method to construct an EmgDecoder with context
public interface IEmgDecoderProvider {
    IEmgDecoder get(Context context);
}
