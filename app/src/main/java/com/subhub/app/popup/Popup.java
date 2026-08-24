package com.subhub.app.popup;

import android.graphics.Bitmap;

/** Mutable position plus immutable visual/lifetime data for one overlay popup. */
final class Popup {
    final long id;
    final String sourcePath;
    final Bitmap bitmap;
    final Bitmap denialBitmap;
    final String caption;
    final int width;
    final int height;
    final float rotation;
    final long spawnedAt;
    final long displayDurationMs;
    final long fadeInMs;
    final long fadeOutMs;
    float x;
    float y;
    float velocityX;
    float velocityY;

    Popup(long id, String sourcePath, Bitmap bitmap, Bitmap denialBitmap, String caption,
            int width, int height, float rotation, long spawnedAt, long displayDurationMs,
            long fadeInMs, long fadeOutMs, float x, float y, float velocityX, float velocityY) {
        this.id = id;
        this.sourcePath = sourcePath;
        this.bitmap = bitmap;
        this.denialBitmap = denialBitmap;
        this.caption = caption;
        this.width = width;
        this.height = height;
        this.rotation = rotation;
        this.spawnedAt = spawnedAt;
        this.displayDurationMs = displayDurationMs;
        this.fadeInMs = fadeInMs;
        this.fadeOutMs = fadeOutMs;
        this.x = x;
        this.y = y;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
    }

    long expiresAt() { return spawnedAt + fadeInMs + displayDurationMs + fadeOutMs; }

    float alphaAt(long now) {
        long age = now - spawnedAt;
        if (age < 0) return 0f;
        if (fadeInMs > 0 && age < fadeInMs) return age / (float) fadeInMs;
        long fadeOutStart = fadeInMs + displayDurationMs;
        if (age < fadeOutStart) return 1f;
        long fading = age - fadeOutStart;
        return fadeOutMs == 0 || fading >= fadeOutMs ? 0f : 1f - fading / (float) fadeOutMs;
    }

    void integrate(float seconds, int screenWidth, int screenHeight, int reservedTop) {
        if (velocityX == 0f && velocityY == 0f) return;
        x += velocityX * seconds;
        y += velocityY * seconds;
        if (x < 0) { x = 0; velocityX = -velocityX; }
        if (y < reservedTop) { y = reservedTop; velocityY = -velocityY; }
        if (x + width > screenWidth) {
            x = Math.max(0, screenWidth - width);
            velocityX = -velocityX;
        }
        if (y + height > screenHeight) {
            y = Math.max(reservedTop, screenHeight - height);
            velocityY = -velocityY;
        }
    }

    void recycleDerived() {
        if (denialBitmap != null && denialBitmap != bitmap && !denialBitmap.isRecycled()) {
            denialBitmap.recycle();
        }
    }
}
