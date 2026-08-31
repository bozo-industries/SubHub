package com.subhub.app.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure tests for the bounded, privacy-safe surface identity algorithm. */
public final class AccessibilitySurfaceIdentityResolverTest {
    @Test public void prefersUniqueIdAndDoesNotExposeRawIdentifiers() {
        AccessibilitySurfaceIdentityResolver resolver =
                new AccessibilitySurfaceIdentityResolver();
        FakeNode owner = new FakeNode(true, "androidx.recyclerview.widget.RecyclerView",
                "unique-owner", "com.example:id/feed");
        owner.parent = new FakeNode(false, "android.view.ViewRootImpl", null, "root");

        AccessibilitySurfaceIdentityResolver.Identity identity =
                resolver.resolveForNode(7, "com.example.app", owner);

        assertEquals(AccessibilitySurfaceIdentityResolver.OWNER_UNIQUE_ID,
                identity.ownerKind);
        assertEquals(AccessibilitySurfaceIdentityResolver.CONFIDENCE_HIGH,
                identity.confidence);
        assertTrue(identity.isCacheable());
        assertNotEquals(0L, identity.telemetryToken());
        assertFalse(identity.toString().contains("com.example.app"));
        assertFalse(identity.toString().contains("unique-owner"));
        assertFalse(identity.toString().contains("com.example:id/feed"));
    }

    @Test public void fallsBackToViewIdBeforeStructuralIdentity() {
        AccessibilitySurfaceIdentityResolver resolver =
                new AccessibilitySurfaceIdentityResolver();
        FakeNode owner = new FakeNode(true, "android.widget.ScrollView",
                null, "com.example:id/content");

        AccessibilitySurfaceIdentityResolver.Identity identity =
                resolver.resolveForNode(3, "com.example.app", owner);

        assertEquals(AccessibilitySurfaceIdentityResolver.OWNER_VIEW_ID,
                identity.ownerKind);
        assertEquals(AccessibilitySurfaceIdentityResolver.CONFIDENCE_MEDIUM,
                identity.confidence);
        assertTrue(identity.isCacheable());
    }

    @Test public void recycledChildrenDoNotChangeTheirScrollableOwner() {
        AccessibilitySurfaceIdentityResolver resolver =
                new AccessibilitySurfaceIdentityResolver();
        FakeNode firstOwner = new FakeNode(true, "androidx.recyclerview.widget.RecyclerView",
                "stable-recycler", null);
        firstOwner.parent = new FakeNode(false, "android.view.ViewRootImpl", null, "root");
        FakeNode firstChild = new FakeNode(false, "android.widget.TextView",
                "virtual-child-a", null);
        firstChild.parent = firstOwner;

        FakeNode secondOwner = new FakeNode(true, "androidx.recyclerview.widget.RecyclerView",
                "stable-recycler", null);
        secondOwner.parent = new FakeNode(false, "android.view.ViewRootImpl", null, "root");
        FakeNode secondChild = new FakeNode(false, "android.widget.TextView",
                "virtual-child-b", null);
        secondChild.parent = secondOwner;

        AccessibilitySurfaceIdentityResolver.Identity first = resolver.resolveForNode(
                9, "com.example.app", firstChild);
        AccessibilitySurfaceIdentityResolver.Identity second = resolver.resolveForNode(
                9, "com.example.app", secondChild);

        assertTrue(first.sameSurface(second));
        assertTrue(first.isCacheable());
        assertEquals(1, firstChild.closeCount);
        assertEquals(1, firstOwner.closeCount);
        assertEquals(1, secondChild.closeCount);
        assertEquals(1, secondOwner.closeCount);
    }

    @Test public void nearestNestedOwnerWins() {
        AccessibilitySurfaceIdentityResolver resolver =
                new AccessibilitySurfaceIdentityResolver();
        FakeNode inner = new FakeNode(true, "androidx.recyclerview.widget.RecyclerView",
                "inner-list", null);
        inner.parent = new FakeNode(true, "android.widget.ScrollView", "outer-page", null);
        FakeNode source = new FakeNode(false, "android.widget.ImageView", "cell", null);
        source.parent = inner;

        AccessibilitySurfaceIdentityResolver.Identity identity = resolver.resolveForNode(
                4, "com.example.app", source);

        assertEquals(AccessibilitySurfaceIdentityResolver.OWNER_UNIQUE_ID,
                identity.ownerKind);
        AccessibilitySurfaceIdentityResolver.Identity outerOnly = resolver.resolveForNode(
                4, "com.example.app", sourceWithoutInnerOwner());
        assertFalse(identity.sameSurface(outerOnly));
    }

