package com.subhub.app.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.WindowManager;

import com.subhub.app.detection.TrackedObject;
import com.subhub.app.detection.Detection;
import com.subhub.app.settings.CensorAppearance;

import java.util.List;

/** Owns the non-interactive system overlay used to render censor regions. */
public final class OverlayController implements AutoCloseable {
    private final WindowManager windowManager;
    private final CensorOverlayView view;
    private final int windowType;
    private boolean attached;

    public OverlayController(Context context) {
        this(context, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
    }

    public OverlayController(Context context, int windowType) {
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        view = new CensorOverlayView(context);
        this.windowType = windowType;
    }

    public void show() {
        if (attached) return;
        WindowManager.LayoutParams params = createLayoutParams(windowType);
        windowManager.addView(view, params);
        attached = true;
    }

    static WindowManager.LayoutParams createLayoutParams(int windowType) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                windowType,
                flags,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        // Detector boxes use full-display coordinates. Android 11+ otherwise fits overlay windows
        // below system bars even with FLAG_LAYOUT_IN_SCREEN, shifting every censor down by the
        // status-bar height. Keep the overlay origin at the physical display's top-left instead.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) params.setFitInsetsTypes(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        return params;
    }

    public void update(
            List<TrackedObject> tracks,
            int captureWidth,
            int captureHeight,
            Bitmap frame) {
        update(tracks, captureWidth, captureHeight, frame, 0, 0);
    }

    /**
     * Publishes a detector frame while compensating for scroll motion that happened after the
     * screenshot was requested. Motion is expressed in screen pixels, not detector coordinates.
     */
    public void update(
            List<TrackedObject> tracks,
            int captureWidth,
            int captureHeight,
            Bitmap frame,
            int motionX,
            int motionY) {
        view.setTracks(tracks, captureWidth, captureHeight, frame, motionX, motionY);
    }

    /**
     * Publishes live-coordinate tracks with independent motion for events received after the
     * inference snapshot and for the older source frame used by image-based effects.
     */
    public void update(
            List<TrackedObject> tracks,
            int captureWidth,
            int captureHeight,
            Bitmap frame,
            int motionX,
            int motionY,
            int sourceMotionX,
            int sourceMotionY) {
        view.setTracks(tracks, captureWidth, captureHeight, frame,
                motionX, motionY, sourceMotionX, sourceMotionY);
    }

    /** Publishes a pooled source frame whose release returns it to capture instead of recycling. */
    public void updatePooledFrame(
            List<TrackedObject> tracks,
            int captureWidth,
            int captureHeight,
            Bitmap frame,
            Runnable frameRelease) {
        view.setTracks(tracks, captureWidth, captureHeight, frame,
                0, 0, 0, 0, frameRelease);
    }

    /** Moves the current lightweight overlay immediately while the next inference is pending. */
    public void offsetContent(int deltaX, int deltaY) {
        view.offsetContent(deltaX, deltaY);
    }

    /** Publishes stabilized Accessibility/OCR geometry without waiting behind visual inference. */
    public void updateText(
            List<Detection> detections,
            int captureWidth,
            int captureHeight,
            int motionX,
            int motionY) {
        view.setTextDetections(detections, captureWidth, captureHeight, motionX, motionY);
    }

    /** Applies renderer-only finger motion between authoritative Accessibility scroll events. */
    /** Immediately removes rendered content while keeping the lightweight window ready. */
    public void clear() {
        view.clearContent();
    }

    public void setAppearance(CensorAppearance appearance) {
        view.setAppearance(appearance);
    }

    public void setDiagnostics(String text) {
        view.setDiagnostics(text);
    }

    /** Caps display-rate motion prediction after the most recent detector observation. */
    public void setMaxExtrapolationMs(float value) {
        view.setMaxExtrapolationMs(value);
    }

    @Override
    public void close() {
        if (attached) windowManager.removeViewImmediate(view);
        else view.release();
        attached = false;
    }
}
