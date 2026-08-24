package com.subhub.app.detection;

import java.util.Objects;

/** Immutable pixel-space bounding box. */
public final class BBox {
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    public BBox(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getRight() { return x + width; }
    public int getBottom() { return y + height; }
    public int getCenterX() { return x + width / 2; }
    public int getCenterY() { return y + height / 2; }
    public long getArea() { return (long) width * height; }

    public BBox padded(float ratio, int frameWidth, int frameHeight) {
        int padX = (int) (width * Math.max(0f, ratio));
        int padY = (int) (height * Math.max(0f, ratio));
        int left = Math.max(0, x - padX);
        int top = Math.max(0, y - padY);
        int right = Math.min(frameWidth, getRight() + padX);
        int bottom = Math.min(frameHeight, getBottom() + padY);
        return new BBox(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
    }

    public float intersectionOverUnion(BBox other) {
        int left = Math.max(x, other.x);
        int top = Math.max(y, other.y);
        int right = Math.min(getRight(), other.getRight());
        int bottom = Math.min(getBottom(), other.getBottom());
        if (right <= left || bottom <= top) {
            return 0f;
        }
        long intersection = (long) (right - left) * (bottom - top);
        long union = getArea() + other.getArea() - intersection;
        return union > 0 ? (float) intersection / union : 0f;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof BBox)) return false;
        BBox other = (BBox) value;
        return x == other.x && y == other.y && width == other.width && height == other.height;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, width, height);
    }

    @Override
    public String toString() {
        return "BBox{" + x + ',' + y + ',' + width + 'x' + height + '}';
    }
}
