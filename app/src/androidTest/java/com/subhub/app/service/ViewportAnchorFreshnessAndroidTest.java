package com.subhub.app.service;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Opt-in shadow probe for whether retained Accessibility nodes expose fresh viewport geometry.
 *
 * <p>The harness prepares the target app and injects gestures externally. This test never launches
 * an app, changes SubHub settings, dispatches input, or feeds the production censor pipeline. A
 * positive result is diagnostic evidence only and does not select a production implementation.</p>
 */
@RunWith(AndroidJUnit4.class)
@SuppressWarnings("deprecation") // Every AccessibilityNodeInfo obtained here is explicitly owned.
public final class ViewportAnchorFreshnessAndroidTest {
    private static final String TAG = "AnchorFreshness";
    private static final String ENABLE_ARGUMENT = "enableAnchorFreshnessProbe";
    private static final String TARGET_PACKAGE_ARGUMENT = "anchorFreshnessTargetPackage";
    private static final String DEFAULT_TARGET_PACKAGE = "com.android.chrome";

    private static final int MAX_TREE_NODES = 192;
    private static final int MAX_TREE_DEPTH = 32;
    private static final int MIN_ANCHORS = 3;
    private static final int MAX_ANCHORS = 5;
    private static final int TOP_EXCLUSION_PERCENT = 28;
    private static final int MAX_ANCHOR_AREA_PERCENT = 20;
    private static final int MOVEMENT_THRESHOLD_PX = 2;
    private static final int DELTA_INLIER_TOLERANCE_PX = 4;
    private static final long MAX_READ_COST_MS = 8L;
    private static final long SAMPLE_DELAY_MS = 16L;
    private static final long ROOT_READY_TIMEOUT_MS = 2_000L;
    private static final long ROOT_RETRY_DELAY_MS = 50L;
    private static final long SETUP_TRAVERSAL_CAP_MS = 2_000L;
    private static final long SESSION_CAP_MS = 20_000L;
    private static final int MAX_PENDING_PAIR_TIMESTAMPS =
            (int) (SESSION_CAP_MS / SAMPLE_DELAY_MS) + 2;

    private static final int REASON_SUCCESS = 0;
    private static final int REASON_ROOT_UNAVAILABLE = 1;
    private static final int REASON_PACKAGE_CHANGED = 2;
    private static final int REASON_WINDOW_CHANGED = 3;
    private static final int REASON_INSUFFICIENT_ANCHORS = 4;
    private static final int REASON_REFRESH_FAILED = 5;
    private static final int REASON_SLOW_READ = 6;
    private static final int REASON_BASELINE_UNSTABLE = 7;
    private static final int REASON_INSUFFICIENT_SAMPLES = 8;
    private static final int REASON_INTERRUPTED = 9;
    private static final int REASON_SESSION_CAP = 10;
    private static final int REASON_SETUP_TRAVERSAL_CAP = 11;

    @Test
    public void retainedLeafAnchorsRefreshBetweenScrollEvents() throws Exception {
        Bundle arguments = InstrumentationRegistry.getArguments();
        boolean enabled = "true".equalsIgnoreCase(arguments.getString(ENABLE_ARGUMENT, ""));
        Assume.assumeTrue(
                "Viewport anchor freshness probe is opt-in; pass -e "
                        + ENABLE_ARGUMENT + " true",
                enabled);

        String targetPackage = arguments.getString(
                TARGET_PACKAGE_ARGUMENT, DEFAULT_TARGET_PACKAGE).trim();
        Assume.assumeTrue("Viewport anchor freshness target package is empty",
                !targetPackage.isEmpty());

        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        UiAutomation automation = instrumentation.getUiAutomation(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        assertNotNull(automation);

        long session = Math.max(1L, SystemClock.elapsedRealtimeNanos());
        AtomicInteger expectedWindow = new AtomicInteger(-1);
        AtomicInteger stopReason = new AtomicInteger(REASON_SUCCESS);
        AtomicLong eventSequence = new AtomicLong();
        AtomicReference<NumericScrollEvent> latestEvent = new AtomicReference<>();
        ArrayBlockingQueue<Long> eventSignal = new ArrayBlockingQueue<>(1);

        UiAutomation.OnAccessibilityEventListener listener = event -> {
            if (event == null) return;
            int windowId = expectedWindow.get();
            int type = event.getEventType();
            boolean targetEvent = packageMatches(event.getPackageName(), targetPackage);
            if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
                if (windowId >= 0 && (!targetEvent || event.getWindowId() != windowId)) {
                    stopReason.compareAndSet(REASON_SUCCESS,
                            targetEvent ? REASON_WINDOW_CHANGED : REASON_PACKAGE_CHANGED);
                    replaceSignal(eventSignal, eventSequence.get());
                    return;
                }
                if (!targetEvent) return;
                long sequence = eventSequence.incrementAndGet();
                long received = SystemClock.uptimeMillis();
                latestEvent.set(NumericScrollEvent.from(event, sequence, received));
                replaceSignal(eventSignal, sequence);
            } else if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && windowId >= 0
                    && (!targetEvent || event.getWindowId() != windowId)) {
                stopReason.compareAndSet(REASON_SUCCESS,
                        targetEvent ? REASON_WINDOW_CHANGED : REASON_PACKAGE_CHANGED);
                replaceSignal(eventSignal, eventSequence.get());
            }
        };

