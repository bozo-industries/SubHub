package com.subhub.app.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.accessibility.AccessibilityEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class AccessibilityScrollMotionResolverAndroidTest {
    @Test public void explicitContentDeltaBecomesOppositeScreenMotion() {
        AccessibilityScrollMotionResolver resolver = new AccessibilityScrollMotionResolver();
        AccessibilityEvent event = scrollEvent();
        event.setScrollDeltaY(180);

        AccessibilityScrollMotionResolver.Motion motion = resolver.resolve(event, 1080, 2400);

        assertTrue(motion.moved());
        assertEquals(0, motion.dx);
        assertEquals(-180, motion.dy);
        event.recycle();
    }

    @Test public void absoluteScrollPositionIsUsedWhenDeltaIsMissing() {
        AccessibilityScrollMotionResolver resolver = new AccessibilityScrollMotionResolver();
        AccessibilityEvent first = scrollEvent();
        first.setScrollY(100);
        assertFalse(resolver.resolve(first, 1080, 2400).moved());

        AccessibilityEvent second = scrollEvent();
        second.setScrollY(164);
        AccessibilityScrollMotionResolver.Motion motion = resolver.resolve(second, 1080, 2400);

        assertTrue(motion.moved());
        assertEquals(-64, motion.dy);
        first.recycle();
        second.recycle();
    }

    @Test public void resetPreventsCrossPagePositionJump() {
        AccessibilityScrollMotionResolver resolver = new AccessibilityScrollMotionResolver();
        AccessibilityEvent first = scrollEvent();
        first.setScrollY(800);
        resolver.resolve(first, 1080, 2400);
        resolver.reset();

        AccessibilityEvent replacement = scrollEvent();
        replacement.setScrollY(10);
        assertFalse(resolver.resolve(replacement, 1080, 2400).moved());
        first.recycle();
        replacement.recycle();
    }

    @Test public void alternatingProducerCannotOverwriteAnotherAbsoluteBaseline() {
        AccessibilityScrollMotionResolver resolver = new AccessibilityScrollMotionResolver();
        AccessibilityEvent absoluteStart = scrollEvent();
        absoluteStart.setScrollY(100);
        assertFalse(resolver.resolve(absoluteStart, 1080, 2400, "absolute-node").moved());

        AccessibilityEvent explicitCompanion = scrollEvent();
        explicitCompanion.setScrollY(4_000);
        explicitCompanion.setScrollDeltaY(80);
        AccessibilityScrollMotionResolver.Motion explicit = resolver.resolve(
                explicitCompanion, 1080, 2400, "explicit-node");
        assertEquals(-80, explicit.dy);

        AccessibilityEvent absoluteReturn = scrollEvent();
        absoluteReturn.setScrollY(160);
        assertFalse(resolver.resolve(
                absoluteReturn, 1080, 2400, "absolute-node").moved());

        AccessibilityEvent absoluteFollowUp = scrollEvent();
        absoluteFollowUp.setScrollY(200);
        AccessibilityScrollMotionResolver.Motion absolute = resolver.resolve(
                absoluteFollowUp, 1080, 2400, "absolute-node");
        assertEquals(-40, absolute.dy);

        absoluteStart.recycle();
        explicitCompanion.recycle();
        absoluteReturn.recycle();
        absoluteFollowUp.recycle();
    }

    @Test public void visibleItemIndexProvidesLastResortFeedMotion() {
        AccessibilityScrollMotionResolver resolver = new AccessibilityScrollMotionResolver();
        AccessibilityEvent first = scrollEvent();
        first.setFromIndex(10);
        first.setToIndex(14);
        assertFalse(resolver.resolve(first, 1080, 2400).moved());

        AccessibilityEvent second = scrollEvent();
        second.setFromIndex(11);
        second.setToIndex(15);
        AccessibilityScrollMotionResolver.Motion motion = resolver.resolve(second, 1080, 2400);

        assertTrue(motion.moved());
        assertEquals(-480, motion.dy);
        first.recycle();
        second.recycle();
    }

    private static AccessibilityEvent scrollEvent() {
        AccessibilityEvent event = AccessibilityEvent.obtain(
                AccessibilityEvent.TYPE_VIEW_SCROLLED);
        event.setPackageName("com.example.feed");
        event.setClassName("androidx.recyclerview.widget.RecyclerView");
        return event;
    }
}
