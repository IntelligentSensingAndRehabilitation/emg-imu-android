package org.sralab.emgimu.controller;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.sralab.emgimu.streaming.NetworkStreaming;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class EmgDecoder {

    private static final String TAG = EmgDecoder.class.getSimpleName();

    protected ByteBuffer model;
    protected Interpreter interpreter;
    protected GpuDelegate delegate;
    protected Interpreter.Options options;

    private static final int MODEL_SIZE = 32064;

    public EmgDecoder(Activity activity) throws IOException
    {
        model = loadModelFile(activity);
        options = new Interpreter.Options();

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

    private NetworkStreaming modelStream;
    void setNewModelStream(NetworkStreaming stream) {
        this.modelStream = stream;
    }

    NetworkStreaming.MessageReceiver modelReceiver = msg -> {
        Log.d(TAG, "Received model message. Creating a new interpreter");

        if (msg.length != MODEL_SIZE) {
            Log.e(TAG, "Wrong model size received");
            return;
        }

        /* This should work but doesn't seem to */
        /*ByteBuffer newModel = ByteBuffer.allocate(msg.length);
        newModel.order(ByteOrder.nativeOrder());
        newModel.put(msg);*/

        MappedByteBuffer newModel;
        try {
            File tempFile = File.createTempFile("model", "tflite", null);
            FileOutputStream fos = new FileOutputStream(tempFile);
            fos.write(msg);

            FileInputStream inputStream = null;
            inputStream = new FileInputStream(tempFile);

            FileChannel fileChannel = inputStream.getChannel();
            newModel = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, msg.length);

            Interpreter newInterpreter = new Interpreter(newModel, options);

            synchronized (this) {
                interpreter.close();
                model = newModel;
                interpreter = newInterpreter;

                Log.d(TAG, "New interpreter installed");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    };

    private void updateModel() {
        if (modelStream == null)
            return;

        modelStream.checkForMessage(MODEL_SIZE, modelReceiver); // modelReceiver
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

    public static final int WINDOW_LENGTH = 250;
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

    private int sample_counter = 0;
    public boolean decode(float [] data, float [] coordinates)
    {
        writeToInput(data);

        if (sample_counter++ % 50 == 0) {
            if (interpreter != null) {
                synchronized (this) {
                    interpreter.run(floatInputBuffer, floatOutputBuffer);
                }
                readFromOutput(coordinates);
                return true;
            } else {
                return false;
            }
        } else if (sample_counter % 50 == 2) {
            Log.d(TAG, "Attempting to update model");
            updateModel();
        }

        return false;
    }
}
