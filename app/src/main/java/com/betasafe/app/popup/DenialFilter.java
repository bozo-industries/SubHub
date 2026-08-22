package com.betasafe.app.popup;

import android.graphics.Bitmap;

/** Lightweight recovered blur/pixelation transform used only on popup copies. */
public final class DenialFilter {
    private DenialFilter() {}

    public static Bitmap blur(Bitmap source, int intensity) {
        return process(source, intensity, true);
    }

    public static Bitmap pixelate(Bitmap source, int intensity) {
        return process(source, intensity, false);
    }

    private static Bitmap process(Bitmap source, int intensity, boolean smooth) {
        int factor = Math.max(2, (int) (2 + (clamp(intensity, 0, 100) / 100f) * 38));
        int width = source.getWidth();
        int height = source.getHeight();
        Bitmap small = Bitmap.createScaledBitmap(source,
                Math.max(1, width / factor), Math.max(1, height / factor), true);
        try {
            return Bitmap.createScaledBitmap(small, width, height, smooth);
        } finally {
            if (small != source && !small.isRecycled()) small.recycle();
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
