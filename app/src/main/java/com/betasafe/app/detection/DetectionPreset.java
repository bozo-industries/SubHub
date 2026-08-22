package com.betasafe.app.detection;

import java.util.Locale;

/** Recovered Beta Blocker 1.67 quality/performance presets. */
public enum DetectionPreset {
    LOW("Low", "Battery saver", 0.38f, 150, 28, 0.35f, 320, 15, 0.80f, 0.60f, 200f),
    MEDIUM("Medium", "Recommended", 0.30f, 0, 20, 0.45f, 320, 25, 0.75f, 0.55f, 180f),
    HIGH("High", "Better quality", 0.25f, 0, 12, 0.50f, 480, 35, 0.70f, 0.50f, 230f),
    ULTRA("Ultra", "Maximum", 0.18f, 0, 6, 0.75f, 640, 50, 0.65f, 0.45f, 300f);

    private final String displayName;
    private final String description;
    private final float confidence;
    private final long intervalMs;
    private final int minimumSize;
    private final float captureScale;
    private final int inferenceResolution;
    private final int idleFrames;
    private final float renderAlpha;
    private final float velocitySmoothing;
    private final float maximumExtrapolationMs;

    DetectionPreset(
            String displayName,
            String description,
            float confidence,
            long intervalMs,
            int minimumSize,
            float captureScale,
            int inferenceResolution,
            int idleFrames,
            float renderAlpha,
            float velocitySmoothing,
            float maximumExtrapolationMs) {
        this.displayName = displayName;
        this.description = description;
        this.confidence = confidence;
        this.intervalMs = intervalMs;
        this.minimumSize = minimumSize;
        this.captureScale = captureScale;
        this.inferenceResolution = inferenceResolution;
        this.idleFrames = idleFrames;
        this.renderAlpha = renderAlpha;
        this.velocitySmoothing = velocitySmoothing;
        this.maximumExtrapolationMs = maximumExtrapolationMs;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public float getConfidence() { return confidence; }
    public long getIntervalMs() { return intervalMs; }
    public int getMinimumSize() { return minimumSize; }
    public float getCaptureScale() { return captureScale; }
    public int getInferenceResolution() { return inferenceResolution; }

    public DetectorConfig.Builder applyTo(DetectorConfig.Builder builder) {
        return builder
                .confidenceThreshold(confidence)
                .detectionIntervalMs(intervalMs)
                .minDetectionSize(minimumSize)
                .captureScale(captureScale)
                .inferenceResolution(inferenceResolution)
                .idleThresholdFrames(idleFrames)
                .renderAlpha(renderAlpha)
                .velocitySmoothing(velocitySmoothing)
                .maxExtrapolationMs(maximumExtrapolationMs);
    }

    public String preferenceValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DetectionPreset fromPreference(String value) {
        if (value != null) {
            for (DetectionPreset preset : values()) {
                if (preset.name().equalsIgnoreCase(value)
                        || preset.displayName.equalsIgnoreCase(value)) return preset;
            }
        }
        return MEDIUM;
    }
}
