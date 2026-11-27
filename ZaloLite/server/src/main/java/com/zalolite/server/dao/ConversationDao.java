package com.zalolite.server.dao;

import com.zalolite.common.Conversation;
import com.zalolite.common.Member;
import com.zalolite.common.Message;
import com.zalolite.common.User;
import com.zalolite.server.Db;
import com.zalolite.server.SessionRegistry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ConversationDao {
    public static final String CLOUD_NAME = "☁️ Cloud của tôi";

    private static final String CONV_SELECT =
            "SELECT c.id, c.is_group, c.name, COALESCE(c.owner_id, 0) AS owner_id, c.avatar, m.pinned, "
                    + "EXTRACT(EPOCH FROM c.created_at) * 1000 AS conv_time, "
                    + "(SELECT COUNT(*) FROM members mc WHERE mc.conv_id = c.id) AS member_count, "
                    + "pu.id AS peer_id, pu.name AS peer_name, pu.avatar AS peer_avatar, "
                    + "lm.kind AS last_kind, lm.content AS last_content, lm.file_name AS last_file_name, "
                    + "lm.recalled AS last_recalled, lm.ts AS last_ts, "
                    + "(SELECT COUNT(*) FROM messages um WHERE um.conv_id = c.id AND um.id > m.last_read_msg_id "
                    + "  AND COALESCE(um.sender_id, -1) <> m.user_id AND um.created_at >= m.joined_at) AS unread_count "
                    + "FROM members m "
                    + "JOIN conversations c ON c.id = m.conv_id "
                    + "LEFT JOIN members pm ON NOT c.is_group AND pm.conv_id = c.id AND pm.user_id <> m.user_id "
                    + "LEFT JOIN users pu ON pu.id = pm.user_id "
                    + "LEFT JOIN LATERAL ("
                    + "  SELECT kind, content, file_name, recalled, EXTRACT(EPOCH FROM created_at) * 1000 AS ts "
                    + "  FROM messages WHERE conv_id = c.id AND created_at >= m.joined_at ORDER BY id DESC LIMIT 1"
                    + ") lm ON true ";

    private final Db db;

    public ConversationDao(Db db) {
        this.db = db;
    }

    public Conversation openOrCreateDirect(long u1, long u2, SessionRegistry sessions) throws SQLException {
        String find = "SELECT c.id FROM conversations c "
                + "JOIN members m1 ON m1.conv_id = c.id AND m1.user_id = ? "
                + "JOIN members m2 ON m2.conv_id = c.id AND m2.user_id = ? "
                + "WHERE c.is_group = false LIMIT 1";
        long convId = 0;
        try (Connection c = db.open()) {
            try (PreparedStatement ps = c.prepareStatement(find)) {
                ps.setLong(1, u1);
                ps.setLong(2, u2);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) convId = rs.getLong(1);
                }
            }
            if (convId == 0) {
                c.setAutoCommit(false);
                try {
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO conversations(is_group) VALUES (false) RETURNING id");
                         ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        convId = rs.getLong(1);
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO members(conv_id, user_id, role, joined_at) VALUES (?, ?, 'MEMBER', clock_timestamp())")) {
                        ps.setLong(1, convId);
                        ps.setLong(2, u1);
                        ps.addBatch();
                        ps.setLong(1, convId);
                        ps.setLong(2, u2);
                        ps.addBatch();
                        ps.executeBatch();
                    }
                    c.commit();
                } catch (SQLException e) {
                    c.rollback();
                    throw e;
                }
            }
        }
        return findForUser(convId, u1, sessions);
    }

    public Conversation createGroup(long ownerId, String name, List<Long> memberIds, SessionRegistry sessions) throws SQLException {
        Set<Long> others = new LinkedHashSet<>();
        if (memberIds != null) {
            for (Long id : memberIds) {
                if (id != null && id > 0 && id != ownerId) others.add(id);
            }
        }
        if (others.size() < 2) {
            throw new IllegalArgumentException("Nhóm phải có ít nhất 3 thành viên (gồm cả trưởng nhóm)");
        }
        long convId;
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO conversations(is_group, name, owner_id) VALUES (true, ?, ?) RETURNING id")) {
                    ps.setString(1, name);
                    ps.setLong(2, ownerId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        convId = rs.getLong(1);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO members(conv_id, user_id, role, joined_at) VALUES (?, ?, ?, clock_timestamp())")) {
                    ps.setLong(1, convId);
                    ps.setLong(2, ownerId);
                    ps.setString(3, "OWNER");
                    ps.addBatch();
                    for (Long uid : others) {
                        ps.setLong(1, convId);
                        ps.setLong(2, uid);
                        ps.setString(3, "MEMBER");
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO messages(conv_id, sender_id, kind, content, created_at) VALUES (?, ?, ?, ?, clock_timestamp())")) {
                    ps.setLong(1, convId);
                    ps.setLong(2, ownerId);
                    ps.setString(3, Message.KIND_SYSTEM);
                    ps.setString(4, "Đã tạo nhóm " + name);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
        return findForUser(convId, ownerId, sessions);
    }

    public Conversation findForUser(long convId, long userId, SessionRegistry sessions) throws SQLException {
        String sql = CONV_SELECT + "WHERE m.conv_id = ? AND m.user_id = ?";
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, convId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapConv(rs, sessions) : null;
            }
        }
    }

    public long ensureCloudConversation(long userId) throws SQLException {
        String find = "SELECT c.id FROM conversations c "
                + "JOIN members m ON m.conv_id = c.id AND m.user_id = ? "
                + "WHERE c.is_group = false AND c.owner_id = ? AND c.name = ? LIMIT 1";
        try (Connection c = db.open()) {
            try (PreparedStatement ps = c.prepareStatement(find)) {
                ps.setLong(1, userId);
                ps.setLong(2, userId);
                ps.setString(3, CLOUD_NAME);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getLong(1);
                }
            }
            c.setAutoCommit(false);
            try {
                long convId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO conversations(is_group, name, owner_id) VALUES (false, ?, ?) RETURNING id")) {
                    ps.setString(1, CLOUD_NAME);
                    ps.setLong(2, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        convId = rs.getLong(1);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO members(conv_id, user_id, role, pinned, joined_at) VALUES (?, ?, 'OWNER', true, clock_timestamp())")) {
                    ps.setLong(1, convId);
                    ps.setLong(2, userId);
                    ps.executeUpdate();
                }
                c.commit();
                return convId;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public List<Conversation> listForUser(long userId, SessionRegistry sessions) throws SQLException {
        ensureCloudConversation(userId);
        String sql = CONV_SELECT
                + "WHERE m.user_id = ? ORDER BY m.pinned DESC, COALESCE(lm.ts, EXTRACT(EPOCH FROM c.created_at) * 1000) DESC";
        List<Conversation> list = new ArrayList<>();
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapConv(rs, sessions));
            }
        }
        return list;
    }

    public List<Long> getMemberIds(long convId) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement("SELECT user_id FROM members WHERE conv_id = ?")) {
            ps.setLong(1, convId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    public List<Member> getMembers(long convId, SessionRegistry sessions) throws SQLException {
        String sql = "SELECT u.id, u.phone, u.name, u.email, u.avatar, m.role, m.last_read_msg_id "
                + "FROM members m JOIN users u ON u.id = m.user_id WHERE m.conv_id = ? "
                + "ORDER BY CASE WHEN m.role = 'OWNER' THEN 0 ELSE 1 END, m.joined_at ASC";
        List<Member> list = new ArrayList<>();
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, convId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    User u = UserDao.map(rs);
                    u.online = sessions != null && sessions.isOnline(u.id);
                    list.add(new Member(u, rs.getString("role"), rs.getLong("last_read_msg_id")));
                }
            }
        }
        return list;
    }

    public boolean isMember(long convId, long userId) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM members WHERE conv_id = ? AND user_id = ?")) {
            ps.setLong(1, convId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean isOwner(long convId, long userId) throws SQLException {
        String sql = "SELECT 1 FROM members m JOIN conversations c ON c.id = m.conv_id "
                + "WHERE m.conv_id = ? AND m.user_id = ? AND m.role = 'OWNER' AND c.is_group = true";
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, convId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean addMember(long convId, long userId) throws SQLException {
        String sql = "INSERT INTO members(conv_id, user_id, role, joined_at) VALUES (?, ?, 'MEMBER', clock_timestamp()) "
                + "ON CONFLICT (conv_id, user_id) DO NOTHING";
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, convId);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean removeMember(long convId, long userId) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement("DELETE FROM members WHERE conv_id = ? AND user_id = ?")) {
            ps.setLong(1, convId);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean leaveGroup(long convId, long userId) throws SQLException {
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                String role = null;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT m.role FROM members m JOIN conversations c ON c.id = m.conv_id "
                                + "WHERE m.conv_id = ? AND m.user_id = ? AND c.is_group = true")) {
                    ps.setLong(1, convId);
                    ps.setLong(2, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) role = rs.getString(1);
                    }
                }
                if (role == null) {
                    c.rollback();
                    return false;
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM members WHERE conv_id = ? AND user_id = ?")) {
                    ps.setLong(1, convId);
                    ps.setLong(2, userId);
                    ps.executeUpdate();
                }
                if ("OWNER".equals(role)) {
                    long nextOwner = 0;
                    try (PreparedStatement ps = c.prepareStatement(
                            "SELECT user_id FROM members WHERE conv_id = ? ORDER BY joined_at ASC, user_id ASC LIMIT 1")) {
                        ps.setLong(1, convId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) nextOwner = rs.getLong(1);
                        }
                    }
                    if (nextOwner > 0) {
                        try (PreparedStatement ps = c.prepareStatement(
                                "UPDATE members SET role = 'OWNER' WHERE conv_id = ? AND user_id = ?")) {
                            ps.setLong(1, convId);
                            ps.setLong(2, nextOwner);
                            ps.executeUpdate();
                        }
                        try (PreparedStatement ps = c.prepareStatement(
                                "UPDATE conversations SET owner_id = ? WHERE id = ?")) {
                            ps.setLong(1, nextOwner);
                            ps.setLong(2, convId);
                            ps.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement ps = c.prepareStatement("DELETE FROM conversations WHERE id = ?")) {
                            ps.setLong(1, convId);
                            ps.executeUpdate();
                        }
                    }
                }
                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public boolean renameGroup(long convId, String newName) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE conversations SET name = ? WHERE id = ? AND is_group = true")) {
            ps.setString(1, newName);
            ps.setLong(2, convId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean setPinned(long convId, long userId, boolean pinned) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE members SET pinned = ? WHERE conv_id = ? AND user_id = ?")) {
            ps.setBoolean(1, pinned);
            ps.setLong(2, convId);
            ps.setLong(3, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public void updateLastRead(long convId, long userId, long msgId) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE members SET last_read_msg_id = GREATEST(last_read_msg_id, ?) WHERE conv_id = ? AND user_id = ?")) {
            ps.setLong(1, msgId);
            ps.setLong(2, convId);
            ps.setLong(3, userId);
            ps.executeUpdate();
        }
    }

    private static Conversation mapConv(ResultSet rs, SessionRegistry sessions) throws SQLException {
        Conversation conv = new Conversation();
        conv.id = rs.getLong("id");
        conv.isGroup = rs.getBoolean("is_group");
        conv.ownerId = rs.getLong("owner_id");
        conv.pinned = rs.getBoolean("pinned");
        conv.unreadCount = rs.getInt("unread_count");
        conv.memberCount = rs.getInt("member_count");

        String convName = rs.getString("name");
        conv.isCloud = !conv.isGroup && (CLOUD_NAME.equals(convName) || conv.memberCount == 1);

        if (conv.isGroup) {
            conv.name = convName;
            conv.avatar = rs.getString("avatar");
        } else if (conv.isCloud) {
            conv.name = convName != null ? convName : CLOUD_NAME;
            conv.avatar = rs.getString("avatar");
            conv.peerId = 0;
            conv.peerOnline = false;
        } else {
            conv.peerId = rs.getLong("peer_id");
            conv.name = rs.getString("peer_name");
            conv.avatar = rs.getString("peer_avatar");
            conv.peerOnline = conv.peerId > 0 && sessions != null && sessions.isOnline(conv.peerId);
        }

        double lastTs = rs.getDouble("last_ts");
        if (rs.wasNull()) {
            conv.lastTime = Math.round(rs.getDouble("conv_time"));
            conv.lastMessage = "";
        } else {
            conv.lastTime = Math.round(lastTs);
            if (rs.getBoolean("last_recalled")) {
                conv.lastMessage = "Tin nhắn đã được thu hồi";
            } else {
                String content = rs.getString("last_content");
                String kind = rs.getString("last_kind");
                String fileName = rs.getString("last_file_name");
                conv.lastMessage = (content != null && !content.isEmpty()) ? content : summarizeKind(kind, fileName);
            }
        }
        return conv;
    }

    private static String summarizeKind(String kind, String fileName) {
        if (kind == null) return "";
        return switch (kind) {
            case Message.KIND_IMAGE -> "[Hình ảnh]";
            case Message.KIND_VIDEO -> "[Video]";
            case Message.KIND_AUDIO -> "[Tin nhắn thoại]";
            case Message.KIND_FILE -> fileName == null || fileName.isEmpty() ? "[Tệp]" : "[Tệp] " + fileName;
            default -> "";
        };
    }
}
