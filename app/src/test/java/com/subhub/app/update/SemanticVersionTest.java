package com.subhub.app.update;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public final class SemanticVersionTest {
    @Test public void releaseSortsAfterItsPrerelease() {
        assertTrue(SemanticVersion.parse("v0.4.0")
                .compareTo(SemanticVersion.parse("0.4.0-rc.2")) > 0);
    }

    @Test public void numericPrereleaseIdentifiersSortNumerically() {
        assertTrue(SemanticVersion.parse("0.4.0-beta.10")
                .compareTo(SemanticVersion.parse("0.4.0-beta.2")) > 0);
    }

    @Test public void newerMinorSortsAfterStablePatch() {
        assertTrue(SemanticVersion.parse("0.5.0-alpha.1")
                .compareTo(SemanticVersion.parse("0.4.99")) > 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void malformedTagIsRejected() {
        SemanticVersion.parse("release-latest");
    }
}
