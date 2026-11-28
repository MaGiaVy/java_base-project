package com.zalolite.server;

import com.zalolite.common.Conversation;
import com.zalolite.common.Message;
import com.zalolite.common.Packet;
import com.zalolite.common.PacketType;
import com.zalolite.common.User;
import com.zalolite.server.dao.ConversationDao;
import com.zalolite.server.dao.FriendDao;
import com.zalolite.server.dao.MessageDao;
import com.zalolite.server.dao.OtpDao;
import com.zalolite.server.dao.UserDao;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * [CỔNG/GIAO THỨC] [XỬ LÝ] Luồng phục vụ riêng cho từng kết nối Client trên kênh TCP/TLS (cổng 9000).
 * Kiến trúc: 1 kết nối TCP/TLS <-> 1 luồng ClientHandler trong thread pool của ZaloServer.
 * - Vòng lặp run() liên tục [NHẬN] từng dòng JSON (NDJSON) qua BufferedReader.readLine().
 * - Hàm handle(req) kiểm tra phiên đăng nhập và định tuyến toàn bộ 38 lệnh nghiệp vụ (Tài khoản/OTP, P2P, Bạn bè/Chặn,
 *   Báo hiệu gọi thoại 1-1, Hội thoại/Nhóm, Tin nhắn/Reaction/Typing/Seen).
 * - Mọi phản hồi trực tiếp đi qua send(Packet) (synchronized) và mọi sự kiện realtime đẩy tới người khác qua SessionRegistry.
 */
public class ClientHandler implements Runnable {
    /** Giới hạn tối đa 64KB cho 1 dòng JSON để chống tràn bộ nhớ (DoS). */
    private static final int MAX_LINE = 64 * 1024;
    private static final Pattern PHONE = Pattern.compile("0\\d{9,10}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    /** Bộ sinh số ngẫu nhiên an toàn mật mã dùng để tạo mã OTP 6 chữ số. */
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Các loại tin nhắn Client được phép gửi (không cho Client tự giả mạo KIND_SYSTEM). */
    private static final Set<String> CLIENT_MSG_KINDS = Set.of(
            Message.KIND_TEXT, Message.KIND_IMAGE, Message.KIND_FILE, Message.KIND_VIDEO, Message.KIND_AUDIO);

    private final Socket socket;
    private final SessionRegistry sessions;
    private final UserDao users;
    private final FriendDao friends;
    private final ConversationDao convs;
    private final MessageDao messages;
    private final OtpDao otps;
    private final MailOtpService mailOtp;
    private BufferedWriter out;
    private User me; // null khi chưa đăng nhập

    /** Khởi tạo ClientHandler gắn với 1 socket TLS vừa được ZaloServer.accept() và các dịch vụ dùng chung. */
    public ClientHandler(Socket socket, SessionRegistry sessions, UserDao users,
                         FriendDao friends, ConversationDao convs, MessageDao messages,
                         OtpDao otps, MailOtpService mailOtp) {
        this.socket = socket;
        this.sessions = sessions;
        this.users = users;
        this.friends = friends;
        this.convs = convs;
        this.messages = messages;
        this.otps = otps;
        this.mailOtp = mailOtp;
    }

    /**
     * [NHẬN] [XỬ LÝ] Vòng đời chính của một kết nối Client:
     * Mở luồng đọc/ghi UTF-8 trên socket TLS -> đọc từng dòng JSON -> parse Packet -> gọi handle(req).
     * Khi Client ngắt kết nối (EOF hoặc IOException), khối finally tự gỡ phiên khỏi SessionRegistry và phát PRESENCE offline.
     */
    @Override
    public void run() {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            String line;
            // [NHẬN] Đọc từng dòng JSON kết thúc bằng '\n' từ kênh TCP/TLS của Client (trả null khi Client đóng kết nối)
            while ((line = in.readLine()) != null) {
                // [XỬ LÝ] Chặn gói tin vượt quá 64KB
                if (line.length() > MAX_LINE) {
                    send(Packet.error(null, "Gói tin quá lớn"));
                    continue;
                }
                Packet req;
                try {
                    // [NHẬN] Giải mã chuỗi JSON thành đối tượng Packet
                    req = Packet.fromJson(line);
                } catch (Exception e) {
                    send(Packet.error(null, "Gói tin sai định dạng"));
                    continue;
                }
                try {
                    // [XỬ LÝ] Định tuyến gói tin tới hàm nghiệp vụ tương ứng theo req.type
                    handle(req);
                } catch (IllegalArgumentException e) {
                    send(Packet.error(req, e.getMessage()));
                } catch (Exception e) {
                    System.err.println("[ERROR] " + req.type + ": " + e);
                    send(Packet.error(req, "Lỗi máy chủ"));
                }
            }
        } catch (IOException e) {
            // client ngắt kết nối
        } finally {
            // [XỬ LÝ] Dọn phiên đăng nhập khi mất kết nối và thông báo offline tới bạn bè nếu hết thiết bị online
            if (me != null) {
                long oldId = me.id;
                String oldPhone = me.phone;
                me = null;
                if (sessions.remove(oldId, this)) notifyPresence(oldId, false);
                System.out.println("[INFO] " + oldPhone + " đã thoát");
            }
        }
    }

