package com.betasafe.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.betasafe.app.MainActivity;
import com.betasafe.app.R;
import com.betasafe.app.capture.ScreenCaptureManager;
import com.betasafe.app.detection.Detection;
import com.betasafe.app.detection.DetectionEngine;
import com.betasafe.app.detection.DetectorConfig;
import com.betasafe.app.detection.ObjectTracker;
import com.betasafe.app.detection.TrackedObject;
import com.betasafe.app.overlay.OverlayController;
import com.betasafe.app.settings.SettingsRepository;
import com.betasafe.app.stats.StatsRepository;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Foreground pipeline: capture -> local ONNX inference -> tracking -> touch-through overlay. */
public final class ScreenCaptureService extends Service {
    private static final String TAG = "ScreenCaptureService";
    private static final String CHANNEL_ID = "betasafe_protection";
    private static final int NOTIFICATION_ID = 1701;
    private static final String ACTION_START = "com.betasafe.app.action.START";
    private static final String ACTION_STOP = "com.betasafe.app.action.STOP";
    private static final String EXTRA_RESULT_CODE = "projection_result_code";
    private static final String EXTRA_RESULT_DATA = "projection_result_data";
    private static volatile boolean running;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean processing = new AtomicBoolean();
    private final AtomicBoolean firstFrameReported = new AtomicBoolean();
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsListener =
            (preferences, key) -> reloadSettings();
    private ScheduledExecutorService executor;
    private MediaProjection projection;
    private ScreenCaptureManager capture;
    private DetectionEngine detector;
    private ObjectTracker tracker;
    private OverlayController overlay;
    private SettingsRepository settings;
    private StatsRepository stats;

    public static Intent startIntent(Context context, int resultCode, Intent resultData) {
        return new Intent(context, ScreenCaptureService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, resultData);
    }

    public static Intent stopIntent(Context context) {
        return new Intent(context, ScreenCaptureService.class).setAction(ACTION_STOP);
    }

    public static boolean isRunning() { return running; }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        executor = Executors.newSingleThreadScheduledExecutor();
        settings = new SettingsRepository(this);
        stats = new StatsRepository(this);
        settings.preferences().registerOnSharedPreferenceChangeListener(settingsListener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(intent.getAction()) || running) return START_NOT_STICKY;

        startForeground(NOTIFICATION_ID, buildNotification());
        Intent projectionData = projectionData(intent);
        if (projectionData == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        projection = manager.getMediaProjection(
                intent.getIntExtra(EXTRA_RESULT_CODE, 0), projectionData);
        if (projection == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                stopSelf();
            }
        }, mainHandler);

        running = true;
        stats.startSession();
        overlay = new OverlayController(this);
        overlay.setAppearance(settings.loadAppearance());
        overlay.show();
        executor.execute(this::startPipeline);
        return START_NOT_STICKY;
    }

    private void startPipeline() {
        try {
            DetectorConfig config = settings.loadDetectorConfig();
            detector = new DetectionEngine(this, config);
            detector.initialize();
            tracker = new ObjectTracker(config);

            DisplayInfo display = displayInfo();
            capture = new ScreenCaptureManager(
                    projection,
                    display.width,
                    display.height,
                    display.densityDpi,
                    config.getInferenceResolution());
            capture.start();
            executor.scheduleWithFixedDelay(this::processFrame, 0, 90, TimeUnit.MILLISECONDS);
        } catch (Exception error) {
            Log.e(TAG, "Could not start on-device protection", error);
            stopSelf();
        }
    }

    private void reloadSettings() {
        if (overlay != null) overlay.setAppearance(settings.loadAppearance());
        DetectorConfig config = settings.loadDetectorConfig();
        if (detector != null) detector.setConfig(config);
        if (tracker != null) tracker.setConfig(config);
    }

    private void processFrame() {
        if (!running || !processing.compareAndSet(false, true)) return;
        Bitmap frame = null;
        try {
            frame = capture.acquireLatestFrame();
            if (frame == null) return;
            List<Detection> detections = detector.detect(frame);
            List<TrackedObject> tracks = tracker.update(detections);
            stats.onTracks(tracks);
            int width = capture.getCaptureWidth();
            int height = capture.getCaptureHeight();
            if (firstFrameReported.compareAndSet(false, true)) {
                Log.i(TAG, "First frame processed with "
                        + detector.getActiveModel() + " on " + detector.getActiveProvider()
                        + " in " + detector.getLastInferenceMs() + " ms at "
                        + width + "x" + height);
            }
            mainHandler.post(() -> {
                if (overlay != null) overlay.update(tracks, width, height);
            });
        } catch (Exception error) {
            Log.w(TAG, "Frame processing failed", error);
        } finally {
            if (frame != null) frame.recycle();
            processing.set(false);
        }
    }

    private DisplayInfo displayInfo() {
        WindowManager manager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = manager.getMaximumWindowMetrics().getBounds();
            return new DisplayInfo(bounds.width(), bounds.height(), metrics.densityDpi);
        }
        manager.getDefaultDisplay().getRealMetrics(metrics);
        return new DisplayInfo(metrics.widthPixels, metrics.heightPixels, metrics.densityDpi);
    }

    private Intent projectionData(Intent source) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return source.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        }
        @SuppressWarnings("deprecation")
        Intent value = source.getParcelableExtra(EXTRA_RESULT_DATA);
        return value;
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openApp = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(
                this, 1, stopIntent(this), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(openApp)
                .addAction(0, getString(R.string.notification_stop), stop)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        running = false;
        if (stats != null) stats.endSession();
        if (settings != null) {
            settings.preferences().unregisterOnSharedPreferenceChangeListener(settingsListener);
        }
        if (executor != null) executor.shutdownNow();
        if (capture != null) capture.close();
        if (detector != null) detector.close();
        if (overlay != null) overlay.close();
        if (projection != null) projection.stop();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    private static final class DisplayInfo {
        private final int width;
        private final int height;
        private final int densityDpi;

        private DisplayInfo(int width, int height, int densityDpi) {
            this.width = width;
            this.height = height;
            this.densityDpi = densityDpi;
        }
    }
}
