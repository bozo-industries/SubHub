package com.subhub.app.service;

import java.util.ArrayList;
import java.util.List;

/** Worker-owned numerical bridge from independently observed anchor positions to event intervals. */
final class ScrollReferenceAligner {
    private static final int MAX_POINTS = 96;
    private static final long MAX_GAP_MS = 32;
    // ViewportAnchorGeometry accepts inliers within four pixels of its translation consensus.
    private static final double GEOMETRY_ERROR_PX = 4;
    enum Status { ALIGNED, WAITING, NO_REFERENCE, WRONG_SCOPE, INVALID_EVENT, REPEATED_INTERVAL,
        EXPIRED, SPARSE_REFERENCE, UNSTABLE_REFERENCE, UNCERTAIN }

    static final class Alignment {
        final Status status;
        final ScrollCalibrationLearner.Sample sample;
        Alignment(Status status, ScrollCalibrationLearner.Sample sample) {
            this.status = status; this.sample = sample;
        }
    }

    private static final class Point {
        final long time;
        final double position;
        final int inliers, readMs;
        Point(long time, double position, int inliers, int readMs) {
            this.time = time; this.position = position; this.inliers = inliers; this.readMs = readMs;
        }
    }

    private static final class Estimate {
        final Status status;
        final double position, uncertainty;
        final int inliers, readMs;
        Estimate(Status status, double position, double uncertainty, int inliers, int readMs) {
            this.status = status; this.position = position; this.uncertainty = uncertainty;
            this.inliers = inliers; this.readMs = readMs;
        }
        static Estimate rejected(Status status) { return new Estimate(status, 0, 0, 0, 0); }
    }

    private final List<Point> points = new ArrayList<>();
    private ScrollLearningKey key;
    private long fence, baseline, lastAlignedEnd = -1;
    private long acceptedReferences, rejectedReferences, evictedReferences;

    void begin(ScrollLearningKey key, long fence) {
        this.key = key; this.fence = fence;
        points.clear(); baseline = 0; lastAlignedEnd = -1;
        acceptedReferences = rejectedReferences = evictedReferences = 0;
    }

    boolean addGeometry(ScrollLearningKey key, long fence, ViewportAnchorGeometry.Result value, long now) {
        if (!sameScope(key, fence) || value == null || !value.accepted() || value.inlierCount() < 3
                || value.baselineIdentity() <= 0 || value.baselineIdentity() < baseline
                || value.readStartUptimeMs() < 0 || value.readEndUptimeMs() < value.readStartUptimeMs()
                || value.readEndUptimeMs() > now || now - value.readStartUptimeMs() > 32
                || value.readEndUptimeMs() - value.readStartUptimeMs() > 16) {
            rejectedReferences++;
            return false;
        }
        long time = value.readStartUptimeMs() + (value.readEndUptimeMs() - value.readStartUptimeMs()) / 2;
        double position = key.axis == ScrollLearningKey.Axis.X ? value.measuredX : value.measuredY;
        if (!Double.isFinite(position) || !points.isEmpty() && time <= points.get(points.size() - 1).time) {
            rejectedReferences++;
            return false;
        }
        if (value.baselineIdentity() != baseline) {
            // Origins may differ arbitrarily. Never interpolate across reacquired anchors.
            points.clear(); baseline = value.baselineIdentity();
        }
        points.add(new Point(time, position, value.inlierCount(),
                (int) (value.readEndUptimeMs() - value.readStartUptimeMs())));
        acceptedReferences++;
        if (points.size() > MAX_POINTS) { points.remove(0); evictedReferences++; }
        return true;
    }

