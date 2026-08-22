package com.betasafe.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.betasafe.app.databinding.ActivityMainBinding;
import com.betasafe.app.service.ScreenCaptureService;
import com.betasafe.app.settings.SettingsActivity;
import com.google.android.material.snackbar.Snackbar;

/** Main source UI and explicit permission flow for starting on-device protection. */
public final class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private MediaProjectionManager projectionManager;
    private ActivityResultLauncher<Intent> projectionPermission;
    private ActivityResultLauncher<Intent> overlayPermission;
    private ActivityResultLauncher<String> notificationPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        projectionPermission = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Intent service = ScreenCaptureService.startIntent(
                                this, result.getResultCode(), result.getData());
                        ContextCompat.startForegroundService(this, service);
                        updateProtectionButton(true);
                    } else {
                        showStatus(R.string.capture_cancelled);
                    }
                });
        overlayPermission = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                ignored -> {
                    if (Settings.canDrawOverlays(this)) requestProjection();
                    else showStatus(R.string.overlay_permission_needed);
                });
        notificationPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                ignored -> continueStartFlow());

        binding.buttonProtection.setOnClickListener(this::toggleProtection);
        binding.tabHome.setOnClickListener(view -> selectTab(binding.tabHome, R.string.tab_home));
        binding.tabSettings.setOnClickListener(
                view -> startActivity(new Intent(this, SettingsActivity.class)));
        binding.tabBrowser.setOnClickListener(view -> selectTab(binding.tabBrowser, R.string.tab_browser));
        binding.tabHelp.setOnClickListener(view -> selectTab(binding.tabHelp, R.string.tab_help));
    }

    private void toggleProtection(View view) {
        if (ScreenCaptureService.isRunning()) {
            startService(ScreenCaptureService.stopIntent(this));
            updateProtectionButton(false);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            continueStartFlow();
        }
    }

    private void continueStartFlow() {
        if (!Settings.canDrawOverlays(this)) {
            showStatus(R.string.overlay_permission_needed);
            overlayPermission.launch(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        requestProjection();
    }

    private void requestProjection() {
        projectionPermission.launch(projectionManager.createScreenCaptureIntent());
    }

    private void showStatus(int message) {
        Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    private void updateProtectionButton(boolean running) {
        binding.buttonProtection.setText(running ? R.string.stop_protection : R.string.start_protection);
    }

    private void selectTab(TextView selected, int label) {
        TextView[] tabs = {binding.tabHome, binding.tabSettings, binding.tabBrowser, binding.tabHelp};
        for (TextView tab : tabs) {
            boolean active = tab == selected;
            tab.setTextColor(getColor(active ? R.color.text_primary : R.color.text_muted));
            tab.setBackgroundResource(active ? R.drawable.bg_tab_active : android.R.color.transparent);
        }
        binding.sectionTitle.setText(label);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null) updateProtectionButton(ScreenCaptureService.isRunning());
    }

    @Override
    protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}
