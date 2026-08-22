package com.betasafe.app.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.WindowManager;

import com.betasafe.app.detection.TrackedObject;
import com.betasafe.app.settings.CensorAppearance;

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
        windowManager.addView(view, params);
        attached = true;
    }

    public void update(
            List<TrackedObject> tracks,
            int captureWidth,
            int captureHeight,
            Bitmap frame) {
        view.setTracks(tracks, captureWidth, captureHeight, frame);
    }

    public void setAppearance(CensorAppearance appearance) {
        view.setAppearance(appearance);
    }

    public void setDiagnostics(String text) {
        view.setDiagnostics(text);
    }

    @Override
    public void close() {
        if (attached) windowManager.removeView(view);
        else view.release();
        attached = false;
    }
}
