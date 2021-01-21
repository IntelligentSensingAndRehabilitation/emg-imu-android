package org.sralab.emgimu.spasticitymonitor.ui.home;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import androidx.camera.view.PreviewView;

import com.google.gson.Gson;
import com.google.common.util.concurrent.ListenableFuture;

import org.sralab.emgimu.logging.FirebaseGameLogger;
import org.sralab.emgimu.service.IEmgImuServiceBinder;
import org.sralab.emgimu.spasticitymonitor.R;
import org.sralab.emgimu.spasticitymonitor.ui.stream_visualization.DeviceViewModel;
import org.sralab.emgimu.spasticitymonitor.ui.stream_visualization.StreamingAdapter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import no.nordicsemi.android.nrftoolbox.widget.DividerItemDecoration;

public class HomeFragment extends Fragment {

    private final static String TAG = HomeFragment.class.getSimpleName();

    private DeviceViewModel dvm;
    private StreamingAdapter streamingAdapter;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;
    private VideoCapture videoCapture;

    ArrayList<String> fileNames = new ArrayList<>();

    private FirebaseGameLogger mGameLogger;

    @SuppressLint("RestrictedApi")
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_home, container, false);

        final RecyclerView recyclerView = root.findViewById(R.id.emg_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(root.getContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(root.getContext(), DividerItemDecoration.VERTICAL_LIST));

        dvm = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(getActivity().getApplication())).get(DeviceViewModel.class);
        dvm.getDevicesLiveData().observe(getViewLifecycleOwner(), devices -> streamingAdapter.notifyDataSetChanged());
        recyclerView.setAdapter(streamingAdapter = new StreamingAdapter(this, dvm));


        dvm.getServiceLiveData().observe(getViewLifecycleOwner(), new Observer<IEmgImuServiceBinder>() {
            @Override
            public void onChanged(IEmgImuServiceBinder binder) {
                if (binder != null) {
                    Log.d(TAG, "Service updated.");
                    long startTime = new Date().getTime();
                    mGameLogger = new FirebaseGameLogger(dvm.getService(), getString(R.string.app_name), startTime);
                } else {
                    mGameLogger = null;
                }
            }
        });

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // Request camera-related permissions
            requestPermissions(new String[] {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 10);
        }

        previewView = root.findViewById(R.id.cameraView);
        cameraProviderFuture = ProcessCameraProvider.getInstance(getContext());
        cameraProviderFuture.addListener(() -> {
                    try {
                        ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                        HomeFragment.this.bindPreview(cameraProvider);
                    } catch (ExecutionException | InterruptedException e) {
                        // No errors need to be handled for this Future.
                        // This should never be reached.
                    }
                }, ContextCompat.getMainExecutor(getContext()));

        Button startButton = root.findViewById(R.id.startButton);
        Button stopButton = root.findViewById(R.id.stopButton);

        startButton.setOnClickListener(v ->
                {
                    startButton.setEnabled(false);
                    stopButton.setEnabled(true);

                    SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
                    Date now = new Date();
                    String fileName = formatter.format(now) + ".mp4";

                    fileNames.add(fileName);

                    ContentValues contentValues = new ContentValues();
                    contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");

                    VideoCapture.OutputFileOptions outputFile = new VideoCapture.OutputFileOptions.Builder(
                            getContext().getContentResolver(),
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            contentValues
                    ).build();

                    videoCapture.startRecording(outputFile,
                        ContextCompat.getMainExecutor(getContext()),
                        new VideoCapture.OnVideoSavedCallback() {

                            @Override
                            public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                                Log.d(TAG, "Video saved");
                            }

                            @Override
                            public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                                Log.d(TAG, "Video error");
                            }
                    });
            }
        );

        stopButton.setOnClickListener(v -> {
            videoCapture.stopRecording();
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
        });

        return root;
    }

    public void onStop() {
        super.onStop();

        if (mGameLogger != null) {
            Gson gson = new Gson();
            String json = gson.toJson(fileNames);
            mGameLogger.finalize(fileNames.size(), json);
        }
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

}
