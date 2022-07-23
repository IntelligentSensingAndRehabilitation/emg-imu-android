package org.sralab.emgimu.camera;

import android.graphics.Matrix;
import android.hardware.camera2.CameraCharacteristics;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

public class CameraUtils {


    /**
     * Computes rotation required to transform the camera sensor output orientation to the
     * device's current orientation in degrees.
     *
     * @param characteristics The CameraCharacteristics to query for the sensor orientation.
     * @param surfaceRotationDegrees The current device orientation as a Surface constant.
     * @return Relative rotation of the camera sensor output.
     */
    public static int computeRelativeRotation(CameraCharacteristics characteristics, int surfaceRotationDegrees) {
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
    public static Matrix computeTransformationMatrix(
            TextureView textureView,
            CameraCharacteristics characteristics,
            Size previewSize,
            int surfaceRotation,
            int rotationOffset
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

        surfaceRotationDegrees = surfaceRotationDegrees + rotationOffset;

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
