package com.zalolite.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/** Một tin nhắn trong hội thoại (chữ, ảnh, file, video, thoại hoặc thông báo hệ thống). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {
    public static final String KIND_TEXT = "TEXT";
    public static final String KIND_IMAGE = "IMAGE";
    public static final String KIND_FILE = "FILE";
    public static final String KIND_VIDEO = "VIDEO";
    public static final String KIND_AUDIO = "AUDIO";
    public static final String KIND_SYSTEM = "SYSTEM";

    public static final String STATE_SENT = "SENT";
    public static final String STATE_DELIVERED = "DELIVERED";
    public static final String STATE_SEEN = "SEEN";

    public long id;
    public long convId;
    public long senderId;
    public String senderName;
    public String kind;
    public String content;

    // Thông tin đính kèm cho IMAGE / FILE / VIDEO / AUDIO
    public String filePath;
    public String fileName;
    public long fileSize;
    public int durationSec;

    public Long replyTo;
    public boolean recalled;
    public long createdAt; // epoch millis

    // Trạng thái & cảm xúc (userId -> emoji)
    public String deliveryState;
    public int seenCount;
    public String transport; // "P2P" hoặc "SERVER" (nhãn hiển thị khi demo)
    public Map<Long, String> reactions = new LinkedHashMap<>();

    public Message() {
    }
}
