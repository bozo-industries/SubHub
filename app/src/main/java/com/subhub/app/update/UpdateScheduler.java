package com.subhub.app.update;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Owns the single periodic and foreground-stale update checks. */
public final class UpdateScheduler {
    public static final long INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(6);
    static final String PERIODIC_NAME = "subhub-release-check";
    static final String IMMEDIATE_NAME = "subhub-release-check-now";

    private UpdateScheduler() {}

    public static void synchronize(Context context) {
        UpdateStateStore state = new UpdateStateStore(context);
        WorkManager manager = WorkManager.getInstance(context);
        if (!state.automaticChecks()) {
            manager.cancelUniqueWork(PERIODIC_NAME);
            return;
        }
        Constraints network = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(
                UpdateCheckWorker.class, 6, TimeUnit.HOURS)
                .setConstraints(network).build();
        manager.enqueueUniquePeriodicWork(PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP, periodic);
        if (System.currentTimeMillis() - state.lastCheck() >= INTERVAL_MILLIS) checkNow(context);
    }

    public static void checkNow(Context context) {
        Constraints network = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(UpdateCheckWorker.class)
                .setConstraints(network).build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_NAME, ExistingWorkPolicy.REPLACE, request);
    }
}
