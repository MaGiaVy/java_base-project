package client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Xử lý HTTP Request/Response bằng java.net.http.HttpClient chuẩn JDK (Java 11+)
 * Xây dựng bám sát Slide 16, 17, 19 Chương 12
 */
public class HttpClientHelper {

    private final HttpClient client;

    public HttpClientHelper() {
        // Slide 19: Cấu hình Client với connectTimeout 5s
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Gửi HTTP GET Request (Slide 16, 19)
     */
    public HttpResult sendGet(String urlString) {
        long startTime = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlString))
                    .header("User-Agent", "MiniChrome-SwingClient/1.0 (Saigon University Network Programming)")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - startTime;

            return new HttpResult(
                    response.statusCode(),
                    response.body(),
                    response.headers().firstValue("Content-Type").orElse("text/html"),
                    response.body().getBytes().length,
                    latency,
                    null,
                    formatHeaders(response)
            );
        } catch (HttpConnectTimeoutException e) {
            long latency = System.currentTimeMillis() - startTime;
            return new HttpResult(0, null, null, 0, latency, "Hết thời gian kết nối (Timeout): " + e.getMessage(), "");
        } catch (IOException e) {
            long latency = System.currentTimeMillis() - startTime;
            return new HttpResult(0, null, null, 0, latency, "Lỗi kết nối / Server chưa bật (Connection Refused): " + e.getMessage(), "");
        } catch (InterruptedException e) {
            long latency = System.currentTimeMillis() - startTime;
            return new HttpResult(0, null, null, 0, latency, "Tiến trình bị gián đoạn: " + e.getMessage(), "");
        } catch (IllegalArgumentException e) {
            long latency = System.currentTimeMillis() - startTime;
            return new HttpResult(0, null, null, 0, latency, "Cú pháp URL không hợp lệ: " + e.getMessage(), "");
        }
    }

    /**
     * Gửi HTTP POST JSON Request (Slide 17, 18, 19)
     * Mặc định timeout 60 giây để chờ Quản trị viên phê duyệt
     */
    public HttpResult sendPostJson(String urlString, String jsonPayload) {
        return sendPostJson(urlString, jsonPayload, 60);
    }

    /**
     * Gửi HTTP POST JSON Request với thời gian chờ tùy chỉnh
     */
    public HttpResult sendPostJson(String urlString, String jsonPayload, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlString))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("User-Agent", "MiniChrome-SwingClient/1.0")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - startTime;

            return new HttpResult(
                    response.statusCode(),
                    response.body(),
                    response.headers().firstValue("Content-Type").orElse("application/json"),
                    response.body().getBytes().length,
                    latency,
                    null,
                    formatHeaders(response)
            );
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.trim().isEmpty()) {
                if (e instanceof java.net.http.HttpTimeoutException) {
                    errorMsg = "Hết thời gian chờ phản hồi (" + timeoutSeconds + "s). Quản trị viên chưa duyệt kịp!";
                } else if (e instanceof java.net.ConnectException || (e.getCause() instanceof java.net.ConnectException)) {
                    errorMsg = "Máy chủ Server chưa được bật (Hãy bật Server trên Server Dashboard trước)!";
                } else {
                    errorMsg = e.getClass().getSimpleName();
                }
            } else if (errorMsg.contains("refused") || errorMsg.contains("Connection refused")) {
                errorMsg = "Máy chủ Server chưa được bật (Connection refused). Hãy bật Server 1 hoặc Server 2!";
            }
            return new HttpResult(0, null, null, 0, latency, errorMsg, "");
        }
    }

    private String formatHeaders(HttpResponse<?> response) {
        StringBuilder sb = new StringBuilder();
        response.headers().map().forEach((k, v) -> {
            sb.append(k).append(": ").append(String.join(", ", v)).append("\n");
        });
        return sb.toString();
    }

    /**
     * Đối tượng đóng gói kết quả phản hồi HTTP kèm metadata
     */
    public static class HttpResult {
        public final int statusCode;
        public final String body;
        public final String contentType;
        public final long contentLength;
        public final long latencyMs;
        public final String errorMessage;
        public final String rawHeaders;

        public HttpResult(int statusCode, String body, String contentType, long contentLength,
                          long latencyMs, String errorMessage, String rawHeaders) {
            this.statusCode = statusCode;
            this.body = body;
            this.contentType = contentType;
            this.contentLength = contentLength;
            this.latencyMs = latencyMs;
            this.errorMessage = errorMessage;
            this.rawHeaders = rawHeaders;
        }

        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }

        public boolean hasError() {
            return errorMessage != null && !errorMessage.isEmpty();
        }
    }
}
