package com.betasafe.app.commitment;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.betasafe.app.R;
import com.betasafe.app.databinding.ActivityCommitmentBinding;
import com.betasafe.app.security.ControllerEditMode;
import com.betasafe.app.security.ControllerPinManager;
import com.betasafe.app.settings.SettingsActivity;

import java.util.Locale;

/** Timed commitment ceremony with keeper-code and controller-PIN release paths. */
public final class CommitmentActivity extends AppCompatActivity {
    public static final String EXTRA_DURATION_MS = "commitment_duration_ms";
    private ActivityCommitmentBinding binding;
    private ControllerEditMode editMode;
    private final Handler timer = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            renderState();
            if (CommitmentManager.isActive(CommitmentActivity.this)) {
                timer.postDelayed(this, 1000L);
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCommitmentBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.buttonBack.setOnClickListener(view -> finish());
        binding.buttonSealPact.setOnClickListener(view -> sealPact());
        binding.buttonKeeperRelease.setOnClickListener(view -> keeperRelease());
        binding.buttonEmergencyRelease.setOnClickListener(view -> confirmEmergencyRelease());
        applyRequestedDuration();
        editMode = ControllerEditMode.bind(
                this, binding.buttonEditLock, editing -> applyEditState());
        renderState();
    }

    @Override protected void onResume() {
        super.onResume();
        if (editMode != null) editMode.refresh();
        timer.removeCallbacks(tick);
        timer.post(tick);
    }

    @Override protected void onPause() {
        timer.removeCallbacks(tick);
        super.onPause();
    }

    private void sealPact() {
        String code = binding.keeperCode.getText().toString();
        String confirmation = binding.keeperCodeConfirm.getText().toString();
        if (!code.equals(confirmation)) {
            Toast.makeText(this, R.string.commitment_codes_mismatch, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!CommitmentManager.start(this, selectedDuration(), code)) {
            Toast.makeText(this, R.string.commitment_code_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        binding.keeperCode.setText("");
        binding.keeperCodeConfirm.setText("");
        Toast.makeText(this, R.string.commitment_sealed_toast, Toast.LENGTH_SHORT).show();
        renderState();
    }

    private void keeperRelease() {
        if (CommitmentManager.verifyAndRelease(
                this, binding.releaseCode.getText().toString())) {
            binding.releaseCode.setText("");
            Toast.makeText(this, R.string.commitment_released, Toast.LENGTH_SHORT).show();
            openSettings();
        } else {
            Toast.makeText(this, R.string.commitment_wrong_code, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmEmergencyRelease() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.commitment_emergency_title)
                .setMessage(R.string.commitment_emergency_body)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.commitment_release_now, (dialog, which) -> {
                    CommitmentManager.emergencyRelease(this);
                    Toast.makeText(this, R.string.commitment_released, Toast.LENGTH_SHORT).show();
                    openSettings();
                })
                .show();
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    private void renderState() {
        if (binding == null) return;
        boolean active = CommitmentManager.isActive(this);
        binding.setupPanel.setVisibility(active ? View.GONE : View.VISIBLE);
        binding.activePanel.setVisibility(active ? View.VISIBLE : View.GONE);
        if (active) binding.countdown.setText(formatDuration(
                CommitmentManager.remainingMillis(this)));
        applyEditState();
    }

    private void applyEditState() {
        if (binding == null) return;
        boolean editing = ControllerPinManager.isSessionUnlocked();
        binding.durationGroup.setEnabled(editing);
        for (int index = 0; index < binding.durationGroup.getChildCount(); index++) {
            binding.durationGroup.getChildAt(index).setEnabled(editing);
        }
        binding.keeperCode.setEnabled(editing);
        binding.keeperCodeConfirm.setEnabled(editing);
        binding.buttonSealPact.setEnabled(editing);
        binding.buttonEmergencyRelease.setVisibility(editing ? View.VISIBLE : View.GONE);
        // The keeper-code release stays available without unlocking the rest of Dom mode.
    }

    private long selectedDuration() {
        int id = binding.durationGroup.getCheckedRadioButtonId();
        if (id == R.id.duration_1h) return 60L * 60L * 1000L;
        if (id == R.id.duration_24h) return 24L * 60L * 60L * 1000L;
        if (id == R.id.duration_7d) return 7L * 24L * 60L * 60L * 1000L;
        if (id == R.id.duration_30d) return 30L * 24L * 60L * 60L * 1000L;
        return 60L * 60L * 1000L;
    }

    private void applyRequestedDuration() {
        long requested = getIntent().getLongExtra(EXTRA_DURATION_MS, 60L * 60L * 1000L);
        if (requested >= 30L * 24L * 60L * 60L * 1000L) {
            binding.durationGroup.check(R.id.duration_30d);
        } else if (requested >= 7L * 24L * 60L * 60L * 1000L) {
            binding.durationGroup.check(R.id.duration_7d);
        } else if (requested >= 24L * 60L * 60L * 1000L) {
            binding.durationGroup.check(R.id.duration_24h);
        } else {
            binding.durationGroup.check(R.id.duration_1h);
        }
    }

    public static String formatDuration(long milliseconds) {
        long seconds = Math.max(0L, (milliseconds + 999L) / 1000L);
        long days = seconds / 86400L;
        long hours = (seconds % 86400L) / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        return days > 0
                ? String.format(Locale.ROOT, "%dd %02d:%02d:%02d", days, hours, minutes, remainder)
                : String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainder);
    }

    @Override protected void onDestroy() {
        timer.removeCallbacks(tick);
        binding = null;
        super.onDestroy();
    }
}
