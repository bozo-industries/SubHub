package com.subhub.app.detection;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@RunWith(AndroidJUnit4.class)
public final class DetectionEngineUltraAndroidTest {
    private static final String TAG = "UltraInferenceTest";

    @Test public void warmedRealtimeLaneIsFasterThanSettledQuality() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DetectorConfig qualityConfig = DetectionPreset.ULTRA
                .applyTo(DetectorConfig.builder()).build();
        DetectorConfig fastConfig = qualityConfig.toBuilder()
                .inferenceResolution(320).detectionIntervalMs(0L).build();
        Bitmap qualityFrame = Bitmap.createBitmap(230, 512, Bitmap.Config.ARGB_8888);
        Bitmap fastFrame = Bitmap.createBitmap(144, 320, Bitmap.Config.ARGB_8888);
        new Canvas(qualityFrame).drawColor(Color.rgb(74, 20, 95));
        new Canvas(fastFrame).drawColor(Color.rgb(74, 20, 95));
        try {
            try (DetectionEngine quality = new DetectionEngine(context, qualityConfig, false);
                 DetectionEngine fast = new DetectionEngine(context, fastConfig, true)) {
                quality.initialize();
                fast.initialize();
                long qualityMedian = medianNanos(quality, qualityFrame);
                long fastMedian = medianNanos(fast, fastFrame);
                Log.i(TAG, "quality=" + qualityConfig.getInferenceResolution() + "@"
                        + quality.getActiveProvider() + ':' + qualityMedian / 1_000_000f
                        + " ms(pre=" + quality.getLastPreprocessMs()
                        + ", runtime=" + quality.getLastRuntimeMs() + ')'
                        + " ms, fast=" + fastConfig.getInferenceResolution() + "@"
                        + fast.getActiveProvider() + ':' + fastMedian / 1_000_000f
                        + " ms(pre=" + fast.getLastPreprocessMs()
                        + ", runtime=" + fast.getLastRuntimeMs() + ")");
                assertTrue("320px real-time lane must beat 512px quality refinement",
                        fastMedian < qualityMedian);
            }
        } finally {
            qualityFrame.recycle();
            fastFrame.recycle();
        }
    }

    @Test public void fastLanePreemptsInFlightQualityInsteadOfCompeting() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DetectorConfig qualityConfig = DetectionPreset.ULTRA
                .applyTo(DetectorConfig.builder()).build();
        DetectorConfig fastConfig = qualityConfig.toBuilder()
                .inferenceResolution(320).detectionIntervalMs(0L).build();
        Bitmap qualityFrame = Bitmap.createBitmap(230, 512, Bitmap.Config.ARGB_8888);
        Bitmap fastFrame = Bitmap.createBitmap(144, 320, Bitmap.Config.ARGB_8888);
        new Canvas(qualityFrame).drawColor(Color.rgb(74, 20, 95));
        new Canvas(fastFrame).drawColor(Color.rgb(74, 20, 95));
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            try (DetectionEngine quality = new DetectionEngine(context, qualityConfig, false);
                 DetectionEngine fast = new DetectionEngine(context, fastConfig, true)) {
                quality.initialize();
                fast.initialize();
                quality.detect(qualityFrame, 1080, 2400);
                fast.detect(fastFrame, 1080, 2400);
                long[] unpreemptedFast = new long[15];
                long[] preemptedFast = new long[15];
                long[] cancellation = new long[15];
                Random order = new Random(23L);
                int pairedWins = 0;
                for (int index = 0; index < unpreemptedFast.length; index++) {
                    if (order.nextBoolean()) {
                        preemptedFast[index] = measureFastDuringQuality(
                                workers, quality, fast, qualityFrame, fastFrame,
                                true, cancellation, index);
                        unpreemptedFast[index] = measureFastDuringQuality(
                                workers, quality, fast, qualityFrame, fastFrame,
                                false, cancellation, index);
                    } else {
                        unpreemptedFast[index] = measureFastDuringQuality(
                                workers, quality, fast, qualityFrame, fastFrame,
                                false, cancellation, index);
                        preemptedFast[index] = measureFastDuringQuality(
                                workers, quality, fast, qualityFrame, fastFrame,
                                true, cancellation, index);
                    }
                    if (preemptedFast[index] < unpreemptedFast[index]) pairedWins++;
                }
                Arrays.sort(unpreemptedFast);
                Arrays.sort(preemptedFast);
                Arrays.sort(cancellation);
                long unpreemptedFastMedian = unpreemptedFast[7];
                long preemptedFastMedian = preemptedFast[7];
                long cancellationMedian = cancellation[7];
                Log.i(TAG, "unpreemptedFast=" + unpreemptedFastMedian / 1_000_000f
                        + " ms preemptedFast=" + preemptedFastMedian / 1_000_000f
                        + " ms unpreemptedP95=" + unpreemptedFast[14] / 1_000_000f
                        + " ms preemptedP95=" + preemptedFast[14] / 1_000_000f
                        + " ms cancellation=" + cancellationMedian / 1_000_000f
                        + " ms pairedWins=" + pairedWins + '/' + unpreemptedFast.length
                        + " ms fastProvider=" + fast.getActiveProvider()
                        + " qualityProvider=" + quality.getActiveProvider());
                assertTrue("Preemption must reduce fast-lane contention",
                        preemptedFastMedian < unpreemptedFastMedian);
                assertTrue("Preemption must win a majority of paired trials",
                        pairedWins >= 9);
            }
        } finally {
            workers.shutdownNow();
            qualityFrame.recycle();
            fastFrame.recycle();
        }
    }

    @Test public void staleAdmissionTokenCancelsQualityBeforeNativeExecution() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DetectorConfig qualityConfig = DetectionPreset.ULTRA
                .applyTo(DetectorConfig.builder()).build();
        Bitmap frame = Bitmap.createBitmap(230, 512, Bitmap.Config.ARGB_8888);
        new Canvas(frame).drawColor(Color.rgb(74, 20, 95));
        try (DetectionEngine quality = new DetectionEngine(context, qualityConfig, false)) {
            quality.initialize();
            List<Detection> result = quality.detect(frame, 1080, 2400, () -> true);
            assertTrue(result.isEmpty());
            assertTrue(quality.wasLastRunCancelled());
            assertTrue(!quality.isNativeInferenceRunning());
        } finally {
            frame.recycle();
        }
    }

    @Test public void benchmarksNnapiFp16RelaxationWithoutEnablingItBlindly() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DetectorConfig qualityConfig = DetectionPreset.ULTRA
                .applyTo(DetectorConfig.builder()).build();
        Bitmap qualityFrame = Bitmap.createBitmap(230, 512, Bitmap.Config.ARGB_8888);
        new Canvas(qualityFrame).drawColor(Color.rgb(74, 20, 95));
        try {
            ProviderTiming strict = measureProviderVariant(
                    context, qualityConfig, qualityFrame, false);
            ProviderTiming relaxed = measureProviderVariant(
                    context, qualityConfig, qualityFrame, true);
            Log.i(TAG, "nnapiStrict=" + strict.provider + ':'
                    + strict.medianNanos / 1_000_000f + "ms detections=" + strict.detections
                    + " nnapiFp16=" + relaxed.provider + ':'
                    + relaxed.medianNanos / 1_000_000f + "ms detections=" + relaxed.detections);
            assertTrue(strict.medianNanos > 0L);
            assertTrue(relaxed.medianNanos > 0L);
        } finally {
            qualityFrame.recycle();
        }
    }

    private static long medianNanos(DetectionEngine engine, Bitmap frame) throws Exception {
        long[] timings = new long[5];
        for (int index = 0; index < timings.length; index++) {
            long started = SystemClock.elapsedRealtimeNanos();
            assertNotNull(engine.detect(frame, 1080, 2400));
            timings[index] = SystemClock.elapsedRealtimeNanos() - started;
        }
        Arrays.sort(timings);
        return timings[2];
    }

    private static long measureFastDuringQuality(
            ExecutorService workers,
            DetectionEngine quality,
            DetectionEngine fast,
            Bitmap qualityFrame,
            Bitmap fastFrame,
            boolean preempt,
            long[] cancellations,
            int index) throws Exception {
        Future<List<Detection>> qualityRun = workers.submit(() ->
                quality.detect(qualityFrame, 1080, 2400));
        awaitNativeInference(quality, qualityRun);
        long cancellationStarted = 0L;
        if (preempt) {
            cancellationStarted = SystemClock.elapsedRealtimeNanos();
            assertTrue("An in-flight quality run must accept preemption",
                    quality.cancelActiveInference());
        }
        long fastStarted = SystemClock.elapsedRealtimeNanos();
        fast.detect(fastFrame, 1080, 2400);
        long fastElapsed = SystemClock.elapsedRealtimeNanos() - fastStarted;
        List<Detection> qualityOutput = qualityRun.get();
        if (preempt) {
            cancellations[index] = SystemClock.elapsedRealtimeNanos() - cancellationStarted;
            assertTrue("Cancelled quality output must be reported as cancelled",
                    quality.wasLastRunCancelled());
            assertTrue("Cancelled quality output must never escape the engine",
                    qualityOutput.isEmpty());
            assertTrue("A completed cancellation must close its preemption window",
                    !quality.cancelActiveInference());
        }
        return fastElapsed;
    }

    private static void awaitNativeInference(
            DetectionEngine engine,
            Future<?> run) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 2_000L;
        while (!engine.isNativeInferenceRunning()
                && !run.isDone()
                && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(1L);
        }
        assertTrue("Quality inference must still be running for the contention probe",
                engine.isNativeInferenceRunning());
    }

    private static ProviderTiming measureProviderVariant(
            Context context,
            DetectorConfig config,
            Bitmap frame,
            boolean nnapiFp16) throws Exception {
        try (DetectionEngine engine = new DetectionEngine(
                context, config, false, nnapiFp16)) {
            engine.initializeForProvider("NNAPI");
            List<Detection> detections = engine.detect(frame, 1080, 2400);
            return new ProviderTiming(
                    engine.getActiveProvider(), medianNanos(engine, frame), detections.size());
        }
    }

    private static final class ProviderTiming {
        final String provider;
        final long medianNanos;
        final int detections;

        ProviderTiming(String provider, long medianNanos, int detections) {
            this.provider = provider;
            this.medianNanos = medianNanos;
            this.detections = detections;
        }
    }
}
