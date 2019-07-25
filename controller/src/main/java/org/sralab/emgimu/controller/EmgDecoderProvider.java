package org.sralab.emgimu.controller.telepresence;

import android.content.Context;

import org.sralab.emgimu.controller.EmgDecoder;
import org.sralab.emgimu.controller.IEmgDecoderProvider;

public class EmgDecoderProvider implements IEmgDecoderProvider {

    public EmgDecoder get(Context context) {
        return new EmgDecoder(context);
    }

}
