package com.zalolite.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO biểu diễn thông tin công khai của người dùng trao đổi qua JSON giữa Server và Client.
 * Thiết kế an toàn: không bao giờ chứa passwordHash để tránh rò rỉ dữ liệu nhạy cảm ra mạng.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class User {
    /** Khóa chính định danh người dùng trong PostgreSQL (bảng users). */
    public long id;
    /** Số điện thoại đăng nhập (duy nhất). */
    public String phone;
    /** Tên hiển thị của người dùng. */
    public String name;
    /** Địa chỉ email dùng để nhận mã OTP khôi phục mật khẩu qua SMTP. */
    public String email;
    /** Đường dẫn tương đối tới ảnh đại diện trên FileServer (cổng 9001). */
    public String avatar;
    /** Trạng thái trực tuyến hiện tại (suy ra động từ SessionRegistry trên Server). */
    public boolean online;

    /** Constructor rỗng phục vụ Jackson deserialize JSON. */
    public User() {
    }

    /** Khởi tạo đối tượng User từ dữ liệu đọc ra ở UserDao. */
    public User(long id, String phone, String name, String email, String avatar) {
        this.id = id;
        this.phone = phone;
        this.name = name;
        this.email = email;
        this.avatar = avatar;
    }

    /** Chuỗi hiển thị ngắn gọn trên giao diện (VD: danh sách chọn bạn bè). */
    @Override
    public String toString() {
        return name + " (" + phone + ")";
    }
}
