package com.subhub.app.detection;

import static org.junit.Assert.*;

import java.nio.FloatBuffer;
import java.util.List;
import org.junit.Test;

public final class PersonBoxDecoderTest {
    private final FloatBuffer[] scores = new FloatBuffer[3];
    private final FloatBuffer[] distances = new FloatBuffer[3];

    public PersonBoxDecoderTest() {
        for (int level = 0; level < 3; level++) {
            int side = 416 / PersonBoxDecoder.STRIDES[level];
            scores[level] = FloatBuffer.allocate(side * side * 80);
            distances[level] = FloatBuffer.allocate(side * side * 32);
        }
    }

    @Test public void centeredLetterboxMapsPortraitAndLandscapeWithoutStretching() {
        PersonBoxDecoder.Letterbox portrait = new PersonBoxDecoder.Letterbox(200, 400);
        assertEquals(new BBox(0, 0, 200, 400), portrait.toSource(104, 0, 312, 416));
        PersonBoxDecoder.Letterbox landscape = new PersonBoxDecoder.Letterbox(400, 200);
        assertEquals(new BBox(0, 0, 400, 200), landscape.toSource(0, 104, 416, 312));
    }

    @Test public void uniformDflProducesExpectedAnchorBoxWithoutMovingBufferPositions() {
        int anchor = 26 * 52 + 26;
        scores[0].put(anchor * 80, .9f);
        List<PersonBoxDecoder.Person> people = decode();
        assertEquals(1, people.size());
        assertEquals(new BBox(183, 183, 57, 57), people.get(0).box);
        assertEquals(0, scores[0].position());
        assertEquals(0, distances[0].position());
    }

    @Test public void skipsNonPersonWinnerAndNonfiniteDistance() {
        scores[0].put(0, .8f);
        scores[0].put(1, .9f);
        scores[0].put(80, .9f);
        distances[0].put(32, Float.NaN);
        assertTrue(decode().isEmpty());
    }

    @Test public void stableSoftmaxHandlesLargeLogitsAndNmsSuppressesDuplicates() {
        for (int anchor : new int[]{26 * 52 + 26, 26 * 52 + 27}) {
            scores[0].put(anchor * 80, .9f);
            for (int offset = 0; offset < 32; offset++) {
                distances[0].put(anchor * 32 + offset, 10_000f);
            }
        }
        assertEquals(1, decode().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMalformedContractInsteadOfReportingNoPeople() {
        scores[0] = FloatBuffer.allocate(1);
        decode();
    }

    private List<PersonBoxDecoder.Person> decode() {
        return new PersonBoxDecoder().decode(scores, distances,
                new PersonBoxDecoder.Letterbox(416, 416), .35f);
    }
}
