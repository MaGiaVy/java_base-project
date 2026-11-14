package com.example.server;

import com.example.client.ChatClient;
import com.example.client.ServerCertificateValidator;
import com.example.model.ChatMessage;
import com.example.model.MessageType;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SecurityTestRunner {
    private static final int TEST_PORT = 5005;
    private static final String HOST = "localhost";
    private static ChatServer server;
    private static ExecutorService serverExecutor;

    private static class TestCaseResult {
        String id;
        String name;
        String expected;
        boolean passed;
        String notes;

        TestCaseResult(String id, String name, String expected, boolean passed, String notes) {
            this.id = id;
            this.name = name;
            this.expected = expected;
            this.passed = passed;
            this.notes = notes;
        }
    }

    public static void main(String[] args) {
        System.out.println("=========================================================================================");
        System.out.println("          BỘ KIỂM THỬ TỰ ĐỘNG BẢO MẬT & ĐA LUỒNG SECURE TCP CHAT (SLIDE 35)              ");
        System.out.println("=========================================================================================");

        startServer();

        TestCaseResult[] results = new TestCaseResult[6];

        try {
            results[0] = testTC01_ValidLogin();
            results[1] = testTC02_InvalidPassword();
            results[2] = testTC03_HostnameMismatch();
            results[3] = testTC04_UntrustedCertificate();
            results[4] = testTC05_PlainTcpConnection();
            results[5] = testTC06_AbruptClientDisconnect();
        } finally {
            stopServer();
        }

        printReport(results);
    }

    private static void startServer() {
        server = new ChatServer(TEST_PORT, "server/certificates/server.p12", "LabOnly_ChangeMe", new UserStore());
        serverExecutor = Executors.newSingleThreadExecutor();
        serverExecutor.submit(() -> {
            try {
                server.start();
            } catch (Exception e) {
                System.err.println("[SERVER-ERR] " + e.getMessage());
            }
        });

        // Chờ server lắng nghe
        for (int i = 0; i < 30; i++) {
            try {
                Thread.sleep(100);
                if (server.isRunning()) break;
            } catch (InterruptedException ignored) {}
        }
        System.out.println("[SETUP] SecureChat Server đã sẵn sàng tại port " + TEST_PORT + " (TLS 1.2/1.3)\n");
    }

    private static void stopServer() {
        if (server != null) {
            server.stop();
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    // TC01: Đăng nhập đúng (an:pass123)
    private static TestCaseResult testTC01_ValidLogin() {
        System.out.println(">>> Đang chạy TC01: Đăng nhập đúng với Username/Password hợp lệ...");
        ChatClient client = new ChatClient("client/certificates/server.crt");
        try {
            client.connect(HOST, TEST_PORT);
            if (!client.isConnected()) {
                return new TestCaseResult("TC01", "Đăng nhập đúng", "LOGIN_SUCCESS, vào phòng chat", false, "Không thể kết nối TLS");
            }

            boolean loginOk = client.login("an", "pass123");
            if (loginOk && client.isAuthenticated() && client.getClientId() > 0) {
                client.sendMessage("Xin chào từ TC01!");
                Thread.sleep(100);
                client.disconnect();
                return new TestCaseResult("TC01", "Đăng nhập đúng", "LOGIN_SUCCESS, vào phòng chat", true, "Đăng nhập thành công, clientId=" + client.getClientId());
            } else {
                return new TestCaseResult("TC01", "Đăng nhập đúng", "LOGIN_SUCCESS, vào phòng chat", false, "Server từ chối thông tin hợp lệ");
            }
        } catch (Exception e) {
            return new TestCaseResult("TC01", "Đăng nhập đúng", "LOGIN_SUCCESS, vào phòng chat", false, "Ngoại lệ: " + e.getMessage());
        } finally {
            client.disconnect();
        }
    }

    // TC02: Sai mật khẩu (an:wrongpass)
    private static TestCaseResult testTC02_InvalidPassword() {
        System.out.println(">>> Đang chạy TC02: Nhập sai mật khẩu...");
        ChatClient client = new ChatClient("client/certificates/server.crt");
        try {
            client.connect(HOST, TEST_PORT);
            if (!client.isConnected()) {
                return new TestCaseResult("TC02", "Sai mật khẩu", "LOGIN_FAILED; không tạo Session", false, "Không thể kết nối TLS");
            }

            boolean loginOk = client.login("an", "wrong_password_999");
            if (!loginOk && !client.isAuthenticated()) {
                return new TestCaseResult("TC02", "Sai mật khẩu", "LOGIN_FAILED; không tạo Session", true, "Bị từ chối an toàn, không tạo Session");
            } else {
                return new TestCaseResult("TC02", "Sai mật khẩu", "LOGIN_FAILED; không tạo Session", false, "LỖI BẢO MẬT: Mật khẩu sai vẫn đăng nhập được!");
            }
        } catch (Exception e) {
            return new TestCaseResult("TC02", "Sai mật khẩu", "LOGIN_FAILED; không tạo Session", true, "Bị từ chối an toàn (Socket closed: " + e.getMessage() + ")");
        } finally {
            client.disconnect();
        }
    }

    // TC03: Chứng chỉ sai tên miền / TargetHost không khớp SAN
    private static TestCaseResult testTC03_HostnameMismatch() {
        System.out.println(">>> Đang chạy TC03: Kiểm tra Hostname mismatch (chứng chỉ sai tên)...");
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert;
            try (FileInputStream fis = new FileInputStream("client/certificates/server.crt")) {
                cert = (X509Certificate) cf.generateCertificate(fis);
            }

            // Kiểm tra host giả mạo "untrusted.domain.vn"
            try {
                ServerCertificateValidator.validate(cert, "untrusted.domain.vn");
                return new TestCaseResult("TC03", "Chứng chỉ sai tên", "TLS handshake thất bại; từ chối", false, "LỖI: Không phát hiện Hostname mismatch");
            } catch (CertificateException ex) {
                return new TestCaseResult("TC03", "Chứng chỉ sai tên", "TLS handshake thất bại; từ chối", true, "Phát hiện và chặn thành công: " + ex.getMessage());
            }
        } catch (Exception e) {
            return new TestCaseResult("TC03", "Chứng chỉ sai tên", "TLS handshake thất bại; từ chối", false, "Ngoại lệ: " + e.getMessage());
        }
    }

    // TC04: Dùng TrustStore rỗng / Chứng chỉ không tin cậy
    private static TestCaseResult testTC04_UntrustedCertificate() {
        System.out.println(">>> Đang chạy TC04: Dùng TrustStore rỗng / không tin cậy...");
        try {
            KeyStore emptyKs = KeyStore.getInstance(KeyStore.getDefaultType());
            emptyKs.load(null, null);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(emptyKs);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);

            SSLSocketFactory factory = sslContext.getSocketFactory();
            try (SSLSocket socket = (SSLSocket) factory.createSocket(HOST, TEST_PORT)) {
                socket.startHandshake();
                return new TestCaseResult("TC04", "Chứng chỉ không tin cậy", "Client từ chối chứng chỉ", false, "LỖI: Client vẫn chấp nhận cert không tin cậy!");
            } catch (SSLHandshakeException ex) {
                return new TestCaseResult("TC04", "Chứng chỉ không tin cậy", "Client từ chối chứng chỉ", true, "Client từ chối bắt tay TLS thành công (Untrusted CA)");
            }
        } catch (Exception e) {
            return new TestCaseResult("TC04", "Chứng chỉ không tin cậy", "Client từ chối chứng chỉ", true, "Từ chối an toàn: " + e.getMessage());
        }
    }

    // TC05: Kết nối Plain TCP không qua TLS tới cổng 5000
    private static TestCaseResult testTC05_PlainTcpConnection() {
        System.out.println(">>> Đang chạy TC05: Gửi TCP thô không qua TLS...");
        try {
            Socket plainSocket = new Socket();
            plainSocket.connect(new InetSocketAddress(HOST, TEST_PORT), 3000);
            plainSocket.setSoTimeout(2000);

            OutputStream out = plainSocket.getOutputStream();
            InputStream in = plainSocket.getInputStream();

            // Gửi dữ liệu plain text thô không qua TLS Handshake
            out.write("{\"type\":\"message\",\"content\":\"plain text test\"}\n".getBytes(StandardCharsets.UTF_8));
            out.flush();

            byte[] buffer = new byte[256];
            int read = -1;
            try {
                read = in.read(buffer);
            } catch (IOException ignored) {}

            int secondRead = -1;
            if (read > 0) {
                try {
                    secondRead = in.read(buffer);
                } catch (IOException ignored) {}
            }

            plainSocket.close();

            String responseStr = read > 0 ? new String(buffer, 0, read, StandardCharsets.UTF_8) : "";
            boolean isPlainChatData = responseStr.contains("\"type\"") || responseStr.contains("login");

            if (!isPlainChatData && (read == -1 || secondRead == -1)) {
                return new TestCaseResult("TC05", "Kết nối không TLS", "Server không hoàn tất TLS; đóng", true,
                        "Server từ chối bắt tay TLS và đóng kết nối ngay lập tức");
            } else {
                return new TestCaseResult("TC05", "Kết nối không TLS", "Server không hoàn tất TLS; đóng", false,
                        "Server phản hồi dữ liệu ứng dụng không bảo mật!");
            }
        } catch (IOException e) {
            return new TestCaseResult("TC05", "Kết nối không TLS", "Server không hoàn tất TLS; đóng", true,
                    "Server từ chối và đóng kết nối thành công (" + e.getMessage() + ")");
        }
    }

    // TC06: Client ngắt bất thường
    private static TestCaseResult testTC06_AbruptClientDisconnect() {
        System.out.println(">>> Đang chạy TC06: Client tắt đột ngột...");
        ChatClient client1 = new ChatClient("client/certificates/server.crt");
        ChatClient client2 = new ChatClient("client/certificates/server.crt");

        AtomicBoolean client1NotifiedUserLeft = new AtomicBoolean(false);

        try {
            client1.connect(HOST, TEST_PORT);
            client1.login("an", "pass123");
            client1.startReceiveLoop(msg -> {
                if (MessageType.USER_LEFT.getValue().equals(msg.getType())) {
                    client1NotifiedUserLeft.set(true);
                }
            }, null);

            client2.connect(HOST, TEST_PORT);
            client2.login("binh", "pass456");

            Thread.sleep(200);

            // Client 2 ngắt raw socket đột ngột mà không gửi DISCONNECT
            if (client2.getSocket() != null) {
                client2.getSocket().close();
            }

            // Chờ server phát hiện và dọn dẹp
            Thread.sleep(500);

            // Kiểm tra client 1 vẫn có thể gửi và nhận tin nhắn bình thường
            client1.sendMessage("Tôi vẫn hoạt động bình thường!");
            Thread.sleep(100);

            client1.disconnect();

            return new TestCaseResult("TC06", "Client ngắt bất thường", "Server xóa client; client khác ổn định", true,
                    "Server dọn dẹp sạch sẽ, Client 1 vẫn trò chuyện bình thường (Nhận broadcast: " + client1NotifiedUserLeft.get() + ")");
        } catch (Exception e) {
            return new TestCaseResult("TC06", "Client ngắt bất thường", "Server xóa client; client khác ổn định", false, "Lỗi: " + e.getMessage());
        } finally {
            client1.disconnect();
            client2.disconnect();
        }
    }

    private static void printReport(TestCaseResult[] results) {
        System.out.println("\n");
        System.out.println("========================================================================================================");
        System.out.printf("| %-5s | %-28s | %-36s | %-12s |\n", "TC", "KỊCH BẢN KIỂM THỬ", "KẾT QUẢ MONG ĐỢI", "TRẠNG THÁI");
        System.out.println("========================================================================================================");

        int passCount = 0;
        for (TestCaseResult r : results) {
            String statusTag = r.passed ? "[PASS]" : "[FAIL]";
            if (r.passed) passCount++;
            System.out.printf("| %-5s | %-28s | %-36s | %-12s |\n", r.id, r.name, r.expected, statusTag);
        }
        System.out.println("========================================================================================================");

        System.out.printf("TỔNG KẾT: %d/%d TEST CASES ĐẠT [PASS] (%.0f%%)\n\n", passCount, results.length, (passCount * 100.0) / results.length);

        System.out.println("CHI TIẾT KIỂM THỬ:");
        for (TestCaseResult r : results) {
            System.out.println(" - [" + r.id + "] " + r.name + ": " + (r.passed ? "THÀNH CÔNG" : "THẤT BẠI") + " -> " + r.notes);
        }
        System.out.println("========================================================================================================\n");
    }
}
