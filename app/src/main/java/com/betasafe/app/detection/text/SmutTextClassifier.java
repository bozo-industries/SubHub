package com.betasafe.app.detection.text;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Small deterministic text-risk classifier. It deliberately requires explicit words or multiple
 * contextual signals; generic relationship language is not enough by itself.
 */
public final class SmutTextClassifier {
    private static final List<Rule> RULES = rules();
    private static final Pattern SAFE_CONTEXT = Pattern.compile(
            "\\b(?:sex education|sexual health|sexual assault|rape crisis|breast cancer|"
                    + "prostate cancer|medical|clinical|anatomy|gynecolog\\w*|urolog\\w*|"
                    + "healthcare|survivor support|support hotline|consent guide)\\b");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public Match classify(String source, TextSmutConfig config) {
        if (config == null || !config.isEnabled() || source == null) return Match.none();
        String text = normalize(source);
        if (text.length() < 3) return Match.none();

        int explicit = 0;
        int fetish = 0;
        int solicitation = 0;
        for (Rule rule : RULES) {
            if (!config.getEnabledCategories().contains(rule.category)
                    || !rule.pattern.matcher(text).find()) continue;
            if (TextSmutConfig.CATEGORY_EXPLICIT.equals(rule.category)) explicit += rule.points;
            else if (TextSmutConfig.CATEGORY_FETISH.equals(rule.category)) fetish += rule.points;
            else if (TextSmutConfig.CATEGORY_SOLICITATION.equals(rule.category)) {
                solicitation += rule.points;
            }
        }

        int score = explicit + fetish + solicitation;
        if (SAFE_CONTEXT.matcher(text).find()) score = Math.max(0, score - 5);
        int threshold = config.getSensitivity() == TextSmutConfig.SENSITIVITY_STRICT
                ? 6 : config.getSensitivity() == TextSmutConfig.SENSITIVITY_BROAD ? 3 : 4;
        if (score < threshold) return Match.none();

        String category = TextSmutConfig.CATEGORY_EXPLICIT;
        int strongest = explicit;
        if (fetish > strongest) {
            category = TextSmutConfig.CATEGORY_FETISH;
            strongest = fetish;
        }
        if (solicitation > strongest) category = TextSmutConfig.CATEGORY_SOLICITATION;
        float confidence = Math.min(0.99f, 0.55f + score * 0.07f);
        return new Match(true, category, score, confidence);
    }

    static String normalize(String source) {
        String decomposed = Normalizer.normalize(source, Normalizer.Form.NFKD)
                .toLowerCase(Locale.ROOT)
                .replace('0', 'o')
                .replace('1', 'i')
                .replace('3', 'e')
                .replace('4', 'a')
                .replace('5', 's')
                .replace('7', 't')
                .replace('@', 'a')
                .replace('$', 's');
        String plain = COMBINING_MARKS.matcher(decomposed).replaceAll("");
        return WHITESPACE.matcher(NON_WORD.matcher(plain).replaceAll(" ")).replaceAll(" ").trim();
    }

    private static List<Rule> rules() {
        List<Rule> values = new ArrayList<>();
        values.add(rule(TextSmutConfig.CATEGORY_EXPLICIT, 4,
                "clit(?:oris)?|puss(?:y|ies)|cock(?:s)?|dick(?:s)?|penis(?:es)?|"
                        + "vagin(?:a|as|al)|cum(?:ming|s|med)?|orgasm(?:s|ic)?|ejaculat\\w*|"
                        + "masturbat\\w*|blowjobs?|handjobs?|hentai|porn(?:o|ography)?|nudes?|nsfw|smut|erotica?"));
        values.add(rule(TextSmutConfig.CATEGORY_EXPLICIT, 2,
                "sex(?:ual|ually)?|fuck\\w*|suck\\w*|naked|breasts?|boobs?|nipples?|anal|genitals?"));
        values.add(rule(TextSmutConfig.CATEGORY_FETISH, 3,
                "bdsm|strap on|strapon|chastity|cuck\\w*|goon\\w*|bondage|fetish|kink\\w*|noncon"));
        values.add(rule(TextSmutConfig.CATEGORY_FETISH, 2,
                "strap|edg(?:e|ed|ing)|spank\\w*|whip\\w*|breed\\w*|domme|submissive|humiliat\\w*"));
        values.add(rule(TextSmutConfig.CATEGORY_FETISH, 1,
                "daddy|mommy|master|slave|force\\w*|obey|punish\\w*|feet|armpits?"));
        values.add(phrase(TextSmutConfig.CATEGORY_SOLICITATION, 5,
                "send nudes", "suck my", "suck the", "ride me", "touch yourself",
                "make me cum", "make you cum", "open your legs", "open my legs",
                "not negotiable", "meet for sex", "fuck me", "fuck you until"));
        values.add(phrase(TextSmutConfig.CATEGORY_SOLICITATION, 3,
                "onlyfans", "fansly", "cam girl", "camgirl", "adult content", "link in bio"));
        return Collections.unmodifiableList(values);
    }

    private static Rule rule(String category, int points, String alternatives) {
        return new Rule(category, points, Pattern.compile("\\b(?:" + alternatives + ")\\b"));
    }

    private static Rule phrase(String category, int points, String... phrases) {
        StringBuilder pattern = new StringBuilder();
        for (String phrase : phrases) {
            if (pattern.length() > 0) pattern.append('|');
            pattern.append(Pattern.quote(phrase));
        }
        return new Rule(category, points, Pattern.compile("(?:" + pattern + ")"));
    }

    private static final class Rule {
        private final String category;
        private final int points;
        private final Pattern pattern;

        private Rule(String category, int points, Pattern pattern) {
            this.category = category;
            this.points = points;
            this.pattern = pattern;
        }
    }

    public static final class Match {
        private static final Match NONE = new Match(false, "", 0, 0f);
        private final boolean matched;
        private final String category;
        private final int score;
        private final float confidence;

        private Match(boolean matched, String category, int score, float confidence) {
            this.matched = matched;
            this.category = category;
            this.score = score;
            this.confidence = confidence;
        }

        public static Match none() { return NONE; }
        public boolean isMatched() { return matched; }
        public String getCategory() { return category; }
        public int getScore() { return score; }
        public float getConfidence() { return confidence; }
    }
}
