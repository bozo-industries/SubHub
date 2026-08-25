package com.subhub.app.update;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.Executors;

/** Copies a completed system download into private storage and verifies it off the main thread. */
public final class UpdateDownloadReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
        UpdateStateStore state = new UpdateStateStore(context);
        if (id < 0 || id != state.downloadId()) return;
        PendingResult pending = goAsync();
        Executors.newSingleThreadExecutor().execute(() -> {
            try { verify(context.getApplicationContext(), id, state); }
            finally { pending.finish(); }
        });
    }

    private static void verify(Context context, long id, UpdateStateStore state) {
        UpdateCandidate candidate = state.candidate();
        UpdateManifest.Asset asset = candidate == null ? null
                : candidate.manifest.selectAsset(android.os.Build.SUPPORTED_ABIS);
        DownloadManager manager = context.getSystemService(DownloadManager.class);
        Uri source = manager.getUriForDownloadedFile(id);
        if (candidate == null || asset == null || source == null) {
            state.clearDownload(true);
            UpdateNotifications.failed(context);
            return;
        }
        File destination = new File(state.updateDirectory(), asset.name);
        try (InputStream raw = context.getContentResolver().openInputStream(source);
                BufferedInputStream input = new BufferedInputStream(raw);
                BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(destination))) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        } catch (Exception exception) {
            destination.delete();
            state.clearDownload(false);
            UpdateNotifications.failed(context);
            return;
        } finally {
            manager.remove(id);
        }
        UpdateVerifier.Result result = UpdateVerifier.verify(context, destination, candidate.manifest, asset);
        if (result.succeeded()) {
            state.setVerifiedPath(destination.getAbsolutePath());
            state.setDownloadId(-1L);
            UpdateNotifications.ready(context, candidate);
        } else {
            destination.delete();
            state.clearDownload(false);
            UpdateNotifications.failed(context);
        }
    }
}
