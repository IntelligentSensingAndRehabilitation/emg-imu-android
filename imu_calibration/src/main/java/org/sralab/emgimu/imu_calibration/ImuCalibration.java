package org.sralab.emgimu.imu_calibration;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;

import org.sralab.emgimu.EmgImuAdapterActivity;
import org.sralab.emgimu.logging.FirebaseGameLogger;
import org.sralab.emgimu.service.EmgImuService;

import java.util.Date;
import java.util.List;

import no.nordicsemi.android.nrftoolbox.widget.DividerItemDecoration;

public class ImuCalibration extends AppCompatActivity {

    static final private String TAG = ImuCalibration.class.getSimpleName();

    CalibrationAdapter calibrationAdapater;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_imu_calibration);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final RecyclerView recyclerView = findViewById(R.id.imu_calibration_list);

        DeviceViewModel dvm = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())).get(DeviceViewModel.class);
        dvm.getDevicesLiveData().observe(this, devices -> calibrationAdapater.notifyDataSetChanged());

        recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL_LIST));

        recyclerView.setAdapter(calibrationAdapater = new CalibrationAdapter(this, dvm));
    }
        /*
        private static final int REQUEST_VIDEO_CAPTURE = 1;
        boolean recording = false;
        long recordingStartTime;

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
    */
}
