package com.subhub.app.overlay;

import com.subhub.app.detection.BBox;
import com.subhub.app.detection.Detection;
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

    static RenderTrackSnapshot fromWorld(
            TrackedObject track,
            long cameraX,
            long cameraY,
            int sourceWidth,
            int sourceHeight,
            int viewportWidth,
            int viewportHeight) {
        return new RenderTrackSnapshot(
                track.getId(),
                track.getCategory(),
                ContentSpaceCoordinates.toWorld(track.getBox(), cameraX, cameraY,
                        sourceWidth, sourceHeight, viewportWidth, viewportHeight),
                track.getVelocityX(),
                track.getVelocityY());
    }

    static RenderTrackSnapshot fromTextDetection(Detection detection) {
        BBox box = detection.getBox();
        return new RenderTrackSnapshot(
                stableTextId(detection, box), detection.getCategory(), box, 0f, 0f);
    }

    static RenderTrackSnapshot fromWorldTextDetection(
            Detection detection,
            long cameraX,
            long cameraY,
            int sourceWidth,
            int sourceHeight,
            int viewportWidth,
            int viewportHeight) {
        BBox world = ContentSpaceCoordinates.toWorld(
                detection.getBox(), cameraX, cameraY,
                sourceWidth, sourceHeight, viewportWidth, viewportHeight);
        return new RenderTrackSnapshot(
                stableTextId(detection, world),
                detection.getCategory(),
                world,
                0f,
                0f);
    }

    private static int stableTextId(Detection detection, BBox identityBox) {
        String anchor = detection.getAnchorKey();
        String identity = anchor == null || anchor.isEmpty()
                ? detection.getClassName() + '|' + identityBox.getCenterX() / 32 + '|'
                        + identityBox.getCenterY() / 24 + '|' + identityBox.getWidth() / 32
                : anchor;
        return -1 - (identity.hashCode() & 0x3fffffff);
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
