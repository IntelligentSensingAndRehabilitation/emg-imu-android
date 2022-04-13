package org.sralab.emgimu.gaitvideoimu;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.RecordingStats;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.installations.FirebaseInstallations;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.gson.Gson;

import org.jetbrains.annotations.NotNull;
import org.sralab.emgimu.gaitvideoimu.databinding.ActivityGaitVideoImuBinding;
import org.sralab.emgimu.gaitvideoimu.stream_visualization.DeviceViewModel;
import org.sralab.emgimu.gaitvideoimu.stream_visualization.StreamingAdapter;
import org.sralab.emgimu.logging.FirebaseGameLogger;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kotlin.jvm.internal.Intrinsics;
import no.nordicsemi.android.nrftoolbox.widget.DividerItemDecoration;

public class GaitVideoImu extends AppCompatActivity {

    private static final String TAG = GaitVideoImu.class.getSimpleName();

    private DeviceViewModel dvm;
    private StreamingAdapter streamingAdapter;


    class GaitTrial {
        public String fileName;
        public long startTime;
        public long endTime;
        public long videoRecordEventStartTime;
    }
    ArrayList<GaitTrial> trials = new ArrayList<>();
    private GaitTrial curTrial;

    private FirebaseAuth mAuth;
    private FirebaseStorage storage;
    private FirebaseUser mUser;
    private FirebaseGameLogger mGameLogger;

    private ActivityGaitVideoImuBinding viewBinding;
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    @NotNull
    private static final String[] REQUIRED_PERMISSIONS = new String[] {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};

    private File videoDirectory;

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

    private void openCamera(int width, int height) {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivityGaitVideoImuBinding.inflate(getLayoutInflater());
        View view = viewBinding.getRoot();
        setContentView(view);

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

        // Set up the listener for video capture buttons
        //viewBinding.startButton.setOnClickListener(v -> captureVideo());
        //viewBinding.stopButton.setOnClickListener(v -> stopCaptureVideo());
        viewBinding.stopButton.setEnabled(false); // disable btn initially
        videoDirectory = createDirectory("GaitAnalysis", "Videos");
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

    /**
     * @brief                   Creates an empty directory with a nested directory to host the
     *                          desired app files - local video/picture copy.
     * @param directoryName     Main application directory.
     * @param subDirectoryName  Nested directory to contain desired files.
     * @return                  File object.
     */
    private File createDirectory(String directoryName, String subDirectoryName) {
        File directory = new File(Environment.getExternalStorageDirectory() + "/" + directoryName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!Files.exists(Paths.get(String.valueOf(directory)))) {
                directory.mkdir();
            }
        }
        File subDirectory = new File(directory + "/" + subDirectoryName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!Files.exists(Paths.get(String.valueOf(subDirectory)))) {
                subDirectory.mkdir();
            }
        }
        return subDirectory;
    }
}