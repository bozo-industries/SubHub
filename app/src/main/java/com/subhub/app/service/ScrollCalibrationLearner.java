package com.subhub.app.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure numerical calibration gate. It does not collect data or mutate renderer/camera state. */
final class ScrollCalibrationLearner {
    enum State { DISABLED, LEARNING, VALIDATING, READY, INSUFFICIENT }
    enum Result { REJECTED, TRAINING, CANDIDATE, VALIDATING, VALIDATION_FAILED, READY,
        INVALIDATED, BUDGET_EXHAUSTED }
    private static final int TRAINING_COUNT = 12, VALIDATION_COUNT = 6, MAX_TRAINING = 32;
    private static final long MOTION_BUDGET_MS = 6000;
    // Calibration fits settings from retained history; this is NOT a live-pose freshness limit.
    static final long MAX_EVIDENCE_AGE_MS = 500;

    /** Adapter must pair independent geometry and event displacement over the same interval. */
    static final class Sample {
        final ScrollLearningKey key;
        final long fence, gesture, eventStart, eventEnd, referenceStart, referenceEnd, receivedAt;
        final double eventDelta, measuredDelta, uncertaintyPixels;
        final int inliers, readDurationMs;
        final boolean independent;

        Sample(ScrollLearningKey key, long fence, long gesture, long eventStart, long eventEnd,
                long referenceStart, long referenceEnd, long receivedAt, double eventDelta,
                double measuredDelta, double uncertaintyPixels, int inliers, int readDurationMs,
                boolean independent) {
            this.key = key; this.fence = fence; this.gesture = gesture;
            this.eventStart = eventStart; this.eventEnd = eventEnd;
            this.referenceStart = referenceStart; this.referenceEnd = referenceEnd;
            this.receivedAt = receivedAt; this.eventDelta = eventDelta;
            this.measuredDelta = measuredDelta; this.uncertaintyPixels = uncertaintyPixels;
            this.inliers = inliers; this.readDurationMs = readDurationMs; this.independent = independent;
        }
    }

    static final class Profile {
        final ScrollLearningKey key;
        final double pixelsPerEventPixel, eventIntervalMs, deliveryLagMs, deliveryJitterMs;
        final double validationMeanErrorPx;
        final int trainingSamples, validationSamples, gestures;

        Profile(ScrollLearningKey key, double scale, double interval, double lag, double jitter,
                double error, int training, int validation, int gestures) {
            this.key = key; pixelsPerEventPixel = scale; eventIntervalMs = interval;
            deliveryLagMs = lag; deliveryJitterMs = jitter; validationMeanErrorPx = error;
            trainingSamples = training; validationSamples = validation; this.gestures = gestures;
        }
    }

    private State state = State.DISABLED;
    private ScrollLearningKey key;
    private long fence, lastEnd = -1, observedMotionMs;
    private int rejected, accepted, consecutiveErrors;
    private final List<Sample> training = new ArrayList<>(), validation = new ArrayList<>();
    private final Set<Long> trainingGestures = new HashSet<>(), validationGestures = new HashSet<>();
    private double candidateScale;
    private Profile profile;
    private Profile prior;

    void begin(boolean censoringActive, ScrollLearningKey key, long fence) {
        clearEvidence();
        this.key = key; this.fence = fence;
        state = censoringActive && key != null && fence > 0 ? State.LEARNING : State.DISABLED;
    }

    void disable() { begin(false, null, 0); }
    State state() { return state; }
    Profile profile() { return state == State.READY ? profile : null; }
    int rejectedSamples() { return rejected; }
    int acceptedSamples() { return accepted; }
    long observedMotionMillis() { return observedMotionMs; }

    /** A matching persisted profile skips fitting, not fresh independent validation. */
    boolean useCandidate(Profile candidate) {
        if (state != State.LEARNING || !training.isEmpty() || candidate == null
                || !key.equals(candidate.key) || !Double.isFinite(candidate.pixelsPerEventPixel)
                || candidate.pixelsPerEventPixel < .125 || candidate.pixelsPerEventPixel > 8
                || candidate.trainingSamples < 12 || candidate.trainingSamples > 32
                || candidate.validationSamples < 6 || candidate.validationSamples > 32
                || candidate.gestures < 3 || candidate.gestures > 64) return false;
        prior = candidate;
        candidateScale = candidate.pixelsPerEventPixel;
        state = State.VALIDATING;
        return true;
    }

