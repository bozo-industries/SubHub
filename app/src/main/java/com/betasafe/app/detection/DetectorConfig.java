package com.betasafe.app.detection;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Runtime knobs shared by inference, postprocessing, tracking, and capture. */
public final class DetectorConfig {
    private final Set<String> enabledCategories;
    private final float confidenceThreshold;
    private final float trackingSmoothing;
    private final float trackMaxAgeSeconds;
    private final int minRemoveFrames;
    private final boolean motionPrediction;
    private final long detectionIntervalMs;
    private final float boxPadding;
    private final int minDetectionSize;
    private final float captureScale;
    private final int inferenceResolution;
    private final String modelFilename;

    private DetectorConfig(Builder builder) {
        enabledCategories = Collections.unmodifiableSet(new LinkedHashSet<>(builder.enabledCategories));
        confidenceThreshold = builder.confidenceThreshold;
        trackingSmoothing = builder.trackingSmoothing;
        trackMaxAgeSeconds = builder.trackMaxAgeSeconds;
        minRemoveFrames = builder.minRemoveFrames;
        motionPrediction = builder.motionPrediction;
        detectionIntervalMs = builder.detectionIntervalMs;
        boxPadding = builder.boxPadding;
        minDetectionSize = builder.minDetectionSize;
        captureScale = builder.captureScale;
        inferenceResolution = builder.inferenceResolution;
        modelFilename = builder.modelFilename;
    }

    public static Builder builder() { return new Builder(); }
    public Set<String> getEnabledCategories() { return enabledCategories; }
    public float getConfidenceThreshold() { return confidenceThreshold; }
    public float getTrackingSmoothing() { return trackingSmoothing; }
    public float getTrackMaxAgeSeconds() { return trackMaxAgeSeconds; }
    public int getMinRemoveFrames() { return minRemoveFrames; }
    public boolean isMotionPrediction() { return motionPrediction; }
    public long getDetectionIntervalMs() { return detectionIntervalMs; }
    public float getBoxPadding() { return boxPadding; }
    public int getMinDetectionSize() { return minDetectionSize; }
    public float getCaptureScale() { return captureScale; }
    public int getInferenceResolution() { return inferenceResolution; }
    public String getModelFilename() { return modelFilename; }

    public static final class Builder {
        private Set<String> enabledCategories = NudeNetClassCatalog.DEFAULT_ENABLED;
        private float confidenceThreshold = 0.30f;
        private float trackingSmoothing = 1.0f;
        private float trackMaxAgeSeconds = 0.2f;
        private int minRemoveFrames = 2;
        private boolean motionPrediction = true;
        private long detectionIntervalMs;
        private float boxPadding;
        private int minDetectionSize = 30;
        private float captureScale = 0.5f;
        private int inferenceResolution = 320;
        private String modelFilename = "320n_fp16.onnx";

        public Builder enabledCategories(Set<String> value) { enabledCategories = value; return this; }
        public Builder confidenceThreshold(float value) { confidenceThreshold = value; return this; }
        public Builder trackingSmoothing(float value) { trackingSmoothing = value; return this; }
        public Builder trackMaxAgeSeconds(float value) { trackMaxAgeSeconds = value; return this; }
        public Builder minRemoveFrames(int value) { minRemoveFrames = value; return this; }
        public Builder motionPrediction(boolean value) { motionPrediction = value; return this; }
        public Builder detectionIntervalMs(long value) { detectionIntervalMs = value; return this; }
        public Builder boxPadding(float value) { boxPadding = value; return this; }
        public Builder minDetectionSize(int value) { minDetectionSize = value; return this; }
        public Builder captureScale(float value) { captureScale = value; return this; }
        public Builder inferenceResolution(int value) { inferenceResolution = value; return this; }
        public Builder modelFilename(String value) { modelFilename = value; return this; }

        public DetectorConfig build() {
            if (enabledCategories == null || modelFilename == null || modelFilename.trim().isEmpty()) {
                throw new IllegalArgumentException("Categories and model filename are required");
            }
            if (confidenceThreshold < 0f || confidenceThreshold > 1f
                    || trackingSmoothing <= 0f || trackingSmoothing > 1f
                    || inferenceResolution <= 0 || captureScale <= 0f || captureScale > 1f) {
                throw new IllegalArgumentException("Detector configuration is out of range");
            }
            return new DetectorConfig(this);
        }
    }
}
