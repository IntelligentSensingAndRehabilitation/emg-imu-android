package org.sralab.emgimu.controller;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.sralab.emgimu.EmgImuBaseActivity;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.streaming.NetworkStreaming;
import org.sralab.emgimu.visualization.VectorGraphView;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Learning and visualization of a neural network that maps an array of EMG
 * activity into control signals. This is comprised of several components
 * both locally and in the cloud.
 *
 * EmgDecoder - wrapper class for the TensorFlow Lite model that maps EMG data
 * to control signals.
 *
 * GameController - model and controller of the game chase signal
 *
 * GameView - simple cursor visualization
 *
 * This system works by streaming the EMG and chase coordinate data to a server
 * or the cloud where a model will be trained to perform this mapping. The model
 * will be periodically distilled and sent from the server back to this activity
 * to refine control.
 */
public class LearningGameActivity extends EmgImuBaseActivity {

    private static final String TAG = LearningGameActivity.class.getSimpleName();

    private EmgDecoder emgDecoder = null;

    private VectorGraphView inputGraph;
    private VectorGraphView outputGraph;

    private GameView gameView;
    private GameController gameController = new GameController();

    private Timer gameTimer;

    private NetworkStreaming networkStreaming;

    private String ip_address = "192.168.43.73";
    private int port = 5000;

    @Override
    protected void onCreateView(Bundle savedInstanceState) {
        setContentView(R.layout.activity_learning_game);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ViewGroup decoder_inputs = findViewById(R.id.decoder_inputs);
        inputGraph = new VectorGraphView(decoder_inputs.getContext(), decoder_inputs, EmgDecoder.CHANNELS);
        inputGraph.setWindowSize(250);
        //inputGraph.setRange(10);

        ViewGroup decoder_outputs = findViewById(R.id.decoder_outputs);
        outputGraph = new VectorGraphView(decoder_outputs.getContext(), decoder_outputs, EmgDecoder.EMBEDDINGS_SIZE);
        outputGraph.setWindowSize(250);
        //outputGraph.setRange(10);

        gameView = findViewById(R.id.game_view);

        // Do nothing for now but keep for later
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> Log.d(TAG, "Click"));

        try {
            emgDecoder = new EmgDecoder(this);
        } catch (Exception e) {
            Log.e(TAG, "Error creating TF Lite model.", e);
        }

        // Set up periodic timer that updates the game model/controller then
        // then the view
        gameTimer = new Timer();
        gameTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    gameController.update(0.0f, 0.0f);
                    gameView.setGoalCoordinate(gameController.getGoalX(), gameController.getGoalY());
                });

                if (networkStreaming != null && networkStreaming.isConnected()) {
                    networkStreaming.streamTrackingXY(gameController.getGoalX(), gameController.getGoalY());
                }
            }
        }, 0, 25);

    }

    @Override
    protected void onPause() {
        super.onPause();
        gameTimer.cancel();
    }

    @Override
    public void onDeviceReady(BluetoothDevice device) {
        Log.d(TAG, "Device ready: " + device);
        getService().streamBuffered(device);

        networkStreaming = getService().getNetworkStreaming();
        networkStreaming.start(ip_address,port);
        emgDecoder.setNewModelStream(networkStreaming);
    }

    @Override
    protected void onServiceBinded(EmgImuService.EmgImuBinder binder) {

    }

    @Override
    protected void onServiceUnbinded() {

    }

    @Override
    protected int getAboutTextId() {
        return 0;
    }

    @Override
    public void onBatteryReceived(BluetoothDevice device, float battery) {

    }

    @Override
    public void onEmgBuffReceived(BluetoothDevice device, int count, double[][] data) {

        float coordinates[] = new float[EmgDecoder.EMBEDDINGS_SIZE];

        int CHANNELS = data.length;
        final int SAMPLES = data[0].length;

        /*if (BuildConfig.DEBUG && CHANNELS != EmgDecoder.CHANNELS) {
            Log.e(TAG, "Size of data not compatible with model type");
            return;
        }*/

        float input_data[] = new float[CHANNELS];

        for (int i = 0; i < SAMPLES; i++) {

            for (int j = 0; j < CHANNELS; j++) {
                input_data[j]= (float) data[0][i];
            }

            boolean res = emgDecoder.decode(input_data, coordinates);
            if (res) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Output: " + Arrays.toString(coordinates));
                    gameView.setOutputCoordinate(coordinates[0], coordinates[1]);
                    outputGraph.addValue(coordinates);
                    outputGraph.repaint();
                });
            }
            inputGraph.addValue(input_data);

        }

        runOnUiThread(() -> inputGraph.repaint());
    }

    @Override
    public void onImuAccelReceived(BluetoothDevice device, float[][] accel) {

    }

    @Override
    public void onImuGyroReceived(BluetoothDevice device, float[][] gyro) {

    }

    @Override
    public void onImuMagReceived(BluetoothDevice device, float[][] mag) {

    }

    @Override
    public void onImuAttitudeReceived(BluetoothDevice device, float[] quaternion) {

    }

    @Override
    public void onDeviceConnected(@NonNull BluetoothDevice device) {

    }

    @Override
    public void onDeviceDisconnected(@NonNull BluetoothDevice device) {

    }

    @Override
    public void onLinkLossOccurred(@NonNull BluetoothDevice device) {

    }

    @Override
    public void onBondingFailed(@NonNull BluetoothDevice device) {

    }
}
