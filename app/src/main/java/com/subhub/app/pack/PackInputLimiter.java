package com.subhub.app.pack;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/** Absolute archive bounds that allow image-heavy packs without permitting unbounded input. */
final class PackInputLimiter {
    static final long MAX_ARCHIVE_BYTES = 256L * 1024L * 1024L;
    static final long MAX_ENTRY_BYTES = 96L * 1024L * 1024L;
    static final long MAX_TOTAL_BYTES = 512L * 1024L * 1024L;
    static final int MAX_ENTRIES = 512;

    private PackInputLimiter() {}

    static long copy(InputStream input, OutputStream output, long limit, String label)
            throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > limit) {
                throw new IOException(String.format(
                        Locale.ROOT,
                        "%s is larger than the supported %d MiB limit",
                        label,
                        limit / (1024L * 1024L)));
            }
            output.write(buffer, 0, read);
        }
        output.flush();
        return total;
    }
}
