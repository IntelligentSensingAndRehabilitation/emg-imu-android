package org.sralab.emgimu.controller;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;

@RunWith(RobolectricTestRunner.class)
public class UnitTest {

    private Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void emgDecoderLoad() throws IOException {
        EmgDecoder emgDecoder = new EmgDecoder(context);

        float zeros[] = new float[8];
        for (int i = 0; i < 8; i++)
            zeros[i] = 0;
        float coordinates[] = new float[2];

        emgDecoder.decode(zeros, coordinates);
    }
}