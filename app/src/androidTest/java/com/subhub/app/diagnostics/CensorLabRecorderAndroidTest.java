package com.subhub.app.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.core.content.FileProvider;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RunWith(AndroidJUnit4.class)
public final class CensorLabRecorderAndroidTest {
    @Test public void recordsSanitizedTraceAndExportsPortableBundle() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        if (CensorLabRecorder.isActive()) CensorLabRecorder.stop(context);

        CensorLabRecorder.SessionState started = CensorLabRecorder.start(context);
        assertTrue(started.active);
        assertNotNull(started.id);
        CensorLabLog.i("ScreenshotA11y", "SCROLL_EVENT id=9 source=absolute dy=120");
        CensorLabLog.i("CensorMotion", "DRAW seq=3 inputToDrawMs=8 viewportLead=0,2");
        CensorLabLog.i("ScreenshotA11y",
                "Recognition activated for foreground package com.example.private");
        CensorLabRecorder.mark("UI\nMARKER");

        CensorLabRecorder.CompletedSession completed = CensorLabRecorder.stop(context);
        assertNotNull(completed);
        assertTrue(completed.manifest.isFile());
        assertTrue(completed.trace.isFile());
        assertTrue(completed.eventCount >= 5);
        String trace = read(completed.trace);
        assertTrue(trace.contains("SCROLL_EVENT"));
        assertTrue(trace.contains("CensorMotion"));
        assertTrue(trace.contains("UI MARKER"));
        assertFalse(trace.contains("com.example.private"));

        JSONObject manifest = new JSONObject(read(completed.manifest));
        assertEquals(1, manifest.getInt("schemaVersion"));
        assertFalse(manifest.getJSONObject("privacy").getBoolean("pixelCapture"));
        assertFalse(manifest.getJSONObject("privacy").getBoolean("ocrTextStored"));
        assertFalse(manifest.getJSONObject("privacy").getBoolean("foregroundPackageStored"));

        File bundle = CensorLabBundleExporter.export(context, completed, null);
        assertTrue(bundle.isFile());
        Set<String> entries = zipEntries(bundle);
        assertEquals(Set.of("manifest.json", "trace.ndjson", "README.txt"), entries);
        assertNotNull(FileProvider.getUriForFile(context,
                context.getPackageName() + ".updates", bundle));

        File videoDirectory = new File(context.getCacheDir(), "censor-lab/exports");
        assertTrue(videoDirectory.isDirectory() || videoDirectory.mkdirs());
        File video = new File(videoDirectory, "test-recording-" + completed.id + ".mp4");
        try (FileOutputStream output = new FileOutputStream(video)) {
            output.write(new byte[]{0, 0, 0, 20, 'f', 't', 'y', 'p'});
        }
        Uri videoUri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".updates", video);
        File videoBundle = CensorLabBundleExporter.export(context, completed, videoUri);
        assertTrue(zipEntries(videoBundle).contains("screen-recording.mp4"));
    }

    private static String read(File file) throws Exception {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line).append('\n');
        }
        return result.toString();
    }

    private static Set<String> zipEntries(File file) throws Exception {
        Set<String> names = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(file))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) names.add(entry.getName());
        }
        return names;
    }
}
