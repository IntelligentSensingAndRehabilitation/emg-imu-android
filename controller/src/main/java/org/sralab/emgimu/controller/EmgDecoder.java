package org.sralab.emgimu.controller;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class EmgDecoder {

    protected MappedByteBuffer model;
    protected Interpreter interpreter;
    protected GpuDelegate delegate;

    public EmgDecoder(Activity activity) throws IOException
    {
        model = loadModelFile(activity);
        Interpreter.Options options = new Interpreter.Options();

        if (false) {
            // TODO: right now this causes an error
            // E/libEGL: call to OpenGL ES API with no current context (logged once per thread)
            // A/libc: Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR), fault addr 0x100000007

            // Initialize interpreter with GPU delegate
            delegate = new GpuDelegate();
            options.addDelegate(delegate);
        } else {
            options.setUseNNAPI(true);
        }

        options.setNumThreads(2);

        interpreter = new Interpreter(model, options);
    }

    private String getModelPath() {
        return "model.tflite";
    }

    /** Memory-map the model file in Assets. */
    private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(getModelPath());
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        if (delegate != null) {
            delegate.close();
            delegate = null;
        }
        model = null;
    }

    private final int WINDOW_LENGTH = 50;
    public static final int CHANNELS = 8;
    public static final int EMBEDDINGS_SIZE = 2;

    protected ByteBuffer output = null;

    float [][] floatInputBuffer = new float[WINDOW_LENGTH][CHANNELS];
    float [] floatOutputBuffer = new float[EMBEDDINGS_SIZE];

    public boolean writeToInput(float [] data)
    {
        // TODO: consider a larger rolling window to minimized copy shifting
        for (int j = 0; j < CHANNELS; j++) {
            for (int i = 0; i < WINDOW_LENGTH-1; i++) {
                floatInputBuffer[i][j] = floatInputBuffer[i+1][j];
            }
            floatInputBuffer[WINDOW_LENGTH-1][j] = data[j];
        }

        return true;
    }

    public boolean readFromOutput(float [] coordinates)
    {
        for (int i = 0; i < EMBEDDINGS_SIZE; i++)
            coordinates[i] = floatOutputBuffer[i];

        return true;
    }

    public boolean decode(float [] data, float [] coordinates)
    {
        writeToInput(data);
        interpreter.run(floatInputBuffer, floatOutputBuffer);
        readFromOutput(coordinates);

        return true;
    }
}
