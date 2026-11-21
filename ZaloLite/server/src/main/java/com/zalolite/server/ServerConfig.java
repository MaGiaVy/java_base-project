package com.zalolite.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** Đọc cấu hình từ server.properties (file này chứa mật khẩu, KHÔNG đưa lên git). */
public class ServerConfig {
    private final Properties props = new Properties();

    public ServerConfig(Properties p) {
        props.putAll(p);
    }

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

    public String get(String key, String def) {
        String v = props.getProperty(key);
        return v == null || v.isBlank() ? def : v.trim();
    }

    public int getInt(String key, int def) {
        return Integer.parseInt(get(key, String.valueOf(def)));
    }
}
