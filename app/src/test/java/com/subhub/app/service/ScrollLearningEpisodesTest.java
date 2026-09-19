package com.subhub.app.service;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ScrollLearningEpisodesTest {
    private static AutomaticScrollLearningObserver.Scope scope(long producer) {
        return new AutomaticScrollLearningObserver.Scope("com.example.app", 1, 1, producer,
                7, 1000, 2000, 420, 0, 60000, ScrollLearningKey.Axis.Y, ScrollLearningKey.Evidence.ABSOLUTE);
    }
    @Test public void ordinaryScrollingDoesNotNeedTouchExplorationEvents() {
        ScrollLearningEpisodes episodes = new ScrollLearningEpisodes();
        long first = episodes.observe(scope(1), 100, 0);
        assertTrue(first > 0);
        assertEquals(first, episodes.observe(scope(1), 200, 0));
        assertEquals(first, episodes.observe(scope(1), 699, 0));
        assertTrue(episodes.observe(scope(1), 1199, 0) > first);
    }
    @Test public void continuousFlingCannotSupplyMultipleHoldoutEpisodes() {
        ScrollLearningEpisodes episodes = new ScrollLearningEpisodes();
        long first = episodes.observe(scope(1), 100, 0);
        for (long time = 200; time < 6000; time += 100) assertEquals(first, episodes.observe(scope(1), time, 0));
    }
    @Test public void actualTouchStartCanSeparateEpisodesWhenAvailable() {
        ScrollLearningEpisodes episodes = new ScrollLearningEpisodes();
        long first = episodes.observe(scope(1), 100, 1);
        assertEquals(first, episodes.observe(scope(1), 200, 1));
        assertTrue(episodes.observe(scope(1), 250, 2) > first);
    }
    @Test public void sourceChangeOrExplicitBreakCannotReuseEpisodeLineage() {
        ScrollLearningEpisodes episodes = new ScrollLearningEpisodes();
        long first = episodes.observe(scope(1), 100, 0);
        long second = episodes.observe(scope(2), 200, 0);
        assertTrue(second > first);
        episodes.breakInterval();
        assertTrue(episodes.observe(scope(2), 300, 0) > second);
    }
    @Test public void outOfOrderAndUnknownScopeNeverBecomeEvidence() {
        ScrollLearningEpisodes episodes = new ScrollLearningEpisodes();
        long first = episodes.observe(scope(1), 100, 0);
        assertEquals(0, episodes.observe(scope(1), 100, 0));
        assertTrue(episodes.observe(scope(1), 200, 0) > first);
        assertEquals(0, episodes.observe(null, 300, 0));
    }
}
