package com.subhub.app.service;

import android.view.MotionEvent;

/**
 * Tracks unreported finger motion between sparse Accessibility scroll callbacks.
 *
 * <p>The prediction is renderer-only: authoritative content/tracker coordinates still advance
 * exclusively from {@code TYPE_VIEW_SCROLLED}. Re-anchoring on every authoritative callback
 * prevents touch prediction from accumulating drift or double-applying a scroll delta.</p>
 */
final class TouchScrollPredictor {
    private boolean gestureActive;
    private boolean scrollConfirmed;
    private float latestX;
    private float latestY;
    private float anchorX;
    private float anchorY;

    synchronized Prediction onMotionEvent(MotionEvent event) {
        if (event == null) return Prediction.NONE;
        return onAction(event.getActionMasked(), event.getX(), event.getY());
    }

    synchronized Prediction onAction(int action, float x, float y) {
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                gestureActive = true;
                scrollConfirmed = false;
                latestX = anchorX = x;
                latestY = anchorY = y;
                return Prediction.NONE;
            case MotionEvent.ACTION_MOVE:
                if (!gestureActive) return Prediction.NONE;
                latestX = x;
                latestY = y;
                if (!scrollConfirmed) return Prediction.NONE;
                return new Prediction(latestX - anchorX, latestY - anchorY, true);
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                reset();
                return Prediction.NONE;
            default:
                return currentPrediction();
        }
    }

    /** Reconciles prediction with an exact Accessibility content delta. */
    synchronized Prediction onAuthoritativeScroll() {
        if (!gestureActive) return Prediction.NONE;
        anchorX = latestX;
        anchorY = latestY;
        scrollConfirmed = true;
        return Prediction.NONE;
    }

    synchronized Prediction currentPrediction() {
        return gestureActive && scrollConfirmed
                ? new Prediction(latestX - anchorX, latestY - anchorY, true)
                : Prediction.NONE;
    }

    synchronized void reset() {
        gestureActive = false;
        scrollConfirmed = false;
        latestX = latestY = anchorX = anchorY = 0f;
    }

    static final class Prediction {
        static final Prediction NONE = new Prediction(0f, 0f, false);
        final float dx;
        final float dy;
        final boolean active;

        Prediction(float dx, float dy, boolean active) {
            this.dx = dx;
            this.dy = dy;
            this.active = active;
        }
    }
}
