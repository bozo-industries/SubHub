package com.subhub.app.overlay;

import com.subhub.app.detection.BBox;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Persistent display-time geometry steering keyed by stable tracker identity.
 *
 * <p>Detector publications update targets; they never replace the currently displayed position.
 * Each display sample advances monotonically toward a projected target, so a course correction
 * bends an existing path instead of restarting from rest or jumping to a new snapshot.</p>
 */
final class ContinuousTrackSteering {
    private static final float RESPONSE_TIME_MS = 45f;
    private static final long MIN_OBSERVATION_GAP_MS = 16L;
    private static final long MAX_OBSERVATION_GAP_MS = 600L;
    private static final long MIN_PREDICTION_HALF_WINDOW_MS = 64L;
    private static final long MAX_PREDICTION_HALF_WINDOW_MS = 320L;
    private static final float VELOCITY_ALPHA = 0.55f;
    private static final float REVERSAL_VELOCITY_ALPHA = 0.72f;
    private static final float PREDICTION_FRACTION = 0.45f;
    private static final float MAX_NORMALIZED_VELOCITY_PER_MS = 0.003f;
    private static final float POSITION_EPSILON = 0.0004f;
    private static final float SIZE_EPSILON = 0.0003f;

    private final Map<Integer, State> states = new HashMap<>();

    void updateTarget(
            int id,
            BBox box,
            int sourceWidth,
            int sourceHeight,
            long nowMillis,
            boolean predictMotion) {
        if (box == null) return;
        float width = Math.max(1, sourceWidth);
        float height = Math.max(1, sourceHeight);
        float targetWidth = box.getWidth() / width;
        float targetHeight = box.getHeight() / height;
        float targetCenterX = (box.getX() + box.getWidth() * 0.5f) / width;
        float targetCenterY = (box.getY() + box.getHeight() * 0.5f) / height;
        State state = states.get(id);
        if (state == null) {
            states.put(id, new State(targetCenterX, targetCenterY,
                    targetWidth, targetHeight, nowMillis, predictMotion));
            return;
        }
        state.updateTarget(targetCenterX, targetCenterY, targetWidth, targetHeight,
                nowMillis, predictMotion);
    }

    BBox position(int id, int sourceWidth, int sourceHeight, long nowMillis) {
        State state = states.get(id);
        if (state == null) return null;
        state.advance(nowMillis);
        int width = Math.max(1, sourceWidth);
        int height = Math.max(1, sourceHeight);
        int boxWidth = Math.max(1, Math.round(state.currentWidth * width));
        int boxHeight = Math.max(1, Math.round(state.currentHeight * height));
        int x = Math.round(state.currentCenterX * width - boxWidth * 0.5f);
        int y = Math.round(state.currentCenterY * height - boxHeight * 0.5f);
        return new BBox(x, y, boxWidth, boxHeight);
    }

    void offsetAll(float normalizedDx, float normalizedDy, long nowMillis) {
        if (normalizedDx == 0f && normalizedDy == 0f) return;
        for (State state : states.values()) {
            state.advance(nowMillis);
            state.currentCenterX += normalizedDx;
            state.currentCenterY += normalizedDy;
            state.targetCenterX += normalizedDx;
            state.targetCenterY += normalizedDy;
            // This translation changes coordinate origins; it is not subject velocity. The next
            // detector target should steer out the residual viewport lead without extrapolating
            // that correction as if the face itself had started moving.
            state.suppressNextVelocity = true;
        }
    }

    void retain(Set<Integer> activeIds) {
        states.keySet().retainAll(activeIds);
    }

    void clear() {
        states.clear();
    }

    boolean isAnimating(long nowMillis) {
        for (State state : states.values()) {
            state.advance(nowMillis);
            if (state.isAnimating(nowMillis)) return true;
        }
        return false;
    }

    int size() {
        return states.size();
    }

    private static final class State {
        private float currentCenterX;
        private float currentCenterY;
        private float currentWidth;
        private float currentHeight;
        private float targetCenterX;
        private float targetCenterY;
        private float targetWidth;
        private float targetHeight;
        private float velocityCenterX;
        private float velocityCenterY;
        private float velocityWidth;
        private float velocityHeight;
        private long lastObservationMillis;
        private long lastSampleMillis;
        private long predictionHalfWindowMillis;
        private boolean predictMotion;
        private boolean suppressNextVelocity;

        State(
                float centerX,
                float centerY,
                float width,
                float height,
                long nowMillis,
                boolean predictMotion) {
            currentCenterX = targetCenterX = centerX;
            currentCenterY = targetCenterY = centerY;
            currentWidth = targetWidth = width;
            currentHeight = targetHeight = height;
            lastObservationMillis = nowMillis;
            lastSampleMillis = nowMillis;
            this.predictMotion = predictMotion;
        }

