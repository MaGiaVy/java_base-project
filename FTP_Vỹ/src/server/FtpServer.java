package server;

import common.FtpConstants;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Lõi FTP Server xử lý đa luồng (Multi-threading) hỗ trợ nhiều Client đồng thời (Slide 26, 28)
 */
public class FtpServer {

    private final int port;
    private final Path rootDir;
    private final UserManager userManager;
    private final Consumer<String> logger;
    private final Runnable onSessionsChanged;

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private volatile boolean running = false;

    private final AtomicInteger sessionCounter = new AtomicInteger(1);
    private final ConcurrentHashMap<String, FtpSession> activeSessions = new ConcurrentHashMap<>();

    public FtpServer(int port, Path rootDir, Consumer<String> logger, Runnable onSessionsChanged) {
        this.port = port;
        this.rootDir = rootDir.toAbsolutePath().normalize();
        this.userManager = new UserManager();
        this.logger = logger != null ? logger : s -> {};
        this.onSessionsChanged = onSessionsChanged != null ? onSessionsChanged : () -> {};
    }

    public synchronized void start() throws IOException {
        if (running) return;

        if (!Files.exists(rootDir)) {
            Files.createDirectories(rootDir);
        }

        serverSocket = new ServerSocket(port);
        threadPool = Executors.newCachedThreadPool();
        running = true;

        log("[SERVER] FTP Server đã khởi động thành công trên Port " + port);
        log("[SERVER] Thư mục gốc FtpRoot: " + rootDir);
        log("[SERVER] Sẵn sàng phục vụ nhiều Client đồng thời (RFC 959 Multi-client)");

        Thread acceptThread = new Thread(this::listenLoop, "FtpServer-AcceptThread");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void listenLoop() {
        while (running && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                String sid = String.format("#S-%03d", sessionCounter.getAndIncrement());
                FtpSession session = new FtpSession(sid, clientSocket);

                activeSessions.put(sid, session);
                notifySessionsChanged();

                ClientHandler handler = new ClientHandler(
                        session,
                        rootDir,
                        userManager,
                        logger,
                        () -> {
                            activeSessions.remove(sid);
                            notifySessionsChanged();
                        }
                );

                threadPool.submit(handler);
            } catch (IOException e) {
                if (running) {
                    log("[SERVER] Ngoại lệ khi chấp nhận kết nối: " + e.getMessage());
                }
            }
        }
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;

        log("[SERVER] Đang dừng FTP Server...");

        // Đóng toàn bộ các phiên client đang hoạt động
        for (FtpSession session : activeSessions.values()) {
            session.closeAll();
        }
        activeSessions.clear();
        notifySessionsChanged();

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }

        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdownNow();
        }

        log("[SERVER] FTP Server đã dừng hoàn toàn.");
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public Path getRootDir() {
        return rootDir;
    }

    public List<FtpSession> getActiveSessions() {
        return Collections.unmodifiableList(new ArrayList<>(activeSessions.values()));
    }

    private void log(String msg) {
        logger.accept(msg);
    }

    private void notifySessionsChanged() {
        onSessionsChanged.run();
    }
}
