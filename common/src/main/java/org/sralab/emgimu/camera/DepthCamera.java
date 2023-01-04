// Code originally from https://github.com/plluke/tof and previously licensed under
// The Unlicense

package org.sralab.emgimu.camera;

import static org.sralab.emgimu.camera.CameraUtils.computeTransformationMatrix;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SizeF;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class DepthCamera {
    private static final String TAG = DepthCamera.class.getSimpleName();

    private static int FPS_MIN = 15;
    private static int FPS_MAX = 30;

    private Context context;
    private CameraCallbacks callbacks;
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    protected CameraCharacteristics characteristics;
    private ImageReader previewReader;
    private ImageReader recordingReader;
    private CaptureRequest.Builder captureRequestBuilder;
    private DepthFrameAvailableListener previewImageAvailableListener;
    private DepthFrameAvailableListener recordingImageAvailableListener;
    private TextureView textureView;
    private Surface previewSurface;

    private Long exposureOfFirstFrameTimestamp;
    private ArrayList<Long> recordingTimestamps;

    // Note this is a slightly altered version due to quirk with depth
    // camera
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray() {{
        put(Surface.ROTATION_0, 270);
        put(Surface.ROTATION_90, 180);
        put(Surface.ROTATION_180, 90);
        put(Surface.ROTATION_270, 0);
    }};

    protected MediaRecorder mediaRecorder;
    protected File currentFile;

    public DepthCamera(Context context, CameraCallbacks callbacks, TextureView textureView) {
        this.context = context;
        this.callbacks = callbacks;
        this.textureView = textureView;

        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        // Map DEPTH16 images to RGB images and pass them onto the texture (connected
        // below)
        previewImageAvailableListener = new DepthFrameAvailableListener();
        previewReader = ImageReader.newInstance(DepthFrameAvailableListener.WIDTH,
                DepthFrameAvailableListener.HEIGHT, ImageFormat.DEPTH16,2);
        previewReader.setOnImageAvailableListener(previewImageAvailableListener, null);

        // Use a different listener when recording to files, to make sure no frames from
        // preview accidentally get pushed to file when it is connected
        recordingImageAvailableListener = new DepthFrameAvailableListener();
        recordingReader = ImageReader.newInstance(recordingImageAvailableListener.WIDTH,
                recordingImageAvailableListener.HEIGHT, ImageFormat.DEPTH16,2);
        recordingReader.setOnImageAvailableListener(recordingImageAvailableListener, null);
    }

    // Open the front depth camera and start sending frames
    public void openFrontDepthCamera() {

        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
                final String cameraId = getFrontDepthCameraID();
                Log.d(TAG, "Depth camera: " + cameraId);
                openCamera(cameraId);

                previewSurface = new Surface(surfaceTexture);
                previewImageAvailableListener.setPreviewSurface(previewSurface);
                recordingImageAvailableListener.setPreviewSurface(previewSurface);
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
                if (characteristics != null) {
                    int rotation = callbacks.getDisplayRotation();
                    Matrix transform = computeTransformationMatrix(textureView, characteristics,
                            new Size(DepthFrameAvailableListener.HEIGHT, DepthFrameAvailableListener.WIDTH), rotation, 90);
                    textureView.setTransform(transform);
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

            }
        });
    }

    public void closeCamera() {
        if (cameraDevice != null)
            cameraDevice.close();
    }

    public String getFrontDepthCameraID() {
        try {
            for (String camera : cameraManager.getCameraIdList()) {
                characteristics = cameraManager.getCameraCharacteristics(camera);
                final int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                Log.d(TAG, "Capabilities: " + Arrays.toString(capabilities));
                boolean facingBack = characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_BACK;
                boolean depthCapable = false;
                for (int capability : capabilities) {
                    boolean capable = capability == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT;
                    depthCapable = depthCapable || capable;
                }
                if (depthCapable && facingBack) {
                    // Note that the sensor size is much larger than the available capture size
                    SizeF sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                    Log.i(TAG, "Sensor size: " + sensorSize);

                    // Since sensor size doesn't actually match capture size and because it is
                    // reporting an extremely wide aspect ratio, this FoV is bogus
                    float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);

                    int sensor_orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    if (focalLengths.length > 0) {
                        float focalLength = focalLengths[0];
                        double fov = 2 * Math.atan(sensorSize.getWidth() / (2 * focalLength));
                        if (sensor_orientation == 90 || sensor_orientation == 270) {
                            Log.d(TAG, "here");
                            fov = 2 * Math.atan(sensorSize.getHeight() / (2 * focalLength));
                        }
                        Log.i(TAG, "Calculated FoV: " + fov);
                    }
                    Log.d(TAG, "Focal lengths " + focalLengths.length + " " + focalLengths[0]);
                    Log.d(TAG, "Sensor Orientation: " + sensor_orientation);
                    return camera;
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Could not initialize Camera Cache");
            e.printStackTrace();
        }
        return null;
    }

    private void openCamera(String cameraId) {
        try{
            int permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA);
            if(PackageManager.PERMISSION_GRANTED == permission) {
                cameraManager.openCamera(cameraId, openStateCallback, null);
            }else{
                Log.e(TAG,"Permission not available to open camera");
            }
        }catch (CameraAccessException | IllegalStateException | SecurityException e){
            Log.e(TAG,"Opening Camera has an Exception " + e);
            e.printStackTrace();
        }

        mediaRecorder = new MediaRecorder();
    }

    /**** CameraDevice.StateCallback callbacks *****/
    // Callbacks for opening the camera
    CameraDevice.StateCallback openStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {

        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {

        }
    };

    /**** End CameraDevice.StateCallback callbacks *****/

    private void createPreview() {
        int rotation = callbacks.getDisplayRotation();
        Matrix transform = computeTransformationMatrix(textureView, characteristics,
                new Size(DepthFrameAvailableListener.HEIGHT, DepthFrameAvailableListener.WIDTH), rotation, 90);

        textureView.setTransform(transform);

        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, 0);
            Range<Integer> fpsRange = new Range<>(FPS_MIN, FPS_MAX);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
            captureRequestBuilder.addTarget(previewReader.getSurface());

            List<Surface> targetSurfaces = Arrays.asList(previewReader.getSurface());
            cameraDevice.createCaptureSession(targetSurfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            onCaptureSessionConfigured(session, true);
                        }
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG,"!!! Creating Capture Session failed due to internal error ");
                        }
                    }, callbacks.getBackgroundHandler());

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void onCaptureSessionConfigured(@NonNull CameraCaptureSession session, boolean preview) {
        Log.i(TAG,"Capture Session created");
        this.cameraCaptureSession = session;
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

        try {

            CameraCaptureSession.CaptureCallback callback = null;
            if (!preview) {
                Log.d(TAG, "Installing callback");
                exposureOfFirstFrameTimestamp = null;
                recordingTimestamps = new ArrayList<>();
                callback = recordingFrameCallback;
                mediaRecorder.start();
            }

            session.setRepeatingRequest(captureRequestBuilder.build(), callback, callbacks.getBackgroundHandler());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // If recording, track timestamps
    private CameraCaptureSession.CaptureCallback recordingFrameCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
            if (exposureOfFirstFrameTimestamp == null) {
                exposureOfFirstFrameTimestamp = new Date().getTime();
            }
            recordingTimestamps.add(new Date().getTime());
            recordingTimestamps.add(timestamp);
        }
    };

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
        mediaRecorder.setVideoSize(DepthFrameAvailableListener.WIDTH,
                DepthFrameAvailableListener.HEIGHT); // absolutely necessary
        int fps = 30;
        Log.d(TAG, "FPS: " + fps);
        mediaRecorder.setVideoFrameRate(fps);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setVideoEncodingBitRate(500_000);

        int rotation = callbacks.getDisplayRotation();
        Log.d(TAG, "Video rotation: " + rotation);
        mediaRecorder.setOrientationHint(ORIENTATIONS.get(rotation));

        currentFile = callbacks.createNewFile("_depth");
        mediaRecorder.setOutputFile(currentFile.getAbsolutePath());

        Log.d(TAG, "Recording to " + currentFile.getAbsolutePath());

        mediaRecorder.prepare();
    }

    public void startVideoRecording() {
        Log.d(TAG, "startVideoRecording");
        closePreview();
        try {
            setupMediaRecorder();

            // Only one surface to write to since we have to map the DEPTH16 to
            // RGB in the image listener and don't want two of them. Passing
            // this surface informs the listener to write the video file.
            recordingImageAvailableListener.setListeningSurface(mediaRecorder.getSurface());

            int rotation = callbacks.getDisplayRotation();
            Matrix transform = computeTransformationMatrix(textureView, characteristics,
                    new Size(DepthFrameAvailableListener.HEIGHT, DepthFrameAvailableListener.WIDTH), rotation, 90);

            textureView.setTransform(transform);

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            Range<Integer> fpsRange = new Range<>(FPS_MIN, FPS_MAX);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
            captureRequestBuilder.addTarget(recordingReader.getSurface());

            List<Surface> targetSurfaces = Arrays.asList(recordingReader.getSurface());
            cameraDevice.createCaptureSession(targetSurfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            onCaptureSessionConfigured(session, false);
                        }
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG,"!!! Creating Capture Session failed due to internal error ");
                        }
                    }, callbacks.getBackgroundHandler());

        } catch (CameraAccessException | IOException e) {
            throw new RuntimeException("Problem starting video recording: " + e);
        }

    }

    public void stopVideoRecording() {
        Log.d(TAG, "stopVideoRecording");
        recordingImageAvailableListener.setListeningSurface(null);
        try {
            cameraCaptureSession.stopRepeating();
            cameraCaptureSession.abortCaptures();
        } catch (CameraAccessException e) {
            throw new RuntimeException("Problem stopping video recording: " + e);
        }
        // Stop recording
        mediaRecorder.stop();
        mediaRecorder.reset();
        createPreview();
        callbacks.pushVideoFileToFirebase(currentFile, exposureOfFirstFrameTimestamp, true, recordingTimestamps);
    }

}