        void updateTarget(
                float centerX,
                float centerY,
                float width,
                float height,
                long nowMillis,
                boolean shouldPredict) {
            advance(nowMillis);
            long gap = nowMillis - lastObservationMillis;
            if (!suppressNextVelocity && shouldPredict && gap >= MIN_OBSERVATION_GAP_MS
                    && gap <= MAX_OBSERVATION_GAP_MS) {
                float measuredCenterX = clampVelocity((centerX - targetCenterX) / gap);
                float measuredCenterY = clampVelocity((centerY - targetCenterY) / gap);
                float measuredWidth = clampVelocity((width - targetWidth) / gap);
                float measuredHeight = clampVelocity((height - targetHeight) / gap);
                velocityCenterX = blendVelocity(velocityCenterX, measuredCenterX);
                velocityCenterY = blendVelocity(velocityCenterY, measuredCenterY);
                velocityWidth = blendVelocity(velocityWidth, measuredWidth);
                velocityHeight = blendVelocity(velocityHeight, measuredHeight);
                predictionHalfWindowMillis = Math.max(MIN_PREDICTION_HALF_WINDOW_MS,
                        Math.min(MAX_PREDICTION_HALF_WINDOW_MS, gap));
            } else {
                velocityCenterX = velocityCenterY = 0f;
                velocityWidth = velocityHeight = 0f;
                predictionHalfWindowMillis = 0L;
            }
            targetCenterX = centerX;
            targetCenterY = centerY;
            targetWidth = Math.max(SIZE_EPSILON, width);
            targetHeight = Math.max(SIZE_EPSILON, height);
            lastObservationMillis = nowMillis;
            predictMotion = shouldPredict;
            suppressNextVelocity = false;
        }

        void advance(long nowMillis) {
            long elapsed = Math.max(0L, nowMillis - lastSampleMillis);
            if (elapsed <= 0L) return;
            float projectedCenterX = targetCenterX + predictionLead(
                    velocityCenterX, targetWidth, nowMillis, true);
            float projectedCenterY = targetCenterY + predictionLead(
                    velocityCenterY, targetHeight, nowMillis, true);
            float projectedWidth = Math.max(SIZE_EPSILON, targetWidth + predictionLead(
                    velocityWidth, targetWidth, nowMillis, false));
            float projectedHeight = Math.max(SIZE_EPSILON, targetHeight + predictionLead(
                    velocityHeight, targetHeight, nowMillis, false));
            float alpha = 1f - (float) Math.exp(-elapsed / RESPONSE_TIME_MS);
            currentCenterX += (projectedCenterX - currentCenterX) * alpha;
            currentCenterY += (projectedCenterY - currentCenterY) * alpha;
            currentWidth += (projectedWidth - currentWidth) * alpha;
            currentHeight += (projectedHeight - currentHeight) * alpha;
            lastSampleMillis = nowMillis;
        }

        boolean isAnimating(long nowMillis) {
            float projectedCenterX = targetCenterX + predictionLead(
                    velocityCenterX, targetWidth, nowMillis, true);
            float projectedCenterY = targetCenterY + predictionLead(
                    velocityCenterY, targetHeight, nowMillis, true);
            float projectedWidth = targetWidth + predictionLead(
                    velocityWidth, targetWidth, nowMillis, false);
            float projectedHeight = targetHeight + predictionLead(
                    velocityHeight, targetHeight, nowMillis, false);
            boolean predictionActive = predictMotion && predictionHalfWindowMillis > 0L
                    && nowMillis - lastObservationMillis < predictionHalfWindowMillis * 2L;
            return predictionActive
                    || Math.abs(projectedCenterX - currentCenterX) > POSITION_EPSILON
                    || Math.abs(projectedCenterY - currentCenterY) > POSITION_EPSILON
                    || Math.abs(projectedWidth - currentWidth) > SIZE_EPSILON
                    || Math.abs(projectedHeight - currentHeight) > SIZE_EPSILON;
        }

        private float predictionLead(
                float velocity,
                float dimension,
                long nowMillis,
                boolean position) {
            if (!predictMotion || predictionHalfWindowMillis <= 0L
                    || Math.abs(velocity) < 0.000001f) return 0f;
            long age = Math.max(0L, nowMillis - lastObservationMillis);
            long duration = predictionHalfWindowMillis * 2L;
            if (age >= duration) return 0f;
            float progress = age / (float) duration;
            float wave = (float) Math.sin(Math.PI * progress);
            float amplitude = velocity * predictionHalfWindowMillis * PREDICTION_FRACTION;
            float maximum = Math.max(position ? 0.02f : 0.01f,
                    dimension * (position ? 0.75f : 0.35f));
            return clamp(amplitude, -maximum, maximum) * wave * wave;
        }

        private static float blendVelocity(float previous, float measured) {
            boolean reversal = previous != 0f && measured != 0f
                    && Math.signum(previous) != Math.signum(measured);
            float alpha = reversal ? REVERSAL_VELOCITY_ALPHA : VELOCITY_ALPHA;
            return previous * (1f - alpha) + measured * alpha;
        }
    }

    private static float clampVelocity(float value) {
        return clamp(value, -MAX_NORMALIZED_VELOCITY_PER_MS,
                MAX_NORMALIZED_VELOCITY_PER_MS);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
