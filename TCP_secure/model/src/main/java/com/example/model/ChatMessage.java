package com.example.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatMessage {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String type;
    private String username;
    private String password;
    private String content;
    private Boolean success;
    private Integer clientId;

    public ChatMessage() {
    }

    public ChatMessage(String type) {
        this.type = type;
    }

    public static ChatMessage createLogin(String username, String password) {
        ChatMessage msg = new ChatMessage(MessageType.LOGIN.getValue());
        msg.setUsername(username);
        msg.setPassword(password);
        return msg;
    }

    public static ChatMessage createLoginResult(boolean success, int clientId, String message) {
        ChatMessage msg = new ChatMessage(MessageType.LOGIN_RESULT.getValue());
        msg.setSuccess(success);
        msg.setClientId(clientId);
        msg.setContent(message);
        return msg;
    }

    public static ChatMessage createMessage(String username, String content) {
        ChatMessage msg = new ChatMessage(MessageType.MESSAGE.getValue());
        msg.setUsername(username);
        msg.setContent(content);
        return msg;
    }

    public static ChatMessage createSystem(String content) {
        ChatMessage msg = new ChatMessage(MessageType.SYSTEM.getValue());
        msg.setContent(content);
        return msg;
    }

    public static ChatMessage createUserLeft(int clientId, String username) {
        ChatMessage msg = new ChatMessage(MessageType.USER_LEFT.getValue());
        msg.setClientId(clientId);
        msg.setUsername(username);
        msg.setContent(username + " đã rời phòng chat");
        return msg;
    }

    public static ChatMessage createDisconnect() {
        return new ChatMessage(MessageType.DISCONNECT.getValue());
    }

    public String toJson() throws JsonProcessingException {
        return MAPPER.writeValueAsString(this) + "\n";
    }

    public static ChatMessage fromJson(String json) throws JsonProcessingException {
        return MAPPER.readValue(json.trim(), ChatMessage.class);
    }

    // Getters and Setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public Integer getClientId() {
        return clientId;
    }

    public void setClientId(Integer clientId) {
        this.clientId = clientId;
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "type='" + type + '\'' +
                ", username='" + username + '\'' +
                ", content='" + content + '\'' +
                ", success=" + success +
                ", clientId=" + clientId +
                '}';
    }
}
