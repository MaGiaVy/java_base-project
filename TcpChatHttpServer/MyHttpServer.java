import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;

/**
 * ============================================================================
 * MyHttpServer.java - HTTP Server phân phối file HTML (Bài toán Test chéo 4 đường)
 * ============================================================================
 * - Lắng nghe tại cổng 8080.
 * - Nhận Request GET, bóc tách URL Path để tìm file tương ứng (P3.HTM, P4.HTM).
 * - Trả về mã Status 200 OK nếu tìm thấy file, hoặc 404 Not Found nếu không có file.
 * ============================================================================
 */
public class MyHttpServer {
    public static void main(String[] args) throws IOException {
        // Khởi tạo Server lắng nghe tại cổng 8080
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // Tạo context xử lý mọi request gửi đến
        server.createContext("/", new FileHandler());

        server.start();
        System.out.println("=============================================================");
        System.out.println("  🚀 MyHttpServer đang chạy tại http://localhost:8080/");
        System.out.println("  👉 Phân phối các file HTML trong thư mục: P3.html, P4.html");
        System.out.println("=============================================================");
    }

    static class FileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Lấy đường dẫn từ URL (ví dụ: /P3.html)
            String path = exchange.getRequestURI().getPath();

            // Bỏ dấu "/" ở đầu để lấy tên file thực tế
            String fileName = path.equals("/") ? "P3.html" : path.substring(1);
            File file = new File(fileName);

            // Tự động tìm kiếm linh hoạt giữa .html và .htm
            if (!file.exists()) {
                if (fileName.toLowerCase().endsWith(".htm")) {
                    File alt = new File(fileName + "l");
                    if (alt.exists()) file = alt;
                } else if (fileName.toLowerCase().endsWith(".html")) {
                    File alt = new File(fileName.substring(0, fileName.length() - 1));
                    if (alt.exists()) file = alt;
                }
            }

            if (file.exists() && !file.isDirectory()) {
                // Nếu tìm thấy file, đọc nội dung và trả về Status 200 OK
                byte[] responseBytes = Files.readAllBytes(file.toPath());
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);

                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
                System.out.println("✅ [200 OK] Phân phối file: " + fileName + " (" + responseBytes.length + " bytes)");
            } else {
                // Nếu không tìm thấy file, trả về lỗi 404 Not Found
                String errorMsg = "<h1>404 Not Found - Khong tim thay file " + fileName + "</h1>";
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(404, errorMsg.getBytes().length);

                OutputStream os = exchange.getResponseBody();
                os.write(errorMsg.getBytes());
                os.close();
                System.out.println("❌ [404 Not Found] Không tìm thấy file: " + fileName);
            }
        }
    }
}
