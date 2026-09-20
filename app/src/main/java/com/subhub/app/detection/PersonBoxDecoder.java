package com.subhub.app.detection;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Person-only NanoDet-m-plus 416 decoder. No Android state or native buffer ownership. */
public final class PersonBoxDecoder {
    public static final int INPUT_SIZE = 416;
    public static final int[] STRIDES = {8, 16, 32};
    private static final int CLASSES = 80;
    private static final int BINS = 8;
    private static final int MAX_PEOPLE = 32;

    public static final class Person {
        public final BBox box;
        public final float confidence;

        public Person(BBox box, float confidence) {
            this.box = java.util.Objects.requireNonNull(box);
            this.confidence = confidence;
        }
    }

    /** Exact centered letterbox geometry; dimensions use the upstream demo's integer truncation. */
    public static final class Letterbox {
        public final int sourceWidth, sourceHeight, width, height, left, top;

        public Letterbox(int sourceWidth, int sourceHeight) {
            if (sourceWidth <= 0 || sourceHeight <= 0) {
                throw new IllegalArgumentException("Invalid image dimensions");
            }
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            double scale = INPUT_SIZE / (double) Math.max(sourceWidth, sourceHeight);
            width = Math.max(1, (int) (sourceWidth * scale));
            height = Math.max(1, (int) (sourceHeight * scale));
            left = (INPUT_SIZE - width) / 2;
            top = (INPUT_SIZE - height) / 2;
        }

        BBox toSource(float x1, float y1, float x2, float y2) {
            int x = clamp((int) Math.floor((x1 - left) * sourceWidth / width), sourceWidth);
            int y = clamp((int) Math.floor((y1 - top) * sourceHeight / height), sourceHeight);
            int right = clamp((int) Math.ceil((x2 - left) * sourceWidth / width), sourceWidth);
            int bottom = clamp((int) Math.ceil((y2 - top) * sourceHeight / height), sourceHeight);
            return new BBox(x, y, Math.max(0, right - x), Math.max(0, bottom - y));
        }

        private static int clamp(int value, int limit) { return Math.max(0, Math.min(limit, value)); }
    }

    public List<Person> decode(FloatBuffer[] scores, FloatBuffer[] distances,
            Letterbox geometry, float threshold) {
        if (scores == null || distances == null || scores.length != STRIDES.length
                || distances.length != STRIDES.length || geometry == null
                || !Float.isFinite(threshold) || threshold < 0f || threshold > 1f) {
            throw new IllegalArgumentException("Invalid NanoDet contract");
        }
        List<Person> candidates = new ArrayList<>();
        for (int level = 0; level < STRIDES.length; level++) {
            int stride = STRIDES[level];
            int side = INPUT_SIZE / stride;
            int count = side * side;
            FloatBuffer cls = scores[level], dfl = distances[level];
            if (cls == null || dfl == null || cls.remaining() != count * CLASSES
                    || dfl.remaining() != count * 4 * BINS) {
                throw new IllegalArgumentException("Unexpected NanoDet output shape");
            }
            int clsStart = cls.position(), dflStart = dfl.position();
            for (int index = 0; index < count; index++) {
                float score = cls.get(clsStart + index * CLASSES);
                if (!Float.isFinite(score) || score < threshold || score > 1f) continue;
                boolean personWins = true;
                for (int category = 1; category < CLASSES; category++) {
                    float other = cls.get(clsStart + index * CLASSES + category);
                    if (!Float.isFinite(other) || other > score) { personWins = false; break; }
                }
                if (!personWins) continue;
                int base = dflStart + index * 4 * BINS;
                float l = distance(dfl, base), t = distance(dfl, base + BINS);
                float r = distance(dfl, base + 2 * BINS), b = distance(dfl, base + 3 * BINS);
                if (!Float.isFinite(l + t + r + b)) continue;
                float cx = index % side * stride + (stride - 1) * .5f;
                float cy = index / side * stride + (stride - 1) * .5f;
                BBox box = geometry.toSource(Math.max(0, cx - l * stride),
                        Math.max(0, cy - t * stride), Math.min(INPUT_SIZE, cx + r * stride),
                        Math.min(INPUT_SIZE, cy + b * stride));
                if (box.getArea() > 0) candidates.add(new Person(box, score));
            }
        }
        candidates.sort(Comparator.comparingDouble((Person value) -> value.confidence).reversed());
        List<Person> accepted = new ArrayList<>();
        for (Person candidate : candidates) {
            boolean overlaps = false;
            for (Person existing : accepted) {
                if (candidate.box.intersectionOverUnion(existing.box) > .6f) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) accepted.add(candidate);
            if (accepted.size() == MAX_PEOPLE) break;
        }
        return java.util.Collections.unmodifiableList(accepted);
    }

    private static float distance(FloatBuffer buffer, int offset) {
        float maximum = -Float.MAX_VALUE;
        for (int bin = 0; bin < BINS; bin++) {
            float value = buffer.get(offset + bin);
            if (!Float.isFinite(value)) return Float.NaN;
            maximum = Math.max(maximum, value);
        }
        double total = 0, weighted = 0;
        for (int bin = 0; bin < BINS; bin++) {
            double weight = Math.exp(buffer.get(offset + bin) - maximum);
            total += weight;
            weighted += bin * weight;
        }
        return (float) (weighted / total);
    }
}
