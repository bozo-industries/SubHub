package com.subhub.app.popup;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.subhub.app.R;
import com.subhub.app.databinding.ActivityPopupStormBinding;
import com.subhub.app.security.ControllerEditMode;
import com.subhub.app.security.ControllerPinManager;
import com.subhub.app.pack.SubHubPackLocks;
import com.subhub.app.pack.SubHubPackSchema;
import com.subhub.app.service.ScreenCaptureService;
import com.subhub.app.service.ScreenshotAccessibilityService;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Styled local configuration UI with explicit consent and a persistent escape control. */
public final class PopupStormActivity extends AppCompatActivity {
    private ActivityPopupStormBinding binding;
    private SharedPreferences preferences;
    private boolean bindingEnabled;
    private ControllerEditMode editMode;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable statusUpdater = new Runnable() {
        @Override public void run() {
            if (binding == null) return;
            refreshStatus();
            ui.postDelayed(this, 500);
        }
    };
    private final ActivityResultLauncher<Uri> folderPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), this::onFolderPicked);
    private final ActivityResultLauncher<Intent> overlayPermission = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Settings.canDrawOverlays(this)) completeEnable();
                else refreshStatus();
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPopupStormBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferences = PopupStormSettings.preferences(this);
        PopupStormManager.get().reloadSettings(this);
        binding.buttonBack.setOnClickListener(view -> finish());
        binding.buttonAddFolder.setOnClickListener(view -> folderPicker.launch(null));
        binding.buttonStop.setOnClickListener(view -> {
            PopupStormManager.get().stop();
            refreshStatus();
        });
        binding.buttonPreview.setOnClickListener(view -> preview());
        binding.switchEnabled.setOnCheckedChangeListener((button, checked) -> {
            if (bindingEnabled) return;
            if (checked) requestEnable();
            else {
                preferences.edit().putBoolean(PopupStormSettings.K_ENABLED, false).apply();
                PopupStormManager.get().stop();
                PopupStormManager.get().reloadSettings(this);
                refreshStatus();
            }
        });
        buildPresetButtons();
        rebuildSettings();
        editMode = ControllerEditMode.bind(
                this, binding.buttonEditLock, editing -> applyEditState());
    }

    @Override protected void onResume() {
        super.onResume();
        if (editMode != null) editMode.refresh();
        PopupStormManager.get().reloadSettings(this);
        ui.removeCallbacks(statusUpdater);
        ui.post(statusUpdater);
        mainDelayRefresh();
    }

    @Override protected void onPause() {
        ui.removeCallbacks(statusUpdater);
        super.onPause();
    }

    private void requestEnable() {
        if (!preferences.getBoolean(PopupStormSettings.K_ACK, false)) {
            bindingEnabled = true;
            binding.switchEnabled.setChecked(false);
            bindingEnabled = false;
            new AlertDialog.Builder(this)
                    .setTitle(R.string.popup_photosensitivity_title)
                    .setMessage(R.string.popup_photosensitivity_body)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.popup_acknowledge, (dialog, which) -> {
                        preferences.edit().putBoolean(PopupStormSettings.K_ACK, true).apply();
                        requestOverlayOrComplete();
                    }).show();
            return;
        }
        requestOverlayOrComplete();
    }

    private void requestOverlayOrComplete() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.popup_overlay_permission, Toast.LENGTH_LONG).show();
            overlayPermission.launch(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } else completeEnable();
    }

    private void completeEnable() {
        preferences.edit().putBoolean(PopupStormSettings.K_ENABLED, true).apply();
        PopupStormManager.get().reloadSettings(this);
        if (ScreenCaptureService.isRunning() || ScreenshotAccessibilityService.isRunning()) {
            PopupStormManager.get().start(this);
        }
        refreshStatus();
    }

    private void preview() {
        PopupStormSettings current = PopupStormSettings.load(this);
        if (!current.isEnabled() || !current.isAcknowledged() || !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.popup_preview_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        PopupStormManager.get().start(this);
        binding.getRoot().postDelayed(this::refreshStatus, 350);
    }

    private void buildPresetButtons() {
        binding.presetContainer.removeAllViews();
        String selectedPreset = preferences.getString(
                PopupStormSettings.K_PRESET, IntensityPresets.MEDIUM.name());
        for (IntensityPresets preset : IntensityPresets.values()) {
            Button button = new Button(this);
            button.setText(preset.getDisplayName().toUpperCase(Locale.ROOT));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
            if (binding.presetContainer.getChildCount() > 0) params.setMarginStart(dp(4));
            button.setLayoutParams(params);
            button.setTextSize(11);
            button.setAllCaps(false);
            boolean selected = preset.name().equals(selectedPreset);
            button.setBackgroundResource(selected
                    ? R.drawable.bg_bottom_tab_active : R.drawable.bg_outline_button);
            button.setTextColor(getColor(selected ? R.color.text_primary : R.color.accent));
            button.setEnabled(ControllerPinManager.isSessionUnlocked());
            button.setOnClickListener(view -> {
                preset.apply(this);
                buildPresetButtons();
                rebuildSettings();
                PopupStormManager.get().reloadSettings(this);
                Toast.makeText(this, preset.getDisplayName(), Toast.LENGTH_SHORT).show();
            });
            binding.presetContainer.addView(button);
        }
    }

    private void rebuildSettings() {
        binding.dynamicSettings.removeAllViews();
        LinearLayout behavior = card(R.string.popup_behavior);
        addFloatSlider(behavior, R.string.popup_spawn_rate, PopupStormSettings.K_SPAWN_RATE,
                0, 8, 2, .1f, "%.1f");
        addFloatSlider(behavior, R.string.popup_display_duration,
                PopupStormSettings.K_DISPLAY_DURATION, .3f, 6, 1, .1f, "%.1f");
        addIntSlider(behavior, R.string.popup_max_simultaneous,
                PopupStormSettings.K_MAX_SIMULTANEOUS, 1, 15, 8);
        addChoice(behavior, R.string.popup_position, PopupStormSettings.K_POSITION_MODE,
                new String[]{"random", "center"},
                new int[]{R.string.popup_random, R.string.popup_center}, "random");
        addChoice(behavior, R.string.popup_size_mode, PopupStormSettings.K_SIZE_MODE,
                new String[]{"random", "fixed"},
                new int[]{R.string.popup_random, R.string.popup_fixed}, "random");
        addIntSlider(behavior, R.string.popup_min_size, PopupStormSettings.K_MIN_SIZE, 80, 800, 160);
        addIntSlider(behavior, R.string.popup_max_size, PopupStormSettings.K_MAX_SIZE, 80, 1200, 380);
        addIntSlider(behavior, R.string.popup_fixed_size, PopupStormSettings.K_FIXED_SIZE, 80, 1200, 260);
        addToggle(behavior, R.string.popup_random_rotation, PopupStormSettings.K_RANDOM_ROT, false);
        addIntSlider(behavior, R.string.popup_rotation_max, PopupStormSettings.K_ROT_MAX, 0, 90, 25);
        addIntSlider(behavior, R.string.popup_fade_in, PopupStormSettings.K_FADE_IN, 0, 2000, 100);
        addIntSlider(behavior, R.string.popup_fade_out, PopupStormSettings.K_FADE_OUT, 0, 3000, 200);
        addToggle(behavior, R.string.popup_bouncing, PopupStormSettings.K_BOUNCING, false);
        addIntSlider(behavior, R.string.popup_bouncing_speed,
                PopupStormSettings.K_BOUNCING_SPEED, 10, 600, 60);
        addToggle(behavior, R.string.popup_tap_dismiss,
                PopupStormSettings.K_CLICK_DISMISS_ALL, true);

        LinearLayout modes = card(R.string.popup_modes);
        addToggle(modes, R.string.popup_burst_mode, PopupStormSettings.K_BURST_ENABLED, false);
        addFloatSlider(modes, R.string.popup_burst_frequency,
                PopupStormSettings.K_BURST_FREQUENCY, 5, 120, 30, 1, "%.0f");
        addFloatSlider(modes, R.string.popup_burst_duration,
                PopupStormSettings.K_BURST_DURATION, 1, 15, 4, .5f, "%.1f");
        addFloatSlider(modes, R.string.popup_burst_multiplier,
                PopupStormSettings.K_BURST_MULTIPLIER, 1, 5, 3, .5f, "%.1f");
        addIntSlider(modes, R.string.popup_denial_chance,
                PopupStormSettings.K_DENIAL_CHANCE, 0, 100, 0);
        addChoice(modes, R.string.popup_denial_style, PopupStormSettings.K_DENIAL_STYLE,
                new String[]{"blur", "pixelate", "mixed"},
                new int[]{R.string.popup_blur, R.string.popup_pixelate, R.string.popup_mixed}, "blur");
        addIntSlider(modes, R.string.popup_denial_intensity,
                PopupStormSettings.K_DENIAL_INTENSITY, 0, 100, 50);
        addToggle(modes, R.string.popup_denial_caption, PopupStormSettings.K_DENIAL_CAPTION, true);
        addTextField(modes, R.string.popup_caption_text,
                PopupStormSettings.K_DENIAL_CAPTION_TEXT, "NO");

        LinearLayout detection = card(R.string.popup_detection);
        addChoice(detection, R.string.popup_detection_mode, PopupStormSettings.K_DETECTION_MODE,
                new String[]{"off", "cover", "avoid"},
                new int[]{R.string.popup_off, R.string.popup_cover, R.string.popup_avoid}, "off");
        addIntSlider(detection, R.string.popup_avoid_padding,
                PopupStormSettings.K_AVOID_PADDING, 0, 400, 80);
        rebuildFolders();
        refreshStatus();
        applyEditState();
    }

    private void applyEditState() {
        if (binding == null) return;
        boolean editing = ControllerPinManager.isSessionUnlocked()
                && !SubHubPackLocks.isLocked(this, SubHubPackSchema.POPUP);
        binding.switchEnabled.setEnabled(editing);
        binding.buttonPreview.setEnabled(editing);
        binding.buttonAddFolder.setEnabled(editing);
        setEnabledRecursive(binding.presetContainer, editing);
        setEnabledRecursive(binding.dynamicSettings, editing);
        rebuildFolders();
        // Stop remains available as an unconditional safety action.
    }

    private static void setEnabledRecursive(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                setEnabledRecursive(group.getChildAt(index), enabled);
            }
        }
    }

    private LinearLayout card(int title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (binding.dynamicSettings.getChildCount() > 0) params.topMargin = dp(14);
        card.setLayoutParams(params);
        TextView header = label(getString(title), 13, true, R.color.accent);
        card.addView(header);
        binding.dynamicSettings.addView(card);
        return card;
    }

    private void addToggle(LinearLayout parent, int title, String key, boolean defaultValue) {
        SwitchMaterial toggle = new SwitchMaterial(this);
        toggle.setText(title);
        toggle.setTextColor(getColor(R.color.text_primary));
        toggle.setMinHeight(dp(50));
        toggle.setEnabled(ControllerPinManager.isSessionUnlocked()
                && !SubHubPackLocks.isLocked(this, SubHubPackSchema.POPUP));
        toggle.setChecked(preferences.getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(key, checked).apply();
            settingsChanged();
        });
        parent.addView(toggle);
    }

    private void addIntSlider(LinearLayout parent, int title, String key,
            int minimum, int maximum, int defaultValue) {
        int stored = Math.max(minimum, Math.min(maximum, preferences.getInt(key, defaultValue)));
        TextView value = label(getString(title) + ": " + stored, 11, false, R.color.text_secondary);
        value.setPadding(0, dp(10), 0, 0);
        parent.addView(value);
        SeekBar slider = new SeekBar(this);
        slider.setMax(maximum - minimum);
        slider.setProgress(stored - minimum);
        slider.setEnabled(ControllerPinManager.isSessionUnlocked()
                && !SubHubPackLocks.isLocked(this, SubHubPackSchema.POPUP));
        slider.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int actual = minimum + progress;
                value.setText(getString(title) + ": " + actual);
                if (fromUser) preferences.edit().putInt(key, actual).apply();
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { settingsChanged(); }
        });
        parent.addView(slider);
    }

    private void addFloatSlider(LinearLayout parent, int title, String key, float minimum,
            float maximum, float defaultValue, float step, String format) {
        float stored = Math.max(minimum, Math.min(maximum, preferences.getFloat(key, defaultValue)));
        int steps = Math.round((maximum - minimum) / step);
        TextView value = label(getString(title) + ": " + String.format(Locale.ROOT, format, stored),
                11, false, R.color.text_secondary);
        value.setPadding(0, dp(10), 0, 0);
        parent.addView(value);
        SeekBar slider = new SeekBar(this);
        slider.setMax(steps);
        slider.setProgress(Math.round((stored - minimum) / step));
        slider.setEnabled(ControllerPinManager.isSessionUnlocked());
        slider.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float actual = minimum + progress * step;
                value.setText(getString(title) + ": " + String.format(Locale.ROOT, format, actual));
                if (fromUser) preferences.edit().putFloat(key, actual).apply();
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { settingsChanged(); }
        });
        parent.addView(slider);
    }

    private void addChoice(LinearLayout parent, int title, String key, String[] values,
            int[] labels, String defaultValue) {
        TextView heading = label(getString(title), 11, false, R.color.text_secondary);
        heading.setPadding(0, dp(10), 0, 0);
        parent.addView(heading);
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.HORIZONTAL);
        String selected = preferences.getString(key, defaultValue);
        for (int index = 0; index < values.length; index++) {
            RadioButton option = new RadioButton(this);
            option.setId(View.generateViewId());
            option.setTag(values[index]);
            option.setText(labels[index]);
            option.setTextColor(getColor(R.color.text_primary));
            option.setTextSize(11);
            option.setEnabled(ControllerPinManager.isSessionUnlocked());
            option.setChecked(values[index].equals(selected));
            group.addView(option, new RadioGroup.LayoutParams(0, dp(48), 1));
        }
        group.setOnCheckedChangeListener((radioGroup, checkedId) -> {
            View checked = radioGroup.findViewById(checkedId);
            if (checked != null) {
                preferences.edit().putString(key, String.valueOf(checked.getTag())).apply();
                settingsChanged();
            }
        });
        parent.addView(group);
    }

    private void addTextField(LinearLayout parent, int title, String key, String defaultValue) {
        TextView heading = label(getString(title), 11, false, R.color.text_secondary);
        heading.setPadding(0, dp(10), 0, 0);
        parent.addView(heading);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setMaxLines(1);
        input.setText(preferences.getString(key, defaultValue));
        input.setTextColor(getColor(R.color.text_primary));
        input.setTextSize(12);
        input.setEnabled(ControllerPinManager.isSessionUnlocked());
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        View.OnFocusChangeListener save = (view, focused) -> {
            if (!focused) saveText(input, key, defaultValue);
        };
        input.setOnFocusChangeListener(save);
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveText(input, key, defaultValue);
                input.clearFocus();
                return true;
            }
            return false;
        });
        parent.addView(input);
    }

    private void saveText(EditText input, String key, String defaultValue) {
        String value = input.getText().toString().trim();
        if (value.isEmpty()) value = defaultValue;
        if (value.length() > 32) value = value.substring(0, 32);
        preferences.edit().putString(key, value).apply();
        settingsChanged();
    }

    private void settingsChanged() {
        preferences.edit().remove(PopupStormSettings.K_PRESET).apply();
        buildPresetButtons();
        PopupStormManager.get().reloadSettings(this);
        if (PopupStormManager.get().isRunning()) PopupStormManager.get().start(this);
        refreshStatus();
    }

    private void onFolderPicked(Uri uri) {
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            Toast.makeText(this, R.string.popup_preview_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        Set<String> folders = new LinkedHashSet<>(preferences.getStringSet(
                PopupStormSettings.K_FOLDERS, Set.of()));
        if (folders.size() < 20) folders.add(uri.toString());
        preferences.edit().putStringSet(PopupStormSettings.K_FOLDERS, folders).apply();
        PopupStormManager.get().reloadSettings(this);
        rebuildFolders();
        binding.libraryCount.setText(R.string.popup_library_scanning);
        mainDelayRefresh();
    }

    private void rebuildFolders() {
        binding.foldersList.removeAllViews();
        Set<String> folders = preferences.getStringSet(PopupStormSettings.K_FOLDERS, Set.of());
        if (folders == null || folders.isEmpty()) {
            TextView empty = label(getString(R.string.popup_no_folders), 11, false, R.color.text_muted);
            empty.setPadding(0, dp(10), 0, 0);
            binding.foldersList.addView(empty);
        } else {
            for (String value : new ArrayList<>(folders)) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView name = label(prettyFolder(value), 11, false, R.color.text_secondary);
                row.addView(name, new LinearLayout.LayoutParams(0, dp(48), 1));
                Button remove = new Button(this);
                remove.setText(R.string.popup_remove);
                remove.setTextSize(11);
                remove.setAllCaps(false);
                remove.setBackgroundResource(R.drawable.bg_outline_button);
                remove.setTextColor(getColor(R.color.accent));
                remove.setEnabled(ControllerPinManager.isSessionUnlocked());
                remove.setOnClickListener(view -> removeFolder(value));
                row.addView(remove, new LinearLayout.LayoutParams(dp(104), dp(44)));
                binding.foldersList.addView(row);
            }
        }
        int images = Math.max(1, PopupStormManager.get().libraryImageCount());
        binding.libraryCount.setText(getResources().getQuantityString(
                R.plurals.popup_library_count, images, images,
                folders == null ? 0 : folders.size()));
    }

    private void removeFolder(String value) {
        Set<String> folders = new LinkedHashSet<>(preferences.getStringSet(
                PopupStormSettings.K_FOLDERS, Set.of()));
        folders.remove(value);
        preferences.edit().putStringSet(PopupStormSettings.K_FOLDERS, folders).apply();
        try { getContentResolver().releasePersistableUriPermission(
                Uri.parse(value), Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (Exception ignored) {}
        PopupStormManager.get().reloadSettings(this);
        rebuildFolders();
        mainDelayRefresh();
    }

    private String prettyFolder(String value) {
        try {
            String last = Uri.parse(value).getLastPathSegment();
            return last == null ? value : Uri.decode(last).replace("primary:", "");
        } catch (Exception ignored) { return value; }
    }

    private void refreshStatus() {
        PopupStormSettings current = PopupStormSettings.load(this);
        bindingEnabled = true;
        binding.switchEnabled.setChecked(current.isEnabled());
        bindingEnabled = false;
        if (PopupStormManager.get().isRunning()) binding.status.setText(R.string.popup_status_running);
        else if (!current.isEnabled()) binding.status.setText(R.string.popup_status_off);
        else if (!current.isAcknowledged()) binding.status.setText(R.string.popup_status_ack);
        else if (!Settings.canDrawOverlays(this)) binding.status.setText(R.string.popup_status_permission);
        else binding.status.setText(R.string.popup_status_ready);
    }

    private void mainDelayRefresh() {
        binding.getRoot().postDelayed(() -> {
            if (binding == null) return;
            rebuildFolders();
            refreshStatus();
        }, 450);
    }

    private TextView label(String text, float size, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(getColor(color));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        binding = null;
        super.onDestroy();
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar seekBar) {}
    }
}
