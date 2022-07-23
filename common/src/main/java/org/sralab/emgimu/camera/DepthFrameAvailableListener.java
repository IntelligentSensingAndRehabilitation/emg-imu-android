// Code originally from https://github.com/plluke/tof and previously licensed under
// The Unlicense

package org.sralab.emgimu.camera;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

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

    private static float RANGE_MIN = 100.0f;
    private static float RANGE_MAX = 5000.0f;
    private static float CONFIDENCE_FILTER = 0.1f;

    private Bitmap bitmap;
    private int[] rawMask;

    private Surface listeningSurface = null;
    private Surface previewSurface = null;
    private Long firstTimestamp = null;

    public DepthFrameAvailableListener() {
        int size = WIDTH * HEIGHT;
        rawMask = new int[size];
    }

    /* Gettors and settors */
    public void setListeningSurface(Surface listeningSurface) {
        this.listeningSurface = listeningSurface;

        // when starting a new recording grab first timestamp
        if (listeningSurface != null)
            firstTimestamp = null;
    }

    public void setPreviewSurface(Surface surface) {
        previewSurface = surface;
    }

    public Long getFirstTimestamp() {
        return firstTimestamp;
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
        //Log.d(TAG, "Height: " + HEIGHT + " " + "Width: " + WIDTH);
        if (image != null && image.getFormat() == ImageFormat.DEPTH16) {
            processImage(image);
            bitmap = convertToRGBBitmap(rawMask);
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
        // canvas.drawBitmap(bitmap, defaultBitmapTransform(textureView), null);
        //canvas.drawBitmap(bitmap, 0, 0, null);
        canvas.drawBitmap(bitmap, src, dest, null);

        previewSurface.unlockCanvasAndPost(canvas);
    }
    
    private void postToRecordingSurface() {
        if (listeningSurface != null) {

            if (listeningSurface.isValid()) {
                Canvas canvas = listeningSurface.lockHardwareCanvas();
                if (!canvas.isHardwareAccelerated()) {
                    Log.e(TAG, "No hardware accel");
                }

                // Store the first timestamp
                if (firstTimestamp == null) {
                    firstTimestamp = new Date().getTime();
                    Log.d(TAG, "First timestamp: " + firstTimestamp);
                }

                Log.d(TAG, "Canvas width: " + canvas.getWidth() + ". Canvas height: " + canvas.getHeight());
                Log.d(TAG, "Bitmap width: " + bitmap.getWidth() + ". Bitmap height: " + bitmap.getHeight());
                //canvas.setBitmap(bitmap);
                canvas.drawBitmap(bitmap, 0, 0, null);
                listeningSurface.unlockCanvasAndPost(canvas);
                Log.d(TAG, "Posted");
            } else {
                Log.e(TAG, "Invalid listening surface");
            }
        }
    }

    private void processImage(Image image) {
        ShortBuffer shortDepthBuffer = image.getPlanes()[0].getBuffer().asShortBuffer();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int index = y * WIDTH + x;
                short depthSample = shortDepthBuffer.get(index);
                int newValue = extractRange(depthSample, CONFIDENCE_FILTER);
                // Store value in the rawMask for visualization
                rawMask[index] = newValue;
            }
        }

    }

    private int extractRange(short sample, float confidenceFilter) {
        int depthRange = (short) (sample & 0x1FFF);
        int depthConfidence = (short) ((sample >> 13) & 0x7);
        float depthPercentage = depthConfidence == 0 ? 1.f : (depthConfidence - 1) / 7.f;
        if (depthPercentage > confidenceFilter) {
            return normalizeRange(depthRange);
        } else {
            return 0;
        }
    }

    private int normalizeRange(int range) {
        float normalized = (float)range - RANGE_MIN;
        // Clamp to min/max
        normalized = Math.max(RANGE_MIN, normalized);
        normalized = Math.min(RANGE_MAX, normalized);
        // Normalize to 0 to 255
        normalized = normalized - RANGE_MIN;
        normalized = normalized / (RANGE_MAX - RANGE_MIN) * 255;
        return (int)normalized;
    }

    private Bitmap convertToRGBBitmap(int[] mask) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_4444);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int index = HEIGHT * WIDTH - (y * WIDTH + x) - 1;
                bitmap.setPixel(x, y, Color.argb(255, 0, mask[index],0));
            }
        }
        return bitmap;
    }
}