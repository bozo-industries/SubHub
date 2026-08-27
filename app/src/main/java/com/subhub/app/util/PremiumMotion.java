package com.subhub.app.util;

import android.app.Dialog;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;

import com.subhub.app.R;

/** Shared, restrained motion and depth for SubHub's modal surfaces. */
public final class PremiumMotion {
    private PremiumMotion() {}

    public static void styleDialog(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setWindowAnimations(R.style.Animation_SubHub_Dialog);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            WindowManager manager = window.getWindowManager();
            if (manager != null && manager.isCrossWindowBlurEnabled()) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                WindowManager.LayoutParams attributes = window.getAttributes();
                attributes.setBlurBehindRadius(22);
                window.setAttributes(attributes);
            }
        }
    }
}
