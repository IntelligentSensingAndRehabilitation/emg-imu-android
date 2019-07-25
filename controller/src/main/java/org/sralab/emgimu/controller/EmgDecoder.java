package org.sralab.emgimu.controller;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import org.sralab.emgimu.streaming.NetworkStreaming;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class EmgDecoder implements IEmgDecoder {

    private static final String TAG = EmgDecoder.class.getSimpleName();

    private ByteBuffer model;
    private Interpreter interpreter;
    private Interpreter rmsInterpreter;
    private GpuDelegate delegate;
    private Interpreter.Options options;
    private Context context;
    private final String modelSaveFile = "emgDecoder.tflite";

    public EmgDecoder(Context context) {
        this.context = context;
        try {
            // See if there is a save model to default to start with
            model = loadFileModel(modelSaveFile);
        } catch (IOException e) {
            try {
                model = loadAssetModel("model.tflite");
            } catch (IOException e1) {
                throw new RuntimeException("Unable to initialize EMG Decoder TF-Lite model");
            }
        }

        ByteBuffer rmsModel = null;
        try {
            rmsModel = loadAssetModel("rms.tflite");
        } catch (IOException e) {
            throw new RuntimeException("Unable to initialize the EMG Decoder TF-Lite RMS model");
        }
        options = new Interpreter.Options();
        Interpreter.Options rmsOptions = new Interpreter.Options();

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
            if (!test()) {
                throw new RuntimeException("EMG Decoder tests failed");
            }
        }

        interpreter = new Interpreter(model, options);
        rmsInterpreter = new Interpreter(rmsModel, rmsOptions);
    }

    /** Memory-map the model file in Assets. */
    private MappedByteBuffer loadAssetModel(String model) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(model);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /** Memory-map the model in file. */
    private MappedByteBuffer loadFileModel(String model) throws IOException {
        File modelFile = new File(context.getFilesDir(), model);
        FileInputStream inputStream = new FileInputStream(modelFile);
        FileChannel fileChannel = inputStream.getChannel();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length());
    }

    private NetworkStreaming modelStream;
    void setNewModelStream(NetworkStreaming stream) {
        this.modelStream = stream;
    }

    NetworkStreaming.MessageReceiver modelReceiver = msg -> {
        Log.d(TAG, "Received model message. Creating a new interpreter");

        MappedByteBuffer newModel;
        try {
            FileOutputStream fos = context.openFileOutput(modelSaveFile, context.MODE_PRIVATE);
            fos.write(msg);

            newModel = loadFileModel(modelSaveFile);
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

    private static final int WINDOW_LENGTH = 250;
    static final int CHANNELS = 8;
    static final int EMBEDDINGS_SIZE = 2;

    protected ByteBuffer output = null;

    private float [][] floatInputBuffer = new float[WINDOW_LENGTH][CHANNELS];
    private float [] floatOutputBuffer = new float[EMBEDDINGS_SIZE];

    private void writeToInput(float[] data)
    {
        // TODO: consider a larger rolling window to minimized copy shifting
        for (int j = 0; j < CHANNELS; j++) {
            for (int i = 0; i < WINDOW_LENGTH-1; i++) {
                floatInputBuffer[i][j] = floatInputBuffer[i+1][j];
            }
            floatInputBuffer[WINDOW_LENGTH-1][j] = data[j];
        }
    }

    private void readFromOutput(float[] coordinates)
    {
        System.arraycopy(floatOutputBuffer, 0, coordinates, 0, EMBEDDINGS_SIZE);
    }

    private int sample_counter = 0;

    @Override
    public int getChannels() {
        return CHANNELS;
    }

    public boolean decode(float[] data, float[] coordinates)
    {
        if (model == null || interpreter == null) {
            throw new RuntimeException("Attempted to call decoded on uninitialized EMG Decoder model");
        }
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
    void get_rms(float[] rms)
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

    private boolean test() {
        ByteBuffer testModel;
        try {
            testModel = loadAssetModel("test.tflite");
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
