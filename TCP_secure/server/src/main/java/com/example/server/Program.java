package com.example.server;

/**
 * Điểm khởi chạy chính (Entry Point) của Secure TCP Chat Server.
 */
public class Program {
    public static void main(String[] args) {
        int port = ChatServer.DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.printf("[WARN] Cổng '%s' không hợp lệ. Sử dụng cổng mặc định %d.%n", args[0], port);
            }
        }

        System.out.println("============================================================");
        System.out.println("   HỆ THỐNG MÁY CHỦ BẢO MẬT JAVA SECURE TCP CHAT (TLS 1.2/1.3)   ");
        System.out.println("============================================================");

        ChatServer server = new ChatServer(port);

        // Đăng ký ShutdownHook để dọn dẹp tài nguyên khi tắt ứng dụng
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[INFO] Nhận tín hiệu tắt máy chủ. Đang tiến hành dọn dẹp...");
            server.stop();
        }));

        try {
            server.start();
        } catch (Exception e) {
            System.err.printf("[ERROR] Máy chủ gặp sự cố nghiêm trọng không thể khởi động: %s%n", e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
