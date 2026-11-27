package com.zalolite.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Một cuộc hội thoại (chat 1-1 hoặc nhóm) hiển thị ở cột danh sách bên trái. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Conversation {
    public long id;
    public boolean isGroup;
    public boolean isCloud;
    public String name;
    public long ownerId;
    public String avatar;
    public int memberCount;

    // Dùng cho chat 1-1 để hiện chấm xanh và mở P2P
    public long peerId;
    public boolean peerOnline;

    // Tóm tắt ở danh sách hội thoại
    public String lastMessage;
    public long lastTime; // epoch millis
    public int unreadCount;
    public boolean pinned;

    public Conversation() {
    }
}
