package com.subhub.app.detection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Greedy IoU/distance tracker that keeps censor boxes stable between inference frames. */
public final class ObjectTracker {
    private static final float IOU_THRESHOLD = 0.20f;
    private static final float MAX_VELOCITY_PER_MS = 3f;

    private final Map<Integer, TrackedObject> tracks = new LinkedHashMap<>();
    private DetectorConfig config;
    private int nextId = 1;

    public ObjectTracker(DetectorConfig config) {
        this.config = config;
    }

    public synchronized List<TrackedObject> update(List<Detection> detections) {
        return update(detections, System.nanoTime());
    }

    /** Deterministic-time entry point used by capture timestamps and replay verification. */
    public synchronized List<TrackedObject> update(List<Detection> detections, long nowNanos) {
        List<MatchCandidate> candidates = new ArrayList<>();
        for (int detectionIndex = 0; detectionIndex < detections.size(); detectionIndex++) {
            Detection detection = detections.get(detectionIndex);
            for (TrackedObject track : tracks.values()) {
                // Classification may flicker between adjacent NudeNet labels for the same region.
                // Spatial continuity owns the identity so one stable image cannot create a new
                // censor (and a new ledger event) merely because its label changed for one frame.
                if (!track.isActive()) continue;
                float score = matchScore(detection, track, nowNanos,
                        config.getMaxExtrapolationMs());
                if (score >= IOU_THRESHOLD) {
                    candidates.add(new MatchCandidate(detectionIndex, track.getId(), score));
                }
            }
        }
        candidates.sort(Comparator.comparing(MatchCandidate::getScore).reversed());

        boolean[] matchedDetections = new boolean[detections.size()];
        Set<Integer> matchedTracks = new HashSet<>();
        for (MatchCandidate candidate : candidates) {
            if (matchedDetections[candidate.detectionIndex]
                    || matchedTracks.contains(candidate.trackId)) continue;
            Detection detection = detections.get(candidate.detectionIndex);
            TrackedObject track = tracks.get(candidate.trackId);
            if (track == null) continue;

            float elapsedMs = Math.max(1f,
                    (nowNanos - track.getLastSeenNanos()) / 1_000_000f);
            float measuredX = clamp(
                    (detection.getBox().getCenterX() - track.getRawBox().getCenterX()) / elapsedMs,
                    -MAX_VELOCITY_PER_MS,
                    MAX_VELOCITY_PER_MS);
            float measuredY = clamp(
                    (detection.getBox().getCenterY() - track.getRawBox().getCenterY()) / elapsedMs,
                    -MAX_VELOCITY_PER_MS,
                    MAX_VELOCITY_PER_MS);
            float velocitySmoothing = config.getVelocitySmoothing();
            float dx = track.getVelocityX() * (1f - velocitySmoothing)
                    + measuredX * velocitySmoothing;
            float dy = track.getVelocityY() * (1f - velocitySmoothing)
                    + measuredY * velocitySmoothing;
            // Text geometry is already screen-aligned. Smoothing makes a corrected OCR bar trail
            // behind its text and remain visibly displaced for several frames.
            BBox rendered = "text_smut".equals(detection.getCategory())
                    ? detection.getBox()
                    : config.isMotionPrediction()
                            ? smooth(track.getBox(), detection.getBox(),
                                    config.getTrackingSmoothing())
                            : smoothEventOwnedGeometry(track.getBox(), detection.getBox(),
                                    config.getTrackingSmoothing());
            track.update(detection, rendered, dx, dy, nowNanos);
            detection.setTrackId(track.getId());
            matchedDetections[candidate.detectionIndex] = true;
            matchedTracks.add(track.getId());
        }

        for (int index = 0; index < detections.size(); index++) {
            Detection detection = detections.get(index);
            if (!matchedDetections[index]
                    && detection.getConfidence() >= config.getConfidenceThreshold()) {
                TrackedObject covering = coveringTrack(detection.getBox());
                if (covering != null) {
                    // Effects such as blur, mosaic, labels, and static can produce several nested
                    // model boxes when a display capture includes the overlay. Keep the existing
                    // stable region instead of recursively spawning censor-on-censor tracks.
                    detection.setTrackId(covering.getId());
                    continue;
                }
                int id = nextId++;
                // Visual model results have already passed class-specific confidence and
                // ambiguity filters, so hiding them until the next expensive screenshot adds a
                // complete capture interval of latency. Anchored accessibility text has also
                // already survived TextDetectionStabilizer's two independent classifier scans.
                // Only unanchored text keeps the tracker's own second-observation gate.
                boolean visibleImmediately = !"text_smut".equals(detection.getCategory())
                        || detection.getAnchorKey() != null;
                TrackedObject track = new TrackedObject(
                        id, detection, nowNanos, visibleImmediately);
                tracks.put(id, track);
                detection.setTrackId(id);
            }
        }

        Iterator<Map.Entry<Integer, TrackedObject>> iterator = tracks.entrySet().iterator();
        while (iterator.hasNext()) {
            TrackedObject track = iterator.next().getValue();
            if (matchedTracks.contains(track.getId()) || track.getLastSeenNanos() == nowNanos) continue;
            BBox predicted = config.isMotionPrediction() && track.getFramesMissing() < 4
                    ? track.predict(nowNanos, config.getMaxExtrapolationMs())
                    : null;
            track.miss(predicted);
            float ageSeconds = (nowNanos - track.getLastSeenNanos()) / 1_000_000_000f;
            if ((!track.isVisible() && track.getFramesMissing() >= 1)
                    || (track.getFramesMissing() >= config.getMinRemoveFrames()
                    && ageSeconds > config.getTrackMaxAgeSeconds())) {
                iterator.remove();
            }
        }
        return activeTracks();
    }

