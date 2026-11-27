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

/** Một luồng phục vụ một client: đọc từng dòng JSON, xử lý, trả lời. */
public class ClientHandler implements Runnable {
    private static final int MAX_LINE = 64 * 1024;
    private static final Pattern PHONE = Pattern.compile("0\\d{9,10}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final SecureRandom RANDOM = new SecureRandom();
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
            if (me != null) {
                long oldId = me.id;
                String oldPhone = me.phone;
                me = null;
                if (sessions.remove(oldId, this)) notifyPresence(oldId, false);
                System.out.println("[INFO] " + oldPhone + " đã thoát");
            }
        }
    }

    private void handle(Packet req) throws Exception {
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
            case PacketType.MSG_SEARCH -> msgSearch(req);
            case PacketType.TYPING -> typing(req);
            case PacketType.MSG_SEEN -> msgSeen(req);
            default -> send(Packet.error(req, "Không hỗ trợ: " + req.type));
        }
    }

    // --- Tài khoản & hồ sơ ---

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
        if (me != null && me.id != u.id && sessions.remove(me.id, this)) {
            notifyPresence(me.id, false);
        }
        me = u;
        me.online = true;
        boolean becameOnline = sessions.add(u.id, this);
        System.out.println("[INFO] " + u.phone + " đăng nhập từ " + socket.getRemoteSocketAddress());
        send(Packet.ok(req).put("user", u));
        if (becameOnline) notifyPresence(u.id, true);
    }

    private void logout(Packet req) {
        long oldId = me.id;
        me = null;
        if (sessions.remove(oldId, this)) notifyPresence(oldId, false);
        send(Packet.ok(req));
    }

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
            send(Packet.ok(req).put("user", updated));
        }
    }

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
            send(Packet.ok(req));
        }
    }

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
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        otps.save(u.email, code, 5);
        mailOtp.sendOtp(u.email, code);
        send(Packet.ok(req)
                .put("maskedEmail", maskEmail(u.email))
                .put("consoleMode", mailOtp.isConsoleMode()));
    }

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
        if (!otps.verifyAndConsume(u.email, code)) {
            send(Packet.error(req, "Mã OTP không đúng hoặc đã hết hạn"));
            return;
        }
        if (!users.resetPasswordByEmail(u.email, newPass)) {
            send(Packet.error(req, "Không thể đặt lại mật khẩu"));
            return;
        }
        send(Packet.ok(req));
    }

    // --- P2P ---

    private void p2pRegister(Packet req) {
        int port = (int) req.num("port");
        if (port < 1 || port > 65535) {
            send(Packet.error(req, "Cổng P2P không hợp lệ"));
            return;
        }
        String ip = socket.getInetAddress().getHostAddress();
        sessions.registerP2p(me.id, ip, port);
        send(Packet.ok(req).put("ip", ip).put("port", port));
    }

    private void p2pLookup(Packet req) {
        long userId = req.num("userId");
        boolean online = sessions.isOnline(userId);
        SessionRegistry.P2pEndpoint ep = sessions.getP2p(userId);
        send(Packet.ok(req)
                .put("online", online)
                .put("ip", ep == null ? null : ep.ip())
                .put("port", ep == null ? 0 : ep.port()));
    }

    // --- Bạn bè ---

    private void userSearch(Packet req) throws Exception {
        List<User> list = users.search(req.str("query"), me.id);
        for (User u : list) u.online = sessions.isOnline(u.id);
        send(Packet.ok(req).put("users", list));
    }

    private void friendList(Packet req) throws Exception {
        List<User> friendList = friends.listFriends(me.id);
        for (User u : friendList) u.online = sessions.isOnline(u.id);
        send(Packet.ok(req)
                .put("friends", friendList)
                .put("incoming", friends.listIncoming(me.id))
                .put("outgoing", friends.listOutgoing(me.id)));
    }

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
        sessions.sendTo(targetId, new Packet(PacketType.FRIEND_EVENT).put("action", "REQUEST").put("user", me));
    }

    private void friendAccept(Packet req) throws Exception {
        long requesterId = req.num("userId");
        if (!friends.accept(me.id, requesterId)) {
            send(Packet.error(req, "Không tìm thấy lời mời kết bạn"));
            return;
        }
        send(Packet.ok(req));
        sessions.sendTo(requesterId, new Packet(PacketType.FRIEND_EVENT).put("action", "ACCEPTED").put("user", me));
    }

    private void friendReject(Packet req) throws Exception {
        long requesterId = req.num("userId");
        if (!friends.reject(me.id, requesterId)) {
            send(Packet.error(req, "Không tìm thấy lời mời kết bạn"));
            return;
        }
        send(Packet.ok(req));
        sessions.sendTo(requesterId, new Packet(PacketType.FRIEND_EVENT).put("action", "REJECTED").put("userId", me.id));
    }

    private void friendRemove(Packet req) throws Exception {
        long otherId = req.num("userId");
        if (!friends.remove(me.id, otherId)) {
            send(Packet.error(req, "Không tìm thấy quan hệ bạn bè"));
            return;
        }
        send(Packet.ok(req));
        sessions.sendTo(otherId, new Packet(PacketType.FRIEND_EVENT).put("action", "REMOVED").put("userId", me.id));
    }

    private void userBlock(Packet req) throws Exception {
        long targetId = req.num("userId");
        if (targetId <= 0 || targetId == me.id || users.findById(targetId) == null) {
            send(Packet.error(req, "Người dùng không hợp lệ"));
            return;
        }
        friends.block(me.id, targetId);
        send(Packet.ok(req));
    }

    private void userUnblock(Packet req) throws Exception {
        long targetId = req.num("userId");
        if (!friends.unblock(me.id, targetId)) {
            send(Packet.error(req, "Người dùng không nằm trong danh sách chặn"));
            return;
        }
        send(Packet.ok(req));
    }

    private void blockList(Packet req) throws Exception {
        send(Packet.ok(req).put("blocked", friends.listBlocked(me.id)));
    }

    // --- Báo hiệu gọi thoại 1-1 ---

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
        String callerIp = socket.getInetAddress().getHostAddress();
        sessions.sendTo(targetId, new Packet(PacketType.CALL_OFFER)
                .put("callerId", me.id)
                .put("callerName", me.name)
                .put("callerIp", callerIp)
                .put("udpPort", udpPort));
        send(Packet.ok(req));
    }

    private void callAnswer(Packet req) {
        long callerId = req.num("callerId");
        int udpPort = (int) req.num("udpPort");
        if (callerId <= 0 || udpPort < 1 || udpPort > 65535) {
            send(Packet.error(req, "Thông tin trả lời cuộc gọi không hợp lệ"));
            return;
        }
        String calleeIp = socket.getInetAddress().getHostAddress();
        sessions.sendTo(callerId, new Packet(PacketType.CALL_ANSWER)
                .put("calleeId", me.id)
                .put("calleeIp", calleeIp)
                .put("udpPort", udpPort));
        send(Packet.ok(req));
    }

    private void callReject(Packet req) {
        long callerId = req.num("callerId");
        String reason = req.str("reason");
        sessions.sendTo(callerId, new Packet(PacketType.CALL_REJECT)
                .put("calleeId", me.id)
                .put("reason", reason == null ? "" : reason));
        send(Packet.ok(req));
    }

    private void callEnd(Packet req) {
        long peerId = req.num("peerId");
        sessions.sendTo(peerId, new Packet(PacketType.CALL_END).put("userId", me.id));
        send(Packet.ok(req));
    }

    // --- Hội thoại & nhóm ---

    private void convList(Packet req) throws Exception {
        send(Packet.ok(req).put("conversations", convs.listForUser(me.id, sessions)));
    }

    private void convOpenDirect(Packet req) throws Exception {
        long peerId = req.num("peerId");
        if (peerId <= 0 || peerId == me.id || users.findById(peerId) == null) {
            send(Packet.error(req, "Người dùng không hợp lệ"));
            return;
        }
        Conversation conv = convs.openOrCreateDirect(me.id, peerId, sessions);
        send(Packet.ok(req).put("conversation", conv));
    }

    private void convPin(Packet req) throws Exception {
        long convId = req.num("convId");
        if (!convs.setPinned(convId, me.id, req.bool("pinned"))) {
            send(Packet.error(req, "Bạn không thuộc hội thoại này"));
            return;
        }
        send(Packet.ok(req));
    }

    private void groupCreate(Packet req) throws Exception {
        String name = trim(req.str("name"));
        if (name == null || name.isEmpty() || name.length() > 80) {
            send(Packet.error(req, "Tên nhóm phải từ 1 đến 80 ký tự"));
            return;
        }
        List<Long> memberIds = req.getList("memberIds", Long.class);
        Conversation conv = convs.createGroup(me.id, name, memberIds, sessions);
        send(Packet.ok(req).put("conversation", conv));
        sessions.sendToOthers(convs.getMemberIds(conv.id), me.id,
                new Packet(PacketType.CONV_UPDATE).put("conversation", conv));
    }

    private void groupMembers(Packet req) throws Exception {
        long convId = req.num("convId");
        if (!convs.isMember(convId, me.id)) {
            send(Packet.error(req, "Bạn không thuộc hội thoại này"));
            return;
        }
        send(Packet.ok(req).put("members", convs.getMembers(convId, sessions)));
    }

    private void groupAddMember(Packet req) throws Exception {
        long convId = req.num("convId");
        long userId = req.num("userId");
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
        broadcastGroupChange(convId, sys);
    }

    private void groupRemoveMember(Packet req) throws Exception {
        long convId = req.num("convId");
        long userId = req.num("userId");
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
        sessions.sendTo(userId, new Packet(PacketType.CONV_UPDATE).put("convId", convId).put("removed", true));
    }

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

    private void msgSend(Packet req) throws Exception {
        long convId = req.num("convId");
        Conversation conv = convs.findForUser(convId, me.id, sessions);
        if (conv == null) {
            send(Packet.error(req, "Bạn không thuộc hội thoại này"));
            return;
        }
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

        messages.save(m);
        List<Long> memberIds = convs.getMemberIds(convId);
        boolean anyOtherOnline = memberIds.stream().anyMatch(id -> id != me.id && sessions.isOnline(id));
        m.deliveryState = anyOtherOnline ? Message.STATE_DELIVERED : Message.STATE_SENT;
        convs.updateLastRead(convId, me.id, m.id);

        send(Packet.ok(req).put("message", m));
        sessions.sendToOthers(memberIds, me.id, new Packet(PacketType.MSG_NEW).put("message", m));
    }

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

    private void msgRecall(Packet req) throws Exception {
        long msgId = req.num("msgId");
        long convId = messages.recall(msgId, me.id);
        if (convId == 0) {
            send(Packet.error(req, "Không thể thu hồi tin nhắn này"));
            return;
        }
        send(Packet.ok(req));
        sessions.sendToAll(convs.getMemberIds(convId),
                new Packet(PacketType.MSG_RECALLED).put("convId", convId).put("msgId", msgId));
    }

    private void msgReact(Packet req) throws Exception {
        long msgId = req.num("msgId");
        String emoji = req.str("emoji");
        long convId = messages.react(msgId, me.id, emoji);
        if (convId == 0) {
            send(Packet.error(req, "Không tìm thấy tin nhắn"));
            return;
        }
        send(Packet.ok(req));
        sessions.sendToAll(convs.getMemberIds(convId),
                new Packet(PacketType.MSG_REACTED)
                        .put("convId", convId).put("msgId", msgId).put("userId", me.id).put("emoji", emoji));
    }

    private void msgSearch(Packet req) throws Exception {
        long convId = req.num("convId");
        if (!convs.isMember(convId, me.id)) {
            send(Packet.error(req, "Bạn không thuộc hội thoại này"));
            return;
        }
        send(Packet.ok(req).put("messages", messages.search(convId, me.id, req.str("query"))));
    }

    private void typing(Packet req) throws Exception {
        long convId = req.num("convId");
        if (!convs.isMember(convId, me.id)) {
            if (req.reqId != null) send(Packet.error(req, "Bạn không thuộc hội thoại này"));
            return;
        }
        sessions.sendToOthers(convs.getMemberIds(convId), me.id,
                new Packet(PacketType.TYPING)
                        .put("convId", convId).put("userId", me.id)
                        .put("userName", me.name).put("typing", req.bool("typing")));
        if (req.reqId != null) send(Packet.ok(req));
    }

    private void msgSeen(Packet req) throws Exception {
        long convId = req.num("convId");
        long lastReadMsgId = req.num("lastReadMsgId");
        if (!convs.isMember(convId, me.id)) {
            send(Packet.error(req, "Bạn không thuộc hội thoại này"));
            return;
        }
        convs.updateLastRead(convId, me.id, lastReadMsgId);
        send(Packet.ok(req));
        sessions.sendToOthers(convs.getMemberIds(convId), me.id,
                new Packet(PacketType.SEEN_UPDATE)
                        .put("convId", convId).put("userId", me.id).put("lastReadMsgId", lastReadMsgId));
    }

    // --- Helpers ---

    private Message saveSystemMsg(long convId, String text) throws SQLException {
        Message sys = new Message();
        sys.convId = convId;
        sys.senderId = me.id;
        sys.senderName = me.name;
        sys.kind = Message.KIND_SYSTEM;
        sys.content = text;
        return messages.save(sys);
    }

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

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + (at >= 0 ? email.substring(at) : "");
        int show = Math.min(2, at - 1);
        return email.substring(0, show) + "***" + email.substring(at);
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
