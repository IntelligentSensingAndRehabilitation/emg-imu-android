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
import java.util.Arrays;

public class EmgDecoder {

    private static final String TAG = EmgDecoder.class.getSimpleName();

    protected ByteBuffer model;
    protected Interpreter interpreter;
    protected ByteBuffer rmsModel;
    protected Interpreter rmsInterpreter;
    protected GpuDelegate delegate;
    protected Interpreter.Options options;
    protected Interpreter.Options rmsOptions;

    public EmgDecoder(Activity activity) throws IOException
    {
        model = loadModelFile(activity, "model.tflite");
        rmsModel = loadModelFile(activity, "rms.tflite");
        options = new Interpreter.Options();
        rmsOptions = new Interpreter.Options();

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

        if (BuildConfig.DEBUG) {
            if (!test(activity)) {
                throw new RuntimeException("TF Lite tests failed");
            }
        }

        interpreter = new Interpreter(model, options);
        rmsInterpreter = new Interpreter(rmsModel, rmsOptions);
    }

    private String getModelPath() {
        return "model.tflite";
    }

    /** Memory-map the model file in Assets. */
    private MappedByteBuffer loadModelFile(Activity activity, String model) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(model);
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

        modelStream.checkForMessage(modelReceiver); // modelReceiver
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
        if (rmsInterpreter != null) {
            rmsInterpreter.close();
            rmsInterpreter = null;
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

    private void centerInput() {
        for (int i = 0; i < CHANNELS; i++) {
            float mean = 0;
            for (int j = 0; j < WINDOW_LENGTH; j++) {
                mean += floatInputBuffer[j][i];
            }
            mean /= WINDOW_LENGTH;
            for (int j = 0; j < WINDOW_LENGTH; j++) {
                floatInputBuffer[j][i] -= mean;
            }
        }
    }

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
            //centerInput();
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
            updateModel();
        }

        return false;
    }

    float rmsOutputBuffer[] = new float[CHANNELS];
    public void get_rms(float [] rms)
    {
        rmsInterpreter.run(floatInputBuffer, rmsOutputBuffer);
        for(int i = 0; i < CHANNELS; i++)
            rms[i] = rmsOutputBuffer[i];
    }

    private void zeroInputs() {
        for (int i = 0; i < WINDOW_LENGTH; i++)
            for (int j = 0; j < CHANNELS; j++)
                floatInputBuffer[i][j] = 0;
    }

    boolean test(Activity activity) {
        ByteBuffer testModel;
        try {
            testModel = loadModelFile(activity, "test.tflite");
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        Interpreter testInterpreter = new Interpreter(testModel, options);

        boolean pass = true;
        zeroInputs();
        testInterpreter.run(floatInputBuffer, floatOutputBuffer);
        pass = pass && floatOutputBuffer[0] == 0 && floatOutputBuffer[1] == 0;
        Log.d(TAG, "Test 1: " + floatOutputBuffer[0] + " " + floatOutputBuffer[1]);

        zeroInputs();
        floatInputBuffer[0][0] = 1.0f;
        testInterpreter.run(floatInputBuffer, floatOutputBuffer);
        pass = pass && floatOutputBuffer[0] == 0 && floatOutputBuffer[1] == 1;
        Log.d(TAG, "Test 2: " + floatOutputBuffer[0] + " " + floatOutputBuffer[1]);

        zeroInputs();
        floatInputBuffer[0][CHANNELS-1] = 1.0f;
        testInterpreter.run(floatInputBuffer, floatOutputBuffer);
        pass = pass && floatOutputBuffer[0] == 7 && floatOutputBuffer[1] == -6;
        Log.d(TAG, "Test 3: " + floatOutputBuffer[0] + " " + floatOutputBuffer[1]);

        zeroInputs();
        floatInputBuffer[WINDOW_LENGTH-1][0] = 1.0f;
        testInterpreter.run(floatInputBuffer, floatOutputBuffer);
        pass = pass && floatOutputBuffer[0] == 1992 && floatOutputBuffer[1] == -1991;
        Log.d(TAG, "Test 4: " + floatOutputBuffer[0] + " " + floatOutputBuffer[1]);

        zeroInputs();
        floatInputBuffer[WINDOW_LENGTH-1][CHANNELS-1] = 1.0f;
        testInterpreter.run(floatInputBuffer, floatOutputBuffer);
        pass = pass && floatOutputBuffer[0] == 1999 && floatOutputBuffer[1] == -1998;
        Log.d(TAG, "Test 5: " + floatOutputBuffer[0] + " " + floatOutputBuffer[1]);

        testInterpreter.close();

        return pass;
    }
}
