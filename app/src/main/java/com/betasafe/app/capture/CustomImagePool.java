package com.betasafe.app.capture;

import android.content.Context;
import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Keeps decoded custom images stable per tracked object and releases them as one unit. */
public final class CustomImagePool implements AutoCloseable {
    private final CustomImageManager manager;
    private final Map<Integer, Integer> assignments = new HashMap<>();
    private List<Bitmap> bitmaps = new ArrayList<>();

    public CustomImagePool(Context context) {
        manager = new CustomImageManager(context);
    }

    public synchronized boolean isEmpty() { return bitmaps.isEmpty(); }

    public synchronized void reload() {
        releaseBitmaps();
        bitmaps = manager.loadEnabledBitmaps(1024);
        assignments.clear();
    }

    public synchronized Bitmap bitmapFor(int trackId) {
        if (bitmaps.isEmpty()) return null;
        Integer index = assignments.get(trackId);
        if (index == null || index >= bitmaps.size()) {
            index = Math.floorMod(trackId, bitmaps.size());
            assignments.put(trackId, index);
        }
        return bitmaps.get(index);
    }

    public synchronized void retainAssignments(Set<Integer> activeIds) {
        assignments.keySet().retainAll(new HashSet<>(activeIds));
    }

    @Override
    public synchronized void close() {
        releaseBitmaps();
        assignments.clear();
    }

    private void releaseBitmaps() {
        for (Bitmap bitmap : bitmaps) if (!bitmap.isRecycled()) bitmap.recycle();
        bitmaps = new ArrayList<>();
    }
}
