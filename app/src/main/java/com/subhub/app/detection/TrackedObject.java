package com.subhub.app.detection;

/** Mutable temporal state for one censored object. */
public final class TrackedObject {
    private final int id;
    private final String category;
    private final String className;
    private BBox box;
    private BBox rawBox;
    private float confidence;
    private long lastSeenNanos;
    private int framesTracked;
    private int framesMissing;
    private float velocityX;
    private float velocityY;
    private boolean active = true;
    private boolean confirmed;

    TrackedObject(int id, Detection detection, long nowNanos) {
        this.id = id;
        category = detection.getCategory();
        className = detection.getClassName();
        box = detection.getBox();
        rawBox = detection.getBox();
        confidence = detection.getConfidence();
        lastSeenNanos = nowNanos;
        framesTracked = 1;
    }

    public int getId() { return id; }
    public String getCategory() { return category; }
    public String getClassName() { return className; }
    public BBox getBox() { return box; }
    public BBox getRawBox() { return rawBox; }
    public float getConfidence() { return confidence; }
    public long getLastSeenNanos() { return lastSeenNanos; }
    public int getFramesTracked() { return framesTracked; }
    public int getFramesMissing() { return framesMissing; }
    public float getVelocityX() { return velocityX; }
    public float getVelocityY() { return velocityY; }
    public boolean isActive() { return active; }
    public boolean isConfirmed() { return confirmed; }

    void update(Detection detection, BBox renderedBox, float dx, float dy, long nowNanos) {
        rawBox = detection.getBox();
        box = renderedBox;
        confidence = detection.getConfidence();
        lastSeenNanos = nowNanos;
        framesTracked++;
        framesMissing = 0;
        active = true;
        velocityX = dx;
        velocityY = dy;
        if (framesTracked >= 5) confirmed = true;
    }

    void miss(BBox predicted) {
        framesMissing++;
        if (predicted != null) box = predicted;
        velocityX *= 0.8f;
        velocityY *= 0.8f;
    }

    void deactivate() { active = false; }
}
