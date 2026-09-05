package com.subhub.app.service;

import android.graphics.Bitmap;
import android.graphics.ColorSpace;

/** Converts a hardware screenshot into the smallest software bitmap needed by ONNX. */
final class InferenceBitmapPreparer {
    private InferenceBitmapPreparer() {}

    static Prepared prepare(Bitmap source, int inferenceResolution, boolean retainSourceFrame) {
        if (source == null || source.isRecycled()
                || source.getWidth() <= 0 || source.getHeight() <= 0) return null;
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int[] dimensions = targetDimensions(
                sourceWidth, sourceHeight, inferenceResolution);
        Bitmap scaled = null;
        Bitmap readable = null;
        Bitmap readbackSource = null;
        try {
            // Bitmap's hardware createScaledBitmap path can read the entire source to software,
            // scale it, then upload the result back to hardware. Avoid that extra round trip for
            // ordinary sRGB screenshots. Other color spaces retain the original conversion order.
            boolean directReadback = source.getConfig() == Bitmap.Config.HARDWARE
                    && ColorSpace.get(ColorSpace.Named.SRGB).equals(source.getColorSpace());
            long readbackNanos = 0L;
            if (directReadback) {
                long started = System.nanoTime();
                readbackSource = source.copy(Bitmap.Config.ARGB_8888, false);
                readbackNanos = System.nanoTime() - started;
                if (readbackSource == null) return null;
            }
            long scaleStartedNanos = System.nanoTime();
            scaled = Bitmap.createScaledBitmap(
                    readbackSource != null ? readbackSource : source,
                    dimensions[0], dimensions[1], true);
            long scaleFinishedNanos = System.nanoTime();
            boolean hardwareReadback = directReadback
                    || scaled.getConfig() == Bitmap.Config.HARDWARE;
            readable = scaled.getConfig() == Bitmap.Config.HARDWARE
                    ? scaled.copy(Bitmap.Config.ARGB_8888, false) : scaled;
            long readbackFinishedNanos = System.nanoTime();
            readbackNanos += readbackFinishedNanos - scaleFinishedNanos;
            if (readable == null) return null;
            Bitmap owned = readable;
            if (owned == readbackSource) readbackSource = null;
            if (owned == scaled) scaled = null;
            readable = null;
            // Retain only model-sized pixels for effects; the full-size temporary readback is
            // released below rather than kept alive with the inference frame.
            return new Prepared(owned, sourceWidth, sourceHeight, retainSourceFrame,
                    scaleFinishedNanos - scaleStartedNanos,
                    readbackNanos, hardwareReadback);
        } finally {
            if (readable != null && readable != scaled && !readable.isRecycled()) {
                readable.recycle();
            }
            if (scaled != null && scaled != source && scaled != readable
                    && !scaled.isRecycled()) scaled.recycle();
            if (readbackSource != null && !readbackSource.isRecycled()) readbackSource.recycle();
        }
    }

    static int[] targetDimensions(int width, int height, int inferenceResolution) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        int targetMaximum = Math.max(32,
                Math.min(Math.max(safeWidth, safeHeight), inferenceResolution));
        float scale = targetMaximum / (float) Math.max(safeWidth, safeHeight);
        return new int[]{
                Math.max(1, Math.round(safeWidth * scale)),
                Math.max(1, Math.round(safeHeight * scale))
        };
    }

    static final class Prepared {
        final Bitmap bitmap;
        final int sourceWidth;
        final int sourceHeight;
        final boolean retainedSourceFrame;
        final long scaleNanos;
        final long readbackNanos;
        final boolean hardwareReadback;

        Prepared(Bitmap bitmap, int sourceWidth, int sourceHeight,
                boolean retainedSourceFrame) {
            this(bitmap, sourceWidth, sourceHeight, retainedSourceFrame, 0L, 0L, false);
        }

        Prepared(Bitmap bitmap, int sourceWidth, int sourceHeight,
                boolean retainedSourceFrame, long scaleNanos, long readbackNanos,
                boolean hardwareReadback) {
            this.bitmap = bitmap;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.retainedSourceFrame = retainedSourceFrame;
            this.scaleNanos = scaleNanos;
            this.readbackNanos = readbackNanos;
            this.hardwareReadback = hardwareReadback;
        }
    }
}
