package com.example.server;

import com.example.model.ChatMessage;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lõi máy chủ Secure TCP Chat hỗ trợ đa người dùng (Multi-Client) qua kênh mã hóa TLS 1.2/1.3.
 * Quản lý danh sách kết nối qua ConcurrentHashMap, chống xung đột luồng khi broadcast với ReentrantLock,
 * và dọn dẹp tài nguyên an toàn theo cơ chế idempotent.
 */
public class ChatServer {
    public static final int DEFAULT_PORT = 5000;
    public static final String DEFAULT_KEYSTORE_PATH = "server/certificates/server.p12";
    public static final String DEFAULT_KEYSTORE_PASS = "LabOnly_ChangeMe";

    private final int port;
    private final String keystorePath;
    private final String keystorePassword;
    private final UserStore userStore;

    private final ConcurrentHashMap<Integer, ClientSession> clients = new ConcurrentHashMap<>();
    private final AtomicInteger clientIdCounter = new AtomicInteger(1);
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    private SSLServerSocket serverSocket;
    private volatile boolean running = false;

    public ChatServer() {
        this(DEFAULT_PORT, DEFAULT_KEYSTORE_PATH, DEFAULT_KEYSTORE_PASS, new UserStore());
    }

    public ChatServer(int port) {
        this(port, DEFAULT_KEYSTORE_PATH, DEFAULT_KEYSTORE_PASS, new UserStore());
    }

    public ChatServer(int port, String keystorePath, String keystorePassword, UserStore userStore) {
        this.port = port;
        this.keystorePath = keystorePath;
        this.keystorePassword = keystorePassword;
        this.userStore = userStore != null ? userStore : new UserStore();
    }

    /**
     * Khởi tạo SSLContext từ file Keystore PKCS#12 (server.p12).
     */
    private SSLContext initSSLContext() throws Exception {
        File ksFile = resolveKeystoreFile(keystorePath);
        if (!ksFile.exists()) {
            throw new FileNotFoundException("Không tìm thấy file Keystore PKCS#12 tại: " + ksFile.getAbsolutePath());
        }

        System.out.printf("[INFO] Đang nạp chứng chỉ máy chủ từ: %s%n", ksFile.getAbsolutePath());
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = new FileInputStream(ksFile)) {
            keyStore.load(is, keystorePassword.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keystorePassword.toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, new SecureRandom());
        return sslContext;
    }

    private File resolveKeystoreFile(String path) {
        if (path != null) {
            File f = new File(path);
            if (f.exists()) return f;
        }
        File[] candidates = new File[] {
                new File("server/certificates/server.p12"),
                new File("certificates/server.p12"),
                new File("server.p12")
        };
        for (File c : candidates) {
            if (c.exists()) return c;
        }
        return new File(DEFAULT_KEYSTORE_PATH);
    }

    /**
     * Khởi động máy chủ, lắng nghe kết nối SSLServerSocket và thực hiện Accept Loop.
     */
    public void start() throws Exception {
        SSLContext sslContext = initSSLContext();
        SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();

        serverSocket = (SSLServerSocket) ssf.createServerSocket(port);
        // Hạn chế chỉ cho phép các giao thức TLS an toàn
        serverSocket.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});

        running = true;
        System.out.println("============================================================");
        System.out.printf("[PASS] Server khởi động thành công trên cổng %d với TLS 1.2 / 1.3%n", port);
        System.out.printf("[INFO] Số tài khoản sẵn sàng trong hệ thống: %d%n", userStore.getUserCount());
        System.out.println("[INFO] Đang chờ client kết nối...");
        System.out.println("============================================================");

        try {
            while (running && !serverSocket.isClosed()) {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                int clientId = clientIdCounter.getAndIncrement();

                System.out.printf("[INFO] Tiếp nhận kết nối mới từ %s -> Cấp ClientId #%d%n",
                        clientSocket.getRemoteSocketAddress(), clientId);

                try {
                    ClientSession session = new ClientSession(clientId, clientSocket);
                    clients.put(clientId, session);

                    ClientHandler handler = new ClientHandler(this, session);
                    threadPool.submit(handler);
                } catch (IOException e) {
                    System.err.printf("[WARN] Không thể khởi tạo phiên cho Client #%d: %s%n", clientId, e.getMessage());
                    try {
                        clientSocket.close();
                    } catch (IOException ignored) {}
                }
            }
        } catch (SocketException e) {
            if (running) {
                System.out.printf("[INFO] Socket máy chủ đã dừng: %s%n", e.getMessage());
            }
        } finally {
            stop();
        }
    }

    /**
     * Broadcast thông điệp tới toàn bộ các client đang online và đã xác thực thành công.
     * Mỗi client được ghi dữ liệu dưới sự bảo vệ của sendLock riêng.
     *
     * @param msg Thông điệp cần gửi
     * @param excludeClientId ClientId cần bỏ qua (hoặc null nếu gửi tới tất cả)
     */
    public void broadcast(ChatMessage msg, Integer excludeClientId) {
        if (msg == null) return;

        for (ClientSession session : clients.values()) {
            if (excludeClientId != null && session.getId() == excludeClientId) {
                continue;
            }

            // Chỉ broadcast tới các client đã qua xác thực
            if (session.isAuthenticated() && !session.isClosed()) {
                try {
                    session.send(msg);
                } catch (IOException e) {
                    System.out.printf("[WARN] Gửi broadcast thất bại tới Client #%d (%s): %s%n",
                            session.getId(), session.getUsername(), e.getMessage());
                    // Nếu socket lỗi, kích hoạt gỡ bỏ phiên
                    removeClient(session.getId(), "Lỗi ghi dữ liệu đường truyền: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Dọn dẹp và đóng phiên làm việc của một client.
     * Phương thức có tính chất idempotent, an toàn khi được gọi nhiều lần.
     *
     * @param clientId ID của client cần gỡ bỏ
     * @param reason Lý do ngắt kết nối
     */
    public void removeClient(int clientId, String reason) {
        ClientSession session = clients.remove(clientId);
        if (session != null) {
            String username = session.getUsername();
            boolean wasAuth = session.isAuthenticated();

            session.close();

            System.out.printf("[INFO] Client #%d (%s) đã ngắt kết nối. Lý do: %s (Còn lại %d clients online)%n",
                    clientId, username != null ? username : "chưa xác thực", reason, clients.size());

            // Nếu client đã từng đăng nhập, broadcast cho các client còn lại biết
            if (wasAuth && username != null) {
                ChatMessage leftMsg = ChatMessage.createUserLeft(clientId, username);
                broadcast(leftMsg, null);
            }
        }
    }

    /**
     * Dừng máy chủ và giải phóng toàn bộ tài nguyên.
     */
    public void stop() {
        if (!running) return;
        running = false;

        System.out.println("[INFO] Đang dừng ChatServer...");

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        for (ClientSession session : clients.values()) {
            session.close();
        }
        clients.clear();
        threadPool.shutdownNow();

        System.out.println("[INFO] ChatServer đã dừng hoàn toàn.");
    }

    public UserStore getUserStore() {
        return userStore;
    }

    public int getOnlineClientCount() {
        return clients.size();
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }
}
