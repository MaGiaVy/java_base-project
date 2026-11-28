package com.zalolite.server.dao;

import com.zalolite.server.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * [CSDL] Lớp DAO thao tác với bảng {@code otp} trong PostgreSQL phục vụ chức năng quên mật khẩu qua email.
 * Lưu mã OTP 6 chữ số kèm thời hạn hết hiệu lực (TTL) và tiêu thụ mã dùng 1 lần (atomic DELETE).
 */
public class OtpDao {
    private final Db db;

    /** Khởi tạo OtpDao với nguồn kết nối Db. */
    public OtpDao(Db db) {
        this.db = db;
    }

    /** [CSDL] Lưu hoặc ghi đè (UPSERT) mã OTP cho email với thời hạn hết hiệu lực tính theo ttlMinutes phút từ hiện tại. */
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

    /** [CSDL] Xác thực và tiêu thụ ngay mã OTP bằng 1 lệnh DELETE nguyên tử (chỉ thành công khi đúng mã và chưa hết hạn expires_at > now()). */
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
