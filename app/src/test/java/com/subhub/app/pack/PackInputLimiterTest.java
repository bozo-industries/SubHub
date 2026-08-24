package com.subhub.app.pack;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class PackInputLimiterTest {
    @Test public void acceptsInputAtTheBoundary() throws Exception {
        byte[] source = new byte[4096];
        for (int index = 0; index < source.length; index++) source[index] = (byte) index;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertEquals(source.length, PackInputLimiter.copy(
                new ByteArrayInputStream(source), output, source.length, "Test pack"));
        assertArrayEquals(source, output.toByteArray());
    }

    @Test public void rejectsInputPastTheBoundaryWithActionableMessage() throws Exception {
        try {
            PackInputLimiter.copy(
                    new ByteArrayInputStream(new byte[2049]),
                    new ByteArrayOutputStream(),
                    2048,
                    "Test pack");
            fail("Oversized input should be rejected");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Test pack"));
            assertTrue(expected.getMessage().contains("limit"));
        }
    }
}
