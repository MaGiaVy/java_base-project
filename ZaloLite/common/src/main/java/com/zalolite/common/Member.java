package com.zalolite.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO biểu diễn một thành viên thuộc cuộc hội thoại / nhóm chat.
 * Được Server trả về trong gói GROUP_MEMBERS kèm vai trò và vị trí tin nhắn đã đọc cuối cùng.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Member {
    /** Thông tin công khai của thành viên. */
    public User user;
    /** Vai trò trong hội thoại: OWNER (trưởng nhóm) hoặc MEMBER (thành viên). */
    public String role; // OWNER | MEMBER
    /** ID tin nhắn mới nhất mà thành viên này đã đọc (dùng tính trạng thái SEEN). */
    public long lastReadMsgId;

    /** Constructor rỗng phục vụ Jackson deserialize JSON. */
    public Member() {
    }

    /** Khởi tạo đối tượng Member từ bản ghi bảng conversation_members kết hợp bảng users. */
    public Member(User user, String role, long lastReadMsgId) {
        this.user = user;
        this.role = role;
        this.lastReadMsgId = lastReadMsgId;
    }
}
