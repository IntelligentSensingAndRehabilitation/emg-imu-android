package org.sralab.emgimu.camera;

import static org.sralab.emgimu.camera.Camera.selectMode;
import static org.sralab.emgimu.camera.CameraUtils.computeTransformationMatrix;
import static org.sralab.emgimu.camera.CameraUtils.getRotationDegrees;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCaptureSession.CaptureCallback;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.RecordingConfig;
import com.google.ar.core.Session;
import com.google.ar.core.SharedCamera;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.RecordingFailedException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;

import java.io.File;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

public class ARCameraTracking {

    private static final String TAG = ARCameraTracking.class.getSimpleName();

    protected Session session;
    protected File currentFile;

    protected Context context;
    protected CameraCallbacks callbacks;
    protected GLSurfaceView surfaceView;

    SharedCamera sharedCamera;
    CameraDevice cameraDevice;
    CameraCaptureSession cameraCaptureSession;

    public ARCameraTracking(Context context, CameraCallbacks callbacks, GLSurfaceView surfaceView) {
        this.context = context;
        this.callbacks = callbacks;
        this.surfaceView = surfaceView;

        assert this.surfaceView != null;


        try {
            session = new Session(context, EnumSet.of(Session.Feature.SHARED_CAMERA));
        } catch (UnavailableArcoreNotInstalledException e) {
            e.printStackTrace();
        } catch (UnavailableApkTooOldException e) {
            e.printStackTrace();
        } catch (UnavailableSdkTooOldException e) {
            e.printStackTrace();
        } catch (UnavailableDeviceNotCompatibleException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("MissingPermission")
    public void openCamera(Size resolution, int fps) {
        sharedCamera = session.getSharedCamera();
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        Camera.CameraInfo cameraInfo = selectMode(manager, resolution, fps);

        try {
            manager.openCamera(
                    cameraInfo.cameraId,
                    sharedCamera.createARDeviceStateCallback(openStateCallback, callbacks.getBackgroundHandler()),
                    callbacks.getBackgroundHandler());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }


    // Callbacks for opening the camera
    CameraDevice.StateCallback openStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            Log.d(TAG, "openStateCallback.onOpened");
            ARCameraTracking.this.cameraDevice = cameraDevice;
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

    CaptureRequest.Builder previewCaptureRequestBuilder;
    Surface previewSurface;

    /**
     * @brief Instantiates video stream for user to view inside the application.
     */
    public void createPreview() {
        Log.d(TAG, "createPreview");

        int rotation = callbacks.getDisplayRotation();
        /*Matrix transform = computeTransformationMatrix(textureView, characteristics,
                imageDimension, rotation, 0);
        textureView.setTransform(transform);*/

        //previewSurface = surfaceView.getHolder().getSurface();
        try {
            previewCaptureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            // Build a list of surfaces, starting with ARCore provided surfaces.
            List<Surface> surfaceList = sharedCamera.getArCoreSurfaces();
            //surfaceList.add(previewSurface);

            // Add ARCore surfaces and CPU image surface targets.
            for (Surface surface : surfaceList) {
                Log.d(TAG, "Adding surface");
                previewCaptureRequestBuilder.addTarget(surface);
            }

            // Wrap our callback in a shared camera callback.
            CameraCaptureSession.StateCallback wrappedCallback =
                    sharedCamera.createARSessionStateCallback(cameraSessionStateCallback, callbacks.getBackgroundHandler());

            // Create a camera capture session for camera preview using an ARCore wrapped callback.
            cameraDevice.createCaptureSession(surfaceList, wrappedCallback, callbacks.getBackgroundHandler());

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    CameraCaptureSession captureSession;
    boolean arcoreActive = false;
    boolean arMode = true;

    // Repeating camera capture session state callback.
    CameraCaptureSession.StateCallback cameraSessionStateCallback =
            new CameraCaptureSession.StateCallback() {

                // Called when ARCore first configures the camera capture session after
                // initializing the app, and again each time the activity resumes.
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, "onConfigured");
                    captureSession = session;
                    setRepeatingCaptureRequest();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.e(TAG, "onConfigureFailed");
                }

                @Override
                public void onActive(@NonNull CameraCaptureSession session) {
                    Log.d(TAG, "onActive");
                    if (arMode && !arcoreActive) {
                        resumeARCore();
                    }
                }
            };

    // A repeating camera capture session capture callback.
    CaptureCallback cameraCaptureCallback =
            new CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    Log.d(TAG, "onCaptureCompleted. arMode: " + arMode + " arcoreActive: " + arcoreActive );
                    //shouldUpdateSurfaceTexture.set(true);

                    if (arcoreActive) {
                        //surfaceView.post(() -> processFrame());
                    }
                }
            };

    void setRepeatingCaptureRequest() {
        Log.d(TAG, "setRepeatingCaptureRequest");
        try {
            captureSession.setRepeatingRequest(
                    previewCaptureRequestBuilder.build(), cameraCaptureCallback, callbacks.getBackgroundHandler());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    void resumeARCore() {
        Log.d(TAG, "resumeARCore");
        // Resume ARCore.
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }
        arcoreActive = true;

        // Set the capture session callback while in AR mode.
        sharedCamera.setCaptureCallback(cameraCaptureCallback, callbacks.getBackgroundHandler());
    }

    public void startRecording(Context context, CameraCallbacks callbacks) throws UnavailableDeviceNotCompatibleException, UnavailableSdkTooOldException, UnavailableArcoreNotInstalledException, UnavailableApkTooOldException {
        // Configure the ARCore session.
        session = new Session(context); //, EnumSet.of(Session.Feature.SHARED_CAMERA));

        /*Config config = session.getConfig();
        config.setGeospatialMode(Config.GeospatialMode.ENABLED);
        session.configure(config);*/

        /*// Store the ARCore shared camera reference.
        SharedCamera sharedCamera = session.getSharedCamera();

        // Store the ID of the camera that ARCore uses.
        String cameraId = session.getCameraConfig().getCameraId();*/

        currentFile = callbacks.createNewFile("_arcore");

        Uri destination = Uri.fromFile(currentFile);
        Log.d(TAG, "Recording path: " + destination.getPath() + " " + currentFile.getAbsolutePath());
        RecordingConfig recordingConfig =
                new RecordingConfig(session)
                        .setMp4DatasetUri(destination)
                        .setAutoStopOnPause(true)
                        .setRecordingRotation(getRotationDegrees(callbacks));
        try {
            // Prepare the session for recording, but do not start recording yet.
            session.startRecording(recordingConfig);
        } catch (RecordingFailedException e) {
            Log.e(TAG, "Failed to start recording", e);
        }

        // Resume the ARCore session to start recording.
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }

        processFrame();

    }

    public void stopRecording() {
        Log.d(TAG, "stopRecording");
        try {
            session.stopRecording();
        } catch (RecordingFailedException e) {
            e.printStackTrace();
        }
    }

    public void processFrame() {
        if (!arcoreActive) {
            Log.d(TAG, "Skipping. ARCore inactive");
            return;
        }
        Frame frame = null;
        try {
            frame = session.update();
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "Pose: " + frame.getAndroidSensorPose());

        /*

                        Image image = null;
                        try {
                            image = frame.acquireDepthImage16Bits();
                        } catch (NotYetAvailableException e) {
                            e.printStackTrace();
                        }
                        Bitmap bitmap = processImage(image);

                        Canvas canvas = previewSurface.lockHardwareCanvas();

                        Rect src = new Rect(0, 0, image.getWidth(), image.getHeight());
                        Rect dest = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());

                        canvas.drawBitmap(bitmap, src, dest, null);

                        previewSurface.unlockCanvasAndPost(canvas);
         */
    }

    private Bitmap processImage(Image image) {
        int HEIGHT = image.getHeight();
        int WIDTH = image.getWidth();
        float RANGE_MAX = 5000.0f;
        float RANGE_MIN = 10.0f;

        ShortBuffer shortDepthBuffer = image.getPlanes()[0].getBuffer().asShortBuffer();
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int index = HEIGHT * WIDTH - (y * WIDTH + x) - 1;
                short depthSample = shortDepthBuffer.get(index);

                int depthRange = (short) (depthSample & 0x1FFF);
                int depthConfidence = (short) ((depthSample >> 13) & 0x7);

                float normalized = depthRange;
                normalized = Math.max(RANGE_MIN, normalized);
                normalized = Math.min(RANGE_MAX, normalized);
                normalized = (normalized - RANGE_MIN) / (RANGE_MAX - RANGE_MIN);
                bitmap.setPixel(x, y, Color.argb(255, depthConfidence * 30, (int) (Math.sqrt(normalized) * 255), (int) (normalized * 255)));
            }
        }

        return bitmap;
    }

}
