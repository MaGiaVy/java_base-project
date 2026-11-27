package com.zalolite.client;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Socket HTTP/HTTPS link preview fetcher (no java.net.http or third-party HTTP clients).
 */
public final class LinkPreviewFetcher {
    private static final int TIMEOUT_MS = 2000;
    private static final int MAX_REDIRECTS = 2;
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\"]+");
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public record Preview(String url, String host, String title) {}

    private LinkPreviewFetcher() {}

    public static String extractFirstUrl(String text) {
        if (text == null || text.isBlank()) return null;
        Matcher m = URL_PATTERN.matcher(text);
        if (!m.find()) return null;
        String url = m.group();
        while (!url.isEmpty() && ".,)!;]".indexOf(url.charAt(url.length() - 1)) >= 0) {
            url = url.substring(0, url.length() - 1);
        }
        return url.isEmpty() ? null : url;
    }

    public static Preview fetch(String url) {
        return fetch(url, false);
    }

    public static Preview fetch(String url, boolean allowLoopback) {
        if (url == null || url.isBlank()) return null;
        String currentUrl = url.trim();
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            URI uri;
            try {
                uri = URI.create(currentUrl);
            } catch (IllegalArgumentException e) {
                return null;
            }
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) return null;
            String host = uri.getHost();
            if (host == null || host.isBlank()) return null;
            int defaultPort = "https".equals(scheme) ? 443 : 80;
            int port = uri.getPort() > 0 ? uri.getPort() : defaultPort;

            InetAddress targetAddr = resolveAllowedAddress(host, allowLoopback);
            if (targetAddr == null) return null;

            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) path = "/";
            if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
            String hostHeader = port == defaultPort ? host : host + ":" + port;

            try (Socket socket = openSocket(scheme, host, targetAddr, port)) {
                String request = "GET " + path + " HTTP/1.1\r\n"
                        + "Host: " + hostHeader + "\r\n"
                        + "User-Agent: ZaloLite/1.0\r\n"
                        + "Accept: text/html\r\n"
                        + "Connection: close\r\n\r\n";
                OutputStream out = socket.getOutputStream();
                out.write(request.getBytes(StandardCharsets.US_ASCII));
                out.flush();

                BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
                String statusLine = readLine(in);
                if (statusLine == null) return null;
                int status = parseStatusCode(statusLine);
                if (status < 0) return null;

                String location = null;
                String line;
                while ((line = readLine(in)) != null && !line.isEmpty()) {
                    int colon = line.indexOf(':');
                    if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase("location")) {
                        location = line.substring(colon + 1).trim();
                    }
                }

                if (isRedirect(status)) {
                    if (location == null || location.isEmpty() || hop == MAX_REDIRECTS) {
                        return null;
                    }
                    try {
                        currentUrl = uri.resolve(location).toString();
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                    continue;
                }

                if (status < 200 || status >= 300) return null;

                byte[] bodyBytes = readUpTo(in, MAX_BODY_BYTES);
                String html = new String(bodyBytes, StandardCharsets.UTF_8);
                String title = extractTitle(html);
                return title == null ? null : new Preview(currentUrl, host, title);
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    private static InetAddress resolveAllowedAddress(String host, boolean allowLoopback) {
        try {
            InetAddress[] addrs = InetAddress.getAllByName(host);
            if (addrs.length == 0) return null;
            if (!allowLoopback) {
                for (InetAddress addr : addrs) {
                    if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                            || addr.isLinkLocalAddress() || addr.isAnyLocalAddress()
                            || addr.isMulticastAddress()) {
                        return null;
                    }
                }
            }
            return addrs[0];
        } catch (IOException e) {
            return null;
        }
    }

    private static Socket openSocket(String scheme, String host, InetAddress addr, int port) throws IOException {
        Socket plain = new Socket();
        plain.connect(new InetSocketAddress(addr, port), TIMEOUT_MS);
        plain.setSoTimeout(TIMEOUT_MS);
        if ("https".equals(scheme)) {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket ssl = (SSLSocket) factory.createSocket(plain, host, port, true);
            ssl.setSoTimeout(TIMEOUT_MS);
            ssl.startHandshake();
            return ssl;
        }
        return plain;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(128);
        int b;
        boolean any = false;
        while ((b = in.read()) != -1) {
            any = true;
            if (b == '\n') break;
            if (b != '\r') buf.write(b);
            if (buf.size() > 8192) break;
        }
        return any ? buf.toString(StandardCharsets.ISO_8859_1) : null;
    }

    private static int parseStatusCode(String statusLine) {
        String[] parts = statusLine.split(" ");
        if (parts.length < 2) return -1;
        try {
            return Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static byte[] readUpTo(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buf = new byte[4096];
        int remaining = maxBytes;
        while (remaining > 0) {
            int n = in.read(buf, 0, Math.min(buf.length, remaining));
            if (n < 0) break;
            out.write(buf, 0, n);
            remaining -= n;
        }
        return out.toByteArray();
    }

    private static String extractTitle(String html) {
        Matcher m = TITLE_PATTERN.matcher(html);
        if (!m.find()) return null;
        String raw = m.group(1)
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
        return raw.isEmpty() ? null : raw;
    }
}
