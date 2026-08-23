package com.betasafe.app.appmode;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Gravity;
import android.view.accessibility.AccessibilityManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.CompoundButtonCompat;

import com.betasafe.app.R;
import com.betasafe.app.databinding.ActivityAppModeBinding;
import com.betasafe.app.service.ScreenshotAccessibilityService;
import com.betasafe.app.security.ControllerPinGate;
import com.betasafe.app.security.ControllerPinManager;
import com.betasafe.app.security.HardcoreModeManager;
import com.betasafe.app.security.ControllerEditMode;
import com.betasafe.app.util.SubHubNavigation;

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
    private AppTimerManager timers;
    private WeeklyScheduleManager schedule;
    private int scheduleStartMinute;
    private int scheduleEndMinute;
    private final Set<String> selectedPackages = new LinkedHashSet<>();
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private boolean editingUnlocked;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAppModeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        if (SubHubNavigation.redirectIfDisabled(this, SubHubNavigation.Screen.LIMITS)) return;
        manager = new AppModeManager(this);
        timers = new AppTimerManager(this);
        schedule = new WeeklyScheduleManager(this);
        SubHubNavigation.bind(this, binding.getRoot(), SubHubNavigation.Screen.LIMITS);
        selectedPackages.addAll(manager.getSelectedPackages());
        binding.armed.setChecked(manager.isArmed());
        binding.autoResume.setChecked(manager.isAutoResumeEnabled());
        binding.modeGroup.check(manager.getMode() == AppModePolicy.Mode.SELECTED_APPS
                ? R.id.mode_selected : R.id.mode_always);
        AppTimerManager.Settings timerSettings = timers.loadSettings();
        binding.perAppLimitEnabled.setChecked(timerSettings.perAppEnabled);
        binding.perAppLimitMinutes.setText(String.valueOf(timerSettings.perAppMinutes));
        binding.totalLimitEnabled.setChecked(timerSettings.totalEnabled);
        binding.totalLimitMinutes.setText(String.valueOf(timerSettings.totalMinutes));
        WeeklyScheduleManager.Settings scheduleSettings = schedule.load();
        binding.scheduleEnabled.setChecked(scheduleSettings.enabled);
        setSelectedDays(scheduleSettings.dayMask);
        scheduleStartMinute = scheduleSettings.startMinute;
        scheduleEndMinute = scheduleSettings.endMinute;
        binding.perAppLimitEnabled.setOnCheckedChangeListener((button, checked) ->
                renderTimerControls());
        binding.totalLimitEnabled.setOnCheckedChangeListener((button, checked) ->
                renderTimerControls());
        binding.scheduleEnabled.setOnCheckedChangeListener((button, checked) ->
                renderScheduleControls());
        binding.scheduleStart.setOnClickListener(view -> pickScheduleTime(true));
        binding.scheduleEnd.setOnClickListener(view -> pickScheduleTime(false));
        binding.buttonBack.setOnClickListener(view -> finish());
        binding.buttonEditLock.setOnClickListener(view -> toggleEditSession());
        binding.buttonAccessibilitySettings.setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        binding.buttonSave.setOnClickListener(view -> save());
        binding.modeGroup.setOnCheckedChangeListener((group, checkedId) -> renderMode());
        renderMode();
        renderTimerControls();
        renderScheduleControls();
        renderTimerUsage();
        loadApps();
        applyEditState();
    }

    @Override protected void onResume() {
        super.onResume();
        renderServiceStatus();
        applyEditState();
    }

    private void toggleEditSession() {
        if (ControllerPinManager.isSessionUnlocked()) {
            ControllerEditMode.enterSubMode(this);
        } else ControllerPinGate.require(this, this::applyEditState, false);
    }

    private void applyEditState() {
        if (binding == null) return;
        editingUnlocked = ControllerPinManager.isSessionUnlocked();
        ControllerEditMode.renderButton(this, binding.buttonEditLock);
        View[] editable = {binding.buttonAccessibilitySettings, binding.armed,
                binding.modeAlways, binding.modeSelected, binding.autoResume, binding.buttonSave,
                binding.scheduleEnabled, binding.perAppLimitEnabled, binding.totalLimitEnabled};
        for (View view : editable) view.setEnabled(editingUnlocked);
        boolean hardcore = new HardcoreModeManager(this).isEnabled();
        if (hardcore) {
            binding.armed.setChecked(true);
            binding.autoResume.setChecked(true);
            binding.armed.setEnabled(false);
            binding.autoResume.setEnabled(false);
        }
        renderTimerControls();
        renderScheduleControls();
        for (int index = 0; index < binding.appList.getChildCount(); index++) {
            setEnabledRecursive(binding.appList.getChildAt(index), editingUnlocked);
        }
    }

    private static void setEnabledRecursive(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (!(view instanceof android.view.ViewGroup)) return;
        android.view.ViewGroup group = (android.view.ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            setEnabledRecursive(group.getChildAt(index), enabled);
        }
    }

    private void save() {
        AppModePolicy.Mode mode = binding.modeSelected.isChecked()
                ? AppModePolicy.Mode.SELECTED_APPS : AppModePolicy.Mode.ALWAYS;
        boolean watchedAppsRequired = mode == AppModePolicy.Mode.SELECTED_APPS
                || binding.perAppLimitEnabled.isChecked()
                || binding.totalLimitEnabled.isChecked();
        if (watchedAppsRequired && selectedPackages.isEmpty()) {
            Toast.makeText(this, R.string.app_mode_select_one, Toast.LENGTH_SHORT).show();
            return;
        }
        Integer perAppMinutes = readMinutes(binding.perAppLimitMinutes,
                binding.perAppLimitEnabled.isChecked());
        Integer totalMinutes = readMinutes(binding.totalLimitMinutes,
                binding.totalLimitEnabled.isChecked());
        if (perAppMinutes == null || totalMinutes == null) {
            Toast.makeText(this, R.string.app_timer_invalid_minutes, Toast.LENGTH_SHORT).show();
            return;
        }
        int selectedDays = selectedDays();
        if (binding.scheduleEnabled.isChecked() && selectedDays == 0) {
            Toast.makeText(this, R.string.app_schedule_select_day, Toast.LENGTH_SHORT).show();
            return;
        }
        boolean hardcore = new HardcoreModeManager(this).isEnabled();
        manager.save(hardcore || binding.armed.isChecked(), mode,
                hardcore || binding.autoResume.isChecked(),
                selectedPackages);
        schedule.save(binding.scheduleEnabled.isChecked(), selectedDays,
                scheduleStartMinute, scheduleEndMinute);
        timers.saveSettings(binding.perAppLimitEnabled.isChecked(), perAppMinutes,
                binding.totalLimitEnabled.isChecked(), totalMinutes);
        if (binding.armed.isChecked()) ResumeNotificationManager.show(this);
        else ResumeNotificationManager.cancel(this);
        Toast.makeText(this, R.string.app_mode_saved, Toast.LENGTH_SHORT).show();
        binding.buttonSave.setText(R.string.app_mode_saved_button);
        binding.buttonSave.postDelayed(() -> {
            if (binding != null) binding.buttonSave.setText(R.string.app_mode_save);
        }, 1400L);
        renderServiceStatus();
        boolean accessibilityRequired = binding.armed.isChecked()
                || binding.scheduleEnabled.isChecked()
                || binding.perAppLimitEnabled.isChecked()
                || binding.totalLimitEnabled.isChecked();
        if (accessibilityRequired && !accessibilityEnabled()) {
            Toast.makeText(this, R.string.app_mode_enable_prompt, Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
    }

    private void renderMode() {
        binding.appListCard.setVisibility(View.VISIBLE);
        binding.timerCard.setVisibility(View.VISIBLE);
    }

    private void renderTimerControls() {
        binding.perAppLimitMinutes.setEnabled(
                editingUnlocked && binding.perAppLimitEnabled.isChecked());
        binding.totalLimitMinutes.setEnabled(
                editingUnlocked && binding.totalLimitEnabled.isChecked());
        binding.perAppLimitMinutes.setAlpha(binding.perAppLimitEnabled.isChecked() ? 1f : 0.5f);
        binding.totalLimitMinutes.setAlpha(binding.totalLimitEnabled.isChecked() ? 1f : 0.5f);
    }

    private void renderScheduleControls() {
        boolean enabled = binding.scheduleEnabled.isChecked();
        binding.scheduleDays.setAlpha(enabled ? 1f : 0.45f);
        for (int index = 0; index < binding.scheduleDays.getChildCount(); index++) {
            binding.scheduleDays.getChildAt(index).setEnabled(editingUnlocked && enabled);
        }
        binding.scheduleStart.setEnabled(editingUnlocked && enabled);
        binding.scheduleEnd.setEnabled(editingUnlocked && enabled);
        binding.scheduleStart.setAlpha(enabled ? 1f : 0.45f);
        binding.scheduleEnd.setAlpha(enabled ? 1f : 0.45f);
        binding.scheduleStart.setText(formatTime(scheduleStartMinute));
        binding.scheduleEnd.setText(formatTime(scheduleEndMinute));
    }

    private void pickScheduleTime(boolean start) {
        int value = start ? scheduleStartMinute : scheduleEndMinute;
        new TimePickerDialog(this, (picker, hour, minute) -> {
            int selected = hour * 60 + minute;
            if (start) scheduleStartMinute = selected;
            else scheduleEndMinute = selected;
            renderScheduleControls();
        }, value / 60, value % 60, true).show();
    }

    private String formatTime(int minute) {
        int safe = WeeklySchedulePolicy.sanitizeMinute(minute);
        return String.format(Locale.getDefault(), "%02d:%02d", safe / 60, safe % 60);
    }

    private int selectedDays() {
        int mask = 0;
        CheckBox[] days = {binding.scheduleMonday, binding.scheduleTuesday,
                binding.scheduleWednesday, binding.scheduleThursday, binding.scheduleFriday,
                binding.scheduleSaturday, binding.scheduleSunday};
        for (int index = 0; index < days.length; index++) {
            if (days[index].isChecked()) mask |= 1 << index;
        }
        return mask;
    }

    private void setSelectedDays(int mask) {
        CheckBox[] days = {binding.scheduleMonday, binding.scheduleTuesday,
                binding.scheduleWednesday, binding.scheduleThursday, binding.scheduleFriday,
                binding.scheduleSaturday, binding.scheduleSunday};
        for (int index = 0; index < days.length; index++) {
            days[index].setChecked((mask & (1 << index)) != 0);
        }
    }

    private void renderTimerUsage() {
        AppTimerManager.UsageSnapshot usage = timers.snapshot("", System.currentTimeMillis());
        if (usage.totalUsedMillis <= 0L) {
            binding.timerUsageStatus.setText(R.string.app_timer_usage_none);
        } else {
            binding.timerUsageStatus.setText(getString(R.string.app_timer_usage_total,
                    formatUsage(usage.totalUsedMillis)));
        }
    }

    private Integer readMinutes(EditText input, boolean required) {
        String value = input.getText() == null ? "" : input.getText().toString().trim();
        if (!required) {
            try {
                return AppTimerManager.sanitizeMinutes(Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }
        try {
            int minutes = Integer.parseInt(value);
            return minutes >= 1 && minutes <= 1440 ? minutes : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String formatUsage(long millis) {
        long totalMinutes = Math.max(0L, millis) / 60_000L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        return hours > 0L ? hours + "h " + minutes + "m" : minutes + "m";
    }

    private void renderServiceStatus() {
        int status;
        if (!accessibilityEnabled()) status = R.string.app_mode_status_permission_off;
        else if (ScreenshotAccessibilityService.isRecognitionActive()) {
            status = R.string.app_mode_status_recognizing;
        } else if (schedule != null && schedule.isActive(System.currentTimeMillis())) {
            status = R.string.app_mode_status_schedule_waiting;
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
        int columns = Math.max(2, getResources().getInteger(R.integer.app_picker_columns));
        int tileHeight = dp(92);
        for (int index = 0; index < entries.size(); index++) {
            AppEntry entry = entries.get(index);
            installed.add(entry.packageName);
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(7), dp(5), dp(7), dp(5));
            tile.setContentDescription(entry.label + ", " + entry.packageName);

            FrameLayout iconArea = new FrameLayout(this);
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(entry.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(38), dp(38),
                    Gravity.CENTER);
            iconArea.addView(icon, iconParams);

            CheckBox check = new CheckBox(this);
            check.setChecked(selectedPackages.contains(entry.packageName));
            check.setEnabled(editingUnlocked);
            check.setPadding(0, 0, 0, 0);
            CompoundButtonCompat.setButtonTintList(check,
                    ColorStateList.valueOf(getColor(R.color.accent)));
            FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(dp(28), dp(28),
                    Gravity.END | Gravity.TOP);
            iconArea.addView(check, checkParams);
            tile.addView(iconArea, new LinearLayout.LayoutParams(dp(72), dp(44)));

            TextView label = new TextView(this);
            label.setText(entry.label);
            label.setTextColor(getColor(R.color.text_primary));
            label.setTextSize(11f);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tile.addView(label, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(36)));

            Runnable renderSelection = () -> tile.setBackgroundResource(check.isChecked()
                    ? R.drawable.bg_app_picker_tile_selected : R.drawable.bg_app_picker_tile);
            renderSelection.run();
            check.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selectedPackages.add(entry.packageName);
                else selectedPackages.remove(entry.packageName);
                renderSelection.run();
                renderSelectedCount();
            });
            tile.setOnClickListener(view -> {
                if (editingUnlocked) check.setChecked(!check.isChecked());
            });
            GridLayout.LayoutParams tileParams = new GridLayout.LayoutParams(
                    GridLayout.spec(index / columns), GridLayout.spec(index % columns, 1f));
            tileParams.width = 0;
            tileParams.height = tileHeight;
            tileParams.setMargins(dp(3), dp(3), dp(3), dp(3));
            binding.appList.addView(tile, tileParams);
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