    /** [XỬ LÝ] Kiểm tra xác thực (chỉ 4 gói công khai được phép chạy khi chưa đăng nhập) và điều phối theo PacketType. */
    private void handle(Packet req) throws Exception {
        // [XỬ LÝ] Cổng bảo vệ xác thực: chặn mọi gói nghiệp vụ nếu chưa LOGIN (trừ LOGIN, REGISTER, OTP_REQUEST, OTP_RESET_PASSWORD)
        if (me == null
                && !PacketType.LOGIN.equals(req.type)
                && !PacketType.REGISTER.equals(req.type)
                && !PacketType.OTP_REQUEST.equals(req.type)
                && !PacketType.OTP_RESET_PASSWORD.equals(req.type)) {
            send(Packet.error(req, "Chưa đăng nhập"));
            return;
        }
        switch (req.type) {
            case PacketType.REGISTER -> register(req);
            case PacketType.LOGIN -> login(req);
            case PacketType.LOGOUT -> logout(req);
            case PacketType.PROFILE_UPDATE -> profileUpdate(req);
            case PacketType.PASSWORD_CHANGE -> passwordChange(req);
            case PacketType.OTP_REQUEST -> otpRequest(req);
            case PacketType.OTP_RESET_PASSWORD -> otpResetPassword(req);
            case PacketType.P2P_REGISTER -> p2pRegister(req);
            case PacketType.P2P_LOOKUP -> p2pLookup(req);
            case PacketType.USER_SEARCH -> userSearch(req);
            case PacketType.FRIEND_LIST -> friendList(req);
            case PacketType.FRIEND_REQUEST -> friendRequest(req);
            case PacketType.FRIEND_ACCEPT -> friendAccept(req);
            case PacketType.FRIEND_REJECT -> friendReject(req);
            case PacketType.FRIEND_REMOVE -> friendRemove(req);
            case PacketType.USER_BLOCK -> userBlock(req);
            case PacketType.USER_UNBLOCK -> userUnblock(req);
            case PacketType.BLOCK_LIST -> blockList(req);
            case PacketType.CALL_OFFER -> callOffer(req);
            case PacketType.CALL_ANSWER -> callAnswer(req);
            case PacketType.CALL_REJECT -> callReject(req);
            case PacketType.CALL_END -> callEnd(req);
            case PacketType.CONV_LIST -> convList(req);
            case PacketType.CONV_OPEN_DIRECT -> convOpenDirect(req);
            case PacketType.CONV_PIN -> convPin(req);
            case PacketType.GROUP_CREATE -> groupCreate(req);
            case PacketType.GROUP_MEMBERS -> groupMembers(req);
            case PacketType.GROUP_ADD_MEMBER -> groupAddMember(req);
            case PacketType.GROUP_REMOVE_MEMBER -> groupRemoveMember(req);
            case PacketType.GROUP_LEAVE -> groupLeave(req);
            case PacketType.GROUP_RENAME -> groupRename(req);
            case PacketType.MSG_SEND -> msgSend(req);
            case PacketType.MSG_HISTORY -> msgHistory(req);
            case PacketType.MSG_RECALL -> msgRecall(req);
            case PacketType.MSG_REACT -> msgReact(req);
            case PacketType.MSG_PIN -> msgPin(req);
            case PacketType.MSG_SEARCH -> msgSearch(req);
            case PacketType.TYPING -> typing(req);
            case PacketType.MSG_SEEN -> msgSeen(req);
            default -> send(Packet.error(req, "Không hỗ trợ: " + req.type));
        }
    }

    // --- Tài khoản & hồ sơ ---

