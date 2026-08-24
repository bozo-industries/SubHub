package com.subhub.app.detection.text;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;

import com.subhub.app.detection.Detection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
        return detect(root, config, width, height, false);
    }

    public List<Detection> detect(
            AccessibilityNodeInfo root,
            TextSmutConfig config,
            int width,
            int height,
            boolean semanticEnabled) {
        if (root == null || config == null || !config.isEnabled()) return Collections.emptyList();
        List<Detection> result = new ArrayList<>();
        ArrayDeque<AccessibilityNodeInfo> pending = new ArrayDeque<>();
        pending.add(root);
        int visited = 0;
        while (!pending.isEmpty() && visited++ < MAX_NODES) {
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
                        Detection detection = factory.create(text,
                                characterBounds == null ? bounds : characterBounds,
                                match, width, height, "TEXT_SMUT_ACCESSIBILITY_");
                        if (detection != null) result.add(detection);
                    }
                }
                for (int index = 0; index < node.getChildCount(); index++) {
                    AccessibilityNodeInfo child = node.getChild(index);
                    if (child != null) pending.addLast(child);
                }
            } finally {
                if (ownsNode) node.recycle();
            }
        }
        while (!pending.isEmpty()) pending.removeFirst().recycle();
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
}
