package com.subhub.app.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class FastPriorityInferenceGateTest {
    @Test public void qualityNeedsEnoughDeadlineSlack() {
        FastPriorityInferenceGate gate = new FastPriorityInferenceGate();

        FastPriorityInferenceGate.QualityAdmission rejected =
                gate.tryAcquireQuality(199L, 200L);

        assertFalse(rejected.admitted());
        assertEquals(FastPriorityInferenceGate.QualityRejection.INSUFFICIENT_SLACK,
                rejected.rejection());
        assertEquals(FastPriorityInferenceGate.Lane.IDLE, gate.activeLane());
    }

    @Test public void announcedFastDemandPreventsQualityFromBarging() {
        FastPriorityInferenceGate gate = new FastPriorityInferenceGate();
        FastPriorityInferenceGate.FastDemand demand = gate.registerFastDemand();

        FastPriorityInferenceGate.QualityAdmission rejected =
                gate.tryAcquireQuality(500L, 200L);

        assertFalse(rejected.admitted());
        assertEquals(FastPriorityInferenceGate.QualityRejection.FAST_DEMAND,
                rejected.rejection());
        demand.close();
        assertFalse(gate.hasFastDemand());
    }

    @Test public void fastAndQualityLeasesNeverOverlap() throws Exception {
        FastPriorityInferenceGate gate = new FastPriorityInferenceGate();
        FastPriorityInferenceGate.QualityAdmission quality =
                gate.tryAcquireQuality(500L, 200L);
        assertTrue(quality.admitted());
        FastPriorityInferenceGate.FastDemand demand = gate.registerFastDemand();
        CountDownLatch fastStarted = new CountDownLatch(1);
        CountDownLatch releaseFast = new CountDownLatch(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<Long> fast = worker.submit(() -> {
                try (FastPriorityInferenceGate.Lease lease = demand.acquire()) {
                    fastStarted.countDown();
                    releaseFast.await(1L, TimeUnit.SECONDS);
                    return lease.waitMs();
                }
            });

            assertFalse(fastStarted.await(40L, TimeUnit.MILLISECONDS));
            assertEquals(FastPriorityInferenceGate.Lane.QUALITY, gate.activeLane());
            quality.lease().close();

            assertTrue(fastStarted.await(1L, TimeUnit.SECONDS));
            assertEquals(FastPriorityInferenceGate.Lane.FAST, gate.activeLane());
            releaseFast.countDown();
            assertTrue(fast.get(1L, TimeUnit.SECONDS) >= 20L);
            assertEquals(FastPriorityInferenceGate.Lane.IDLE, gate.activeLane());
        } finally {
            worker.shutdownNow();
        }
    }
}
