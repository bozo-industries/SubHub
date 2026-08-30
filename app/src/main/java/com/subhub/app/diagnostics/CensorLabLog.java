package com.subhub.app.diagnostics;

import android.util.Log;

/** Mirrors selected, sanitized performance log lines into an explicit Censor Lab session. */
public final class CensorLabLog {
    private static final String[] ALLOWED_PREFIXES = {
            "CAPTURE_PHASE", "FAST_DROP", "FRAME_MOTION", "INFERENCE_GATE",
            "OVERLAY_PUBLISH", "QUALITY_", "SCROLL_", "SETTLED_",
            "SOURCE_FRAME_", "STARTUP", "TEXT_"
    };

    private CensorLabLog() {}

    public static int i(String tag, String message) {
        int result = Log.i(tag, message);
        if (CensorLabRecorder.isActive() && allowed(tag, message)) {
            CensorLabRecorder.record(tag, message);
        }
        return result;
    }

    static boolean allowed(String tag, String message) {
        if ("CensorMotion".equals(tag)) return true;
        if (!"ScreenshotA11y".equals(tag) || message == null) return false;
        for (String prefix : ALLOWED_PREFIXES) {
            if (message.startsWith(prefix)) return true;
        }
        return false;
    }
}
