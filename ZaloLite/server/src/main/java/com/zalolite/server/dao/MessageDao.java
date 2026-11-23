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

public class MessageDao {
    private final Db db;

    public MessageDao(Db db) {
        this.db = db;
    }

    public Message save(Message m) throws SQLException {
        String sql = "INSERT INTO messages(conv_id, sender_id, kind, content, file_path, file_name, file_size, duration_sec, reply_to, created_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,clock_timestamp()) RETURNING id, EXTRACT(EPOCH FROM created_at) * 1000";
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
        }
        return m;
    }

    public List<Message> history(long convId, long viewerId, long beforeId, int limit) throws SQLException {
        String sql = "SELECT m.id, m.conv_id, COALESCE(m.sender_id, 0) AS sender_id, u.name AS sender_name, "
                + "m.kind, m.content, m.file_path, m.file_name, m.file_size, m.duration_sec, m.reply_to, m.recalled, "
                + "EXTRACT(EPOCH FROM m.created_at) * 1000 AS ts, "
                + "(SELECT COUNT(*) FROM members om WHERE om.conv_id = m.conv_id "
                + "  AND om.user_id <> COALESCE(m.sender_id, -1) AND om.last_read_msg_id >= m.id) AS seen_count "
                + "FROM messages m "
                + "JOIN members vm ON vm.conv_id = m.conv_id AND vm.user_id = ? "
                + "LEFT JOIN users u ON u.id = m.sender_id "
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

    public List<Message> search(long convId, long viewerId, String query) throws SQLException {
        if (query == null || query.isBlank()) return List.of();
        String sql = "SELECT m.id, m.conv_id, COALESCE(m.sender_id, 0) AS sender_id, u.name AS sender_name, "
                + "m.kind, m.content, m.file_path, m.file_name, m.file_size, m.duration_sec, m.reply_to, m.recalled, "
                + "EXTRACT(EPOCH FROM m.created_at) * 1000 AS ts, 0 AS seen_count "
                + "FROM messages m "
                + "JOIN members vm ON vm.conv_id = m.conv_id AND vm.user_id = ? "
                + "LEFT JOIN users u ON u.id = m.sender_id "
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
        m.recalled = rs.getBoolean("recalled");
        m.createdAt = Math.round(rs.getDouble("ts"));
        m.seenCount = rs.getInt("seen_count");
        m.deliveryState = m.seenCount > 0 ? Message.STATE_SEEN : Message.STATE_SENT;
        return m;
    }

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
