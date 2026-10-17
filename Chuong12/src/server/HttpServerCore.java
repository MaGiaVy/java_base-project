package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Lõi HTTP Server xây dựng theo kiến thức Chương 12 (Slide 11 - 18)
 * Sử dụng com.sun.net.httpserver.HttpServer chuẩn JDK
 */
public class HttpServerCore {

    private final String serverName;
    private final int port;
    private final File webRoot;
    private final Consumer<String> logger;
    private HttpServer server;
    private boolean running = false;

    @FunctionalInterface
    public interface IngestApprovalListener {
        boolean approveIngest(String serverName, String sourceUrl, String filename, int contentSize);
    }

    private IngestApprovalListener approvalListener;

    public void setApprovalListener(IngestApprovalListener listener) {
        this.approvalListener = listener;
    }

    public HttpServerCore(String serverName, int port, File webRoot, Consumer<String> logger) {
        this.serverName = serverName;
        this.port = port;
        this.webRoot = webRoot;
        this.logger = (logger != null) ? logger : System.out::println;
    }

    public synchronized void start() throws IOException {
        if (running) return;

        // 1. Khởi tạo server tại cổng chỉ định (Slide 13)
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // Slide 15: Xử lý đa luồng với ThreadPool
        server.setExecutor(Executors.newFixedThreadPool(10));

        // 2. Đăng ký các Router Handler (Slide 14, 18)
        server.createContext("/", new StaticFileHandler());
        server.createContext("/api/time", new ApiTimeHandler());
        server.createContext("/api/message", new ApiMessageHandler());
        server.createContext("/api/ingest", new ApiIngestHandler());

        server.start();
        running = true;

        log(String.format(">>> [%s] ĐÃ KHỞI ĐỘNG thành công tại http://localhost:%d/ (Webroot: %s)",
                serverName, port, webRoot.getAbsolutePath()));
    }

