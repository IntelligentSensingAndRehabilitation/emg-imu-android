// Code originally from https://github.com/plluke/tof and previously licensed under
// The Unlicense

package org.sralab.emgimu.camera;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;
import android.view.Surface;

import java.nio.ShortBuffer;
import java.util.Date;

/**
 * The camera outputs a DEPTH16 image which cannot be directly shown in
 * a preview or written to disk. This class
 */
public class DepthFrameAvailableListener implements ImageReader.OnImageAvailableListener {
    private static final String TAG = DepthFrameAvailableListener.class.getSimpleName();

    public static int WIDTH = 320;
    public static int HEIGHT = 240;

    final private static float RANGE_MIN = 10.0f;
    final private static float RANGE_MAX = 5000.0f;

    private Bitmap bitmap;

    private Surface listeningSurface = null;
    private Surface previewSurface = null;

    public DepthFrameAvailableListener() {
    }

    /* Gettors and settors */
    public void setListeningSurface(Surface listeningSurface) {
        this.listeningSurface = listeningSurface;
    }

    public void setPreviewSurface(Surface surface) {
        previewSurface = surface;
    }

    // Callback when new data available
    @Override
    public void onImageAvailable(ImageReader reader) {
        Image image;
        try {
            image = reader.acquireNextImage();
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to acquireNextImage: " + e.getMessage());
            return;
        }
        WIDTH = image.getWidth();
        HEIGHT = image.getHeight();

        if (image != null && image.getFormat() == ImageFormat.DEPTH16) {
            bitmap = processImage(image);
            postToPreviewSurface();
            postToRecordingSurface();
            bitmap.recycle();
        }
        image.close();
    }

    private void postToPreviewSurface() {
        if (previewSurface == null)
            return;

        Canvas canvas = previewSurface.lockHardwareCanvas();

        Rect src = new Rect(0, 0, WIDTH, HEIGHT);
        Rect dest = new Rect(0, 0, canvas.getWidth(), canvas.getHeight());

        canvas.drawBitmap(bitmap, src, dest, null);

        previewSurface.unlockCanvasAndPost(canvas);
    }

    private void postToRecordingSurface() {
        if (listeningSurface == null)
            return;

        if (!listeningSurface.isValid()) {
            Log.e(TAG, "Invalid listening surface");
            return;
        }

        Canvas canvas = listeningSurface.lockHardwareCanvas();
        if (!canvas.isHardwareAccelerated()) {
            Log.e(TAG, "No hardware accel");
        }

        canvas.drawBitmap(bitmap, 0, 0, null);
        listeningSurface.unlockCanvasAndPost(canvas);
    }

    private Bitmap processImage(Image image) {
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