    Result observe(Sample sample, long now) {
        if (state == State.DISABLED || state == State.INSUFFICIENT || !valid(sample, now)) {
            rejected++;
            return Result.REJECTED;
        }
        lastEnd = sample.eventEnd;
        if (state != State.READY && !learnable(sample)) {
            rejected++;
            return Result.REJECTED;
        }
        accepted++;
        if (state == State.READY) {
            if (fits(sample, profile.pixelsPerEventPixel)) consecutiveErrors = 0;
            else consecutiveErrors++;
            if (consecutiveErrors < 3) return Result.READY;
            clearEvidence();
            lastEnd = sample.eventEnd;
            state = State.LEARNING;
            return Result.INVALIDATED;
        }
        observedMotionMs += sample.eventEnd - sample.eventStart;
        if (observedMotionMs > MOTION_BUDGET_MS) {
            state = State.INSUFFICIENT;
            return Result.BUDGET_EXHAUSTED;
        }
        if (state == State.LEARNING) {
            training.add(sample);
            trainingGestures.add(sample.gesture);
            if (training.size() >= TRAINING_COUNT && trainingGestures.size() >= 2) {
                candidateScale = median(training, 0);
                int inliers = 0;
                for (Sample entry : training) if (fits(entry, candidateScale)) inliers++;
                if (inliers >= Math.ceil(training.size() * .85)) {
                    state = State.VALIDATING;
                    return Result.CANDIDATE;
                }
            }
            if (training.size() >= MAX_TRAINING) {
                state = State.INSUFFICIENT;
                return Result.BUDGET_EXHAUSTED;
            }
            return Result.TRAINING;
        }
        // Holdout uses a later gesture, not samples reused to fit the same model.
        if (trainingGestures.contains(sample.gesture)) return Result.VALIDATING;
        if (!fits(sample, candidateScale)) {
            state = State.INSUFFICIENT;
            return Result.VALIDATION_FAILED;
        }
        validation.add(sample);
        validationGestures.add(sample.gesture);
        if (validation.size() < VALIDATION_COUNT) return Result.VALIDATING;
        double error = 0;
        for (Sample entry : validation) {
            error += Math.abs(entry.measuredDelta - candidateScale * entry.eventDelta);
        }
        List<Sample> timingSamples = prior == null ? training : validation;
        double lag = median(timingSamples, 2);
        List<Double> deviations = new ArrayList<>();
        for (Sample entry : timingSamples) deviations.add(Math.abs(entry.receivedAt - entry.eventEnd - lag));
        profile = new Profile(key, candidateScale, median(timingSamples, 1), lag, medianValues(deviations),
                error / validation.size(), prior == null ? training.size() : prior.trainingSamples, validation.size(),
                prior == null ? trainingGestures.size() + validationGestures.size() : prior.gestures);
        state = State.READY;
        return Result.READY;
    }

    private boolean valid(Sample s, long now) {
        if (s == null || !key.equals(s.key) || s.fence != fence || s.gesture <= 0 || !s.independent
                || s.inliers < 3 || s.readDurationMs < 0 || s.readDurationMs > 16
                || !Double.isFinite(s.eventDelta) || !Double.isFinite(s.measuredDelta)
                || !Double.isFinite(s.uncertaintyPixels) || s.uncertaintyPixels < 0
                || s.eventStart < 0 || s.eventEnd <= s.eventStart || s.eventEnd > now
                || s.referenceStart < 0 || s.referenceEnd <= s.referenceStart || s.referenceEnd > now
                || s.receivedAt < s.eventEnd || s.receivedAt > now
                || now - s.referenceEnd > MAX_EVIDENCE_AGE_MS
                || s.eventStart < lastEnd || s.eventEnd <= lastEnd) return false;
        long interval = s.eventEnd - s.eventStart;
        if (interval < 16 || interval > 300) return false;
        long alignmentTolerance = Math.min(4, interval / 20);
        if (Math.abs(s.referenceStart - s.eventStart) > alignmentTolerance
                || Math.abs(s.referenceEnd - s.eventEnd) > alignmentTolerance) return false;
        if (Math.abs(s.eventDelta) < 8 || Math.abs(s.measuredDelta) < 8
                || s.uncertaintyPixels > Math.abs(s.measuredDelta) * .05) return false;
        return true;
    }

    private static boolean learnable(Sample s) {
        double ratio = s.measuredDelta / s.eventDelta;
        return ratio >= .125 && ratio <= 8;
    }

    private static boolean fits(Sample sample, double scale) {
        return Math.abs(sample.measuredDelta - scale * sample.eventDelta)
                <= Math.max(2, Math.abs(sample.measuredDelta) * .08) + sample.uncertaintyPixels;
    }

    private static double median(List<Sample> samples, int field) {
        List<Double> values = new ArrayList<>();
        for (Sample sample : samples) values.add(field == 0 ? sample.measuredDelta / sample.eventDelta
                : field == 1 ? (double) (sample.eventEnd - sample.eventStart)
                : (double) (sample.receivedAt - sample.eventEnd));
        return medianValues(values);
    }

    private static double medianValues(List<Double> values) {
        values.sort(Double::compare);
        int middle = values.size() / 2;
        return values.size() % 2 == 0 ? (values.get(middle - 1) + values.get(middle)) / 2 : values.get(middle);
    }

    private void clearEvidence() {
        training.clear(); validation.clear(); trainingGestures.clear(); validationGestures.clear();
        profile = prior = null; lastEnd = -1; observedMotionMs = 0; accepted = rejected = consecutiveErrors = 0;
        candidateScale = 0;
    }
}
