package com.zalolite.server.dao;

import com.zalolite.common.User;
import com.zalolite.server.Db;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * [CSDL] Lớp DAO thao tác với bảng {@code users} trong PostgreSQL.
 * Đảm nhận đăng ký tài khoản, xác thực đăng nhập, tìm kiếm người dùng, cập nhật hồ sơ, đổi/đặt lại mật khẩu.
 * Bảo mật: mọi mật khẩu đều được băm bằng BCrypt (có salt ngẫu nhiên) trước khi lưu và xác minh bằng BCrypt.checkpw().
 */
public class UserDao {
    private final Db db;

    /** Khởi tạo UserDao với nguồn kết nối Db. */
    public UserDao(Db db) {
        this.db = db;
    }

    /** [CSDL] [XỬ LÝ] Băm mật khẩu bằng BCrypt rồi INSERT vào bảng users; trả về User mới hoặc null nếu số điện thoại đã tồn tại (ON CONFLICT DO NOTHING). */
    public User register(String phone, String name, String email, String password) throws SQLException {
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
        String sql = "INSERT INTO users(phone, name, email, pass_hash) VALUES (?,?,?,?) "
                + "ON CONFLICT (phone) DO NOTHING RETURNING id";
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, phone);
            ps.setString(2, name);
            ps.setString(3, email);
            ps.setString(4, hash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new User(rs.getLong(1), phone, name, email, null) : null;
            }
        }
    }

    /** [CSDL] [XỬ LÝ] Truy vấn user theo số điện thoại và so khớp mật khẩu bằng BCrypt.checkpw(); trả về User nếu hợp lệ, ngược lại null. */
    public User login(String phone, String password) throws SQLException {
        String sql = "SELECT id, phone, name, email, avatar, pass_hash FROM users WHERE phone = ?";
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !BCrypt.checkpw(password, rs.getString("pass_hash"))) return null;
                return map(rs);
            }
        }
    }

    /** [CSDL] Tìm thông tin công khai của user theo số điện thoại (dùng trong luồng quên mật khẩu OTP). */
    public User findByPhone(String phone) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement("SELECT id, phone, name, email, avatar FROM users WHERE phone = ?")) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** [CSDL] Tìm thông tin công khai của user theo khóa chính id. */
    public User findById(long id) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement("SELECT id, phone, name, email, avatar FROM users WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** [CSDL] Tìm kiếm tối đa 20 người dùng theo số điện thoại hoặc tên (ILIKE không phân biệt hoa thường), loại trừ chính người tìm. */
    public List<User> search(String query, long excludeUserId) throws SQLException {
        if (query == null || query.isBlank()) return List.of();
        String sql = "SELECT id, phone, name, email, avatar FROM users "
                + "WHERE id <> ? AND (phone ILIKE ? OR name ILIKE ?) ORDER BY name LIMIT 20";
        String like = "%" + query.trim() + "%";
        List<User> list = new ArrayList<>();
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, excludeUserId);
            ps.setString(2, like);
            ps.setString(3, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    /** [CSDL] Cập nhật tên hiển thị và email của user theo id, trả về bản ghi User mới nhất qua RETURNING. */
    public User updateProfile(long id, String name, String email) throws SQLException {
        String sql = "UPDATE users SET name = ?, email = ? WHERE id = ? RETURNING id, phone, name, email, avatar";
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setLong(3, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    /** [CSDL] [XỬ LÝ] Đổi mật khẩu: đọc pass_hash hiện tại, kiểm tra mật khẩu cũ bằng BCrypt.checkpw() rồi cập nhật hash mới. */
    public boolean changePassword(long userId, String oldPassword, String newPassword) throws SQLException {
        if (oldPassword == null || newPassword == null) return false;
        try (Connection c = db.open()) {
            String hash;
            try (PreparedStatement ps = c.prepareStatement("SELECT pass_hash FROM users WHERE id = ?")) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return false;
                    hash = rs.getString(1);
                }
            }
            if (!BCrypt.checkpw(oldPassword, hash)) return false;
            try (PreparedStatement ps = c.prepareStatement("UPDATE users SET pass_hash = ? WHERE id = ?")) {
                ps.setString(1, BCrypt.hashpw(newPassword, BCrypt.gensalt()));
                ps.setLong(2, userId);
                return ps.executeUpdate() > 0;
            }
        }
    }

    /** [CSDL] [XỬ LÝ] Đặt lại mật khẩu mới (băm BCrypt) theo email sau khi đã xác thực thành công mã OTP. */
    public boolean resetPasswordByEmail(String email, String newPassword) throws SQLException {
        if (email == null || email.isBlank() || newPassword == null) return false;
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET pass_hash = ? WHERE email = ?")) {
            ps.setString(1, BCrypt.hashpw(newPassword, BCrypt.gensalt()));
            ps.setString(2, email);
            return ps.executeUpdate() > 0;
        }
    }

    /** Ánh xạ một dòng ResultSet thành DTO User (chỉ gồm thông tin công khai). */
    static User map(ResultSet rs) throws SQLException {
        return new User(rs.getLong("id"), rs.getString("phone"), rs.getString("name"),
                rs.getString("email"), rs.getString("avatar"));
    }
}
