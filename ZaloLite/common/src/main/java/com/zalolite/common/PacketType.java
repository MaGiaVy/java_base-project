package com.zalolite.common;

/**
 * Hợp đồng tên gói tin dùng chung giữa Server và Client.
 * Dùng String hằng để thêm loại mới không phải sửa enum.
 */
public final class PacketType {
    private PacketType() {
    }

    // Trả lời cho request (có reqId)
    public static final String RESPONSE = "RESPONSE";

    // Tài khoản & hồ sơ
    public static final String REGISTER = "REGISTER";
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String PROFILE_UPDATE = "PROFILE_UPDATE";

    // P2P (client báo cổng lắng nghe, server trả ip:port của đối phương)
    public static final String P2P_REGISTER = "P2P_REGISTER";
    public static final String P2P_LOOKUP = "P2P_LOOKUP";

    // Bạn bè
    public static final String USER_SEARCH = "USER_SEARCH";
    public static final String FRIEND_LIST = "FRIEND_LIST";
    public static final String FRIEND_REQUEST = "FRIEND_REQUEST";
    public static final String FRIEND_ACCEPT = "FRIEND_ACCEPT";
    public static final String FRIEND_REJECT = "FRIEND_REJECT";
    public static final String FRIEND_REMOVE = "FRIEND_REMOVE";
    public static final String FRIEND_EVENT = "FRIEND_EVENT"; // push

    // Hội thoại & nhóm
    public static final String CONV_LIST = "CONV_LIST";
    public static final String CONV_OPEN_DIRECT = "CONV_OPEN_DIRECT";
    public static final String CONV_PIN = "CONV_PIN";
    public static final String CONV_UPDATE = "CONV_UPDATE"; // push
    public static final String GROUP_CREATE = "GROUP_CREATE";
    public static final String GROUP_MEMBERS = "GROUP_MEMBERS";
    public static final String GROUP_ADD_MEMBER = "GROUP_ADD_MEMBER";
    public static final String GROUP_REMOVE_MEMBER = "GROUP_REMOVE_MEMBER";
    public static final String GROUP_LEAVE = "GROUP_LEAVE";
    public static final String GROUP_RENAME = "GROUP_RENAME";

    // Tin nhắn & lịch sử
    public static final String MSG_SEND = "MSG_SEND";
    public static final String MSG_HISTORY = "MSG_HISTORY";
    public static final String MSG_NEW = "MSG_NEW"; // push
    public static final String MSG_RECALL = "MSG_RECALL";
    public static final String MSG_RECALLED = "MSG_RECALLED"; // push
    public static final String MSG_REACT = "MSG_REACT";
    public static final String MSG_REACTED = "MSG_REACTED"; // push
    public static final String MSG_SEARCH = "MSG_SEARCH";

    // Trạng thái (State)
    public static final String PRESENCE = "PRESENCE"; // push: user online/offline
    public static final String TYPING = "TYPING"; // client -> server -> push
    public static final String MSG_SEEN = "MSG_SEEN"; // client -> server
    public static final String SEEN_UPDATE = "SEEN_UPDATE"; // push
    public static final String MSG_DELIVERED = "MSG_DELIVERED"; // push
}
