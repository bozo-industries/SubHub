package com.subhub.app.service;

/** Main-thread adapter: transforms future increments, never absolute/historical camera values. */
final class ScrollMotionCalibration {
    private AutomaticScrollLearningObserver.Scope scope;
    private ScrollCalibrationLearner.Profile profile;
    private ScrollCalibrationLearner.Profile rejectedProfile;
    private double residual;
    private long appliedEvents, transitions;

    static final class Motion {
        final int dx, dy;
        final boolean calibrated, scaleChanged;
        final ScrollCalibrationLearner.Profile profile;
        Motion(int dx, int dy, boolean calibrated, boolean scaleChanged,
                ScrollCalibrationLearner.Profile profile) {
            this.dx = dx; this.dy = dy; this.calibrated = calibrated;
            this.scaleChanged = scaleChanged; this.profile = profile;
        }
    }

    Motion apply(AutomaticScrollLearningObserver.Scope nextScope,
            ScrollCalibrationLearner.Profile candidate, int dx, int dy) {
        if (nextScope == null || !nextScope.equals(scope)) rejectedProfile = null;
        if (candidate == rejectedProfile) candidate = null;
        if (!valid(candidate, nextScope) || dx != 0 && dy != 0
                || nextScope != null && (nextScope.axis == ScrollLearningKey.Axis.X ? dy != 0 : dx != 0)) {
            candidate = null;
        }
        double oldScale = profile == null ? 1 : profile.pixelsPerEventPixel;
        double newScale = candidate == null ? 1 : candidate.pixelsPerEventPixel;
        boolean changed = Double.compare(oldScale, newScale) != 0;
        if (changed || nextScope == null || !nextScope.equals(scope)) residual = 0;
        scope = nextScope;
        profile = candidate;
        if (candidate == null) {
            if (changed) transitions++;
            return new Motion(dx, dy, false, changed, null);
        }
        double value = (candidate.key.axis == ScrollLearningKey.Axis.X ? dx : dy) * newScale;
        int extent = candidate.key.axis == ScrollLearningKey.Axis.X ? nextScope.width : nextScope.height;
        // Never hide an unsafe extrapolation behind a downstream clamp. Revoke this handoff
        // and use the pre-calibration producer value for the exceptional event.
        if (Math.abs(value) > 2.0 * extent || !Double.isFinite(value)) {
            rejectedProfile = candidate; profile = null; residual = 0;
            boolean reverted = Double.compare(oldScale, 1) != 0;
            if (reverted) transitions++;
            return new Motion(dx, dy, false, reverted, null);
        }
        if (changed) transitions++;
        long rounded = Math.round(value + residual);
        residual += value - rounded;
        appliedEvents++;
        return new Motion(candidate.key.axis == ScrollLearningKey.Axis.X ? (int) rounded : 0,
                candidate.key.axis == ScrollLearningKey.Axis.Y ? (int) rounded : 0,
                true, changed, candidate);
    }

    private static boolean valid(ScrollCalibrationLearner.Profile candidate,
            AutomaticScrollLearningObserver.Scope scope) {
        return candidate != null && scope != null && candidate.key != null
                && candidate.key.packageName.equals(scope.packageName)
                && candidate.key.width == scope.width && candidate.key.height == scope.height
                && candidate.key.densityDpi == scope.densityDpi && candidate.key.rotation == scope.rotation
                && candidate.key.refreshMilliHz == scope.refreshMilliHz
                && candidate.key.axis == scope.axis && candidate.key.evidence == scope.evidence
                && Double.isFinite(candidate.pixelsPerEventPixel)
                && candidate.pixelsPerEventPixel >= .125 && candidate.pixelsPerEventPixel <= 8
                && Double.isFinite(candidate.eventIntervalMs)
                && candidate.eventIntervalMs >= 1 && candidate.eventIntervalMs <= 300
                && Double.isFinite(candidate.deliveryLagMs) && candidate.deliveryLagMs >= 0
                && candidate.deliveryLagMs <= 500
                && Double.isFinite(candidate.deliveryJitterMs) && candidate.deliveryJitterMs >= 0
                && candidate.deliveryJitterMs <= 500 && candidate.trainingSamples >= 12
                && candidate.validationSamples >= 6 && candidate.gestures >= 3;
    }

    void reset() { scope = null; profile = null; rejectedProfile = null; residual = 0; }

    String diagnostics() {
        return "\"applied\":" + (profile != null) + ",\"appliedEvents\":" + appliedEvents
                + ",\"scaleTransitions\":" + transitions + ",\"activeScale\":"
                + (profile == null ? 1 : profile.pixelsPerEventPixel);
    }
}
