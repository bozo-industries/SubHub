package com.subhub.app.detection.text;

import android.graphics.Rect;
import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;

import com.subhub.app.detection.Detection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Bounded extraction of visible native app text from a user-enabled Accessibility session. */
public final class AccessibilityTextSmutDetector {
    private static final int MAX_NODES = 900;
    private static final int MAX_TEXT_LENGTH = 2_000;
    private final TextSmutDetectionFactory factory;

    public AccessibilityTextSmutDetector() {
        this(new SmutTextClassifier());
    }

    public AccessibilityTextSmutDetector(SmutTextClassifier classifier) {
        factory = new TextSmutDetectionFactory(classifier);
    }

    public List<Detection> detect(
            AccessibilityNodeInfo root,
            TextSmutConfig config,
            int width,
            int height) {
        return detect(root, config, width, height, false, false);
    }

    public List<Detection> detect(
            AccessibilityNodeInfo root,
            TextSmutConfig config,
            int width,
            int height,
            boolean semanticEnabled) {
        return detect(root, config, width, height, semanticEnabled, false);
    }

    public List<Detection> detect(
            AccessibilityNodeInfo root,
            TextSmutConfig config,
            int width,
            int height,
            boolean semanticEnabled,
            boolean exactGeometryPreferred) {
        return detect(root, config, width, height, semanticEnabled,
                exactGeometryPreferred, () -> false);
    }

    /** Traverses visible text while allowing a newly started scroll to cancel obsolete work. */
    public List<Detection> detect(
            AccessibilityNodeInfo root,
            TextSmutConfig config,
            int width,
            int height,
            boolean semanticEnabled,
            boolean exactGeometryPreferred,
            BooleanSupplier cancelled) {
        if (root == null || config == null || !config.isEnabled()) return Collections.emptyList();
        List<Detection> result = new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> pending = new ArrayDeque<>();
        pending.add(root);
        int visited = 0;
        while (!pending.isEmpty() && visited++ < MAX_NODES) {
            if (cancelled != null && cancelled.getAsBoolean()) break;
            AccessibilityNodeInfo node = pending.removeFirst();
            boolean ownsNode = node != root;
            try {
                if (!node.isVisibleToUser()) continue;
                String text = textOf(node);
                if (!text.isEmpty()) {
                    Rect bounds = new Rect();
                    node.getBoundsInScreen(bounds);
                    if (isUsefulTextRegion(node, bounds, width, height)) {
                        SmutTextClassifier.Match match = factory.classify(
                                text, config, semanticEnabled);
                        Rect characterBounds = AccessibilityTextGeometry.resolve(
                                node, text, match, width, height);
                        if (characterBounds == null
                                && (!isPlausibleTextRegion(text, bounds, width)
                                || (exactGeometryPreferred
                                && !isTightEstimatedRegion(bounds, width)))) continue;
                        Detection detection = characterBounds == null
                                ? factory.create(text, bounds, match, width, height,
                                        "TEXT_SMUT_ACCESSIBILITY_")
                                : factory.createExact(characterBounds, match, width, height,
                                        "TEXT_SMUT_ACCESSIBILITY_");
                        if (detection != null) {
                            detection = detection.withObservation(
                                    Detection.ObservationSource.ACCESSIBILITY,
                                    characterBounds == null
                                            ? Detection.GeometryQuality.ESTIMATED
                                            : Detection.GeometryQuality.EXACT,
                                    anchorFor(node, text));
                            result.add(detection);
                        }
                    }
                }
                for (int index = 0; index < node.getChildCount(); index++) {
                    AccessibilityNodeInfo child = childWithPrefetch(node, index);
                    if (child != null) pending.addLast(child);
                }
            } finally {
                if (ownsNode) node.recycle();
            }
        }
        while (!pending.isEmpty()) {
            AccessibilityNodeInfo abandoned = pending.removeFirst();
            if (abandoned != root) abandoned.recycle();
        }
        return Collections.unmodifiableList(
                DetectionFusion.merge(Collections.emptyList(), result));
    }

    private static boolean isUsefulTextRegion(
            AccessibilityNodeInfo node, Rect bounds, int width, int height) {
        if (bounds.isEmpty()) return false;
        long area = (long) bounds.width() * bounds.height();
        long screen = (long) Math.max(1, width) * Math.max(1, height);
        // Aggregate root/window descriptions duplicate descendant text and create screen-sized
        // censors. Post-sized containers remain eligible; the projector reduces them to lines.
        return node.getChildCount() == 0 || area * 100L < screen * 58L;
    }

    /** Rejects long container descriptions attached to tiny icons and action controls. */
    private static boolean isPlausibleTextRegion(String text, Rect bounds, int screenWidth) {
        int normalizedLength = SmutTextClassifier.normalize(text).length();
        int lineHeight = Math.max(24, Math.round(screenWidth * 0.035f));
        int estimatedLines = Math.max(1,
                (bounds.height() + lineHeight - 1) / lineHeight);
        int charactersPerLine = Math.max(4,
                Math.round(bounds.width() / Math.max(8f, lineHeight * 0.48f)));
        long estimatedCapacity = (long) estimatedLines * charactersPerLine * 2L;
        return normalizedLength <= estimatedCapacity;
    }

    /** Ultra OCR owns post/image-sized containers; Accessibility keeps only tight text nodes. */
    private static boolean isTightEstimatedRegion(Rect bounds, int screenWidth) {
        return bounds.height() <= Math.max(120, Math.round(screenWidth * 0.24f));
    }

    private static String textOf(AccessibilityNodeInfo node) {
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        String first = text == null ? "" : text.toString().trim();
        String second = description == null ? "" : description.toString().trim();
        // Some Compose/social feeds expose a caption only as a container description. Keep that
        // fallback: the region projector bounds it to text lines and fusion prefers tighter child
        // nodes whenever the same phrase is also exposed below this container.
        if (!first.isEmpty() && node.getChildCount() > 0) second = "";
        String combined = first;
        if (!second.isEmpty() && !second.equalsIgnoreCase(first)) {
            combined = first.isEmpty() ? second : first + " " + second;
        }
        return combined.length() <= MAX_TEXT_LENGTH
                ? combined : combined.substring(0, MAX_TEXT_LENGTH);
    }

    private static String anchorFor(AccessibilityNodeInfo node, String text) {
        String normalized = SmutTextClassifier.normalize(text);
        String textIdentity = Integer.toUnsignedString(normalized.hashCode(), 36);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String uniqueId = node.getUniqueId();
            if (uniqueId != null && !uniqueId.isEmpty()) {
                // Recycler-backed views may keep a node identity while binding different text.
                // Include content identity so a recycled cell cannot inherit confirmation.
                return "a11y:id:" + uniqueId + ':' + textIdentity;
            }
        }
        String viewId = node.getViewIdResourceName();
        String className = node.getClassName() == null ? "" : node.getClassName().toString();
        return "a11y:text:" + (viewId == null ? "" : viewId) + ':' + className + ':'
                + textIdentity;
    }

    private static AccessibilityNodeInfo childWithPrefetch(
            AccessibilityNodeInfo node,
            int index) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return node.getChild(index,
                    AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_BREADTH_FIRST);
        }
        return node.getChild(index);
    }
}
