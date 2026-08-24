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
        if (!match.isMatched()) return null;
        BBox projected = TextRegionProjector.project(text, match,
                source.left, source.top, source.right, source.bottom, width, height);
        if (projected == null) return null;
        return new Detection(
                "TEXT_SMUT_" + match.getCategory().toUpperCase(java.util.Locale.ROOT),
                "text_smut",
                match.getConfidence(),
                projected,
                true,
                false);
    }
}
