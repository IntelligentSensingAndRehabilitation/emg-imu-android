package org.sralab.emgimu.controller;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.sralab.emgimu.EmgImuBaseActivity;
import org.sralab.emgimu.logging.FirebaseGameLogger;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.streaming.NetworkStreaming;
import org.sralab.emgimu.visualization.VectorGraphView;

import java.util.Arrays;
import java.util.Date;
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

    private FirebaseGameLogger mGameLogger;

    //! Track the most recent coordinates
    private float coordinates[] = new float[EmgDecoder.EMBEDDINGS_SIZE];

    private Timer gameTimer;

    private NetworkStreaming networkStreaming;

    private String ip_address = "10.30.155.96";
    private int port = 5000;
    private final float RMS_SPACING = 10;

    @Override
    protected void onCreateView(Bundle savedInstanceState) {
        setContentView(R.layout.activity_learning_game);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        emgDecoder = new EmgDecoder(this);

        ViewGroup decoder_inputs = findViewById(R.id.decoder_inputs);
        inputGraph = new VectorGraphView(decoder_inputs.getContext(), decoder_inputs, EmgDecoder.CHANNELS);
        inputGraph.setWindowSize(250);
        inputGraph.setRange(RMS_SPACING * EmgDecoder.CHANNELS / 2);

        ViewGroup decoder_outputs = findViewById(R.id.decoder_outputs);
        outputGraph = new VectorGraphView(decoder_outputs.getContext(), decoder_outputs, EmgDecoder.EMBEDDINGS_SIZE);
        outputGraph.setWindowSize(250);
        //outputGraph.setRange(10);

        gameView = findViewById(R.id.game_view);
        gameView.setShowOutput(false);
        gameView.setShowGoal(true);
        gameView.setOnClickListener(v -> gameView.toggleShowOutput() );

        // Do nothing for now but keep for later
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> gameController.toggleMode());

        // Set up periodic timer that updates the game model/controller then
        // then the view
        final int dt_ms = 20;
        gameTimer = new Timer();
        gameTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(() -> {
                    gameController.update(coordinates[0], coordinates[1], dt_ms);
                    gameView.setOutputCoordinate(gameController.getCurrentX(), gameController.getCurrentY());
                    gameView.setGoalCoordinate(gameController.getGoalX(), gameController.getGoalY());
                });

                if (networkStreaming != null && networkStreaming.isConnected()) {
                    String mode = gameController.getMode().name();
                    networkStreaming.streamTrackingXY(gameController.getGoalX(),
                                    gameController.getGoalY(),
                                    coordinates[0], coordinates[1],
                                    gameController.getCurrentX(), gameController.getCurrentY(),
                                    mode);
                }
            }
        }, 0, dt_ms);

    }

    @Override
    protected void onPause() {
        super.onPause();
        gameTimer.cancel();
    }

    public void onDeviceReady(BluetoothDevice device) {
        Log.d(TAG, "Device ready: " + device);
        getService().streamBuffered(device);

        networkStreaming = getService().getNetworkStreaming();
        networkStreaming.start(ip_address,port);
        emgDecoder.setNewModelStream(networkStreaming);
    }

    @Override
    protected void onServiceBinded(EmgImuService.EmgImuBinder binder) {
        long startTime = new Date().getTime();
        mGameLogger = new FirebaseGameLogger(binder, getString(org.sralab.emgimu.R.string.title_activity_controller), startTime);
        gameController.setGameLogger(mGameLogger);
    }

    @Override
    protected void onServiceUnbinded() {
        mGameLogger = null;
    }

    @Override
    protected int getAboutTextId() {
        return 0;
    }

    public void onBatteryReceived(BluetoothDevice device, float battery) {
    }

    public void onEmgBuffReceived(BluetoothDevice device, long ts_ms, double[][] data) {

        int CHANNELS = data.length;
        final int SAMPLES = data[0].length;
        float [] rms = new float[CHANNELS];

        /*if (BuildConfig.DEBUG && CHANNELS != EmgDecoder.CHANNELS) {
            Log.e(TAG, "Size of data not compatible with model type");
            return;
        }*/

        float input_data[] = new float[CHANNELS];

        for (int i = 0; i < SAMPLES; i++) {

            for (int j = 0; j < CHANNELS; j++) {
                input_data[j]= (float) data[j][i];
            }

            boolean res = emgDecoder.decode(input_data, coordinates);
            boolean valid = ! (Float.isNaN(coordinates[0]) || Float.isNaN(coordinates[1]));
            if (!valid) {
                coordinates[0] = 0.0f;
                coordinates[1] = 0.0f;
            }
            if (res) {

                emgDecoder.get_rms(rms);
                runOnUiThread(() -> {
                    Log.d(TAG, "RMS: " + Arrays.toString(rms));

                    // Space them apart
                    for (int k = 0; k < EmgDecoder.CHANNELS; k++) {
                        rms[k] = rms[k] +  RMS_SPACING * k - RMS_SPACING * EmgDecoder.CHANNELS / 2;
                    }
                    inputGraph.addValue(rms);
                    inputGraph.repaint();

                    Log.d(TAG, "Output: " + Arrays.toString(coordinates));
                    outputGraph.addValue(coordinates);
                    outputGraph.repaint();
                });
            }


        }

    }

    public void onImuAccelReceived(BluetoothDevice device, float[][] accel) {
    }

    public void onImuGyroReceived(BluetoothDevice device, float[][] gyro) {
    }

    public void onImuMagReceived(BluetoothDevice device, float[][] mag) {
    }

    public void onImuAttitudeReceived(BluetoothDevice device, float[] quaternion) {
    }

    public void onDeviceConnected(@NonNull BluetoothDevice device) {
    }

    public void onDeviceDisconnected(@NonNull BluetoothDevice device, int reason) {
    }

}
