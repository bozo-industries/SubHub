package com.betasafe.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.betasafe.app.pack.PackManager;
import com.betasafe.app.util.LocaleHelper;

/** Application root for the source-level BetaSafe reconstruction. */
public final class BetaSafeApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityPreCreated(@NonNull Activity activity, @Nullable Bundle state) {
                WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
            }

            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle state) {
                View content = activity.findViewById(android.R.id.content);
                int left = content.getPaddingLeft();
                int top = content.getPaddingTop();
                int right = content.getPaddingRight();
                int bottom = content.getPaddingBottom();
                ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
                    androidx.core.graphics.Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                    view.setPadding(left + bars.left, top + bars.top,
                            right + bars.right, bottom + bars.bottom);
                    return insets;
                });
                ViewCompat.requestApplyInsets(content);
            }

            @Override public void onActivityStarted(@NonNull Activity activity) { }
            @Override public void onActivityResumed(@NonNull Activity activity) { }
            @Override public void onActivityPaused(@NonNull Activity activity) { }
            @Override public void onActivityStopped(@NonNull Activity activity) { }
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity,
                    @NonNull Bundle outState) { }
            @Override public void onActivityDestroyed(@NonNull Activity activity) { }
        });
        LocaleHelper.applySaved(this);
        new PackManager(this);
    }
}
