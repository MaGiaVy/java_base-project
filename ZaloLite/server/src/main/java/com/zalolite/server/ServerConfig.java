package com.zalolite.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Bộ nạp cấu hình máy chủ từ tệp server.properties (cổng TCP/File/Discovery, PostgreSQL, keystore TLS, SMTP).
 * File server.properties chứa mật khẩu DB/SMTP nên không được commit lên git.
 */
public class ServerConfig {
    private final Properties props = new Properties();

    /** Khởi tạo ServerConfig từ đối tượng Properties đã đọc sẵn. */
    public ServerConfig(Properties p) {
        props.putAll(p);
    }

    /** [XỬ LÝ] Tìm và đọc tệp server.properties theo UTF-8 tại thư mục hiện hành hoặc thư mục con server/. */
    public static ServerConfig load() throws IOException {
        for (String path : new String[]{"server.properties", "server/server.properties"}) {
            File f = new File(path);
            if (f.exists()) {
                Properties p = new Properties();
                try (var r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
                    p.load(r);
                }
                System.out.println("[INFO] Đọc cấu hình: " + f.getAbsolutePath());
                return new ServerConfig(p);
            }
        }
        throw new IOException("Không thấy server.properties. Hãy copy server.properties.example thành server.properties rồi điền mật khẩu.");
    }

    /** [XỬ LÝ] Lấy giá trị cấu hình dạng chuỗi theo key; trả về giá trị mặc định def nếu thiếu hoặc rỗng. */
    public String get(String key, String def) {
        String v = props.getProperty(key);
        return v == null || v.isBlank() ? def : v.trim();
    }

    /** [XỬ LÝ] Lấy giá trị cấu hình dạng số nguyên (dùng cho số cổng TCP/UDP/SMTP). */
    public int getInt(String key, int def) {
        return Integer.parseInt(get(key, String.valueOf(def)));
    }
}
