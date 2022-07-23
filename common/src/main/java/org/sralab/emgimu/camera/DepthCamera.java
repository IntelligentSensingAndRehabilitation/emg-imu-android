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
import java.util.Arrays;
import java.util.List;

public class DepthCamera extends CameraDevice.StateCallback {
    private static final String TAG = DepthCamera.class.getSimpleName();

    private static int FPS_MIN = 15;
    private static int FPS_MAX = 30;

    private Context context;
    private CameraCallbacks callbacks;
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    protected CameraCharacteristics characteristics;
    private ImageReader previewReader;
    private CaptureRequest.Builder previewBuilder;
    private DepthFrameAvailableListener imageAvailableListener;
    private TextureView textureView;
    private Surface previewSurface;

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
        imageAvailableListener = new DepthFrameAvailableListener();
        previewReader = ImageReader.newInstance(DepthFrameAvailableListener.WIDTH,
                DepthFrameAvailableListener.HEIGHT, ImageFormat.DEPTH16,2);
        previewReader.setOnImageAvailableListener(imageAvailableListener, null);
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
                imageAvailableListener.setPreviewSurface(previewSurface);
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {

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
                    if (focalLengths.length > 0) {
                        float focalLength = focalLengths[0];
                        double fov = 2 * Math.atan(sensorSize.getWidth() / (2 * focalLength));
                        Log.i(TAG, "Calculated FoV: " + fov);
                    }
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
                cameraManager.openCamera(cameraId, this, null);
            }else{
                Log.e(TAG,"Permission not available to open camera");
            }
        }catch (CameraAccessException | IllegalStateException | SecurityException e){
            Log.e(TAG,"Opening Camera has an Exception " + e);
            e.printStackTrace();
        }

        mediaRecorder = new MediaRecorder();
    }


    @Override
    public void onOpened(@NonNull CameraDevice camera) {
        cameraDevice = camera;

        int rotation = callbacks.getDisplayRotation();
        Matrix transform = computeTransformationMatrix(textureView, characteristics,
                new Size(DepthFrameAvailableListener.HEIGHT, DepthFrameAvailableListener.WIDTH), rotation, 90);

        textureView.setTransform(transform);

        try {
            previewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.set(CaptureRequest.JPEG_ORIENTATION, 0);
            Range<Integer> fpsRange = new Range<>(FPS_MIN, FPS_MAX);
            previewBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
            previewBuilder.addTarget(previewReader.getSurface());

            List<Surface> targetSurfaces = Arrays.asList(previewReader.getSurface());
            camera.createCaptureSession(targetSurfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            onCaptureSessionConfigured(session);
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

    private void onCaptureSessionConfigured(@NonNull CameraCaptureSession session) {
        Log.i(TAG,"Capture Session created");
        previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            session.setRepeatingRequest(previewBuilder.build(), null, callbacks.getBackgroundHandler());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onDisconnected(@NonNull CameraDevice camera) {

    }

    @Override
    public void onError(@NonNull CameraDevice camera, int error) {

    }
    public void startVideoRecording() {
        Log.d(TAG, "startVideoRecording");
        try {
            setupMediaRecorder();
        } catch (IOException e) {
            e.printStackTrace();
        }
        imageAvailableListener.setListeningSurface(mediaRecorder.getSurface());
    }

    public void stopVideoRecording() {
        Log.d(TAG, "stopVideoRecording");
        imageAvailableListener.setListeningSurface(null);
        mediaRecorder.stop();
        mediaRecorder.reset();
        callbacks.pushVideoFileToFirebase(currentFile, imageAvailableListener.getFirstTimestamp(),
                imageAvailableListener.getFirstTimestamp(), true);
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
        mediaRecorder.start();
    }

}
