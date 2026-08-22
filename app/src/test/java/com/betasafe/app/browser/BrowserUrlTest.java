package com.betasafe.app.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class BrowserUrlTest {
    @Test
    public void domainInputUsesHttps() {
        assertEquals("https://example.com", BrowserUrl.fromInput("example.com"));
    }

    @Test
    public void wordsBecomeEncodedSearch() {
        assertEquals(
                "https://www.google.com/search?q=two+words",
                BrowserUrl.fromInput("two words"));
    }

    @Test
    public void executableAndLocalSchemesAreNotWebUrls() {
        assertFalse(BrowserUrl.isWebUrl("javascript:alert(1)"));
        assertFalse(BrowserUrl.isWebUrl("file:///private/file"));
        assertTrue(BrowserUrl.isWebUrl("https://example.com/path"));
    }
}
