package com.betasafe.app.penance;

import com.betasafe.app.detection.BBox;
import com.betasafe.app.detection.TrackedObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Counts one dwell infraction per stable censor track after an uninterrupted threshold. */
public final class DwellInfractionTracker {
    private final Map<Integer, State> states = new HashMap<>();

    public synchronized int update(
            List<TrackedObject> tracks, long nowMillis, long thresholdMillis) {
        long threshold = Math.max(1L, thresholdMillis);
        Set<Integer> visible = new HashSet<>();
        int crossed = 0;
        for (TrackedObject track : tracks) {
            if (!track.isActive() || track.getFramesMissing() > 0) continue;
            visible.add(track.getId());
            State state = states.get(track.getId());
            if (state == null || movedMeaningfully(state.box, track.getBox())) {
                states.put(track.getId(), new State(nowMillis, track.getBox()));
                continue;
            }
            state.box = track.getBox();
            if (!state.charged && nowMillis - state.visibleSinceMillis >= threshold) {
                state.charged = true;
                crossed++;
            }
        }
        states.keySet().removeIf(id -> !visible.contains(id));
        return crossed;
    }

    /** A scroll starts a fresh uninterrupted dwell window for every visible item. */
    public synchronized void onScroll() {
        states.clear();
    }

    public synchronized void clear() {
        states.clear();
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
        boolean charged;

        State(long visibleSinceMillis, BBox box) {
            this.visibleSinceMillis = visibleSinceMillis;
            this.box = box;
        }
    }
}
