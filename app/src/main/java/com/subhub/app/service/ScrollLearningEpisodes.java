package com.subhub.app.service;

/** Independent scroll episodes without requesting or intercepting touch input. */
final class ScrollLearningEpisodes {
    private static final long SEPARATION_MS = 500;
    private AutomaticScrollLearningObserver.Scope scope;
    private long lastTime = -1, lastTouch, episode;

    long observe(AutomaticScrollLearningObserver.Scope next, long sourceTime, long touchId) {
        if (next == null || sourceTime <= 0 || scope != null && next.equals(scope) && sourceTime <= lastTime) {
            breakInterval();
            return 0;
        }
        if (!next.equals(scope) || lastTime < 0 || sourceTime - lastTime >= SEPARATION_MS
                || touchId > 0 && touchId != lastTouch) episode++;
        scope = next; lastTime = sourceTime; lastTouch = touchId;
        return episode;
    }

    void breakInterval() { scope = null; lastTime = -1; lastTouch = 0; }
}