    @Test public void structuralFallbackIsLowAndNotCacheable() {
        AccessibilitySurfaceIdentityResolver resolver =
                new AccessibilitySurfaceIdentityResolver();
        FakeNode owner = new FakeNode(true, "android.webkit.WebView", null, null);
        owner.parent = new FakeNode(false, "android.view.ViewRootImpl", null, null);

        AccessibilitySurfaceIdentityResolver.Identity identity =
                resolver.resolveForNode(2, "com.example.app", owner);

        assertEquals(AccessibilitySurfaceIdentityResolver.OWNER_STRUCTURAL,
                identity.ownerKind);
        assertEquals(AccessibilitySurfaceIdentityResolver.CONFIDENCE_LOW,
                identity.confidence);
        assertFalse(identity.isCacheable());
        assertTrue(identity.isLowConfidence());
    }

    @Test public void nullSourceIsExplicitlyLowAndNonCacheable() {
        AccessibilitySurfaceIdentityResolver resolver =
                new AccessibilitySurfaceIdentityResolver();

        AccessibilitySurfaceIdentityResolver.Identity identity =
                resolver.resolveForNode(2, "com.example.app", null);

        assertEquals(AccessibilitySurfaceIdentityResolver.OWNER_NONE,
                identity.ownerKind);
        assertEquals(AccessibilitySurfaceIdentityResolver.CONFIDENCE_LOW,
                identity.confidence);
        assertFalse(identity.isCacheable());
    }

    @Test public void parentWalkStopsAtSixHops() {
        AccessibilitySurfaceIdentityResolver resolver =
                new AccessibilitySurfaceIdentityResolver();
        FakeNode[] nodes = new FakeNode[8];
        for (int index = nodes.length - 1; index >= 0; index--) {
            nodes[index] = new FakeNode(index == 7, "node-" + index,
                    "owner-" + index, null);
            if (index + 1 < nodes.length) nodes[index].parent = nodes[index + 1];
        }

        AccessibilitySurfaceIdentityResolver.Identity identity =
                resolver.resolveForNode(1, "com.example.app", nodes[0]);

        assertFalse(identity.isCacheable());
        assertEquals(AccessibilitySurfaceIdentityResolver.CONFIDENCE_LOW,
                identity.confidence);
        int parentReads = 0;
        for (FakeNode node : nodes) parentReads += node.parentReadCount;
        assertEquals(AccessibilitySurfaceIdentityResolver.MAX_PARENT_HOPS, parentReads);
        assertEquals(0, nodes[7].closeCount);
    }

    @Test public void packageAndWindowArePartOfTheSurfaceToken() {
        AccessibilitySurfaceIdentityResolver resolver =
                new AccessibilitySurfaceIdentityResolver();
        AccessibilitySurfaceIdentityResolver.Identity first = resolver.resolveForNode(
                1, "com.example.one", newOwner("same-owner"));
        AccessibilitySurfaceIdentityResolver.Identity differentWindow = resolver.resolveForNode(
                2, "com.example.one", newOwner("same-owner"));
        AccessibilitySurfaceIdentityResolver.Identity differentPackage = resolver.resolveForNode(
                1, "com.example.two", newOwner("same-owner"));

        assertFalse(first.sameSurface(differentWindow));
        assertFalse(first.sameSurface(differentPackage));
    }

    private static FakeNode newOwner(String uniqueId) {
        return new FakeNode(true, "android.widget.ScrollView", uniqueId, null);
    }

    private static FakeNode sourceWithoutInnerOwner() {
        FakeNode source = new FakeNode(false, "android.widget.ImageView", "cell", null);
        source.parent = new FakeNode(true, "android.widget.ScrollView", "outer-page", null);
        return source;
    }

    private static final class FakeNode implements AccessibilitySurfaceIdentityResolver.Node {
        private final boolean scrollable;
        private final String className;
        private final String uniqueId;
        private final String viewId;
        private FakeNode parent;
        private int closeCount;
        private int parentReadCount;

        private FakeNode(
                boolean scrollable,
                String className,
                String uniqueId,
                String viewId) {
            this.scrollable = scrollable;
            this.className = className;
            this.uniqueId = uniqueId;
            this.viewId = viewId;
        }

        @Override public boolean isScrollable() { return scrollable; }
        @Override public String uniqueId() { return uniqueId; }
        @Override public String viewId() { return viewId; }
        @Override public String className() { return className; }

        @Override public AccessibilitySurfaceIdentityResolver.Node parent() {
            parentReadCount++;
            return parent;
        }

        @Override public void close() { closeCount++; }
    }
}
