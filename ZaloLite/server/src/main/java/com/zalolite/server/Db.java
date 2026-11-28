package com.zalolite.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * [CSDL] Lớp quản lý kết nối PostgreSQL bằng JDBC thuần (DriverManager).
 * Chịu trách nhiệm mở Connection cho tất cả các lớp DAO và tự động khởi tạo database + chạy schema.sql khi server mới khởi động.
 */
public class Db {
    private final String url;
    private final String user;
    private final String password;

    /** Khởi tạo đối tượng cấu hình kết nối JDBC tới PostgreSQL. */
    public Db(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /** [CSDL] Tạo instance Db từ ServerConfig (mặc định kết nối jdbc:postgresql://localhost:5432/zalolite). */
    public static Db fromConfig(ServerConfig c) {
        return new Db(c.get("db.url", "jdbc:postgresql://localhost:5432/zalolite"),
                c.get("db.user", "postgres"), c.get("db.password", ""));
    }

    /** [CSDL] Mở một kết nối JDBC mới tới PostgreSQL (được các lớp DAO dùng trong try-with-resources). */
    // shortcut: mở kết nối mới mỗi lần, đủ cho vài chục client; nhiều hơn thì dùng connection pool
    public Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** [CSDL] Khởi tạo CSDL: kết nối thử, nếu gặp mã lỗi 3D000 (chưa có database) thì tự CREATE DATABASE rồi chạy schema.sql. */
    public void init() throws SQLException, IOException {
        try (Connection c = open()) {
            runSchema(c);
        } catch (SQLException e) {
            if (!"3D000".equals(e.getSQLState())) throw e; // 3D000 = database không tồn tại
            createDatabase();
            try (Connection c = open()) {
                runSchema(c);
            }
        }
    }

    /** [CSDL] Kết nối vào database mặc định 'postgres' để thực thi lệnh CREATE DATABASE cho ứng dụng (có kiểm tra regex chống SQL injection). */
    private void createDatabase() throws SQLException {
        int slash = url.lastIndexOf('/');
        String dbName = url.substring(slash + 1).split("\\?")[0];
        if (!dbName.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new SQLException("Tên database không hợp lệ: " + dbName);
        try (Connection c = DriverManager.getConnection(url.substring(0, slash + 1) + "postgres", user, password);
             Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE " + dbName + " ENCODING 'UTF8'");
            System.out.println("[INFO] Đã tạo database " + dbName);
        }
    }

    /** [CSDL] Đọc toàn bộ script DDL trong resources/schema.sql và thực thi để tạo các bảng/index (IF NOT EXISTS). */
    private void runSchema(Connection c) throws SQLException, IOException {
        try (InputStream in = Db.class.getResourceAsStream("/schema.sql"); Statement st = c.createStatement()) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            st.execute(sql);
        }
    }
}
