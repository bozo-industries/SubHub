package com.subhub.app.overlay;

import com.subhub.app.detection.BBox;
import com.subhub.app.detection.Detection;
import com.subhub.app.detection.PersonBoxDecoder;
import com.subhub.app.detection.RenderSourceReference;
import com.subhub.app.detection.WholePersonGeometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Main-thread render state, never a source of detector, tracker, or penalty observations. */
final class PersonCoveragePresentation {
    static final long MAX_AGE_MS = 750L;
    private static final AtomicLong NEXT_GENERATION = new AtomicLong();
    private long generation;
    private long capturedAt;
    private List<Detection> triggers = Collections.emptyList();
    private List<Detection> cues = Collections.emptyList();
    private List<RenderTrackSnapshot> originals = Collections.emptyList();
    private final Map<Integer, BBox> boxes = new HashMap<>();
    private int width, height, viewportWidth, viewportHeight;
    private long cameraX, cameraY;
    private boolean world;
    private RenderSourceReference reference = RenderSourceReference.UNKNOWN;

    long begin(List<RenderTrackSnapshot> raw, List<Detection> selected, List<Detection> support,
            int sourceWidth, int sourceHeight, long sourceTime, long now,
            boolean worldSpace, long sourceCameraX, long sourceCameraY,
            int displayWidth, int displayHeight, RenderSourceReference sourceReference) {
        clear();
        if (selected == null || selected.size() > 128 || support == null || support.size() > 128
                || raw == null || raw.size() > 256 || sourceWidth <= 0 || sourceHeight <= 0
                || sourceTime < 0 || now < sourceTime || now - sourceTime >= MAX_AGE_MS
                || sourceReference == null || worldSpace && (!sourceReference.isKnown()
                || displayWidth <= 0 || displayHeight <= 0
                || sourceReference.sourceUptimeMillis() != sourceTime)) return generation;
        triggers = new ArrayList<>(selected);
        cues = new ArrayList<>(support);
        originals = new ArrayList<>(raw);
        width = sourceWidth;
        height = sourceHeight;
        capturedAt = sourceTime;
        world = worldSpace;
        cameraX = sourceCameraX;
        cameraY = sourceCameraY;
        viewportWidth = displayWidth;
        viewportHeight = displayHeight;
        reference = sourceReference;
        resolve(Collections.emptyList());
        return generation;
    }

    boolean refine(long token, List<PersonBoxDecoder.Person> people, long now) {
        if (token != generation || triggers.isEmpty() || !fresh(now) || people == null
                || people.size() > 128) return false;
        resolve(people);
        return true;
    }

    private void resolve(List<PersonBoxDecoder.Person> people) {
        boxes.clear();
        List<WholePersonGeometry.Coverage> coverage = new WholePersonGeometry().resolve(
                triggers, cues, people, width, height);
        for (RenderTrackSnapshot raw : originals) {
            if (raw == null || "text_smut".equals(raw.category()) || raw.isCached()) continue;
            if (world && (!raw.reference().sameBasis(reference)
                    || raw.reference().sourceUptimeMillis() != capturedAt)) continue;
            BBox chosen = null;
            int matches = 0;
            for (WholePersonGeometry.Coverage item : coverage) {
                if (!raw.category().equals(item.category)) continue;
                BBox trigger = transform(item.trigger);
                if (trigger.intersectionOverUnion(raw.associationBox()) < .5f) continue;
                // Duplicate/ambiguous trigger associations must not bridge neighboring people.
                chosen = transform(item.person);
                matches++;
            }
            if (matches == 1) boxes.put(raw.sourceId(), chosen);
        }
    }

    BBox expand(RenderTrackSnapshot rendered, BBox current, List<Integer> members, long now) {
        if (boxes.isEmpty() || !fresh(now) || "text_smut".equals(rendered.category())) return current;
        BBox result = current;
        if (members.isEmpty()) return include(result, rendered.sourceId(), rendered, current);
        for (Integer member : members) result = include(result, member, rendered, current);
        return result;
    }

    private BBox include(BBox result, int id, RenderTrackSnapshot rendered, BBox current) {
        BBox person = boxes.get(id);
        if (person == null) return result;
        // Projection prediction translates the expansion with its raw track. World-camera
        // movement is applied later by the renderer and must not be applied here a second time.
        if (!world) person = new BBox(person.getX() + current.getX() - rendered.box().getX(),
                person.getY() + current.getY() - rendered.box().getY(),
                person.getWidth(), person.getHeight());
        return WholePersonGeometry.union(result, person);
    }

    private BBox transform(BBox value) {
        return world ? ContentSpaceCoordinates.toWorld(value, cameraX, cameraY,
                width, height, viewportWidth, viewportHeight) : value;
    }

    private boolean fresh(long now) {
        return now >= capturedAt && now - capturedAt < MAX_AGE_MS;
    }

    void clear() {
        generation = NEXT_GENERATION.incrementAndGet();
        boxes.clear();
        triggers = Collections.emptyList();
        cues = Collections.emptyList();
        originals = Collections.emptyList();
        reference = RenderSourceReference.UNKNOWN;
    }
}
