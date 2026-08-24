package com.subhub.app.detection;

import java.util.Objects;

/** One decoded detector result in original-frame coordinates. */
public final class Detection {
    private final String className;
    private final String category;
    private final float confidence;
    private final BBox box;
    private final boolean nsfw;
    private final boolean exposed;
    private int trackId = -1;

    public Detection(
            String className,
            String category,
            float confidence,
            BBox box,
            boolean nsfw,
            boolean exposed) {
        this.className = Objects.requireNonNull(className);
        this.category = Objects.requireNonNull(category);
        this.confidence = confidence;
        this.box = Objects.requireNonNull(box);
        this.nsfw = nsfw;
        this.exposed = exposed;
    }

    public String getClassName() { return className; }
    public String getCategory() { return category; }
    public float getConfidence() { return confidence; }
    public BBox getBox() { return box; }
    public boolean isNsfw() { return nsfw; }
    public boolean isExposed() { return exposed; }
    public int getTrackId() { return trackId; }
    public void setTrackId(int trackId) { this.trackId = trackId; }
}
