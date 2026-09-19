package com.subhub.app.service;

import android.content.Context;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.json.JSONException;

/** Worker-only, bounded atomic storage under no-backup app-private storage. No network or raw data. */
final class ScrollProfileRepository {
    private final AtomicFile file;
    private List<ScrollProfileCodec.Entry> entries = new ArrayList<>();
    private boolean loaded;
    private int rejectedOnLoad;
    private boolean incompleteLoad;

    ScrollProfileRepository(Context context) {
        file = new AtomicFile(new File(context.getApplicationContext().getNoBackupFilesDir(),
                "scroll-learning-v1.json"));
    }

    /** Returned profiles are candidates for fresh validation, never immediate motion authority. */
    ScrollCalibrationLearner.Profile candidate(ScrollLearningKey key, long nowWallMs) {
        load(nowWallMs);
        for (ScrollProfileCodec.Entry entry : entries) {
            if (entry.profile.key.equals(key) && ScrollProfileCodec.valid(entry, nowWallMs)) return entry.profile;
        }
        return null;
    }

    boolean save(ScrollCalibrationLearner.Profile profile, boolean durableSurface, long nowWallMs) {
        if (!durableSurface) return false;
        ScrollProfileCodec.Entry value = new ScrollProfileCodec.Entry(profile, nowWallMs);
        if (!ScrollProfileCodec.valid(value, nowWallMs)) return false;
        load(nowWallMs);
        List<ScrollProfileCodec.Entry> next = new ArrayList<>();
        next.add(value);
        for (ScrollProfileCodec.Entry entry : entries) {
            if (!entry.profile.key.equals(profile.key) && ScrollProfileCodec.valid(entry, nowWallMs)) next.add(entry);
        }
        next.sort(Comparator.comparingLong((ScrollProfileCodec.Entry entry) -> entry.trainedAtWallMs).reversed());
        if (next.size() > ScrollProfileCodec.MAX_ENTRIES) next = new ArrayList<>(next.subList(0, ScrollProfileCodec.MAX_ENTRIES));
        return write(next, nowWallMs);
    }

    boolean remove(ScrollLearningKey key, long nowWallMs) {
        load(nowWallMs);
        List<ScrollProfileCodec.Entry> next = new ArrayList<>();
        for (ScrollProfileCodec.Entry entry : entries) {
            if (!entry.profile.key.equals(key) && ScrollProfileCodec.valid(entry, nowWallMs)) next.add(entry);
        }
        return write(next, nowWallMs);
    }

    private void load(long nowWallMs) {
        if (loaded) return;
        loaded = true;
        try (InputStream stream = file.openRead()) {
            ScrollProfileCodec.Decoded decoded = ScrollProfileCodec.decode(readBounded(stream), nowWallMs);
            entries = new ArrayList<>(decoded.entries);
            rejectedOnLoad = decoded.rejected;
            incompleteLoad = decoded.malformed || decoded.unsupported || decoded.truncated;
        } catch (java.io.FileNotFoundException absent) {
            entries.clear();
        } catch (IOException failure) {
            entries.clear(); incompleteLoad = true;
        }
    }

    private boolean write(List<ScrollProfileCodec.Entry> next, long nowWallMs) {
        FileOutputStream output = null;
        try {
            byte[] bytes = ScrollProfileCodec.encode(next, nowWallMs).getBytes(StandardCharsets.UTF_8);
            if (bytes.length > ScrollProfileCodec.MAX_BYTES) return false;
            output = file.startWrite();
            output.write(bytes);
            file.finishWrite(output);
            output = null;
            try (InputStream verified = file.openRead()) {
                if (!readBounded(verified).equals(new String(bytes, StandardCharsets.UTF_8))) return false;
            }
            entries = new ArrayList<>(next);
            return true;
        } catch (IOException | JSONException | IllegalArgumentException failure) {
            if (output != null) file.failWrite(output);
            return false;
        }
    }

    static String readBounded(InputStream stream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] block = new byte[4096];
        int count;
        while ((count = stream.read(block)) != -1) {
            if (buffer.size() > ScrollProfileCodec.MAX_BYTES - count) throw new IOException("Profile file too large");
            buffer.write(block, 0, count);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    int rejectedOnLoad() { return rejectedOnLoad; }
    boolean incompleteLoad() { return incompleteLoad; }
}
