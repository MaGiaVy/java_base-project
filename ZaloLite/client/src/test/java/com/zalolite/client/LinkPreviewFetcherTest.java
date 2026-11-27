package com.zalolite.client;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

class LinkPreviewFetcherTest {

    @Test
    void extractsTitleFromRawHttpServer() throws Exception {
        try (TestHttpServer server = new TestHttpServer((path, out) -> {
            byte[] body = "<html><head><TITLE>  Đại học   Sài Gòn &amp; SGU </TITLE></head></html>"
                    .getBytes(StandardCharsets.UTF_8);
            writeResponse(out, "200 OK", "text/html; charset=utf-8", null, body);
        })) {
            String url = "http://127.0.0.1:" + server.port + "/home";
            assertEquals(url, LinkPreviewFetcher.extractFirstUrl("Xem tại " + url + "!"));
            assertNull(LinkPreviewFetcher.fetch(url), "Loopback must be blocked by default");

            LinkPreviewFetcher.Preview preview = LinkPreviewFetcher.fetch(url, true);
            assertNotNull(preview);
            assertEquals("Đại học Sài Gòn & SGU", preview.title());
            assertEquals("127.0.0.1", preview.host());
            assertEquals(url, preview.url());
        }
    }

    @Test
    void followsRedirectUpToLimit() throws Exception {
        try (TestHttpServer server = new TestHttpServer((path, out) -> {
            switch (path) {
                case "/one" -> writeResponse(out, "302 Found", null, "/final", new byte[0]);
                case "/final" -> writeResponse(out, "200 OK", "text/html", null,
                        "<html><title>Redirected OK</title></html>".getBytes(StandardCharsets.UTF_8));
                case "/loop1" -> writeResponse(out, "302 Found", null, "/loop2", new byte[0]);
                case "/loop2" -> writeResponse(out, "302 Found", null, "/loop3", new byte[0]);
                case "/loop3" -> writeResponse(out, "302 Found", null, "/loop1", new byte[0]);
                default -> writeResponse(out, "404 Not Found", null, null, new byte[0]);
            }
        })) {
            LinkPreviewFetcher.Preview ok = LinkPreviewFetcher.fetch(
                    "http://127.0.0.1:" + server.port + "/one", true);
            assertNotNull(ok);
            assertEquals("Redirected OK", ok.title());
            assertTrue(ok.url().endsWith("/final"));

            assertNull(LinkPreviewFetcher.fetch("http://127.0.0.1:" + server.port + "/loop1", true));
        }
    }

    @Test
    void returnsNullQuicklyOnTimeout() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (ServerSocket server = new ServerSocket(0)) {
            Thread silent = new Thread(() -> {
                try (Socket s = server.accept()) {
                    release.await(10, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                }
            }, "silent-http-server");
            silent.setDaemon(true);
            silent.start();

            long start = System.nanoTime();
            LinkPreviewFetcher.Preview preview = LinkPreviewFetcher.fetch(
                    "http://127.0.0.1:" + server.getLocalPort() + "/slow", true);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            release.countDown();

            assertNull(preview);
            assertTrue(elapsedMs < 3500, "Expected timeout within 3.5s, took " + elapsedMs + "ms");
        }
    }

    private static void writeResponse(OutputStream out, String status, String contentType,
                                      String location, byte[] body) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 ").append(status).append("\r\n");
            if (contentType != null) sb.append("Content-Type: ").append(contentType).append("\r\n");
            if (location != null) sb.append("Location: ").append(location).append("\r\n");
            sb.append("Content-Length: ").append(body.length).append("\r\n");
            sb.append("Connection: close\r\n\r\n");
            out.write(sb.toString().getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.flush();
        } catch (IOException ignored) {
        }
    }

    private static final class TestHttpServer implements AutoCloseable {
        final ServerSocket server;
        final int port;

        TestHttpServer(BiConsumer<String, OutputStream> handler) throws IOException {
            this.server = new ServerSocket(0);
            this.port = server.getLocalPort();
            Thread t = new Thread(() -> {
                while (!server.isClosed()) {
                    try (Socket s = server.accept()) {
                        BufferedReader r = new BufferedReader(
                                new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
                        String reqLine = r.readLine();
                        if (reqLine == null) continue;
                        String line;
                        while ((line = r.readLine()) != null && !line.isEmpty()) {
                            // consume headers
                        }
                        String[] parts = reqLine.split(" ");
                        String path = parts.length >= 2 ? parts[1] : "/";
                        handler.accept(path, s.getOutputStream());
                    } catch (IOException ignored) {
                    }
                }
            }, "test-http-server");
            t.setDaemon(true);
            t.start();
        }

        @Override
        public void close() throws IOException {
            server.close();
        }
    }
}
