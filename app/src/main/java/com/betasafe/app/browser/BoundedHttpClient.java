package com.betasafe.app.browser;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Small bounded HTTP reader used only for explicit censor-before-save downloads. */
final class BoundedHttpClient {
    private BoundedHttpClient() {}

    static byte[] download(
            String url, String userAgent, String cookies, int maximumBytes) throws Exception {
        if (maximumBytes < 1) throw new IllegalArgumentException("Invalid download limit");
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setInstanceFollowRedirects(true);
        if (userAgent != null) connection.setRequestProperty("User-Agent", userAgent);
        if (cookies != null) connection.setRequestProperty("Cookie", cookies);
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalArgumentException("Unexpected HTTP status " + status);
            }
            int declared = connection.getContentLength();
            if (declared > maximumBytes) throw new IllegalArgumentException("Image too large");
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (total > maximumBytes) {
                        throw new IllegalArgumentException("Image too large");
                    }
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }
}
