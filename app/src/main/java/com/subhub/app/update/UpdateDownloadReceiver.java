package com.subhub.app.update;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
/** Copies a completed system download into private storage and verifies it off the main thread. */
public final class UpdateDownloadReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
        UpdateStateStore state = new UpdateStateStore(context);
        if (id < 0 || id != state.downloadId()) return;
        PendingResult pending = goAsync();
        new Thread(() -> {
            try { UpdateDownloadFinalizer.finish(context, id); }
            finally { pending.finish(); }
        }, "SubHub-update-finalizer").start();
    }
}
