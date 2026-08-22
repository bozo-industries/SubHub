package com.betasafe.app;

import android.app.Application;

import com.betasafe.app.pack.PackManager;

/** Application root for the source-level BetaSafe reconstruction. */
public final class BetaSafeApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        new PackManager(this);
    }
}
