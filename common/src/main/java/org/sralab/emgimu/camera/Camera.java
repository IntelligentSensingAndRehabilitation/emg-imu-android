package org.sralab.emgimu.camera;

import static android.hardware.camera2.CameraDevice.TEMPLATE_RECORD;

import static org.sralab.emgimu.camera.CameraUtils.computeTransformationMatrix;

import android.app.Activity;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SizeF;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class Camera {

    private static final String TAG = Camera.class.getSimpleName() + "Recording";

    protected Context context;
    protected TextureView textureView;
    protected CameraCallbacks callbacks;

    protected MediaRecorder mediaRecorder;
    protected CameraDevice cameraDevice;
    protected CameraCharacteristics characteristics;
    protected CaptureRequest.Builder captureRequestBuilder;
    protected CameraCaptureSession cameraCaptureSession;

    private long startRecordingTimestamp;
    private Long exposureOfFirstFrameTimestamp;

    protected File currentFile;
    protected Size imageDimension;
    protected int fps;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray() {{
        put(Surface.ROTATION_0, 90);
        put(Surface.ROTATION_90, 0);
        put(Surface.ROTATION_180, 270);
        put(Surface.ROTATION_270, 180);
    }};

    /** Setup a dictionary to map the lens orientation enum into a human-readable string */
    HashMap<Integer, String> lensOrientationMap = new HashMap<Integer, String>() {{
        put((int) CameraCharacteristics.LENS_FACING_BACK, "Back");
        put((int) CameraCharacteristics.LENS_FACING_FRONT, "Front");
        put((int) CameraCharacteristics.LENS_FACING_EXTERNAL,"External");
    }};

    public Camera(Context context, CameraCallbacks callbacks, TextureView textureView) {
        this.context = context;
        this.callbacks = callbacks;
        this.textureView = textureView;
    }

    // Callbacks for opening the camera
    CameraDevice.StateCallback openStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice _cameraDevice) {
            Log.d(TAG, "openStateCallback.onOpened");
            cameraDevice = _cameraDevice;
            createPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice _cameraDevice) {
            Log.d(TAG, "openStateCallback.onDisconnected");
            cameraDevice.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice _cameraDevice, int i) {
            String error = "";
            switch (i) {
                case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE:
                    error = "ERROR_CAMERA_DEVICE";
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED:
                    error = "ERROR_CAMERA_DISABLED";
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE:
                    error = "ERROR_CAMERA_IN_USE";
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE:
                    error = "ERROR_CAMERA_SERVICE";
                    break;
                case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE:
                    error = "ERROR_MAX_CAMERAS_IN_USE";
                    break;
            }

            cameraDevice.close();
            cameraDevice = null;

            throw new RuntimeException("openStateCallback.onError: " + error);
        }
    };

    /**
     * @brief Instantiates video stream for user to view inside the application.
     */
    public void createPreview() {
        SurfaceTexture texture = textureView.getSurfaceTexture();
        assert texture != null;

        int rotation = callbacks.getDisplayRotation();
        Matrix transform = computeTransformationMatrix(textureView, characteristics,
                imageDimension, rotation, 0);
        textureView.setTransform(transform);

        Surface previewSurface = new Surface(texture);
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);

            cameraDevice.createCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession captureSession) {

                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSession = captureSession;
                    captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

                    try {
                        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                                super.onCaptureStarted(session, request, timestamp, frameNumber);
                                exposureOfFirstFrameTimestamp = null;
                            }
                        }, callbacks.getBackgroundHandler());
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "cameraCaptureSession.setRepeatingRequest error");
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession captureSession) {
                    Log.e(TAG, "Configuration failed");
                }
            }, callbacks.getBackgroundHandler());
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

    private void setupMediaRecorder() throws IOException {
        Log.d(TAG, "setupMediaRecorder");
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setVideoSize(imageDimension.getWidth(), imageDimension.getHeight()); // absolutely necessary
        Log.d(TAG, "FPS: " + fps + " Resolution: " + imageDimension) ;
        mediaRecorder.setVideoFrameRate(fps);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoEncodingBitRate(10_000_000);

        int rotation = callbacks.getDisplayRotation();
        mediaRecorder.setOrientationHint(ORIENTATIONS.get(rotation));

        currentFile = callbacks.createNewFile("");
        callbacks.showVideoStatus("Recording " + callbacks.getSimpleFilename(currentFile), "gray");
        mediaRecorder.setOutputFile(currentFile.getAbsolutePath());

        mediaRecorder.prepare();
    }

    public void startVideoRecording() {
        Log.d(TAG, "startVideoRecording");
        closePreview();
        try {
            setupMediaRecorder();
            Log.d(TAG, "Recording " + callbacks.getSimpleFilename(currentFile));

            SurfaceTexture texture = textureView.getSurfaceTexture();

            captureRequestBuilder = cameraDevice.createCaptureRequest(TEMPLATE_RECORD);

            // setting the camera fps
            Range<Integer> fpsRange = new Range<>(fps,fps);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange); // This sets the frame rate accurately

            Surface previewSurface = new Surface(texture);
            captureRequestBuilder.addTarget(previewSurface);

            // MediaRecorder setup for surface
            Surface recorderSurface = mediaRecorder.getSurface();
            captureRequestBuilder.addTarget(recorderSurface);

            List<OutputConfiguration> outputConfig = Arrays.asList(new OutputConfiguration(previewSurface),
                    new OutputConfiguration(recorderSurface));

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                SessionConfiguration config = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputConfig,
                        context.getMainExecutor(), new CameraCaptureSession.StateCallback() {

                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession captureSession) {
                        Log.d(TAG, "onConfigured succeeded");
                        cameraCaptureSession = captureSession;
                        try {
                            mediaRecorder.start();
                            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(),
                                    new CameraCaptureSession.CaptureCallback() {
                                @Override
                                public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                                        super.onCaptureStarted(session, request, timestamp, frameNumber);
                                        if (exposureOfFirstFrameTimestamp == null) {
                                            exposureOfFirstFrameTimestamp = new Date().getTime();
                                        }
                                    }
                                }, callbacks.getBackgroundHandler());
                        } catch (CameraAccessException e) {
                            throw new RuntimeException("Error configuring recording", e);
                        }

                        startRecordingTimestamp = new Date().getTime();
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession captureSession) {
                        throw new RuntimeException("Configuration failed");
                    }

                });

                cameraDevice.createCaptureSession(config);

            } else {
                Log.e(TAG, "Not implemented yet");
            }

        } catch (IOException | CameraAccessException e) {
            Log.e(TAG, "Error in startVideoRecording");
            e.printStackTrace();
        }
    }

    public void stopVideoRecording() {
        Log.d(TAG, "stopVideoRecording " + callbacks.getSimpleFilename(currentFile));
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
        callbacks.pushVideoFileToFirebase(currentFile, exposureOfFirstFrameTimestamp, startRecordingTimestamp, false);
    }


    private class CameraInfo {
        Integer fps;
        String orientation;
        String cameraId;
        SizeF sensorSize;
        double fov;
        Size size;
    }

    /** Lists all video-capable cameras and supported resolution and FPS combinations */
    private List<CameraInfo> enumerateVideoCameras(CameraManager manager) {
        List<CameraInfo> availableCameras = new ArrayList<>();

        //  Iterate over the list of cameras and add those with high speed video recording
        //  capability to our output. This function only returns those cameras that declare
        //  constrained high speed video recording, but some cameras may be capable of doing
        //  unconstrained video recording with high enough FPS for some use cases and they will
        //  not necessarily declare constrained high speed video capability.
        String [] cameraList = null;
        try {
            cameraList = manager.getCameraIdList();
            for (String cameraId : cameraList) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                String orientation = lensOrientationMap(characteristics.get(CameraCharacteristics.LENS_FACING));

                // Get optics
                SizeF sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                double fov = 0;
                if (focalLengths.length > 0) {
                    float focalLength = focalLengths[0];
                    fov = 2 * Math.atan(sensorSize.getWidth() / (2 * focalLength));
                }

                // Query the available capabilities and output formats
                int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                StreamConfigurationMap cameraConfig = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                // Return cameras that declare to be backward compatible
                // Recording should always be done in the most efficient format, which is
                //  the format native to the camera framework
                if (Arrays.stream(capabilities).anyMatch(i -> i == CameraCharacteristics
                        .REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)) {
                    // For each size, list the expected FPS
                    for (Size outputSize : cameraConfig.getOutputSizes(SurfaceTexture.class)) {
                        double secondsPerFrame = cameraConfig.getOutputMinFrameDuration(SurfaceTexture.class, outputSize) /
                                        1_000_000_000.0;
                        // Compute the frames per second to let user select a configuration
                        int fps;
                        if (secondsPerFrame > 0) {
                            fps = (int) (1.0 / secondsPerFrame);
                        } else {
                            fps = 0;
                        }
                        CameraInfo cameraInfo = new CameraInfo();
                        cameraInfo.cameraId = cameraId;
                        cameraInfo.orientation = orientation;
                        cameraInfo.size = outputSize;
                        cameraInfo.fps = fps;
                        cameraInfo.fov = fov;
                        cameraInfo.sensorSize = sensorSize;
                        availableCameras.add(cameraInfo);
                    }
                }
            }
        } catch (CameraAccessException e) {
            throw new RuntimeException("Unable to access camera", e);
        }

        return availableCameras;
    }

    private CameraInfo selectMode(Size resolution, int fps) {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        List<CameraInfo> cameraInfoList = enumerateVideoCameras(manager);

        for (CameraInfo cameraInfo : cameraInfoList) {

            Log.d(TAG, "CameraID: " + cameraInfo.cameraId +
                    ". Orientation: " + cameraInfo.orientation +
                    ". Size: " + cameraInfo.size +
                    ". FPS: " + cameraInfo.fps +
                    ". FOV: " + cameraInfo.fov +
                    ". Sensor Size: " + cameraInfo.sensorSize);

            if (cameraInfo.fps == fps && cameraInfo.size.getHeight() == resolution.getHeight()  && cameraInfo.size.getWidth() == resolution.getWidth()) { // && cameraInfo.orientation.toUpperCase() == "BACK") {
                return cameraInfo;
            }
        }

        return null;
    }

    /**
     * @brief Establishes connection with the camera hardware.
     */
    public void openCamera(Size resolution, int fps) {
        Log.d(TAG, "openCamera");
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        CameraInfo cameraInfo = selectMode(resolution, fps);
        if (cameraInfo == null && fps > 30) {
            Log.w(TAG, "Unable to satisfy request for " + resolution + " at " + fps + ". Falling back to 30.");

            fps = 30;
            cameraInfo = selectMode(resolution, fps);
        }
        if (cameraInfo == null) {
            throw new RuntimeException("Unable to match requested camera settings. Resolution: " + resolution + " FPS: " + fps);
        }

        String cameraId = cameraInfo.cameraId;

        imageDimension = cameraInfo.size;
        Log.d(TAG, "Resolution: " + imageDimension + " FPS " + fps);
        this.fps = fps;

        mediaRecorder = new MediaRecorder();

        // Explicitly check user permissions
        if (!callbacks.checkPermissions())
            return;

        try {
            characteristics = manager.getCameraCharacteristics(cameraId);
            manager.openCamera(cameraId, openStateCallback, callbacks.getBackgroundHandler());
        } catch (CameraAccessException e) {
            throw new RuntimeException("Unable to access camera", e);
        }

    }

    public void closeCamera() {
        Log.d(TAG, "closeCamera");
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

    private String lensOrientationMap(Integer integer) {
        return lensOrientationMap.get(integer);
    }

}
