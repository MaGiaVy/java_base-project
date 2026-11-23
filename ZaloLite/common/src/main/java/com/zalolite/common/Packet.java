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
 * Gói tin trao đổi giữa client và server: mỗi gói là 1 dòng JSON.
 * Ví dụ: {"type":"LOGIN","reqId":1,"data":{"phone":"0901234567","password":"..."}}
 * Server trả lời bằng type RESPONSE kèm cùng reqId; sự kiện đẩy (push) thì không có reqId.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Packet {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    public String type;
    public Long reqId;
    public ObjectNode data = MAPPER.createObjectNode();

    public Packet() {
    }

    public Packet(String type) {
        this.type = type;
    }

    public static Packet ok(Packet request) {
        Packet p = new Packet(PacketType.RESPONSE);
        p.reqId = request.reqId;
        p.data.put("ok", true);
        return p;
    }

    public static Packet error(Packet request, String message) {
        Packet p = new Packet(PacketType.RESPONSE);
        p.reqId = request == null ? null : request.reqId;
        p.data.put("ok", false);
        p.data.put("error", message);
        return p;
    }

    public Packet put(String key, Object value) {
        data.set(key, MAPPER.valueToTree(value));
        return this;
    }

    public String str(String key) {
        JsonNode n = data.get(key);
        return n == null || n.isNull() ? null : n.asText();
    }

    public long num(String key) {
        JsonNode n = data.get(key);
        return n == null ? 0 : n.asLong();
    }

    public boolean bool(String key) {
        JsonNode n = data.get(key);
        return n != null && n.asBoolean(false);
    }

    public boolean isOk() {
        return data.path("ok").asBoolean(false);
    }

    public <T> T get(String key, Class<T> cls) {
        try {
            JsonNode n = data.get(key);
            return n == null || n.isNull() ? null : MAPPER.treeToValue(n, cls);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Sai định dạng trường " + key, e);
        }
    }

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

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Packet fromJson(String line) throws JsonProcessingException {
        Packet p = MAPPER.readValue(line, Packet.class);
        if (p.type == null) throw new IllegalArgumentException("Thiếu type");
        if (p.data == null) p.data = MAPPER.createObjectNode();
        return p;
    }
}
