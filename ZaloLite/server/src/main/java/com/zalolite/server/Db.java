package com.zalolite.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Kết nối PostgreSQL bằng JDBC thuần. Tự tạo database và bảng nếu chưa có. */
public class Db {
    private final String url;
    private final String user;
    private final String password;

    public Db(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public static Db fromConfig(ServerConfig c) {
        return new Db(c.get("db.url", "jdbc:postgresql://localhost:5432/zalolite"),
                c.get("db.user", "postgres"), c.get("db.password", ""));
    }

    // shortcut: mở kết nối mới mỗi lần, đủ cho vài chục client; nhiều hơn thì dùng connection pool
    public Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** Tạo database (nếu thiếu) rồi chạy schema.sql. */
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

    private void runSchema(Connection c) throws SQLException, IOException {
        try (InputStream in = Db.class.getResourceAsStream("/schema.sql"); Statement st = c.createStatement()) {
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            st.execute(sql);
        }
    }
}
