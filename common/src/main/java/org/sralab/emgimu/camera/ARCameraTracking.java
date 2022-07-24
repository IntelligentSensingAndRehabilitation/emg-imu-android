package org.sralab.emgimu.camera;

import static org.sralab.emgimu.camera.CameraUtils.getRotationDegrees;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.RecordingConfig;
import com.google.ar.core.Session;
import com.google.ar.core.SharedCamera;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.RecordingFailedException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;

import java.io.File;
import java.util.EnumSet;

public class ARCameraTracking {

    private static final String TAG = ARCameraTracking.class.getSimpleName();

    protected Session session;
    protected File currentFile;

    public ARCameraTracking() {

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

    public void stopRecording() throws RecordingFailedException {
        Log.d(TAG, "stopRecording");
        session.stopRecording();
    }

    public void processFrame() {
        Frame frame = null;
        try {
            frame = session.update();
        } catch (CameraNotAvailableException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "Pose: " + frame.getAndroidSensorPose());
    }

}
