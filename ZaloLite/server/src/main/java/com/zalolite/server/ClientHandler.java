package com.zalolite.server;

import com.zalolite.common.Packet;
import com.zalolite.common.PacketType;
import com.zalolite.common.User;
import com.zalolite.server.dao.UserDao;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Một luồng phục vụ một client: đọc từng dòng JSON, xử lý, trả lời. */
public class ClientHandler implements Runnable {
    private static final int MAX_LINE = 64 * 1024;
    private static final Pattern PHONE = Pattern.compile("0\\d{9,10}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private final Socket socket;
    private final SessionRegistry sessions;
    private final UserDao users;
    private BufferedWriter out;
    private User me; // null khi chưa đăng nhập

    public ClientHandler(Socket socket, SessionRegistry sessions, UserDao users) {
        this.socket = socket;
        this.sessions = sessions;
        this.users = users;
    }

    @Override
    public void run() {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.length() > MAX_LINE) {
                    send(Packet.error(null, "Gói tin quá lớn"));
                    continue;
                }
                Packet req;
                try {
                    req = Packet.fromJson(line);
                } catch (Exception e) {
                    send(Packet.error(null, "Gói tin sai định dạng"));
                    continue;
                }
                try {
                    handle(req);
                } catch (Exception e) {
                    System.err.println("[ERROR] " + req.type + ": " + e);
                    send(Packet.error(req, "Lỗi máy chủ"));
                }
            }
        } catch (IOException e) {
            // client ngắt kết nối
        } finally {
            if (me != null) {
                sessions.remove(me.id, this);
                System.out.println("[INFO] " + me.phone + " đã thoát");
            }
        }
    }

    private void handle(Packet req) throws Exception {
        if (me == null && !PacketType.LOGIN.equals(req.type) && !PacketType.REGISTER.equals(req.type)) {
            send(Packet.error(req, "Chưa đăng nhập"));
            return;
        }
        switch (req.type) {
            case PacketType.REGISTER -> register(req);
            case PacketType.LOGIN -> login(req);
            case PacketType.LOGOUT -> logout(req);
            default -> send(Packet.error(req, "Không hỗ trợ: " + req.type));
        }
    }

    private void register(Packet req) throws Exception {
        String phone = trim(req.str("phone"));
        String name = trim(req.str("name"));
        String email = trim(req.str("email"));
        String pass = req.str("password");
        if (phone == null || !PHONE.matcher(phone).matches()) {
            send(Packet.error(req, "Số điện thoại không hợp lệ"));
        } else if (name == null || name.isEmpty() || name.length() > 60) {
            send(Packet.error(req, "Tên phải từ 1 đến 60 ký tự"));
        } else if (email != null && !email.isEmpty() && !EMAIL.matcher(email).matches()) {
            send(Packet.error(req, "Email không hợp lệ"));
        } else if (pass == null || pass.length() < 6 || pass.length() > 72) {
            send(Packet.error(req, "Mật khẩu phải từ 6 đến 72 ký tự"));
        } else {
            User u = users.register(phone, name, email == null || email.isEmpty() ? null : email, pass);
            send(u == null ? Packet.error(req, "Số điện thoại đã được đăng ký") : Packet.ok(req));
        }
    }

    private void login(Packet req) throws Exception {
        String phone = trim(req.str("phone"));
        String pass = req.str("password");
        User u = phone == null || pass == null ? null : users.login(phone, pass);
        if (u == null) {
            send(Packet.error(req, "Sai số điện thoại hoặc mật khẩu"));
            return;
        }
        if (me != null) sessions.remove(me.id, this);
        me = u;
        sessions.add(u.id, this);
        System.out.println("[INFO] " + u.phone + " đăng nhập từ " + socket.getRemoteSocketAddress());
        send(Packet.ok(req).put("user", u));
    }

    private void logout(Packet req) {
        sessions.remove(me.id, this);
        me = null;
        send(Packet.ok(req));
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    /** Ghi một gói tin; synchronized vì nhiều luồng (người khác nhắn tới) có thể cùng gửi cho client này. */
    public synchronized void send(Packet p) {
        try {
            out.write(p.toJson());
            out.write('\n');
            out.flush();
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
