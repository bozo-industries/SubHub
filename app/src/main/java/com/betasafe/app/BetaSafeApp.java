package com.betasafe.app;

import android.app.Application;

import com.betasafe.app.pack.PackManager;
import com.betasafe.app.util.LocaleHelper;

/** Application root for the source-level BetaSafe reconstruction. */
public final class BetaSafeApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        LocaleHelper.applySaved(this);
        new PackManager(this);
    }
}
