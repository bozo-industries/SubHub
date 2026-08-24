package com.subhub.app.detection.text;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lazy, allocation-bounded inference for the bundled Tiny Guard Model2Vec classifier.
 *
 * <p>The exported asset contains a WordPiece vocabulary, weighted 64-dimensional static token
 * embeddings, and a two-layer MLP. Keeping inference here avoids another native runtime and lets
 * High/Ultra reuse one warm model for Accessibility text and OCR.</p>
 */
final class LocalSmutModel {
    private static final String TAG = "LocalSmutModel";
    private static final String ASSET = "smut_model.bin";
    private static final byte[] MAGIC = "SHSMUT1\0".getBytes(StandardCharsets.US_ASCII);
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_TOKENS = 128;
    private static final int MAX_WORD_CHARS = 100;
    private static final int CACHE_SIZE = 512;

    private final Context context;
    private final Map<String, Float> scoreCache = Collections.synchronizedMap(
            new LinkedHashMap<String, Float>(CACHE_SIZE + 1, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Float> eldest) {
                    return size() > CACHE_SIZE;
                }
            });
    private volatile ModelData data;
    private volatile boolean unavailable;

    LocalSmutModel(Context context) {
        this.context = context.getApplicationContext();
    }

    float score(String normalizedText) {
        if (normalizedText == null || normalizedText.isEmpty() || unavailable) return 0f;
        Float cached = scoreCache.get(normalizedText);
        if (cached != null) return cached;
        ModelData model = load();
        if (model == null) return 0f;
        float result = model.score(normalizedText);
        scoreCache.put(normalizedText, result);
        return result;
    }

    boolean isLoaded() {
        return data != null;
    }

    private ModelData load() {
        ModelData current = data;
        if (current != null || unavailable) return current;
        synchronized (this) {
            if (data != null || unavailable) return data;
            try (InputStream input = context.getAssets().open(ASSET)) {
                data = ModelData.read(input);
                Log.i(TAG, "Loaded local semantic text model");
            } catch (IOException | RuntimeException error) {
                unavailable = true;
                Log.w(TAG, "Local semantic text model unavailable; using rules", error);
            }
            return data;
        }
    }

    static final class ModelData {
        private final Map<String, Integer> vocabulary;
        private final short[] embeddings;
        private final float[] firstWeights;
        private final float[] firstBias;
        private final float[] outputWeights;
        private final float[] outputBias;
        private final int dimensions;
        private final int hiddenSize;
        private final int unknownId;

        private ModelData(
                Map<String, Integer> vocabulary,
                short[] embeddings,
                float[] firstWeights,
                float[] firstBias,
                float[] outputWeights,
                float[] outputBias,
                int dimensions,
                int hiddenSize) {
            this.vocabulary = vocabulary;
            this.embeddings = embeddings;
            this.firstWeights = firstWeights;
            this.firstBias = firstBias;
            this.outputWeights = outputWeights;
            this.outputBias = outputBias;
            this.dimensions = dimensions;
            this.hiddenSize = hiddenSize;
            Integer unknown = vocabulary.get("[UNK]");
            unknownId = unknown == null ? 0 : unknown;
        }

        static ModelData read(InputStream input) throws IOException {
            byte[] bytes = readFully(input);
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            for (byte expected : MAGIC) {
                if (!buffer.hasRemaining() || buffer.get() != expected) {
                    throw new IOException("Invalid semantic model header");
                }
            }
            int version = buffer.getInt();
            int vocabularySize = buffer.getInt();
            int dimensions = buffer.getInt();
            int hiddenSize = buffer.getInt();
            if (version != FORMAT_VERSION || vocabularySize < 1 || dimensions < 1
                    || hiddenSize < 1 || vocabularySize > 100_000
                    || dimensions > 1_024 || hiddenSize > 4_096) {
                throw new IOException("Unsupported semantic model dimensions");
            }
            Map<String, Integer> vocabulary = new LinkedHashMap<>(vocabularySize * 4 / 3);
            for (int id = 0; id < vocabularySize; id++) {
                int length = Short.toUnsignedInt(buffer.getShort());
                if (length > buffer.remaining()) throw new IOException("Truncated vocabulary");
                byte[] token = new byte[length];
                buffer.get(token);
                vocabulary.put(new String(token, StandardCharsets.UTF_8), id);
            }
            short[] embeddings = readHalves(buffer, vocabularySize * dimensions);
            float[] firstWeights = readFloatsFromHalves(buffer, dimensions * hiddenSize);
            float[] firstBias = readFloatsFromHalves(buffer, hiddenSize);
            float[] outputWeights = readFloatsFromHalves(buffer, hiddenSize * 2);
            float[] outputBias = readFloatsFromHalves(buffer, 2);
            if (buffer.hasRemaining()) throw new IOException("Unexpected semantic model payload");
            return new ModelData(Collections.unmodifiableMap(vocabulary), embeddings,
                    firstWeights, firstBias, outputWeights, outputBias,
                    dimensions, hiddenSize);
        }

        float score(String normalizedText) {
            int[] tokenIds = tokenize(normalizedText);
            if (tokenIds.length == 0) return 0f;
            float[] sentence = new float[dimensions];
            for (int tokenId : tokenIds) {
                int offset = tokenId * dimensions;
                for (int dimension = 0; dimension < dimensions; dimension++) {
                    sentence[dimension] += halfToFloat(embeddings[offset + dimension]);
                }
            }
            float scale = 1f / tokenIds.length;
            float normSquared = 0f;
            for (int dimension = 0; dimension < dimensions; dimension++) {
                sentence[dimension] *= scale;
                normSquared += sentence[dimension] * sentence[dimension];
            }
            if (normSquared > 0f) {
                float inverseNorm = (float) (1d / Math.sqrt(normSquared));
                for (int dimension = 0; dimension < dimensions; dimension++) {
                    sentence[dimension] *= inverseNorm;
                }
            }

            float[] hidden = new float[hiddenSize];
            for (int hiddenIndex = 0; hiddenIndex < hiddenSize; hiddenIndex++) {
                float value = firstBias[hiddenIndex];
                for (int dimension = 0; dimension < dimensions; dimension++) {
                    value += sentence[dimension]
                            * firstWeights[dimension * hiddenSize + hiddenIndex];
                }
                hidden[hiddenIndex] = Math.max(0f, value);
            }
            float fail = outputBias[0];
            float pass = outputBias[1];
            for (int hiddenIndex = 0; hiddenIndex < hiddenSize; hiddenIndex++) {
                fail += hidden[hiddenIndex] * outputWeights[hiddenIndex * 2];
                pass += hidden[hiddenIndex] * outputWeights[hiddenIndex * 2 + 1];
            }
            float difference = Math.max(-40f, Math.min(40f, pass - fail));
            return (float) (1d / (1d + Math.exp(difference)));
        }

        private int[] tokenize(String text) {
            int[] result = new int[MAX_TOKENS];
            int size = 0;
            int start = 0;
            while (start < text.length() && size < MAX_TOKENS) {
                while (start < text.length() && text.charAt(start) == ' ') start++;
                if (start >= text.length()) break;
                int end = text.indexOf(' ', start);
                if (end < 0) end = text.length();
                if (end - start > MAX_WORD_CHARS) {
                    result[size++] = unknownId;
                } else {
                    int wordStartSize = size;
                    int cursor = start;
                    boolean failed = false;
                    while (cursor < end && size < MAX_TOKENS) {
                        int pieceEnd = end;
                        Integer pieceId = null;
                        while (pieceEnd > cursor) {
                            String piece = text.substring(cursor, pieceEnd);
                            if (cursor > start) piece = "##" + piece;
                            pieceId = vocabulary.get(piece);
                            if (pieceId != null) break;
                            pieceEnd--;
                        }
                        if (pieceId == null) {
                            failed = true;
                            break;
                        }
                        result[size++] = pieceId;
                        cursor = pieceEnd;
                    }
                    if (failed) {
                        size = wordStartSize;
                        if (size < MAX_TOKENS) result[size++] = unknownId;
                    }
                }
                start = end + 1;
            }
            int[] compact = new int[size];
            System.arraycopy(result, 0, compact, 0, size);
            return compact;
        }

        private static byte[] readFully(InputStream input) throws IOException {
            ByteArrayOutputStream output = new ByteArrayOutputStream(4_200_000);
            byte[] chunk = new byte[32 * 1024];
            int read;
            while ((read = input.read(chunk)) >= 0) output.write(chunk, 0, read);
            return output.toByteArray();
        }

        private static short[] readHalves(ByteBuffer buffer, int count) throws IOException {
            if (count < 0 || count > buffer.remaining() / 2) {
                throw new IOException("Truncated semantic model tensor");
            }
            short[] values = new short[count];
            for (int index = 0; index < count; index++) values[index] = buffer.getShort();
            return values;
        }

        private static float[] readFloatsFromHalves(ByteBuffer buffer, int count)
                throws IOException {
            short[] halves = readHalves(buffer, count);
            float[] values = new float[count];
            for (int index = 0; index < count; index++) values[index] = halfToFloat(halves[index]);
            return values;
        }

        static float halfToFloat(short half) {
            int bits = Short.toUnsignedInt(half);
            int sign = (bits & 0x8000) << 16;
            int exponent = (bits >>> 10) & 0x1f;
            int mantissa = bits & 0x03ff;
            int floatBits;
            if (exponent == 0) {
                if (mantissa == 0) return Float.intBitsToFloat(sign);
                exponent = 1;
                while ((mantissa & 0x0400) == 0) {
                    mantissa <<= 1;
                    exponent--;
                }
                mantissa &= 0x03ff;
                floatBits = sign | ((exponent + 112) << 23) | (mantissa << 13);
            } else if (exponent == 31) {
                floatBits = sign | 0x7f800000 | (mantissa << 13);
            } else {
                floatBits = sign | ((exponent + 112) << 23) | (mantissa << 13);
            }
            return Float.intBitsToFloat(floatBits);
        }
    }
}
