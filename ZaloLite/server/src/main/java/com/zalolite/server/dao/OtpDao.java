package com.zalolite.server.dao;

import com.zalolite.server.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class OtpDao {
    private final Db db;

    public OtpDao(Db db) {
        this.db = db;
    }

    public void save(String email, String code, int ttlMinutes) throws SQLException {
        String sql = "INSERT INTO otp(email, code, expires_at) VALUES (?, ?, now() + (? || ' minutes')::interval) "
                + "ON CONFLICT (email) DO UPDATE SET code = EXCLUDED.code, expires_at = EXCLUDED.expires_at";
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, code);
            ps.setString(3, String.valueOf(ttlMinutes));
            ps.executeUpdate();
        }
    }

    public boolean verifyAndConsume(String email, String code) throws SQLException {
        if (email == null || code == null) return false;
        String sql = "DELETE FROM otp WHERE email = ? AND code = ? AND expires_at > now()";
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, code);
            return ps.executeUpdate() > 0;
        }
    }
}
