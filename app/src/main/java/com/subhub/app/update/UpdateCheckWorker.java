package com.subhub.app.update;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Performs a short release metadata check and posts at most one alert per version. */
public final class UpdateCheckWorker extends Worker {
    public UpdateCheckWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull @Override public Result doWork() {
        GitHubReleaseRepository.Result result =
                new GitHubReleaseRepository(getApplicationContext()).check();
        if (result.succeeded()) {
            if (result.candidate != null) UpdateNotifications.available(
                    getApplicationContext(), result.candidate);
            return Result.success();
        }
        return result.failure == GitHubReleaseRepository.Failure.OFFLINE
                || result.failure == GitHubReleaseRepository.Failure.SERVER
                ? Result.retry() : Result.success();
    }
}
