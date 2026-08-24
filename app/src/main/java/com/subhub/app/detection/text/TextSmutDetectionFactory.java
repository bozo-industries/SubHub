package com.subhub.app.detection.text;

import android.graphics.Rect;

import com.subhub.app.detection.BBox;
import com.subhub.app.detection.Detection;

/** Converts a classified text rectangle into the existing visual-tracker contract. */
final class TextSmutDetectionFactory {
    private final SmutTextClassifier classifier;

    TextSmutDetectionFactory(SmutTextClassifier classifier) {
        this.classifier = classifier;
    }

    Detection create(String text, Rect source, TextSmutConfig config, int width, int height) {
        return create(text, source, config, width, height, false);
    }

    Detection create(
            String text,
            Rect source,
            TextSmutConfig config,
            int width,
            int height,
            boolean semanticEnabled) {
        if (source == null || source.isEmpty() || width <= 0 || height <= 0) return null;
        SmutTextClassifier.Match match = classifier.classify(text, config, semanticEnabled);
        return create(text, source, match, width, height, "TEXT_SMUT_");
    }

    SmutTextClassifier.Match classify(
            String text, TextSmutConfig config, boolean semanticEnabled) {
        return classifier.classify(text, config, semanticEnabled);
    }

    Detection create(
            String text,
            Rect source,
            SmutTextClassifier.Match match,
            int width,
            int height,
            String sourcePrefix) {
        if (source == null || source.isEmpty() || width <= 0 || height <= 0
                || match == null) return null;
        if (!match.isMatched()) return null;
        BBox projected = TextRegionProjector.project(text, match,
                source.left, source.top, source.right, source.bottom, width, height);
        if (projected == null) return null;
        return create(projected, match, sourcePrefix);
    }

    /** Uses provider-supplied rendered line geometry without estimating the line a second time. */
    Detection createExact(
            Rect source,
            SmutTextClassifier.Match match,
            int width,
            int height,
            String sourcePrefix) {
        if (source == null || source.isEmpty() || width <= 0 || height <= 0
                || match == null || !match.isMatched()) return null;
        int left = clamp(source.left, 0, width - 1);
        int top = clamp(source.top, 0, height - 1);
        int right = clamp(source.right, left + 1, width);
        int bottom = clamp(source.bottom, top + 1, height);
        return create(new BBox(left, top, right - left, bottom - top), match, sourcePrefix);
    }

    private static Detection create(
            BBox box, SmutTextClassifier.Match match, String sourcePrefix) {
        return new Detection(
                sourcePrefix + match.getCategory().toUpperCase(java.util.Locale.ROOT),
                "text_smut",
                match.getConfidence(),
                box,
                true,
                false);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
