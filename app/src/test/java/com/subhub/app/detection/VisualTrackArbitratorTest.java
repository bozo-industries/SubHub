package com.subhub.app.detection;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

public final class VisualTrackArbitratorTest {
    @Test
    public void nearIdenticalNewIdentityCannotRenderBesideEstablishedTrack() {
        TrackedObject established = tracked(1, new BBox(100, 200, 180, 180), 6);
        TrackedObject duplicate = tracked(2, new BBox(106, 204, 178, 182), 1);

        VisualTrackArbitrator.Result result = VisualTrackArbitrator.arbitrate(
                List.of(duplicate, established));

        assertEquals(1, result.tracks().size());
        assertEquals(1, result.tracks().get(0).getId());
        assertEquals(1, result.suppressed());
    }

    @Test
    public void nearbyDistinctFacesRemainIndependent() {
        TrackedObject first = tracked(1, new BBox(100, 200, 180, 180), 5);
        TrackedObject second = tracked(2, new BBox(220, 205, 180, 180), 4);

        VisualTrackArbitrator.Result result = VisualTrackArbitrator.arbitrate(
                List.of(first, second));

        assertEquals(2, result.tracks().size());
        assertEquals(0, result.suppressed());
    }

    @Test
    public void nestedDifferentSizedCoverageIsNotMistakenForDuplicate() {
        TrackedObject outer = tracked(1, new BBox(100, 200, 240, 240), 5);
        TrackedObject inner = tracked(2, new BBox(145, 245, 120, 120), 4);

        VisualTrackArbitrator.Result result = VisualTrackArbitrator.arbitrate(
                List.of(outer, inner));

        assertEquals(2, result.tracks().size());
        assertEquals(0, result.suppressed());
    }

    private static TrackedObject tracked(int id, BBox box, int observations) {
        Detection detection = new Detection("FACE_FEMALE", "face", 0.90f,
                box, true, true);
        TrackedObject track = new TrackedObject(id, detection, 0L);
        for (int index = 1; index < observations; index++) {
            track.update(detection, box, 0f, 0f, index * 10_000_000L);
        }
        return track;
    }
}
