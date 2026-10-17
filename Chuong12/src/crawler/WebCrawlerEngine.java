package crawler;

import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;

/**
 * Lõi Cào Web (Web Crawler Engine) xây dựng bằng Socket & SSLSocket thuần JDK.
 * Không phụ thuộc thư viện ngoài (Zero Dependency).
 * 
 * Khả năng:
 * 1. Tải trang web ngoài Internet qua giao thức HTTP (cổng 80) hoặc HTTPS (cổng 443).
 * 2. Tự động chuyển hướng (Redirect 301, 302, 303, 307, 308).
 * 3. Bóc tách và giải mã Chunked Transfer-Encoding (khử hoàn toàn mã hex chunk).
 * 4. Tự động gắn thẻ <base href="..."> để bảo toàn hình ảnh, style và đường dẫn gốc.
 * 5. Lưu vào thư mục webroot của Server 1 / Server 2 và tùy chọn đồng bộ sang IIS.
 */
public class WebCrawlerEngine {

    private static final int TIMEOUT_MS = 10000;
    private static SSLSocketFactory trustingSslSocketFactory;

    static {
        try {
            // Khởi tạo TrustManager bỏ qua kiểm tra chứng chỉ (hỗ trợ SSL/TLS linh hoạt)
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new SecureRandom());
            trustingSslSocketFactory = sc.getSocketFactory();
        } catch (Exception e) {
            trustingSslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        }
    }

    /**
     * Kết quả cào trang web
     */
    public static class CrawlResult {
        public final int statusCode;
        public final String statusLine;
        public final String originalUrl;
        public final String finalUrl;
        public final String domain;
        public final String suggestedFilename;
        public final String htmlContent;
        public final long durationMs;
        public final boolean isHttps;

        public CrawlResult(int statusCode, String statusLine, String originalUrl, String finalUrl,
                           String domain, String suggestedFilename, String htmlContent,
                           long durationMs, boolean isHttps) {
            this.statusCode = statusCode;
            this.statusLine = statusLine;
            this.originalUrl = originalUrl;
            this.finalUrl = finalUrl;
            this.domain = domain;
            this.suggestedFilename = suggestedFilename;
            this.htmlContent = htmlContent;
            this.durationMs = durationMs;
            this.isHttps = isHttps;
        }

        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 400;
        }
    }

    /**
     * Cào trang web từ địa chỉ URL chỉ định
     */
    public static CrawlResult crawl(String urlString) throws Exception {
        return executeCrawl(urlString, urlString, 0);
    }

    private static CrawlResult executeCrawl(String originalUrl, String currentUrl, int redirectCount) throws Exception {
        if (redirectCount > 5) {
            throw new IOException("Quá số lần chuyển hướng cho phép (Redirect loop > 5)!");
        }

        long startTime = System.currentTimeMillis();
        URI uri = new URI(currentUrl);
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Địa chỉ URL không hợp lệ: Thiếu Host!");
        }

        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "http";
        boolean isHttps = "https".equals(scheme);
        int port = uri.getPort();
        if (port == -1) {
            port = isHttps ? 443 : 80;
        }

        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        String query = uri.getRawQuery();
        if (query != null && !query.isEmpty()) {
            path += "?" + query;
        }

        Socket socket = null;
        try {
            if (isHttps) {
                socket = trustingSslSocketFactory.createSocket();
                socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
                socket.setSoTimeout(TIMEOUT_MS);
                ((SSLSocket) socket).startHandshake();
            } else {
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
                socket.setSoTimeout(TIMEOUT_MS);
            }

            OutputStream out = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), false);

            // Gửi HTTP Request Header chuẩn
            writer.print("GET " + path + " HTTP/1.1\r\n");
            writer.print("Host: " + host + (port != 80 && port != 443 ? ":" + port : "") + "\r\n");
            writer.print("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36\r\n");
            writer.print("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\n");
            writer.print("Accept-Language: vi,en-US;q=0.9,en;q=0.8\r\n");
            writer.print("Connection: close\r\n");
            writer.print("\r\n");
            writer.flush();

            BufferedInputStream bin = new BufferedInputStream(socket.getInputStream());
            String statusLine = readAsciiLine(bin);
            if (statusLine == null) {
                throw new IOException("Máy chủ đóng kết nối đột ngột mà không phản hồi.");
            }

            Map<String, String> headers = new LinkedHashMap<>();
            String line;
            while ((line = readAsciiLine(bin)) != null && !line.trim().isEmpty()) {
                int colon = line.indexOf(":");
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim().toLowerCase(), line.substring(colon + 1).trim());
                }
            }

            int statusCode = parseStatusCode(statusLine);

            // Xử lý chuyển hướng (Redirect 301, 302, 303, 307, 308)
            if (statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308) {
                String location = headers.get("location");
                if (location != null && !location.trim().isEmpty()) {
                    try { socket.close(); } catch (Exception ignored) {}
                    URI nextUri = uri.resolve(location.trim());
                    return executeCrawl(originalUrl, nextUri.toString(), redirectCount + 1);
                }
            }

            boolean isChunked = false;
            int contentLength = -1;
            String charsetName = "UTF-8";

            if (headers.containsKey("transfer-encoding") && headers.get("transfer-encoding").toLowerCase().contains("chunked")) {
                isChunked = true;
            }
            if (headers.containsKey("content-length")) {
                try {
                    contentLength = Integer.parseInt(headers.get("content-length").trim());
                } catch (Exception ignored) {}
            }
            if (headers.containsKey("content-type")) {
                String ct = headers.get("content-type").toLowerCase();
                int idx = ct.indexOf("charset=");
                if (idx != -1) {
                    String cs = ct.substring(idx + 8).trim();
                    int end = cs.indexOf(';');
                    if (end != -1) cs = cs.substring(0, end).trim();
                    if (!cs.isEmpty()) charsetName = cs;
                }
            }

            byte[] bodyBytes;
            if (isChunked) {
                bodyBytes = readChunkedStream(bin);
            } else if (contentLength >= 0) {
                bodyBytes = readFixedStream(bin, contentLength);
            } else {
                bodyBytes = readAllRemainingStream(bin);
            }

            String body;
            try {
                body = new String(bodyBytes, Charset.forName(charsetName));
            } catch (Exception e) {
                body = new String(bodyBytes, StandardCharsets.UTF_8);
            }

            body = cleanChunkArtifacts(body);

            // Chèn thẻ <base href="..."> để các tài nguyên (ảnh, CSS) của web thật tải chính xác
            String processedHtml = injectBaseHref(body, currentUrl);

            long duration = System.currentTimeMillis() - startTime;
            String domain = extractCleanDomain(currentUrl);
            String suggestedFilename = "crawled_" + domain + ".htm";

            return new CrawlResult(statusCode, statusLine, originalUrl, currentUrl, domain, suggestedFilename, processedHtml, duration, isHttps);

        } finally {
            if (socket != null && !socket.isClosed()) {
                try { socket.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static String readAsciiLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
        }
        if (sb.length() == 0 && c == -1) return null;
        return sb.toString();
    }

    private static byte[] readChunkedStream(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (true) {
            String hexLine = readAsciiLine(in);
            if (hexLine == null) break;
            hexLine = hexLine.trim();
            if (hexLine.isEmpty()) continue;
            int semi = hexLine.indexOf(';');
            if (semi != -1) hexLine = hexLine.substring(0, semi).trim();
            int chunkSize;
            try {
                chunkSize = Integer.parseInt(hexLine, 16);
            } catch (NumberFormatException e) {
                break;
            }
            if (chunkSize == 0) {
                while (true) {
                    String trailer = readAsciiLine(in);
                    if (trailer == null || trailer.trim().isEmpty()) break;
                }
                break;
            }
            byte[] chunk = new byte[chunkSize];
            int totalRead = 0;
            while (totalRead < chunkSize) {
                int r = in.read(chunk, totalRead, chunkSize - totalRead);
                if (r == -1) break;
                totalRead += r;
            }
            baos.write(chunk, 0, totalRead);
            readAsciiLine(in); // Bỏ \r\n cuối chunk
        }
        return baos.toByteArray();
    }

    private static byte[] readFixedStream(InputStream in, int length) throws IOException {
        byte[] data = new byte[length];
        int totalRead = 0;
        while (totalRead < length) {
            int r = in.read(data, totalRead, length - totalRead);
            if (r == -1) break;
            totalRead += r;
        }
        return data;
    }

    private static byte[] readAllRemainingStream(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) != -1) {
            baos.write(buf, 0, r);
        }
        return baos.toByteArray();
    }

    private static String cleanChunkArtifacts(String body) {
        if (body == null || body.isEmpty()) return "";
        body = body.replaceFirst("^[0-9a-fA-F]{1,8}\\r?\\n(?=[\\s<])", "");
        body = body.replaceFirst("(?s)\\r?\\n0\\r?\\n*$", "");
        return body;
    }

    private static int parseStatusCode(String statusLine) {
        try {
            String[] parts = statusLine.split(" ");
            if (parts.length >= 2) return Integer.parseInt(parts[1]);
        } catch (Exception ignored) {}
        return 0;
    }

    public static String extractCleanDomain(String urlStr) {
        try {
            URI u = new URI(urlStr);
            String host = u.getHost();
            if (host != null) {
                host = host.toLowerCase().replaceAll("^www\\.", "");
                int dotIdx = host.indexOf('.');
                String mainPart = (dotIdx != -1) ? host.substring(0, dotIdx) : host;
                mainPart = mainPart.replaceAll("[^a-zA-Z0-9_-]", "");
                if (!mainPart.isEmpty()) return mainPart;
            }
        } catch (Exception ignored) {}
        return "site";
    }

    public static String injectBaseHref(String html, String targetUrl) {
        if (html == null) return "";
        String baseUrl = targetUrl;
        if (!baseUrl.endsWith("/")) {
            int lastSlash = baseUrl.lastIndexOf('/');
            if (lastSlash > 8) { // sau http://
                baseUrl = baseUrl.substring(0, lastSlash + 1);
            } else {
                baseUrl += "/";
            }
        }

        String baseTag = "<base href=\"" + baseUrl + "\">";
        // Gỡ bỏ thẻ base cũ nếu có
        String cleaned = html.replaceAll("(?i)<base\\s+[^>]*>", "");

        if (cleaned.toLowerCase().contains("<head>")) {
            return cleaned.replaceFirst("(?i)<head>", "<head>\n    " + baseTag);
        } else {
            return baseTag + "\n" + cleaned;
        }
    }

    /**
     * Lưu trang HTML đã cào vào thư mục chỉ định của Server (webroot1 hoặc webroot2)
     */
    public static File saveHtmlToWebroot(String htmlContent, File webrootDir, String filename) throws IOException {
        File crawledDir = new File(webrootDir, "crawled");
        if (!crawledDir.exists()) {
            crawledDir.mkdirs();
        }

        if (!filename.toLowerCase().endsWith(".htm") && !filename.toLowerCase().endsWith(".html")) {
            filename += ".htm";
        }

        File targetFile = new File(crawledDir, filename);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8)) {
            writer.write(htmlContent);
        }
        return targetFile;
    }

    /**
     * Đồng bộ trang cào sang thư mục IIS của Windows (C:\inetpub\site1) nếu người dùng có bật IIS
     */
    public static boolean syncToIIS(File localFile, String iisDirPath) {
        try {
            File iisDir = new File(iisDirPath);
            if (iisDir.exists() && iisDir.isDirectory()) {
                File target = new File(iisDir, localFile.getName());
                java.nio.file.Files.copy(localFile.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static void main(String[] args) {
        String testUrl = (args.length > 0) ? args[0] : "https://example.com";
        System.out.println("[*] Bắt đầu cào thử nghiệm: " + testUrl);
        try {
            CrawlResult res = crawl(testUrl);
            System.out.printf("[OK] Cào thành công! Status: %d (%s), Domain: %s, Kích thước: %,d bytes, Thời gian: %d ms\n",
                    res.statusCode, res.statusLine, res.domain, res.htmlContent.length(), res.durationMs);
            File saved = saveHtmlToWebroot(res.htmlContent, new File("webroot1"), res.suggestedFilename);
            System.out.println("[+] Đã lưu vào file: " + saved.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[LOI] Cào thất bại: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
