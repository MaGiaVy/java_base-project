package com.example.client.cli;

import com.example.client.ChatClient;
import com.example.model.ChatMessage;
import com.example.model.MessageType;

import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

/**
 * Interactive Console / CLI Chat Client.
 */
public class ConsoleChatClient {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void main(String[] args) {
        // Parse optional arguments or fallback to interactive input
        String host = "localhost";
        int port = 5000;
        String username = null;
        String password = null;

        for (int i = 0; i < args.length; i++) {
            if ("--host".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                host = args[++i];
            } else if ("--port".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                try {
                    port = Integer.parseInt(args[++i]);
                } catch (NumberFormatException ignored) {}
            } else if ("--user".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                username = args[++i];
            } else if ("--pass".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
                password = args[++i];
            }
        }

        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        Console console = System.console();

        System.out.println("=========================================================");
        System.out.println("      SECURE TCP CHAT - CONSOLE CLIENT (TLS 1.3)        ");
        System.out.println("=========================================================");

        if (username == null) {
            System.out.print("Nhập Server Host [mặc định: " + host + "]: ");
            String inputHost = scanner.nextLine().trim();
            if (!inputHost.isEmpty()) {
                host = inputHost;
            }

            System.out.print("Nhập Server Port [mặc định: " + port + "]: ");
            String inputPort = scanner.nextLine().trim();
            if (!inputPort.isEmpty()) {
                try {
                    port = Integer.parseInt(inputPort);
                } catch (NumberFormatException e) {
                    System.out.println("[LỖI] Cổng không hợp lệ, sử dụng mặc định: " + port);
                }
            }

            System.out.print("Nhập Username (vd: an, binh, chi): ");
            username = scanner.nextLine().trim();
            while (username.isEmpty()) {
                System.out.print("Username không được để trống. Nhập lại: ");
                username = scanner.nextLine().trim();
            }

            if (console != null) {
                char[] passChars = console.readPassword("Nhập Password: ");
                password = new String(passChars);
            } else {
                System.out.print("Nhập Password: ");
                password = scanner.nextLine().trim();
            }
        }

        ChatClient client = new ChatClient();
        System.out.println("\n[ĐANG KẾT NỐI] Đang bắt tay bảo mật TLS với " + host + ":" + port + "...");

        try {
            client.connect(host, port);
            System.out.println("🔒 [BẢO MẬT] Bắt tay TLS thành công!");
            System.out.println("    - Giao thức: " + client.getTlsProtocol());
            System.out.println("    - Bộ mã hóa (Cipher): " + client.getCipherSuite());
            System.out.println("    - Xác thực Server Certificate: [XÁC THỰC THÀNH CÔNG]");

            System.out.println("\n[XÁC THỰC] Đang đăng nhập tài khoản '" + username + "'...");
            boolean loginSuccess = client.login(username, password);

            if (!loginSuccess) {
                System.err.println("❌ [ĐĂNG NHẬP THẤT BẠI] " + client.getLastErrorMessage());
                client.disconnect();
                return;
            }

            System.out.println("=========================================================");
            System.out.println("✅ [ĐĂNG NHẬP THÀNH CÔNG] Xin chào " + client.getUsername() + " (Client ID: #" + client.getClientId() + ")");
            System.out.println("👉 Nhập tin nhắn và nhấn ENTER để gửi.");
            System.out.println("👉 Gõ '/quit' hoặc '/exit' để rời phòng chat an toàn.");
            System.out.println("=========================================================\n");

            // Start background receiver loop
            client.startReceiveLoop(
                    msg -> handleIncomingMessage(msg),
                    () -> {
                        System.out.println("\n⚠️ [THÔNG BÁO] Kết nối tới máy chủ đã bị ngắt.");
                        System.exit(0);
                    }
            );

            // Command/message input loop
            while (client.isConnected()) {
                if (!scanner.hasNextLine()) {
                    break;
                }
                String line = scanner.nextLine().trim();
                if (line.equalsIgnoreCase("/quit") || line.equalsIgnoreCase("/exit")) {
                    System.out.println("\n[THOÁT] Đang ngắt kết nối an toàn...");
                    client.disconnect();
                    break;
                }
                if (!line.isEmpty()) {
                    try {
                        client.sendMessage(line);
                    } catch (Exception e) {
                        System.err.println("❌ [LỖI GỬI TIN]: " + e.getMessage());
                        break;
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("❌ [LỖI KẾT NỐI / BẢO MẬT]: " + e.getMessage());
        } finally {
            client.disconnect();
            System.out.println("[KẾT THÚC] Phiên làm việc đã đóng.");
        }
    }

    private static void handleIncomingMessage(ChatMessage msg) {
        String timeStr = LocalTime.now().format(TIME_FORMAT);
        String type = msg.getType();

        if (MessageType.MESSAGE.getValue().equalsIgnoreCase(type)) {
            System.out.println("[" + timeStr + "] [" + msg.getUsername() + "]: " + msg.getContent());
        } else if (MessageType.SYSTEM.getValue().equalsIgnoreCase(type)) {
            System.out.println("[" + timeStr + "] [HỆ THỐNG]: " + msg.getContent());
        } else if (MessageType.USER_LEFT.getValue().equalsIgnoreCase(type)) {
            System.out.println("[" + timeStr + "] [THÀNH VIÊN RỜI PHÒNG]: " + msg.getContent());
        } else {
            if (msg.getContent() != null) {
                System.out.println("[" + timeStr + "] [" + type.toUpperCase() + "]: " + msg.getContent());
            }
        }
    }
}
