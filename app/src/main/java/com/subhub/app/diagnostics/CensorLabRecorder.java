package com.subhub.app.diagnostics;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import com.subhub.app.BuildConfig;
import com.subhub.app.detection.DetectorConfig;
import com.subhub.app.settings.SettingsRepository;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Explicit, internal-only telemetry session. It never captures pixels or reads system logcat. */
public final class CensorLabRecorder {
    private static final String PREFERENCES = "censor_lab_sessions";
    private static final String KEY_LATEST = "latest_session";
    private static final String SESSION_ROOT = "censor-lab/sessions";
    private static final String TRACE_FILE = "trace.ndjson";
    private static final String MANIFEST_FILE = "manifest.json";
    private static final int MAX_FIELD_LENGTH = 2_048;
    private static final Object LOCK = new Object();
    private static final AtomicLong EVENT_SEQUENCE = new AtomicLong();
    private static volatile ActiveSession active;
    private static volatile CompletedSession latestCompleted;

    private CensorLabRecorder() {}

    public static boolean isActive() {
        return active != null;
    }

    public static String activeSessionId() {
        ActiveSession value = active;
        return value == null ? null : value.id;
    }

    public static SessionState state(Context context) {
        ActiveSession value = active;
        if (value != null) {
            return new SessionState(true, value.id, value.startedWallMillis,
                    value.buffer.size(), value.buffer.dropped(), null);
        }
        CompletedSession latest = latest(context);
        return latest == null
                ? new SessionState(false, null, 0L, 0, 0L, null)
                : new SessionState(false, latest.id, latest.startedWallMillis,
                        latest.eventCount, latest.droppedEvents, latest);
    }

    public static SessionState start(Context context) throws IOException {
        Context appContext = context.getApplicationContext();
        synchronized (LOCK) {
            if (active != null) return state(appContext);
            String id = shortId();
            File directory = sessionDirectory(appContext, id);
            if (!directory.mkdirs() && !directory.isDirectory()) {
                throw new IOException("Could not create the Censor Lab session directory");
            }
            ActiveSession created = new ActiveSession(id, directory,
                    SystemClock.elapsedRealtimeNanos(), System.currentTimeMillis());
            active = created;
            add(created, "CensorLab", "SYNC_START session=" + id);
            return state(appContext);
        }
    }

    public static void mark(String marker) {
        record("CensorLab", "MARKER " + sanitize(marker, "unknown"));
    }

    public static void record(String tag, String message) {
        ActiveSession value = active;
        if (value == null) return;
        add(value, tag, message);
    }

    public static CompletedSession stop(Context context) throws IOException {
        Context appContext = context.getApplicationContext();
        ActiveSession ending;
        synchronized (LOCK) {
            ending = active;
            if (ending == null) return latest(appContext);
            add(ending, "CensorLab", "SYNC_STOP session=" + ending.id);
            active = null;
        }
        long stoppedElapsedNanos = SystemClock.elapsedRealtimeNanos();
        long stoppedWallMillis = System.currentTimeMillis();
        List<CensorLabEventBuffer.Event> events = ending.buffer.snapshot();
        File trace = new File(ending.directory, TRACE_FILE);
        writeTrace(trace, events);
        File manifest = new File(ending.directory, MANIFEST_FILE);
        writeManifest(appContext, ending, stoppedElapsedNanos, stoppedWallMillis,
                events.size(), ending.buffer.dropped(), manifest);
        CompletedSession completed = new CompletedSession(ending.id, ending.directory,
                manifest, trace, ending.startedWallMillis, stoppedWallMillis,
                events.size(), ending.buffer.dropped());
        latestCompleted = completed;
        appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(KEY_LATEST, ending.id).apply();
        pruneCompletedSessions(appContext, ending.id);
        return completed;
    }

