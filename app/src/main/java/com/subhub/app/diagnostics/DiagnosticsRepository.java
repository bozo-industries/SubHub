package com.subhub.app.diagnostics;

import android.os.SystemClock;

import java.util.Locale;

/** Process-local, secret-free runtime telemetry for the diagnostics screen and overlay. */
public final class DiagnosticsRepository {
    public static final String PREF_OVERLAY = "diagnostics_overlay_enabled";

    private static String mode = "Idle";
    private static boolean running;
    private static boolean ready;
    private static String provider = "—";
    private static String model = "—";
    private static int resolution;
    private static long startedAt;
    private static long frames;
    private static long totalDetections;
    private static long totalInferenceMs;
    private static long lastInferenceMs;
    private static long peakInferenceMs;
    private static int lastDetections;
    private static int frameWidth;
    private static int frameHeight;
    private static String lastFailure = "None";

    private DiagnosticsRepository() {}

    public static synchronized void begin(String captureMode, int inferenceResolution) {
        mode = safeLabel(captureMode, "Unknown");
        running = true;
        ready = false;
        provider = "Initializing";
        model = "Loading";
        resolution = Math.max(0, inferenceResolution);
        startedAt = SystemClock.elapsedRealtime();
        frames = 0;
        totalDetections = 0;
        totalInferenceMs = 0;
        lastInferenceMs = 0;
        peakInferenceMs = 0;
        lastDetections = 0;
        frameWidth = 0;
        frameHeight = 0;
        lastFailure = "None";
    }

    public static synchronized void ready(String captureMode, String activeProvider,
            String activeModel, int inferenceResolution) {
        if (!mode.equals(captureMode)) return;
        ready = true;
        provider = safeLabel(activeProvider, "Unknown");
        model = filenameOnly(activeModel);
        resolution = Math.max(0, inferenceResolution);
    }

    public static synchronized Snapshot recordFrame(String captureMode, long inferenceMs,
            int detections, int width, int height) {
        if (!running || !mode.equals(captureMode)) return snapshot();
        frames++;
        lastInferenceMs = Math.max(0, inferenceMs);
        peakInferenceMs = Math.max(peakInferenceMs, lastInferenceMs);
        totalInferenceMs += lastInferenceMs;
        lastDetections = Math.max(0, detections);
        totalDetections += lastDetections;
        frameWidth = Math.max(0, width);
        frameHeight = Math.max(0, height);
        return snapshot();
    }

    public static synchronized void fail(String captureMode, Throwable error) {
        if (!mode.equals(captureMode)) return;
        lastFailure = error == null ? "Unknown failure" : error.getClass().getSimpleName();
    }

    public static synchronized void failCode(String captureMode, String family, int code) {
        if (!mode.equals(captureMode)) return;
        lastFailure = safeLabel(family, "Failure") + " " + code;
    }

    public static synchronized void stop(String captureMode) {
        if (!mode.equals(captureMode)) return;
        running = false;
        ready = false;
    }

    public static synchronized Snapshot snapshot() {
        long uptime = startedAt == 0 ? 0 : Math.max(0, SystemClock.elapsedRealtime() - startedAt);
        return new Snapshot(mode, running, ready, provider, model, resolution, uptime, frames,
                totalDetections, totalInferenceMs, lastInferenceMs, peakInferenceMs,
                lastDetections, frameWidth, frameHeight, lastFailure);
    }

    public static String overlayText(Snapshot value) {
        if (value == null || !value.isRunning()) return "";
        if (!value.isReady()) return value.getMode() + " • INITIALIZING";
        return String.format(Locale.ROOT, "%s • %d ms (avg %d)\n%d regions • %dx%d",
                value.getProvider(), value.getLastInferenceMs(), value.getAverageInferenceMs(),
                value.getLastDetections(), value.getFrameWidth(), value.getFrameHeight());
    }

    private static String filenameOnly(String value) {
        if (value == null || value.trim().isEmpty()) return "Unknown";
        String normalized = value.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        return safeLabel(separator >= 0 ? normalized.substring(separator + 1) : normalized, "Unknown");
    }

    private static String safeLabel(String value, String fallback) {
        if (value == null) return fallback;
        String trimmed = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return trimmed.isEmpty() ? fallback : trimmed.substring(0, Math.min(80, trimmed.length()));
    }

    public static final class Snapshot {
        private final String mode;
        private final boolean running;
        private final boolean ready;
        private final String provider;
        private final String model;
        private final int resolution;
        private final long uptimeMs;
        private final long frames;
        private final long totalDetections;
        private final long totalInferenceMs;
        private final long lastInferenceMs;
        private final long peakInferenceMs;
        private final int lastDetections;
        private final int frameWidth;
        private final int frameHeight;
        private final String lastFailure;

        private Snapshot(String mode, boolean running, boolean ready, String provider,
                String model, int resolution, long uptimeMs, long frames, long totalDetections,
                long totalInferenceMs, long lastInferenceMs, long peakInferenceMs,
                int lastDetections, int frameWidth, int frameHeight, String lastFailure) {
            this.mode = mode;
            this.running = running;
            this.ready = ready;
            this.provider = provider;
            this.model = model;
            this.resolution = resolution;
            this.uptimeMs = uptimeMs;
            this.frames = frames;
            this.totalDetections = totalDetections;
            this.totalInferenceMs = totalInferenceMs;
            this.lastInferenceMs = lastInferenceMs;
            this.peakInferenceMs = peakInferenceMs;
            this.lastDetections = lastDetections;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
            this.lastFailure = lastFailure;
        }

        public String getMode() { return mode; }
        public boolean isRunning() { return running; }
        public boolean isReady() { return ready; }
        public String getProvider() { return provider; }
        public String getModel() { return model; }
        public int getResolution() { return resolution; }
        public long getUptimeMs() { return uptimeMs; }
        public long getFrames() { return frames; }
        public long getTotalDetections() { return totalDetections; }
        public long getLastInferenceMs() { return lastInferenceMs; }
        public long getPeakInferenceMs() { return peakInferenceMs; }
        public int getLastDetections() { return lastDetections; }
        public int getFrameWidth() { return frameWidth; }
        public int getFrameHeight() { return frameHeight; }
        public String getLastFailure() { return lastFailure; }
        public long getAverageInferenceMs() {
            return frames == 0 ? 0 : Math.round((double) totalInferenceMs / frames);
        }
    }
}
