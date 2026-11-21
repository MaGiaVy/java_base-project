package com.iot.broker;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pure-JDK MQTT v3.1 / v3.1.1 Broker ho tro dong thoi TCP (1883) va WebSocket RFC 6455 (9001).
 */
public class MqttBrokerServer {

    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int MAX_PACKET_BYTES = 16 * 1024 * 1024;

    public interface BrokerListener {
        void onLog(String time, String type, String clientId, String topic, String detail);
        void onClientsChanged(List<ClientInfo> clients);
        void onStatsChanged(int connectedClients, long totalMessages);
    }

    public static class ClientInfo {
        public final String clientId;
        public final String remoteAddress;
        public final String transport;
        public final String username;
        public final Map<String, Integer> subscriptions;

        public ClientInfo(String clientId, String remoteAddress, String transport, String username,
                          Map<String, Integer> subscriptions) {
            this.clientId = clientId;
            this.remoteAddress = remoteAddress;
            this.transport = transport;
            this.username = username;
            this.subscriptions = Collections.unmodifiableMap(new LinkedHashMap<>(subscriptions));
        }
    }

    private static class RetainedMessage {
        final String topic;
        final byte[] payload;
        final int qos;

        RetainedMessage(String topic, byte[] payload, int qos) {
            this.topic = topic;
            this.payload = payload;
            this.qos = qos;
        }
    }

    private static class ClientSession {
        final Socket socket;
        final OutputStream rawOut;
        final boolean webSocket;
        final String remoteAddress;
        final Object writeLock = new Object();
        final Map<String, Integer> subscriptions = new ConcurrentHashMap<>();
        final AtomicInteger nextPacketId = new AtomicInteger(1);

        volatile String clientId;
        volatile String username = "";
        volatile String willTopic;
        volatile byte[] willPayload;
        volatile int willQoS;
        volatile boolean willRetain;
        volatile boolean cleanDisconnect = false;
        volatile boolean replaced = false;

        ClientSession(Socket socket, OutputStream rawOut, boolean webSocket) {
            this.socket = socket;
            this.rawOut = rawOut;
            this.webSocket = webSocket;
            this.remoteAddress = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        }

        int allocatePacketId() {
            int id = nextPacketId.getAndIncrement() & 0xFFFF;
            if (id == 0) {
                id = nextPacketId.getAndIncrement() & 0xFFFF;
                if (id == 0) id = 1;
            }
            return id;
        }

        void sendMqttPacket(byte[] packet) throws IOException {
            synchronized (writeLock) {
                if (webSocket) {
                    writeWebSocketFrame(rawOut, 0x82, packet);
                } else {
                    rawOut.write(packet);
                    rawOut.flush();
                }
            }
        }

        void sendWsControlFrame(int firstByte, byte[] payload) throws IOException {
            synchronized (writeLock) {
                writeWebSocketFrame(rawOut, firstByte, payload);
            }
        }

        void closeQuietly() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private final int tcpPort;
    private final int wsPort;
    private final BrokerListener listener;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger autoClientCounter = new AtomicInteger(1);
    private final AtomicLong totalMessages = new AtomicLong(0);

    private final Map<String, ClientSession> clients = new ConcurrentHashMap<>();
    private final Map<String, RetainedMessage> retainedMessages = new ConcurrentHashMap<>();

    private volatile boolean requireAuth = false;
    private final Map<String, List<String>> allowedUsers = new ConcurrentHashMap<>();

    private ServerSocket tcpServerSocket;
    private ServerSocket wsServerSocket;
    private ExecutorService executor;

    // Quản lý cơ sở dữ liệu PostgreSQL (Tùy chọn: tự động ghi nhận cảm biến & đèn LED)
    private DatabaseManager dbManager = null;

    public void setDatabaseManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public DatabaseManager getDatabaseManager() {
        return this.dbManager;
    }

    public MqttBrokerServer() {
        this(1883, 9001, null);
    }

