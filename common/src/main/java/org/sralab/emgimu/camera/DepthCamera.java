// Code originally from https://github.com/plluke/tof and previously licensed under
// The Unlicense

package org.sralab.emgimu.camera;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
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

    private Activity context;
    private CameraActivity cameraActivity;
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

    public DepthCamera(Activity context, TextureView textureView) {
        this.context = context;
        this.textureView = textureView;

        imageAvailableListener = new DepthFrameAvailableListener();

        cameraActivity = (CameraActivity) context;
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

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

        int rotation = context.getWindowManager().getDefaultDisplay().getRotation();
        Matrix transform1 = computeTransformationMatrix(textureView, characteristics,
                new Size(DepthFrameAvailableListener.HEIGHT, DepthFrameAvailableListener.WIDTH), rotation);
        Matrix transform2 = defaultBitmapTransform(textureView);

        Log.d(TAG, "Matrix: " + transform1 + " Matrix: " + transform2);
        textureView.setTransform(transform1);

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
                    }, cameraActivity.getBackgroundHandler());

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void onCaptureSessionConfigured(@NonNull CameraCaptureSession session) {
        Log.i(TAG,"Capture Session created");
        previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            session.setRepeatingRequest(previewBuilder.build(), null, cameraActivity.getBackgroundHandler());
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
        cameraActivity.pushVideoFileToFirebase(currentFile, imageAvailableListener.getFirstTimestamp(),
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

        int rotation = context.getWindowManager().getDefaultDisplay().getRotation();
        Log.d(TAG, "Video rotation: " + rotation);
        mediaRecorder.setOrientationHint(ORIENTATIONS.get(rotation));

        currentFile = cameraActivity.createNewFile("_depth");
        mediaRecorder.setOutputFile(currentFile.getAbsolutePath());

        Log.d(TAG, "Recording to " + currentFile.getAbsolutePath());

        mediaRecorder.prepare();
        mediaRecorder.start();
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

        // Adding fixed offset to rotations, which seems to be required for depth sensor
        switch (surfaceRotation) {
            case Surface.ROTATION_90:
                surfaceRotationDegrees = 180;
                break;
            case Surface.ROTATION_180:
                surfaceRotationDegrees = 270;
                break;
            case Surface.ROTATION_270:
                surfaceRotationDegrees = 0;
                break;
            default:
                surfaceRotationDegrees = 90;
        }

        /* Rotation required to transform from the camera sensor orientation to the
         * device's current orientation in degrees. */
        int relativeRotation = computeRelativeRotation(characteristics, surfaceRotationDegrees);
        relativeRotation = 180;

        Log.d(TAG, "Matrix calc. Preview size: " + previewSize + " relativeRotation: " +
                relativeRotation + " surfaceRotationDegrees: " + surfaceRotationDegrees +
                " textureView: " + textureView.getWidth() + " " +
                textureView.getHeight() );

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

    private Matrix defaultBitmapTransform(TextureView view) {

        Matrix matrix = new Matrix();
        int centerX = view.getWidth() / 2;
        int centerY = view.getHeight() / 2;

        int bufferWidth = DepthFrameAvailableListener.WIDTH;
        int bufferHeight = DepthFrameAvailableListener.HEIGHT;

        RectF bufferRect = new RectF(0, 0, bufferWidth, bufferHeight);
        RectF viewRect = new RectF(0, 0, view.getWidth(), view.getHeight());
        matrix.setRectToRect(bufferRect, viewRect, Matrix.ScaleToFit.CENTER);
        matrix.postRotate(270, centerX, centerY);

        return matrix;
    }
}
