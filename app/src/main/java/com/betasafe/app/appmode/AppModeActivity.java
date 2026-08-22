package com.betasafe.app.appmode;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.CompoundButtonCompat;

import com.betasafe.app.R;
import com.betasafe.app.databinding.ActivityAppModeBinding;
import com.betasafe.app.service.ScreenshotAccessibilityService;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Styled launcher-app picker and lifecycle controls for accessibility app mode. */
public final class AppModeActivity extends AppCompatActivity {
    private ActivityAppModeBinding binding;
    private AppModeManager manager;
    private final Set<String> selectedPackages = new LinkedHashSet<>();
    private final ExecutorService loader = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAppModeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        manager = new AppModeManager(this);
        selectedPackages.addAll(manager.getSelectedPackages());
        binding.armed.setChecked(manager.isArmed());
        binding.autoResume.setChecked(manager.isAutoResumeEnabled());
        binding.modeGroup.check(manager.getMode() == AppModePolicy.Mode.SELECTED_APPS
                ? R.id.mode_selected : R.id.mode_always);
        binding.buttonBack.setOnClickListener(view -> finish());
        binding.buttonAccessibilitySettings.setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        binding.buttonSave.setOnClickListener(view -> save());
        binding.modeGroup.setOnCheckedChangeListener((group, checkedId) -> renderMode());
        renderMode();
        loadApps();
    }

    @Override protected void onResume() {
        super.onResume();
        renderServiceStatus();
    }

    private void save() {
        AppModePolicy.Mode mode = binding.modeSelected.isChecked()
                ? AppModePolicy.Mode.SELECTED_APPS : AppModePolicy.Mode.ALWAYS;
        if (mode == AppModePolicy.Mode.SELECTED_APPS && selectedPackages.isEmpty()) {
            Toast.makeText(this, R.string.app_mode_select_one, Toast.LENGTH_SHORT).show();
            return;
        }
        manager.save(binding.armed.isChecked(), mode, binding.autoResume.isChecked(),
                selectedPackages);
        if (binding.armed.isChecked()) ResumeNotificationManager.show(this);
        else ResumeNotificationManager.cancel(this);
        Toast.makeText(this, R.string.app_mode_saved, Toast.LENGTH_SHORT).show();
        renderServiceStatus();
    }

    private void renderMode() {
        boolean selected = binding.modeSelected.isChecked();
        binding.appListCard.setVisibility(selected ? View.VISIBLE : View.GONE);
    }

    private void renderServiceStatus() {
        int status;
        if (!accessibilityEnabled()) status = R.string.app_mode_status_permission_off;
        else if (ScreenshotAccessibilityService.isRecognitionActive()) {
            status = R.string.app_mode_status_recognizing;
        } else if (ScreenshotAccessibilityService.isRunning()) {
            status = R.string.app_mode_status_waiting;
        } else status = R.string.app_mode_status_reconnecting;
        binding.serviceStatus.setText(status);
    }

    private boolean accessibilityEnabled() {
        AccessibilityManager accessibility = getSystemService(AccessibilityManager.class);
        for (AccessibilityServiceInfo service : accessibility
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (service.getResolveInfo() != null
                    && getPackageName().equals(service.getResolveInfo().serviceInfo.packageName)) {
                return true;
            }
        }
        return false;
    }

    private void loadApps() {
        loader.execute(() -> {
            PackageManager packages = getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            @SuppressWarnings("deprecation")
            List<ResolveInfo> resolved = packages.queryIntentActivities(
                    launcher, PackageManager.MATCH_ALL);
            Map<String, AppEntry> unique = new LinkedHashMap<>();
            for (ResolveInfo info : resolved) {
                if (info.activityInfo == null
                        || getPackageName().equals(info.activityInfo.packageName)) continue;
                String packageName = info.activityInfo.packageName;
                if (unique.containsKey(packageName)) continue;
                CharSequence label = info.loadLabel(packages);
                Drawable icon;
                try { icon = info.loadIcon(packages); }
                catch (RuntimeException ignored) { icon = packages.getDefaultActivityIcon(); }
                unique.put(packageName, new AppEntry(
                        label == null ? packageName : label.toString(), packageName, icon));
            }
            List<AppEntry> entries = new ArrayList<>(unique.values());
            Collator collator = Collator.getInstance(Locale.getDefault());
            entries.sort((left, right) -> collator.compare(left.label, right.label));
            runOnUiThread(() -> renderApps(entries));
        });
    }

    private void renderApps(List<AppEntry> entries) {
        if (binding == null) return;
        binding.loadingApps.setVisibility(View.GONE);
        binding.appList.removeAllViews();
        Set<String> installed = new LinkedHashSet<>();
        for (AppEntry entry : entries) {
            installed.add(entry.packageName);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(4), 0, dp(4));

            ImageView icon = new ImageView(this);
            icon.setImageDrawable(entry.icon);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(42), dp(42));
            iconParams.setMarginEnd(dp(10));
            row.addView(icon, iconParams);

            CheckBox check = new CheckBox(this);
            check.setText(entry.label + "\n" + entry.packageName);
            check.setTextColor(getColor(R.color.text_primary));
            check.setTextSize(12f);
            check.setChecked(selectedPackages.contains(entry.packageName));
            CompoundButtonCompat.setButtonTintList(check,
                    ColorStateList.valueOf(getColor(R.color.accent)));
            check.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selectedPackages.add(entry.packageName);
                else selectedPackages.remove(entry.packageName);
                renderSelectedCount();
            });
            row.addView(check, new LinearLayout.LayoutParams(0, dp(58), 1f));
            binding.appList.addView(row);
        }
        selectedPackages.retainAll(installed);
        renderSelectedCount();
    }

    private void renderSelectedCount() {
        if (binding != null) binding.selectedCount.setText(getString(
                R.string.app_mode_selected_count, selectedPackages.size()));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        loader.shutdownNow();
        binding = null;
        super.onDestroy();
    }

    private static final class AppEntry {
        private final String label;
        private final String packageName;
        private final Drawable icon;

        private AppEntry(String label, String packageName, Drawable icon) {
            this.label = label;
            this.packageName = packageName;
            this.icon = icon;
        }
    }
}
