package com.subhub.app.penance;

import com.subhub.app.detection.BBox;
import com.subhub.app.detection.TrackedObject;

import java.util.ArrayList;
import java.util.List;

/** Correlates recent local censor rectangles with Accessibility click-target bounds. */
public final class CensorTapTracker {
    private static final long MAX_FRAME_AGE_MILLIS = 2_000L;
    private List<BBox> boxes = List.of();
    private int frameWidth = 1;
    private int frameHeight = 1;
    private long frameAtMillis;

    public synchronized void update(
            List<TrackedObject> tracks, int width, int height, long nowMillis) {
        List<BBox> current = new ArrayList<>();
        for (TrackedObject track : tracks) {
            if (track.isActive() && track.getFramesMissing() == 0) current.add(track.getBox());
        }
        boxes = current;
        frameWidth = Math.max(1, width);
        frameHeight = Math.max(1, height);
        frameAtMillis = nowMillis;
    }

    public synchronized boolean matchesClick(
            int left, int top, int right, int bottom,
            int screenWidth, int screenHeight, long nowMillis) {
        int safeScreenWidth = Math.max(1, screenWidth);
        int safeScreenHeight = Math.max(1, screenHeight);
        if (boxes.isEmpty() || nowMillis - frameAtMillis > MAX_FRAME_AGE_MILLIS
                || right <= left || bottom <= top) return false;
        long clickArea = (long) (right - left) * (bottom - top);
        long screenArea = (long) safeScreenWidth * safeScreenHeight;
        if (clickArea * 100L >= screenArea * 65L) return false;
        for (BBox box : boxes) {
            int centerX = Math.round(box.getCenterX() * safeScreenWidth / (float) frameWidth);
            int centerY = Math.round(box.getCenterY() * safeScreenHeight / (float) frameHeight);
            if (centerX >= left && centerX <= right && centerY >= top && centerY <= bottom) {
                return true;
            }
        }
        return false;
    }

    public synchronized void clear() {
        boxes = List.of();
        frameAtMillis = 0L;
    }
}