    public MqttBrokerServer(int tcpPort, int wsPort, BrokerListener listener) {
        this.tcpPort = tcpPort;
        this.wsPort = wsPort;
        this.listener = listener;
        this.allowedUsers.put("user1", Arrays.asList("123", "123456"));
    }

    public void setRequireAuth(boolean requireAuth) {
        this.requireAuth = requireAuth;
    }

    public boolean isRequireAuth() {
        return requireAuth;
    }

    public int getTcpPort() {
        return tcpPort;
    }

    public int getWsPort() {
        return wsPort;
    }

    public boolean isRunning() {
        return running.get();
    }

    public long getTotalMessages() {
        return totalMessages.get();
    }

    public int getClientCount() {
        return clients.size();
    }

    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }
        String tcpBindHost = "0.0.0.0";
        ServerSocket tcp = new ServerSocket();
        tcp.setReuseAddress(true);
        try {
            try {
                tcp.bind(new InetSocketAddress(tcpBindHost, tcpPort));
            } catch (BindException be) {
                try {
                    tcp.close();
                } catch (IOException ignored) {
                }
                tcp = new ServerSocket();
                tcp.setReuseAddress(true);
                tcpBindHost = "127.0.0.2";
                tcp.bind(new InetSocketAddress(tcpBindHost, tcpPort));
            }
            ServerSocket ws = new ServerSocket();
            ws.setReuseAddress(true);
            try {
                ws.bind(new InetSocketAddress("0.0.0.0", wsPort));
            } catch (IOException e) {
                try {
                    ws.close();
                } catch (IOException ignored) {
                }
                throw e;
            }
            this.tcpServerSocket = tcp;
            this.wsServerSocket = ws;
        } catch (IOException e) {
            try {
                tcp.close();
            } catch (IOException ignored) {
            }
            throw e;
        }

        this.executor = Executors.newCachedThreadPool();
        this.running.set(true);

        executor.submit(() -> acceptLoop(tcpServerSocket, false));
        executor.submit(() -> acceptLoop(wsServerSocket, true));

