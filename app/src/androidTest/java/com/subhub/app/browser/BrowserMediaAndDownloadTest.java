package com.subhub.app.browser;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.subhub.app.R;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public final class BrowserMediaAndDownloadTest {
    @Test public void fullScreenCustomViewEntersAndExitsCleanly() {
        try (ActivityScenario<BrowserActivity> scenario =
                     ActivityScenario.launch(BrowserActivity.class)) {
            scenario.onActivity(activity -> {
                View video = new View(activity);
                AtomicBoolean callback = new AtomicBoolean();
                activity.showFullscreen(video, () -> callback.set(true));
                assertEquals(View.GONE,
                        activity.findViewById(R.id.browser_content).getVisibility());
                assertTrue(video.getParent() == activity.findViewById(R.id.browser_root));
                activity.hideFullscreen();
                assertEquals(View.VISIBLE,
                        activity.findViewById(R.id.browser_content).getVisibility());
                assertTrue(callback.get());
            });
        }
    }

    @Test public void explicitImageDownloadIsBoundedAndForwardsSessionHeaders()
            throws Exception {
        byte[] body = "synthetic-image-body".getBytes(StandardCharsets.UTF_8);
        AtomicReference<String> request = new AtomicReference<>("");
        try (ServerSocket server = new ServerSocket(0)) {
            Thread responder = responder(server, body, request);
            byte[] result = BoundedHttpClient.download(
                    "http://127.0.0.1:" + server.getLocalPort() + "/image.png",
                    "SubHub-Test", "session=local", 1024);
            responder.join(3000L);
            assertArrayEquals(body, result);
            assertTrue(request.get().contains("User-Agent: SubHub-Test"));
            assertTrue(request.get().contains("Cookie: session=local"));
        }

        try (ServerSocket server = new ServerSocket(0)) {
            byte[] oversized = new byte[64];
            Thread responder = responder(server, oversized, new AtomicReference<>());
            try {
                BoundedHttpClient.download(
                        "http://127.0.0.1:" + server.getLocalPort() + "/large.png",
                        null, null, 8);
                fail("Oversized downloads must be rejected");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("large"));
            }
            responder.join(3000L);
        }
    }

    private static Thread responder(
            ServerSocket server, byte[] body, AtomicReference<String> request) {
        Thread thread = new Thread(() -> {
            try (Socket socket = server.accept()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        socket.getInputStream(), StandardCharsets.US_ASCII));
                StringBuilder headers = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    headers.append(line).append('\n');
                }
                request.set(headers.toString());
                OutputStream output = socket.getOutputStream();
                output.write(("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: "
                        + body.length + "\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                output.write(body);
                output.flush();
            } catch (Exception error) {
                request.set("ERROR: " + error.getClass().getSimpleName());
            }
        }, "bounded-download-test-server");
        thread.start();
        return thread;
    }
}
