package org.sralab.emgimu.imu_calibration;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;

import org.sralab.emgimu.EmgImuAdapterActivity;
import org.sralab.emgimu.logging.FirebaseGameLogger;
import org.sralab.emgimu.service.EmgImuService;

import java.util.Date;
import java.util.List;

public class ImuCalibration extends EmgImuAdapterActivity {

    static final private String TAG = ImuCalibration.class.getSimpleName();

    private static final int REQUEST_VIDEO_CAPTURE = 1;
    boolean recording = false;
    long recordingStartTime;

    @Override
    protected void onCreateView(Bundle savedInstanceState) {

        setContentView(R.layout.activity_imu_calibration);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final RecyclerView recyclerView = findViewById(R.id.imu_calibration_list);
        super.onCreateView(new CalibrationAdapter(), recyclerView);

        Button startStreaming = findViewById(R.id.start_streaming_button);
        startStreaming.setOnClickListener(v -> {
            EmgImuService.EmgImuBinder mService = getService();
            if (mService != null) {
                List<BluetoothDevice> devices = mService.getManagedDevices();
                for (final BluetoothDevice dev : devices) {
                    mService.enableAttitude(dev);
                    mService.enableImu(dev);
                }
            }
        });

        Button stopStreaming = findViewById(R.id.stop_streaming_button);
        stopStreaming.setOnClickListener(v -> {
            EmgImuService.EmgImuBinder mService = getService();
            if (mService != null) {
                List<BluetoothDevice> devices = mService.getManagedDevices();
                for (final BluetoothDevice dev : devices) {
                    mService.disableAttitude(dev);
                    mService.disableImu(dev);
                }
            }
        });

        Button recordVideo = findViewById(R.id.video_button);
        recordVideo.setOnClickListener(v -> {
            Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            if (takeVideoIntent.resolveActivity(getPackageManager()) != null) {
                recording = true;
                recordingStartTime =  new Date().getTime();
                startActivityForResult(takeVideoIntent, REQUEST_VIDEO_CAPTURE);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_VIDEO_CAPTURE && resultCode == RESULT_OK) {
            Uri videoUri = intent.getData();
            //videoView.setVideoURI(videoUri);
            Log.d(TAG, "Received video: " + videoUri);
            recording = false;
            FirebaseGameLogger mGameLogger = new FirebaseGameLogger(getService(), getString(R.string.title_imu_calibration), recordingStartTime);
            mGameLogger.finalize(0, videoUri.toString());
        }
    }

    @Override
    public void onImuMagReceived(BluetoothDevice device, float[][] mag) {

    }

    @Override
    public boolean isChangingConfigurations() {
        if (recording)
            return true;
        else
            return super.isChangingConfigurations();
    }

}
