package com.subhub.app.pack;

import static org.junit.Assert.*;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Test;

/** Stable packs must not enable a detector capability that is intentionally dev-only. */
public final class StableCensorPackBoundaryTest {
    @Test public void wholePersonImportIsRejectedInsteadOfSilentlyAccepted() throws Exception {
        JSONObject values = new JSONObject().put("censor_coverage", "whole_person");
        try {
            SubHubPackSchema.sanitizeSection(SubHubPackSchema.CENSOR, values);
            fail("Whole-person coverage is not available in master");
        } catch (PackSettingCatalog.ValidationException expected) {
            assertEquals("censor_coverage", expected.key);
        }
    }

    @Test public void capturingAStaleDevPreferenceExportsActualStableCoverage() throws Exception {
        JSONObject values = SubHubPackSchema.captureMainSection(SubHubPackSchema.CENSOR,
                preferences(Map.of("censor_coverage", "whole_person")));
        assertEquals("detected_areas", values.getString("censor_coverage"));
        assertEquals("detected_areas", PackSettingCatalog.defaults(
                SubHubPackSchema.CENSOR).getString("censor_coverage"));
    }

    private static android.content.SharedPreferences preferences(Map<String, ?> values) {
        return (android.content.SharedPreferences) java.lang.reflect.Proxy.newProxyInstance(
                StableCensorPackBoundaryTest.class.getClassLoader(),
                new Class<?>[]{android.content.SharedPreferences.class},
                (proxy, method, args) -> {
                    if ("getAll".equals(method.getName())) return values;
                    throw new AssertionError("Unexpected preference operation: " + method.getName());
                });
    }
}
