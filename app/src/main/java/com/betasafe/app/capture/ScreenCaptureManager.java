package com.betasafe.app.capture;

import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;

import java.nio.ByteBuffer;

/** Captures scaled RGBA frames from a user-approved MediaProjection session. */
public final class ScreenCaptureManager implements AutoCloseable {
    private final MediaProjection projection;
    private final int densityDpi;
    private final int captureWidth;
    private final int captureHeight;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private Bitmap paddedBitmap;

    public ScreenCaptureManager(
            MediaProjection projection,
            int screenWidth,
            int screenHeight,
            int densityDpi,
            int inferenceResolution,
            float captureScale) {
        this.projection = projection;
        this.densityDpi = densityDpi;
        int maximum = Math.max(screenWidth, screenHeight);
        int targetMaximum = Math.min(
                maximum,
                Math.max(inferenceResolution, Math.round(maximum * captureScale)));
        float scale = (float) targetMaximum / maximum;
        captureWidth = Math.max(32, (int) (screenWidth * scale));
        captureHeight = Math.max(32, (int) (screenHeight * scale));
    }

    public void start() {
        if (virtualDisplay != null) return;
        imageReader = ImageReader.newInstance(
                captureWidth, captureHeight, PixelFormat.RGBA_8888, 2);
        virtualDisplay = projection.createVirtualDisplay(
                "BetaSafeCapture",
                captureWidth,
                captureHeight,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                null);
    }

    public Bitmap acquireLatestFrame() {
        if (imageReader == null) return null;
        try (Image image = imageReader.acquireLatestImage()) {
            if (image == null) return null;
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int paddedWidth = captureWidth + (rowStride - pixelStride * captureWidth) / pixelStride;
            if (paddedBitmap == null
                    || paddedBitmap.getWidth() != paddedWidth
                    || paddedBitmap.getHeight() != captureHeight) {
                if (paddedBitmap != null) paddedBitmap.recycle();
                paddedBitmap = Bitmap.createBitmap(
                        paddedWidth, captureHeight, Bitmap.Config.ARGB_8888);
            }
            buffer.rewind();
            paddedBitmap.copyPixelsFromBuffer(buffer);
            return Bitmap.createBitmap(paddedBitmap, 0, 0, captureWidth, captureHeight);
        }
    }

    public int getCaptureWidth() { return captureWidth; }
    public int getCaptureHeight() { return captureHeight; }

    @Override
    public void close() {
        if (virtualDisplay != null) virtualDisplay.release();
        virtualDisplay = null;
        if (imageReader != null) imageReader.close();
        imageReader = null;
        if (paddedBitmap != null) paddedBitmap.recycle();
        paddedBitmap = null;
    }
}
