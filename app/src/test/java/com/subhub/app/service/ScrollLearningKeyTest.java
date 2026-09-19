package com.subhub.app.service;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ScrollLearningKeyTest {
    @Test public void equivalentKeysMatchButEveryRelevantContextSeparatesProfiles() {
        ScrollLearningKey baseline = ScrollCalibrationLearnerTest.key();
        assertEquals(baseline, ScrollCalibrationLearnerTest.key());
        assertEquals(baseline.hashCode(), ScrollCalibrationLearnerTest.key().hashCode());
        for (int change = 0; change < 10; change++) {
            ScrollLearningKey other = ScrollCalibrationLearnerTest.key(
                    change == 0 ? "com.example.chat" : "com.example.reader", change == 1 ? 2 : 1,
                    (change == 2 ? "1" : "0").repeat(64), change == 3 ? 1200 : 1344,
                    change == 4 ? 2400 : 2992, change == 5 ? 420 : 480, change == 6 ? 1 : 0,
                    change == 7 ? 60000 : 120000,
                    change == 8 ? ScrollLearningKey.Axis.X : ScrollLearningKey.Axis.Y,
                    change == 9 ? ScrollLearningKey.Evidence.INDEXED : ScrollLearningKey.Evidence.EXPLICIT);
            assertNotEquals("dimension " + change, baseline, other);
        }
    }

    @Test public void rawPageTextOrRuntimeWindowNameIsNotADurableSurfaceKey() {
        for (String value : new String[]{"page text", "window:23", "https://example.org", "", "ABC"}) {
            assertThrows(IllegalArgumentException.class, () -> ScrollCalibrationLearnerTest.key(
                    "com.example.reader", 1, value, 1344, 2992, 480, 0, 120000,
                    ScrollLearningKey.Axis.Y, ScrollLearningKey.Evidence.EXPLICIT));
        }
    }
}
