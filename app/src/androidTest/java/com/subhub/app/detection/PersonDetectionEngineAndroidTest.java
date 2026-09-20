package com.subhub.app.detection;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Native-runtime contract smoke test; not a detector-quality or device-performance benchmark. */
@RunWith(AndroidJUnit4.class)
public final class PersonDetectionEngineAndroidTest {
    @Test public void bundledModelRunsRepeatedlyAndHonorsCancellation() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        int[] pixels = new int[208 * 416];
        java.util.Arrays.fill(pixels, 0xff000000);
        SharedModelImage image = SharedModelImage.takeOwnership(pixels, 208, 416);
        try (PersonDetectionEngine engine = new PersonDetectionEngine(context)) {
            assertTrue(engine.detect(image, 200, 400, () -> true).isEmpty());
            for (int pass = 0; pass < 2; pass++) {
                List<PersonBoxDecoder.Person> people = engine.detect(image, 200, 400, () -> false);
                assertNotNull(people);
                for (PersonBoxDecoder.Person person : people) {
                    assertTrue(person.box.getX() >= 0 && person.box.getRight() <= 200);
                    assertTrue(person.box.getY() >= 0 && person.box.getBottom() <= 400);
                }
            }
            assertTrue(engine.detect(image, 200, 400, () -> true).isEmpty());
        }
    }
}