        emitLog("BROKER", "JavaBroker", "-",
                "Dang lang nghe TCP " + tcpBindHost + ":" + tcpPort + " & WebSocket:" + wsPort);
        emitStats();
    }

    public synchronized void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        try {
            if (tcpServerSocket != null) tcpServerSocket.close();
        } catch (IOException ignored) {
        }
        try {
            if (wsServerSocket != null) wsServerSocket.close();
        } catch (IOException ignored) {
        }
        for (ClientSession session : new ArrayList<>(clients.values())) {
            session.cleanDisconnect = true;
            session.closeQuietly();
        }
        clients.clear();
        if (executor != null) {
            executor.shutdownNow();
        }
        emitClients();
        emitStats();
        emitLog("BROKER", "JavaBroker", "-", "Broker da dung");
    }

    public void publishServerMessage(String topic, String payloadText, int qos, boolean retain) {
        byte[] payload = payloadText.getBytes(StandardCharsets.UTF_8);
        if (retain) {
            if (payload.length == 0) {
                retainedMessages.remove(topic);
            } else {
                retainedMessages.put(topic, new RetainedMessage(topic, payload, qos));
            }
        }
        totalMessages.incrementAndGet();
        routePublish(topic, payload, qos, false);
        emitStats();
        emitLog("PUBLISH", "JavaBroker", topic, payloadText);
    }

    private void acceptLoop(ServerSocket serverSocket, boolean webSocket) {
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setTcpNoDelay(true);
                executor.submit(() -> handleConnection(clientSocket, webSocket));
            } catch (SocketException se) {
                if (running.get()) {
                    emitLog("ERROR", "JavaBroker", "-", "Accept error (" + (webSocket ? "WS" : "TCP") + "): " + se.getMessage());
                }
                break;
            } catch (IOException e) {
                if (running.get()) {
                    emitLog("ERROR", "JavaBroker", "-", "Accept IO error: " + e.getMessage());
                }
            }
        }
    }

    private void handleConnection(Socket socket, boolean webSocket) {
        ClientSession session = null;
        try {
            socket.setSoTimeout(15000);
            InputStream rawIn = socket.getInputStream();
            OutputStream rawOut = socket.getOutputStream();
            InputStream mqttIn = rawIn;

            session = new ClientSession(socket, rawOut, webSocket);

            if (webSocket) {
                if (!performWebSocketHandshake(rawIn, rawOut)) {
                    return;
                }
                mqttIn = new WebSocketFrameInputStream(rawIn, session);
            }

            while (running.get()) {
                int fixedHeader = mqttIn.read();
                if (fixedHeader == -1) {
                    break;
                }
                int packetType = (fixedHeader >> 4) & 0x0F;
                int flags = fixedHeader & 0x0F;
                int remainingLength = readRemainingLength(mqttIn);
                if (remainingLength < 0 || remainingLength > MAX_PACKET_BYTES) {
                    break;
                }
                byte[] body = readFully(mqttIn, remainingLength);
                if (body == null) {
                    break;
                }

                if (packetType == 1) {
                    // CONNECT
                    if (!handleConnect(session, body)) {
                        break;
                    }
                } else if (session.clientId == null) {
                    // Must CONNECT first per MQTT spec
                    break;
                } else {
                    switch (packetType) {
                        case 3: // PUBLISH
                            handlePublish(session, flags, body);
                            break;
                        case 4: // PUBACK
                        case 5: // PUBREC
                        case 6: // PUBREL
                        case 7: // PUBCOMP
                            break;
                        case 8: // SUBSCRIBE
                            handleSubscribe(session, body);
                            break;
                        case 10: // UNSUBSCRIBE
                            handleUnsubscribe(session, body);
                            break;
                        case 12: // PINGREQ
                            session.sendMqttPacket(new byte[]{(byte) 0xD0, 0x00});
                            break;
                        case 14: // DISCONNECT
                            session.cleanDisconnect = true;
                            emitLog("DISCONNECT", session.clientId, "-", "Ngat ket noi sach (" + (webSocket ? "WS" : "TCP") + ")");
                            return;
                        default:
                            break;
                    }
                }
            }
        } catch (IOException ignored) {
        } finally {
            cleanupSession(session);
        }
    }

    private boolean performWebSocketHandshake(InputStream in, OutputStream out) throws IOException {
        ByteArrayOutputStream headerBytes = new ByteArrayOutputStream(512);
        int state = 0;
        while (headerBytes.size() < 16384) {
            int b = in.read();
            if (b == -1) {
                return false;
            }
            headerBytes.write(b);
            if (b == '\r' && (state == 0 || state == 2)) {
                state++;
            } else if (b == '\n' && (state == 1 || state == 3)) {
                state++;
                if (state == 4) {
                    break;
                }
            } else {
                state = (b == '\r') ? 1 : 0;
            }
        }
        if (state != 4) {
            return false;
        }

        String rawRequest = headerBytes.toString(StandardCharsets.UTF_8);
        String[] lines = rawRequest.split("\r\n");
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                String k = lines[i].substring(0, colon).trim().toLowerCase();
                String v = lines[i].substring(colon + 1).trim();
                headers.put(k, v);
            }
        }

        String wsKey = headers.get("sec-websocket-key");
        if (wsKey == null || wsKey.isEmpty()) {
            out.write("HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            return false;
        }

        String acceptKey;
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest((wsKey + WS_GUID).getBytes(StandardCharsets.UTF_8));
            acceptKey = Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IOException("SHA-1 unavailable", e);
        }

        StringBuilder response = new StringBuilder();
        response.append("HTTP/1.1 101 Switching Protocols\r\n");
        response.append("Upgrade: websocket\r\n");
        response.append("Connection: Upgrade\r\n");
        response.append("Sec-WebSocket-Accept: ").append(acceptKey).append("\r\n");

        // Trinh duyet (mqtt.js) bat buoc server phai echo dung subprotocol neu client gui
        String reqProto = headers.get("sec-websocket-protocol");
        if (reqProto != null && !reqProto.isEmpty()) {
            String firstProto = reqProto.split(",")[0].trim();
            if (!firstProto.isEmpty()) {
                response.append("Sec-WebSocket-Protocol: ").append(firstProto).append("\r\n");
            }
        }
        response.append("\r\n");

        out.write(response.toString().getBytes(StandardCharsets.US_ASCII));
        out.flush();
        return true;
    }

    private boolean handleConnect(ClientSession session, byte[] body) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(body);
        if (buf.remaining() < 4) return false;
        String protoName = readMqttString(buf);
        if (protoName == null || buf.remaining() < 4) return false;
        int protoLevel = buf.get() & 0xFF;
        if (!("MQTT".equals(protoName) && protoLevel == 4) && !("MQIsdp".equals(protoName) && protoLevel == 3)) {
            session.sendMqttPacket(new byte[]{0x20, 0x02, 0x00, 0x01}); // Unacceptable protocol version
            return false;
        }

        int connectFlags = buf.get() & 0xFF;
        int keepAlive = buf.getShort() & 0xFFFF;
        session.socket.setSoTimeout(keepAlive > 0 ? Math.max(15000, keepAlive * 1500) : 0);

        String cid = readMqttString(buf);
        if (cid == null) return false;
        if (cid.isEmpty()) {
            cid = "Client_" + autoClientCounter.getAndIncrement();
        }

        boolean willFlag = (connectFlags & 0x04) != 0;
        boolean willRetain = (connectFlags & 0x20) != 0;
        int willQoS = (connectFlags >> 3) & 0x03;
        boolean passwordFlag = (connectFlags & 0x40) != 0;
        boolean usernameFlag = (connectFlags & 0x80) != 0;

        String wTopic = null;
        byte[] wPayload = null;
        if (willFlag) {
            wTopic = readMqttString(buf);
            wPayload = readMqttBytes(buf);
            if (wTopic == null || wPayload == null) return false;
        }

        String user = "";
        String pass = "";
        if (usernameFlag) {
            user = readMqttString(buf);
            if (user == null) return false;
        }
        if (passwordFlag) {
            byte[] passBytes = readMqttBytes(buf);
            if (passBytes == null) return false;
            pass = new String(passBytes, StandardCharsets.UTF_8);
        }

        if (requireAuth) {
            List<String> validPasswords = allowedUsers.get(user);
            if (validPasswords == null || !validPasswords.contains(pass)) {
                session.sendMqttPacket(new byte[]{0x20, 0x02, 0x00, 0x04}); // Bad user/password
                emitLog("AUTH_FAIL", cid, "-", "Sai tai khoan: " + user);
                return false;
            }
        }

        session.clientId = cid;
        session.username = user;
        session.willTopic = wTopic;
        session.willPayload = wPayload;
        session.willQoS = willQoS;
        session.willRetain = willRetain;

        ClientSession previous = clients.put(cid, session);
        if (previous != null && previous != session) {
            previous.replaced = true;
            previous.closeQuietly();
        }

        session.sendMqttPacket(new byte[]{0x20, 0x02, 0x00, 0x00}); // Connection Accepted
        emitClients();
        emitStats();
        emitLog("CONNECT", cid, "-",
                (session.webSocket ? "WebSocket" : "TCP") + " | " + session.remoteAddress
                        + (user.isEmpty() ? "" : " | user=" + user));
        return true;
    }

    private void handlePublish(ClientSession session, int flags, byte[] body) throws IOException {
        int qos = (flags >> 1) & 0x03;
        boolean retain = (flags & 0x01) != 0;

        ByteBuffer buf = ByteBuffer.wrap(body);
        String topic = readMqttString(buf);
        if (topic == null) return;

        int packetId = 0;
        if (qos > 0) {
            if (buf.remaining() < 2) return;
            packetId = buf.getShort() & 0xFFFF;
        }

        byte[] payload = new byte[buf.remaining()];
        buf.get(payload);

        if (retain) {
            if (payload.length == 0) {
                retainedMessages.remove(topic);
            } else {
                retainedMessages.put(topic, new RetainedMessage(topic, payload, qos));
            }
        }

        totalMessages.incrementAndGet();
        routePublish(topic, payload, qos, false);

        // [TỰ ĐỘNG GHI NHẬN VÀO POSTGRESQL DATABASE]
        recordToDatabase(topic, payload, session.clientId);

        if (qos == 1) {
            session.sendMqttPacket(new byte[]{
                    0x40, 0x02, (byte) ((packetId >> 8) & 0xFF), (byte) (packetId & 0xFF)
            });
        }

        emitStats();

        String preview = new String(payload, StandardCharsets.UTF_8);
        if (preview.length() > 140) {
            preview = preview.substring(0, 140) + "...";
        }
        emitLog("PUBLISH", session.clientId, topic,
                preview + (retain ? " [RETAIN]" : "") + (qos > 0 ? " [QoS" + qos + "]" : ""));
    }

    /**
     * Tự động trích xuất gói tin JSON và lưu vào cơ sở dữ liệu PostgreSQL.
     * Phương thức này chạy ngầm (async) qua DatabaseManager nên không bao giờ làm nghẽn Broker.
     */
    private void recordToDatabase(String topic, byte[] payload, String clientId) {
        if (dbManager == null || !dbManager.isConnected() || payload == null || payload.length == 0) {
            return;
        }

        try {
            String content = new String(payload, StandardCharsets.UTF_8).trim();

            // 1. Trường hợp là bản tin dữ liệu cảm biến DHT22
            if ("esp32/sensor/dht22/data".equals(topic) || "sensor/data".equals(topic) || topic.contains("sensor")) {
                Double temp = extractJsonNumber(content, "temperature", "temp");
                Double hum = extractJsonNumber(content, "humidity", "hum");
                Double heatIndex = extractJsonNumber(content, "heat_index", "heatIndex");
                String deviceId = extractJsonString(content, "device_id", "deviceId", "clientId", "device");
                if (deviceId == null || deviceId.isEmpty()) {
                    deviceId = (clientId != null && !clientId.isEmpty()) ? clientId : "ESP32-DHT22";
                }

                if (temp != null && hum != null) {
                    if (heatIndex == null) {
                        heatIndex = temp; // Nếu gói tin không có heat_index thì tạm gán bằng temp
                    }
                    dbManager.saveSensorDataAsync(deviceId, temp, hum, heatIndex, topic);
                }
            }
            // 2. Trường hợp là bản tin điều khiển hoặc phản hồi trạng thái đèn LED
            else if ("esp32/actuator/led/control".equals(topic) || "esp32/actuator/led/status".equals(topic) || topic.contains("led")) {
                String cmd = extractJsonString(content, "command", "state", "status", "action");
                if (cmd == null || cmd.isEmpty()) {
                    if (content.equalsIgnoreCase("ON") || content.equalsIgnoreCase("OFF")) {
                        cmd = content.toUpperCase();
                    } else {
                        cmd = "TOGGLE";
                    }
                }
                String deviceId = "ESP32-LED";
                String triggeredBy = (clientId != null && !clientId.isEmpty()) ? clientId : "CLIENT";
                dbManager.saveActuatorLogAsync(deviceId, cmd, triggeredBy, topic);
            }
        } catch (Exception ignored) {
            // Không bao giờ để lỗi ngoại lệ làm ảnh hưởng tới việc định tuyến gói tin MQTT
        }
    }

    private static Double extractJsonNumber(String json, String... keys) {
        for (String key : keys) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*([0-9.-]+)");
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                try {
                    return Double.parseDouble(m.group(1));
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private static String extractJsonString(String json, String... keys) {
        for (String key : keys) {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"");
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
        }
        return null;
    }

    private void handleSubscribe(ClientSession session, byte[] body) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(body);
        if (buf.remaining() < 2) return;
        int packetId = buf.getShort() & 0xFFFF;

        List<String> addedFilters = new ArrayList<>();
        ByteArrayOutputStream grantCodes = new ByteArrayOutputStream();

        while (buf.hasRemaining()) {
            String filter = readMqttString(buf);
            if (filter == null || !buf.hasRemaining()) break;
            int reqQos = buf.get() & 0xFF;
            int granted = Math.min(reqQos, 1);
            session.subscriptions.put(filter, granted);
            addedFilters.add(filter);
            grantCodes.write(granted);
        }

        byte[] codes = grantCodes.toByteArray();
        ByteArrayOutputStream suback = new ByteArrayOutputStream(4 + codes.length);
        suback.write(0x90);
        encodeRemainingLength(suback, 2 + codes.length);
        suback.write((packetId >> 8) & 0xFF);
        suback.write(packetId & 0xFF);
        suback.write(codes);
        session.sendMqttPacket(suback.toByteArray());

        emitClients();
        emitLog("SUBSCRIBE", session.clientId, String.join(", ", addedFilters), "SUBACK OK");

        for (String filter : addedFilters) {
            int subQos = session.subscriptions.getOrDefault(filter, 0);
            for (RetainedMessage retained : retainedMessages.values()) {
                if (topicMatches(filter, retained.topic)) {
                    int outQos = Math.min(retained.qos, subQos);
                    deliverToClient(session, retained.topic, retained.payload, outQos, true);
                }
            }
        }
    }

    private void handleUnsubscribe(ClientSession session, byte[] body) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(body);
        if (buf.remaining() < 2) return;
        int packetId = buf.getShort() & 0xFFFF;

        List<String> removed = new ArrayList<>();
        while (buf.hasRemaining()) {
            String filter = readMqttString(buf);
            if (filter == null) break;
            session.subscriptions.remove(filter);
            removed.add(filter);
        }

        session.sendMqttPacket(new byte[]{
                (byte) 0xB0, 0x02, (byte) ((packetId >> 8) & 0xFF), (byte) (packetId & 0xFF)
        });
        emitClients();
        if (!removed.isEmpty()) {
            emitLog("UNSUBSCRIBE", session.clientId, String.join(", ", removed), "UNSUBACK OK");
        }
    }

    private void cleanupSession(ClientSession session) {
        if (session == null) return;
        session.closeQuietly();
        if (session.clientId == null) return;

        clients.remove(session.clientId, session);
        if (!session.replaced) {
            if (!session.cleanDisconnect && session.willTopic != null && session.willPayload != null) {
                if (session.willRetain) {
                    retainedMessages.put(session.willTopic,
                            new RetainedMessage(session.willTopic, session.willPayload, session.willQoS));
                }
                routePublish(session.willTopic, session.willPayload, session.willQoS, false);
                emitLog("LWT", session.clientId, session.willTopic,
                        new String(session.willPayload, StandardCharsets.UTF_8) + " (mat ket noi dot ngot)");
            } else if (!session.cleanDisconnect) {
                emitLog("DISCONNECT", session.clientId, "-", "Mat ket noi");
            }
            emitClients();
            emitStats();
        }
    }

    private void routePublish(String topic, byte[] payload, int pubQos, boolean retainFlag) {
        for (ClientSession target : clients.values()) {
            int matchedQos = -1;
            for (Map.Entry<String, Integer> entry : target.subscriptions.entrySet()) {
                if (topicMatches(entry.getKey(), topic)) {
                    matchedQos = Math.max(matchedQos, entry.getValue());
                }
            }
            if (matchedQos >= 0) {
                int outQos = Math.min(pubQos, matchedQos);
                deliverToClient(target, topic, payload, outQos, retainFlag);
            }
        }
    }

    private void deliverToClient(ClientSession target, String topic, byte[] payload, int qos, boolean retainFlag) {
        try {
            byte[] packet = buildPublishPacket(topic, payload, qos, retainFlag,
                    qos > 0 ? target.allocatePacketId() : 0);
            target.sendMqttPacket(packet);
        } catch (IOException e) {
            target.closeQuietly();
        }
    }

    private static byte[] buildPublishPacket(String topic, byte[] payload, int qos, boolean retain, int packetId) {
        byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
        int remaining = 2 + topicBytes.length + (qos > 0 ? 2 : 0) + payload.length;

        ByteArrayOutputStream out = new ByteArrayOutputStream(5 + remaining);
        int fixed = 0x30 | ((qos & 0x03) << 1) | (retain ? 0x01 : 0x00);
        out.write(fixed);
        encodeRemainingLength(out, remaining);
        out.write((topicBytes.length >> 8) & 0xFF);
        out.write(topicBytes.length & 0xFF);
        out.writeBytes(topicBytes);
        if (qos > 0) {
            out.write((packetId >> 8) & 0xFF);
            out.write(packetId & 0xFF);
        }
        out.writeBytes(payload);
        return out.toByteArray();
    }

    public static boolean topicMatches(String filter, String topic) {
        if (filter == null || topic == null) return false;
        if (filter.equals(topic)) return true;
        String[] f = filter.split("/", -1);
        String[] t = topic.split("/", -1);
        for (int i = 0; i < f.length; i++) {
            String fp = f[i];
            if ("#".equals(fp)) {
                return i == f.length - 1;
            }
            if (i >= t.length) {
                return false;
            }
            if (!"+".equals(fp) && !fp.equals(t[i])) {
                return false;
            }
        }
        return f.length == t.length;
    }

    private void emitLog(String type, String clientId, String topic, String detail) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        System.out.println("[" + time + "] [" + type + "] " + clientId + " | " + topic + " -> " + detail);
        if (listener != null) {
            listener.onLog(time, type, clientId, topic, detail);
        }
    }

    private void emitClients() {
        if (listener == null) return;
        List<ClientInfo> snapshot = new ArrayList<>();
        for (ClientSession s : clients.values()) {
            snapshot.add(new ClientInfo(
                    s.clientId,
                    s.remoteAddress,
                    s.webSocket ? "WebSocket" : "TCP",
                    s.username,
                    s.subscriptions));
        }
        listener.onClientsChanged(snapshot);
    }

    private void emitStats() {
        if (listener != null) {
            listener.onStatsChanged(clients.size(), totalMessages.get());
        }
    }

    private static int readRemainingLength(InputStream in) throws IOException {
        int multiplier = 1;
        int value = 0;
        int encoded;
        do {
            encoded = in.read();
            if (encoded < 0) {
                throw new EOFException("Unexpected EOF reading Remaining Length");
            }
            value += (encoded & 127) * multiplier;
            multiplier *= 128;
            if (multiplier > 128 * 128 * 128 * 128) {
                throw new IOException("Malformed Remaining Length");
            }
        } while ((encoded & 128) != 0);
        return value;
    }

    private static void encodeRemainingLength(ByteArrayOutputStream out, int len) {
        do {
            int digit = len % 128;
            len /= 128;
            if (len > 0) digit |= 0x80;
            out.write(digit);
        } while (len > 0);
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        if (length == 0) return new byte[0];
        byte[] buf = new byte[length];
        int off = 0;
        while (off < length) {
            int n = in.read(buf, off, length - off);
            if (n < 0) return null;
            off += n;
        }
        return buf;
    }

    private static String readMqttString(ByteBuffer buf) {
        byte[] bytes = readMqttBytes(buf);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] readMqttBytes(ByteBuffer buf) {
        if (buf.remaining() < 2) return null;
        int len = buf.getShort() & 0xFFFF;
        if (buf.remaining() < len) return null;
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return bytes;
    }

    private static void writeWebSocketFrame(OutputStream out, int firstByte, byte[] payload) throws IOException {
        int len = payload.length;
        out.write(firstByte);
        if (len < 126) {
            out.write(len);
        } else if (len <= 65535) {
            out.write(126);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
        } else {
            out.write(127);
            long l = len;
            for (int shift = 56; shift >= 0; shift -= 8) {
                out.write((int) ((l >> shift) & 0xFF));
            }
        }
        if (len > 0) {
            out.write(payload);
        }
        out.flush();
    }

    /**
     * Giai ma luong khung nhị phan WebSocket RFC 6455 thanh InputStream byte MQTT lien tuc.
     */
    private static class WebSocketFrameInputStream extends InputStream {
        private final InputStream raw;
        private final ClientSession session;
        private byte[] frameBuf = new byte[0];
        private int framePos = 0;

        WebSocketFrameInputStream(InputStream raw, ClientSession session) {
            this.raw = raw;
            this.session = session;
        }

        @Override
        public int read() throws IOException {
            if (!ensureFrame()) return -1;
            return frameBuf[framePos++] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) return 0;
            if (!ensureFrame()) return -1;
            int avail = Math.min(len, frameBuf.length - framePos);
            System.arraycopy(frameBuf, framePos, b, off, avail);
            framePos += avail;
            return avail;
        }

        private boolean ensureFrame() throws IOException {
            while (framePos >= frameBuf.length) {
                if (!readNextDataFrame()) {
                    return false;
                }
            }
            return true;
        }

        private boolean readNextDataFrame() throws IOException {
            while (true) {
                int b0 = raw.read();
                if (b0 < 0) return false;
                int b1 = raw.read();
                if (b1 < 0) return false;

                int opcode = b0 & 0x0F;
                boolean masked = (b1 & 0x80) != 0;
                long len = b1 & 0x7F;

                if (len == 126) {
                    byte[] ext = readFully(raw, 2);
                    if (ext == null) return false;
                    len = ((ext[0] & 0xFF) << 8) | (ext[1] & 0xFF);
                } else if (len == 127) {
                    byte[] ext = readFully(raw, 8);
                    if (ext == null) return false;
                    len = 0;
                    for (int i = 0; i < 8; i++) {
                        len = (len << 8) | (ext[i] & 0xFF);
                    }
                }
                if (len < 0 || len > MAX_PACKET_BYTES) {
                    throw new IOException("WebSocket frame too large: " + len);
                }

                byte[] mask = null;
                if (masked) {
                    mask = readFully(raw, 4);
                    if (mask == null) return false;
                }

                byte[] payload = readFully(raw, (int) len);
                if (payload == null) return false;
                if (masked) {
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] ^= mask[i & 3];
                    }
                }

                if (opcode == 0x8) {
                    try {
                        session.sendWsControlFrame(0x88, new byte[0]);
                    } catch (IOException ignored) {
                    }
                    return false;
                } else if (opcode == 0x9) {
                    session.sendWsControlFrame(0x8A, payload);
                } else if (opcode == 0xA) {
                    // Pong frame: bo qua va doc khung tiep theo
                } else if (opcode == 0x2 || opcode == 0x0 || opcode == 0x1) {
                    if (payload.length > 0) {
                        frameBuf = payload;
                        framePos = 0;
                        return true;
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        int tcpPort = 1883;
        int wsPort = 9001;
        boolean auth = false;
        for (int i = 0; i < args.length; i++) {
            if ("--tcp".equals(args[i]) && i + 1 < args.length) {
                tcpPort = Integer.parseInt(args[++i]);
            } else if ("--ws".equals(args[i]) && i + 1 < args.length) {
                wsPort = Integer.parseInt(args[++i]);
            } else if ("--auth".equals(args[i])) {
                auth = true;
            }
        }

        MqttBrokerServer server = new MqttBrokerServer(tcpPort, wsPort, null);
        if (auth) {
            server.setRequireAuth(true);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
        System.out.println("Java MQTT Broker dang chay (TCP=" + tcpPort + ", WS=" + wsPort + (auth ? ", AUTH=true" : "") + "). Nhan Ctrl+C de dung.");

        Object lock = new Object();
        synchronized (lock) {
            while (server.isRunning()) {
                lock.wait(1000);
            }
        }
    }
}
