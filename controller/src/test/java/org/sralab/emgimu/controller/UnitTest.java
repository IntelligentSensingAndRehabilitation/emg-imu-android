package org.sralab.emgimu.controller;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import android.content.Context;
import java.io.IOException;

@RunWith(MockitoJUnitRunner.class)
public class UnitTest {

    @Mock
    Context mockContext;

    @Test
    public void emgDecoderLoad() throws IOException {
        EmgDecoder emgDecoder = new EmgDecoder(mockContext);

        float zeros[] = new float[8];
        for (int i = 0; i < 8; i++)
            zeros[i] = 0;
        float coordinates[] = new float[2];

        emgDecoder.decode(zeros, coordinates);
    }
}