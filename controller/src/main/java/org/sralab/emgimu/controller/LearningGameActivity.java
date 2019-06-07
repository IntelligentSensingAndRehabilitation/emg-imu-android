package org.sralab.emgimu.controller;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import org.sralab.emgimu.EmgImuBaseActivity;
import org.sralab.emgimu.service.EmgImuService;
import org.sralab.emgimu.visualization.VectorGraphView;

import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

public class LearningGameActivity extends EmgImuBaseActivity {

    private static final String TAG = LearningGameActivity.class.getSimpleName();

    private EmgDecoder emgDecoder = null;

    private VectorGraphView inputGraph;
    private VectorGraphView outputGraph;

    private GameView gameView;
    private GameController gameController = new GameController();

    private Timer gameTimer;

    @Override
    protected void onCreateView(Bundle savedInstanceState) {
        setContentView(R.layout.activity_learning_game);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ViewGroup decoder_inputs = findViewById(R.id.decoder_inputs);
        inputGraph = new VectorGraphView(decoder_inputs.getContext(), decoder_inputs, EmgDecoder.CHANNELS);
        inputGraph.setWindowSize(250);
        inputGraph.setRange(10);

        ViewGroup decoder_outputs = findViewById(R.id.decoder_outputs);
        outputGraph = new VectorGraphView(decoder_outputs.getContext(), decoder_outputs, EmgDecoder.EMBEDDINGS_SIZE);
        outputGraph.setWindowSize(250);
        outputGraph.setRange(10);

        gameView = findViewById(R.id.game_view);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Log.d(TAG, "Processing");
                float coordinates[] = new float[EmgDecoder.EMBEDDINGS_SIZE];

                for (int i = 0; i < 100; i++) {
                    float data[] = {1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f};


                    emgDecoder.decode(data, coordinates);

                    inputGraph.addValue(data);
                    outputGraph.addValue(coordinates);

                    gameView.setOutputCoordinate(coordinates[0], coordinates[1]);
                }

                inputGraph.repaint();
                outputGraph.repaint();

                Log.d(TAG, "Output: " + Arrays.toString(coordinates));
            }
        });

        try {
            emgDecoder = new EmgDecoder(this);
        } catch (Exception e) {
            Log.e(TAG, "Error: ", e);
        }

        // Set up periodic timer that updates the game model/controller then
        // then the view
        gameTimer = new Timer();
        gameTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                gameController.update(0.0f, 0.0f);
                gameView.setGoalCoordinate(gameController.getGoalX(), gameController.getGoalY());
            }
        }, 10, 10);

    }

    @Override
    protected void onPause() {
        super.onPause();
        gameTimer.cancel();
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
