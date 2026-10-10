import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import java.util.Scanner;

/**
 * ============================================================================
 * HTTP CHAT CLIENT (Chương 12 - Web API Client)
 * ============================================================================
 * - Sử dụng HttpClient, HttpRequest, HttpResponse trong Java 11+.
 * - Kết nối tới REST API Server tại http://localhost:8080.
 * - Hỗ trợ các chức năng:
 *   1. Xem danh sách client: GET /api/clients (Mã trạng thái HTTP 200 OK)
 *   2. Gửi tin nhắn        : POST /api/message (Mã trạng thái HTTP 201 Created)
 *   3. Xem lịch sử tin nhắn : GET /api/history (Mã trạng thái HTTP 200 OK)
 * - Hiển thị chi tiết Response Status Code và phản hồi JSON.
 * ============================================================================
 */
public class HttpChatClient {

    private static final String BASE_URL = "http://localhost:8080";
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public static void main(String[] args) {
        System.out.println("=========================================================");
        System.out.println("   🌐 HTTP CHAT CLIENT - CHƯƠNG 12 (REST API)");
        System.out.println("   Target Server: " + BASE_URL);
        System.out.println("=========================================================");

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n----------------- MENU CHỨC NĂNG -----------------");
            System.out.println("1. Xem danh sách client   (GET  /api/clients)");
            System.out.println("2. Gửi tin nhắn           (POST /api/message)");
            System.out.println("3. Xem lịch sử tin nhắn   (GET  /api/history)");
            System.out.println("4. Thoát");
            System.out.print("👉 Lựa chọn của bạn (1-4): ");

            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1":
                        listClients();
                        break;
                    case "2":
                        System.out.print("Nhập nội dung tin nhắn cần gửi: ");
                        String msg = scanner.nextLine().trim();
                        if (msg.isEmpty()) {
                            System.out.println("⚠️ Tin nhắn không được để trống!");
                        } else {
                            sendMessage(msg);
                        }
                        break;
                    case "3":
                        showHistory();
                        break;
                    case "4":
                        System.out.println("👋 Tạm biệt! Đã thoát HTTP Chat Client.");
                        return;
                    default:
                        System.out.println("⚠️ Lựa chọn không hợp lệ! Vui lòng chọn từ 1 đến 4.");
                }
            } catch (Exception e) {
                System.out.println("❌ [Lỗi HTTP]: " + e.getMessage());
                System.out.println("👉 Vui lòng chắc chắn rằng Server (ChatAndWebServer) đang chạy tại port 8080!");
            }
        }
    }

    /**
     * 1. GET /api/clients - Lấy danh sách client đang online
     */
    static void listClients() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/clients"))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        long start = System.currentTimeMillis();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - start;

        int status = response.statusCode();
        System.out.println("\n[HTTP Response] Mã Status : " + status + " " + getStatusText(status) + " (" + elapsed + " ms)");
        System.out.println("[HTTP Response] Headers   : Content-Type=" + response.headers().firstValue("Content-Type").orElse("N/A"));
        System.out.println("[HTTP Response] Body JSON :");
        System.out.println(prettyPrintJson(response.body()));
    }

    /**
     * 2. POST /api/message - Gửi tin nhắn qua HTTP API
     */
    static void sendMessage(String text) throws Exception {
        String jsonBody = "{\"text\":\"" + escapeJson(text) + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/message"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        long start = System.currentTimeMillis();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - start;

        int status = response.statusCode();
        System.out.println("\n[HTTP Response] Mã Status : " + status + " " + getStatusText(status) + " (" + elapsed + " ms)");
        System.out.println("[HTTP Response] Body JSON :");
        System.out.println(prettyPrintJson(response.body()));

        if (status == 201) {
            System.out.println("✅ Tin nhắn đã được gửi và broadcast tới tất cả client!");
        } else {
            System.out.println("⚠️ Phản hồi từ server có mã khác 201: " + status);
        }
    }

    /**
     * 3. GET /api/history - Lấy lịch sử 20 tin nhắn gần nhất
     */
    static void showHistory() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/history"))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        long start = System.currentTimeMillis();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - start;

        int status = response.statusCode();
        System.out.println("\n[HTTP Response] Mã Status : " + status + " " + getStatusText(status) + " (" + elapsed + " ms)");
        System.out.println("[HTTP Response] Lịch sử tin nhắn gần nhất:");
        System.out.println(prettyPrintJson(response.body()));
    }

    private static String getStatusText(int code) {
        switch (code) {
            case 200: return "OK";
            case 201: return "Created";
            case 204: return "No Content";
            case 400: return "Bad Request";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 415: return "Unsupported Media Type";
            case 500: return "Internal Server Error";
            default: return "Unknown";
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private static String prettyPrintJson(String json) {
        if (json == null) return "";
        // Căn dòng thụt lề đơn giản cho console
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean inQuotes = false;
        for (char c : json.toCharArray()) {
            if (c == '\"') inQuotes = !inQuotes;
            if (!inQuotes) {
                if (c == '{' || c == '[') {
                    sb.append(c).append("\n");
                    indent += 2;
                    sb.append(" ".repeat(indent));
                    continue;
                } else if (c == '}' || c == ']') {
                    sb.append("\n");
                    indent = Math.max(0, indent - 2);
                    sb.append(" ".repeat(indent));
                    sb.append(c);
                    continue;
                } else if (c == ',') {
                    sb.append(c).append("\n");
                    sb.append(" ".repeat(indent));
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
