package com.zalolite.common;

/** Tên các loại gói tin. Dùng String hằng để thêm loại mới không phải sửa enum/parse. */
public final class PacketType {
    private PacketType() {
    }

    // Trả lời cho một request (có reqId)
    public static final String RESPONSE = "RESPONSE";

    // Tài khoản
    public static final String REGISTER = "REGISTER";
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
}
