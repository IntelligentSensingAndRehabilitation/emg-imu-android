package org.sralab.emgimu.gaitvideoimu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.RecordingStats;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.installations.FirebaseInstallations;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.gson.Gson;

import org.sralab.emgimu.gaitvideoimu.stream_visualization.DeviceViewModel;
import org.sralab.emgimu.gaitvideoimu.stream_visualization.StreamingAdapter;
import org.sralab.emgimu.logging.FirebaseGameLogger;

import java.security.InvalidParameterException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import no.nordicsemi.android.nrftoolbox.widget.DividerItemDecoration;

public class GaitVideoImu extends AppCompatActivity {

    private static String TAG = GaitVideoImu.class.getSimpleName();

    private DeviceViewModel dvm;
    private StreamingAdapter streamingAdapter;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;
    private VideoCapture videoCapture;

    class GaitTrial {
        public String fileName;
        public long startTime;
        public long endTime;

    }
    ArrayList<GaitTrial> trials = new ArrayList<>();
    private GaitTrial curTrial;

    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private FirebaseUser mUser;
    private FirebaseGameLogger mGameLogger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gait_video_imu);

        final RecyclerView recyclerView = findViewById(R.id.emg_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getBaseContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(getBaseContext(), DividerItemDecoration.VERTICAL_LIST));

        enableFirebase();

        dvm = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication())).get(DeviceViewModel.class);
        recyclerView.setAdapter(streamingAdapter = new StreamingAdapter(this, dvm));

        dvm.getServiceLiveData().observe(this, binder -> {
            if (binder != null) {
                Log.d(TAG, "Service updated.");
                long startTime = new Date().getTime();
                mGameLogger = new FirebaseGameLogger(dvm.getService(), getString(R.string.app_name), startTime);
            } else {
                mGameLogger = null;
            }
        });

        if (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // Request camera-related permissions
            requestPermissions(new String[] {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 10);
        }

        previewView = findViewById(R.id.cameraView);
        cameraProviderFuture = ProcessCameraProvider.getInstance(getBaseContext());
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(getBaseContext()));

        Button startButton = findViewById(R.id.startButton);
        Button stopButton = findViewById(R.id.stopButton);
        startButton.setEnabled(true);
        stopButton.setEnabled(false);

        startButton.setOnClickListener(v ->
                {
                    startButton.setEnabled(false);
                    stopButton.setEnabled(true);

                    SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd-HHmmss'Z'");
                    Date now = new Date();
                    String fileName = formatter.format(now) + ".mp4";

                    String uploadFileName =  "videos/" + mUser.getUid() + "/" + fileName;

                    curTrial = new GaitTrial();
                    curTrial.fileName = uploadFileName;
                    curTrial.startTime = now.getTime(); //new Timestamp(now);
                    trials.add(curTrial);

                    ContentValues contentValues = new ContentValues();
                    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");

                    VideoCapture.OutputFileOptions outputFile = new VideoCapture.OutputFileOptions.Builder(
                            getContentResolver(),
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            contentValues
                    ).build();

                    showVideoStatus("Recording " + fileName);

                    videoCapture.startRecording(outputFile,
                            ContextCompat.getMainExecutor(this),
                            new VideoCapture.OnVideoSavedCallback() {

                                @Override
                                public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                                    Log.d(TAG, "Video saved. Uploading");

                                    startButton.setEnabled(true);
                                    stopButton.setEnabled(false);

                                    showVideoStatus("Uploading "  + fileName + "...");

                                    Uri file = outputFileResults.getSavedUri();

                                    StorageReference storageRef = storage.getReference().child(uploadFileName);

                                    storageRef.putFile(file)
                                            .addOnFailureListener(e -> showVideoStatus("Upload "  + fileName + " failed"))
                                            .addOnSuccessListener(taskSnapshot -> showVideoStatus("Upload "  + fileName + " succeeded"));
                                }

                                @Override
                                public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                                    Log.d(TAG, "Video error");
                                    showVideoStatus("Video Error!");
                                }
                            });
                }
        );

        stopButton.setOnClickListener(v -> {
            videoCapture.stopRecording();
            curTrial.endTime = new Date().getTime();
            updateLogger();
        });
    }

    public void updateLogger() {
        if (mGameLogger != null) {
            Gson gson = new Gson();
            String json = gson.toJson(trials);
            mGameLogger.finalize(trials.size(), json);
        }
    }

    public void onStop() {
        updateLogger();
        super.onStop();
    }

    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Log.d(TAG, "bindPreview");
        Preview preview = new Preview.Builder()
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        videoCapture = new VideoCapture.Builder().build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture);
    }

    void showUser() {
        TextView text = findViewById(R.id.userId);
        text.setText("User: " + mUser.getUid());
    }

    void showVideoStatus(String status) {
        TextView text = findViewById(R.id.videoStatus);
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

    // Implements VideoCapture use case, including start and stop capturing.
    private final void captureVideo() {
        viewBinding.videoCaptureButton.setEnabled(false);

        Recording curRecording = recording;
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop();
            recording = null;
            return;
        }

        // create and start a new recording session
        String name = new SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video");
        }

        MediaStoreOutputOptions mediaStoreOutputOptions = new MediaStoreOutputOptions
                .Builder(getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues)
                .build();

        recording = videoCapture.getOutput().
                prepareRecording(this, mediaStoreOutputOptions)
                .start(ContextCompat.getMainExecutor(this), videoRecordEvent -> {
                    if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                        // Handle the start of a new active recording
                        viewBinding.videoCaptureButton.setText(R.string.stop_capture);
                        viewBinding.videoCaptureButton.setEnabled(true);
                    }
                    else if (videoRecordEvent instanceof VideoRecordEvent.Pause) {
                        // Handle the case where the active recording is paused

                    } else if (videoRecordEvent instanceof VideoRecordEvent.Resume) {
                        // Handles the case where the active recording is resumed

                    } else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                        VideoRecordEvent.Finalize finalizeEvent =
                                (VideoRecordEvent.Finalize) videoRecordEvent;
                        // Handles a finalize event for the active recording, checking Finalize.getError()
                        int error = finalizeEvent.getError();
                        if (error == VideoRecordEvent.Finalize.ERROR_NONE) {
                            String msg = "Video capture succeeded: " +
                                    ((VideoRecordEvent.Finalize) videoRecordEvent).getOutputResults().getOutputUri();
                            Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();
                            Log.d(TAG, msg);
                        }
                        viewBinding.videoCaptureButton.setText(R.string.start_capture);
                        viewBinding.videoCaptureButton.setEnabled(true);
                    }

                    // All events, including VideoRecordEvent.Status, contain RecordingStats.
                    // This can be used to update the UI or track the recording duration.
                    RecordingStats recordingStats = videoRecordEvent.getRecordingStats();

                });
    }

    private final void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(getBaseContext());
        // Used to bind the lifecycle of cameras to the lifecycle owner
        cameraProviderFuture.addListener(() -> {
            // Preview
            Preview preview = new Preview.Builder()
                    .build();
            preview.setSurfaceProvider(viewBinding.viewFinder.getSurfaceProvider());

            // VideoCapture
            Recorder recorder = new Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                    .build();
            videoCapture = androidx.camera.video.VideoCapture.withOutput(recorder);

            // Select back camera as a default
            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build();
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                // Unbind use cases before rebinding
                cameraProvider.unbindAll();

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, videoCapture);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(getBaseContext()));
    }

    private final boolean allPermissionsGranted() {
        return (ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(getBaseContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }
}