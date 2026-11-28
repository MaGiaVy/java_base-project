package com.zalolite.server.dao;

import com.zalolite.common.User;
import com.zalolite.server.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * [CSDL] Lớp DAO thao tác với bảng {@code friendships} trong PostgreSQL.
 * Quản lý toàn bộ quan hệ xã hội giữa hai người dùng: lời mời kết bạn (PENDING), bạn bè (ACCEPTED),
 * chặn/bỏ chặn (BLOCKED) và truy vấn danh sách bạn bè để phát trạng thái online (PRESENCE).
 */
public class FriendDao {
    private final Db db;

    /** Khởi tạo FriendDao với nguồn kết nối Db. */
    public FriendDao(Db db) {
        this.db = db;
    }

    /** [CSDL] Kiểm tra quan hệ 2 chiều hiện có; nếu chưa có thì chèn lời mời PENDING mới. Trả về null nếu thành công hoặc chuỗi lý do từ chối. */
    public String request(long fromId, long toId) throws SQLException {
        if (fromId == toId) return "Không thể tự kết bạn với chính mình";
        String check = "SELECT status FROM friendships WHERE (user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)";
        String insert = "INSERT INTO friendships(user_id, friend_id, status) VALUES (?, ?, 'PENDING')";
        try (Connection c = db.open()) {
            try (PreparedStatement ps = c.prepareStatement(check)) {
                ps.setLong(1, fromId);
                ps.setLong(2, toId);
                ps.setLong(3, toId);
                ps.setLong(4, fromId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String st = rs.getString(1);
                        if ("ACCEPTED".equals(st)) return "Hai người đã là bạn bè";
                        if ("PENDING".equals(st)) return "Đã có lời mời kết bạn đang chờ";
                        return "Không thể gửi lời mời kết bạn";
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement(insert)) {
                ps.setLong(1, fromId);
                ps.setLong(2, toId);
                ps.executeUpdate();
            }
            return null;
        }
    }

    /** [CSDL] Chấp nhận lời mời kết bạn đang ở trạng thái PENDING (chuyển sang ACCEPTED). */
    public boolean accept(long userId, long requesterId) throws SQLException {
        String sql = "UPDATE friendships SET status = 'ACCEPTED' WHERE user_id = ? AND friend_id = ? AND status = 'PENDING'";
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, requesterId);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    /** [CSDL] Từ chối lời mời kết bạn bằng cách xóa bản ghi PENDING tương ứng. */
    public boolean reject(long userId, long requesterId) throws SQLException {
        String sql = "DELETE FROM friendships WHERE user_id = ? AND friend_id = ? AND status = 'PENDING'";
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, requesterId);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    /** [CSDL] Hủy kết bạn: xóa quan hệ giữa u1 và u2 theo cả 2 chiều. */
    public boolean remove(long u1, long u2) throws SQLException {
        String sql = "DELETE FROM friendships WHERE (user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)";
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, u1);
            ps.setLong(2, u2);
            ps.setLong(3, u2);
            ps.setLong(4, u1);
            return ps.executeUpdate() > 0;
        }
    }

    /** [CSDL] Chặn người dùng trong một transaction: xóa mọi quan hệ cũ giữa hai người rồi ghi nhận bản ghi BLOCKED. */
    public boolean block(long blockerId, long targetId) throws SQLException {
        if (blockerId == targetId) return false;
        String del = "DELETE FROM friendships WHERE (user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)";
        String ins = "INSERT INTO friendships(user_id, friend_id, status) VALUES (?, ?, 'BLOCKED')";
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(del)) {
                    ps.setLong(1, blockerId);
                    ps.setLong(2, targetId);
                    ps.setLong(3, targetId);
                    ps.setLong(4, blockerId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(ins)) {
                    ps.setLong(1, blockerId);
                    ps.setLong(2, targetId);
                    ps.executeUpdate();
                }
                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** [CSDL] Bỏ chặn: xóa bản ghi BLOCKED do blockerId tạo ra đối với targetId. */
    public boolean unblock(long blockerId, long targetId) throws SQLException {
        String sql = "DELETE FROM friendships WHERE user_id = ? AND friend_id = ? AND status = 'BLOCKED'";
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, blockerId);
            ps.setLong(2, targetId);
            return ps.executeUpdate() > 0;
        }
    }

    /** [CSDL] Kiểm tra xem u1 hoặc u2 có đang chặn nhau hay không (dùng để chặn nhắn tin 1-1 và chặn gọi thoại). */
    public boolean isBlockedEitherWay(long u1, long u2) throws SQLException {
        String sql = "SELECT 1 FROM friendships "
                + "WHERE ((user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)) AND status = 'BLOCKED' LIMIT 1";
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, u1);
            ps.setLong(2, u2);
            ps.setLong(3, u2);
            ps.setLong(4, u1);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** [CSDL] Lấy danh sách người dùng đang bị userId chặn. */
    public List<User> listBlocked(long userId) throws SQLException {
        String sql = "SELECT u.id, u.phone, u.name, u.email, u.avatar FROM friendships f "
                + "JOIN users u ON u.id = f.friend_id WHERE f.user_id = ? AND f.status = 'BLOCKED' ORDER BY f.created_at DESC";
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            return readUsers(ps);
        }
    }

    /** [CSDL] Lấy danh sách bạn bè (status = 'ACCEPTED') của userId, sắp xếp theo tên. */
    public List<User> listFriends(long userId) throws SQLException {
        String sql = "SELECT u.id, u.phone, u.name, u.email, u.avatar FROM friendships f "
                + "JOIN users u ON u.id = CASE WHEN f.user_id = ? THEN f.friend_id ELSE f.user_id END "
                + "WHERE (f.user_id = ? OR f.friend_id = ?) AND f.status = 'ACCEPTED' ORDER BY u.name";
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            ps.setLong(3, userId);
            return readUsers(ps);
        }
    }

    /** [CSDL] Lấy danh sách lời mời kết bạn đến (người khác gửi tới userId, đang PENDING). */
    public List<User> listIncoming(long userId) throws SQLException {
        String sql = "SELECT u.id, u.phone, u.name, u.email, u.avatar FROM friendships f "
                + "JOIN users u ON u.id = f.user_id WHERE f.friend_id = ? AND f.status = 'PENDING' ORDER BY f.created_at DESC";
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            return readUsers(ps);
        }
    }

    /** [CSDL] Lấy danh sách lời mời kết bạn đi (userId đã gửi cho người khác, đang PENDING). */
    public List<User> listOutgoing(long userId) throws SQLException {
        String sql = "SELECT u.id, u.phone, u.name, u.email, u.avatar FROM friendships f "
                + "JOIN users u ON u.id = f.friend_id WHERE f.user_id = ? AND f.status = 'PENDING' ORDER BY f.created_at DESC";
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            return readUsers(ps);
        }
    }

    /** [CSDL] Lấy danh sách ID bạn bè của userId để Server phát sự kiện PRESENCE (online/offline). */
    public List<Long> listFriendIds(long userId) throws SQLException {
        String sql = "SELECT CASE WHEN user_id = ? THEN friend_id ELSE user_id END FROM friendships "
                + "WHERE (user_id = ? OR friend_id = ?) AND status = 'ACCEPTED'";
        List<Long> ids = new ArrayList<>();
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            ps.setLong(3, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    /** Hàm tiện ích thực thi PreparedStatement và gom ResultSet thành List<User>. */
    private static List<User> readUsers(PreparedStatement ps) throws SQLException {
        List<User> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(UserDao.map(rs));
        }
        return list;
    }
}
