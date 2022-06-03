package org.sralab.emgimu.gaitvideoimu;

import static android.hardware.camera2.CameraDevice.TEMPLATE_RECORD;
import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.installations.FirebaseInstallations;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;
import org.sralab.emgimu.gaitvideoimu.stream_visualization.DeviceViewModel;
import org.sralab.emgimu.gaitvideoimu.stream_visualization.StreamingAdapter;
import org.sralab.emgimu.logging.FirebaseGameLogger;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import no.nordicsemi.android.nrftoolbox.widget.DividerItemDecoration;

public class GaitVideoActivity extends AppCompatActivity {
    private static final String TAG = GaitVideoActivity.class.getSimpleName();
    private Button startVideoRecordingButton;
    private Button stopVideoRecordingButton;
    private Button enableEmgPwrButton;


    //region RecyclerView Fields
    private DeviceViewModel dvm;
    private StreamingAdapter streamingAdapter;
    //endregion

    //region Firebase Fields & Nested Class
    class GaitTrial {
        public String fileName;
        public long userPressedStartVideoRecordingButtonTimestamp;
        public long systemCreatedFileTimestamp;
        public long cameraHardwareConfiguredStartRecordingTimestamp;
        public long userPressedStopVideoRecordingButtonTimestamp;
        public boolean isEmgEnabled;
    }
    ArrayList<GaitTrial> trials = new ArrayList<>();
    private GaitTrial curTrial;

    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private FirebaseUser mUser;
    private FirebaseGameLogger mGameLogger;
    private String firebaseUploadFileName;
    private String simpleFilename;
    private long clickButtonTimestamp;      // user presses start button
    private long createFileTimestamp;       // the system created the video file
    private long startRecordingTimestamp;   // after camera has been configured, when recording starts
    //endregion

    //region Video Fields
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    @NotNull
    private static final String[] REQUIRED_PERMISSIONS = new String[] {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};

    private File currentFile;
    private Size imageDimension;
    protected MediaRecorder mediaRecorder;
    protected CameraDevice cameraDevice;
    protected CaptureRequest.Builder captureRequestBuilder;
    protected CameraCaptureSession cameraCaptureSession;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private TextureView textureView;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    //endregion

    //region Sound Effects Fields
    public MediaPlayer ding = new MediaPlayer();
    public MediaPlayer punch = new MediaPlayer();
    public MediaPlayer clap = new MediaPlayer();
    //endregion

    //region Stop Watch Fields
    private int seconds = 0;
    private boolean running;
    private boolean wasRunning;
    private String videoDuration;
    //endregion

    private Boolean isEmgEnabled = false;

    //region Internal Log File Fields
    List<String> uploadedVideos = new ArrayList<>();

