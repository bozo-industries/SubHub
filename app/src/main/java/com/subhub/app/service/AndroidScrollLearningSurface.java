package com.subhub.app.service;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.Resources;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/** Worker-only acquisition of the actual event producer; no page strings survive acquisition. */
@SuppressWarnings("deprecation")
final class AndroidScrollLearningSurface {
    private AndroidScrollLearningSurface() { }

    static final class Surface implements AutoCloseable {
        final ScrollLearningKey key;
        final boolean durable;
        final long producerToken;
        private AccessibilityNodeInfo owner;

        Surface(ScrollLearningKey key, boolean durable, long producerToken,
                AccessibilityNodeInfo owner) {
            this.key = key; this.durable = durable; this.producerToken = producerToken;
            this.owner = owner;
        }

        AccessibilityNodeInfo takeOwner() {
            AccessibilityNodeInfo result = owner;
            owner = null;
            return result;
        }

        @Override public void close() {
            if (owner != null) { owner.recycle(); owner = null; }
        }
    }

    /**
     * The caller retains/recycles its event. Acquisition consumes only independently obtained
     * nodes. The expected token is the non-sticky producer, never the cache's reused surface.
     * Deadline includes package/resource lookup; a slow Binder call cannot be interrupted, so
     * callers must charge actual elapsed time and back off even if this returns null.
     */
    static Surface acquire(Context context, AccessibilityEvent event, String packageName,
            int windowId, long expectedProducer, int width, int height, int densityDpi,
            int rotation, int refreshMilliHz, ScrollLearningKey.Axis axis,
            ScrollLearningKey.Evidence evidence, long deadline) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("Learning acquisition requires a worker");
        }
        OwnerWalk walk = new OwnerWalk(packageName, windowId, deadline);
        try {
            walk.checkDeadline();
            if (event == null || packageName == null || windowId < 0
                    || event.getWindowId() != windowId
                    || !packageName.contentEquals(event.getPackageName() == null
                            ? "" : event.getPackageName())) return null;
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            Resources resources = context.getPackageManager().getResourcesForApplication(packageName);
            walk.checkDeadline();
            AccessibilityNodeInfo source = event.getSource();
            if (source == null) return null;
            // resolveForNode owns this wrapper even when one of its getters fails.
            AccessibilitySurfaceIdentityResolver.Identity identity =
                    new AccessibilitySurfaceIdentityResolver().resolveForNode(windowId, packageName,
                            walk.wrap(source), name -> compiledResource(resources, name));
            walk.checkDeadline();
            if (walk.owner == null || !identity.isCacheable() || identity.telemetryToken() != expectedProducer
                    || identity.ownerKind == AccessibilitySurfaceIdentityResolver.OWNER_NONE) return null;
            long version = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            ScrollLearningKey key = new ScrollLearningKey(packageName, version,
                    identity.learningDigest(), width, height, densityDpi, rotation, refreshMilliHz,
                    axis, evidence);
            Surface result = new Surface(key, identity.durableDigest != null,
                    identity.telemetryToken(), walk.owner);
            walk.owner = null;
            return result;
        } catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException failure) {
            // Provider/lookup failures are failed evidence, not calibration. No payload logging.
            return null;
        } finally {
            if (walk.owner != null) walk.owner.recycle();
        }
    }

    private static boolean compiledResource(Resources resources, String name) {
        try {
            int id = resources.getIdentifier(name, null, null);
            return id != 0 && "id".equals(resources.getResourceTypeName(id))
                    && name.equals(resources.getResourceName(id));
        } catch (Resources.NotFoundException failure) {
            return false;
        }
    }

    private static final class OwnerWalk {
        final String packageName;
        final int windowId;
        final long deadline;
        AccessibilityNodeInfo owner;

        OwnerWalk(String packageName, int windowId, long deadline) {
            this.packageName = packageName; this.windowId = windowId; this.deadline = deadline;
        }

        void checkDeadline() {
            if (SystemClock.uptimeMillis() >= deadline) throw new IllegalStateException("Read budget");
        }

        AccessibilitySurfaceIdentityResolver.Node wrap(AccessibilityNodeInfo node) {
            return new AccessibilitySurfaceIdentityResolver.Node() {
                void check() {
                    checkDeadline();
                    CharSequence observedPackage = node.getPackageName();
                    if (node.getWindowId() != windowId || observedPackage == null
                            || !packageName.contentEquals(observedPackage)) {
                        throw new IllegalStateException("Scope changed");
                    }
                }
                @Override public boolean isScrollable() {
                    check();
                    boolean scrollable = node.isScrollable();
                    if (scrollable && owner == null) owner = AccessibilityNodeInfo.obtain(node);
                    return scrollable;
                }
                @Override public String uniqueId() {
                    check();
                    return Build.VERSION.SDK_INT >= 33 ? node.getUniqueId() : null;
                }
                @Override public String viewId() { check(); return node.getViewIdResourceName(); }
                @Override public String className() {
                    check();
                    CharSequence name = node.getClassName();
                    return name == null ? null : name.toString();
                }
                @Override public AccessibilitySurfaceIdentityResolver.Node parent() {
                    check();
                    AccessibilityNodeInfo parent = Build.VERSION.SDK_INT >= 33
                            ? node.getParent(AccessibilityNodeInfo.FLAG_PREFETCH_ANCESTORS)
                            : node.getParent();
                    return parent == null ? null : wrap(parent);
                }
                @Override public void close() { node.recycle(); }
            };
        }
    }
}
