package com.zalolite.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO biểu diễn một cuộc hội thoại (chat 1-1, nhóm chat, hoặc Cloud của tôi) dùng chung giữa Server và Client.
 * Server tổng hợp dữ liệu từ bảng conversations, conversation_members và messages rồi gửi về Client qua gói CONV_LIST / CONV_OPEN_DIRECT.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Conversation {
    /** Khóa chính hội thoại trong bảng conversations. */
    public long id;
    /** true nếu là nhóm chat nhiều người; false nếu là chat 1-1 hoặc Cloud cá nhân. */
    public boolean isGroup;
    /** true nếu là hộp "Cloud của tôi" (hội thoại lưu trữ riêng của chính user). */
    public boolean isCloud;
    /** Tên hiển thị của hội thoại (tên nhóm, "Cloud của tôi", hoặc tên người đối diện). */
    public String name;
    /** ID trưởng nhóm (chỉ dùng cho nhóm chat để kiểm tra quyền thêm/xóa thành viên, đổi tên). */
    public long ownerId;
    /** Đường dẫn ảnh đại diện của nhóm hoặc người đối diện. */
    public String avatar;
    /** Tổng số thành viên hiện tại trong hội thoại. */
    public int memberCount;

    // Dùng cho chat 1-1 để hiện chấm xanh online và tra cứu địa chỉ mở kết nối P2P / gọi thoại
    public long peerId;
    public boolean peerOnline;

    // Tóm tắt hiển thị ở cột danh sách hội thoại bên trái
    public String lastMessage;
    public long lastTime; // epoch millis
    public int unreadCount;
    public boolean pinned;

    /** Constructor rỗng phục vụ Jackson deserialize JSON. */
    public Conversation() {
    }
}
