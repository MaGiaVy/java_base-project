package com.zalolite.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * [CỔNG/GIAO THỨC] Định dạng gói tin tầng ứng dụng trao đổi qua kết nối TCP/TLS (cổng 9000) giữa Client và Server.
 * Mỗi gói tin được tuần tự hóa thành đúng 1 dòng JSON (NDJSON - kết thúc bằng '\n') để dễ tách khung tin (framing) bằng BufferedReader.readLine().
 * - Request từ Client -> Server: chứa type, reqId tăng dần và payload data (ObjectNode).
 *   Ví dụ: {"type":"LOGIN","reqId":1,"data":{"phone":"0901234567","password":"..."}}
 * - Response từ Server -> Client: dùng type = "RESPONSE", giữ nguyên reqId của request để Client khớp Future, kèm "ok": true/false.
 * - Push event từ Server -> Client (tin nhắn mới, trạng thái online, cuộc gọi...): không có reqId (reqId = null).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Packet {
    /** Jackson ObjectMapper dùng chung để chuyển đổi qua lại giữa Object Java và chuỗi JSON. */
    public static final ObjectMapper MAPPER = new ObjectMapper();

    /** Tên loại gói tin (khai báo hằng trong PacketType). */
    public String type;
    /** Mã định danh yêu cầu để ghép cặp Request - Response bất đồng bộ; null nếu là sự kiện Push. */
    public Long reqId;
    /** Thân dữ liệu JSON chứa tham số hoặc kết quả của gói tin. */
    public ObjectNode data = MAPPER.createObjectNode();

    /** Constructor rỗng phục vụ Jackson deserialize từ chuỗi JSON. */
    public Packet() {
    }

    /** Khởi tạo gói tin mới với loại chỉ định (thường dùng cho Push event hoặc Request). */
    public Packet(String type) {
        this.type = type;
    }

    /** [GỬI] Tạo gói phản hồi thành công (ok=true) gắn đúng reqId của request gốc để trả về Client. */
    public static Packet ok(Packet request) {
        Packet p = new Packet(PacketType.RESPONSE);
        p.reqId = request.reqId;
        p.data.put("ok", true);
        return p;
    }

    /** [GỬI] Tạo gói phản hồi lỗi (ok=false, kèm thông báo error) gắn reqId của request gốc (nếu có). */
    public static Packet error(Packet request, String message) {
        Packet p = new Packet(PacketType.RESPONSE);
        p.reqId = request == null ? null : request.reqId;
        p.data.put("ok", false);
        p.data.put("error", message);
        return p;
    }

    /** [XỬ LÝ] Đưa một cặp key-value (bất kỳ POJO/kiểu cơ bản nào) vào payload JSON theo kiểu builder chain. */
    public Packet put(String key, Object value) {
        data.set(key, MAPPER.valueToTree(value));
        return this;
    }

    /** [XỬ LÝ] Đọc trường chuỗi từ payload data; trả null nếu thiếu hoặc giá trị JSON là null. */
    public String str(String key) {
        JsonNode n = data.get(key);
        return n == null || n.isNull() ? null : n.asText();
    }

    /** [XỬ LÝ] Đọc trường số nguyên kiểu long từ payload data; mặc định trả 0 nếu không tồn tại. */
    public long num(String key) {
        JsonNode n = data.get(key);
        return n == null ? 0 : n.asLong();
    }

    /** [XỬ LÝ] Đọc trường boolean từ payload data; mặc định trả false nếu không tồn tại. */
    public boolean bool(String key) {
        JsonNode n = data.get(key);
        return n != null && n.asBoolean(false);
    }

    /** [XỬ LÝ] Kiểm tra nhanh gói phản hồi có thành công hay không (data.ok == true). */
    public boolean isOk() {
        return data.path("ok").asBoolean(false);
    }

    /** [XỬ LÝ] Giải mã một trường con trong payload JSON thành đối tượng Java kiểu T (User, Message, ...). */
    public <T> T get(String key, Class<T> cls) {
        try {
            JsonNode n = data.get(key);
            return n == null || n.isNull() ? null : MAPPER.treeToValue(n, cls);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Sai định dạng trường " + key, e);
        }
    }

    /** [XỬ LÝ] Giải mã một mảng JSON trong payload thành List<T>; trả danh sách rỗng nếu thiếu hoặc không phải mảng. */
    public <T> List<T> getList(String key, Class<T> cls) {
        JsonNode n = data.get(key);
        List<T> out = new ArrayList<>();
        if (n == null || !n.isArray()) return out;
        try {
            for (JsonNode item : n) out.add(MAPPER.treeToValue(item, cls));
            return out;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Sai định dạng mảng " + key, e);
        }
    }

    /** [GỬI] Tuần tự hóa đối tượng Packet thành 1 dòng chuỗi JSON trước khi ghi ra OutputStream của socket. */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** [NHẬN] Phân tích 1 dòng JSON đọc từ InputStream của socket thành đối tượng Packet; bắt buộc có trường type. */
    public static Packet fromJson(String line) throws JsonProcessingException {
        Packet p = MAPPER.readValue(line, Packet.class);
        if (p.type == null) throw new IllegalArgumentException("Thiếu type");
        if (p.data == null) p.data = MAPPER.createObjectNode();
        return p;
    }
}
