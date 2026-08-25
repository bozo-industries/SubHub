package com.subhub.app.update;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public final class GitHubHttpTransportTest {
    private ServerSocket server;
    private String root;
    private Thread responder;

    @Before public void start() throws Exception {
        server = new ServerSocket(0, 16, java.net.InetAddress.getByName("127.0.0.1"));
        root = "http://127.0.0.1:" + server.getLocalPort();
        responder = new Thread(this::serve, "update-http-test");
        responder.start();
    }

    @After public void stop() throws Exception {
        server.close();
        responder.join(1000L);
    }

    private void serve() {
        while (!server.isClosed()) {
            try (Socket socket = server.accept()) {
                BufferedReader input = new BufferedReader(new InputStreamReader(
                        socket.getInputStream(), StandardCharsets.US_ASCII));
                String first = input.readLine();
                String path = first == null ? "/" : first.split(" ")[1];
                String header;
                do { header = input.readLine(); } while (header != null && !header.isEmpty());
                OutputStream output = socket.getOutputStream();
                if (path.equals("/cached")) write(output, "HTTP/1.1 304 Not Modified\r\nConnection: close\r\n\r\n");
                else if (path.equals("/limited")) write(output, "HTTP/1.1 403 Forbidden\r\nX-RateLimit-Remaining: 0\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
                else if (path.equals("/redirect")) write(output, "HTTP/1.1 302 Found\r\nLocation: " + root + "/ok\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
                else write(output, "HTTP/1.1 200 OK\r\nETag: test-etag\r\nContent-Length: 12\r\nConnection: close\r\n\r\nrelease-list");
            } catch (Exception ignored) { }
        }
    }

    private static void write(OutputStream output, String value) throws Exception {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
        output.flush();
    }

    @Test public void readsSuccessfulResponseAndEtag() throws Exception {
        GitHubReleaseRepository.HttpResponse response = GitHubReleaseRepository.get(root + "/ok", "", 1024);
        assertEquals(200, response.code);
        assertEquals("release-list", response.body);
        assertEquals("test-etag", response.etag);
    }

    @Test public void preservesNotModifiedStatus() throws Exception {
        assertEquals(304, GitHubReleaseRepository.get(root + "/cached", "test-etag", 1024).code);
    }

    @Test public void followsAssetRedirects() throws Exception {
        assertEquals("release-list", GitHubReleaseRepository.get(root + "/redirect", "", 1024).body);
    }

    @Test public void exposesRateLimitHeaders() throws Exception {
        GitHubReleaseRepository.HttpResponse response = GitHubReleaseRepository.get(root + "/limited", "", 1024);
        assertEquals(403, response.code);
        assertEquals("0", response.rateRemaining);
    }
}