    public static CompletedSession latest(Context context) {
        CompletedSession cached = latestCompleted;
        if (cached != null && cached.manifest.isFile() && cached.trace.isFile()) return cached;
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        String id = preferences.getString(KEY_LATEST, null);
        if (!validId(id)) return null;
        File directory = sessionDirectory(context.getApplicationContext(), id);
        File manifest = new File(directory, MANIFEST_FILE);
        File trace = new File(directory, TRACE_FILE);
        if (!manifest.isFile() || !trace.isFile()) return null;
        try {
            JSONObject stored = new JSONObject(readUtf8(manifest));
            CompletedSession restored = new CompletedSession(id, directory, manifest, trace,
                    stored.optLong("startedWallMillis"), stored.optLong("stoppedWallMillis"),
                    stored.optInt("eventCount"), stored.optLong("droppedEvents"));
            latestCompleted = restored;
            return restored;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void add(ActiveSession session, String tag, String message) {
        session.buffer.offer(new CensorLabEventBuffer.Event(
                EVENT_SEQUENCE.incrementAndGet(), SystemClock.elapsedRealtimeNanos(),
                System.currentTimeMillis(), sanitize(Thread.currentThread().getName(), "thread"),
                sanitize(tag, "Unknown"), sanitize(message, "")));
    }

    private static void writeTrace(File output, List<CensorLabEventBuffer.Event> events)
            throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(output), StandardCharsets.UTF_8), 64 * 1024)) {
            for (CensorLabEventBuffer.Event event : events) {
                try {
                    JSONObject line = new JSONObject()
                            .put("sequence", event.sequence)
                            .put("elapsedNanos", event.elapsedNanos)
                            .put("wallMillis", event.wallMillis)
                            .put("thread", event.thread)
                            .put("tag", event.tag)
                            .put("message", event.message);
                    writer.write(line.toString());
                    writer.newLine();
                } catch (JSONException error) {
                    throw new IOException("Could not encode Censor Lab telemetry", error);
                }
            }
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

    private static void writeManifest(Context context, ActiveSession session,
            long stoppedElapsedNanos, long stoppedWallMillis, int eventCount,
            long droppedEvents, File output) throws IOException {
        try {
            SettingsRepository settings = new SettingsRepository(context);
            DetectorConfig config = settings.loadDetectorConfig();
            DiagnosticsRepository.Snapshot runtime = DiagnosticsRepository.snapshot();
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            float refreshRate = 0f;
            WindowManager windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            // This recorder deliberately uses the application Context so it can finish after the
            // Diagnostics activity is gone. Application Context#getDisplay() throws on newer
            // Android releases; WindowManager's default display remains valid for this manifest.
            Display display = windows == null ? null : windows.getDefaultDisplay();
            if (display != null) refreshRate = display.getRefreshRate();
            List<String> categories = new ArrayList<>(config.getEnabledCategories());
            Collections.sort(categories);
            JSONArray enabledCategories = new JSONArray();
            for (String category : categories) enabledCategories.put(category);
            JSONObject manifest = new JSONObject()
                    .put("schemaVersion", 1)
                    .put("sessionId", session.id)
                    .put("startedElapsedNanos", session.startedElapsedNanos)
                    .put("stoppedElapsedNanos", stoppedElapsedNanos)
                    .put("startedWallMillis", session.startedWallMillis)
                    .put("stoppedWallMillis", stoppedWallMillis)
                    .put("eventCount", eventCount)
                    .put("droppedEvents", droppedEvents)
                    .put("videoAttached", false)
                    .put("app", new JSONObject()
                            .put("versionName", BuildConfig.VERSION_NAME)
                            .put("versionCode", BuildConfig.VERSION_CODE))
                    .put("device", new JSONObject()
                            .put("androidSdk", Build.VERSION.SDK_INT)
                            .put("manufacturer", sanitize(Build.MANUFACTURER, "Unknown"))
                            .put("model", sanitize(Build.MODEL, "Unknown"))
                            .put("displayWidth", metrics.widthPixels)
                            .put("displayHeight", metrics.heightPixels)
                            .put("densityDpi", metrics.densityDpi)
                            .put("refreshRateHz", Math.round(refreshRate * 100f) / 100f))
                    .put("capture", new JSONObject()
                            .put("runtimeMode", runtime.getMode())
                            .put("provider", runtime.getProvider())
                            .put("model", runtime.getModel())
                            .put("preset", settings.loadDetectionPreset().preferenceValue())
                            .put("inferenceResolution", config.getInferenceResolution())
                            .put("captureScale", config.getCaptureScale())
                            .put("detectionIntervalMs", config.getDetectionIntervalMs())
                            .put("enabledCategories", enabledCategories))
                    .put("privacy", new JSONObject()
                            .put("pixelCapture", false)
                            .put("ocrTextStored", false)
                            .put("foregroundPackageStored", false)
                            .put("transport", "Android share sheet only"));
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(output), StandardCharsets.UTF_8))) {
                writer.write(manifest.toString(2));
                writer.newLine();
            }
        } catch (JSONException error) {
            throw new IOException("Could not encode the Censor Lab manifest", error);
        }
    }

    private static File sessionDirectory(Context context, String id) {
        return new File(new File(context.getFilesDir(), SESSION_ROOT), id);
    }

    private static void pruneCompletedSessions(Context context, String keepId) {
        File root = new File(context.getFilesDir(), SESSION_ROOT);
        File[] listed = root.listFiles(file -> file.isDirectory() && validId(file.getName()));
        if (listed == null || listed.length <= 5) return;
        List<File> sessions = new ArrayList<>();
        Collections.addAll(sessions, listed);
        sessions.sort((left, right) -> {
            if (left.getName().equals(keepId)) return -1;
            if (right.getName().equals(keepId)) return 1;
            return Long.compare(sessionModified(right), sessionModified(left));
        });
        for (int index = 5; index < sessions.size(); index++) {
            File session = sessions.get(index);
            File manifest = new File(session, MANIFEST_FILE);
            File trace = new File(session, TRACE_FILE);
            if (manifest.isFile()) manifest.delete();
            if (trace.isFile()) trace.delete();
            session.delete();
        }
    }

    private static long sessionModified(File directory) {
        File manifest = new File(directory, MANIFEST_FILE);
        return manifest.isFile() ? manifest.lastModified() : directory.lastModified();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10)
                .toLowerCase(Locale.ROOT);
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[a-f0-9]{10}");
    }

    private static String sanitize(String value, String fallback) {
        String safe = value == null ? fallback : value.replaceAll("[\\r\\n\\t]", " ").trim();
        if (safe == null || safe.isEmpty()) safe = fallback;
        if (safe == null) safe = "";
        return safe.substring(0, Math.min(MAX_FIELD_LENGTH, safe.length()));
    }

    private static final class ActiveSession {
        final String id;
        final File directory;
        final long startedElapsedNanos;
        final long startedWallMillis;
        final CensorLabEventBuffer buffer = new CensorLabEventBuffer();

        ActiveSession(String id, File directory, long startedElapsedNanos,
                long startedWallMillis) {
            this.id = id;
            this.directory = directory;
            this.startedElapsedNanos = startedElapsedNanos;
            this.startedWallMillis = startedWallMillis;
        }
    }

    public static final class SessionState {
        public final boolean active;
        public final String id;
        public final long startedWallMillis;
        public final int eventCount;
        public final long droppedEvents;
        public final CompletedSession completed;

        SessionState(boolean active, String id, long startedWallMillis, int eventCount,
                long droppedEvents, CompletedSession completed) {
            this.active = active;
            this.id = id;
            this.startedWallMillis = startedWallMillis;
            this.eventCount = eventCount;
            this.droppedEvents = droppedEvents;
            this.completed = completed;
        }
    }

    public static final class CompletedSession {
        public final String id;
        public final File directory;
        public final File manifest;
        public final File trace;
        public final long startedWallMillis;
        public final long stoppedWallMillis;
        public final int eventCount;
        public final long droppedEvents;

        CompletedSession(String id, File directory, File manifest, File trace,
                long startedWallMillis, long stoppedWallMillis, int eventCount,
                long droppedEvents) {
            this.id = id;
            this.directory = directory;
            this.manifest = manifest;
            this.trace = trace;
            this.startedWallMillis = startedWallMillis;
            this.stoppedWallMillis = stoppedWallMillis;
            this.eventCount = eventCount;
            this.droppedEvents = droppedEvents;
        }
    }
}
