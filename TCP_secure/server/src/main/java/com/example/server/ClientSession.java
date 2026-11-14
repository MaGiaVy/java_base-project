package com.example.server;

import com.example.model.ChatMessage;

import javax.net.ssl.SSLSocket;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Đại diện cho một phiên làm việc của client kết nối đến server.
 * Sở hữu ReentrantLock (sendLock) để chống xung đột luồng khi broadcast tin nhắn,
 * tránh hiện tượng xé lẻ dữ liệu (interleaved byte stream).
 */
public class ClientSession {
    private final int id;
    private final SSLSocket socket;
    private volatile String username;
    private volatile boolean authenticated = false;
    private final ReentrantLock sendLock = new ReentrantLock();
    private final BufferedWriter writer;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public ClientSession(int id, SSLSocket socket) throws IOException {
        this.id = id;
        this.socket = socket;
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    /**
     * Gửi thông điệp tới client một cách an toàn luồng (Thread-Safe).
     * Bắt buộc giữ sendLock trước khi ghi và giải phóng trong khối finally.
     *
     * @param msg Thông điệp cần gửi
     * @throws IOException Nếu xảy ra lỗi I/O hoặc socket đã đóng
     */
    public void send(ChatMessage msg) throws IOException {
        if (msg == null || isClosed()) {
            return;
        }

        sendLock.lock();
        try {
            String json = msg.toJson();
            writer.write(json);
            writer.flush();
        } finally {
            sendLock.unlock();
        }
    }

    /**
     * Đóng phiên làm việc và giải phóng tài nguyên kết nối an toàn.
     * Thao tác có tính chất idempotent (gọi nhiều lần không gây lỗi).
     */
    public void close() {
        if (closed.compareAndSet(false, true)) {
            authenticated = false;
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException ignored) {}

            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {}
        }
    }

    public boolean isClosed() {
        return closed.get() || socket == null || socket.isClosed();
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public SSLSocket getSocket() {
        return socket;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public ReentrantLock getSendLock() {
        return sendLock;
    }

    @Override
    public String toString() {
        return "ClientSession{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", authenticated=" + authenticated +
                ", closed=" + isClosed() +
                '}';
    }
}
