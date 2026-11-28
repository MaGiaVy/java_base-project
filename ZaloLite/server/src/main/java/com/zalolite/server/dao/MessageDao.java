package com.zalolite.server.dao;

import com.zalolite.common.Message;
import com.zalolite.server.Db;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * [CSDL] Lớp DAO thao tác với hai bảng {@code messages} và {@code reactions} trong PostgreSQL.
 * Đảm nhận lưu tin nhắn mới (kèm thông tin tin được reply), phân trang lịch sử tin nhắn (lọc theo thời điểm joined_at),
 * thu hồi tin nhắn (recall), thả/gỡ biểu tượng cảm xúc (reaction) và tìm kiếm tin nhắn theo từ khóa.
 */
public class MessageDao {
    private final Db db;

    /** Khởi tạo MessageDao với nguồn kết nối Db. */
    public MessageDao(Db db) {
        this.db = db;
    }

    /** [CSDL] Lưu tin nhắn mới vào bảng messages (RETURNING id, created_at), đồng thời nạp thêm tên người gửi và bản xem trước tin được reply (nếu có). */
    public Message save(Message m) throws SQLException {
        String sql = "INSERT INTO messages(conv_id, sender_id, kind, content, file_path, file_name, file_size, duration_sec, reply_to, forwarded, created_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,clock_timestamp()) RETURNING id, EXTRACT(EPOCH FROM created_at) * 1000";
        try (Connection c = db.open()) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, m.convId);
                if (m.senderId > 0) ps.setLong(2, m.senderId);
                else ps.setNull(2, Types.BIGINT);
                ps.setString(3, m.kind);
                ps.setString(4, m.content);
                ps.setString(5, m.filePath);
                ps.setString(6, m.fileName);
                ps.setLong(7, m.fileSize);
                ps.setInt(8, m.durationSec);
                if (m.replyTo != null && m.replyTo > 0) ps.setLong(9, m.replyTo);
                else ps.setNull(9, Types.BIGINT);
                ps.setBoolean(10, m.forwarded);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    m.id = rs.getLong(1);
                    m.createdAt = Math.round(rs.getDouble(2));
                }
            }
            if (m.senderName == null && m.senderId > 0) {
                try (PreparedStatement ps = c.prepareStatement("SELECT name FROM users WHERE id = ?")) {
                    ps.setLong(1, m.senderId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) m.senderName = rs.getString(1);
                    }
                }
            }
            if (m.replyTo != null && m.replyTo > 0) {
                String rSql = "SELECT ru.name, r.kind, r.content, r.file_name, r.recalled "
                        + "FROM messages r LEFT JOIN users ru ON ru.id = r.sender_id WHERE r.id = ?";
                try (PreparedStatement ps = c.prepareStatement(rSql)) {
                    ps.setLong(1, m.replyTo);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            m.replySender = rs.getString(1);
                            m.replyPreview = formatReplyPreview(rs.getBoolean(5), rs.getString(2), rs.getString(3), rs.getString(4));
                        }
                    }
                }
            }
        }
        return m;
    }

    /**
     * [CSDL] Tải lịch sử tin nhắn theo phân trang ngược (cursor beforeId, lấy mới nhất trước rồi đảo ngược về thứ tự thời gian tăng dần).
     * Chỉ trả các tin có created_at >= vm.joined_at (thành viên mới vào nhóm không xem được tin cũ), kèm số người đã xem và toàn bộ reactions.
     */
    public List<Message> history(long convId, long viewerId, long beforeId, int limit) throws SQLException {
        String sql = "SELECT m.id, m.conv_id, COALESCE(m.sender_id, 0) AS sender_id, u.name AS sender_name, "
                + "m.kind, m.content, m.file_path, m.file_name, m.file_size, m.duration_sec, m.reply_to, m.forwarded, m.recalled, "
                + "ru.name AS reply_sender, r.kind AS reply_kind, r.content AS reply_content, "
                + "r.file_name AS reply_file_name, r.recalled AS reply_recalled, "
                + "EXTRACT(EPOCH FROM m.created_at) * 1000 AS ts, "
                + "(SELECT COUNT(*) FROM members om WHERE om.conv_id = m.conv_id "
                + "  AND om.user_id <> COALESCE(m.sender_id, -1) AND om.last_read_msg_id >= m.id) AS seen_count "
                + "FROM messages m "
                + "JOIN members vm ON vm.conv_id = m.conv_id AND vm.user_id = ? "
                + "LEFT JOIN users u ON u.id = m.sender_id "
                + "LEFT JOIN messages r ON r.id = m.reply_to "
                + "LEFT JOIN users ru ON ru.id = r.sender_id "
                + "WHERE m.conv_id = ? AND m.created_at >= vm.joined_at "
                + (beforeId > 0 ? "AND m.id < ? " : "")
                + "ORDER BY m.id DESC LIMIT ?";
        List<Message> list = new ArrayList<>();
        try (Connection c = db.open()) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                int idx = 1;
                ps.setLong(idx++, viewerId);
                ps.setLong(idx++, convId);
                if (beforeId > 0) ps.setLong(idx++, beforeId);
                ps.setInt(idx, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapMsg(rs));
                }
            }
            Collections.reverse(list);
            attachReactions(c, list);
        }
        return list;
    }

    /** [CSDL] Thu hồi tin nhắn: chỉ cho phép chính người gửi (sender_id = ?) thu hồi tin chưa thu hồi, xóa nội dung và trả về conv_id (0 nếu thất bại). */
    public long recall(long msgId, long senderId) throws SQLException {
        String sql = "UPDATE messages SET recalled = true, content = NULL "
                + "WHERE id = ? AND sender_id = ? AND recalled = false RETURNING conv_id";
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, msgId);
            ps.setLong(2, senderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    /** [CSDL] Thả hoặc gỡ cảm xúc (emoji) trên tin nhắn sau khi kiểm tra userId thuộc hội thoại; emoji rỗng sẽ DELETE, có emoji sẽ UPSERT. */
    public long react(long msgId, long userId, String emoji) throws SQLException {
        String findConv = "SELECT m.conv_id FROM messages m "
                + "JOIN members mem ON mem.conv_id = m.conv_id AND mem.user_id = ? WHERE m.id = ?";
        try (Connection c = db.open()) {
            long convId;
            try (PreparedStatement ps = c.prepareStatement(findConv)) {
                ps.setLong(1, userId);
                ps.setLong(2, msgId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return 0;
                    convId = rs.getLong(1);
                }
            }
            if (emoji == null || emoji.isBlank()) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM reactions WHERE msg_id = ? AND user_id = ?")) {
                    ps.setLong(1, msgId);
                    ps.setLong(2, userId);
                    ps.executeUpdate();
                }
            } else {
                String upsert = "INSERT INTO reactions(msg_id, user_id, emoji) VALUES (?,?,?) "
                        + "ON CONFLICT (msg_id, user_id) DO UPDATE SET emoji = EXCLUDED.emoji";
                try (PreparedStatement ps = c.prepareStatement(upsert)) {
                    ps.setLong(1, msgId);
                    ps.setLong(2, userId);
                    ps.setString(3, emoji.trim());
                    ps.executeUpdate();
                }
            }
            return convId;
        }
    }

    /** [CSDL] Tìm tối đa 50 tin nhắn chưa thu hồi trong hội thoại có nội dung khớp từ khóa (ILIKE), chỉ trong phạm vi sau khi viewerId vào nhóm. */
    public List<Message> search(long convId, long viewerId, String query) throws SQLException {
        if (query == null || query.isBlank()) return List.of();
        String sql = "SELECT m.id, m.conv_id, COALESCE(m.sender_id, 0) AS sender_id, u.name AS sender_name, "
                + "m.kind, m.content, m.file_path, m.file_name, m.file_size, m.duration_sec, m.reply_to, m.forwarded, m.recalled, "
                + "ru.name AS reply_sender, r.kind AS reply_kind, r.content AS reply_content, "
                + "r.file_name AS reply_file_name, r.recalled AS reply_recalled, "
                + "EXTRACT(EPOCH FROM m.created_at) * 1000 AS ts, 0 AS seen_count "
                + "FROM messages m "
                + "JOIN members vm ON vm.conv_id = m.conv_id AND vm.user_id = ? "
                + "LEFT JOIN users u ON u.id = m.sender_id "
                + "LEFT JOIN messages r ON r.id = m.reply_to "
                + "LEFT JOIN users ru ON ru.id = r.sender_id "
                + "WHERE m.conv_id = ? AND m.recalled = false AND m.created_at >= vm.joined_at "
                + "AND m.content ILIKE ? ORDER BY m.id DESC LIMIT 50";
        List<Message> list = new ArrayList<>();
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, viewerId);
            ps.setLong(2, convId);
            ps.setString(3, "%" + query.trim() + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapMsg(rs));
            }
        }
        return list;
    }

    /** Ánh xạ 1 dòng ResultSet thành DTO Message (bao gồm cả thông tin reply và trạng thái SEEN/SENT). */
    private static Message mapMsg(ResultSet rs) throws SQLException {
        Message m = new Message();
        m.id = rs.getLong("id");
        m.convId = rs.getLong("conv_id");
        m.senderId = rs.getLong("sender_id");
        m.senderName = rs.getString("sender_name");
        m.kind = rs.getString("kind");
        m.content = rs.getString("content");
        m.filePath = rs.getString("file_path");
        m.fileName = rs.getString("file_name");
        m.fileSize = rs.getLong("file_size");
        m.durationSec = rs.getInt("duration_sec");
        long reply = rs.getLong("reply_to");
        m.replyTo = rs.wasNull() ? null : reply;
        m.forwarded = rs.getBoolean("forwarded");
        if (m.replyTo != null) {
            m.replySender = rs.getString("reply_sender");
            m.replyPreview = formatReplyPreview(
                    rs.getBoolean("reply_recalled"),
                    rs.getString("reply_kind"),
                    rs.getString("reply_content"),
                    rs.getString("reply_file_name"));
        }
        m.recalled = rs.getBoolean("recalled");
        m.createdAt = Math.round(rs.getDouble("ts"));
        m.seenCount = rs.getInt("seen_count");
        m.deliveryState = m.seenCount > 0 ? Message.STATE_SEEN : Message.STATE_SENT;
        return m;
    }

    /** Tạo đoạn xem trước ngắn (tối đa 80 ký tự) cho khung trích dẫn tin nhắn được trả lời (reply). */
    private static String formatReplyPreview(boolean recalled, String kind, String content, String fileName) {
        if (recalled) return "Tin nhắn đã được thu hồi";
        String text = (content != null && !content.isBlank()) ? content
                : (fileName != null && !fileName.isBlank()) ? fileName
                : switch (kind == null ? "" : kind) {
                    case Message.KIND_IMAGE -> "[Hình ảnh]";
                    case Message.KIND_VIDEO -> "[Video]";
                    case Message.KIND_AUDIO -> "[Tin nhắn thoại]";
                    case Message.KIND_FILE -> "[Tệp]";
                    default -> "";
                };
        return text.length() > 80 ? text.substring(0, 80) : text;
    }

    /** [CSDL] Nạp hàng loạt reactions cho cả danh sách tin nhắn chỉ bằng 1 câu SQL `WHERE msg_id = ANY(?)` (tránh lỗi N+1 query). */
    private static void attachReactions(Connection c, List<Message> list) throws SQLException {
        if (list.isEmpty()) return;
        Map<Long, Message> byId = new HashMap<>(list.size());
        Long[] ids = new Long[list.size()];
        for (int i = 0; i < list.size(); i++) {
            ids[i] = list.get(i).id;
            byId.put(ids[i], list.get(i));
        }
        Array arr = c.createArrayOf("bigint", ids);
        try (PreparedStatement ps = c.prepareStatement("SELECT msg_id, user_id, emoji FROM reactions WHERE msg_id = ANY(?)")) {
            ps.setArray(1, arr);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Message m = byId.get(rs.getLong(1));
                    if (m != null) m.reactions.put(rs.getLong(2), rs.getString(3));
                }
            }
        } finally {
            arr.free();
        }
    }
}
