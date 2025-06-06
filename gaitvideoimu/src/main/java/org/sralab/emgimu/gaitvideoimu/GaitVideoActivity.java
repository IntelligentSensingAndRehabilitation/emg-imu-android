package org.sralab.emgimu.gaitvideoimu;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
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
import org.sralab.emgimu.camera.CameraCallbacks;
import org.sralab.emgimu.camera.DepthCamera;
import org.sralab.emgimu.camera.PhoneSensors;
import org.sralab.emgimu.gaitvideoimu.stream_visualization.DeviceViewModel;
import org.sralab.emgimu.gaitvideoimu.stream_visualization.StreamingAdapter;
import org.sralab.emgimu.logging.FirebaseGameLogger;
import org.sralab.emgimu.camera.Camera;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

import no.nordicsemi.android.nrftoolbox.widget.DividerItemDecoration;

public class GaitVideoActivity extends AppCompatActivity implements CameraCallbacks {
    private static final String TAG = GaitVideoActivity.class.getSimpleName();

    Camera camera;

    private Button startVideoRecordingButton;
    private Button stopVideoRecordingButton;
    private Button enableEmgPwrButton;

    // RecyclerView Fields
    private DeviceViewModel dvm;
    private StreamingAdapter streamingAdapter;
    private PhoneSensors phoneSensors;

    //Firebase Fields & Nested Class
    class GaitTrial {
        public String fileName;
        public long startTime;
        public long stopTime;
        public String timestampsRef;
        public boolean isEmgEnabled;
        public String depthFileName;
        public Long depthStartTime;
        public String depthTimestampsRef;
        public String phoneSensorLog;
    }
    ArrayList<GaitTrial> trials = new ArrayList<>();
    private GaitTrial curTrial;

    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private FirebaseUser mUser;
    private FirebaseGameLogger mGameLogger;
    private long clickButtonTimestamp;              // user presses start button
    private long createFileTimestamp;               // the system created the video file

    DepthCamera depthCamera;
    //endregion


    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private TextureView textureView;
    private TextureView depthTextureView;
    private RecyclerView recyclerView;

    private static final int REQUEST_CODE_PERMISSIONS = 10;
    @NotNull
    private static final String[] REQUIRED_PERMISSIONS = new String[] {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};

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

