package com.betasafe.app.detection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Greedy IoU/distance tracker that keeps censor boxes stable between inference frames. */
public final class ObjectTracker {
    private static final float IOU_THRESHOLD = 0.20f;
    private static final float VELOCITY_SMOOTHING = 0.50f;
    private static final float MAX_VELOCITY = 120f;

    private final Map<Integer, TrackedObject> tracks = new LinkedHashMap<>();
    private DetectorConfig config;
    private int nextId = 1;

    public ObjectTracker(DetectorConfig config) {
        this.config = config;
    }

    public synchronized List<TrackedObject> update(List<Detection> detections) {
        return update(detections, System.nanoTime());
    }

    synchronized List<TrackedObject> update(List<Detection> detections, long nowNanos) {
        List<MatchCandidate> candidates = new ArrayList<>();
        for (int detectionIndex = 0; detectionIndex < detections.size(); detectionIndex++) {
            Detection detection = detections.get(detectionIndex);
            for (TrackedObject track : tracks.values()) {
                if (!track.isActive() || !track.getCategory().equals(detection.getCategory())) continue;
                float score = matchScore(detection.getBox(), track);
                if (score >= IOU_THRESHOLD) {
                    candidates.add(new MatchCandidate(detectionIndex, track.getId(), score));
                }
            }
        }
        candidates.sort(Comparator.comparing(MatchCandidate::getScore).reversed());

        boolean[] matchedDetections = new boolean[detections.size()];
        List<Integer> matchedTracks = new ArrayList<>();
        for (MatchCandidate candidate : candidates) {
            if (matchedDetections[candidate.detectionIndex]
                    || matchedTracks.contains(candidate.trackId)) continue;
            Detection detection = detections.get(candidate.detectionIndex);
            TrackedObject track = tracks.get(candidate.trackId);
            if (track == null) continue;

            float measuredX = clamp(
                    detection.getBox().getCenterX() - track.getBox().getCenterX(),
                    -MAX_VELOCITY,
                    MAX_VELOCITY);
            float measuredY = clamp(
                    detection.getBox().getCenterY() - track.getBox().getCenterY(),
                    -MAX_VELOCITY,
                    MAX_VELOCITY);
            float dx = track.getVelocityX() * (1f - VELOCITY_SMOOTHING)
                    + measuredX * VELOCITY_SMOOTHING;
            float dy = track.getVelocityY() * (1f - VELOCITY_SMOOTHING)
                    + measuredY * VELOCITY_SMOOTHING;
            BBox rendered = smooth(track.getBox(), detection.getBox(), config.getTrackingSmoothing());
            track.update(detection, rendered, dx, dy, nowNanos);
            detection.setTrackId(track.getId());
            matchedDetections[candidate.detectionIndex] = true;
            matchedTracks.add(track.getId());
        }

        for (int index = 0; index < detections.size(); index++) {
            Detection detection = detections.get(index);
            if (!matchedDetections[index]
                    && detection.getConfidence() >= config.getConfidenceThreshold()) {
                int id = nextId++;
                TrackedObject track = new TrackedObject(id, detection, nowNanos);
                tracks.put(id, track);
                detection.setTrackId(id);
            }
        }

        for (TrackedObject track : tracks.values()) {
            if (matchedTracks.contains(track.getId()) || track.getLastSeenNanos() == nowNanos) continue;
            BBox predicted = null;
            if (config.isMotionPrediction() && track.getFramesMissing() < 4) {
                predicted = new BBox(
                        Math.max(0, (int) (track.getBox().getX() + track.getVelocityX())),
                        Math.max(0, (int) (track.getBox().getY() + track.getVelocityY())),
                        track.getBox().getWidth(),
                        track.getBox().getHeight());
            }
            track.miss(predicted);
            float ageSeconds = (nowNanos - track.getLastSeenNanos()) / 1_000_000_000f;
            if (track.getFramesMissing() >= config.getMinRemoveFrames()
                    && ageSeconds > config.getTrackMaxAgeSeconds()) {
                track.deactivate();
            }
        }
        return activeTracks();
    }

    public synchronized List<TrackedObject> activeTracks() {
        List<TrackedObject> active = new ArrayList<>();
        for (TrackedObject track : tracks.values()) {
            if (track.isActive()) active.add(track);
        }
        return Collections.unmodifiableList(active);
    }

    public synchronized void clear() {
        tracks.clear();
        nextId = 1;
    }

    public synchronized void setConfig(DetectorConfig config) {
        this.config = config;
    }

    private static float matchScore(BBox detection, TrackedObject track) {
        float iou = detection.intersectionOverUnion(track.getBox());
        BBox predicted = new BBox(
                Math.max(0, (int) (track.getBox().getX() + track.getVelocityX())),
                Math.max(0, (int) (track.getBox().getY() + track.getVelocityY())),
                track.getBox().getWidth(),
                track.getBox().getHeight());
        iou = Math.max(iou, detection.intersectionOverUnion(predicted));
        float dx = detection.getCenterX() - track.getBox().getCenterX();
        float dy = detection.getCenterY() - track.getBox().getCenterY();
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float limit = Math.min(
                Math.max(
                        Math.max(detection.getWidth(), detection.getHeight()),
                        Math.max(track.getBox().getWidth(), track.getBox().getHeight())) * 2f,
                200f);
        if (distance < limit) iou = Math.max(iou, (1f - distance / limit) * 0.5f);
        return iou;
    }

    private static BBox smooth(BBox current, BBox target, float alpha) {
        return new BBox(
                (int) (current.getX() + (target.getX() - current.getX()) * alpha),
                (int) (current.getY() + (target.getY() - current.getY()) * alpha),
                (int) (current.getWidth() + (target.getWidth() - current.getWidth()) * alpha),
                (int) (current.getHeight() + (target.getHeight() - current.getHeight()) * alpha));
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