    public synchronized void stop() {
        if (!running || server == null) return;
        server.stop(1);
        running = false;
        log(String.format("<<< [%s] ĐÃ DỪNG hoạt động trên cổng %d.", serverName, port));
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public String getServerName() {
        return serverName;
    }

    private void log(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        logger.accept(String.format("[%s] %s", timestamp, message));
    }

    // =========================================================================
    // 1. Handler Phục vụ Tệp Tĩnh (HTML P1.HTM, P2.HTM,...)
    // =========================================================================
    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                String rawPath = exchange.getRequestURI().getPath();
                String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();

                if (!"GET".equalsIgnoreCase(method)) {
                    sendResponse(exchange, 405, "text/plain; charset=utf-8", "405 Method Not Allowed");
                    log(String.format("[%s] 405 Method Not Allowed: %s %s từ %s", serverName, method, rawPath, clientIp));
                    return;
                }

                // Nếu người dùng chỉ vào "/", tự động trỏ về trang mặc định của server
                String filename = rawPath;
                if (filename == null || filename.equals("/") || filename.isEmpty()) {
                    filename = (port == 8080) ? "/P1.HTM" : "/P3.HTM";
                }

                // Chuẩn hóa tên file
                if (filename.startsWith("/")) {
                    filename = filename.substring(1);
                }

                File targetFile = new File(webRoot, filename).getCanonicalFile();

                // Kiểm tra tệp có nằm trong thư mục webroot hay không (chống Directory Traversal)
                if (targetFile.exists() && targetFile.isFile() && targetFile.getPath().startsWith(webRoot.getCanonicalPath())) {
                    byte[] data = Files.readAllBytes(targetFile.toPath());
                    exchange.getResponseHeaders().add("Content-Type", determineContentType(filename));
                    exchange.getResponseHeaders().add("Server", serverName + "/JavaHttpServer");
                    exchange.sendResponseHeaders(200, data.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(data);
                    }
                    log(String.format("[%s] 200 OK: GET /%s (%d bytes) từ %s", serverName, filename, data.length, clientIp));
                } else {
                    // Trả 404 Not Found theo giao diện Đỏ - Trắng
                    String notFoundHtml = generate404Html(rawPath);
                    byte[] data = notFoundHtml.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                    exchange.getResponseHeaders().add("Server", serverName + "/JavaHttpServer");
                    exchange.sendResponseHeaders(404, data.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(data);
                    }
                    log(String.format("[%s] 404 Not Found: GET %s từ %s", serverName, rawPath, clientIp));
                }
            } catch (Exception e) {
                log(String.format("[%s] Lỗi xử lý request: %s", serverName, e.getMessage()));
                sendResponse(exchange, 500, "text/plain; charset=utf-8", "500 Internal Server Error");
            } finally {
                exchange.close();
            }
        }
    }

    // =========================================================================
    // 2. Handler API /api/time (Slide 14 & 16)
    // =========================================================================
    private class ApiTimeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                if (!"GET".equalsIgnoreCase(method)) {
                    sendResponse(exchange, 405, "application/json", "{\"error\":\"Chỉ chấp nhận GET\"}");
                    return;
                }

                LocalDateTime now = LocalDateTime.now();
                String timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                String dateStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                String json = String.format("{\"server\":\"%s\",\"port\":%d,\"time\":\"%s\",\"date\":\"%s\"}",
                        serverName, port, timeStr, dateStr);

                sendResponse(exchange, 200, "application/json; charset=UTF-8", json);
                log(String.format("[%s] 200 OK: GET /api/time -> %s", serverName, json));
            } finally {
                exchange.close();
            }
        }
    }

    // =========================================================================
    // 3. Handler API /api/message (Slide 17 & 18: Client gửi tin nhắn JSON POST)
    // =========================================================================
    private class ApiMessageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                if (!"POST".equalsIgnoreCase(method)) {
                    sendResponse(exchange, 405, "application/json", "{\"error\":\"Chỉ chấp nhận POST\"}");
                    return;
                }

                // 1. Kiểm tra Header Content-Type (Slide 18)
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
                    String resp = "{\"error\":\"415 Unsupported Media Type - Yêu cầu Content-Type application/json\"}";
                    sendResponse(exchange, 415, "application/json; charset=UTF-8", resp);
                    log(String.format("[%s] 415 Unsupported Media Type từ Client", serverName));
                    return;
                }

                // 2. Đọc Body từ Request InputStream (Slide 18)
                InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                StringBuilder sb = new StringBuilder();
                char[] buffer = new char[1024];
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, n);
                }
                String requestBody = sb.toString();

                // Trích xuất nội dung tin nhắn gửi lên
                String userMessage = extractJsonValue(requestBody, "text");
                if (userMessage.isEmpty()) {
                    userMessage = requestBody.trim();
                }

                log(String.format("[%s] Nhận tin nhắn POST từ Client: \"%s\"", serverName, userMessage));

                // 3. Chuẩn bị dữ liệu trả về 201 Created (Slide 18)
                String replyTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                String responseJson = String.format(
                        "{\"status\":\"success\",\"message\":\"Đã nhận dữ liệu\",\"server\":\"%s\",\"reply\":\"Chào bạn! %s đã nhận: '%s'\",\"receivedAt\":\"%s\"}",
                        serverName, serverName, escapeJson(userMessage), replyTime
                );

                sendResponse(exchange, 201, "application/json; charset=UTF-8", responseJson);
                log(String.format("[%s] 201 Created: Đã trả lời Client lúc %s", serverName, replyTime));

            } catch (Exception e) {
                log(String.format("[%s] Lỗi xử lý POST /api/message: %s", serverName, e.getMessage()));
                sendResponse(exchange, 500, "application/json", "{\"error\":\"500 Internal Server Error\"}");
            } finally {
                exchange.close();
            }
        }
    }

    // =========================================================================
    // 4. Handler API /api/ingest (Tiếp nhận nạp trang web từ Crawler & Kiểm duyệt)
    // =========================================================================
    private class ApiIngestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "application/json", "{\"error\":\"Chỉ chấp nhận POST\"}");
                    return;
                }

                InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                StringBuilder sb = new StringBuilder();
                char[] buffer = new char[4096];
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, n);
                }
                String requestBody = sb.toString();

                String sourceUrl = extractJsonValue(requestBody, "url");
                String filename = extractJsonValue(requestBody, "filename");
                String htmlContent = extractJsonValue(requestBody, "html");
                boolean syncIis = requestBody.contains("\"syncIis\":true");

                if (filename.isEmpty()) filename = "crawled_page.htm";
                if (!filename.toLowerCase().endsWith(".htm") && !filename.toLowerCase().endsWith(".html")) {
                    filename += ".htm";
                }

                log(String.format("[%s] Nhận yêu cầu nạp trang cào: %s (Nguồn: %s, %,d ký tự)",
                        serverName, filename, sourceUrl, htmlContent.length()));

                // Kiểm duyệt quản trị viên nếu có listener
                boolean approved = true;
                if (approvalListener != null) {
                    approved = approvalListener.approveIngest(serverName, sourceUrl, filename, htmlContent.length());
                }

                if (!approved) {
                    log(String.format("[%s] Quản trị viên ĐÃ TỪ CHỐI nạp trang: %s", serverName, filename));
                    sendResponse(exchange, 403, "application/json; charset=UTF-8",
                            "{\"status\":\"rejected\",\"message\":\"Quản trị viên đã từ chối nạp trang web này vào máy chủ.\"}");
                    return;
                }

                // Lưu file vào thư mục webroot/crawled/
                File crawledDir = new File(webRoot, "crawled");
                if (!crawledDir.exists()) crawledDir.mkdirs();
                File targetFile = new File(crawledDir, filename);

                try (Writer writer = new OutputStreamWriter(new java.io.FileOutputStream(targetFile), StandardCharsets.UTF_8)) {
                    writer.write(htmlContent);
                }

                // Đồng bộ sang IIS nếu có yêu cầu
                boolean iisSynced = false;
                if (syncIis) {
                    String iisPath = (port == 8080) ? "C:\\inetpub\\site1" : "C:\\inetpub\\site2";
                    File iisDir = new File(iisPath);
                    if (iisDir.exists() && iisDir.isDirectory()) {
                        File iisDest = new File(iisDir, filename);
                        Files.copy(targetFile.toPath(), iisDest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        iisSynced = true;
                        log(String.format("[%s] Đã đồng bộ trang cào sang IIS: %s", serverName, iisDest.getAbsolutePath()));
                    }
                }

                String accessPath = "/crawled/" + filename;
                String responseJson = String.format(
                        "{\"status\":\"accepted\",\"message\":\"Trang web đã được duyệt và nạp thành công!\",\"path\":\"%s\",\"url\":\"http://localhost:%d%s\",\"iisSynced\":%b}",
                        accessPath, port, accessPath, iisSynced
                );

                log(String.format("[%s] ĐÃ NẠP VÀ PHÁT HÀNH: http://localhost:%d%s", serverName, port, accessPath));
                sendResponse(exchange, 201, "application/json; charset=UTF-8", responseJson);

            } catch (Exception e) {
                log(String.format("[%s] Lỗi xử lý POST /api/ingest: %s", serverName, e.getMessage()));
                sendResponse(exchange, 500, "application/json", "{\"error\":\"500 Lỗi nạp trang: " + escapeJson(e.getMessage()) + "\"}");
            } finally {
                exchange.close();
            }
        }
    }

    private String determineContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".htm") || lower.endsWith(".html")) return "text/html; charset=UTF-8";
        if (lower.endsWith(".css")) return "text/css; charset=UTF-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".json")) return "application/json; charset=UTF-8";
        return "text/html; charset=UTF-8";
    }

    // Hàm bổ trợ gửi Response tiêu chuẩn (Slide 14)
    private void sendResponse(HttpExchange context, int statusCode, String contentType, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        context.getResponseHeaders().set("Content-Type", contentType);
        context.getResponseHeaders().set("Server", serverName + "/JavaHttpServer");
        context.sendResponseHeaders(statusCode, data.length);
        try (OutputStream os = context.getResponseBody()) {
            os.write(data);
        }
    }

    private String generate404Html(String path) {
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>404 Not Found</title></head>"
                + "<body style=\"font-family: Arial, sans-serif; background-color: #FFF5F5; padding: 30px; text-align: center;\">"
                + "<table width=\"100%\" cellpadding=\"20\" style=\"background-color: #FFFFFF; border: 2px solid #C0392B; border-radius: 8px; max-width: 600px; margin: auto;\">"
                + "<tr><td align=\"center\">"
                + "<h1 style=\"color: #C0392B; font-size: 32px; margin: 0;\">404 NOT FOUND</h1>"
                + "<p style=\"color: #7F8C8D; font-size: 16px; margin-top: 10px;\">Tài nguyên yêu cầu <code>" + path + "</code> không tồn tại trên <b>" + serverName + "</b>!</p>"
                + "<hr style=\"border: none; border-top: 1px solid #FFCDD2; margin: 20px 0;\">"
                + "<p style=\"font-size: 14px;\"><a href=\"/\" style=\"background-color: #C0392B; color: #FFFFFF; padding: 8px 16px; text-decoration: none; border-radius: 4px; font-weight: bold;\">&larr; Quay về trang chủ</a></p>"
                + "</td></tr></table></body></html>";
    }

    public static String extractJsonValue(String json, String key) {
        try {
            String pattern = "\"" + key + "\":\"";
            int idx = json.indexOf(pattern);
            if (idx != -1) {
                int start = idx + pattern.length();
                StringBuilder sb = new StringBuilder();
                boolean escaped = false;
                for (int i = start; i < json.length(); i++) {
                    char c = json.charAt(i);
                    if (escaped) {
                        if (c == 'n') sb.append('\n');
                        else if (c == 'r') sb.append('\r');
                        else if (c == 't') sb.append('\t');
                        else if (c == '"') sb.append('"');
                        else if (c == '\\') sb.append('\\');
                        else sb.append(c);
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        return sb.toString();
                    } else {
                        sb.append(c);
                    }
                }
                return sb.toString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"') sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else sb.append(c);
        }
        return sb.toString();
    }
}