    Alignment align(ScrollLearningKey key, long fence, long gesture, long start, long end,
            long receivedAt, double eventDelta, long now) {
        if (!sameScope(key, fence)) return rejected(Status.WRONG_SCOPE);
        if (gesture <= 0 || start < 0 || end <= start || end - start < 16 || end - start > 300
                || receivedAt < end || receivedAt > now || !Double.isFinite(eventDelta)
                || Math.abs(eventDelta) < 8) return rejected(Status.INVALID_EVENT);
        if (!points.isEmpty()) {
            Point latest = points.get(points.size() - 1);
            if (latest.time + (latest.readMs + 1) / 2 > now) return rejected(Status.INVALID_EVENT);
        }
        if (start < lastAlignedEnd || end <= lastAlignedEnd) return rejected(Status.REPEATED_INTERVAL);
        if (now - end > ScrollCalibrationLearner.MAX_EVIDENCE_AGE_MS) return rejected(Status.EXPIRED);
        Estimate first = estimate(start), last = estimate(end);
        if (first.status != Status.ALIGNED) return rejected(first.status);
        if (last.status != Status.ALIGNED) return rejected(last.status);
        double measured = last.position - first.position;
        double uncertainty = first.uncertainty + last.uncertainty;
        if (!Double.isFinite(measured) || !Double.isFinite(uncertainty)
                || Math.abs(measured) < 8 || uncertainty > Math.abs(measured) * .05) return rejected(Status.UNCERTAIN);
        lastAlignedEnd = end;
        return new Alignment(Status.ALIGNED, new ScrollCalibrationLearner.Sample(key, fence, gesture,
                start, end, start, end, receivedAt, eventDelta, measured, uncertainty,
                Math.min(first.inliers, last.inliers), Math.max(first.readMs, last.readMs), true));
    }

    private Estimate estimate(long time) {
        if (points.isEmpty()) return Estimate.rejected(Status.NO_REFERENCE);
        if (time < points.get(0).time) return Estimate.rejected(Status.EXPIRED);
        int left = -1;
        for (int index = 0; index + 1 < points.size(); index++) {
            if (points.get(index).time <= time && time <= points.get(index + 1).time) {
                left = index;
                break;
            }
        }
        if (left < 0 || left + 2 >= points.size()) return Estimate.rejected(Status.WAITING);
        if (left == 0) return Estimate.rejected(Status.SPARSE_REFERENCE);
        Point before = points.get(left - 1), a = points.get(left), b = points.get(left + 1), after = points.get(left + 2);
        long gap = b.time - a.time;
        if (a.time - before.time > MAX_GAP_MS || gap > MAX_GAP_MS || after.time - b.time > MAX_GAP_MS) {
            return Estimate.rejected(Status.SPARSE_REFERENCE);
        }
        double previousVelocity = (a.position - before.position) / (a.time - before.time);
        double velocity = (b.position - a.position) / gap;
        double nextVelocity = (after.position - b.position) / (after.time - b.time);
        double variation = Math.max(Math.abs(previousVelocity - velocity), Math.abs(nextVelocity - velocity));
        if (!Double.isFinite(previousVelocity) || !Double.isFinite(velocity) || !Double.isFinite(nextVelocity)
                || !Double.isFinite(variation) || previousVelocity * velocity < 0 || nextVelocity * velocity < 0
                || variation > Math.max(.05, Math.abs(velocity) * .35)) {
            return Estimate.rejected(Status.UNSTABLE_REFERENCE);
        }
        // Interpolate only short, locally consistent independent measurements, never an
        // overlay forecast. Carry timing and curvature error instead of claiming an exact point.
        double weight = (time - a.time) / (double) gap;
        double maxSpeed = Math.max(Math.abs(velocity), Math.max(Math.abs(previousVelocity), Math.abs(nextVelocity)));
        int readMs = Math.max(Math.max(a.readMs, b.readMs), Math.max(before.readMs, after.readMs));
        double uncertainty = GEOMETRY_ERROR_PX + maxSpeed * readMs / 2 + variation * gap / 2;
        return new Estimate(Status.ALIGNED, a.position + (b.position - a.position) * weight,
                uncertainty, Math.min(Math.min(a.inliers, b.inliers), Math.min(before.inliers, after.inliers)), readMs);
    }

    private boolean sameScope(ScrollLearningKey key, long fence) {
        return this.key != null && this.key.equals(key) && this.fence > 0 && this.fence == fence;
    }
    private static Alignment rejected(Status status) { return new Alignment(status, null); }
    int retainedReferences() { return points.size(); }
    long acceptedReferences() { return acceptedReferences; }
    long rejectedReferences() { return rejectedReferences; }
    long evictedReferences() { return evictedReferences; }
}
