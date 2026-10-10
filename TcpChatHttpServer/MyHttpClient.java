import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Scanner;

/**
 * ============================================================================
 * MyHttpClient.java - HTTP Client trích xuất nội dung web (Bài toán Test chéo 4 đường)
 * ============================================================================
 * - Sử dụng java.net.http.HttpClient để tạo HTTP Request GET.
 * - Cho phép nhập URL linh hoạt từ bàn phím (trỏ vào IIS hoặc trỏ vào Java Server).
 * - In ra Status Code và nội dung HTML nhận được từ Server.
 * ============================================================================
 */
public class MyHttpClient {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=============================================================");
        System.out.println("  🌐 HTTP CLIENT - TEST CHÉO 4 ĐƯỜNG");
        System.out.println("=============================================================");
        System.out.println("Gợi ý các URL kiểm thử:");
        System.out.println("  - Đường 3 (Java Client <-> Java Server): http://localhost:8080/P3.html");
        System.out.println("  - Đường 4 (Java Client <-> IIS)        : http://localhost/P1.html");
        System.out.println("-------------------------------------------------------------");
        System.out.print("Nhập URL cần tải (mặc định: http://localhost:8080/P3.html): ");
        String urlString = scanner.nextLine().trim();
        if (urlString.isEmpty()) {
            urlString = "http://localhost:8080/P3.html";
        }

        try {
            // Khởi tạo HttpClient
            HttpClient client = HttpClient.newHttpClient();

            // Xây dựng request GET đến URL vừa nhập
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlString))
                    .GET()
                    .build();

            // Gửi request và nhận response dạng String
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("\n--- KẾT QUẢ TỪ SERVER ---");
            System.out.println("Status Code: " + response.statusCode());
            System.out.println("Content-Type: " + response.headers().firstValue("Content-Type").orElse("N/A"));
            System.out.println("Nội dung HTML (Body):");
            System.out.println(response.body());

        } catch (Exception e) {
            System.out.println("\n❌ Lỗi kết nối: " + e.getMessage());
            System.out.println("Vui lòng kiểm tra lại URL hoặc xem Server đã bật chưa.");
        }

        scanner.close();
    }
}
