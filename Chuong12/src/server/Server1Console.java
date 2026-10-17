package server;

import java.io.File;

/**
 * Khởi chạy Server 1 (Mô phỏng Web Server IIS theo sơ đồ bảng)
 * Cổng: 8080 | Thư mục webroot1: P1.HTM, P2.HTM
 * Code bám sát Slide 13 Chương 12
 */
public class Server1Console {
    public static void main(String[] args) {
        int port = 8080;
        File webroot = new File("webroot1");

        if (!webroot.exists()) {
            webroot.mkdirs();
        }

        System.out.println("==================================================");
        System.out.println("   KHỞI ĐỘNG SERVER 1 (MÔ PHỎNG WEB SERVER IIS)   ");
        System.out.println("   Cổng lắng nghe: " + port);
        System.out.println("   Thư mục webroot: " + webroot.getAbsolutePath());
        System.out.println("   Tài nguyên: P1.HTM, P2.HTM");
        System.out.println("==================================================");

        try {
            HttpServerCore server = new HttpServerCore("Server 1 (IIS)", port, webroot, System.out::println);
            server.start();

            // Đăng ký hook tắt an toàn khi người dùng nhấn Ctrl+C
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nĐang dừng Server 1...");
                server.stop();
            }));

            System.out.println("Server 1 đang chạy... Nhấn Ctrl + C để dừng.");
        } catch (Exception e) {
            System.err.println("Lỗi khởi chạy Server 1: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
