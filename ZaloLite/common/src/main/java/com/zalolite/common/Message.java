package com.zalolite.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DTO biểu diễn một tin nhắn trong hội thoại (văn bản, ảnh, tệp, video, ghi âm hoặc thông báo hệ thống).
 * Dùng chung cho cả luồng gửi/nhận qua Server (MSG_SEND / MSG_NEW / MSG_HISTORY) và truyền trực tiếp P2P giữa 2 Client.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {
    // Các loại nội dung tin nhắn hỗ trợ
    public static final String KIND_TEXT = "TEXT";
    public static final String KIND_IMAGE = "IMAGE";
    public static final String KIND_FILE = "FILE";
    public static final String KIND_VIDEO = "VIDEO";
    public static final String KIND_AUDIO = "AUDIO";
    public static final String KIND_SYSTEM = "SYSTEM";

    // Các mốc trạng thái chuyển phát tin nhắn (Đã gửi -> Đã nhận -> Đã xem)
    public static final String STATE_SENT = "SENT";
    public static final String STATE_DELIVERED = "DELIVERED";
    public static final String STATE_SEEN = "SEEN";

    /** ID tin nhắn trong bảng messages. */
    public long id;
    /** ID hội thoại chứa tin nhắn. */
    public long convId;
    /** ID người gửi (0 nếu là tin nhắn hệ thống). */
    public long senderId;
    /** Tên hiển thị của người gửi. */
    public String senderName;
    /** Loại tin nhắn (KIND_TEXT, KIND_IMAGE, ...). */
    public String kind;
    /** Nội dung chữ (hoặc mô tả khi thu hồi). */
    public String content;

    // Thông tin đính kèm cho IMAGE / FILE / VIDEO / AUDIO (tệp thật lưu trên FileServer cổng 9001)
    public String filePath;
    public String fileName;
    public long fileSize;
    public int durationSec;

    // Thông tin trả lời (reply), chuyển tiếp (forward), thu hồi (recall) và thời điểm tạo
    public Long replyTo;
    public String replySender;
    public String replyPreview;
    public boolean forwarded;
    public boolean recalled;
    public long createdAt; // epoch millis

    // Trạng thái chuyển phát, số người đã xem, kênh truyền (P2P/SERVER) và danh sách cảm xúc (userId -> emoji)
    public String deliveryState;
    public int seenCount;
    public String transport; // "P2P" hoặc "SERVER" (nhãn hiển thị khi demo)
    public Map<Long, String> reactions = new LinkedHashMap<>();

    /** Constructor rỗng phục vụ Jackson deserialize JSON. */
    public Message() {
    }
}
