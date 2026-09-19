package com.subhub.app.service;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ScrollLearningBudgetTest {
    @Test public void disarmedCannotStartOrReserveAProbe() {
        ScrollLearningBudget budget = new ScrollLearningBudget();
        assertFalse(budget.begin(false, 1000));
        assertEquals(0, budget.reserve(false, 1000));
        assertTrue(budget.begin(true, 1001));
    }

    @Test public void everyAttemptCountsEvenWhenNoUsableEvidenceIsReturned() {
        ScrollLearningBudget budget = new ScrollLearningBudget();
        budget.begin(true, 1000);
        for (int index = 0; index < 96; index++) {
            long token = budget.reserve(true, 1000 + index);
            assertNotEquals(0, token);
            budget.finish(token, 0, 1000 + index);
        }
        assertEquals(96, budget.attempts());
        assertEquals(0, budget.reserve(true, 1100));
        assertFalse(budget.begin(true, 1101));
        assertTrue(budget.begin(true, budget.cooldownUntil()));
    }

    @Test public void singleSlowReadAndAggregateWorkBothBackOff() {
        ScrollLearningBudget slow = new ScrollLearningBudget();
        slow.begin(true, 1000);
        long token = slow.reserve(true, 1000);
        slow.finish(token, 17, 1017);
        assertEquals(17, slow.workMillis());
        assertEquals(0, slow.reserve(true, 1018));
        ScrollLearningBudget aggregate = new ScrollLearningBudget();
        aggregate.begin(true, 1000);
        for (int index = 0; index < 16; index++) {
            long lease = aggregate.reserve(true, 1000 + index * 20);
            assertNotEquals(0, lease);
            aggregate.finish(lease, 16, 1016 + index * 20);
        }
        assertEquals(256, aggregate.workMillis());
        assertEquals(0, aggregate.reserve(true, 1400));
    }

    @Test public void burstIsBoundedAndCannotHaveConcurrentProbeReservations() {
        ScrollLearningBudget budget = new ScrollLearningBudget();
        budget.begin(true, 1000);
        long token = budget.reserve(true, 1000);
        assertEquals(0, budget.reserve(true, 1001));
        budget.finish(token, 1, 1001);
        assertEquals(0, budget.reserve(true, 7000));
    }

    @Test public void staleCompletionCannotChargeANewBurst() {
        ScrollLearningBudget budget = new ScrollLearningBudget();
        budget.begin(true, 1000);
        long old = budget.reserve(true, 1000);
        budget.stop(1001);
        budget.begin(true, 32000);
        long current = budget.reserve(true, 32000);
        budget.finish(old, 1000, 32001);
        assertEquals(0, budget.workMillis());
        assertEquals(0, budget.reserve(true, 32001));
        budget.finish(current, 5, 32005);
        assertEquals(5, budget.workMillis());
        assertNotEquals(0, budget.reserve(true, 32006));
    }
}
