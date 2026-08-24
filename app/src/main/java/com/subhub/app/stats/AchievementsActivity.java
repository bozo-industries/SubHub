package com.subhub.app.stats;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.subhub.app.R;
import com.subhub.app.databinding.ActivityAchievementsBinding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Illustrated, grouped achievement catalog for both recovered and SubHub-native milestones. */
public final class AchievementsActivity extends AppCompatActivity {
    private static final String[] CATEGORY_ORDER = {
            "blocks", "time", "sessions", "peaks", "streaks", "app_mode", "limits",
            "pact", "hardcore", "censor", "custom", "profiles", "browser", "export",
            "wallet", "hidden", "special"
    };
    private ActivityAchievementsBinding binding;
    private AchievementManager manager;
    private StatsSnapshot stats;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAchievementsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.buttonBack.setOnClickListener(view -> finish());
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
        binding.achievementBadgeStrip.removeAllViews();
        binding.achievementProgress.setText(getString(R.string.achievements_progress_compact,
                manager.getUnlockedCount(), manager.getTotalCount()));
        addShowcaseBadges();
        renderNextMilestone();

        Map<String, List<AchievementManager.Achievement>> groups = new LinkedHashMap<>();
        for (AchievementManager.Achievement achievement : manager.all()) {
            List<AchievementManager.Achievement> group = groups.get(achievement.getCategory());
            if (group == null) {
                group = new ArrayList<>();
                groups.put(achievement.getCategory(), group);
            }
            group.add(achievement);
        }
        for (String category : CATEGORY_ORDER) {
            List<AchievementManager.Achievement> group = groups.remove(category);
            if (group == null) continue;
            addCategoryHeader(category);
            for (AchievementManager.Achievement achievement : group) {
                addRow(achievement);
            }
        }
        for (Map.Entry<String, List<AchievementManager.Achievement>> entry : groups.entrySet()) {
            addCategoryHeader(entry.getKey());
            for (AchievementManager.Achievement achievement : entry.getValue()) addRow(achievement);
        }
    }

    private void addShowcaseBadges() {
        List<AchievementManager.Achievement> values = representativeBadges(8);
        for (int index = 0; index < values.size(); index++) {
            AchievementManager.Achievement achievement = values.get(index);
            boolean unlocked = manager.isUnlocked(achievement.getId());
            boolean concealed = achievement.isHidden() && !unlocked;

            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(dp(68),
                    LinearLayout.LayoutParams.MATCH_PARENT);
            if (index > 0) cellParams.leftMargin = dp(4);
            cell.setLayoutParams(cellParams);

            AchievementBadgeView badge = badge(achievement, unlocked, concealed, 48);
            cell.addView(badge);
            TextView label = text(concealed ? getString(R.string.achievement_hidden_name)
                    : getString(achievement.getName()), 9, R.color.text_secondary, false);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = dp(3);
            label.setLayoutParams(labelParams);
            cell.addView(label);
            binding.achievementBadgeStrip.addView(cell);
        }
    }

    private List<AchievementManager.Achievement> representativeBadges(int maximum) {
        List<AchievementManager.Achievement> selected = new ArrayList<>();
        Set<Integer> artwork = new LinkedHashSet<>();
        for (int pass = 0; pass < 2 && selected.size() < maximum; pass++) {
            boolean wantUnlocked = pass == 0;
            for (AchievementManager.Achievement achievement : manager.all()) {
                if (manager.isUnlocked(achievement.getId()) != wantUnlocked
                        || !artwork.add(achievement.getBadgeArtRes())) continue;
                selected.add(achievement);
                if (selected.size() == maximum) break;
            }
        }
        return selected;
    }

    private void renderNextMilestone() {
        AchievementManager.Achievement next = nextVisibleLocked();
        if (next == null) {
            binding.achievementNextName.setText(R.string.achievements_all_complete);
            binding.achievementNextProgress.setProgress(100);
            return;
        }
        AchievementManager.Progress progress = manager.progress(next, stats);
        binding.achievementNextName.setText(getString(R.string.achievements_next_fmt,
                getString(next.getName()), formatProgress(next, progress)));
        binding.achievementNextProgress.setProgress(progress.percent());
    }

    private AchievementManager.Achievement nextVisibleLocked() {
        for (AchievementManager.Achievement value : manager.all()) {
            if (!manager.isUnlocked(value.getId()) && !value.isHidden()
                    && manager.progress(value, stats).isCountable()) return value;
        }
        return null;
    }

    private void addCategoryHeader(String category) {
        TextView header = text(getString(categoryTitle(category)), 11,
                R.color.accent_mid, true);
        header.setAllCaps(true);
        header.setLetterSpacing(0.15f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = binding.achievementList.getChildCount() == 0 ? dp(2) : dp(15);
        params.bottomMargin = dp(7);
        header.setLayoutParams(params);
        binding.achievementList.addView(header);
    }

    private void addRow(AchievementManager.Achievement achievement) {
        boolean unlocked = manager.isUnlocked(achievement.getId());
        boolean concealed = achievement.isHidden() && !unlocked;
        AchievementManager.Progress progress = manager.progress(achievement, stats);

        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(18));
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(getColor(unlocked
                ? R.color.surface_card_raised : R.color.surface_card));
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(getColor(unlocked ? R.color.accent_hot : R.color.outline_subtle));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(8);
        card.setLayoutParams(cardParams);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(11), dp(12), dp(11));
        card.addView(row);

        row.addView(badge(achievement, unlocked, concealed, 56));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        contentParams.leftMargin = dp(12);
        content.setLayoutParams(contentParams);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(concealed ? getString(R.string.achievement_hidden_name)
                : getString(achievement.getName()), 14, R.color.text_primary, true);
        title.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(title);
        if (unlocked) {
            TextView tag = text(getString(R.string.achievement_unlocked_tag), 8,
                    R.color.accent_hot, true);
            tag.setAllCaps(true);
            tag.setLetterSpacing(0.1f);
            tag.setBackgroundResource(R.drawable.bg_achievement_tag);
            tag.setGravity(Gravity.CENTER);
            tag.setPadding(dp(8), dp(3), dp(8), dp(3));
            LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            tagParams.leftMargin = dp(8);
            tag.setLayoutParams(tagParams);
            titleRow.addView(tag);
        }
        content.addView(titleRow);

        TextView description = text(concealed ? getString(R.string.achievement_hidden_desc)
                : getString(achievement.getDescription()), 11, R.color.text_secondary, false);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(2);
        description.setLayoutParams(descriptionParams);
        content.addView(description);

        LinearLayout progressRow = new LinearLayout(this);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams progressRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        progressRowParams.topMargin = dp(8);
        progressRow.setLayoutParams(progressRowParams);
        if (progress.isCountable() && !concealed) {
            ProgressBar bar = new ProgressBar(this, null,
                    android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);
            bar.setProgress(progress.percent());
            bar.setProgressTintList(ColorStateList.valueOf(getColor(
                    unlocked ? R.color.accent_hot : R.color.accent_mid)));
            bar.setProgressBackgroundTintList(
                    ColorStateList.valueOf(getColor(R.color.outline_subtle)));
            bar.setLayoutParams(new LinearLayout.LayoutParams(0, dp(4), 1f));
            progressRow.addView(bar);
            TextView count = text(formatProgress(achievement, progress), 9,
                    unlocked ? R.color.accent_hot : R.color.text_muted, true);
            LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            countParams.leftMargin = dp(10);
            count.setLayoutParams(countParams);
            progressRow.addView(count);
        } else {
            TextView status = text(concealed ? getString(R.string.achievement_secret)
                    : getString(unlocked ? R.string.achievement_unlocked_tag
                    : R.string.achievement_locked), 9,
                    unlocked ? R.color.accent_hot : R.color.text_muted, true);
            progressRow.addView(status);
        }
        content.addView(progressRow);
        row.addView(content);
        binding.achievementList.addView(card);
    }

    private AchievementBadgeView badge(AchievementManager.Achievement achievement,
            boolean unlocked, boolean concealed, int sizeDp) {
        AchievementBadgeView badge = new AchievementBadgeView(this);
        badge.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        CharSequence description = concealed ? getString(R.string.achievement_hidden_name)
                : getString(achievement.getName());
        badge.bind(achievement.getBadgeArtRes(), unlocked, concealed, description);
        return badge;
    }

    private String formatProgress(AchievementManager.Achievement achievement,
            AchievementManager.Progress progress) {
        String category = achievement.getCategory();
        if ("time".equals(category) || "marathon".equals(achievement.getId())
                || "mega_marathon".equals(achievement.getId())) {
            return compactDuration(progress.getCurrent()) + " / "
                    + compactDuration(progress.getTarget());
        }
        return String.format(Locale.getDefault(), "%,d / %,d",
                progress.getCurrent(), progress.getTarget());
    }

    private String compactDuration(long seconds) {
        if (seconds < 3600) return Math.max(0, seconds / 60) + "m";
        float hours = seconds / 3600f;
        if (hours == Math.round(hours)) return Math.round(hours) + "h";
        return String.format(Locale.getDefault(), "%.1fh", hours);
    }

    private int categoryTitle(String category) {
        switch (category) {
            case "blocks": return R.string.achievement_category_blocks;
            case "time": return R.string.achievement_category_time;
            case "sessions": return R.string.achievement_category_sessions;
            case "peaks": return R.string.achievement_category_peaks;
            case "streaks": return R.string.achievement_category_streaks;
            case "custom": return R.string.achievement_category_custom;
            case "profiles": return R.string.achievement_category_profiles;
            case "browser": return R.string.achievement_category_browser;
            case "export": return R.string.achievement_category_export;
            case "app_mode": return R.string.achievement_category_app_mode;
            case "limits": return R.string.achievement_category_limits;
            case "pact": return R.string.achievement_category_pacts;
            case "hardcore": return R.string.achievement_category_hardcore;
            case "censor": return R.string.achievement_category_text_filter;
            case "control": return R.string.achievement_category_control;
            case "wallet": return R.string.achievement_category_wallet;
            case "hidden": return R.string.achievement_category_hidden;
            case "special": return R.string.achievement_category_special;
            default: return R.string.achievements_title;
        }
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(getColor(color));
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.create(view.getTypeface(), Typeface.BOLD));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        binding = null;
        super.onDestroy();
    }
}
