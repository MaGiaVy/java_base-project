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

public class UserDao {
    private final Db db;

    public UserDao(Db db) {
        this.db = db;
    }

    /** Trả về user mới, hoặc null nếu số điện thoại đã được đăng ký. */
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

    /** Trả về user nếu đúng mật khẩu, ngược lại null. */
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

    public User findByPhone(String phone) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement("SELECT id, phone, name, email, avatar FROM users WHERE phone = ?")) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public User findById(long id) throws SQLException {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement("SELECT id, phone, name, email, avatar FROM users WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

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

    static User map(ResultSet rs) throws SQLException {
        return new User(rs.getLong("id"), rs.getString("phone"), rs.getString("name"),
                rs.getString("email"), rs.getString("avatar"));
    }
}
