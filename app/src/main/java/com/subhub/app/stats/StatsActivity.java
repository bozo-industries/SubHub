package com.subhub.app.stats;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.subhub.app.R;
import com.subhub.app.databinding.ActivityStatsBinding;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Full local statistics, trends, milestones, and achievement entry point. */
public final class StatsActivity extends AppCompatActivity {
    private ActivityStatsBinding binding;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStatsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.buttonBack.setOnClickListener(view -> finish());
        binding.buttonAchievements.setOnClickListener(view ->
                startActivity(new Intent(this, AchievementsActivity.class)));
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        StatsRepository repository = new StatsRepository(this);
        StatsSnapshot stats = repository.load();
        binding.totalBlocks.setText(String.valueOf(stats.getTotalBlocks()));
        binding.totalTime.setText(StatsSnapshot.formatDuration(stats.getTotalProtectedSeconds()));
        binding.totalSessions.setText(String.valueOf(stats.getSessions()));
        binding.detailStats.setText(String.format(Locale.ROOT,
                "%s: %d days\n%s: %d blocks\n%s: %s\n%s: %d\n%s: %d\n%s: %d",
                getString(R.string.statistics_current_streak), stats.getCurrentStreak(),
                getString(R.string.statistics_peak), stats.getPeakSessionBlocks(),
                getString(R.string.statistics_longest),
                StatsSnapshot.formatDuration(stats.getLongestSessionSeconds()),
                getString(R.string.statistics_browser), stats.getBrowserSessions(),
                getString(R.string.statistics_exported), stats.getExportedImages(),
                getString(R.string.statistics_active_days), stats.getActiveDates().size()));

        int next = MilestoneManager.next(stats.getTotalBlocks());
        binding.milestoneLabel.setText(getString(
                R.string.milestone_next, next, Math.min(stats.getTotalBlocks(), next)));
        binding.milestoneProgress.setMax(next);
        binding.milestoneProgress.setProgress((int) Math.min(stats.getTotalBlocks(), next));

        List<StatsRepository.SessionEntry> history = repository.getSessionHistory();
        binding.sessionTrend.setEntries(history);
        binding.historyList.removeAllViews();
        List<StatsRepository.SessionEntry> reverse = new ArrayList<>(history);
        Collections.reverse(reverse);
        for (int index = 0; index < Math.min(10, reverse.size()); index++) {
            StatsRepository.SessionEntry entry = reverse.get(index);
            TextView row = new TextView(this);
            row.setText(getString(R.string.statistics_session_row,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(new Date(entry.getStartMillis())),
                    StatsSnapshot.formatDuration(entry.getDurationSeconds()), entry.getBlocks()));
            row.setTextColor(getColor(R.color.text_secondary));
            row.setTextSize(11);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinHeight(dp(42));
            binding.historyList.addView(row);
        }
        binding.historyEmpty.setVisibility(history.isEmpty() ? View.VISIBLE : View.GONE);
        AchievementManager achievements = new AchievementManager(this);
        binding.achievementProgress.setText(getString(R.string.achievements_progress_fmt,
                achievements.getUnlockedCount(), achievements.getTotalCount()));
    }

    @Override protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
