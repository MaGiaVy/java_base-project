package com.example.model;

public enum MessageType {
    LOGIN("login"),
    LOGIN_RESULT("login_result"),
    MESSAGE("message"),
    SYSTEM("system"),
    DISCONNECT("disconnect"),
    USER_LEFT("user_left"),
    PING("ping");

    private final String value;

    MessageType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MessageType fromString(String text) {
        if (text == null) return null;
        for (MessageType t : MessageType.values()) {
            if (t.value.equalsIgnoreCase(text)) {
                return t;
            }
        }
        return null;
    }
}
