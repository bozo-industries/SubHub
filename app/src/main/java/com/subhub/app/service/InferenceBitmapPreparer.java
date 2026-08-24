package com.subhub.app.service;

import android.graphics.Bitmap;

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
        try {
            // Accessibility screenshots are hardware bitmaps. Scale before CPU readback so a
            // portrait Pixel frame becomes about 230x512 instead of a 1080x2400 ARGB copy.
            scaled = Bitmap.createScaledBitmap(
                    source, dimensions[0], dimensions[1], true);
            readable = scaled.getConfig() == Bitmap.Config.HARDWARE
                    ? scaled.copy(Bitmap.Config.ARGB_8888, false) : scaled;
            if (readable == null) return null;
            Bitmap owned = readable;
            if (owned == scaled) scaled = null;
            readable = null;
            // Source-based effects map their crop through the retained bitmap's dimensions, so
            // blur/pixelate/glitch do not need a full-display 10+ MB software copy either.
            return new Prepared(owned, sourceWidth, sourceHeight, retainSourceFrame);
        } finally {
            if (readable != null && readable != scaled && !readable.isRecycled()) {
                readable.recycle();
            }
            if (scaled != null && scaled != source && scaled != readable
                    && !scaled.isRecycled()) scaled.recycle();
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

        Prepared(Bitmap bitmap, int sourceWidth, int sourceHeight,
                boolean retainedSourceFrame) {
            this.bitmap = bitmap;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.retainedSourceFrame = retainedSourceFrame;
        }
    }
}
