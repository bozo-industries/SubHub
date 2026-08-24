package com.subhub.app.detection;

import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public final class DetectionEngineUltraAndroidTest {
    private static final String TAG = "UltraInferenceTest";

    @Test public void warmedUltraInferenceReportsMedian() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DetectorConfig config = DetectionPreset.ULTRA
                .applyTo(DetectorConfig.builder()).build();
        Bitmap frame = Bitmap.createBitmap(230, 512, Bitmap.Config.ARGB_8888);
        new Canvas(frame).drawColor(Color.rgb(74, 20, 95));
        long[] timings = new long[5];
        try {
            try (DetectionEngine engine = new DetectionEngine(context, config)) {
                engine.initialize();
                for (int index = 0; index < timings.length; index++) {
                    long started = SystemClock.elapsedRealtimeNanos();
                    assertNotNull(engine.detect(frame, 1080, 2400));
                    timings[index] = SystemClock.elapsedRealtimeNanos() - started;
                }
                Arrays.sort(timings);
                Log.i(TAG, "resolution=" + config.getInferenceResolution()
                        + ", provider=" + engine.getActiveProvider()
                        + ", median=" + timings[2] / 1_000_000f + " ms"
                        + ", engine=" + engine.getLastInferenceMs() + " ms");
            }
        } finally {
            frame.recycle();
        }
    }
}
