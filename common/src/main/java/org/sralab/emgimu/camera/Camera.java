package org.sralab.emgimu.camera;

import static android.hardware.camera2.CameraDevice.TEMPLATE_RECORD;

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

    protected Activity context;
    protected TextureView textureView;
    protected CameraActivity activity;

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
    protected Range<Integer> fpsRange;

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

    public Camera(Activity context, CameraActivity activity, TextureView textureView) {
        this.context = context;
        this.activity = activity;
        this.textureView = textureView;
    }

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
            Log.d(TAG, "openStateCallback.onError");
            switch (i) {
                case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE:
                    Log.e(TAG, "ERROR_CAMERA_DEVICE");
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED:
                    Log.e(TAG, "ERROR_CAMERA_DISABLED");
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE:
                    Log.e(TAG, "ERROR_CAMERA_IN_USE");
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE:
                    Log.e(TAG, "ERROR_CAMERA_SERVICE");
                    break;
                case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE:
                    Log.e(TAG, "ERROR_MAX_CAMERAS_IN_USE");
                    break;
            }

            cameraDevice.close();
            cameraDevice = null;
        }
    };
    //endregion

    /**
     * @brief Instantiates video stream for user to view inside the application.
     */
    public void createPreview() {
        SurfaceTexture texture = textureView.getSurfaceTexture();
        assert texture != null;

        Matrix transform = computeTransformationMatrix(textureView, characteristics, new Size(textureView.getWidth(), textureView.getHeight()), 0);
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
                        }, activity.getBackgroundHandler());
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "cameraCaptureSession.setRepeatingRequest error");
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession captureSession) {
                    Log.e(TAG, "Configuration failed");
                }
            }, activity.getBackgroundHandler());
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
        Log.d(TAG, "FPS: " + fps);
        mediaRecorder.setVideoFrameRate(fps);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoEncodingBitRate(10_000_000);

        int rotation = context.getWindowManager().getDefaultDisplay().getRotation();
        mediaRecorder.setOrientationHint(ORIENTATIONS.get(rotation));

        currentFile = activity.createNewFile("");
        activity.showVideoStatus("Recording " + activity.getSimpleFilename(currentFile), "gray");
        mediaRecorder.setOutputFile(currentFile.getAbsolutePath());

        mediaRecorder.prepare();
    }

    public void startVideoRecording() {
        Log.d(TAG, "startVideoRecording");
        closePreview();
        try {
            setupMediaRecorder();
            Log.d(TAG, "Recording " + activity.getSimpleFilename(currentFile));

            SurfaceTexture texture = textureView.getSurfaceTexture();

            captureRequestBuilder = cameraDevice.createCaptureRequest(TEMPLATE_RECORD);

            // setting the camera fps
            Log.d(TAG, "fpsRange: " + fpsRange);
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
                                            Log.d(TAG, "First frame");
                                            exposureOfFirstFrameTimestamp = new Date().getTime();
                                        }
                                    }
                                }, activity.getBackgroundHandler());
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }

                        startRecordingTimestamp = new Date().getTime();
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession captureSession) {
                        Log.d(TAG, "Configuration failed");
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
        Log.d(TAG, "stopVideoRecording " + activity.getSimpleFilename(currentFile));
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
        activity.pushVideoFileToFirebase(currentFile, exposureOfFirstFrameTimestamp, startRecordingTimestamp);
    }


    private class CameraInfo {
        Integer fps;
        String name;
        String cameraId;
        Size size;
    }

    /** Lists all video-capable cameras and supported resolution and FPS combinations */
    private List<CameraInfo> enumerateVideoCameras(CameraManager manager) throws CameraAccessException {
        List<CameraInfo> availableCameras = new ArrayList<>();

        //  Iterate over the list of cameras and add those with high speed video recording
        //  capability to our output. This function only returns those cameras that declare
        //  constrained high speed video recording, but some cameras may be capable of doing
        //  unconstrained video recording with high enough FPS for some use cases and they will
        //  not necessarily declare constrained high speed video capability.
        for (String cameraId : manager.getCameraIdList()) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            String orientation = lensOrientationMap(characteristics.get(CameraCharacteristics.LENS_FACING));
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
                    double secondsPerFrame =
                            cameraConfig.getOutputMinFrameDuration(SurfaceTexture.class, outputSize) /
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
                    cameraInfo.name = orientation;
                    cameraInfo.size = outputSize;
                    cameraInfo.fps = fps;
                    availableCameras.add(cameraInfo);
                }
            }
        }
        return availableCameras;
    }

    /**
     * @brief Establishes connection with the camera hardware.
     */
    public void openCamera(Size resolution, int fps) {
        Log.d(TAG, "openCamera");
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[0]; // Camera 0 facing CAMERA_FACING_BACK
            characteristics = manager.getCameraCharacteristics(cameraId);
            /* Queries the camera capabilities and stores it into the list.
             *  Then we find the index of the camera with the HD resolution of 1920x1080, and feed it
             *  downstream */
            List<CameraInfo> cameraInfoList = new ArrayList<>(enumerateVideoCameras(manager));
            int counter = 0;
            int indexOfCameraWithResolutionHD = 0;
            for(CameraInfo cameraInfo : cameraInfoList) {
                Log.d(TAG, "CameraID: " + cameraInfo.cameraId +
                        ". Orientation: " + cameraInfo.name +
                        ". Size: " + cameraInfo.size +
                        ". FPS: " + cameraInfo.fps);

                if (cameraInfo.cameraId.equals(cameraId) && cameraInfo.size.equals(resolution)) {
                    indexOfCameraWithResolutionHD = counter;
                }
                counter++;
            }
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[indexOfCameraWithResolutionHD]; // TODO: should allow selecting resolution
            fpsRange = new Range<>(fps,fps); // Determined through observation that, when the range is outside of what the camera can provide,
            this.fps = fps;
            //  will automatically default to a lower fps (30 most likely) without crashing the app.
            mediaRecorder = new MediaRecorder();

            // Explicitly check user permissions
            if (!activity.checkPermissions())
                return;

            manager.openCamera(cameraId, openStateCallback, activity.getBackgroundHandler());
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error in openCamera");
            e.printStackTrace();
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

    /**
     * Computes rotation required to transform the camera sensor output orientation to the
     * device's current orientation in degrees.
     *
     * @param characteristics The CameraCharacteristics to query for the sensor orientation.
     * @param surfaceRotationDegrees The current device orientation as a Surface constant.
     * @return Relative rotation of the camera sensor output.
     */
    public int computeRelativeRotation(CameraCharacteristics characteristics, int surfaceRotationDegrees) {
        Integer sensorOrientationDegrees = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        // Reverse device orientation for back-facing cameras.
        int sign = characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT ? 1 : -1;

        // Calculate desired orientation relative to camera orientation to make
        // the image upright relative to the device orientation.
        return (sensorOrientationDegrees - surfaceRotationDegrees * sign + 360) % 360;
    }

    /**
     * This method calculates the transformation matrix that we need to apply to the
     * TextureView to avoid a distorted preview.
     */
    public Matrix computeTransformationMatrix(
            TextureView textureView,
            CameraCharacteristics characteristics,
            Size previewSize,
            int surfaceRotation
    ) {
        Matrix matrix = new Matrix();

        int surfaceRotationDegrees;

        switch (surfaceRotation) {
            case Surface.ROTATION_90:
                surfaceRotationDegrees = 90;
                break;
            case Surface.ROTATION_180:
                surfaceRotationDegrees = 180;
                break;
            case Surface.ROTATION_270:
                surfaceRotationDegrees = 270;
                break;
            default:
                surfaceRotationDegrees = 0;
        }

        /* Rotation required to transform from the camera sensor orientation to the
         * device's current orientation in degrees. */
        int relativeRotation = computeRelativeRotation(characteristics, surfaceRotationDegrees);

        /* Scale factor required to scale the preview to its original size on the x-axis. */
        float scaleX = (relativeRotation % 180 == 0)
                ? (float) textureView.getWidth() / previewSize.getWidth()
                : (float) textureView.getWidth() / previewSize.getHeight();

        /* Scale factor required to scale the preview to its original size on the y-axis. */
        float scaleY = (relativeRotation % 180 == 0)
                ? (float) textureView.getHeight() / previewSize.getHeight()
                : (float) textureView.getHeight() / previewSize.getWidth();

        /* Scale factor required to fit the preview to the TextureView size. */
        float finalScale = Math.min(scaleX, scaleY);

        /* The scale will be different if the buffer has been rotated. */
        if (relativeRotation % 180 == 0) {
            matrix.setScale(
                    textureView.getHeight() / (float) textureView.getWidth() / scaleY * finalScale,
                    textureView.getWidth() / (float) textureView.getHeight() / scaleX * finalScale,
                    textureView.getWidth() / 2f,
                    textureView.getHeight() / 2f
            );
        } else {
            matrix.setScale(
                    1 / scaleX * finalScale,
                    1 / scaleY * finalScale,
                    textureView.getWidth() / 2f,
                    textureView.getHeight() / 2f
            );
        }

        // Rotate the TextureView to compensate for the Surface's rotation.
        matrix.postRotate(
                (float) -surfaceRotationDegrees,
                textureView.getWidth() / 2f,
                textureView.getHeight() / 2f
        );

        return matrix;
    }

}
