package com.example.server;

import com.example.model.ChatMessage;
import com.example.model.MessageType;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

/**
 * Xử lý luồng giao tiếp với một client cụ thể trên một thread riêng biệt.
 * Kiểm soát bắt tay TLS, trạng thái đăng nhập, xác thực bằng BCrypt, và chống tấn công DoS/tràn bộ nhớ
 * thông qua việc giới hạn độ dài dòng đọc tối đa 4096 ký tự (4 KB).
 */
public class ClientHandler implements Runnable {
    public static final int MAX_LINE_LENGTH = 4096;

    private final ChatServer server;
    private final ClientSession session;

    public ClientHandler(ChatServer server, ClientSession session) {
        this.server = server;
        this.session = session;
    }

    @Override
    public void run() {
        String clientInfo = String.format("Client #%d (%s)", session.getId(), session.getSocket().getRemoteSocketAddress());
        try {
            // Thực hiện TLS Handshake sớm để kiểm tra mã hóa và xác nhận giao thức
            session.getSocket().startHandshake();
            SSLSession sslSession = session.getSocket().getSession();
            System.out.printf("[PASS] Bắt tay TLS thành công với %s [Protocol: %s, Cipher: %s]%n",
                    clientInfo, sslSession.getProtocol(), sslSession.getCipherSuite());

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(session.getSocket().getInputStream(), StandardCharsets.UTF_8))) {

                while (!Thread.currentThread().isInterrupted() && !session.isClosed()) {
                    String line = readLineBounded(reader, MAX_LINE_LENGTH);
                    if (line == null) {
                        // Client đã đóng kết nối (EOF)
                        System.out.printf("[INFO] %s đóng luồng kết nối (EOF).%n", clientInfo);
                        break;
                    }

                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }

                    ChatMessage msg;
                    try {
                        msg = ChatMessage.fromJson(line);
                    } catch (Exception e) {
                        System.out.printf("[WARN] %s gửi dữ liệu JSON không hợp lệ: %s%n", clientInfo, e.getMessage());
                        continue;
                    }

                    handleMessage(msg);

                    // Nếu sau khi xử lý mà phiên đã đóng hoặc bị từ chối thì thoát vòng lặp
                    if (session.isClosed()) {
                        break;
                    }
                }
            }
        } catch (SSLHandshakeException e) {
            System.out.printf("[BLOCK] %s Bắt tay TLS thất bại (Kết nối Plain TCP hoặc chứng chỉ không hợp lệ): %s%n",
                    clientInfo, e.getMessage());
        } catch (SocketException | SSLException e) {
            System.out.printf("[INFO] %s ngắt kết nối: %s%n", clientInfo, e.getMessage());
        } catch (IOException e) {
            System.out.printf("[INFO] %s gặp lỗi I/O: %s%n", clientInfo, e.getMessage());
        } catch (Exception e) {
            System.err.printf("[WARN] Ngoại lệ ngoài ý muốn tại %s: %s%n", clientInfo, e.getMessage());
        } finally {
            server.removeClient(session.getId(), "Phiên làm việc kết thúc");
        }
    }

    /**
     * Xử lý thông điệp dựa trên trạng thái phiên đăng nhập.
     */
    private void handleMessage(ChatMessage msg) throws IOException {
        String rawType = msg.getType();
        MessageType type = MessageType.fromString(rawType);

        if (!session.isAuthenticated()) {
            // Khi chưa đăng nhập, CHỈ chấp nhận bản tin LOGIN
            if (type == MessageType.LOGIN) {
                processLogin(msg);
            } else {
                System.out.printf("[BLOCK] Client #%d cố gắng gửi '%s' khi chưa đăng nhập!%n",
                        session.getId(), rawType != null ? rawType : "UNKNOWN");
                ChatMessage failRes = ChatMessage.createLoginResult(false, session.getId(), "Yêu cầu đăng nhập trước.");
                try {
                    session.send(failRes);
                } catch (IOException ignored) {}
                server.removeClient(session.getId(), "Chưa xác thực mà gửi tin");
            }
        } else {
            // Đã đăng nhập thành công
            if (type == MessageType.MESSAGE) {
                processChatMessage(msg);
            } else if (type == MessageType.DISCONNECT) {
                System.out.printf("[INFO] Client #%d (%s) chủ động gửi DISCONNECT.%n", session.getId(), session.getUsername());
                server.removeClient(session.getId(), "Người dùng chủ động rời phòng");
            } else if (type == MessageType.PING) {
                // Nhận ping giữ kết nối
            } else {
                System.out.printf("[WARN] Client #%d (%s) gửi loại thông điệp không hỗ trợ: %s%n",
                        session.getId(), session.getUsername(), rawType);
            }
        }
    }

    /**
     * Xác thực tài khoản với BCrypt qua UserStore.
     * BẢO MẬT: Tuyệt đối không log mật khẩu thô ra màn hình/file.
     */
    private void processLogin(ChatMessage msg) throws IOException {
        String username = msg.getUsername();
        String password = msg.getPassword();

        // Kiểm tra tính hợp lệ cơ bản
        if (username == null || username.trim().isEmpty() || password == null) {
            rejectLogin(username, "Tên tài khoản hoặc mật khẩu không được để trống.");
            return;
        }

        username = username.trim();
        boolean authenticated = server.getUserStore().authenticate(username, password);

        if (authenticated) {
            session.setUsername(username);
            session.setAuthenticated(true);

            ChatMessage successMsg = ChatMessage.createLoginResult(true, session.getId(), "Đăng nhập thành công!");
            session.send(successMsg);

            System.out.printf("[PASS] Client #%d xác thực thành công với tài khoản: '%s'%n", session.getId(), username);

            // Broadcast thông báo cho toàn bộ phòng chat
            ChatMessage joinNotification = ChatMessage.createSystem(username + " đã tham gia phòng chat.");
            server.broadcast(joinNotification, null);
        } else {
            rejectLogin(username, "Sai tên đăng nhập hoặc mật khẩu.");
        }
    }

    /**
     * Từ chối đăng nhập và ngắt kết nối ngay lập tức.
     * Trả về thông báo lỗi chung để tránh tấn công dò quét tài khoản (Username Enumeration).
     */
    private void rejectLogin(String username, String reason) {
        System.out.printf("[BLOCK] Client #%d xác thực thất bại đối với tài khoản '%s' (Sai thông tin đăng nhập)%n",
                session.getId(), username != null ? username : "unknown");

        ChatMessage failMsg = ChatMessage.createLoginResult(false, session.getId(), reason);
        try {
            session.send(failMsg);
        } catch (IOException ignored) {}

        server.removeClient(session.getId(), "Xác thực thất bại");
    }

    /**
     * Xử lý tin nhắn chat từ client đã đăng nhập và broadcast tới mọi người.
     */
    private void processChatMessage(ChatMessage msg) {
        String content = msg.getContent();
        if (content == null || content.trim().isEmpty()) {
            return;
        }

        System.out.printf("[INFO] [%s]: %s%n", session.getUsername(), content);

        ChatMessage broadcastMsg = ChatMessage.createMessage(session.getUsername(), content);
        broadcastMsg.setClientId(session.getId());
        server.broadcast(broadcastMsg, null);
    }

    /**
     * Đọc một dòng ký tự với giới hạn kích thước tối đa maxChars (4KB)
     * nhằm ngăn ngừa các cuộc tấn công DoS tràn bộ nhớ từ luồng socket.
     */
    private String readLineBounded(BufferedReader reader, int maxChars) throws IOException {
        StringBuilder sb = new StringBuilder();
        int ch;
        while ((ch = reader.read()) != -1) {
            if (ch == '\n') {
                break;
            }
            if (ch == '\r') {
                continue; // Bỏ qua ký tự Carriage Return
            }
            sb.append((char) ch);
            if (sb.length() > maxChars) {
                throw new IOException("Độ dài dòng dữ liệu vượt quá giới hạn " + maxChars + " ký tự (Phát hiện DoS)");
            }
        }
        if (ch == -1 && sb.length() == 0) {
            return null; // Kết thúc luồng
        }
        return sb.toString();
    }
}
