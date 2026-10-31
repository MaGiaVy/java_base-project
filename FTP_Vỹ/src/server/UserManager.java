package server;

import java.util.HashMap;
import java.util.Map;

/**
 * Quản lý người dùng, mật khẩu và phân quyền RBAC (Slide 30)
 */
public class UserManager {

    public enum Role {
        ADMIN,      // Toàn quyền: Đọc, Ghi, Xóa, Tạo thư mục, Đổi tên
        STUDENT,    // Quyền sinh viên: Đọc, Ghi, Upload, Tạo thư mục
        GUEST       // Khách (Anonymous): Chỉ đọc (LIST, RETR)
    }

    public static class UserAccount {
        public final String username;
        public final String password;
        public final Role role;

        public UserAccount(String username, String password, Role role) {
            this.username = username;
            this.password = password;
            this.role = role;
        }
    }

    private final Map<String, UserAccount> users = new HashMap<>();

    public UserManager() {
        // Tài khoản mặc định theo yêu cầu của Thầy và đồ án
        users.put("student", new UserAccount("student", "123456", Role.STUDENT));
        users.put("admin", new UserAccount("admin", "admin", Role.ADMIN));
        users.put("anonymous", new UserAccount("anonymous", "", Role.GUEST));
        users.put("ftp", new UserAccount("ftp", "", Role.GUEST));
    }

    public boolean userExists(String username) {
        if (username == null) return false;
        return users.containsKey(username.toLowerCase());
    }

    public boolean authenticate(String username, String password) {
        if (username == null) return false;
        UserAccount acc = users.get(username.toLowerCase());
        if (acc == null) return false;

        // Cho phép anonymous đăng nhập với bất kỳ mật khẩu nào
        if (acc.role == Role.GUEST) {
            return true;
        }
        return acc.password.equals(password);
    }

    public Role getRole(String username) {
        if (username == null) return Role.GUEST;
        UserAccount acc = users.get(username.toLowerCase());
        return acc != null ? acc.role : Role.GUEST;
    }

    public boolean canWrite(String username) {
        Role r = getRole(username);
        return r == Role.ADMIN || r == Role.STUDENT;
    }

    public boolean canDelete(String username) {
        Role r = getRole(username);
        // Chỉ ADMIN mới có quyền Xóa file/thư mục (Bảo mật RBAC theo đúng Slide 30)
        return r == Role.ADMIN;
    }
}
