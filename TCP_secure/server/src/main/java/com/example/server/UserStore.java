package com.example.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.mindrot.jbcrypt.BCrypt;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quản lý xác thực người dùng và băm mật khẩu với BCrypt.
 * Tuyệt đối không lưu trữ hoặc ghi log mật khẩu dạng rõ (plaintext).
 */
public class UserStore {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final File storageFile;
    private final ConcurrentHashMap<String, String> users = new ConcurrentHashMap<>();

    public UserStore() {
        this(resolveDefaultFile());
    }

    public UserStore(String filePath) {
        this(new File(filePath));
    }

    public UserStore(File file) {
        this.storageFile = file;
        loadUsers();
    }

    private static File resolveDefaultFile() {
        // Thứ tự ưu tiên tìm file users.json
        File f1 = new File("server/src/main/resources/users.json");
        if (f1.exists()) return f1;

        File f2 = new File("src/main/resources/users.json");
        if (f2.exists()) return f2;

        File f3 = new File("users.json");
        if (f3.exists()) return f3;

        // Nếu chưa tồn tại, ưu tiên tạo ở server/src/main/resources nếu thư mục cha có sẵn
        if (f1.getParentFile() != null && f1.getParentFile().exists()) {
            return f1;
        }
        return f3;
    }

    private synchronized void loadUsers() {
        if (storageFile != null && storageFile.exists() && storageFile.length() > 0) {
            try {
                Map<String, String> loaded = MAPPER.readValue(storageFile, new TypeReference<Map<String, String>>() {});
                if (loaded != null) {
                    users.putAll(loaded);
                    System.out.printf("[INFO] Đã nạp %d tài khoản từ file: %s%n", users.size(), storageFile.getPath());
                    return;
                }
            } catch (IOException e) {
                System.err.printf("[WARN] Không thể đọc file %s: %s. Thử nạp từ classpath...%n",
                        storageFile.getPath(), e.getMessage());
            }
        }

        // Thử đọc từ classpath
        try (InputStream is = getClass().getResourceAsStream("/users.json")) {
            if (is != null) {
                Map<String, String> loaded = MAPPER.readValue(is, new TypeReference<Map<String, String>>() {});
                if (loaded != null) {
                    users.putAll(loaded);
                    System.out.printf("[INFO] Đã nạp %d tài khoản từ classpath /users.json%n", users.size());
                    persist();
                    return;
                }
            }
        } catch (IOException e) {
            System.err.printf("[WARN] Không thể đọc từ classpath: %s%n", e.getMessage());
        }

        // Khởi tạo mặc định nếu chưa có tài khoản nào
        initDefaultUsers();
    }

    private void initDefaultUsers() {
        System.out.println("[INFO] Khởi tạo tài khoản mẫu mặc định (an, binh, chi)...");
        users.put("an", BCrypt.hashpw("pass123", BCrypt.gensalt(12)));
        users.put("binh", BCrypt.hashpw("pass456", BCrypt.gensalt(12)));
        users.put("chi", BCrypt.hashpw("pass789", BCrypt.gensalt(12)));
        persist();
    }

    private synchronized void persist() {
        if (storageFile == null) {
            return;
        }
        try {
            if (storageFile.getParentFile() != null && !storageFile.getParentFile().exists()) {
                storageFile.getParentFile().mkdirs();
            }
            MAPPER.writeValue(storageFile, users);
        } catch (IOException e) {
            System.err.printf("[WARN] Không thể lưu file %s: %s%n", storageFile.getPath(), e.getMessage());
        }
    }

    /**
     * Xác thực thông tin đăng nhập của người dùng.
     * Sử dụng BCrypt.checkpw để so khớp mật khẩu thô với mã băm.
     *
     * @param username tên tài khoản
     * @param rawPassword mật khẩu thô
     * @return true nếu xác thực thành công, false nếu thất bại
     */
    public boolean authenticate(String username, String rawPassword) {
        if (username == null || rawPassword == null) {
            return false;
        }
        String storedHash = users.get(username);
        if (storedHash == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword, storedHash);
        } catch (Exception e) {
            System.err.printf("[WARN] Lỗi khi kiểm tra mật khẩu BCrypt cho user '%s': %s%n", username, e.getMessage());
            return false;
        }
    }

    /**
     * Đăng ký tài khoản người dùng mới với mật khẩu được băm BCrypt (cost factor 12).
     *
     * @param username tên tài khoản mới
     * @param rawPassword mật khẩu thô
     * @return true nếu đăng ký thành công, false nếu tài khoản đã tồn tại hoặc tham số rỗng
     */
    public synchronized boolean registerUser(String username, String rawPassword) {
        if (username == null || rawPassword == null || username.trim().isEmpty() || rawPassword.isEmpty()) {
            return false;
        }
        String cleanUser = username.trim();
        if (users.containsKey(cleanUser)) {
            return false;
        }
        String hashed = BCrypt.hashpw(rawPassword, BCrypt.gensalt(12));
        users.put(cleanUser, hashed);
        persist();
        System.out.printf("[INFO] Đăng ký thành công tài khoản mới: '%s'%n", cleanUser);
        return true;
    }

    public boolean userExists(String username) {
        return username != null && users.containsKey(username.trim());
    }

    public int getUserCount() {
        return users.size();
    }
}
