package com.subhub.app.detection;

import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * One immutable packed-pixel snapshot, shared by model-specific input adapters. Contains no
 * Bitmap/native ownership: a delayed optional model cannot retain or race a recycled screenshot.
 */
public final class SharedModelImage {
    private final int[] argb;
    public final int width, height;

    private SharedModelImage(int[] ownedPixels, int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height != ownedPixels.length) {
            throw new IllegalArgumentException("Invalid shared image dimensions");
        }
        argb = ownedPixels;
        this.width = width;
        this.height = height;
    }

    /** Caller transfers exclusive array ownership and must never mutate it after this call. */
    public static SharedModelImage takeOwnership(int[] pixels, int width, int height) {
        return new SharedModelImage(java.util.Objects.requireNonNull(pixels), width, height);
    }

    /** Detaches only real image pixels from a reusable top-left-letterboxed model buffer. */
    public static SharedModelImage copyContent(int[] pixels, int stride, int width, int height) {
        if (width <= 0 || height <= 0 || stride < width || pixels == null
                || (long) stride * height > pixels.length) {
            throw new IllegalArgumentException("Invalid shared content rectangle");
        }
        int[] compact = new int[Math.multiplyExact(width, height)];
        for (int y = 0; y < height; y++) {
            System.arraycopy(pixels, y * stride, compact, y * width, width);
        }
        return takeOwnership(compact, width, height);
    }

    /** Single bitmap readback; the returned image remains valid after the bitmap is released. */
    public static SharedModelImage read(android.graphics.Bitmap bitmap) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] pixels = new int[Math.multiplyExact(width, height)];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        return takeOwnership(pixels, width, height);
    }

    /** Exact copy fast path used when capture already has the model's content dimensions. */
    public void copyTopLeft(int[] target, int targetWidth, int targetHeight) {
        if (targetWidth < width || targetHeight < height
                || target.length != (long) targetWidth * targetHeight) {
            throw new IllegalArgumentException("Shared image does not fit input");
        }
        Arrays.fill(target, 0xff000000);
        for (int row = 0; row < height; row++) {
            System.arraycopy(argb, row * width, target, row * targetWidth, width);
        }
    }

    /**
     * Centered RGB CHW input matching the OpenCV Zoo demo (which converts BGR to RGB before
     * subtracting these means). Area averaging for downscaling; bilinear for upscaling.
     * Writes into a reusable direct tensor, without an intermediate bitmap or float array.
     */
    public void writePersonTensor(FloatBuffer target, PersonBoxDecoder.Letterbox geometry) {
        final int size = PersonBoxDecoder.INPUT_SIZE, plane = size * size;
        if (target.capacity() != 3 * plane) throw new IllegalArgumentException("Invalid input buffer");
        for (int i = 0; i < plane; i++) {
            target.put(i, -103.53f / 57.375f);
            target.put(plane + i, -116.28f / 57.12f);
            target.put(2 * plane + i, -123.675f / 58.395f);
        }
        for (int y = 0; y < geometry.height; y++) {
            for (int x = 0; x < geometry.width; x++) {
                int pixel = sample(x, y, geometry.width, geometry.height);
                int i = (y + geometry.top) * size + x + geometry.left;
                target.put(i, (((pixel >>> 16) & 255) - 103.53f) / 57.375f);
                target.put(plane + i, (((pixel >>> 8) & 255) - 116.28f) / 57.12f);
                target.put(2 * plane + i, ((pixel & 255) - 123.675f) / 58.395f);
            }
        }
        target.position(0);
        target.limit(3 * plane);
    }

    private int sample(int x, int y, int targetWidth, int targetHeight) {
        if (targetWidth == width && targetHeight == height) return argb[y * width + x];
        if (targetWidth <= width && targetHeight <= height) {
            double left = x * (double) width / targetWidth;
            double right = (x + 1) * (double) width / targetWidth;
            double top = y * (double) height / targetHeight;
            double bottom = (y + 1) * (double) height / targetHeight;
            double red = 0, green = 0, blue = 0;
            for (int sy = (int) top; sy < Math.ceil(bottom); sy++) {
                double wy = Math.min(bottom, sy + 1) - Math.max(top, sy);
                for (int sx = (int) left; sx < Math.ceil(right); sx++) {
                    double wx = Math.min(right, sx + 1) - Math.max(left, sx);
                    int pixel = argb[Math.min(height - 1, sy) * width + Math.min(width - 1, sx)];
                    double weight = wx * wy;
                    red += ((pixel >>> 16) & 255) * weight;
                    green += ((pixel >>> 8) & 255) * weight;
                    blue += (pixel & 255) * weight;
                }
            }
            // The reference resizes uint8 images before normalizing to float.
            double area = (right - left) * (bottom - top);
            return pack(red / area, green / area, blue / area);
        }
        double sx = Math.max(0, Math.min(width - 1, (x + .5) * width / targetWidth - .5));
        double sy = Math.max(0, Math.min(height - 1, (y + .5) * height / targetHeight - .5));
        int x0 = (int) sx, y0 = (int) sy;
        int x1 = Math.min(width - 1, x0 + 1), y1 = Math.min(height - 1, y0 + 1);
        double fx = sx - x0, fy = sy - y0;
        int a = argb[y0 * width + x0], b = argb[y0 * width + x1];
        int c = argb[y1 * width + x0], d = argb[y1 * width + x1];
        return pack(blend(a, b, c, d, 16, fx, fy), blend(a, b, c, d, 8, fx, fy),
                blend(a, b, c, d, 0, fx, fy));
    }

    private static double blend(int a, int b, int c, int d, int shift, double x, double y) {
        return (((a >>> shift) & 255) * (1 - x) + ((b >>> shift) & 255) * x) * (1 - y)
                + (((c >>> shift) & 255) * (1 - x) + ((d >>> shift) & 255) * x) * y;
    }

    private static int pack(double red, double green, double blue) {
        return 0xff000000 | ((int) Math.rint(red) << 16) | ((int) Math.rint(green) << 8)
                | (int) Math.rint(blue);
    }
}
