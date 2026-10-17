package server;

import java.io.File;

/**
 * Khởi chạy Server 2 (Mô phỏng Máy chủ ứng dụng theo sơ đồ bảng)
 * Cổng: 8081 | Thư mục webroot2: P3.HTM, P4.HTM
 * Code bám sát Slide 13 Chương 12
 */
public class Server2Console {
    public static void main(String[] args) {
        int port = 8081;
        File webroot = new File("webroot2");

        if (!webroot.exists()) {
            webroot.mkdirs();
        }

        System.out.println("==================================================");
        System.out.println("       KHỞI ĐỘNG SERVER 2 (MÁY CHỦ THỨ HAI)       ");
        System.out.println("   Cổng lắng nghe: " + port);
        System.out.println("   Thư mục webroot: " + webroot.getAbsolutePath());
        System.out.println("   Tài nguyên: P3.HTM, P4.HTM");
        System.out.println("==================================================");

        try {
            HttpServerCore server = new HttpServerCore("Server 2 (App)", port, webroot, System.out::println);
            server.start();

            // Đăng ký hook tắt an toàn khi người dùng nhấn Ctrl+C
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nĐang dừng Server 2...");
                server.stop();
            }));

            System.out.println("Server 2 đang chạy... Nhấn Ctrl + C để dừng.");
        } catch (Exception e) {
            System.err.println("Lỗi khởi chạy Server 2: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
