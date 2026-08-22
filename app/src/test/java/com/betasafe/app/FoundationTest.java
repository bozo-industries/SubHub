package com.betasafe.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class FoundationTest {
    @Test
    public void packageIdentityIsStable() {
        assertEquals("com.betasafe.app", BuildConfig.APPLICATION_ID);
    }
}
