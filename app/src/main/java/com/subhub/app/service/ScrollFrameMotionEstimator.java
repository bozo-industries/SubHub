package com.subhub.app.service;

import android.graphics.Bitmap;

/**
 * Estimates whole-feed motion from a tiny luminance thumbnail.
 *
 * This deliberately does not run the detector. It gives the overlay a cheap motion path while
 * a finger or fling keeps a feed moving, including apps that delay accessibility scroll events.
 */
final class ScrollFrameMotionEstimator implements AutoCloseable {
    private static final int SAMPLE_WIDTH = 54;
    private static final int SAMPLE_HEIGHT = 96;
    private static final int MAX_DX = 3;
    private static final int MAX_DY = 30;
    private static final int EDGE_THRESHOLD = 24;
    private static final int MIN_FEATURES = 70;

    private final int[] pixels = new int[SAMPLE_WIDTH * SAMPLE_HEIGHT];
    private int[] previous = new int[SAMPLE_WIDTH * SAMPLE_HEIGHT];
    private int[] current = new int[SAMPLE_WIDTH * SAMPLE_HEIGHT];
    private boolean ready;

    synchronized Motion update(Bitmap frame) {
        if (frame == null || frame.isRecycled()) return Motion.NONE;
        Bitmap scaled = null;
        Bitmap readable = null;
        try {
            // Accessibility supplies a hardware bitmap. Scale that first so the GPU/native path
            // reduces the frame before the tiny CPU readback; never copy the full display merely
            // to estimate scroll motion.
            scaled = Bitmap.createScaledBitmap(frame, SAMPLE_WIDTH, SAMPLE_HEIGHT, true);
            readable = scaled.getConfig() == Bitmap.Config.HARDWARE
                    ? scaled.copy(Bitmap.Config.ARGB_8888, false) : scaled;
            if (readable == null) return Motion.NONE;
            readable.getPixels(pixels, 0, SAMPLE_WIDTH, 0, 0,
                    SAMPLE_WIDTH, SAMPLE_HEIGHT);
        } finally {
            if (readable != null && readable != scaled && !readable.isRecycled()) {
                readable.recycle();
            }
            if (scaled != null && scaled != frame && !scaled.isRecycled()) scaled.recycle();
        }
        for (int index = 0; index < pixels.length; index++) {
            int color = pixels[index];
            current[index] = (((color >>> 16) & 0xff) * 3
                    + ((color >>> 8) & 0xff) * 6 + (color & 0xff)) / 10;
        }
        if (!ready) {
            swapFrames();
            ready = true;
            return Motion.NONE;
        }
        SampleMotion sampleMotion = estimate(previous, current, SAMPLE_WIDTH, SAMPLE_HEIGHT);
        swapFrames();
        if (!sampleMotion.moved) return Motion.NONE;
        return new Motion(
                Math.round(sampleMotion.dx * frame.getWidth() / (float) SAMPLE_WIDTH),
                Math.round(sampleMotion.dy * frame.getHeight() / (float) SAMPLE_HEIGHT));
    }

    synchronized void reset() {
        ready = false;
    }

    private void swapFrames() {
        int[] oldPrevious = previous;
        previous = current;
        current = oldPrevious;
    }

    /** Pure-array entry point kept package-visible for deterministic host-side tests. */
    static SampleMotion estimate(int[] previous, int[] current, int width, int height) {
        if (previous == null || current == null || width < 12 || height < 20
                || previous.length < width * height || current.length < width * height) {
            return SampleMotion.NONE;
        }
        Score stationary = score(previous, current, width, height, 0, 0);
        if (stationary.features < MIN_FEATURES) return SampleMotion.NONE;

        Score best = stationary;
        int bestX = 0;
        int bestY = 0;
        int horizontalLimit = Math.min(MAX_DX, Math.max(1, width / 12));
        int verticalLimit = Math.min(MAX_DY, Math.max(2, height / 3));
        for (int dy = -verticalLimit; dy <= verticalLimit; dy++) {
            for (int dx = -horizontalLimit; dx <= horizontalLimit; dx++) {
                if (dx == 0 && dy == 0) continue;
                Score candidate = score(previous, current, width, height, dx, dy);
                if (candidate.features < MIN_FEATURES || candidate.coveredBands < 3) continue;
                if (candidate.average < best.average) {
                    best = candidate;
                    bestX = dx;
                    bestY = dy;
                }
            }
        }
        // Require a material global improvement. Local GIF/video animation should not drag every
        // censor, while actual scrolling aligns edges across most of the sampled feed.
        boolean material = (bestX != 0 || bestY != 0)
                && best.average <= 38f
                && best.average <= stationary.average * 0.78f;
        return material ? new SampleMotion(bestX, bestY, true) : SampleMotion.NONE;
    }

    private static Score score(
            int[] previous, int[] current, int width, int height, int dx, int dy) {
        int top = Math.max(6, 2 - dy);
        int bottom = Math.min(height - 6, height - 2 - dy);
        int left = Math.max(3, 2 - dx);
        int right = Math.min(width - 3, width - 2 - dx);
        long difference = 0L;
        int features = 0;
        int[] bands = new int[4];
        for (int y = top; y < bottom; y += 2) {
            int row = y * width;
            for (int x = left; x < right; x += 2) {
                int index = row + x;
                int value = previous[index];
                int edge = Math.abs(value - previous[index - 1])
                        + Math.abs(value - previous[index - width]);
                if (edge < EDGE_THRESHOLD) continue;
                int shifted = (y + dy) * width + x + dx;
                difference += Math.min(96, Math.abs(value - current[shifted]));
                features++;
                bands[Math.min(3, y * 4 / height)]++;
            }
        }
        int covered = 0;
        for (int count : bands) if (count >= 8) covered++;
        return new Score(features == 0 ? Float.MAX_VALUE : difference / (float) features,
                features, covered);
    }

    @Override public void close() {
        reset();
    }

    static final class Motion {
        static final Motion NONE = new Motion(0, 0);
        final int dx;
        final int dy;
        Motion(int dx, int dy) { this.dx = dx; this.dy = dy; }
        boolean moved() { return dx != 0 || dy != 0; }
    }

    static final class SampleMotion {
        static final SampleMotion NONE = new SampleMotion(0, 0, false);
        final int dx;
        final int dy;
        final boolean moved;
        SampleMotion(int dx, int dy, boolean moved) {
            this.dx = dx;
            this.dy = dy;
            this.moved = moved;
        }
    }

    private static final class Score {
        final float average;
        final int features;
        final int coveredBands;
        Score(float average, int features, int coveredBands) {
            this.average = average;
            this.features = features;
            this.coveredBands = coveredBands;
        }
    }
}
