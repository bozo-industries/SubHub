package com.subhub.app.overlay;

import com.subhub.app.detection.BBox;
import com.subhub.app.detection.RenderSourceReference;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public final class StableVisualLayoutTest {
    @Test public void touchingPaddedTorsoFootprintsMergeEvenWhenRawBoxesAreSeparate() {
        StableVisualLayout layout = new StableVisualLayout();
        List<RenderTrackSnapshot> result = layout.update(List.of(
                box(1, "face", 10, -100, 80, 80),
                box(2, "breasts", 0, 0, 100, 100),
                box(3, "belly", 0, 110, 100, 100)), .2f, 100);
        assertEquals(2, result.size());
        RenderTrackSnapshot body = result.get(1);
        assertEquals(new BBox(-20, -20, 140, 250), body.box());
        assertTrue(body.paddingApplied());
        assertEquals(body.box(), body.withPadding(.2f).box());
        assertEquals(List.of(2, 3), layout.memberIds(body.id()));
    }

    @Test public void paddingDoesNotGrowAgainAfterUnion() {
        StableVisualLayout layout = new StableVisualLayout();
        List<RenderTrackSnapshot> result = layout.update(List.of(
                box(1, "breasts", 100, 100, 100, 100),
                box(2, "belly", 100, 160, 100, 100)), .2f, 100);
        assertEquals(1, result.size());
        assertEquals(new BBox(80, 80, 140, 200), result.get(0).box());
    }

    @Test public void stableGeometryIgnoresRepeatedSmallDetectorJitter() {
        StableVisualLayout layout = new StableVisualLayout();
        RenderTrackSnapshot initial = layout.update(List.of(box(1, "face", 100, 100, 100, 100)), .2f, 100).get(0);
        for (int step = 1; step < 20; step++) {
            int jitter = step % 3 - 1;
            RenderTrackSnapshot next = layout.update(List.of(box(1, "face",
                    100 + jitter, 100 - jitter, 100 + jitter, 100)), .2f, 100 + step * 100).get(0);
            assertEquals(initial.id(), next.id());
            assertEquals(initial.box(), next.box());
            assertTrue(layout.frozenGeometry() > 0);
        }
    }

    @Test public void transientTinySplitKeepsGroupButPersistentSplitEventuallySeparates() {
        StableVisualLayout layout = new StableVisualLayout();
        List<RenderTrackSnapshot> joined = List.of(box(1, "breasts", 0, 0, 100, 100),
                box(2, "belly", 0, 70, 100, 100));
        int id = layout.update(joined, 0f, 100).get(0).id();
        List<RenderTrackSnapshot> split = List.of(box(1, "breasts", 0, 0, 100, 100),
                box(2, "belly", 0, 102, 100, 100));
        List<RenderTrackSnapshot> transientResult = layout.update(split, 0f, 200);
        assertEquals(1, transientResult.size());
        assertEquals(id, transientResult.get(0).id());
        assertEquals(1, layout.heldGroups());
        assertEquals(1, layout.update(split, 0f, 600).size());
        assertEquals(2, layout.update(split, 0f, 701).size());
    }

    @Test public void realGapIsNeverBridgedByHysteresis() {
        StableVisualLayout layout = new StableVisualLayout();
        layout.update(List.of(box(1, "breasts", 0, 0, 100, 100),
                box(2, "belly", 0, 70, 100, 100)), 0f, 100);
        assertEquals(2, layout.update(List.of(box(1, "breasts", 0, 0, 100, 100),
                box(2, "belly", 0, 200, 100, 100)), 0f, 200).size());
    }

    @Test public void groupNeverDrawsACorridorToOldSmoothedLocation() {
        RenderTrackSnapshot group = box(1, "breasts", 100, 100, 100, 200)
                .withGroupBox(new BBox(100, 100, 100, 200));
        assertEquals(group.box(), group.preserveGroupCoverage(new BBox(100, 1200, 100, 200)));
    }

    @Test public void settledSingletonDoesNotAnimateEverySmallGeometryCorrection() {
        StableVisualLayout layout = new StableVisualLayout();
        RenderTrackSnapshot rendered = layout.update(List.of(box(1, "face", 100, 100, 100, 100)), .2f, 100).get(0);
        assertEquals(rendered.box(), rendered.preserveGroupCoverage(new BBox(81, 82, 135, 138)));
    }

    @Test public void changedSourceBasisDoesNotFreezeOldGeometry() {
        StableVisualLayout layout = new StableVisualLayout();
        RenderSourceReference.Origin origin = new RenderSourceReference.Origin(1, 1, 1, 1000, 2000, 1);
        RenderTrackSnapshot first = new RenderTrackSnapshot(1, "face", new BBox(100, 100, 100, 100),
                0, 0, false, RenderSourceReference.known(origin, 10, 0, 0));
        layout.update(List.of(first), .2f, 100);
        RenderTrackSnapshot second = new RenderTrackSnapshot(1, "face", new BBox(102, 100, 100, 100),
                0, 0, false, RenderSourceReference.known(origin, 20, 0, 5));
        assertEquals(new BBox(82, 80, 140, 140), layout.update(List.of(second), .2f, 200).get(0).box());
    }

    @Test public void newlyAddedRawCoverageIsNeverFrozenOutsideExistingMask() {
        StableVisualLayout layout = new StableVisualLayout();
        layout.update(List.of(box(1, "face", 100, 100, 100, 100)), 0f, 100);
        assertEquals(new BBox(100, 100, 102, 100),
                layout.update(List.of(box(1, "face", 100, 100, 102, 100)), 0f, 200).get(0).box());
    }

    @Test public void manyDuplicateCopiesDoNotExhaustBodyGroupCapacity() {
        StableVisualLayout layout = new StableVisualLayout();
        List<RenderTrackSnapshot> items = new ArrayList<>();
        for (int id = 1; id <= 12; id++) items.add(box(id, "breasts", 100, 100, 100, 100));
        items.add(box(13, "belly", 100, 160, 100, 100));
        assertEquals(1, layout.update(items, .2f, 100).size());
    }

    @Test public void separateImageHeadsPreventColumnSpanningGroup() {
        StableVisualLayout layout = new StableVisualLayout();
        List<RenderTrackSnapshot> items = List.of(
                box(1, "face", 20, 0, 80, 80),
                box(2, "breasts", 0, 90, 120, 100),
                box(3, "face", 20, 200, 80, 80),
                box(4, "breasts", 0, 290, 120, 100),
                box(5, "face", 20, 400, 80, 80),
                box(6, "breasts", 0, 490, 120, 100));
        for (RenderTrackSnapshot item : layout.update(items, .2f, 100)) {
            assertTrue(item.box().getHeight() < 280);
        }
    }

    @Test public void lowerCardTorsoDoesNotAssociateWithLargerDistantUpperHead() {
        // Numeric source geometry from the active Pass 91 Pixel dump. The distant,
        // larger head used to narrowly win a head-size-normalized distance score.
        List<RenderTrackSnapshot> items = List.of(
                box(1, "face", 842, 1179, 272, 238),
                box(9, "face", 966, 2125, 176, 173),
                box(7, "breasts_covered", 867, 2347, 217, 224),
                new RenderTrackSnapshot(524626693, "breasts_covered",
                        new BBox(867, 2347, 217, 224), 0, 0, true),
                box(4, "breasts_covered", 994, 2395, 207, 207),
                box(6, "belly", 914, 2623, 239, 166));
        StableVisualLayout layout = new StableVisualLayout();
        List<RenderTrackSnapshot> result = layout.update(items, .14f, 100);
        assertEquals("two distinct heads and one lower-card body", 3, result.size());
        RenderTrackSnapshot body = result.stream()
                .filter(item -> "breasts_covered".equals(item.category())).findFirst().get();
        assertEquals(4, layout.memberIds(body.id()).size());
        for (RenderTrackSnapshot item : items.subList(2, items.size())) {
            BBox raw = item.box();
            assertTrue(body.box().getX() <= raw.getX());
            assertTrue(body.box().getY() <= raw.getY());
            assertTrue(body.box().getRight() >= raw.getRight());
            assertTrue(body.box().getBottom() >= raw.getBottom());
        }
    }

    @Test public void headRankingIsStableAcrossScaleAndInputOrder() {
        for (float scale : new float[]{.5f, 1f, 2f}) {
            List<RenderTrackSnapshot> items = new ArrayList<>();
            for (RenderTrackSnapshot raw : List.of(
                    box(1, "face", 842, 1179, 272, 238),
                    box(9, "face", 966, 2125, 176, 173),
                    box(7, "breasts_covered", 867, 2347, 217, 224),
                    box(4, "breasts_covered", 994, 2395, 207, 207))) {
                BBox b = raw.box();
                items.add(box(raw.id(), raw.category(), Math.round(b.getX() * scale),
                        Math.round(b.getY() * scale), Math.round(b.getWidth() * scale),
                        Math.round(b.getHeight() * scale)));
            }
            for (int order = 0; order < 2; order++) {
                StableVisualLayout layout = new StableVisualLayout();
                List<RenderTrackSnapshot> result = layout.update(items, .14f, 100);
                assertEquals(3, result.size());
                RenderTrackSnapshot body = result.get(2);
                assertTrue(layout.memberIds(body.id()).containsAll(List.of(4, 7)));
                java.util.Collections.reverse(items);
            }
        }
    }

    @Test public void layoutIdsStayUniqueEvenIfInputsReuseAnId() {
        StableVisualLayout layout = new StableVisualLayout();
        List<RenderTrackSnapshot> result = layout.update(List.of(
                box(1, "face", 0, 0, 80, 80), box(1, "face", 400, 400, 80, 80)), .2f, 100);
        assertNotEquals(result.get(0).id(), result.get(1).id());
        assertEquals(1, result.get(0).sourceId());
        assertEquals(1, result.get(1).sourceId());
    }

    @Test public void clearDiscardsPreviousGroupAndGeometryMemory() {
        StableVisualLayout layout = new StableVisualLayout();
        layout.update(List.of(box(1, "face", 100, 100, 100, 100)), .2f, 100);
        layout.clear();
        assertEquals(new BBox(81, 80, 140, 140),
                layout.update(List.of(box(1, "face", 101, 100, 100, 100)), .2f, 200).get(0).box());
    }

    static RenderTrackSnapshot box(int id, String category, int x, int y, int w, int h) {
        return new RenderTrackSnapshot(id, category, new BBox(x, y, w, h), 0, 0, false);
    }
}
