package com.subhub.app.popup;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;

/** Thread-safe image index across user-selected trees and the active pack image folder. */
final class PopupLibrary {
    private final PopupBitmapCache cache;
    private final Random random = new Random();
    private volatile List<String> paths = List.of();

    PopupLibrary(PopupBitmapCache cache) { this.cache = cache; }

    void rescan(String packDirectory, List<String> folders) {
        LinkedHashSet<String> found = new LinkedHashSet<>();
        found.add("asset://popup_storm/guardian_shield.png");
        if (packDirectory != null && !packDirectory.isBlank()) {
            found.addAll(cache.listFolderImages(packDirectory, PopupStormSettings.MAX_FOLDER_IMAGES));
        }
        for (String folder : folders) {
            if (found.size() >= PopupStormSettings.MAX_FOLDER_IMAGES) break;
            found.addAll(cache.listFolderImages(folder,
                    PopupStormSettings.MAX_FOLDER_IMAGES - found.size()));
        }
        paths = Collections.unmodifiableList(new ArrayList<>(found));
    }

    int count() { return paths.size(); }
    boolean isEmpty() { return paths.isEmpty(); }

    BitmapSource randomBitmap() {
        List<String> snapshot = paths;
        if (snapshot.isEmpty()) return null;
        int start = random.nextInt(snapshot.size());
        for (int offset = 0; offset < Math.min(snapshot.size(), 12); offset++) {
            String path = snapshot.get((start + offset) % snapshot.size());
            Bitmap bitmap = cache.get(path);
            if (bitmap != null && !bitmap.isRecycled()) return new BitmapSource(path, bitmap);
        }
        return null;
    }

    static final class BitmapSource {
        final String path;
        final Bitmap bitmap;
        BitmapSource(String path, Bitmap bitmap) { this.path = path; this.bitmap = bitmap; }
    }
}