    /** [XỬ LÝ] [GỬI] Xử lý REGISTER: kiểm tra định dạng SĐT/tên/email/mật khẩu, gọi UserDao.register() (BCrypt) và trả kết quả. */
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
            // [GỬI] Trả RESPONSE ok nếu tạo thành công hoặc lỗi nếu SĐT đã tồn tại
            send(u == null ? Packet.error(req, "Số điện thoại đã được đăng ký") : Packet.ok(req));
        }
    }

    /** [XỬ LÝ] [GỬI] Xử lý LOGIN: xác thực BCrypt qua UserDao, đăng ký phiên vào SessionRegistry, trả thông tin user và phát PRESENCE online. */
    private void login(Packet req) throws Exception {
        String phone = trim(req.str("phone"));
        String pass = req.str("password");
        User u = phone == null || pass == null ? null : users.login(phone, pass);
        if (u == null) {
            send(Packet.error(req, "Sai số điện thoại hoặc mật khẩu"));
            return;
        }
        // [XỬ LÝ] Nếu kết nối này đang đăng nhập tài khoản khác thì gỡ phiên cũ trước
        if (me != null && me.id != u.id && sessions.remove(me.id, this)) {
            notifyPresence(me.id, false);
        }
        me = u;
        me.online = true;
        boolean becameOnline = sessions.add(u.id, this);
        System.out.println("[INFO] " + u.phone + " đăng nhập từ " + socket.getRemoteSocketAddress());
        // [GỬI] Trả RESPONSE ok kèm đối tượng User đã đăng nhập
        send(Packet.ok(req).put("user", u));
        // [GỬI] Nếu user vừa chuyển từ offline -> online thì đẩy gói PRESENCE tới tất cả bạn bè đang online
        if (becameOnline) notifyPresence(u.id, true);
    }

    /** [XỬ LÝ] [GỬI] Xử lý LOGOUT: gỡ phiên khỏi SessionRegistry, phát PRESENCE offline nếu hết phiên và trả RESPONSE ok. */
    private void logout(Packet req) {
        long oldId = me.id;
        me = null;
        if (sessions.remove(oldId, this)) notifyPresence(oldId, false);
        // [GỬI] Xác nhận đăng xuất thành công
        send(Packet.ok(req));
    }

    /** [XỬ LÝ] [GỬI] Xử lý PROFILE_UPDATE: kiểm tra tên/email, cập nhật bảng users và trả về đối tượng User mới. */
    private void profileUpdate(Packet req) throws Exception {
        String name = trim(req.str("name"));
        String email = trim(req.str("email"));
        if (name == null || name.isEmpty() || name.length() > 60) {
            send(Packet.error(req, "Tên phải từ 1 đến 60 ký tự"));
        } else if (email != null && !email.isEmpty() && !EMAIL.matcher(email).matches()) {
            send(Packet.error(req, "Email không hợp lệ"));
        } else {
            User updated = users.updateProfile(me.id, name, email == null || email.isEmpty() ? null : email);
            if (updated == null) {
                send(Packet.error(req, "Không tìm thấy tài khoản"));
                return;
            }
            updated.online = true;
            me = updated;
            // [GỬI] Trả về hồ sơ đã cập nhật
            send(Packet.ok(req).put("user", updated));
        }
    }

    /** [XỬ LÝ] [GỬI] Xử lý PASSWORD_CHANGE: kiểm tra mật khẩu cũ/mới và đổi mật khẩu qua UserDao.changePassword(). */
    private void passwordChange(Packet req) throws Exception {
        String oldPass = req.str("oldPassword");
        String newPass = req.str("newPassword");
        if (oldPass == null || oldPass.isEmpty()) {
            send(Packet.error(req, "Mật khẩu cũ không được trống"));
        } else if (newPass == null || newPass.length() < 6 || newPass.length() > 72) {
            send(Packet.error(req, "Mật khẩu mới phải từ 6 đến 72 ký tự"));
        } else if (!users.changePassword(me.id, oldPass, newPass)) {
            send(Packet.error(req, "Mật khẩu cũ không đúng"));
        } else {
            // [GỬI] Xác nhận đổi mật khẩu thành công
            send(Packet.ok(req));
        }
    }

    /** [XỬ LÝ] [GỬI] Xử lý OTP_REQUEST: sinh mã OTP 6 chữ số ngẫu nhiên, lưu bảng otp (TTL 5 phút), gửi qua MailOtpService và trả email đã che. */
    private void otpRequest(Packet req) throws Exception {
        String phone = trim(req.str("phone"));
        User u = phone == null ? null : users.findByPhone(phone);
        if (u == null) {
            send(Packet.error(req, "Không tìm thấy tài khoản với số điện thoại này"));
            return;
        }
        if (u.email == null || u.email.isBlank()) {
            send(Packet.error(req, "Tài khoản chưa liên kết email để nhận OTP"));
            return;
        }
        // [XỬ LÝ] Sinh mã OTP 6 chữ số bằng SecureRandom và lưu vào PostgreSQL (hết hạn sau 5 phút)
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        otps.save(u.email, code, 5);
        // [GỬI] Gửi mã OTP qua ESMTP (cổng 587/465) hoặc in ra console nếu ở chế độ fallback
        mailOtp.sendOtp(u.email, code);
        // [GỬI] Trả về email đã che (vd: ab***@gmail.com) để Client hiển thị xác nhận
        send(Packet.ok(req)
                .put("maskedEmail", maskEmail(u.email))
                .put("consoleMode", mailOtp.isConsoleMode()));
    }

    /** [XỬ LÝ] [GỬI] Xử lý OTP_RESET_PASSWORD: xác thực + tiêu thụ mã OTP 1 lần rồi cập nhật mật khẩu mới cho tài khoản. */
    private void otpResetPassword(Packet req) throws Exception {
        String phone = trim(req.str("phone"));
        String code = trim(req.str("code"));
        String newPass = req.str("newPassword");
        if (newPass == null || newPass.length() < 6 || newPass.length() > 72) {
            send(Packet.error(req, "Mật khẩu mới phải từ 6 đến 72 ký tự"));
            return;
        }
        if (code == null || code.isEmpty()) {
            send(Packet.error(req, "Mã OTP không được trống"));
            return;
        }
        User u = phone == null ? null : users.findByPhone(phone);
        if (u == null || u.email == null || u.email.isBlank()) {
            send(Packet.error(req, "Tài khoản không hợp lệ"));
            return;
        }
        // [XỬ LÝ] Kiểm tra và xóa ngay mã OTP (dùng 1 lần, kiểm tra hạn 5 phút)
        if (!otps.verifyAndConsume(u.email, code)) {
            send(Packet.error(req, "Mã OTP không đúng hoặc đã hết hạn"));
            return;
        }
        if (!users.resetPasswordByEmail(u.email, newPass)) {
            send(Packet.error(req, "Không thể đặt lại mật khẩu"));
            return;
        }
        // [GỬI] Xác nhận đặt lại mật khẩu thành công
        send(Packet.ok(req));
    }

    // --- P2P ---

    /** [XỬ LÝ] [GỬI] Xử lý P2P_REGISTER: lấy IP thật của Client từ socket kết nối và lưu cặp (ip, port) P2P vào SessionRegistry. */
    private void p2pRegister(Packet req) {
        int port = (int) req.num("port");
        if (port < 1 || port > 65535) {
            send(Packet.error(req, "Cổng P2P không hợp lệ"));
            return;
        }
        // [XỬ LÝ] Tự lấy IP phía Client từ socket TCP đang kết nối để tránh Client khai báo sai IP
        String ip = socket.getInetAddress().getHostAddress();
        sessions.registerP2p(me.id, ip, port);
        // [GỬI] Trả lại IP và cổng P2P đã đăng ký
        send(Packet.ok(req).put("ip", ip).put("port", port));
    }

    /** [XỬ LÝ] [GỬI] Xử lý P2P_LOOKUP: tra cứu trạng thái online và địa chỉ (ip, port) P2P của đối phương để Client tự mở kết nối trực tiếp. */
    private void p2pLookup(Packet req) {
        long userId = req.num("userId");
        boolean online = sessions.isOnline(userId);
        SessionRegistry.P2pEndpoint ep = sessions.getP2p(userId);
        // [GỬI] Trả về trạng thái online và điểm cuối P2P (ip, port) của đối phương
        send(Packet.ok(req)
                .put("online", online)
                .put("ip", ep == null ? null : ep.ip())
                .put("port", ep == null ? 0 : ep.port()));
    }

    // --- Bạn bè ---

    /** [XỬ LÝ] [GỬI] Xử lý USER_SEARCH: tìm người dùng theo SĐT/tên trong UserDao, gắn cờ online từ SessionRegistry và trả về Client. */
    private void userSearch(Packet req) throws Exception {
        List<User> list = users.search(req.str("query"), me.id);
        for (User u : list) u.online = sessions.isOnline(u.id);
        send(Packet.ok(req).put("users", list));
    }

    /** [XỬ LÝ] [GỬI] Xử lý FRIEND_LIST: trả về đồng thời danh sách bạn bè (kèm cờ online), lời mời đến và lời mời đã gửi. */
    private void friendList(Packet req) throws Exception {
        List<User> friendList = friends.listFriends(me.id);
        for (User u : friendList) u.online = sessions.isOnline(u.id);
        send(Packet.ok(req)
                .put("friends", friendList)
                .put("incoming", friends.listIncoming(me.id))
                .put("outgoing", friends.listOutgoing(me.id)));
    }

    /** [XỬ LÝ] [GỬI] Xử lý FRIEND_REQUEST: lưu lời mời PENDING vào FriendDao, trả ok cho người gửi và đẩy FRIEND_EVENT (REQUEST) tới người nhận. */
    private void friendRequest(Packet req) throws Exception {
        long targetId = req.num("targetId");
        if (targetId <= 0 || users.findById(targetId) == null) {
            send(Packet.error(req, "Không tìm thấy người dùng"));
            return;
        }
        String err = friends.request(me.id, targetId);
        if (err != null) {
            send(Packet.error(req, err));
            return;
        }
        send(Packet.ok(req));
        // [GỬI] Đẩy thông báo lời mời kết bạn realtime tới đối phương nếu đang online
        sessions.sendTo(targetId, new Packet(PacketType.FRIEND_EVENT).put("action", "REQUEST").put("user", me));
    }

    /** [XỬ LÝ] [GỬI] Xử lý FRIEND_ACCEPT: chấp nhận lời mời trong FriendDao và đẩy FRIEND_EVENT (ACCEPTED) cho người đã gửi lời mời. */
    private void friendAccept(Packet req) throws Exception {
        long requesterId = req.num("userId");
        if (!friends.accept(me.id, requesterId)) {
            send(Packet.error(req, "Không tìm thấy lời mời kết bạn"));
            return;
        }
        send(Packet.ok(req));
        // [GỬI] Thông báo realtime cho người gửi lời mời biết đã được chấp nhận
        sessions.sendTo(requesterId, new Packet(PacketType.FRIEND_EVENT).put("action", "ACCEPTED").put("user", me));
    }

    /** [XỬ LÝ] [GỬI] Xử lý FRIEND_REJECT: từ chối lời mời trong FriendDao và đẩy FRIEND_EVENT (REJECTED) cho người gửi lời mời. */
    private void friendReject(Packet req) throws Exception {
        long requesterId = req.num("userId");
        if (!friends.reject(me.id, requesterId)) {
            send(Packet.error(req, "Không tìm thấy lời mời kết bạn"));
            return;
        }
        send(Packet.ok(req));
        sessions.sendTo(requesterId, new Packet(PacketType.FRIEND_EVENT).put("action", "REJECTED").put("userId", me.id));
    }

    /** [XỬ LÝ] [GỬI] Xử lý FRIEND_REMOVE: hủy kết bạn trong FriendDao và đẩy FRIEND_EVENT (REMOVED) cho đối phương. */
    private void friendRemove(Packet req) throws Exception {
        long otherId = req.num("userId");
        if (!friends.remove(me.id, otherId)) {
            send(Packet.error(req, "Không tìm thấy quan hệ bạn bè"));
            return;
        }
        send(Packet.ok(req));
        sessions.sendTo(otherId, new Packet(PacketType.FRIEND_EVENT).put("action", "REMOVED").put("userId", me.id));
    }

    /** [XỬ LÝ] [GỬI] Xử lý USER_BLOCK: ghi nhận trạng thái BLOCKED trong FriendDao để chặn nhắn tin và gọi thoại 1-1. */
    private void userBlock(Packet req) throws Exception {
        long targetId = req.num("userId");
        if (targetId <= 0 || targetId == me.id || users.findById(targetId) == null) {
            send(Packet.error(req, "Người dùng không hợp lệ"));
            return;
        }
        friends.block(me.id, targetId);
        send(Packet.ok(req));
    }

    /** [XỬ LÝ] [GỬI] Xử lý USER_UNBLOCK: gỡ trạng thái chặn đối với targetId trong FriendDao. */
    private void userUnblock(Packet req) throws Exception {
        long targetId = req.num("userId");
        if (!friends.unblock(me.id, targetId)) {
            send(Packet.error(req, "Người dùng không nằm trong danh sách chặn"));
            return;
        }
        send(Packet.ok(req));
    }

    /** [XỬ LÝ] [GỬI] Xử lý BLOCK_LIST: trả về danh sách người dùng đang bị tài khoản hiện tại chặn. */
    private void blockList(Packet req) throws Exception {
        send(Packet.ok(req).put("blocked", friends.listBlocked(me.id)));
    }

    // --- Báo hiệu gọi thoại 1-1 (Signaling qua TLS cổng 9000; luồng âm thanh PCM truyền trực tiếp UDP giữa 2 Client) ---

    /** [XỬ LÝ] [GỬI] Xử lý CALL_OFFER: kiểm tra chặn & trạng thái online, gắn IP thật của người gọi + udpPort rồi chuyển tiếp tới người nhận. */
    private void callOffer(Packet req) throws Exception {
        long targetId = req.num("targetId");
        int udpPort = (int) req.num("udpPort");
        if (targetId <= 0 || targetId == me.id) {
            send(Packet.error(req, "Người nhận cuộc gọi không hợp lệ"));
            return;
        }
        if (udpPort < 1 || udpPort > 65535) {
            send(Packet.error(req, "Cổng UDP không hợp lệ"));
            return;
        }
        if (friends.isBlockedEitherWay(me.id, targetId)) {
            send(Packet.error(req, "Không thể gọi do một bên đã chặn"));
            return;
        }
        if (!sessions.isOnline(targetId)) {
            send(Packet.error(req, "Người dùng hiện không trực tuyến"));
            return;
        }
        // [XỬ LÝ] [GỬI] Lấy IP người gọi từ socket và đẩy gói báo hiệu CALL_OFFER kèm cổng UDP âm thanh tới người nhận
        String callerIp = socket.getInetAddress().getHostAddress();
        sessions.sendTo(targetId, new Packet(PacketType.CALL_OFFER)
                .put("callerId", me.id)
                .put("callerName", me.name)
                .put("callerIp", callerIp)
                .put("udpPort", udpPort));
        send(Packet.ok(req));
    }

    /** [XỬ LÝ] [GỬI] Xử lý CALL_ANSWER: gửi IP và cổng UDP âm thanh của người nghe về lại cho người gọi để hai bên bắt đầu trao đổi giọng nói UDP. */
    private void callAnswer(Packet req) {
        long callerId = req.num("callerId");
        int udpPort = (int) req.num("udpPort");
        if (callerId <= 0 || udpPort < 1 || udpPort > 65535) {
            send(Packet.error(req, "Thông tin trả lời cuộc gọi không hợp lệ"));
            return;
        }
        String calleeIp = socket.getInetAddress().getHostAddress();
        // [GỬI] Đẩy gói CALL_ANSWER chứa calleeIp:udpPort tới người gọi
        sessions.sendTo(callerId, new Packet(PacketType.CALL_ANSWER)
                .put("calleeId", me.id)
                .put("calleeIp", calleeIp)
                .put("udpPort", udpPort));
        send(Packet.ok(req));
    }

    /** [GỬI] Xử lý CALL_REJECT: chuyển tiếp tín hiệu từ chối cuộc gọi (kèm lý do) về phía người gọi. */
    private void callReject(Packet req) {
        long callerId = req.num("callerId");
        String reason = req.str("reason");
        sessions.sendTo(callerId, new Packet(PacketType.CALL_REJECT)
                .put("calleeId", me.id)
                .put("reason", reason == null ? "" : reason));
        send(Packet.ok(req));
    }

    /** [GỬI] Xử lý CALL_END: chuyển tiếp tín hiệu kết thúc cuộc gọi tới đầu dây bên kia. */
    private void callEnd(Packet req) {
        long peerId = req.num("peerId");
        sessions.sendTo(peerId, new Packet(PacketType.CALL_END).put("userId", me.id));
        send(Packet.ok(req));
    }

    // --- Hội thoại & nhóm ---

    /** [XỬ LÝ] [GỬI] Xử lý CONV_LIST: tải toàn bộ danh sách hội thoại của user hiện tại (kèm Cloud, tin cuối, số chưa đọc). */
    private void convList(Packet req) throws Exception {
        send(Packet.ok(req).put("conversations", convs.listForUser(me.id, sessions)));
    }

    /** [XỬ LÝ] [GỬI] Xử lý CONV_OPEN_DIRECT: mở hoặc tạo mới cuộc hội thoại 1-1 giữa me và peerId. */
    private void convOpenDirect(Packet req) throws Exception {
        long peerId = req.num("peerId");
        if (peerId <= 0 || peerId == me.id || users.findById(peerId) == null) {
            send(Packet.error(req, "Người dùng không hợp lệ"));
            return;
        }
        Conversation conv = convs.openOrCreateDirect(me.id, peerId, sessions);
        send(Packet.ok(req).put("conversation", conv));
    }

    /** [XỬ LÝ] [GỬI] Xử lý CONV_PIN: bật hoặc tắt ghim hội thoại cho người dùng hiện tại. */
    private void convPin(Packet req) throws Exception {
        long convId = req.num("convId");
        if (!convs.setPinned(convId, me.id, req.bool("pinned"))) {
            send(Packet.error(req, "Bạn không thuộc hội thoại này"));
            return;
        }
        send(Packet.ok(req));
    }

    /** [XỬ LÝ] [GỬI] Xử lý GROUP_CREATE: tạo nhóm mới trong ConversationDao, trả cho người tạo và đẩy CONV_UPDATE tới các thành viên còn lại. */
    private void groupCreate(Packet req) throws Exception {
        String name = trim(req.str("name"));
        if (name == null || name.isEmpty() || name.length() > 80) {
            send(Packet.error(req, "Tên nhóm phải từ 1 đến 80 ký tự"));
            return;
        }
        List<Long> memberIds = req.getList("memberIds", Long.class);
        Conversation conv = convs.createGroup(me.id, name, memberIds, sessions);
        send(Packet.ok(req).put("conversation", conv));
        // [GỬI] Thông báo nhóm mới vừa tạo tới các thành viên khác đang online
        sessions.sendToOthers(convs.getMemberIds(conv.id), me.id,
                new Packet(PacketType.CONV_UPDATE).put("conversation", conv));
    }

    /** [XỬ LÝ] [GỬI] Xử lý GROUP_MEMBERS: kiểm tra quyền thành viên rồi trả danh sách thành viên của hội thoại. */
    private void groupMembers(Packet req) throws Exception {
        long convId = req.num("convId");
        if (!convs.isMember(convId, me.id)) {
            send(Packet.error(req, "Bạn không thuộc hội thoại này"));
            return;
        }
        send(Packet.ok(req).put("members", convs.getMembers(convId, sessions)));
    }

    /** [XỬ LÝ] [GỬI] Xử lý GROUP_ADD_MEMBER: kiểm tra quyền trưởng nhóm (OWNER), thêm thành viên, lưu tin hệ thống và broadcast cho cả nhóm. */
    private void groupAddMember(Packet req) throws Exception {
        long convId = req.num("convId");
        long userId = req.num("userId");
        // [XỬ LÝ] Chỉ trưởng nhóm (OWNER) mới được phép thêm thành viên
        if (!convs.isOwner(convId, me.id)) {
            send(Packet.error(req, "Chỉ trưởng nhóm mới có quyền thêm thành viên"));
            return;
        }
        User target = users.findById(userId);
        if (target == null) {
            send(Packet.error(req, "Không tìm thấy người dùng"));
            return;
        }
        if (!convs.addMember(convId, userId)) {
            send(Packet.error(req, "Người dùng đã ở trong nhóm"));
            return;
        }
        Message sys = saveSystemMsg(convId, me.name + " đã thêm " + target.name + " vào nhóm");
        send(Packet.ok(req));
        // [GỬI] Phát tin nhắn hệ thống và cập nhật hội thoại tới toàn bộ thành viên nhóm
        broadcastGroupChange(convId, sys);
    }

    /** [XỬ LÝ] [GỬI] Xử lý GROUP_REMOVE_MEMBER: kiểm tra quyền OWNER, xóa thành viên, broadcast tin hệ thống và gửi gói removed=true cho người bị xóa. */
    private void groupRemoveMember(Packet req) throws Exception {
        long convId = req.num("convId");
        long userId = req.num("userId");
        // [XỬ LÝ] Chỉ trưởng nhóm mới được xóa thành viên khác (không tự xóa chính mình)
        if (!convs.isOwner(convId, me.id)) {
            send(Packet.error(req, "Chỉ trưởng nhóm mới có quyền xóa thành viên"));
            return;
        }
        if (userId == me.id) {
            send(Packet.error(req, "Trưởng nhóm không thể tự xóa mình, hãy chọn rời nhóm"));
            return;
        }
        User target = users.findById(userId);
        if (!convs.removeMember(convId, userId)) {
            send(Packet.error(req, "Người dùng không có trong nhóm"));
            return;
        }
        Message sys = saveSystemMsg(convId, me.name + " đã xóa " + (target != null ? target.name : userId) + " khỏi nhóm");
        send(Packet.ok(req));
        broadcastGroupChange(convId, sys);
        // [GỬI] Báo riêng cho người vừa bị xóa để Client gỡ nhóm khỏi danh sách
        sessions.sendTo(userId, new Packet(PacketType.CONV_UPDATE).put("convId", convId).put("removed", true));
    }

    /** [XỬ LÝ] [GỬI] Xử lý GROUP_LEAVE: rời nhóm (tự chuyển quyền OWNER nếu cần) và phát tin hệ thống cho những thành viên còn lại. */
    private void groupLeave(Packet req) throws Exception {
        long convId = req.num("convId");
        if (!convs.leaveGroup(convId, me.id)) {
            send(Packet.error(req, "Không thể rời hội thoại này"));
            return;
        }
        List<Long> remaining = convs.getMemberIds(convId);
        if (!remaining.isEmpty()) {
            Message sys = saveSystemMsg(convId, me.name + " đã rời khỏi nhóm");
            broadcastGroupChange(convId, sys);
        }
        send(Packet.ok(req));
    }

    /** [XỬ LÝ] [GỬI] Xử lý GROUP_RENAME: kiểm tra quyền OWNER, đổi tên nhóm trong DB và broadcast cập nhật tới cả nhóm. */
    private void groupRename(Packet req) throws Exception {
        long convId = req.num("convId");
        String name = trim(req.str("name"));
        if (name == null || name.isEmpty() || name.length() > 80) {
            send(Packet.error(req, "Tên nhóm phải từ 1 đến 80 ký tự"));
            return;
        }
        if (!convs.isOwner(convId, me.id)) {
            send(Packet.error(req, "Chỉ trưởng nhóm mới có quyền đổi tên nhóm"));
            return;
        }
        convs.renameGroup(convId, name);
        Message sys = saveSystemMsg(convId, me.name + " đã đổi tên nhóm thành " + name);
        send(Packet.ok(req));
        broadcastGroupChange(convId, sys);
    }

    // --- Tin nhắn & trạng thái ---

    /**
     * [XỬ LÝ] [GỬI] Xử lý MSG_SEND:
     * Kiểm tra quyền thành viên + kiểm tra chặn 2 chiều (chat 1-1) + kiểm tra loại/nội dung tin ->
     * Lưu tin vào MessageDao -> xác định trạng thái DELIVERED/SENT -> trả RESPONSE cho người gửi và đẩy MSG_NEW tới các thành viên khác.
     */
    private void msgSend(Packet req) throws Exception {
        long convId = req.num("convId");
        Conversation conv = convs.findForUser(convId, me.id, sessions);
        if (conv == null) {
            send(Packet.error(req, "Bạn không thuộc hội thoại này"));
            return;
        }
        // [XỬ LÝ] Chặn gửi tin nhắn 1-1 nếu một trong hai bên đã block nhau
        if (!conv.isGroup && !conv.isCloud && conv.peerId > 0 && friends.isBlockedEitherWay(me.id, conv.peerId)) {
            send(Packet.error(req, "Không thể nhắn tin do một bên đã chặn"));
            return;
        }
        String kind = trim(req.str("kind"));
        if (kind == null || kind.isEmpty()) kind = Message.KIND_TEXT;
        if (!CLIENT_MSG_KINDS.contains(kind)) {
            send(Packet.error(req, "Loại tin nhắn không hợp lệ"));
            return;
        }
        String content = req.str("content");
        if (Message.KIND_TEXT.equals(kind) && (content == null || content.isBlank())) {
            send(Packet.error(req, "Nội dung tin nhắn không được trống"));
            return;
        }

        Message m = new Message();
        m.convId = convId;
        m.senderId = me.id;
        m.senderName = me.name;
        m.kind = kind;
        m.content = content;
        m.filePath = req.str("filePath");
        m.fileName = req.str("fileName");
        m.fileSize = req.num("fileSize");
        m.durationSec = (int) req.num("durationSec");
        long replyTo = req.num("replyTo");
        m.replyTo = replyTo > 0 ? replyTo : null;
        m.forwarded = req.bool("forwarded");
        String transport = trim(req.str("transport"));
        m.transport = (transport == null || transport.isEmpty()) ? "SERVER" : transport;

        // [XỬ LÝ] Lưu bền vững vào PostgreSQL và tính deliveryState (DELIVERED nếu có người nhận online, ngược lại SENT)
        messages.save(m);
        List<Long> memberIds = convs.getMemberIds(convId);
        boolean anyOtherOnline = memberIds.stream().anyMatch(id -> id != me.id && sessions.isOnline(id));
        m.deliveryState = anyOtherOnline ? Message.STATE_DELIVERED : Message.STATE_SENT;
        convs.updateLastRead(convId, me.id, m.id);

        // [GỬI] Trả RESPONSE kèm đối tượng Message (đã có id, createdAt) về cho người gửi
        send(Packet.ok(req).put("message", m));
        // [GỬI] Đẩy sự kiện MSG_NEW realtime tới tất cả các thành viên khác đang online trong hội thoại
        sessions.sendToOthers(memberIds, me.id, new Packet(PacketType.MSG_NEW).put("message", m));
    }

    /** [XỬ LÝ] [GỬI] Xử lý MSG_HISTORY: kiểm tra quyền thành viên, phân trang lịch sử tin nhắn (kỹ thuật lấy limit+1 để biết hasMore). */
    private void msgHistory(Packet req) throws Exception {
        long convId = req.num("convId");
        if (!convs.isMember(convId, me.id)) {
            send(Packet.error(req, "Bạn không thuộc hội thoại này"));
            return;
        }
        long beforeId = req.num("beforeId");
        int limit = (int) req.num("limit");
        if (limit <= 0) limit = 30;
        if (limit > 100) limit = 100;

        List<Message> list = messages.history(convId, me.id, beforeId, limit + 1);
        boolean hasMore = list.size() > limit;
        if (hasMore) list = list.subList(1, list.size());
        send(Packet.ok(req).put("messages", list).put("hasMore", hasMore));
    }

    /** [XỬ LÝ] [GỬI] Xử lý MSG_RECALL: thu hồi tin nhắn của chính mình trong MessageDao và đẩy MSG_RECALLED tới toàn bộ hội thoại. */
    private void msgRecall(Packet req) throws Exception {
        long msgId = req.num("msgId");
        long convId = messages.recall(msgId, me.id);
        if (convId == 0) {
            send(Packet.error(req, "Không thể thu hồi tin nhắn này"));
            return;
        }
        send(Packet.ok(req));
        // [GỬI] Đẩy thông báo thu hồi tới mọi thành viên đang online
        sessions.sendToAll(convs.getMemberIds(convId),
                new Packet(PacketType.MSG_RECALLED).put("convId", convId).put("msgId", msgId));
    }

    /** [XỬ LÝ] [GỬI] Xử lý MSG_REACT: lưu/gỡ cảm xúc trong MessageDao và đẩy MSG_REACTED tới toàn bộ hội thoại. */
    private void msgReact(Packet req) throws Exception {
        long msgId = req.num("msgId");
        String emoji = req.str("emoji");
        long convId = messages.react(msgId, me.id, emoji);
        if (convId == 0) {
            send(Packet.error(req, "Không tìm thấy tin nhắn"));
            return;
        }
        send(Packet.ok(req));
        // [GỬI] Đẩy cập nhật reaction realtime tới mọi thành viên
        sessions.sendToAll(convs.getMemberIds(convId),
                new Packet(PacketType.MSG_REACTED)
                        .put("convId", convId).put("msgId", msgId).put("userId", me.id).put("emoji", emoji));
    }

    /** [NHẬN] [CSDL] [GỬI] Xử lý MSG_PIN: kiểm tra quyền thành viên, ghim/bỏ ghim tin nhắn trong ConversationDao và đẩy MSG_PINNED tới cả hội thoại. */
    private void msgPin(Packet req) throws Exception {
        long convId = req.num("convId");
        long msgId = req.num("msgId");
        if (!convs.isMember(convId, me.id)) {
            send(Packet.error(req, "Bạn không thuộc hội thoại này"));
            return;
        }
        // [CSDL] Cập nhật pinned_msg_id (msgId <= 0 sẽ đặt NULL để bỏ ghim)
        ConversationDao.PinnedInfo info = convs.pinMessage(convId, msgId > 0 ? msgId : null);
        long pinnedId = info != null ? msgId : 0;
        String senderName = info == null ? null : info.senderName();
        String preview = info == null ? null : info.preview();
        send(Packet.ok(req)
                .put("msgId", pinnedId)
                .put("senderName", senderName)
                .put("preview", preview));
        // [GỬI] Đẩy sự kiện MSG_PINNED realtime tới mọi thành viên đang online trong hội thoại
        sessions.sendToAll(convs.getMemberIds(convId),
                new Packet(PacketType.MSG_PINNED)
                        .put("convId", convId)
                        .put("msgId", pinnedId)
                        .put("senderName", senderName)
                        .put("preview", preview));
    }

    /** [XỬ LÝ] [GỬI] Xử lý MSG_SEARCH: kiểm tra quyền thành viên và tìm kiếm tin nhắn khớp từ khóa trong hội thoại. */
    private void msgSearch(Packet req) throws Exception {
        long convId = req.num("convId");
        if (!convs.isMember(convId, me.id)) {
            send(Packet.error(req, "Bạn không thuộc hội thoại này"));
            return;
        }
        send(Packet.ok(req).put("messages", messages.search(convId, me.id, req.str("query"))));
    }

    /** [XỬ LÝ] [GỬI] Xử lý TYPING: chuyển tiếp trạng thái "đang nhập..." tới các thành viên khác trong hội thoại (không lưu DB). */
    private void typing(Packet req) throws Exception {
        long convId = req.num("convId");
        if (!convs.isMember(convId, me.id)) {
            if (req.reqId != null) send(Packet.error(req, "Bạn không thuộc hội thoại này"));
            return;
        }
        // [GỬI] Đẩy sự kiện TYPING tới các thành viên khác
        sessions.sendToOthers(convs.getMemberIds(convId), me.id,
                new Packet(PacketType.TYPING)
                        .put("convId", convId).put("userId", me.id)
                        .put("userName", me.name).put("typing", req.bool("typing")));
        if (req.reqId != null) send(Packet.ok(req));
    }

    /** [XỬ LÝ] [GỬI] Xử lý MSG_SEEN: cập nhật last_read_msg_id trong DB và đẩy SEEN_UPDATE để đối phương hiển thị "Đã xem". */
    private void msgSeen(Packet req) throws Exception {
        long convId = req.num("convId");
        long lastReadMsgId = req.num("lastReadMsgId");
        if (!convs.isMember(convId, me.id)) {
            send(Packet.error(req, "Bạn không thuộc hội thoại này"));
            return;
        }
        convs.updateLastRead(convId, me.id, lastReadMsgId);
        send(Packet.ok(req));
        // [GỬI] Đẩy thông báo đã xem tới các thành viên khác trong hội thoại
        sessions.sendToOthers(convs.getMemberIds(convId), me.id,
                new Packet(PacketType.SEEN_UPDATE)
                        .put("convId", convId).put("userId", me.id).put("lastReadMsgId", lastReadMsgId));
    }

    // --- Helpers ---

    /** [XỬ LÝ] Tạo và lưu một tin nhắn hệ thống (KIND_SYSTEM) cho các sự kiện quản trị nhóm (thêm/xóa/rời/đổi tên). */
    private Message saveSystemMsg(long convId, String text) throws SQLException {
        Message sys = new Message();
        sys.convId = convId;
        sys.senderId = me.id;
        sys.senderName = me.name;
        sys.kind = Message.KIND_SYSTEM;
        sys.content = text;
        return messages.save(sys);
    }

    /** [GỬI] Phát đồng thời tin nhắn hệ thống (MSG_NEW) và trạng thái hội thoại mới nhất (CONV_UPDATE) cho mọi thành viên online. */
    private void broadcastGroupChange(long convId, Message sys) throws SQLException {
        List<Long> memberIds = convs.getMemberIds(convId);
        sessions.sendToAll(memberIds, new Packet(PacketType.MSG_NEW).put("message", sys));
        for (Long uid : memberIds) {
            if (sessions.isOnline(uid)) {
                Conversation updated = convs.findForUser(convId, uid, sessions);
                if (updated != null) {
                    sessions.sendTo(uid, new Packet(PacketType.CONV_UPDATE).put("conversation", updated));
                }
            }
        }
    }

    /** [GỬI] Lấy danh sách bạn bè của userId từ FriendDao và đẩy gói PRESENCE (online/offline) tới những người đang trực tuyến. */
    private void notifyPresence(long userId, boolean online) {
        try {
            List<Long> friendIds = friends.listFriendIds(userId);
            if (!friendIds.isEmpty()) {
                sessions.sendToAll(friendIds, new Packet(PacketType.PRESENCE).put("userId", userId).put("online", online));
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] PRESENCE: " + e);
        }
    }

    /** Tiện ích cắt khoảng trắng đầu/cuối chuỗi, giữ nguyên null nếu đầu vào là null. */
    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    /** [XỬ LÝ] Che bớt phần tên email (VD: nguyen@gmail.com -> ng***@gmail.com) trước khi trả về Client trong luồng OTP. */
    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + (at >= 0 ? email.substring(at) : "");
        int show = Math.min(2, at - 1);
        return email.substring(0, show) + "***" + email.substring(at);
    }

    /**
     * [GỬI] Điểm ghi dữ liệu duy nhất xuống socket TCP/TLS của Client này: tuần tự hóa Packet thành 1 dòng JSON + '\n' rồi flush.
     * Dùng synchronized để tránh xen kẽ byte khi nhiều luồng khác đồng thời đẩy (push) tin nhắn tới cùng một Client.
     */
    public synchronized void send(Packet p) {
        try {
            // [GỬI] Ghi chuỗi JSON kết thúc bằng ký tự xuống dòng '\n' và đẩy ngay ra kênh TCP/TLS
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
