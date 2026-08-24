package com.subhub.app.penance;

import com.subhub.app.detection.BBox;
import com.subhub.app.detection.TrackedObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Counts one dwell infraction per stationary screen episode after an uninterrupted threshold. */
public final class DwellInfractionTracker {
    private final Map<Integer, State> states = new HashMap<>();
    private long episodeStartedMillis = -1L;
    private boolean episodeCharged;

    public synchronized int update(
            List<TrackedObject> tracks, long nowMillis, long thresholdMillis) {
        return update(tracks, nowMillis, thresholdMillis, true);
    }

    /**
     * @param resetOnTrackedMovement true when tracked motion is the only available scroll signal.
     */
    public synchronized int update(
            List<TrackedObject> tracks,
            long nowMillis,
            long thresholdMillis,
            boolean resetOnTrackedMovement) {
        long threshold = Math.max(1L, thresholdMillis);
        Set<Integer> visible = new HashSet<>();
        boolean moved = false;
        for (TrackedObject track : tracks) {
            if (!track.isActive() || track.getFramesMissing() > 0) continue;
            visible.add(track.getId());
            State state = states.get(track.getId());
            if (state == null) {
                states.put(track.getId(), new State(track.getBox()));
                continue;
            }
            if (movedMeaningfully(state.box, track.getBox())) {
                moved = true;
                states.put(track.getId(), new State(track.getBox()));
                continue;
            }
            state.box = track.getBox();
        }
        states.keySet().removeIf(id -> !visible.contains(id));
        if (resetOnTrackedMovement && moved) {
            episodeStartedMillis = visible.isEmpty() ? -1L : nowMillis;
            episodeCharged = false;
            return 0;
        }
        // A stationary-screen episode belongs to the viewport, not to one detector identity.
        // Video frames can legitimately replace every track while the user remains still.
        if (!visible.isEmpty() && episodeStartedMillis < 0L) episodeStartedMillis = nowMillis;
        if (!visible.isEmpty() && !episodeCharged
                && nowMillis - episodeStartedMillis >= threshold) {
            episodeCharged = true;
            return 1;
        }
        return 0;
    }

    /** A scroll starts a fresh uninterrupted dwell window for every visible item. */
    public synchronized void onScroll() {
        states.clear();
        episodeStartedMillis = -1L;
        episodeCharged = false;
    }

    public synchronized void clear() {
        states.clear();
        episodeStartedMillis = -1L;
        episodeCharged = false;
    }

    private static boolean movedMeaningfully(BBox previous, BBox current) {
        int dx = previous.getCenterX() - current.getCenterX();
        int dy = previous.getCenterY() - current.getCenterY();
        int tolerance = Math.max(18, Math.min(previous.getWidth(), previous.getHeight()) / 3);
        return (long) dx * dx + (long) dy * dy > (long) tolerance * tolerance;
    }

    private static final class State {
        BBox box;

        State(BBox box) {
            this.box = box;
        }
    }
}