        int threadId = Process.myTid();
        int originalPriority = Process.getThreadPriority(threadId);
        AccessibilityServiceInfo originalServiceInfo = automation.getServiceInfo();
        int originalServiceFlags = originalServiceInfo == null
                ? -1 : originalServiceInfo.flags;
        int originalCapabilities = originalServiceInfo == null
                ? -1 : originalServiceInfo.getCapabilities();
        int requestedServiceFlags = originalServiceFlags < 0
                ? -1 : originalServiceFlags
                        | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        boolean serviceInfoUpdateAttempted = originalServiceInfo != null
                && requestedServiceFlags != originalServiceFlags;
        ProbeResult result;
        try {
            Process.setThreadPriority(threadId, Process.THREAD_PRIORITY_BACKGROUND);
            if (serviceInfoUpdateAttempted) {
                originalServiceInfo.flags = requestedServiceFlags;
                automation.setServiceInfo(originalServiceInfo);
            }
            AccessibilityServiceInfo activeServiceInfo = automation.getServiceInfo();
            Log.i(TAG, "ANCHOR_SHADOW_CONNECTION session=" + session
                    + " originalFlags=" + originalServiceFlags
                    + " originalCapabilities=" + originalCapabilities
                    + " requestedFlags=" + requestedServiceFlags
                    + " activeFlags=" + (activeServiceInfo == null
                            ? -1 : activeServiceInfo.flags)
                    + " activeCapabilities=" + (activeServiceInfo == null
                            ? -1 : activeServiceInfo.getCapabilities())
                    + " updateAttempted=" + (serviceInfoUpdateAttempted ? 1 : 0));
            automation.setOnAccessibilityEventListener(listener);
            result = runProbe(
                    automation, targetPackage, session, expectedWindow, stopReason,
                    eventSequence, latestEvent, eventSignal);
        } finally {
            try {
                automation.setOnAccessibilityEventListener(null);
            } finally {
                try {
                    if (serviceInfoUpdateAttempted) {
                        originalServiceInfo.flags = originalServiceFlags;
                        automation.setServiceInfo(originalServiceInfo);
                    }
                } finally {
                    Process.setThreadPriority(threadId, originalPriority);
                }
            }
        }

        Log.i(TAG, "ANCHOR_SHADOW_SUMMARY session=" + session
                + " window=" + result.windowId
                + " result=" + (result.reason == REASON_SUCCESS ? 1 : 0)
                + " reason=" + result.reason
                + " anchors=" + result.anchorCount
                + " events=" + result.eventCount
                + " samples=" + result.sampleCount
                + " receiptTimeBetweenEventMovingPairs="
                        + result.receiptTimeBetweenEventMovingPairs
                + " sourceTimeBetweenEventMovingPairs="
                        + result.sourceTimeBetweenEventMovingPairs
                + " sourceTimeUnconfirmedMovingPairs="
                        + result.sourceTimeUnconfirmedMovingPairs
                + " pendingTimestampDrops=" + result.pendingTimestampDrops
                + " eventCoupledPairs=" + result.eventCoupledPairs
                + " maxDeltaInliers=" + result.maxDeltaInliers
                + " readCostP50Ms=" + result.readCostP50Ms
                + " readCostP95Ms=" + result.readCostP95Ms
                + " maxReadCostMs=" + result.maxReadCostMs);

