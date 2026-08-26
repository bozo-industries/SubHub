package com.subhub.app.penance;

import com.subhub.app.detection.BBox;
import com.subhub.app.detection.TrackedObject;

import java.util.ArrayList;
import java.util.List;

/** Correlates the currently visible censor rectangles with Accessibility click-target bounds. */
public final class CensorTapTracker {
    private static final CensorTapTracker SHARED = new CensorTapTracker();
    private List<BBox> boxes = List.of();
    private int frameWidth = 1;
    private int frameHeight = 1;

    /** One process-wide hit map shared by App Mode and Screen Capture. */
    public static CensorTapTracker shared() { return SHARED; }

    public synchronized void update(
            List<TrackedObject> tracks, int width, int height, long nowMillis) {
        List<BBox> current = new ArrayList<>();
        for (TrackedObject track : tracks) {
            // If the overlay still renders a predicted track, its tap target must remain active
            // too. Requiring a detection in the latest frame made the visible censor unbillable.
            if (track.isActive()) current.add(track.getBox());
        }
        boxes = current;
        frameWidth = Math.max(1, width);
        frameHeight = Math.max(1, height);
    }

    /** Keeps tap targets in lockstep with the event-speed overlay between detector frames. */
    public synchronized void offsetContent(
            int screenDx, int screenDy, int screenWidth, int screenHeight, long nowMillis) {
        if (boxes.isEmpty() || (screenDx == 0 && screenDy == 0)) return;
        int dx = Math.round(screenDx * frameWidth / (float) Math.max(1, screenWidth));
        int dy = Math.round(screenDy * frameHeight / (float) Math.max(1, screenHeight));
        List<BBox> shifted = new ArrayList<>(boxes.size());
        for (BBox box : boxes) {
            int left = clamp(box.getX() + dx, 0, frameWidth);
            int top = clamp(box.getY() + dy, 0, frameHeight);
            int right = clamp(box.getRight() + dx, 0, frameWidth);
            int bottom = clamp(box.getBottom() + dy, 0, frameHeight);
            if (right > left && bottom > top) {
                shifted.add(new BBox(left, top, right - left, bottom - top));
            }
        }
        boxes = shifted;
    }

    public synchronized boolean matchesClick(
            int left, int top, int right, int bottom,
            int screenWidth, int screenHeight, long nowMillis) {
        int safeScreenWidth = Math.max(1, screenWidth);
        int safeScreenHeight = Math.max(1, screenHeight);
        if (boxes.isEmpty() || right <= left || bottom <= top) return false;
        long clickArea = (long) (right - left) * (bottom - top);
        long screenArea = (long) safeScreenWidth * safeScreenHeight;
        if (clickArea * 100L >= screenArea * 65L) return false;
        for (BBox box : boxes) {
            int boxLeft = Math.round(box.getX() * safeScreenWidth / (float) frameWidth);
            int boxTop = Math.round(box.getY() * safeScreenHeight / (float) frameHeight);
            int boxRight = Math.round(box.getRight() * safeScreenWidth / (float) frameWidth);
            int boxBottom = Math.round(box.getBottom() * safeScreenHeight / (float) frameHeight);
            int intersectionLeft = Math.max(left, boxLeft);
            int intersectionTop = Math.max(top, boxTop);
            int intersectionRight = Math.min(right, boxRight);
            int intersectionBottom = Math.min(bottom, boxBottom);
            if (intersectionRight <= intersectionLeft || intersectionBottom <= intersectionTop) {
                continue;
            }
            long intersection = (long) (intersectionRight - intersectionLeft)
                    * (intersectionBottom - intersectionTop);
            long boxArea = (long) Math.max(1, boxRight - boxLeft)
                    * Math.max(1, boxBottom - boxTop);
            long smallerArea = Math.min(clickArea, boxArea);
            boolean censorCenterInTarget = box.getCenterX() * safeScreenWidth
                    >= (long) left * frameWidth
                    && box.getCenterX() * safeScreenWidth <= (long) right * frameWidth
                    && box.getCenterY() * safeScreenHeight >= (long) top * frameHeight
                    && box.getCenterY() * safeScreenHeight <= (long) bottom * frameHeight;
            int clickCenterX = left + (right - left) / 2;
            int clickCenterY = top + (bottom - top) / 2;
            boolean targetCenterInCensor = clickCenterX >= boxLeft && clickCenterX <= boxRight
                    && clickCenterY >= boxTop && clickCenterY <= boxBottom;
            if (censorCenterInTarget || targetCenterInCensor
                    || intersection * 100L >= smallerArea * 15L) return true;
        }
        return false;
    }

    /** Matches an actual screen touch against the censor regions currently visible to the user. */
    public synchronized boolean matchesPoint(
            float screenX, float screenY,
            int screenWidth, int screenHeight, long nowMillis) {
        int safeScreenWidth = Math.max(1, screenWidth);
        int safeScreenHeight = Math.max(1, screenHeight);
        if (boxes.isEmpty() || screenX < 0f || screenY < 0f
                || screenX > safeScreenWidth || screenY > safeScreenHeight) return false;
        float frameX = screenX * frameWidth / safeScreenWidth;
        float frameY = screenY * frameHeight / safeScreenHeight;
        for (BBox box : boxes) {
            if (frameX >= box.getX() && frameX <= box.getRight()
                    && frameY >= box.getY() && frameY <= box.getBottom()) return true;
        }
        return false;
    }

    public synchronized void clear() {
        boxes = List.of();
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