    public synchronized List<TrackedObject> activeTracks() {
        List<TrackedObject> active = new ArrayList<>();
        for (TrackedObject track : tracks.values()) {
            if (track.isActive() && track.isVisible()) active.add(track);
        }
        return Collections.unmodifiableList(active);
    }

    public synchronized void clear() {
        tracks.clear();
        nextId = 1;
    }

    /** Keeps tracker identity in the same moving screen coordinate space as the overlay. */
    public synchronized void offsetActiveTracks(int dx, int dy, int frameWidth, int frameHeight) {
        if (dx == 0 && dy == 0) return;
        for (TrackedObject track : tracks.values()) {
            if (track.isActive()) track.offset(dx, dy, frameWidth, frameHeight);
        }
    }

    public synchronized void setConfig(DetectorConfig config) {
        this.config = config;
    }

    synchronized int retainedTrackCount() {
        return tracks.size();
    }

    private static float matchScore(
            Detection detection,
            TrackedObject track,
            long nowNanos,
            float maxExtrapolationMs) {
        if (detection.getAnchorKey() != null
                && detection.getAnchorKey().equals(track.getAnchorKey())) return 2f;
        BBox detectionBox = detection.getBox();
        float iou = detectionBox.intersectionOverUnion(track.getBox());
        BBox predicted = track.predict(nowNanos, maxExtrapolationMs);
        iou = Math.max(iou, detectionBox.intersectionOverUnion(predicted));
        float dx = detectionBox.getCenterX() - track.getBox().getCenterX();
        float dy = detectionBox.getCenterY() - track.getBox().getCenterY();
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float limit = Math.min(
                Math.max(
                        Math.max(detectionBox.getWidth(), detectionBox.getHeight()),
                        Math.max(track.getBox().getWidth(), track.getBox().getHeight())) * 2f,
                200f);
        if (distance < limit) iou = Math.max(iou, (1f - distance / limit) * 0.5f);
        return iou;
    }

    private TrackedObject coveringTrack(BBox candidate) {
        for (TrackedObject track : tracks.values()) {
            if (!track.isActive() || track.getFramesTracked() < 2) continue;
            BBox existing = track.getBox();
            int left = Math.max(candidate.getX(), existing.getX());
            int top = Math.max(candidate.getY(), existing.getY());
            int right = Math.min(candidate.getRight(), existing.getRight());
            int bottom = Math.min(candidate.getBottom(), existing.getBottom());
            if (right <= left || bottom <= top) continue;
            long intersection = (long) (right - left) * (bottom - top);
            long smaller = Math.min(candidate.getArea(), existing.getArea());
            float containment = smaller > 0 ? (float) intersection / smaller : 0f;
            if (containment >= 0.70f || candidate.intersectionOverUnion(existing) >= 0.45f) {
                return track;
            }
        }
        return null;
    }

    private static BBox smooth(BBox current, BBox target, float alpha) {
        return new BBox(
                (int) (current.getX() + (target.getX() - current.getX()) * alpha),
                (int) (current.getY() + (target.getY() - current.getY()) * alpha),
                (int) (current.getWidth() + (target.getWidth() - current.getWidth()) * alpha),
                (int) (current.getHeight() + (target.getHeight() - current.getHeight()) * alpha));
    }

    /**
     * Accessibility already supplies real viewport motion. Ignore sub-box detector wobble while
     * still following a genuinely moving/resizing subject once it exits a proportional dead zone.
     */
    private static BBox smoothEventOwnedGeometry(BBox current, BBox target, float alpha) {
        int minimumDimension = Math.max(1,
                Math.min(current.getWidth(), current.getHeight()));
        int centerTolerance = Math.max(3, Math.round(minimumDimension * 0.035f));
        int sizeTolerance = Math.max(4, Math.round(minimumDimension * 0.05f));
        int centerDelta = Math.max(
                Math.abs(current.getCenterX() - target.getCenterX()),
                Math.abs(current.getCenterY() - target.getCenterY()));
        int sizeDelta = Math.max(
                Math.abs(current.getWidth() - target.getWidth()),
                Math.abs(current.getHeight() - target.getHeight()));
        if (centerDelta <= centerTolerance && sizeDelta <= sizeTolerance) return current;
        return smooth(current, target, alpha);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class MatchCandidate {
        private final int detectionIndex;
        private final int trackId;
        private final float score;

        private MatchCandidate(int detectionIndex, int trackId, float score) {
            this.detectionIndex = detectionIndex;
            this.trackId = trackId;
            this.score = score;
        }

        private float getScore() { return score; }
    }
}