    //region Activity Lifecycle Methods
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gait_video_imu);
        textureView = (TextureView) findViewById(R.id.texture);
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
        startVideoRecordingButton = (Button) findViewById(R.id.start_video_recording_button);
        stopVideoRecordingButton = (Button) findViewById(R.id.stop_video_recording_button);
        enableEmgPwrButton = (Button) findViewById(R.id.enable_emg_pwr_button);

        final RecyclerView recyclerView = findViewById(R.id.emg_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getBaseContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(getBaseContext(), DividerItemDecoration.VERTICAL_LIST));

        enableFirebase();

        dvm = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())).get(DeviceViewModel.class);
        recyclerView.setAdapter(streamingAdapter = new StreamingAdapter(this, dvm));
        Toast.makeText(GaitVideoActivity.this, "Connecting to emg-imu sensors!", Toast.LENGTH_SHORT).show();

        dvm.getServiceLiveData().observe(this, binder -> {
            if (binder != null) {
                Log.d(TAG, "Service updated.");
                long startTime = new Date().getTime();
                mGameLogger = new FirebaseGameLogger(dvm.getService(), getString(R.string.app_name), startTime);
            } else {
                mGameLogger = null;
            }
        });

        // Sound effects
        ding = MediaPlayer.create(GaitVideoActivity.this, R.raw.ding);
        punch = MediaPlayer.create(GaitVideoActivity.this, R.raw.punch);
        clap = MediaPlayer.create(GaitVideoActivity.this, R.raw.clap);

        // Set up the listeners for the buttons
        startVideoRecordingButton.setOnClickListener(
                v -> {
                    clickButtonTimestamp = new Date().getTime();
                    ding.start();
                    startVideoRecording();
                    running = true;
                    runTimer(true);
                });
        stopVideoRecordingButton.setOnClickListener(
                v -> {
                    punch.start();
                    stopVideoRecording();
                    running = false;
                    seconds = 0;
                    runTimer(false);
                });
        stopVideoRecordingButton.setEnabled(false); // disable btn initially
        if (savedInstanceState != null) {

            // Get the previous state of the stopwatch
            // if the activity has been
            // destroyed and recreated.
            seconds
                    = savedInstanceState
                    .getInt("seconds");
            running
                    = savedInstanceState
                    .getBoolean("running");
            wasRunning
                    = savedInstanceState
                    .getBoolean("wasRunning");
        }

        enableEmgPwrButton.setOnClickListener(v -> {
            try {
                //enableEmgPwr();
                enableEmgStream();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        updateLogger();
        dvm.onPause();
        closeCamera();
        stopBackgroundThread();

        // If the activity is paused,
        // stop the stopwatch.
        wasRunning = running;
        running = false;
    }

    @Override
    public void onResume() {
        super.onResume();
        dvm.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }

        // If the activity is resumed,
        // start the stopwatch
        // again if it was running previously.
        if (wasRunning) {
            running = true;
        }
    }

    @Override
    public void onStop() {
        this.finish();
        super.onStop();
    }

    //endregion

    //region Video Setup
    /**
     * Prepares TextureView
     * TextureView is the view which renders captured camera image data.
     * TextureView is prepared at View creation, and this callback gives us a notification
     * when we are ready to prepare for the camera device initialization.
     */
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

        }
    };

    /**
     * This is used to check a camera device state (open, close). It is required to open a camera.
     */
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            // This is called when the camera is in the open state
            cameraDevice = camera;
            createPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    protected void startBackgroundThread() {
        backgroundThread = new HandlerThread("Camera Background Thread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * @brief Establishes connection with the camera hardware.
     * @param width
     * @param height
     */
    private void openCamera(int width, int height) {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[0]; // Camera 0 facing CAMERA_FACING_BACK
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

            // Exploring the fps ranges that camera supports
            for (int j=0; j <manager.getCameraIdList().length; j++) {
                CameraCharacteristics characteristicsTemp = manager.getCameraCharacteristics(cameraId);
                Range<Integer>[] rangesTemp = characteristicsTemp.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                String fpsRangeArrayToDisplay = new String();
                for (int i=0; i <rangesTemp.length; i++) {
                    fpsRangeArrayToDisplay = fpsRangeArrayToDisplay + rangesTemp[i].toString() + ", ";
                }
                Log.d(TAG, "gait,  cameraId = " + manager.getCameraIdList()[j] + " | fps range = " + fpsRangeArrayToDisplay);
            }

            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            mediaRecorder = new MediaRecorder();
            // Explicitly check user permissions
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                            != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
                return;
            }
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        closePreview();
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != mediaRecorder) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }
    //endregion

    //region Video Preview
    /**
     * @brief Instantiates video stream for user to view inside the application.
     */
    private void createPreview() {
        SurfaceTexture texture = textureView.getSurfaceTexture();
        assert texture != null;
        texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
        Surface previewSurface = new Surface(texture);
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);
            cameraDevice.createCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession captureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSession = captureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession captureSession) {
                    Toast.makeText(GaitVideoActivity.this, "Configuration failed", Toast.LENGTH_SHORT).show();
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        startBackgroundThread();
        try {
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closePreview() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
    }
    //endregion

    //region Video Recording
    private void startVideoRecording() {
        startVideoRecordingButton.setEnabled(false);
        stopVideoRecordingButton.setEnabled(true);
        closePreview();
        try {
            setupMediaRecorder();
            Toast.makeText(GaitVideoActivity.this, "Recording " + simpleFilename, Toast.LENGTH_SHORT).show();
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            captureRequestBuilder = cameraDevice.createCaptureRequest(TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            captureRequestBuilder.addTarget(previewSurface);

            // MediaRecorder setup for surface
            Surface recorderSurface = mediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            captureRequestBuilder.addTarget(recorderSurface);

            // setting the camera fps
            Range<Integer> fpsRange = new Range<>(30,30);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,fpsRange);

            // Start capture session
            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession captureSession) {
                    cameraCaptureSession = captureSession;
                    updatePreview();
                    mediaRecorder.start();
                    startRecordingTimestamp = new Date().getTime();
                    curTrial.cameraHardwareConfiguredStartRecordingTimestamp = startRecordingTimestamp;
                    Log.d(TAG, "Timestamp difference (startRecordingTimestamp - clickButtonTimestamp) = " + (startRecordingTimestamp - clickButtonTimestamp) + " ms");
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession captureSession) {
                    Toast.makeText(GaitVideoActivity.this, "Configuration failed", Toast.LENGTH_SHORT).show();
                }
            }, backgroundHandler);

        } catch (IOException | CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setupMediaRecorder() throws IOException {
        final Activity activity = this;
        if (null == activity) {
            return;
        }
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        currentFile = createNewFile();
        mediaRecorder.setOutputFile(currentFile.getAbsolutePath());
        simpleFilename = getSimpleFilename(currentFile);
        firebaseUploadFileName = setupFirebaseFile(simpleFilename);
        showVideoStatus("Recording " + simpleFilename, "gray");
        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
        mediaRecorder.setVideoFrameRate(profile.videoFrameRate);
        mediaRecorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
        mediaRecorder.setVideoEncodingBitRate(profile.videoBitRate);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        mediaRecorder.setOrientationHint(ORIENTATIONS.get(rotation));
        mediaRecorder.prepare();
    }

    private void stopVideoRecording() {
        Toast.makeText(GaitVideoActivity.this, "Uploading " + simpleFilename, Toast.LENGTH_SHORT).show();
        stopVideoRecordingButton.setEnabled(false);
        try {
            cameraCaptureSession.stopRepeating();
            cameraCaptureSession.abortCaptures();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        // Stop recording
        mediaRecorder.stop();
        mediaRecorder.reset();
        createPreview();
        pushVideoFileToFirebase();
    }
    //endregion

    //region Firebase
    public void updateLogger() {
        if (mGameLogger != null) {
            Gson gson = new Gson();
            String json = gson.toJson(trials);
            mGameLogger.finalize(trials.size(), json);
        }
    }

    void showUser() {
        TextView text = findViewById(R.id.userId);
        text.setText("User: " + mUser.getUid());
    }

    void showVideoStatus(String status, String color) {
        TextView text = findViewById(R.id.videoStatus);
        HashMap<String, String> textColorMap = new HashMap<>();
        textColorMap.put("green", "#6BB02F");
        textColorMap.put("red", "#FF0000");
        textColorMap.put("gray", "#BDBDBD");
        text.setTextColor(Color.parseColor(textColorMap.get(color)));
        text.setText(status);
    }

    void enableFirebase() {
        FirebaseApp.initializeApp(this);
        mAuth = FirebaseAuth.getInstance();

        // Check if user is signed in (non-null) and update UI accordingly.
        mUser = mAuth.getCurrentUser();
        if (mUser == null) {
            Log.d(TAG, "Attempting to log in to firebase");
            mAuth.signInAnonymously()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            mUser = mAuth.getCurrentUser();
                            Log.d(TAG, "signInAnonymously:success. UID:" + mUser.getUid());
                            showUser();
                        } else {
                            // If sign in fails, display a message to the user.
                            Log.d(TAG, "signInAnonymously:failure" + task.getException());
                        }
                    });
        } else {
            Log.d(TAG, "User ID: " + mUser.getUid());
            showUser();
        }

        FirebaseInstallations.getInstance().getId().addOnSuccessListener(mToken -> {
            Log.d(TAG, "Received token: " + mToken);
        });

        storage = FirebaseStorage.getInstance();

    }

    private String setupFirebaseFile(String filename) {
        String uploadFileName =  "videos/" + mUser.getUid() + "/" + filename;
        curTrial = new GaitTrial();
        curTrial.fileName = uploadFileName;
        curTrial.userPressedStartVideoRecordingButtonTimestamp = clickButtonTimestamp;
        curTrial.systemCreatedFileTimestamp = createFileTimestamp;
        curTrial.isEmgEnabled = isEmgEnabled;
        trials.add(curTrial);
        return uploadFileName;
    }

    private void pushVideoFileToFirebase() {
        curTrial.userPressedStopVideoRecordingButtonTimestamp = new Date().getTime();
        updateLogger();
        showVideoStatus("Uploading "  + simpleFilename + "...", "red");
        StorageReference storageRef = storage.getReference().child(firebaseUploadFileName);
        storageRef.putFile(Uri.fromFile(currentFile))
                .addOnFailureListener(e -> showVideoStatus("Upload "  + simpleFilename + " failed", "failed"))
                .addOnSuccessListener(taskSnapshot -> {
                    String fileSize = " " + String.valueOf(currentFile.length()/1024) + " KB";
                    Path path = Paths.get(String.valueOf(currentFile));
                    try {
                        long bytes = Files.size(path);
                        if(bytes > 1024 && bytes < 1000000) {
                            fileSize = " " + (String.format("%,d KB", bytes / 1024));
                        } else if(bytes > 1000000) {
                            fileSize = " " + (String.format("%,d MB", bytes / 1000000));;
                        } else {
                            fileSize = " " + (String.format("%,d bytes", bytes));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    showVideoStatus("Uploaded video: "  + simpleFilename + ", " + fileSize + " " + videoDuration, "green");
                    Log.d(TAG, "fileSize = " +fileSize);
                    Toast.makeText(GaitVideoActivity.this, "Upload "  + simpleFilename + " succeeded!", Toast.LENGTH_SHORT).show();
                    clap.start();
                    startVideoRecordingButton.setEnabled(true);
                        });

    }
    //endregion

    //region Internal File Storage

    private File createNewFile() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss'Z'",
                Locale.getDefault()).format(new Date());
        createFileTimestamp = new Date().getTime();
        String filename = timeStamp + ".mp4";
        File mediaFile = new File(getApplicationContext().getExternalFilesDir("gait_video"), filename);
        Log.d(TAG, "Filename: " + mediaFile.getAbsolutePath());
        return mediaFile;
    }

    private String getSimpleFilename(File file) {
        String temp[] = file.getAbsolutePath().split("/");
        String filename = temp[temp.length - 1];
        return filename;
    }
    //endregion

    //region Stop Watch
    // Save the state of the stopwatch
    // if it's about to be destroyed.
    @Override
    public void onSaveInstanceState(
            Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState
                .putInt("seconds", seconds);
        savedInstanceState
                .putBoolean("running", running);
        savedInstanceState
                .putBoolean("wasRunning", wasRunning);
    }

    // Sets the NUmber of seconds on the timer.
    // The runTimer() method uses a Handler
    // to increment the seconds and
    // update the text view.
    private void runTimer(boolean isTimerVisible)
    {

        // Get the text view.
        final TextView timeView
                = (TextView)findViewById(
                R.id.time_view);

        if (isTimerVisible) {
            timeView.setVisibility(View.VISIBLE);
            timeView.setTextColor(Color.parseColor("#FFFFFF"));
            timeView.setBackgroundColor(Color.parseColor("#FF0000"));
        } else {
            timeView.setVisibility(View.INVISIBLE);
        }

        // Creates a new Handler
        final Handler handler
                = new Handler();

        // Call the post() method,
        // passing in a new Runnable.
        // The post() method processes
        // code without a delay,
        // so the code in the Runnable
        // will run almost immediately.
        handler.post(new Runnable() {
            @Override

            public void run()
            {
                int hours = seconds / 3600;
                int minutes = (seconds % 3600) / 60;
                int secs = seconds % 60;

                // Format the seconds into hours, minutes,
                // and seconds.
                String time
                        = String
                        .format(Locale.getDefault(),
                                "%02d:%02d:%02d", hours,
                                minutes, secs);

                // Set the text view text.
                timeView.setText(time);

                // If running is true, increment the
                // seconds variable.
                if (running) {
                    if (minutes > 0) {
                        videoDuration = ", " + String
                                .format(Locale.getDefault(), "%d:%02d", minutes, secs);
                    } else {
                        videoDuration = ", " + String.format(Locale.getDefault(), "%d sec", secs);
                    }
                    seconds++;
                    // Post the code again
                    // with a delay of 1 second.
                    handler.postDelayed(this, 1000);
                }

            }
        });
    }
    //endregion

    //region EmgPwr

    /**
     * Enables emgPwr streaming - assumes that it is initially disabled. Note: once
     * emg is enabled, it will remain enabled until the app is killed.
     * Firebase streams will be updated with EmgPwr message and the field isEmgEnabled in the
     * details of the gamePlay logs will be updated to true.
     */
    private void enableEmgPwr() throws RemoteException {
        dvm.enableEmgPwr();
        enableEmgPwrButton.setEnabled(false);
        Toast.makeText(GaitVideoActivity.this, "EMG enabled!", Toast.LENGTH_SHORT).show();
        isEmgEnabled = true;
    }

    private void enableEmgStream() throws RemoteException {
        dvm.enableEmgStream();
        enableEmgPwrButton.setEnabled(false);
        Toast.makeText(GaitVideoActivity.this, "EMG enabled!", Toast.LENGTH_SHORT).show();
        isEmgEnabled = true;
    }
    //endregion

}