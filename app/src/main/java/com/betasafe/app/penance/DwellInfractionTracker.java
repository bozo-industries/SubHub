package com.betasafe.app.penance;

import com.betasafe.app.detection.BBox;
import com.betasafe.app.detection.TrackedObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Counts one dwell infraction per stationary screen episode after an uninterrupted threshold. */
public final class DwellInfractionTracker {
    private final Map<Integer, State> states = new HashMap<>();
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
                states.put(track.getId(), new State(nowMillis, track.getBox()));
                continue;
            }
            if (movedMeaningfully(state.box, track.getBox())) {
                moved = true;
                states.put(track.getId(), new State(nowMillis, track.getBox()));
                continue;
            }
            state.box = track.getBox();
        }
        states.keySet().removeIf(id -> !visible.contains(id));
        if (resetOnTrackedMovement && moved) {
            episodeCharged = false;
            for (Integer id : visible) {
                State state = states.get(id);
                if (state != null) states.put(id, new State(nowMillis, state.box));
            }
            return 0;
        }
        if (episodeCharged) return 0;
        for (Integer id : visible) {
            State state = states.get(id);
            if (state != null && nowMillis - state.visibleSinceMillis >= threshold) {
                episodeCharged = true;
                return 1;
            }
        }
        return 0;
    }

    /** A scroll starts a fresh uninterrupted dwell window for every visible item. */
    public synchronized void onScroll() {
        states.clear();
        episodeCharged = false;
    }

    public synchronized void clear() {
        states.clear();
        episodeCharged = false;
    }

    private static boolean movedMeaningfully(BBox previous, BBox current) {
        int dx = previous.getCenterX() - current.getCenterX();
        int dy = previous.getCenterY() - current.getCenterY();
        int tolerance = Math.max(18, Math.min(previous.getWidth(), previous.getHeight()) / 3);
        return (long) dx * dx + (long) dy * dy > (long) tolerance * tolerance;
    }

    private static final class State {
        final long visibleSinceMillis;
        BBox box;

        State(long visibleSinceMillis, BBox box) {
            this.visibleSinceMillis = visibleSinceMillis;
            this.box = box;
        }
    }
}