        if (result.reason != REASON_SUCCESS) {
            fail("diagnostic inconclusive reason=" + result.reason);
        }
    }

    private static ProbeResult runProbe(
            UiAutomation automation,
            String targetPackage,
            long session,
            AtomicInteger expectedWindow,
            AtomicInteger stopReason,
            AtomicLong eventSequence,
            AtomicReference<NumericScrollEvent> latestEvent,
            ArrayBlockingQueue<Long> eventSignal) {
        long deadline = SystemClock.uptimeMillis() + SESSION_CAP_MS;
        List<Anchor> anchors = Collections.emptyList();
        List<Long> readCosts = new ArrayList<>();
        int windowId = -1;
        int sampleCount = 0;
        EvidenceCounts evidence = new EvidenceCounts();
        int eventCoupledPairs = 0;
        int maxDeltaInliers = 0;
        try {
            RootLookup rootLookup = awaitTargetRoot(automation, targetPackage, deadline);
            AccessibilityNodeInfo root = rootLookup.root;
            if (root == null) {
                Log.i(TAG, "ANCHOR_SHADOW_SETUP session=" + session
                        + " window=-1"
                        + " result=0"
                        + " reason=" + rootLookup.reason
                        + " readStart=" + rootLookup.startedUptimeMs
                        + " readEnd=" + rootLookup.endedUptimeMs
                        + " costMs=" + Math.max(0L,
                                rootLookup.endedUptimeMs - rootLookup.startedUptimeMs)
                        + " rootAttempts=" + rootLookup.attempts
                        + " rootNulls=" + rootLookup.nullResults
                        + " packageMismatches=" + rootLookup.packageMismatches);
                return ProbeResult.of(rootLookup.reason, windowId, 0,
                        eventSequence.get(), sampleCount, evidence,
                        eventCoupledPairs, maxDeltaInliers, readCosts);
            }
            windowId = root.getWindowId();
            expectedWindow.set(windowId);
            long traversalDeadline = Math.min(
                    deadline, SystemClock.uptimeMillis() + SETUP_TRAVERSAL_CAP_MS);
            AnchorCollection collection = collectAnchors(
                    root, targetPackage, windowId, traversalDeadline);
            anchors = collection.anchors;
            Log.i(TAG, "ANCHOR_SHADOW_SETUP session=" + session
                    + " window=" + windowId
                    + " result=1"
                    + " reason=0"
                    + " readStart=" + collection.startedUptimeMs
                    + " readEnd=" + collection.endedUptimeMs
                    + " costMs=" + Math.max(0L,
                            collection.endedUptimeMs - collection.startedUptimeMs)
                    + " fetches=" + collection.fetchAttempts
                    + " visited=" + collection.visited
                    + " anchors=" + anchors.size()
                    + " deadline=" + (collection.deadlineReached ? 1 : 0)
                    + " rootAttempts=" + rootLookup.attempts
                    + " rootNulls=" + rootLookup.nullResults
                    + " packageMismatches=" + rootLookup.packageMismatches);
            if (collection.deadlineReached || SystemClock.uptimeMillis() >= deadline) {
                int reason = SystemClock.uptimeMillis() >= deadline
                        ? REASON_SESSION_CAP : REASON_SETUP_TRAVERSAL_CAP;
                return ProbeResult.of(reason, windowId, anchors.size(),
                        eventSequence.get(), sampleCount, evidence,
                        eventCoupledPairs, maxDeltaInliers, readCosts);
            }
            if (anchors.size() < MIN_ANCHORS) {
                return ProbeResult.of(REASON_INSUFFICIENT_ANCHORS, windowId,
                        anchors.size(), eventSequence.get(), sampleCount,
                        evidence, eventCoupledPairs,
                        maxDeltaInliers, readCosts);
            }

            AnchorRead baseline = refreshAnchors(
                    anchors, targetPackage, windowId,
                    eventSequence.get(), eventSequence);
            readCosts.add(baseline.costMs);
            if (baseline.reason != REASON_SUCCESS) {
                return ProbeResult.of(baseline.reason, windowId, anchors.size(),
                        eventSequence.get(), sampleCount, evidence,
                        eventCoupledPairs, maxDeltaInliers, readCosts);
            }
            Movement baselineMovement = movementBetween(initialBounds(anchors), baseline.bounds);
            if (baselineMovement.movedAnchors > 0
                    || baseline.eventSequenceBefore != baseline.eventSequenceAfter) {
                return ProbeResult.of(REASON_BASELINE_UNSTABLE, windowId,
                        anchors.size(), eventSequence.get(), sampleCount,
                        evidence, eventCoupledPairs,
                        maxDeltaInliers, readCosts);
            }

            Log.i(TAG, "ANCHOR_SHADOW_READY session=" + session
                    + " window=" + windowId
                    + " readStart=" + baseline.startedUptimeMs
                    + " readEnd=" + baseline.endedUptimeMs
                    + " costMs=" + baseline.costMs
                    + " medianDeltaX=" + baselineMovement.medianDx
                    + " medianDeltaY=" + baselineMovement.medianDy
                    + " anchors=" + anchors.size()
                    + " eventSeqBefore=" + baseline.eventSequenceBefore
                    + " eventSeqAfter=" + baseline.eventSequenceAfter);

            eventSignal.clear();
            long readySequence = eventSequence.get();
            boolean armed = false;
            while (SystemClock.uptimeMillis() < deadline) {
                int stopped = stopReason.get();
                if (stopped != REASON_SUCCESS) {
                    return ProbeResult.of(stopped, windowId, anchors.size(),
                            eventSequence.get(), sampleCount, evidence,
                            eventCoupledPairs, maxDeltaInliers, readCosts);
                }
                NumericScrollEvent observed = latestEvent.get();
                if (observed != null && observed.sequence > readySequence) {
                    armed = true;
                    break;
                }
                long remaining = Math.max(1L, deadline - SystemClock.uptimeMillis());
                try {
                    eventSignal.poll(remaining, TimeUnit.MILLISECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return ProbeResult.of(REASON_INTERRUPTED, windowId, anchors.size(),
                            eventSequence.get(), sampleCount, evidence,
                            eventCoupledPairs, maxDeltaInliers, readCosts);
                }
            }
            if (!armed) {
                int reason = stopReason.get();
                return ProbeResult.of(reason == REASON_SUCCESS
                                ? REASON_INSUFFICIENT_SAMPLES : reason,
                        windowId, anchors.size(), eventSequence.get(), sampleCount,
                        evidence, eventCoupledPairs,
                        maxDeltaInliers, readCosts);
            }

            // The event queue only arms this loop. Geometry reads now run independently at a
            // fixed delay, so movement with an unchanged event sequence is the freshness signal.
            eventSignal.clear();
            long previousEventSequence = -1L;
            List<Rect> previousEventBounds = null;
            boolean previousAssociationStable = false;
            long pendingBetweenEventSequence = -1L;
            int pendingBetweenEventPairs = 0;
            long[] pendingCandidateReadEnds = new long[MAX_PENDING_PAIR_TIMESTAMPS];
            int pendingCandidateReadEndCount = 0;
            int pendingTimestampDrops = 0;
            while (SystemClock.uptimeMillis() < deadline) {
                int stopped = stopReason.get();
                if (stopped != REASON_SUCCESS) {
                    return ProbeResult.of(stopped, windowId, anchors.size(),
                            eventSequence.get(), sampleCount, evidence,
                            eventCoupledPairs, maxDeltaInliers, readCosts);
                }
                long sequenceBefore = eventSequence.get();
                NumericScrollEvent observed = latestEvent.get();
                if (observed == null) observed = NumericScrollEvent.NONE;
                AnchorRead sample = refreshAnchors(
                        anchors, targetPackage, windowId,
                        sequenceBefore, eventSequence);
                readCosts.add(sample.costMs);
                Movement movement = sample.reason != REASON_SUCCESS
                        || previousEventBounds == null
                        ? Movement.NONE : movementBetween(previousEventBounds, sample.bounds);
                sampleCount++;
                maxDeltaInliers = Math.max(maxDeltaInliers, movement.deltaInliers);
                boolean currentAssociationStable = sample.reason == REASON_SUCCESS
                        && observed.sequence == sample.eventSequenceBefore
                        && sample.eventSequenceBefore == sample.eventSequenceAfter
                        && sample.eventSequenceBefore > readySequence;
                boolean consensusMotion = movement.hasConsensus();
                int receiptTimeConfirmedPairs = 0;
                int sourceTimeConfirmedPairs = 0;
                int sourceTimeUnconfirmedPairs = 0;
                int confirmationTimestampDrops = 0;
                if (currentAssociationStable && pendingBetweenEventSequence >= 0L
                        && sample.eventSequenceBefore > pendingBetweenEventSequence) {
                    // Receipt order only proves that the callback arrived after the candidate.
                    // A source-time confirmation additionally proves that the later event was
                    // generated after that candidate read completed, excluding delayed delivery.
                    receiptTimeConfirmedPairs = pendingBetweenEventPairs;
                    if (observed.sourceUptimeMs >= 0L) {
                        for (int index = 0; index < pendingCandidateReadEndCount; index++) {
                            if (observed.sourceUptimeMs > pendingCandidateReadEnds[index]) {
                                sourceTimeConfirmedPairs++;
                            }
                        }
                    }
                    sourceTimeUnconfirmedPairs = receiptTimeConfirmedPairs
                            - sourceTimeConfirmedPairs;
                    confirmationTimestampDrops = pendingTimestampDrops;
                    evidence.receiptTimeBetweenEventMovingPairs +=
                            receiptTimeConfirmedPairs;
                    evidence.sourceTimeBetweenEventMovingPairs +=
                            sourceTimeConfirmedPairs;
                    evidence.sourceTimeUnconfirmedMovingPairs +=
                            sourceTimeUnconfirmedPairs;
                    pendingBetweenEventSequence = -1L;
                    pendingBetweenEventPairs = 0;
                    pendingCandidateReadEndCount = 0;
                    pendingTimestampDrops = 0;
                }
                boolean betweenEventCandidate = previousAssociationStable
                        && currentAssociationStable
                        && previousEventSequence == sample.eventSequenceBefore
                        && consensusMotion;
                boolean eventCoupled = previousAssociationStable && currentAssociationStable
                        && previousEventSequence != sample.eventSequenceBefore
                        && consensusMotion;
                if (betweenEventCandidate) {
                    if (pendingBetweenEventSequence != sample.eventSequenceBefore) {
                        pendingBetweenEventSequence = sample.eventSequenceBefore;
                        pendingBetweenEventPairs = 0;
                        pendingCandidateReadEndCount = 0;
                        pendingTimestampDrops = 0;
                    }
                    pendingBetweenEventPairs++;
                    if (pendingCandidateReadEndCount < pendingCandidateReadEnds.length) {
                        pendingCandidateReadEnds[pendingCandidateReadEndCount++] =
                                sample.endedUptimeMs;
                    } else {
                        pendingTimestampDrops++;
                        evidence.pendingTimestampDrops++;
                    }
                }
                if (eventCoupled) eventCoupledPairs++;
                int pairType = betweenEventCandidate ? 1 : eventCoupled ? 2 : 0;
                logSample(session, windowId, sample, movement, observed,
                        currentAssociationStable, pairType,
                        receiptTimeConfirmedPairs, sourceTimeConfirmedPairs,
                        sourceTimeUnconfirmedPairs, confirmationTimestampDrops);

                if (sample.reason != REASON_SUCCESS) {
                    return ProbeResult.of(sample.reason, windowId, anchors.size(),
                            eventSequence.get(), sampleCount, evidence,
                            eventCoupledPairs, maxDeltaInliers, readCosts);
                }
                if (currentAssociationStable) {
                    previousEventBounds = sample.bounds;
                    previousEventSequence = sample.eventSequenceBefore;
                    previousAssociationStable = true;
                }
                if (SystemClock.uptimeMillis() >= deadline) break;
                SystemClock.sleep(Math.min(
                        SAMPLE_DELAY_MS, Math.max(1L, deadline - SystemClock.uptimeMillis())));
            }
            return ProbeResult.of(evidence.sourceTimeBetweenEventMovingPairs > 0
                            ? REASON_SUCCESS : REASON_INSUFFICIENT_SAMPLES,
                    windowId, anchors.size(), eventSequence.get(), sampleCount,
                    evidence, eventCoupledPairs,
                    maxDeltaInliers, readCosts);
        } finally {
            for (Anchor anchor : anchors) anchor.recycle();
        }
    }

    private static void logSample(
            long session,
            int windowId,
            AnchorRead sample,
            Movement movement,
            NumericScrollEvent observed,
            boolean associationStable,
            int pairType,
            int receiptTimeConfirmedPairs,
            int sourceTimeConfirmedPairs,
            int sourceTimeUnconfirmedPairs,
            int confirmationTimestampDrops) {
        Log.i(TAG, "ANCHOR_SHADOW_SAMPLE session=" + session
                + " window=" + windowId
                + " readStart=" + sample.startedUptimeMs
                + " readEnd=" + sample.endedUptimeMs
                + " costMs=" + sample.costMs
                + " readResult=" + sample.reason
                + " medianDeltaX=" + movement.medianDx
                + " medianDeltaY=" + movement.medianDy
                + " anchors=" + sample.bounds.size()
                + " movedAnchors=" + movement.movedAnchors
                + " deltaInliers=" + movement.deltaInliers
                + " eventSeqBefore=" + sample.eventSequenceBefore
                + " eventSeqAfter=" + sample.eventSequenceAfter
                + " eventRecordSeq=" + observed.sequence
                + " sourceUptimeMs=" + observed.sourceUptimeMs
                + " receiptUptimeMs=" + observed.receiptUptimeMs
                + " eventAgeMs=" + observed.eventAgeMs
                + " scrollX=" + observed.scrollX
                + " scrollY=" + observed.scrollY
                + " maxScrollX=" + observed.maxScrollX
                + " maxScrollY=" + observed.maxScrollY
                + " fromIndex=" + observed.fromIndex
                + " toIndex=" + observed.toIndex
                + " itemCount=" + observed.itemCount
                + " scrollDeltaX=" + observed.scrollDeltaX
                + " scrollDeltaY=" + observed.scrollDeltaY
                + " associationStable=" + (associationStable ? 1 : 0)
                + " pairType=" + pairType
                + " receiptTimeConfirmedPairs=" + receiptTimeConfirmedPairs
                + " sourceTimeConfirmedPairs=" + sourceTimeConfirmedPairs
                + " sourceTimeUnconfirmedPairs=" + sourceTimeUnconfirmedPairs
                + " sourceTimestampComparable="
                        + (observed.sourceUptimeMs >= 0L ? 1 : 0)
                + " confirmationTimestampDrops=" + confirmationTimestampDrops);
    }

    /** Returns one owned target root, retrying only during bounded setup. */
    private static RootLookup awaitTargetRoot(
            UiAutomation automation,
            String targetPackage,
            long sessionDeadlineUptimeMs) {
        long startedUptime = SystemClock.uptimeMillis();
        long readinessDeadline = Math.min(
                sessionDeadlineUptimeMs, startedUptime + ROOT_READY_TIMEOUT_MS);
        int attempts = 0;
        int nullResults = 0;
        int packageMismatches = 0;
        while (SystemClock.uptimeMillis() < readinessDeadline) {
            attempts++;
            AccessibilityNodeInfo candidate = automation.getRootInActiveWindow();
            if (candidate == null) {
                nullResults++;
            } else if (packageMatches(candidate.getPackageName(), targetPackage)) {
                return new RootLookup(candidate, REASON_SUCCESS, attempts, nullResults,
                        packageMismatches, startedUptime, SystemClock.uptimeMillis());
            } else {
                packageMismatches++;
                candidate.recycle();
            }
            long remaining = readinessDeadline - SystemClock.uptimeMillis();
            if (remaining <= 0L) break;
            SystemClock.sleep(Math.min(ROOT_RETRY_DELAY_MS, remaining));
        }
        long endedUptime = SystemClock.uptimeMillis();
        int reason = endedUptime >= sessionDeadlineUptimeMs
                ? REASON_SESSION_CAP
                : packageMismatches > 0 ? REASON_PACKAGE_CHANGED : REASON_ROOT_UNAVAILABLE;
        return new RootLookup(null, reason, attempts, nullResults, packageMismatches,
                startedUptime, endedUptime);
    }

    /** Consumes {@code root}; selected leaves remain owned by the returned collection. */
    private static AnchorCollection collectAnchors(
            AccessibilityNodeInfo root,
            String targetPackage,
            int windowId,
            long deadlineUptimeMs) {
        long startedUptime = SystemClock.uptimeMillis();
        Rect windowBounds = new Rect();
        root.getBoundsInScreen(windowBounds);
        ArrayDeque<NodeAtDepth> pending = new ArrayDeque<>();
        pending.addLast(new NodeAtDepth(root, 0));
        int fetchAttempts = 1;
        int visited = 0;
        boolean deadlineReached = false;
        List<Anchor> anchors = new ArrayList<>(MAX_ANCHORS);
        try {
            while (!pending.isEmpty() && visited < MAX_TREE_NODES
                    && anchors.size() < MAX_ANCHORS) {
                if (SystemClock.uptimeMillis() >= deadlineUptimeMs) {
                    deadlineReached = true;
                    break;
                }
                // Depth-first traversal reaches deeply nested WebView content without spending
                // the bounded fetch budget across every shallow sibling first.
                NodeAtDepth entry = pending.removeLast();
                AccessibilityNodeInfo node = entry.node;
                boolean retained = false;
                visited++;
                try {
                    int childCount = node.getChildCount();
                    Rect bounds = new Rect();
                    node.getBoundsInScreen(bounds);
                    if (childCount == 0 && qualifiesAsAnchor(
                            node, bounds, windowBounds, targetPackage, windowId)
                            && hasDistinctBounds(anchors, bounds)) {
                        anchors.add(new Anchor(node, bounds));
                        retained = true;
                    } else if (entry.depth < MAX_TREE_DEPTH) {
                        List<NodeAtDepth> viewportChildren = new ArrayList<>();
                        try {
                            for (int childIndex = 0; childIndex < childCount; childIndex++) {
                                // Count the attempted fetch, including a null result, before
                                // crossing the Binder/cache boundary. Large virtual trees cannot
                                // exceed the global work bound merely by returning null children.
                                if (fetchAttempts >= MAX_TREE_NODES) break;
                                if (SystemClock.uptimeMillis() >= deadlineUptimeMs) {
                                    deadlineReached = true;
                                    break;
                                }
                                fetchAttempts++;
                                AccessibilityNodeInfo child = node.getChild(childIndex);
                                if (child == null) continue;
                                if (isViewportDescendant(
                                        child, windowBounds, targetPackage, windowId)) {
                                    viewportChildren.add(
                                            new NodeAtDepth(child, entry.depth + 1));
                                } else {
                                    child.recycle();
                                }
                            }
                            // Reverse push preserves child order when popping from the tail.
                            for (int index = viewportChildren.size() - 1; index >= 0; index--) {
                                pending.addLast(viewportChildren.get(index));
                            }
                            viewportChildren.clear();
                        } finally {
                            for (NodeAtDepth child : viewportChildren) child.node.recycle();
                        }
                    }
                } finally {
                    if (!retained) node.recycle();
                }
            }
        } catch (RuntimeException failure) {
            for (Anchor anchor : anchors) anchor.recycle();
            anchors.clear();
            throw failure;
        } finally {
            while (!pending.isEmpty()) pending.removeFirst().node.recycle();
        }
        long endedUptime = SystemClock.uptimeMillis();
        return new AnchorCollection(anchors, fetchAttempts, visited,
                startedUptime, endedUptime,
                deadlineReached || endedUptime >= deadlineUptimeMs);
    }

    private static boolean isViewportDescendant(
            AccessibilityNodeInfo node,
            Rect windowBounds,
            String targetPackage,
            int windowId) {
        if (!node.isVisibleToUser() || node.getWindowId() != windowId
                || !packageMatches(node.getPackageName(), targetPackage)) {
            return false;
        }
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        // Some virtual WebView containers expose no geometry even though their descendants do.
        // Retain those ancestors; prune only nodes proven to be outside this viewport.
        return bounds.isEmpty() || Rect.intersects(bounds, windowBounds);
    }

    private static boolean hasDistinctBounds(List<Anchor> anchors, Rect candidateBounds) {
        for (Anchor anchor : anchors) {
            if (anchor.initialBounds.equals(candidateBounds)) return false;
        }
        return true;
    }

    private static boolean qualifiesAsAnchor(
            AccessibilityNodeInfo node,
            Rect bounds,
            Rect windowBounds,
            String targetPackage,
            int windowId) {
        if (!node.isVisibleToUser() || node.getWindowId() != windowId
                || !packageMatches(node.getPackageName(), targetPackage)
                || bounds.isEmpty() || windowBounds.isEmpty()
                || !Rect.intersects(bounds, windowBounds)) {
            return false;
        }
        int topCutoff = windowBounds.top
                + windowBounds.height() * TOP_EXCLUSION_PERCENT / 100;
        if (bounds.centerY() <= topCutoff) return false;
        long windowArea = (long) windowBounds.width() * windowBounds.height();
        long anchorArea = (long) bounds.width() * bounds.height();
        return bounds.width() >= 4 && bounds.height() >= 4
                && anchorArea * 100L < windowArea * MAX_ANCHOR_AREA_PERCENT;
    }

    private static AnchorRead refreshAnchors(
            List<Anchor> anchors,
            String targetPackage,
            int expectedWindow,
            long sequenceBefore,
            AtomicLong eventSequence) {
        long startedNanos = SystemClock.elapsedRealtimeNanos();
        long startedUptime = SystemClock.uptimeMillis();
        List<Rect> bounds = new ArrayList<>(anchors.size());
        for (Anchor anchor : anchors) {
            // AccessibilityNodeInfo has no caller-supplied refresh timeout. Its synchronous
            // interaction is bounded by the platform AccessibilityInteractionClient timeout;
            // after it returns, the 8 ms observed-cost and session deadline prevent another read.
            if (!anchor.node.refresh()) {
                return AnchorRead.failed(REASON_REFRESH_FAILED, sequenceBefore,
                        eventSequence.get(), startedNanos, startedUptime);
            }
            if (!packageMatches(anchor.node.getPackageName(), targetPackage)) {
                return AnchorRead.failed(REASON_PACKAGE_CHANGED, sequenceBefore,
                        eventSequence.get(), startedNanos, startedUptime);
            }
            if (anchor.node.getWindowId() != expectedWindow) {
                return AnchorRead.failed(REASON_WINDOW_CHANGED, sequenceBefore,
                        eventSequence.get(), startedNanos, startedUptime);
            }
            Rect value = new Rect();
            anchor.node.getBoundsInScreen(value);
            if (value.isEmpty()) {
                return AnchorRead.failed(REASON_REFRESH_FAILED, sequenceBefore,
                        eventSequence.get(), startedNanos, startedUptime);
            }
            bounds.add(value);
        }
        long endedNanos = SystemClock.elapsedRealtimeNanos();
        long endedUptime = SystemClock.uptimeMillis();
        long costMs = elapsedMillisRoundedUp(startedNanos, endedNanos);
        if (costMs > MAX_READ_COST_MS) {
            return new AnchorRead(REASON_SLOW_READ, bounds, startedUptime, endedUptime,
                    costMs, sequenceBefore, eventSequence.get());
        }
        return new AnchorRead(REASON_SUCCESS, bounds, startedUptime, endedUptime,
                costMs, sequenceBefore, eventSequence.get());
    }

    private static List<Rect> initialBounds(List<Anchor> anchors) {
        List<Rect> result = new ArrayList<>(anchors.size());
        for (Anchor anchor : anchors) result.add(new Rect(anchor.initialBounds));
        return result;
    }

    private static Movement movementBetween(List<Rect> previous, List<Rect> current) {
        if (previous == null || current == null || previous.size() != current.size()
                || previous.isEmpty()) {
            return Movement.NONE;
        }
        int[] dx = new int[current.size()];
        int[] dy = new int[current.size()];
        int moved = 0;
        for (int index = 0; index < current.size(); index++) {
            dx[index] = current.get(index).centerX() - previous.get(index).centerX();
            dy[index] = current.get(index).centerY() - previous.get(index).centerY();
            if (Math.abs(dx[index]) >= MOVEMENT_THRESHOLD_PX
                    || Math.abs(dy[index]) >= MOVEMENT_THRESHOLD_PX) {
                moved++;
            }
        }
        int medianDx = median(dx);
        int medianDy = median(dy);
        int deltaInliers = 0;
        if (Math.abs(medianDx) >= MOVEMENT_THRESHOLD_PX
                || Math.abs(medianDy) >= MOVEMENT_THRESHOLD_PX) {
            for (int index = 0; index < dx.length; index++) {
                if ((Math.abs(dx[index]) >= MOVEMENT_THRESHOLD_PX
                        || Math.abs(dy[index]) >= MOVEMENT_THRESHOLD_PX)
                        && Math.abs(dx[index] - medianDx) <= DELTA_INLIER_TOLERANCE_PX
                        && Math.abs(dy[index] - medianDy) <= DELTA_INLIER_TOLERANCE_PX) {
                    deltaInliers++;
                }
            }
        }
        return new Movement(medianDx, medianDy, moved, deltaInliers);
    }

    private static int median(int[] values) {
        int[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        int middle = sorted.length / 2;
        if ((sorted.length & 1) == 1) return sorted[middle];
        return Math.round((sorted[middle - 1] + sorted[middle]) * .5f);
    }

    private static long elapsedMillisRoundedUp(long startedNanos, long endedNanos) {
        long elapsed = Math.max(0L, endedNanos - startedNanos);
        return (elapsed + 999_999L) / 1_000_000L;
    }

    private static boolean packageMatches(CharSequence observed, String expected) {
        return observed != null && expected.contentEquals(observed);
    }

    private static void replaceSignal(ArrayBlockingQueue<Long> signal, long sequence) {
        while (!signal.offer(sequence)) signal.poll();
    }

    private static final class NodeAtDepth {
        final AccessibilityNodeInfo node;
        final int depth;

        NodeAtDepth(AccessibilityNodeInfo node, int depth) {
            this.node = node;
            this.depth = depth;
        }
    }

    private static final class RootLookup {
        final AccessibilityNodeInfo root;
        final int reason;
        final int attempts;
        final int nullResults;
        final int packageMismatches;
        final long startedUptimeMs;
        final long endedUptimeMs;

        RootLookup(
                AccessibilityNodeInfo root,
                int reason,
                int attempts,
                int nullResults,
                int packageMismatches,
                long startedUptimeMs,
                long endedUptimeMs) {
            this.root = root;
            this.reason = reason;
            this.attempts = attempts;
            this.nullResults = nullResults;
            this.packageMismatches = packageMismatches;
            this.startedUptimeMs = startedUptimeMs;
            this.endedUptimeMs = endedUptimeMs;
        }
    }

    private static final class AnchorCollection {
        final List<Anchor> anchors;
        final int fetchAttempts;
        final int visited;
        final long startedUptimeMs;
        final long endedUptimeMs;
        final boolean deadlineReached;

        AnchorCollection(
                List<Anchor> anchors,
                int fetchAttempts,
                int visited,
                long startedUptimeMs,
                long endedUptimeMs,
                boolean deadlineReached) {
            this.anchors = anchors;
            this.fetchAttempts = fetchAttempts;
            this.visited = visited;
            this.startedUptimeMs = startedUptimeMs;
            this.endedUptimeMs = endedUptimeMs;
            this.deadlineReached = deadlineReached;
        }
    }

    private static final class Anchor {
        final AccessibilityNodeInfo node;
        final Rect initialBounds;

        Anchor(AccessibilityNodeInfo node, Rect initialBounds) {
            this.node = node;
            this.initialBounds = new Rect(initialBounds);
        }

        void recycle() {
            node.recycle();
        }
    }

    private static final class AnchorRead {
        final int reason;
        final List<Rect> bounds;
        final long startedUptimeMs;
        final long endedUptimeMs;
        final long costMs;
        final long eventSequenceBefore;
        final long eventSequenceAfter;

        AnchorRead(
                int reason,
                List<Rect> bounds,
                long startedUptimeMs,
                long endedUptimeMs,
                long costMs,
                long eventSequenceBefore,
                long eventSequenceAfter) {
            this.reason = reason;
            this.bounds = Collections.unmodifiableList(new ArrayList<>(bounds));
            this.startedUptimeMs = startedUptimeMs;
            this.endedUptimeMs = endedUptimeMs;
            this.costMs = costMs;
            this.eventSequenceBefore = eventSequenceBefore;
            this.eventSequenceAfter = eventSequenceAfter;
        }

        static AnchorRead failed(
                int reason,
                long sequenceBefore,
                long sequenceAfter,
                long startedNanos,
                long startedUptime) {
            long endedNanos = SystemClock.elapsedRealtimeNanos();
            return new AnchorRead(reason, Collections.emptyList(), startedUptime,
                    SystemClock.uptimeMillis(), elapsedMillisRoundedUp(startedNanos, endedNanos),
                    sequenceBefore, sequenceAfter);
        }
    }

    private static final class Movement {
        static final Movement NONE = new Movement(0, 0, 0, 0);

        final int medianDx;
        final int medianDy;
        final int movedAnchors;
        final int deltaInliers;

        Movement(int medianDx, int medianDy, int movedAnchors, int deltaInliers) {
            this.medianDx = medianDx;
            this.medianDy = medianDy;
            this.movedAnchors = movedAnchors;
            this.deltaInliers = deltaInliers;
        }

        boolean hasConsensus() {
            return deltaInliers >= MIN_ANCHORS
                    && (Math.abs(medianDx) >= MOVEMENT_THRESHOLD_PX
                    || Math.abs(medianDy) >= MOVEMENT_THRESHOLD_PX);
        }
    }

    private static final class NumericScrollEvent {
        static final NumericScrollEvent NONE = new NumericScrollEvent(
                -1L, -1L, -1L,
                -1, -1, -1, -1, -1, -1, -1, -1, -1);

        final long sequence;
        final long sourceUptimeMs;
        final long receiptUptimeMs;
        final long eventAgeMs;
        final int scrollX;
        final int scrollY;
        final int maxScrollX;
        final int maxScrollY;
        final int fromIndex;
        final int toIndex;
        final int itemCount;
        final int scrollDeltaX;
        final int scrollDeltaY;

        NumericScrollEvent(
                long sequence,
                long sourceUptimeMs,
                long receiptUptimeMs,
                int scrollX,
                int scrollY,
                int maxScrollX,
                int maxScrollY,
                int fromIndex,
                int toIndex,
                int itemCount,
                int scrollDeltaX,
                int scrollDeltaY) {
            this.sequence = sequence;
            this.sourceUptimeMs = sourceUptimeMs;
            this.receiptUptimeMs = receiptUptimeMs;
            eventAgeMs = Math.max(0L, receiptUptimeMs - sourceUptimeMs);
            this.scrollX = scrollX;
            this.scrollY = scrollY;
            this.maxScrollX = maxScrollX;
            this.maxScrollY = maxScrollY;
            this.fromIndex = fromIndex;
            this.toIndex = toIndex;
            this.itemCount = itemCount;
            this.scrollDeltaX = scrollDeltaX;
            this.scrollDeltaY = scrollDeltaY;
        }

        static NumericScrollEvent from(
                AccessibilityEvent event, long sequence, long receiptUptimeMs) {
            int deltaX = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? event.getScrollDeltaX() : 0;
            int deltaY = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? event.getScrollDeltaY() : 0;
            return new NumericScrollEvent(
                    sequence, event.getEventTime(), receiptUptimeMs,
                    event.getScrollX(), event.getScrollY(),
                    event.getMaxScrollX(), event.getMaxScrollY(),
                    event.getFromIndex(), event.getToIndex(), event.getItemCount(),
                    deltaX, deltaY);
        }
    }

    private static final class EvidenceCounts {
        int receiptTimeBetweenEventMovingPairs;
        int sourceTimeBetweenEventMovingPairs;
        int sourceTimeUnconfirmedMovingPairs;
        int pendingTimestampDrops;
    }

    private static final class ProbeResult {
        final int reason;
        final int windowId;
        final int anchorCount;
        final long eventCount;
        final int sampleCount;
        final int receiptTimeBetweenEventMovingPairs;
        final int sourceTimeBetweenEventMovingPairs;
        final int sourceTimeUnconfirmedMovingPairs;
        final int pendingTimestampDrops;
        final int eventCoupledPairs;
        final int maxDeltaInliers;
        final long readCostP50Ms;
        final long readCostP95Ms;
        final long maxReadCostMs;

        ProbeResult(
                int reason,
                int windowId,
                int anchorCount,
                long eventCount,
                int sampleCount,
                int receiptTimeBetweenEventMovingPairs,
                int sourceTimeBetweenEventMovingPairs,
                int sourceTimeUnconfirmedMovingPairs,
                int pendingTimestampDrops,
                int eventCoupledPairs,
                int maxDeltaInliers,
                long readCostP50Ms,
                long readCostP95Ms,
                long maxReadCostMs) {
            this.reason = reason;
            this.windowId = windowId;
            this.anchorCount = anchorCount;
            this.eventCount = eventCount;
            this.sampleCount = sampleCount;
            this.receiptTimeBetweenEventMovingPairs =
                    receiptTimeBetweenEventMovingPairs;
            this.sourceTimeBetweenEventMovingPairs = sourceTimeBetweenEventMovingPairs;
            this.sourceTimeUnconfirmedMovingPairs = sourceTimeUnconfirmedMovingPairs;
            this.pendingTimestampDrops = pendingTimestampDrops;
            this.eventCoupledPairs = eventCoupledPairs;
            this.maxDeltaInliers = maxDeltaInliers;
            this.readCostP50Ms = readCostP50Ms;
            this.readCostP95Ms = readCostP95Ms;
            this.maxReadCostMs = maxReadCostMs;
        }

        static ProbeResult of(
                int reason,
                int windowId,
                int anchorCount,
                long eventCount,
                int sampleCount,
                EvidenceCounts evidence,
                int eventCoupledPairs,
                int maxDeltaInliers,
                List<Long> readCosts) {
            List<Long> sorted = new ArrayList<>(readCosts);
            Collections.sort(sorted);
            long p50 = percentile(sorted, .50f);
            long p95 = percentile(sorted, .95f);
            long maximum = sorted.isEmpty() ? 0L : sorted.get(sorted.size() - 1);
            return new ProbeResult(reason, windowId, anchorCount, eventCount,
                    sampleCount,
                    evidence.receiptTimeBetweenEventMovingPairs,
                    evidence.sourceTimeBetweenEventMovingPairs,
                    evidence.sourceTimeUnconfirmedMovingPairs,
                    evidence.pendingTimestampDrops,
                    eventCoupledPairs,
                    maxDeltaInliers, p50, p95, maximum);
        }

        private static long percentile(List<Long> sorted, float fraction) {
            if (sorted.isEmpty()) return 0L;
            int index = Math.max(0, Math.min(sorted.size() - 1,
                    Math.round((sorted.size() - 1) * fraction)));
            return sorted.get(index);
        }
    }
}
