package com.subhub.app.browser;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AdBlockerTest {
    @Test
    public void blocksExactAndSubdomainHostsWithoutSubstringFalsePositives() {
        assertTrue(AdBlocker.shouldBlock("https://securepubads.doubleclick.net/script.js"));
        assertFalse(AdBlocker.shouldBlock("https://notdoubleclick.net/page"));
        assertFalse(AdBlocker.shouldBlock("https://example.com/?next=doubleclick.net"));
    }
}
