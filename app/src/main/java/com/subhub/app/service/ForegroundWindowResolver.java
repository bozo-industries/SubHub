package com.subhub.app.service;

import com.subhub.app.appmode.AppModePolicy;

import java.util.Collections;
import java.util.List;

/** Selects the real foreground application window from Accessibility's interactive windows. */
final class ForegroundWindowResolver {
    private ForegroundWindowResolver() {}

    static Candidate select(List<Candidate> candidates, String inputMethodPackage) {
        List<Candidate> source = candidates == null ? Collections.emptyList() : candidates;
        Candidate best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Candidate candidate : source) {
            if (candidate == null || !AppModePolicy.shouldAcceptLiveForegroundPackage(
                    candidate.packageName, inputMethodPackage)) continue;
            int score = (candidate.focused ? 2_000_000 : 0)
                    + (candidate.active ? 1_000_000 : 0)
                    + candidate.layer;
            if (best == null || score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    static final class Candidate {
        final String packageName;
        final int windowId;
        final boolean active;
        final boolean focused;
        final int layer;

        Candidate(String packageName, int windowId, boolean active, boolean focused, int layer) {
            this.packageName = packageName == null ? "" : packageName;
            this.windowId = windowId;
            this.active = active;
            this.focused = focused;
            this.layer = layer;
        }
    }
}