    //region Activity Lifecycle Methods
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gait_video_imu);

        textureView = (TextureView) findViewById(R.id.texture);
        depthTextureView = (TextureView) findViewById(R.id.depth_texture);
        startVideoRecordingButton = (Button) findViewById(R.id.start_video_recording_button);
        stopVideoRecordingButton = (Button) findViewById(R.id.stop_video_recording_button);
        enableEmgPwrButton = (Button) findViewById(R.id.enable_emg_pwr_button);

        recyclerView = findViewById(R.id.emg_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getBaseContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(getBaseContext(), DividerItemDecoration.VERTICAL_LIST));

        depthCamera = new DepthCamera(this, this, depthTextureView);
        if (depthCamera.getFrontDepthCameraID() == null) {
            depthTextureView.setVisibility(View.GONE);
            depthCamera = null;
        }

        // Set up the camera. When the texture is ready the camera is opened.
        camera = new Camera(this, this, textureView);
        phoneSensors = new PhoneSensors(this, this);

        dvm = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())).get(DeviceViewModel.class);
        recyclerView.setAdapter(streamingAdapter = new StreamingAdapter(this, dvm));
        Toast.makeText(GaitVideoActivity.this, "Connecting to emg-imu sensors!", Toast.LENGTH_SHORT).show();

        enableFirebase();

        // Sound effects
        ding = MediaPlayer.create(GaitVideoActivity.this, R.raw.ding);
        punch = MediaPlayer.create(GaitVideoActivity.this, R.raw.punch);
        clap = MediaPlayer.create(GaitVideoActivity.this, R.raw.clap);

        // Set up the listeners for the buttons
        startVideoRecordingButton.setOnClickListener(
                v -> {
                    clickButtonTimestamp = new Date().getTime();
                    ding.start();
                    curTrial = new GaitTrial();

                    camera.startVideoRecording();
                    if (depthCamera != null)
                        depthCamera.startVideoRecording();
                    if (phoneSensors != null)
                        phoneSensors.startRecording();

                    startVideoRecordingButton.setEnabled(false);
                    stopVideoRecordingButton.setEnabled(true);
                    running = true;
                    runTimer(true);
                });
        stopVideoRecordingButton.setOnClickListener(
                v -> {
                    punch.start();

                    if (phoneSensors != null) {
                        curTrial.phoneSensorLog = phoneSensors.getFirebasePath();
                        phoneSensors.stopRecording();
                    }
                    if (depthCamera != null)
                        depthCamera.stopVideoRecording();
                    camera.stopVideoRecording();
                    stopVideoRecordingButton.setEnabled(false);
                    running = false;
                    seconds = 0;
                    runTimer(false);
                });
        stopVideoRecordingButton.setEnabled(false); // disable btn initially
        if (savedInstanceState != null) {

            // Get the previous state of the stopwatch
            // if the activity has been
            // destroyed and recreated.
            seconds = savedInstanceState.getInt("seconds");
            running = savedInstanceState.getBoolean("running");
            wasRunning = savedInstanceState.getBoolean("wasRunning");
        }

        enableEmgPwrButton.setOnClickListener(v -> {
            try {
                dvm.enableEmgPwr();
                dvm.enableEmgStream();
                dvm.disableImuStream();

                enableEmgPwrButton.setEnabled(false);
                Toast.makeText(GaitVideoActivity.this, "EMG enabled!", Toast.LENGTH_SHORT).show();
                isEmgEnabled = true;

            } catch (RemoteException e) {
                e.printStackTrace();
            }
        });
    }

    protected void firebaseActivated() {

        dvm.getServiceLiveData().observe(this, binder -> {
            if (binder != null) {
                Log.d(TAG, "Service updated.");
                long startTime = new Date().getTime();
                mGameLogger = new FirebaseGameLogger(dvm.getService(), getString(R.string.app_name), startTime);
            } else {
                mGameLogger = null;
            }
        });

    }

    @Override
    protected void onPause() {
        super.onPause();
        updateLogger();
        dvm.onPause();
        camera.closeCamera();
        if (depthCamera != null)
            depthCamera.closeCamera();

        stopBackgroundThread();

        // If the activity is paused,
        // stop the stopwatch.
        wasRunning = running;
        running = false;
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        dvm.onResume();
        startBackgroundThread();

        camera.openCamera(new Size(1920, 1080), 60);
        if (depthCamera != null)
            depthCamera.openFrontDepthCamera();

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

    public Handler getBackgroundHandler() {
        return backgroundHandler;
    }

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

    public void showVideoStatus(String status, String color) {
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
                            firebaseActivated();
                        } else {
                            // If sign in fails, display a message to the user.
                            Log.d(TAG, "signInAnonymously:failure" + task.getException());
                        }
                    });
        } else {
            Log.d(TAG, "User ID: " + mUser.getUid());
            showUser();
            firebaseActivated();
        }

        FirebaseInstallations.getInstance().getId().addOnSuccessListener(mToken -> {
            Log.d(TAG, "Received token: " + mToken);
        });

        storage = FirebaseStorage.getInstance();

    }

    private String setupFirebaseFile(String filename) {
        String uploadFileName =  "videos/" + mUser.getUid() + "/" + filename;
        return uploadFileName;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    public void pushVideoFileToFirebase(File currentFile, long startTime, boolean depth, ArrayList<Long> timestamps) {

        String simpleFilename = getSimpleFilename(currentFile);
        String firebaseUploadFileName = setupFirebaseFile(simpleFilename);

        if (depth) {
            curTrial.depthFileName = firebaseUploadFileName;
            curTrial.depthStartTime = startTime;
            if (timestamps != null) {
                curTrial.depthTimestampsRef = uploadTimestamps(currentFile.getAbsolutePath(), timestamps);
            } else {
                curTrial.depthTimestampsRef = null;
            }
        }
        else {
            curTrial.fileName = firebaseUploadFileName;

            // Store lots of timestamps
            curTrial.startTime = startTime;
            curTrial.stopTime = new Date().getTime();

            // TODO: this should probably be outside of this function
            curTrial.isEmgEnabled = isEmgEnabled;

            // Store trial
            // Note: this presumes that the depth video is uploaded first!
            trials.add(curTrial);

            if (timestamps != null) {
                curTrial.timestampsRef = uploadTimestamps(currentFile.getAbsolutePath(), timestamps);
            } else {
                curTrial.timestampsRef = null;
            }

        }

        updateLogger();

        if (!isNetworkAvailable()) {
            Log.d(TAG, "Network unavailable, skipping upload for " + simpleFilename);
            if (!depth) {
                showVideoStatus("Saved locally: " + simpleFilename + " (offline mode)", "gray");
                clap.start();
                startVideoRecordingButton.setEnabled(true);
            }
            return;
        }

        if (!depth) {
            showVideoStatus("Uploading " + simpleFilename + "...", "red");
        }

        StorageReference storageRef = storage.getReference().child(firebaseUploadFileName);
        storageRef.putFile(Uri.fromFile(currentFile))
                .addOnFailureListener(e -> {
                    showVideoStatus("Upload "  + simpleFilename + " failed: " + e.getMessage(), "red");

                    if (!depth)
                        startVideoRecordingButton.setEnabled(true);
                })
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

                    if (!depth) {
                        showVideoStatus("Uploaded video: " + simpleFilename + ", " + fileSize + " " + videoDuration, "green");
                    }
                    Log.d(TAG, "fileSize = " + fileSize);
                    Toast.makeText(GaitVideoActivity.this, "Upload " + simpleFilename + " succeeded!", Toast.LENGTH_SHORT).show();

                    if (!depth) {
                        clap.start();
                        startVideoRecordingButton.setEnabled(true);
                    }
                });

    }

    public String uploadTimestamps(String fileName, ArrayList<Long> timestamps) {
        Log.d(TAG, "uploadTimestamps: " + fileName + " timestamps length: " + timestamps.size());

        // Write timestamps to a local file (partly as a backup)
        String timestampFileName = fileName.substring(0, fileName.length() - 4) + "_timestamps.json.gz";
        Gson gson = new Gson();
        OutputStream writer = null;
        try {
            FileOutputStream filewriter = new FileOutputStream(timestampFileName);
            writer = new GZIPOutputStream(filewriter);
            writer.write(gson.toJson(timestamps).getBytes());
            writer.close();
            filewriter.close();
            Log.d(TAG, "Timestamps written to " + timestampFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Now upload to Firestore
        String temp[] = timestampFileName.split("/");
        String simpleFilename = temp[temp.length - 1];
        String firebaseUploadFileName = setupFirebaseFile(simpleFilename);

        File timestampFile = new File(timestampFileName);

        StorageReference storageRef = storage.getReference().child(firebaseUploadFileName);

        // only upload if online
        if (isNetworkAvailable()) {
            storageRef.putFile(Uri.fromFile(timestampFile))
                    .addOnFailureListener(e ->
                            Log.d(TAG, "Timestamps upload to " + firebaseUploadFileName + " + failed. " + e.getMessage()))
                    .addOnSuccessListener(taskSnapshot ->
                            Log.d(TAG, "Timestamps uploaded successfully to " + firebaseUploadFileName)
                    );
        }
        else {
            Log.d(TAG, "Timestamps upload to " + firebaseUploadFileName + " failed. No network connection.");
        }

        return firebaseUploadFileName;
    }

    // Internal File Storage
    public File createNewFile(String suffix) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                Locale.getDefault()).format(new Date());
        if (suffix != null) {
            timeStamp = timeStamp + suffix;
        }
        createFileTimestamp = new Date().getTime();
        String filename = timeStamp + ".mp4";
        File mediaFile = new File(getApplicationContext().getExternalFilesDir("gait_video"), filename);
        Log.d(TAG, "Filename: " + mediaFile.getAbsolutePath());
        return mediaFile;
    }

    public String getSimpleFilename(File file) {
        String temp[] = file.getAbsolutePath().split("/");
        String filename = temp[temp.length - 1];
        return filename;
    }

    // Save the state of the stopwatch
    // if it's about to be destroyed.
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putInt("seconds", seconds);
        savedInstanceState.putBoolean("running", running);
        savedInstanceState.putBoolean("wasRunning", wasRunning);
    }

    // Sets the NUmber of seconds on the timer.
    // The runTimer() method uses a Handler
    // to increment the seconds and
    // update the text view.
    private void runTimer(boolean isTimerVisible)
    {

        // Get the text view.
        final TextView timeView = (TextView)findViewById(R.id.time_view);

        if (isTimerVisible) {
            timeView.setVisibility(View.VISIBLE);
            timeView.setTextColor(Color.parseColor("#FFFFFF"));
            timeView.setBackgroundColor(Color.parseColor("#FF0000"));
        } else {
            timeView.setVisibility(View.INVISIBLE);
        }

        // Creates a new Handler
        final Handler handler = new Handler(Looper.getMainLooper());

        // Call the post() method, passing in a new Runnable.
        // The post() method processes code without a delay,
        // so the code in the Runnable will run almost immediately.
        handler.post(new Runnable() {

            @Override
            public void run()
            {
                int hours = seconds / 3600;
                int minutes = (seconds % 3600) / 60;
                int secs = seconds % 60;

                // Format the seconds into hours, minutes,
                // and seconds.
                String time = String.format(Locale.getDefault(),"%02d:%02d:%02d",
                        hours, minutes, secs);

                // Set the text view text.
                timeView.setText(time);

                // If running is true, increment the seconds variable.
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

    public boolean checkPermissions() {
        // Explicitly check user permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
            return false;
        }
        return true;
    }

    @Override
    public int getDisplayRotation() {
        return getDisplay().getRotation();
    }

}