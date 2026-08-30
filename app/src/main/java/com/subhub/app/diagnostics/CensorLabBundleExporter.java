package com.subhub.app.diagnostics;

import android.content.Context;
import android.net.Uri;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds one portable, secret-free lab bundle with an optional system screen recording. */
public final class CensorLabBundleExporter {
    private static final int COPY_BUFFER = 64 * 1024;

    private CensorLabBundleExporter() {}

    public static File export(Context context, CensorLabRecorder.CompletedSession session,
            Uri video) throws IOException {
        if (session == null || !session.manifest.isFile() || !session.trace.isFile()) {
            throw new IOException("No completed Censor Lab session is available");
        }
        File outputDirectory = new File(context.getCacheDir(), "censor-lab/exports");
        if (!outputDirectory.mkdirs() && !outputDirectory.isDirectory()) {
            throw new IOException("Could not create the diagnostics export directory");
        }
        pruneExports(outputDirectory);
        File output = new File(outputDirectory, "subhub-censor-lab-" + session.id + '-'
                + System.currentTimeMillis() + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(
                new FileOutputStream(output), COPY_BUFFER))) {
            zip.setLevel(Deflater.BEST_SPEED);
            JSONObject manifest;
            byte[] manifestBytes;
            try {
                manifest = new JSONObject(readUtf8(session.manifest));
                manifest.put("videoAttached", video != null);
                manifestBytes = (manifest.toString(2) + "\n")
                        .getBytes(StandardCharsets.UTF_8);
            } catch (Exception error) {
                throw new IOException("Could not read the diagnostics manifest", error);
            }
            addBytes(zip, "manifest.json", manifestBytes);
            addFile(zip, "trace.ndjson", session.trace);
            addBytes(zip, "README.txt", (
                    "SubHub Censor Lab bundle\n"
                    + "trace.ndjson contains sanitized timing and geometry telemetry only.\n"
                    + "screen-recording.mp4 is present only when explicitly selected.\n"
                    + "Use manifest elapsed timestamps and CensorLab SYNC markers to align video.\n")
                    .getBytes(StandardCharsets.UTF_8));
            if (video != null) {
                try (InputStream stream = new BufferedInputStream(
                        context.getContentResolver().openInputStream(video), COPY_BUFFER)) {
                    if (stream == null) throw new IOException("Could not open the selected recording");
                    addStream(zip, "screen-recording.mp4", stream);
                }
            }
        }
        return output;
    }

    private static void pruneExports(File directory) {
        File[] exports = directory.listFiles(file -> file.isFile()
                && file.getName().startsWith("subhub-censor-lab-")
                && file.getName().endsWith(".zip"));
        if (exports == null || exports.length < 3) return;
        Arrays.sort(exports, Comparator.comparingLong(File::lastModified).reversed());
        for (int index = 2; index < exports.length; index++) exports[index].delete();
    }

    private static void addFile(ZipOutputStream zip, String name, File file) throws IOException {
        try (InputStream stream = new BufferedInputStream(
                new FileInputStream(file), COPY_BUFFER)) {
            addStream(zip, name, stream);
        }
    }

    private static String readUtf8(File input) throws IOException {
        try (FileInputStream stream = new FileInputStream(input);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                if (read > 0) bytes.write(buffer, 0, read);
            }
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static void addBytes(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static void addStream(ZipOutputStream zip, String name, InputStream stream)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        byte[] buffer = new byte[COPY_BUFFER];
        int read;
        while ((read = stream.read(buffer)) >= 0) {
            if (read > 0) zip.write(buffer, 0, read);
        }
        zip.closeEntry();
    }
}
