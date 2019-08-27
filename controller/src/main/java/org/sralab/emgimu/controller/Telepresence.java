package org.sralab.emgimu.controller;

import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;

import org.sralab.emgimu.EmgImuBaseActivity;
import org.sralab.emgimu.controller.telepresence.APIService;
import org.sralab.emgimu.controller.telepresence.Post;
import org.sralab.emgimu.controller.telepresence.RetrofitClient;
import org.sralab.emgimu.controller.telepresence.Status;
import org.sralab.emgimu.service.EmgImuService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import yjkim.mjpegviewer.MjpegView;

// Note for using retrofit from this
// https://android.jlelse.eu/consuming-rest-api-using-retrofit-library-in-android-ed47aef01ecb
// Interfaces generated with
// http://www.jsonschema2pojo.org/


public class Telepresence extends EmgImuBaseActivity {

    private EmgDecoder emgDecoder = null;
    private final static String TAG = Telepresence.class.getSimpleName();
    private float coordinates[] = new float[EmgDecoder.EMBEDDINGS_SIZE];
    private String hostname = "http://10.42.0.253";  // Default robot IP address
    private String control= hostname + ":8000";
    //private String video_address = hostname + ":8000/streaming";
    private String video_address = hostname + ":8080/stream/video.mjpeg";

    private GameView gameView;
    private TextView responseText;
    private MjpegView videoView;
    private Retrofit teleprescenceService = RetrofitClient.getClient(control);
    private boolean enabled = false;
    private boolean commandPending = false;

    private APIService getAPIService() {
        return teleprescenceService.create(APIService.class);
    }

    @Override
    protected void onCreateView(Bundle savedInstanceState) {
        setContentView(R.layout.activity_telepresence);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        emgDecoder = new EmgDecoder(this);

        gameView = findViewById(R.id.game_view);
        gameView.setShowGoal(false);

        videoView = findViewById(R.id.video_view);
        //videoView.clearCache(true);
        //videoView.loadUrl(video_address);
        //videoView.setVideoURI(Uri.parse(video_address));
        videoView.Start(video_address);

        ToggleButton enableDisable = findViewById(R.id.button_enable_robot);
        enabled = enableDisable.isChecked();
        enableDisable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                enabled = true;
            } else {
                enabled = false;
                sendControlCommand(0, 0);
            }
        });

        responseText = findViewById(R.id.testViewResponse);

        sendControlCommand(0f, 0f);
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "Pausing and stopping");
        enabled = false;
        sendControlCommand(0,0);
        super.onPause();
    }

    private void sendControlCommand(float speed, float turn) {

        Log.d(TAG, "Sending control message: " + speed + " " + turn);

        commandPending = true;

        getAPIService().control(new Post(speed, turn)).enqueue(new Callback<Status>() {
            @Override
            public void onResponse(Call<Status> call, Response<Status> response) {

                commandPending = false;
                Log.d(TAG, "Sending");

                Log.i(TAG, "onResponse: for " + call.request()); // + " " + response.body().toString());
                if (response.isSuccessful()) {
                    float motor1 = response.body().getMotor1();
                    float motor2 = response.body().getMotor2();

                    Log.i(TAG, "Response received: " + motor1 + " " + motor2);
                    responseText.setText(response.body().toString());
                } else {
                    Log.e(TAG, response.message());
                }
            }

            @Override
            public void onFailure(Call<Status> call, Throwable t) {

                commandPending = false;

                Log.e(TAG, "Unable to submit post to API.", t);
            }
        });
    }
    @Override
    public void onDeviceReady(BluetoothDevice device) {
        Log.d(TAG, "Device ready: " + device);
        getService().streamBuffered(device);
    }

    @Override
    public void onEmgBuffReceived(BluetoothDevice device, long ts_ms, double[][] data) {

        int CHANNELS = data.length;
        final int SAMPLES = data[0].length;
        float[] rms = new float[CHANNELS];

        if (BuildConfig.DEBUG && CHANNELS != EmgDecoder.CHANNELS) {
            Log.e(TAG, "Size of data not compatible with model type");
            return;
        }

        float input_data[] = new float[CHANNELS];

        for (int i = 0; i < SAMPLES; i++) {

            for (int j = 0; j < CHANNELS; j++) {
                input_data[j] = (float) data[j][i];
            }

            boolean res = emgDecoder.decode(input_data, coordinates);

            if (res) {

                if (enabled && !commandPending) {
                    float speed = 2 * (coordinates[1] - 0.5f);
                    float turn = 2 * (coordinates[0] - 0.5f);
                    sendControlCommand(speed, turn);
                }

                runOnUiThread(() -> gameView.setOutputCoordinate(coordinates[0], coordinates[1]));
            }
        }
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
