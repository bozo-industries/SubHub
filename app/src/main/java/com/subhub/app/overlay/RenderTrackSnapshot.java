package com.subhub.app.overlay;

import com.subhub.app.detection.BBox;
import com.subhub.app.detection.TrackedObject;

/** Immutable renderer input. Detection workers can keep mutating tracks without tearing a frame. */
final class RenderTrackSnapshot {
    private final int id;
    private final String category;
    private final BBox box;
    private final float velocityXPerMs;
    private final float velocityYPerMs;

    static RenderTrackSnapshot from(TrackedObject track) {
        return new RenderTrackSnapshot(
                track.getId(),
                track.getCategory(),
                track.getBox(),
                track.getVelocityX(),
                track.getVelocityY());
    }

    RenderTrackSnapshot(
            int id,
            String category,
            BBox box,
            float velocityXPerMs,
            float velocityYPerMs) {
        this.id = id;
        this.category = category;
        this.box = box;
        this.velocityXPerMs = velocityXPerMs;
        this.velocityYPerMs = velocityYPerMs;
    }

    int id() { return id; }
    String category() { return category; }
    BBox box() { return box; }
    float velocityXPerMs() { return velocityXPerMs; }
    float velocityYPerMs() { return velocityYPerMs; }

    BBox predict(float ageMs, float maxExtrapolationMs) {
        // Accessibility/OCR rectangles already follow content through the viewport transform.
        // Extrapolating classifier geometry turns tiny line-bound changes into visible drift.
        if ("text_smut".equals(category)) return box;
        float predictionMs = Math.max(0f, Math.min(ageMs, maxExtrapolationMs));
        return new BBox(
                Math.max(0, Math.round(box.getX() + velocityXPerMs * predictionMs)),
                Math.max(0, Math.round(box.getY() + velocityYPerMs * predictionMs)),
                box.getWidth(),
                box.getHeight());
    }

    boolean isMoving() {
        return !"text_smut".equals(category)
                && (Math.abs(velocityXPerMs) >= 0.005f
                || Math.abs(velocityYPerMs) >= 0.005f);
    }
}
