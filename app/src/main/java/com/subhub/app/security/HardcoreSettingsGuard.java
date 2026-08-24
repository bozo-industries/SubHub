package com.subhub.app.security;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import android.widget.Toast;

import com.subhub.app.R;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Touch-blocking accessibility badges over SubHub's destructive Android Settings actions. */
public final class HardcoreSettingsGuard {
    private static final String TAG = "HardcoreSettingsGuard";
    static final String SETTINGS_PACKAGE = "com.android.settings";
    private static final int MAX_NODES = 500;
    private static final int MAX_BADGES = 4;

    private final AccessibilityService service;
    private final Context overlayContext;
    private final WindowManager windows;
    private final List<View> badges = new ArrayList<>();
    private final List<Rect> guardedBounds = new ArrayList<>();

    public HardcoreSettingsGuard(AccessibilityService service) {
        this.service = service;
        overlayContext = createOverlayContext(service);
        windows = overlayContext.getSystemService(WindowManager.class);
    }

    private static Context createOverlayContext(AccessibilityService service) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return service;
        }
        DisplayManager displays = service.getSystemService(DisplayManager.class);
        Display primary = displays == null ? null : displays.getDisplay(Display.DEFAULT_DISPLAY);
        if (primary == null) return service;
        // A single display-bound window context gives TYPE_ACCESSIBILITY_OVERLAY its own valid
        // token. Reuse it for the service lifetime: creating one per Settings event is costly.
        return service.createDisplayContext(primary).createWindowContext(
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null);
    }

    public void refresh(String foregroundPackage, AccessibilityNodeInfo root) {
        boolean hardcore = new HardcoreModeManager(service).isEnabled();
        boolean domMode = ControllerPinManager.isDomModeActive();
        // The accessibility tree can be very large in scrolling feeds. Reject every non-Settings
        // event before walking it; the guard must be effectively free during normal recognition.
        if (!shouldGuard(hardcore, domMode, foregroundPackage)) {
            clear();
            return;
        }
        // A null tree is a transient Settings handoff, so retain the last verified bounds for one
        // refresh. A real non-SubHub page must clear immediately or stale badges remain stranded.
        if (root == null) {
            return;
        }
        if (!isSubHubPage(root)) {
            clear();
            return;
        }
        List<Rect> controls = new ArrayList<>();
        collectControls(root, controls, new int[]{0});
        updateBadges(controls);
    }

    public void clear() {
        for (View badge : badges) {
            try { windows.removeViewImmediate(badge); } catch (RuntimeException ignored) {}
        }
        badges.clear();
        guardedBounds.clear();
    }

    private static boolean sameBounds(List<Rect> left, List<Rect> right) {
        int size = Math.min(MAX_BADGES, left.size());
        if (size != right.size()) return false;
        for (int index = 0; index < size; index++) {
            if (!left.get(index).equals(right.get(index))) return false;
        }
        return true;
    }

    private void updateBadges(List<Rect> controls) {
        List<Rect> clipped = new ArrayList<>();
        for (int index = 0; index < Math.min(MAX_BADGES, controls.size()); index++) {
            Rect bounds = clipBounds(controls.get(index));
            if (bounds != null) clipped.add(bounds);
        }
        if (sameBounds(clipped, guardedBounds)) return;
        if (clipped.size() == badges.size()) {
            try {
                for (int index = 0; index < clipped.size(); index++) {
                    windows.updateViewLayout(badges.get(index), layoutParams(clipped.get(index)));
                }
                guardedBounds.clear();
                for (Rect bounds : clipped) guardedBounds.add(new Rect(bounds));
                return;
            } catch (RuntimeException ignored) {
                // Rebuild below if Settings replaced the underlying window during navigation.
            }
        }
        clear();
        for (Rect bounds : clipped) {
            if (showBadge(bounds)) guardedBounds.add(new Rect(bounds));
        }
    }

    static boolean shouldGuard(boolean hardcore, boolean domMode, String foregroundPackage) {
        return hardcore && !domMode && isSystemSettingsPackage(foregroundPackage);
    }

    public static boolean isSettingsPackage(String packageName) {
        return isSystemSettingsPackage(packageName);
    }

    private static boolean isSystemSettingsPackage(String packageName) {
        String value = packageName == null ? "" : packageName.toLowerCase(Locale.ROOT);
        if (SETTINGS_PACKAGE.equals(value)) return true;
        boolean trustedAndroidNamespace = value.startsWith("com.android.")
                || value.startsWith("com.google.android.");
        return trustedAndroidNamespace && (value.contains("settings")
                || value.endsWith("permissioncontroller"));
    }

    static int overlayFlags() {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
    }

    static boolean isTargetControl(String viewId, CharSequence text, CharSequence description) {
        String id = normalize(viewId);
        if (id.contains("uninstall") || id.contains("clear_data")
                || id.contains("clear_storage") || id.contains("delete_data")) return true;
        String label = normalize(join(text, description));
        String[] targets = {
                "uninstall", "deinstallieren", "desinstaller", "desinstalar",
                "disinstalla", "アンインストール", "제거", "удалить", "卸载", "解除安裝",
                "clear data", "clear storage", "delete data", "daten loschen",
                "speicherinhalt loschen", "effacer les donnees", "borrar datos",
                "limpar armazenamento", "limpar dados", "cancella dati",
                "データを消去", "데이터 삭제", "стереть данные", "清除数据", "清除資料"
        };
        for (String target : targets) if (label.contains(target)) return true;
        return false;
    }

    private boolean isSubHubPage(AccessibilityNodeInfo root) {
        return containsAppIdentity(root, new int[]{0});
    }

    private boolean containsAppIdentity(AccessibilityNodeInfo node, int[] count) {
        if (node == null || count[0]++ >= MAX_NODES) return false;
        String value = normalize(join(node.getText(), node.getContentDescription()));
        String viewId = normalize(node.getViewIdResourceName());
        if (value.contains("subhub") || value.contains(service.getPackageName())
                || viewId.contains(service.getPackageName())) return true;
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) continue;
            try {
                if (containsAppIdentity(child, count)) return true;
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    private void collectControls(AccessibilityNodeInfo node, List<Rect> controls, int[] count) {
        if (node == null || count[0]++ >= MAX_NODES || controls.size() >= MAX_BADGES) return;
        if (node.isVisibleToUser() && isTargetControl(node.getViewIdResourceName(),
                node.getText(), node.getContentDescription())) {
            Rect bounds = clickableBounds(node);
            if (bounds.width() >= dp(48) && bounds.height() >= dp(32)) addUnique(controls, bounds);
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child == null) continue;
            try { collectControls(child, controls, count); }
            finally { child.recycle(); }
        }
    }

    private Rect clickableBounds(AccessibilityNodeInfo node) {
        Rect best = new Rect();
        node.getBoundsInScreen(best);
        AccessibilityNodeInfo cursor = null;
        try {
            cursor = node.getParent();
            for (int depth = 0; cursor != null && depth < 3; depth++) {
                Rect candidate = new Rect();
                cursor.getBoundsInScreen(candidate);
                if (cursor.isClickable() && candidate.width() > 0 && candidate.height() > 0) {
                    best.set(candidate);
                    break;
                }
                AccessibilityNodeInfo parent = cursor.getParent();
                cursor.recycle();
                cursor = parent;
            }
        } finally {
            if (cursor != null) cursor.recycle();
        }
        return best;
    }

    private void addUnique(List<Rect> controls, Rect candidate) {
        for (Rect existing : controls) {
            Rect overlap = new Rect();
            if (overlap.setIntersect(existing, candidate)
                    && overlap.width() * overlap.height() >= candidate.width() * candidate.height() / 2) {
                return;
            }
        }
        controls.add(new Rect(candidate));
    }

    private boolean showBadge(Rect rawBounds) {
        Rect bounds = clipBounds(rawBounds);
        if (bounds == null) return false;

        TextView badge = new TextView(overlayContext);
        badge.setText(R.string.hardcore_settings_badge);
        badge.setTextColor(Color.WHITE);
        badge.setTextSize(11f);
        badge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF24112F, 0xFF4A145F});
        background.setCornerRadius(dp(12));
        background.setStroke(dp(2), 0xFF9A35D0);
        badge.setBackground(background);
        badge.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                Toast.makeText(service, R.string.hardcore_settings_blocked,
                        Toast.LENGTH_SHORT).show();
            }
            return true;
        });

        try {
            windows.addView(badge, layoutParams(bounds));
            badges.add(badge);
            return true;
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not attach Hardcore Settings guard", error);
            return false;
        }
    }

    private Rect clipBounds(Rect rawBounds) {
        android.util.DisplayMetrics display = service.getResources().getDisplayMetrics();
        Rect bounds = new Rect(
                Math.max(0, rawBounds.left), Math.max(0, rawBounds.top),
                Math.min(display.widthPixels, rawBounds.right),
                Math.min(display.heightPixels, rawBounds.bottom));
        return bounds.width() > 0 && bounds.height() > 0 ? bounds : null;
    }

    private WindowManager.LayoutParams layoutParams(Rect bounds) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                bounds.width(), bounds.height(), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                overlayFlags(),
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = bounds.left;
        params.y = bounds.top;
        params.setTitle("SubHub Hardcore guard");
        return params;
    }

    private int dp(int value) {
        return Math.round(value * service.getResources().getDisplayMetrics().density);
    }

    private static String join(CharSequence first, CharSequence second) {
        return String.valueOf(first == null ? "" : first) + " "
                + String.valueOf(second == null ? "" : second);
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}
