package com.betasafe.app.detection.text;

import com.betasafe.app.detection.BBox;
import com.betasafe.app.detection.Detection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fuses native-text and visual boxes before tracking to prevent duplicate strikes. */
public final class DetectionFusion {
    private DetectionFusion() {}

    @SafeVarargs
    public static List<Detection> merge(List<Detection> visual, List<Detection>... textSources) {
        List<Detection> merged = new ArrayList<>(visual == null
                ? Collections.emptyList() : visual);
        if (textSources == null) return merged;
        for (List<Detection> source : textSources) {
            if (source == null) continue;
            for (Detection candidate : source) addOrFuse(merged, candidate);
        }
        coalesceTextRegions(merged);
        return merged;
    }

    private static void coalesceTextRegions(List<Detection> merged) {
        boolean changed;
        do {
            changed = false;
            for (int firstIndex = 0; firstIndex < merged.size() && !changed; firstIndex++) {
                Detection first = merged.get(firstIndex);
                if (!isText(first)) continue;
                for (int secondIndex = firstIndex + 1; secondIndex < merged.size(); secondIndex++) {
                    Detection second = merged.get(secondIndex);
                    if (!isText(second) || !shouldFuse(first, second)) continue;
                    merged.set(firstIndex, mergedDetection(first, second));
                    merged.remove(secondIndex);
                    changed = true;
                    break;
                }
            }
        } while (changed);
    }

    private static void addOrFuse(List<Detection> merged, Detection candidate) {
        if (candidate == null) return;
        for (int index = 0; index < merged.size(); index++) {
            Detection existing = merged.get(index);
            if (!shouldFuse(existing, candidate)) continue;
            merged.set(index, mergedDetection(existing, candidate));
            return;
        }
        merged.add(candidate);
    }

    private static Detection mergedDetection(Detection first, Detection second) {
        BBox fused = fusedBox(first, second);
        Detection preferred = isText(first) ? second : first;
        return new Detection(
                preferred.getClassName(),
                preferred.getCategory(),
                Math.max(first.getConfidence(), second.getConfidence()),
                fused,
                first.isNsfw() || second.isNsfw(),
                first.isExposed() || second.isExposed());
    }

    private static boolean isText(Detection detection) {
        return detection != null && "text_smut".equals(detection.getCategory());
    }

    private static BBox fusedBox(Detection first, Detection second) {
        boolean textPair = "text_smut".equals(first.getCategory())
                && "text_smut".equals(second.getCategory());
        if (textPair) {
            long intersection = intersection(first.getBox(), second.getBox());
            long firstArea = first.getBox().getArea();
            long secondArea = second.getBox().getArea();
            long smaller = Math.min(firstArea, secondArea);
            long larger = Math.max(firstArea, secondArea);
            float containment = smaller == 0 ? 0f : (float) intersection / smaller;
            // A coarse parent Accessibility description must never enlarge a precise child line.
            if (containment >= 0.70f && larger >= smaller * 2L) {
                return firstArea <= secondArea ? first.getBox() : second.getBox();
            }
        }
        return union(first.getBox(), second.getBox());
    }

    private static boolean shouldFuse(Detection first, Detection second) {
        boolean textPair = "text_smut".equals(first.getCategory())
                && "text_smut".equals(second.getCategory());
        long intersection = intersection(first.getBox(), second.getBox());
        long smaller = Math.min(first.getBox().getArea(), second.getBox().getArea());
        float containment = smaller == 0 ? 0f : (float) intersection / smaller;
        if (containment >= (textPair ? 0.25f : 0.70f)) return true;
        if (!textPair) return false;
        BBox a = first.getBox();
        BBox b = second.getBox();
        int verticalGap = Math.max(0, Math.max(a.getY(), b.getY()) - Math.min(a.getBottom(), b.getBottom()));
        int horizontalOverlap = Math.min(a.getRight(), b.getRight()) - Math.max(a.getX(), b.getX());
        return verticalGap <= 24 && horizontalOverlap > Math.min(a.getWidth(), b.getWidth()) / 3;
    }

    private static long intersection(BBox first, BBox second) {
        int left = Math.max(first.getX(), second.getX());
        int top = Math.max(first.getY(), second.getY());
        int right = Math.min(first.getRight(), second.getRight());
        int bottom = Math.min(first.getBottom(), second.getBottom());
        return right <= left || bottom <= top ? 0L : (long) (right - left) * (bottom - top);
    }

    private static BBox union(BBox first, BBox second) {
        int left = Math.min(first.getX(), second.getX());
        int top = Math.min(first.getY(), second.getY());
        int right = Math.max(first.getRight(), second.getRight());
        int bottom = Math.max(first.getBottom(), second.getBottom());
        return new BBox(left, top, right - left, bottom - top);
    }
}
