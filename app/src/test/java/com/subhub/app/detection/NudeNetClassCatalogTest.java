package com.subhub.app.detection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class NudeNetClassCatalogTest {
    @Test
    public void modelOutputOrderMapsEveryClassToMetadata() {
        List<String> expected = Arrays.asList(
                "FEMALE_GENITALIA_COVERED",
                "FACE_FEMALE",
                "BUTTOCKS_EXPOSED",
                "FEMALE_BREAST_EXPOSED",
                "FEMALE_GENITALIA_EXPOSED",
                "MALE_BREAST_EXPOSED",
                "ANUS_EXPOSED",
                "FEET_EXPOSED",
                "BELLY_COVERED",
                "FEET_COVERED",
                "ARMPITS_COVERED",
                "ARMPITS_EXPOSED",
                "FACE_MALE",
                "BELLY_EXPOSED",
                "MALE_GENITALIA_EXPOSED",
                "ANUS_COVERED",
                "FEMALE_BREAST_COVERED",
                "BUTTOCKS_COVERED");

        assertEquals(NudeNetClassCatalog.CLASS_COUNT, expected.size());
        for (int index = 0; index < expected.size(); index++) {
            assertEquals(expected.get(index), NudeNetClassCatalog.nameByIndex(index));
            assertNotNull(NudeNetClassCatalog.byIndex(index));
        }
    }
}
