package com.subhub.app.stats;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.subhub.app.R;
import com.subhub.app.databinding.ActivityAchievementsBinding;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Styled achievement catalog with progress and licensed badge export. */
public final class AchievementsActivity extends AppCompatActivity {
    private static final String BADGE_PATH = "achievement_badges/";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ActivityAchievementsBinding binding;
    private AchievementManager manager;
    private StatsSnapshot stats;
    private String pendingLegacyBadge;
    private ActivityResultLauncher<String> createBadge;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAchievementsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.buttonBack.setOnClickListener(view -> finish());
        createBadge = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("image/png"), this::writeLegacyBadge);
    }

    @Override protected void onResume() {
        super.onResume();
        manager = new AchievementManager(this);
        stats = new StatsRepository(this).load();
        manager.checkAchievements(stats);
        manager.takePendingNotifications();
        rebuild();
    }

    private void rebuild() {
        binding.achievementList.removeAllViews();
        binding.achievementProgress.setText(getString(R.string.achievements_progress_fmt,
                manager.getUnlockedCount(), manager.getTotalCount()));
        for (AchievementManager.Achievement achievement : manager.all()) addRow(achievement);
    }

    private void addRow(AchievementManager.Achievement achievement) {
        boolean unlocked = manager.isUnlocked(achievement.getId());
        boolean concealed = achievement.isHidden() && !unlocked;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setPadding(dp(14), dp(12), dp(12), dp(12));
        row.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(9);
        row.setLayoutParams(rowParams);

        TextView icon = new TextView(this);
        icon.setText(concealed ? "?" : achievement.getIcon());
        icon.setTextColor(getColor(unlocked ? R.color.accent : R.color.text_muted));
        icon.setTextSize(24);
        icon.setGravity(Gravity.CENTER);
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(52), dp(52)));
        row.addView(icon);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView title = text(concealed ? getString(R.string.achievement_hidden_name)
                : getString(achievement.getName()), 14, R.color.text_primary, true);
        TextView description = text(concealed ? getString(R.string.achievement_hidden_desc)
                : getString(achievement.getDescription()), 11, R.color.text_secondary, false);
        AchievementManager.Progress progress = manager.progress(achievement, stats);
        TextView status = text(unlocked ? getString(R.string.achievement_unlocked_tag)
                : progress.isCountable() ? progress.getCurrent() + " / " + progress.getTarget()
                : getString(R.string.achievement_locked), 10,
                unlocked ? R.color.accent : R.color.text_muted, true);
        content.addView(title); content.addView(description); content.addView(status);
        if (progress.isCountable() && !concealed) {
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100); bar.setProgress(progress.percent());
            bar.setProgressTintList(getColorStateList(R.color.accent));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(5));
            params.topMargin = dp(6); bar.setLayoutParams(params); content.addView(bar);
        }
        if (unlocked && achievement.getBadge() != null) {
            Button save = new Button(this);
            save.setText(R.string.achievement_save_badge);
            save.setTextColor(getColor(R.color.accent));
            save.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            save.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            save.setOnClickListener(view -> saveBadge(achievement));
            content.addView(save);
        }
        row.addView(content);
        binding.achievementList.addView(row);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(getColor(color));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private void saveBadge(AchievementManager.Achievement achievement) {
        if (!badgeExists(achievement.getBadge())) {
            Toast.makeText(this, R.string.badge_missing, Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            worker.execute(() -> saveModernBadge(achievement));
        } else {
            pendingLegacyBadge = achievement.getBadge();
            createBadge.launch("SubHub-" + achievement.getId() + ".png");
        }
    }

    private void saveModernBadge(AchievementManager.Achievement achievement) {
        Uri uri = null;
        boolean success = false;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME,
                    "SubHub-" + achievement.getId() + ".png");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SubHub");
            uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                success = output != null && copyBadge(achievement.getBadge(), output);
            }
        } catch (Exception ignored) { }
        if (!success && uri != null) getContentResolver().delete(uri, null, null);
        reportSave(success);
    }

    private void writeLegacyBadge(Uri uri) {
        String badge = pendingLegacyBadge;
        pendingLegacyBadge = null;
        if (uri == null || badge == null) return;
        worker.execute(() -> {
            boolean success = false;
            try (OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                success = output != null && copyBadge(badge, output);
            } catch (Exception ignored) { }
            reportSave(success);
        });
    }

    private boolean copyBadge(String badge, OutputStream output) {
        try (InputStream input = getAssets().open(BADGE_PATH + badge)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            output.flush();
            return true;
        } catch (Exception ignored) { return false; }
    }

    private boolean badgeExists(String badge) {
        try (InputStream ignored = getAssets().open(BADGE_PATH + badge)) { return true; }
        catch (Exception missing) { return false; }
    }

    private void reportSave(boolean success) {
        runOnUiThread(() -> Toast.makeText(this,
                success ? R.string.badge_saved : R.string.badge_save_failed,
                Toast.LENGTH_LONG).show());
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        binding = null;
        super.onDestroy();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
