import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ============================================================================
 * JAVA CHAT SERVER & HTTP BACKEND + UDP MULTICAST HUB (CHUẨN ĐA NHÓM)
 * ============================================================================
 * 1. Phân biệt rõ ràng 2 phạm vi gửi/nhận tin:
 *    - KÊNH CHAT CHUNG (TCP & Web): Tất cả các client đều nghe và nói được với nhau.
 *    - KÊNH NHÓM MULTICAST (UDP Lớp D 239.x.x.x): CHỈ AI ĐÃ THAM GIA NHÓM mới nhận
 *      được tin nhắn nhóm. Người từ chối tham gia nhóm (như Hùng) sẽ KHÔNG nhận được!
 * 2. Đổi nhóm / Rời nhóm linh hoạt cả trên Web lẫn TCP Client.
 * 3. Tự động nhận diện card Mobile Hotspot (192.168.137.x) qua NetworkHelper.
 * ============================================================================
 */
public class ChatAndWebServer {

    public static final int TCP_PORT = 5000;
    public static final int HTTP_PORT = 8080;
    public static final int MULTICAST_PORT = 8000;

    private static final AtomicInteger tcpClientIdCounter = new AtomicInteger(0);
    private static final AtomicInteger webClientIdCounter = new AtomicInteger(100);
    private static final AtomicLong messageIdCounter = new AtomicLong(0);

    // Danh sách TCP Clients
    private static final ConcurrentHashMap<Integer, TcpClientHandler> tcpClients = new ConcurrentHashMap<>();

    // Danh sách Web Clients
    public static class WebClientInfo {
        public int id;
        public String nickname;
        public volatile boolean joinedMulticast;
        public volatile String multicastGroup;

        public WebClientInfo(int id, String nickname, boolean joinedMulticast, String multicastGroup) {
            this.id = id;
            this.nickname = nickname;
            this.joinedMulticast = joinedMulticast;
            this.multicastGroup = multicastGroup != null ? multicastGroup : "239.1.1.1";
        }
    }
    private static final ConcurrentHashMap<Integer, WebClientInfo> webClients = new ConcurrentHashMap<>();

    // Hàng đợi tin nhắn toàn hệ thống (Phân biệt rõ ràng GENERAL vs MULTICAST)
    public static class ChatMessage {
        public long id;
        public String sender;
        public String text;
        public String type; // "SYSTEM", "GENERAL", "MULTICAST"
        public String targetGroup; // null nếu là GENERAL/SYSTEM, hoặc IP nhóm ví dụ "239.1.1.1"
        public String time;

        public ChatMessage(long id, String sender, String text, String type, String targetGroup) {
            this.id = id;
            this.sender = sender;
            this.text = text;
            this.type = type;
            this.targetGroup = targetGroup;
            this.time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        }
    }
    private static final List<ChatMessage> chatHistory = new CopyOnWriteArrayList<>();
    private static final int MAX_HISTORY = 120;

    // Quản lý Multicast Socket trên Server
    private static volatile boolean isMulticastJoined = false;
    private static volatile String currentMulticastGroup = "239.1.1.1";
    private static MulticastSocket multicastSocket = null;
    private static InetSocketAddress multicastGroupAddr = null;
    private static NetworkHelper.CardInfo multicastCard = null;
    private static Thread multicastReceiveThread = null;

    private static final LocalDateTime startTime = LocalDateTime.now();

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("   🚀 KHỞI ĐỘNG HỆ THỐNG: TCP + HTTP + MULTICAST CHAT HUB");
        System.out.println("==================================================================");

        new Thread(ChatAndWebServer::startTcpServer, "TCP-Server-Thread").start();
        new Thread(ChatAndWebServer::startHttpServer, "HTTP-Server-Thread").start();

        // Khởi động Multicast mặc định với card Hotspot/LAN
        initDefaultMulticast();

        System.out.println("==================================================================");
        System.out.println("[INFO] TCP Chat Server        : Port " + TCP_PORT);
        System.out.println("[INFO] HTTP Web Dashboard     : http://localhost:" + HTTP_PORT + "/");
        System.out.println("[INFO] Web Chat Client        : http://localhost:" + HTTP_PORT + "/chat");
        System.out.println("[INFO] UDP Multicast Port     : " + MULTICAST_PORT + " (Nhóm: " + currentMulticastGroup + ")");
        System.out.println("==================================================================\n");
    }

    // ========================================================================
    // MODULE 1: TCP CHAT SERVER (Chương 11)
    // ========================================================================
    private static void startTcpServer() {
        ExecutorService clientThreadPool = Executors.newCachedThreadPool();

        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
            System.out.println("✅ [TCP Server] Đang lắng nghe kết nối tại port " + TCP_PORT + "...");

            while (true) {
                Socket socket = serverSocket.accept();
                int clientId = tcpClientIdCounter.incrementAndGet();

                TcpClientHandler handler = new TcpClientHandler(socket, clientId);
                tcpClients.put(clientId, handler);

                System.out.println("🟢 [TCP Connect] Client #" + clientId + " kết nối từ " +
                        socket.getInetAddress().getHostAddress() + ":" + socket.getPort() +
                        " (TCP Online: " + tcpClients.size() + ")");

                clientThreadPool.execute(handler);
            }
        } catch (IOException e) {
            System.err.println("❌ [TCP Server Lỗi]: " + e.getMessage());
        } finally {
            clientThreadPool.shutdown();
        }
    }

    public static void removeTcpClient(int clientId) {
        TcpClientHandler handler = tcpClients.remove(clientId);
        if (handler != null) {
            handler.closeQuietly();
            String leaveMsg = "Client #" + clientId + " (TCP) đã rời phòng.";
            System.out.println("🔴 [TCP Disconnect] " + leaveMsg);
            broadcastSystemMessage(leaveMsg);
        }
    }

    static class TcpClientHandler implements Runnable {
        private final Socket socket;
        private final int clientId;
        private BufferedReader reader;
        private BufferedWriter writer;
        private volatile boolean isRunning = true;
        // Nhóm multicast mà client này tham gia (null nếu chưa tham gia)
        private volatile String joinedMulticastGroup = "239.1.1.1";

        public TcpClientHandler(Socket socket, int clientId) throws IOException {
            this.socket = socket;
            this.clientId = clientId;
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        public String getRemoteAddress() {
            return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        }

        public String getJoinedMulticastGroup() {
            return joinedMulticastGroup;
        }

        public synchronized void sendMessage(String msg) throws IOException {
            if (!isRunning) return;
            writer.write(msg);
            writer.newLine();
            writer.flush();
        }

        @Override
        public void run() {
            try {
                sendMessage("=================================================");
                sendMessage("🎉 CHÀO MỪNG BẠN ĐẾN VỚI PHÒNG CHAT JAVA!");
                sendMessage("👉 ID của bạn là: #" + clientId + " (TCP Client)");
                sendMessage("👉 Chat Chung       : Gõ văn bản rồi nhấn Enter.");
                sendMessage("👉 Chat Nhóm Mcast  : Gõ /mcast <nội dung>");
                sendMessage("👉 Đổi nhóm Mcast   : Gõ /join <IP_nhóm> (VD: /join 239.1.1.2)");
                sendMessage("👉 Rời nhóm Mcast   : Gõ /leave");
                sendMessage("👉 Thoát            : Gõ /quit");
                sendMessage("=================================================");

                broadcastSystemMessage("Client #" + clientId + " (TCP) đã tham gia phòng chat.");

                String line;
                while (isRunning && (line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    if (line.equalsIgnoreCase("/quit") || line.equalsIgnoreCase("exit")) {
                        sendMessage("[Hệ thống] Tạm biệt bạn!");
                        break;
                    }

                    // Lệnh tham gia nhóm Multicast
                    if (line.toLowerCase().startsWith("/join ")) {
                        String target = line.substring(6).trim();
                        if (target.isEmpty()) target = "239.1.1.1";
                        joinedMulticastGroup = target;
                        sendMessage("[Hệ thống] ✅ Bạn đã THAM GIA nhóm Multicast: " + target);
                        continue;
                    }

                    // Lệnh rời nhóm Multicast
                    if (line.equalsIgnoreCase("/leave")) {
                        joinedMulticastGroup = null;
                        sendMessage("[Hệ thống] ⏹ Bạn đã RỜI KHỎI nhóm Multicast.");
                        continue;
                    }

                    // Lệnh gửi tin vào nhóm Multicast
                    if (line.toLowerCase().startsWith("/mcast ")) {
                        if (joinedMulticastGroup == null) {
                            sendMessage("[Hệ thống] ⚠️ Bạn chưa tham gia nhóm Multicast nào! Hãy gõ /join 239.1.1.1 trước.");
                            continue;
                        }
                        String mcastContent = line.substring(7).trim();
                        broadcastMulticastMessage("TCP Client #" + clientId, mcastContent, joinedMulticastGroup);
                        continue;
                    }

                    // Tin nhắn gửi vào Chat Chung (Phòng chung cho tất cả mọi người)
                    broadcastGeneralMessage("TCP Client #" + clientId, line);
                }
            } catch (IOException ignored) {
            } finally {
                isRunning = false;
                removeTcpClient(clientId);
            }
        }

        public void closeQuietly() {
            isRunning = false;
            try {
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ignored) {}
        }
    }

    // ========================================================================
    // MODULE 2: UDP MULTICAST HUB (Kế thừa từ BroadcastMulticast)
    // ========================================================================
    private static void initDefaultMulticast() {
        try {
            multicastCard = NetworkHelper.pickCard(null);
            System.out.println("📡 [Multicast Card] Tự động chọn: " + multicastCard.displayName + " (" + multicastCard.ip + ")"
                    + (multicastCard.isHotspot ? " ★ [HOTSPOT]" : ""));
            joinMulticastGroup("239.1.1.1", MULTICAST_PORT, null);
        } catch (Exception e) {
            System.err.println("⚠️ [Multicast Init Cảnh báo]: " + e.getMessage());
        }
    }

    public static synchronized boolean joinMulticastGroup(String groupIp, int port, String preferredCardIp) {
        try {
            leaveMulticastGroup();

            if (preferredCardIp != null && !preferredCardIp.isEmpty()) {
                multicastCard = NetworkHelper.pickCard(preferredCardIp);
            } else if (multicastCard == null) {
                multicastCard = NetworkHelper.pickCard(null);
            }

            InetAddress group = InetAddress.getByName(groupIp);
            multicastGroupAddr = new InetSocketAddress(group, port);

            multicastSocket = new MulticastSocket(port);
            multicastSocket.setReuseAddress(true);
            multicastSocket.setNetworkInterface(multicastCard.nif);
            multicastSocket.setTimeToLive(1); // Chỉ trong LAN/Hotspot
            multicastSocket.joinGroup(multicastGroupAddr, multicastCard.nif);

            isMulticastJoined = true;
            currentMulticastGroup = groupIp;

            // Luồng nhận tin Multicast UDP từ mạng LAN/Hotspot
            multicastReceiveThread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                while (isMulticastJoined && multicastSocket != null && !multicastSocket.isClosed()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        multicastSocket.receive(packet);
                        String raw = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                        String senderIp = packet.getAddress().getHostAddress();

                        // Bỏ qua tin nhắn do chính server phát ra để tránh vòng lặp
                        if (raw.startsWith("[SERVER_ORIGIN]")) continue;

                        String senderLabel = "Multicast Peer (" + senderIp + ")";
                        String content = raw;
                        if (raw.contains(": ")) {
                            int colon = raw.indexOf(": ");
                            senderLabel = raw.substring(0, colon);
                            content = raw.substring(colon + 2);
                        }

                        // Nhận được tin Multicast -> CHỈ gửi cho những ai cùng ở nhóm này!
                        broadcastMulticastMessage(senderLabel, content, groupIp);

                    } catch (IOException e) {
                        break;
                    }
                }
            }, "Multicast-Receive-Thread");
            multicastReceiveThread.setDaemon(true);
            multicastReceiveThread.start();

            System.out.println("✅ [Multicast] Đã THAM GIA nhóm: " + groupIp + ":" + port + " trên card: " + multicastCard.displayName);
            return true;
        } catch (Exception e) {
            System.err.println("❌ [Multicast Join Lỗi]: " + e.getMessage());
            isMulticastJoined = false;
            return false;
        }
    }

    public static synchronized boolean leaveMulticastGroup() {
        try {
            if (multicastSocket != null && !multicastSocket.isClosed()) {
                if (multicastGroupAddr != null && multicastCard != null && multicastCard.nif != null) {
                    try { multicastSocket.leaveGroup(multicastGroupAddr, multicastCard.nif); } catch (Exception ignored) {}
                }
                multicastSocket.close();
            }
            isMulticastJoined = false;
            if (multicastReceiveThread != null) multicastReceiveThread.interrupt();
            System.out.println("⏹ [Multicast] Đã RỜI khỏi nhóm.");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static synchronized void sendMulticast(String groupIp, int port, String text) throws IOException {
        if (multicastSocket == null || multicastSocket.isClosed()) {
            multicastSocket = new MulticastSocket();
            if (multicastCard != null) multicastSocket.setNetworkInterface(multicastCard.nif);
            multicastSocket.setTimeToLive(1);
        }
        InetAddress group = InetAddress.getByName(groupIp);
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        multicastSocket.send(new DatagramPacket(data, data.length, group, port));
    }

    // ========================================================================
    // CÁC HÀM PHÁT TIN NHẮN (PHÂN BIỆT RẠCH RÒI PHÒNG CHUNG vs NHÓM MULTICAST)
    // ========================================================================

    /**
     * 1. GỬI TIN NHẮN PHÒNG CHUNG (GENERAL):
     * Tất cả mọi người (kể cả Hùng không vào Multicast) đều nhận được!
     */
    public static void broadcastGeneralMessage(String senderName, String message) {
        long id = messageIdCounter.incrementAndGet();
        ChatMessage msg = new ChatMessage(id, senderName, message, "GENERAL", null);
        addMessageToHistory(msg);

        // Gửi tới tất cả TCP clients
        String line = "[" + senderName + "]: " + message;
        for (TcpClientHandler h : tcpClients.values()) {
            try { h.sendMessage(line); } catch (IOException ignored) {}
        }
    }

    /**
     * 2. GỬI TIN NHẮN VÀO NHÓM MULTICAST:
     * CHỈ CÓ NHỮNG CLIENT ĐÃ THAM GIA ĐÚNG NHÓM NÀY MỚI NHẬN ĐƯỢC!
     * Người từ chối (như Hùng) hoặc ở nhóm khác sẽ TUYỆT ĐỐI KHÔNG THẤY!
     */
    public static void broadcastMulticastMessage(String senderName, String message, String groupIp) {
        long id = messageIdCounter.incrementAndGet();
        ChatMessage msg = new ChatMessage(id, senderName, message, "MULTICAST", groupIp);
        addMessageToHistory(msg);

        // Bắn gói UDP Multicast ra mạng LAN/Hotspot
        try {
            String packetData = "[SERVER_ORIGIN] " + senderName + ": " + message;
            sendMulticast(groupIp, MULTICAST_PORT, packetData);
        } catch (Exception ignored) {}

        // Gửi tới các TCP clients ĐÃ THAM GIA đúng nhóm này
        String line = "[MULTICAST @" + groupIp + "] " + senderName + ": " + message;
        for (TcpClientHandler h : tcpClients.values()) {
            if (groupIp.equals(h.getJoinedMulticastGroup())) {
                try { h.sendMessage(line); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * 3. GỬI THÔNG BÁO HỆ THỐNG / TOÀN BỘ SERVER (SYSTEM BROADCAST):
     * Phát tới tất cả mọi người.
     */
    public static void broadcastSystemMessage(String text) {
        long id = messageIdCounter.incrementAndGet();
        ChatMessage msg = new ChatMessage(id, "Hệ Thống", text, "SYSTEM", null);
        addMessageToHistory(msg);

        String line = "[Hệ thống] " + text;
        for (TcpClientHandler h : tcpClients.values()) {
            try { h.sendMessage(line); } catch (IOException ignored) {}
        }
    }

    private static void addMessageToHistory(ChatMessage msg) {
        chatHistory.add(msg);
        if (chatHistory.size() > MAX_HISTORY) {
            chatHistory.remove(0);
        }
        System.out.println("💬 [" + msg.type + (msg.targetGroup != null ? " @" + msg.targetGroup : "") + "] " + msg.sender + ": " + msg.text);
    }

    // ========================================================================
    // MODULE 3: HTTP SERVER & REST API (Chương 12)
    // ========================================================================
    private static void startHttpServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);

            server.createContext("/", new AdminDashboardHandler());
            server.createContext("/chat", new WebChatClientHandler());

            server.createContext("/api/status", new ApiStatusHandler());
            server.createContext("/api/users", new ApiUsersHandler());
            server.createContext("/api/broadcast", new ApiBroadcastHandler());
            server.createContext("/api/multicast/status", new ApiMulticastStatusHandler());
            server.createContext("/api/multicast/join", new ApiMulticastJoinHandler());
            server.createContext("/api/multicast/leave", new ApiMulticastLeaveHandler());
            server.createContext("/api/multicast/send", new ApiMulticastSendHandler());
            server.createContext("/api/chat/messages", new ApiChatMessagesHandler());
            server.createContext("/api/chat/send", new ApiChatSendHandler());
            server.createContext("/api/webclient/register", new ApiWebClientRegisterHandler());
            server.createContext("/api/webclient/update-group", new ApiWebClientUpdateGroupHandler());
            server.createContext("/api/network/cards", new ApiNetworkCardsHandler());

            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

            System.out.println("✅ [HTTP Server] Đang chạy tại http://localhost:" + HTTP_PORT + "/");
        } catch (IOException e) {
            System.err.println("❌ [HTTP Server Lỗi]: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // HTTP HANDLERS
    // ------------------------------------------------------------------------

    static class AdminDashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            if (!"/".equals(exchange.getRequestURI().getPath())) {
                sendJsonResponse(exchange, 404, "{\"error\":\"Not Found\"}"); return;
            }
            sendHtmlResponse(exchange, 200, renderAdminDashboardHtml());
        }
    }

    static class WebChatClientHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            sendHtmlResponse(exchange, 200, renderWebChatClientHtml());
        }
    }

    static class ApiStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            String json = "{\n" +
                    "  \"status\": \"OK\",\n" +
                    "  \"message\": \"Server đang hoạt động tốt!\",\n" +
                    "  \"tcp_port\": " + TCP_PORT + ",\n" +
                    "  \"http_port\": " + HTTP_PORT + ",\n" +
                    "  \"multicast_port\": " + MULTICAST_PORT + ",\n" +
                    "  \"multicast_joined\": " + isMulticastJoined + ",\n" +
                    "  \"multicast_group\": \"" + currentMulticastGroup + "\",\n" +
                    "  \"network_card\": \"" + (multicastCard != null ? multicastCard.displayName + " (" + multicastCard.ip + ")" : "Auto") + "\",\n" +
                    "  \"tcp_online\": " + tcpClients.size() + ",\n" +
                    "  \"web_online\": " + webClients.size() + "\n" +
                    "}";
            sendJsonResponse(exchange, 200, json);
        }
    }

    static class ApiUsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            StringBuilder json = new StringBuilder("{\n");
            json.append("  \"status\": \"OK\",\n");
            json.append("  \"total_online\": ").append(tcpClients.size() + webClients.size()).append(",\n");
            json.append("  \"tcp_count\": ").append(tcpClients.size()).append(",\n");
            json.append("  \"web_count\": ").append(webClients.size()).append(",\n");

            json.append("  \"tcp_users\": [");
            List<Integer> tcpIds = new ArrayList<>(tcpClients.keySet());
            Collections.sort(tcpIds);
            for (int i = 0; i < tcpIds.size(); i++) {
                int id = tcpIds.get(i);
                TcpClientHandler h = tcpClients.get(id);
                json.append("{\"id\": ").append(id)
                    .append(", \"address\": \"").append(h != null ? h.getRemoteAddress() : "unknown").append("\"")
                    .append(", \"mcast_group\": \"").append(h != null ? h.getJoinedMulticastGroup() : "null").append("\"}");
                if (i < tcpIds.size() - 1) json.append(", ");
            }
            json.append("],\n");

            json.append("  \"web_users\": [");
            List<WebClientInfo> wList = new ArrayList<>(webClients.values());
            for (int i = 0; i < wList.size(); i++) {
                WebClientInfo w = wList.get(i);
                json.append("{\"id\": ").append(w.id)
                    .append(", \"nickname\": \"").append(escapeJson(w.nickname)).append("\"")
                    .append(", \"joined_mcast\": ").append(w.joinedMulticast)
                    .append(", \"group\": \"").append(w.multicastGroup).append("\"}");
                if (i < wList.size() - 1) json.append(", ");
            }
            json.append("]\n}");
            sendJsonResponse(exchange, 200, json.toString());
        }
    }

    static class ApiBroadcastHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\": \"Use POST\"}"); return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
            String msg = extractJsonStringField(body, "message");
            if (msg == null || msg.isEmpty()) msg = extractJsonStringField(body, "text");
            if (msg == null || msg.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"error\": \"message field is required\"}"); return;
            }

            broadcastSystemMessage("[THÔNG BÁO TỪ WEB HTTP]: " + msg);
            sendJsonResponse(exchange, 201, "{\"status\": \"Created\", \"message\": \"Đã broadcast thành công\", \"recipients\": " + (tcpClients.size() + webClients.size()) + "}");
        }
    }

    static class ApiMulticastStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            String cardDesc = multicastCard != null ? multicastCard.displayName + " (" + multicastCard.ip + ")" : "N/A";
            String json = "{\n" +
                    "  \"joined\": " + isMulticastJoined + ",\n" +
                    "  \"group\": \"" + currentMulticastGroup + "\",\n" +
                    "  \"port\": " + MULTICAST_PORT + ",\n" +
                    "  \"card\": \"" + escapeJson(cardDesc) + "\",\n" +
                    "  \"is_hotspot\": " + (multicastCard != null && multicastCard.isHotspot) + "\n" +
                    "}";
            sendJsonResponse(exchange, 200, json);
        }
    }

    static class ApiMulticastJoinHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\": \"Use POST\"}"); return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
            String group = extractJsonStringField(body, "group");
            String cardIp = extractJsonStringField(body, "cardIp");
            if (group == null || group.isEmpty()) group = "239.1.1.1";

            boolean ok = joinMulticastGroup(group, MULTICAST_PORT, cardIp);
            if (ok) {
                broadcastSystemMessage("Hệ thống đã THAM GIA nhóm Multicast: " + group);
                sendJsonResponse(exchange, 200, "{\"status\": \"OK\", \"message\": \"Đã tham gia nhóm " + group + "\", \"group\": \"" + group + "\"}");
            } else {
                sendJsonResponse(exchange, 500, "{\"error\": \"Không thể tham gia nhóm Multicast! Kiểm tra địa chỉ lớp D 224-239.x.x.x\"}");
            }
        }
    }

    static class ApiMulticastLeaveHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            leaveMulticastGroup();
            broadcastSystemMessage("Hệ thống đã RỜI KHỎI nhóm Multicast.");
            sendJsonResponse(exchange, 200, "{\"status\": \"OK\", \"message\": \"Đã rời khỏi nhóm Multicast\"}");
        }
    }

    static class ApiMulticastSendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
            String text = extractJsonStringField(body, "message");
            String sender = extractJsonStringField(body, "sender");
            String group = extractJsonStringField(body, "group");
            if (sender == null || sender.isEmpty()) sender = "Admin Dashboard";
            if (group == null || group.isEmpty()) group = currentMulticastGroup;

            if (text == null || text.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"error\": \"message is required\"}"); return;
            }

            broadcastMulticastMessage(sender, text, group);
            sendJsonResponse(exchange, 200, "{\"status\": \"Sent\", \"message\": \"Đã phát Multicast thành công vào nhóm " + group + "\"}");
        }
    }

    /**
     * LẤY TIN NHẮN (CÓ LỌC THEO NHÓM MULTICAST):
     * - Nếu tin là MULTICAST: CHỈ TRẢ VỀ CHO CLIENT ĐÃ THAM GIA ĐÚNG NHÓM ĐÓ!
     * - Người từ chối (như Hùng) hoặc ở nhóm khác sẽ KHÔNG NHẬN ĐƯỢC!
     */
    static class ApiChatMessagesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            long since = 0;
            Integer clientId = null;

            String query = exchange.getRequestURI().getQuery();
            if (query != null) {
                for (String part : query.split("&")) {
                    if (part.startsWith("since=")) {
                        try { since = Long.parseLong(part.substring(6)); } catch (Exception ignored) {}
                    } else if (part.startsWith("clientId=")) {
                        try { clientId = Integer.parseInt(part.substring(9)); } catch (Exception ignored) {}
                    }
                }
            }

            // Lấy thông tin của client này (nếu có)
            WebClientInfo client = clientId != null ? webClients.get(clientId) : null;

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            for (ChatMessage m : chatHistory) {
                if (m.id > since) {
                    // KIỂM TRA QUYỀN NHẬN TIN MULTICAST:
                    if ("MULTICAST".equals(m.type)) {
                        // Nếu là Web Client: phải đã join và đúng nhóm mới được nhận!
                        if (client != null) {
                            if (!client.joinedMulticast || !m.targetGroup.equals(client.multicastGroup)) {
                                continue; // BỎ QUA - HÙNG SẼ KHÔNG THẤY TIN NÀY!
                            }
                        }
                    }

                    if (!first) json.append(",");
                    json.append("{\"id\": ").append(m.id)
                        .append(", \"sender\": \"").append(escapeJson(m.sender)).append("\"")
                        .append(", \"text\": \"").append(escapeJson(m.text)).append("\"")
                        .append(", \"type\": \"").append(m.type).append("\"")
                        .append(", \"group\": \"").append(m.targetGroup != null ? m.targetGroup : "").append("\"")
                        .append(", \"time\": \"").append(m.time).append("\"}");
                    first = false;
                }
            }
            json.append("]");
            sendJsonResponse(exchange, 200, json.toString());
        }
    }

    /**
     * GỬI TIN NHẮN TỪ WEB CLIENT (CHỌN KÊNH GENERAL HOẶC MULTICAST)
     */
    static class ApiChatSendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
            String sender = extractJsonStringField(body, "sender");
            String text = extractJsonStringField(body, "message");
            String channel = extractJsonStringField(body, "channel"); // "GENERAL" hoặc "MULTICAST"
            String group = extractJsonStringField(body, "group");

            if (sender == null || sender.isEmpty()) sender = "Web Guest";
            if (text == null || text.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"error\": \"message is required\"}"); return;
            }

            if ("MULTICAST".equalsIgnoreCase(channel)) {
                if (group == null || group.isEmpty()) group = currentMulticastGroup;
                broadcastMulticastMessage(sender, text, group);
            } else {
                broadcastGeneralMessage(sender, text);
            }

            sendJsonResponse(exchange, 200, "{\"status\": \"OK\"}");
        }
    }

    static class ApiWebClientRegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
            String nick = extractJsonStringField(body, "nickname");
            if (nick == null || nick.isEmpty()) nick = "User" + (new Random().nextInt(900) + 100);

            boolean joinMcast = body.toLowerCase().matches(".*\"join_multicast\"\\s*:\\s*true.*");
            String mGroup = extractJsonStringField(body, "multicast_group");
            if (mGroup == null || mGroup.isEmpty()) mGroup = currentMulticastGroup;

            int id = webClientIdCounter.incrementAndGet();
            WebClientInfo info = new WebClientInfo(id, nick, joinMcast, mGroup);
            webClients.put(id, info);

            String statusStr = joinMcast ? " (Đã tham gia nhóm Multicast: " + mGroup + ")" : " (KHÔNG tham gia Multicast)";
            broadcastSystemMessage("Khách " + nick + " (#" + id + ") đã vào Web Chat" + statusStr);

            String resp = "{\"id\": " + id + ", \"nickname\": \"" + escapeJson(nick) + "\", \"joined_mcast\": " + joinMcast + ", \"multicast_group\": \"" + mGroup + "\"}";
            sendJsonResponse(exchange, 200, resp);
        }
    }

    static class ApiWebClientUpdateGroupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
            String idStr = extractJsonStringField(body, "clientId");
            String group = extractJsonStringField(body, "group");
            boolean join = body.toLowerCase().matches(".*\"join_multicast\"\\s*:\\s*true.*");

            try {
                int id = Integer.parseInt(idStr);
                WebClientInfo info = webClients.get(id);
                if (info != null) {
                    info.joinedMulticast = join;
                    if (group != null && !group.isEmpty()) info.multicastGroup = group;
                    String statusText = join ? "đã vào nhóm Multicast " + info.multicastGroup : "đã rời nhóm Multicast";
                    broadcastSystemMessage("User " + info.nickname + " " + statusText);
                    sendJsonResponse(exchange, 200, "{\"status\":\"OK\",\"joined\":" + join + ",\"group\":\"" + info.multicastGroup + "\"}");
                    return;
                }
            } catch (Exception ignored) {}
            sendJsonResponse(exchange, 400, "{\"error\":\"Invalid request\"}");
        }
    }

    static class ApiNetworkCardsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            List<NetworkHelper.CardInfo> cards = NetworkHelper.getAvailableCards();
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < cards.size(); i++) {
                NetworkHelper.CardInfo c = cards.get(i);
                json.append("{\n")
                    .append("  \"ip\": \"").append(c.ip).append("\",\n")
                    .append("  \"name\": \"").append(escapeJson(c.name)).append("\",\n")
                    .append("  \"displayName\": \"").append(escapeJson(c.displayName)).append("\",\n")
                    .append("  \"isHotspot\": ").append(c.isHotspot).append("\n")
                    .append("}");
                if (i < cards.size() - 1) json.append(",\n");
            }
            json.append("]");
            sendJsonResponse(exchange, 200, json.toString());
        }
    }

    // ------------------------------------------------------------------------
    // TIỆN ÍCH HTTP
    // ------------------------------------------------------------------------
    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private static void sendHtmlResponse(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private static String extractJsonStringField(String json, String field) {
        if (json == null) return null;
        String key = "\"" + field + "\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex == -1) return null;

        int colonIndex = json.indexOf(":", keyIndex + key.length());
        if (colonIndex == -1) return null;

        int quoteStart = json.indexOf("\"", colonIndex + 1);
        if (quoteStart == -1) {
            // Trường hợp số nguyên hoặc boolean
            int comma = json.indexOf(",", colonIndex + 1);
            int brace = json.indexOf("}", colonIndex + 1);
            int end = comma != -1 ? (brace != -1 ? Math.min(comma, brace) : comma) : brace;
            if (end != -1) return json.substring(colonIndex + 1, end).trim();
            return null;
        }

        int quoteEnd = json.indexOf("\"", quoteStart + 1);
        while (quoteEnd != -1 && json.charAt(quoteEnd - 1) == '\\') {
            quoteEnd = json.indexOf("\"", quoteEnd + 1);
        }
        if (quoteEnd == -1) return null;
        return json.substring(quoteStart + 1, quoteEnd);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    // ========================================================================
    // GIAO DIỆN 1: ADMIN WEB DASHBOARD (MÀU ĐỎ CHỦ ĐẠO / CRIMSON)
    // ========================================================================
    private static String renderAdminDashboardHtml() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"vi\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "  <title>Admin Dashboard | TCP Chat, HTTP & Multicast Hub</title>\n" +
                "  <link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">\n" +
                "  <style>\n" +
                "    :root {\n" +
                "      --primary: #C62828; --primary-dark: #8E0000; --accent: #E53935;\n" +
                "      --bg: #F8F9FA; --card: #FFFFFF; --border: #FFCDD2; --text: #212121;\n" +
                "    }\n" +
                "    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Segoe UI', system-ui, sans-serif; }\n" +
                "    body { background: var(--bg); color: var(--text); padding-bottom: 50px; }\n" +
                "    \n" +
                "    .header {\n" +
                "      background: linear-gradient(135deg, var(--primary-dark), var(--primary), var(--accent));\n" +
                "      color: white; padding: 26px 20px; text-align: center;\n" +
                "      box-shadow: 0 4px 15px rgba(198, 40, 40, 0.35);\n" +
                "    }\n" +
                "    .header h1 { font-size: 25px; margin-bottom: 6px; }\n" +
                "    .header p { font-size: 14px; opacity: 0.9; }\n" +
                "    .badges { display: flex; gap: 10px; justify-content: center; flex-wrap: wrap; margin-top: 14px; }\n" +
                "    .badge { background: rgba(255,255,255,0.22); padding: 5px 12px; border-radius: 20px; font-size: 13px; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; }\n" +
                "    .dot { width: 8px; height: 8px; background: #00E676; border-radius: 50%; box-shadow: 0 0 6px #00E676; }\n" +
                "    \n" +
                "    .container { max-width: 1100px; margin: -20px auto 0; padding: 0 15px; }\n" +
                "    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 20px; }\n" +
                "    \n" +
                "    .card { background: var(--card); border-radius: 12px; border: 1px solid var(--border); box-shadow: 0 3px 12px rgba(0,0,0,0.05); overflow: hidden; }\n" +
                "    .card-header {\n" +
                "      background: #FFF5F5; border-bottom: 2px solid var(--border); padding: 14px 18px;\n" +
                "      color: var(--primary-dark); font-size: 16px; font-weight: 700; display: flex; align-items: center; justify-content: space-between;\n" +
                "    }\n" +
                "    .card-body { padding: 18px; }\n" +
                "    \n" +
                "    .btn-red { background: linear-gradient(135deg, var(--primary), var(--accent)); color: white; border: none; padding: 10px 16px; border-radius: 8px; font-weight: 700; cursor: pointer; width: 100%; transition: opacity 0.2s; display: flex; align-items: center; justify-content: center; gap: 8px; }\n" +
                "    .btn-red:hover { opacity: 0.95; }\n" +
                "    .btn-outline { background: white; border: 1.5px solid var(--primary); color: var(--primary); padding: 8px 14px; border-radius: 8px; font-weight: 600; cursor: pointer; width: 100%; margin-top: 8px; }\n" +
                "    .btn-outline:hover { background: #FFF5F5; }\n" +
                "    \n" +
                "    .form-group { margin-bottom: 14px; }\n" +
                "    .form-group label { display: block; font-size: 13px; font-weight: 600; margin-bottom: 5px; color: var(--primary-dark); }\n" +
                "    .form-control { width: 100%; padding: 10px 12px; border: 1.5px solid var(--border); border-radius: 8px; font-size: 14px; outline: none; }\n" +
                "    .form-control:focus { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(198,40,40,0.15); }\n" +
                "    \n" +
                "    .user-list { list-style: none; max-height: 200px; overflow-y: auto; }\n" +
                "    .user-item { display: flex; justify-content: space-between; align-items: center; padding: 8px 10px; border-bottom: 1px dashed var(--border); font-size: 13px; }\n" +
                "    .pill { background: var(--primary); color: white; padding: 2px 8px; border-radius: 12px; font-size: 11px; font-weight: bold; }\n" +
                "    \n" +
                "    .chat-box { background: #FAFAFA; border: 1px solid #EEEEEE; border-radius: 8px; height: 210px; overflow-y: auto; padding: 10px; font-size: 13px; display: flex; flex-direction: column; gap: 6px; }\n" +
                "    .chat-msg { background: white; padding: 6px 10px; border-radius: 6px; border-left: 3px solid var(--primary); box-shadow: 0 1px 3px rgba(0,0,0,0.03); }\n" +
                "    .chat-msg.mcast { border-left-color: #2E7D32; background: #F1F8E9; }\n" +
                "    \n" +
                "    #toast { position: fixed; bottom: 20px; right: 20px; background: #2E7D32; color: white; padding: 12px 20px; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.2); display: none; z-index: 9999; font-weight: 600; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "\n" +
                "  <div class=\"header\">\n" +
                "    <h1><i class=\"fa-solid fa-tower-broadcast\"></i> TRUNG TÂM QUẢN TRỊ SERVER & MULTICAST HUB</h1>\n" +
                "    <p>Phân định rạch ròi: Kênh Chat Chung (tất cả) vs Kênh Multicast (chỉ thành viên nhóm)</p>\n" +
                "    <div class=\"badges\">\n" +
                "      <span class=\"badge\"><span class=\"dot\"></span> TCP Port: <b>5000</b></span>\n" +
                "      <span class=\"badge\"><span class=\"dot\"></span> HTTP Port: <b>8080</b></span>\n" +
                "      <span class=\"badge\" id=\"mcastStatusBadge\"><span class=\"dot\" style=\"background:#FFEB3B\"></span> Multicast: <b>239.1.1.1:8000</b></span>\n" +
                "      <a href=\"/chat\" target=\"_blank\" class=\"badge\" style=\"background:#FFEBEE; color:var(--primary); text-decoration:none;\">\n" +
                "        <i class=\"fa-solid fa-comments\"></i> Mở Web Chat Client\n" +
                "      </a>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "\n" +
                "  <div class=\"container\">\n" +
                "    <div class=\"grid\">\n" +
                "      \n" +
                "      <!-- Thẻ 1: Multicast Controls -->\n" +
                "      <div class=\"card\">\n" +
                "        <div class=\"card-header\">\n" +
                "          <span><i class=\"fa-solid fa-network-wired\" style=\"color:var(--primary);\"></i> Cấu Hình Multicast UDP</span>\n" +
                "          <span id=\"mcastPill\" class=\"pill\">Đang kiểm tra...</span>\n" +
                "        </div>\n" +
                "        <div class=\"card-body\">\n" +
                "          <div class=\"form-group\">\n" +
                "            <label>Địa chỉ nhóm Multicast (224-239.x.x.x):</label>\n" +
                "            <input type=\"text\" id=\"mcastGroupInput\" class=\"form-control\" value=\"239.1.1.1\">\n" +
                "          </div>\n" +
                "          <div class=\"form-group\">\n" +
                "            <label>Chọn Card mạng (Ưu tiên Hotspot):</label>\n" +
                "            <div style=\"display:flex; gap:6px;\">\n" +
                "              <select id=\"cardSelect\" class=\"form-control\" style=\"flex:1;\"></select>\n" +
                "              <button onclick=\"loadNetworkCards()\" style=\"background:#EEEEEE; border:1px solid #CCC; border-radius:6px; padding:0 12px; cursor:pointer;\" title=\"Quét lại card mạng\"><i class=\"fa-solid fa-arrows-rotate\"></i></button>\n" +
                "            </div>\n" +
                "            <small style=\"color:#666; font-size:11px; margin-top:3px; display:block;\">\n" +
                "              🔥 Khi bật Mobile Hotspot trên Laptop, bấm nút xoay 🔄 và chọn card <b>192.168.137.1 (Hotspot)</b>\n" +
                "            </small>\n" +
                "          </div>\n" +
                "          <p id=\"cardInfo\" style=\"font-size:12px; color:#666; margin-bottom:12px;\">Đang dò card mạng...</p>\n" +
                "          <div style=\"display:flex; gap:10px;\">\n" +
                "            <button class=\"btn-red\" onclick=\"joinMulticast()\"><i class=\"fa-solid fa-right-to-bracket\"></i> Tham Gia Nhóm</button>\n" +
                "            <button class=\"btn-outline\" onclick=\"leaveMulticast()\" style=\"margin-top:0;\"><i class=\"fa-solid fa-right-from-bracket\"></i> Rời Nhóm</button>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "\n" +
                "      <!-- Thẻ 2: Phát Broadcast / Multicast -->\n" +
                "      <div class=\"card\">\n" +
                "        <div class=\"card-header\">\n" +
                "          <span><i class=\"fa-solid fa-bullhorn\" style=\"color:var(--primary);\"></i> Phát Tin Từ Server</span>\n" +
                "        </div>\n" +
                "        <div class=\"card-body\">\n" +
                "          <div class=\"form-group\">\n" +
                "            <label>Nội dung phát:</label>\n" +
                "            <input type=\"text\" id=\"broadcastInput\" class=\"form-control\" value=\"Thông báo: Bảo trì hệ thống sau 5 phút nữa!\">\n" +
                "          </div>\n" +
                "          <button class=\"btn-red\" onclick=\"sendBroadcast()\"><i class=\"fa-solid fa-bullhorn\"></i> Phát Toàn Server (Tất cả nhận)</button>\n" +
                "          <button class=\"btn-outline\" onclick=\"sendMulticastOnly()\"><i class=\"fa-solid fa-satellite-dish\"></i> Chỉ Gửi Vào Nhóm Multicast (Chỉ nhóm nhận)</button>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "\n" +
                "      <!-- Thẻ 3: Danh sách Online -->\n" +
                "      <div class=\"card\">\n" +
                "        <div class=\"card-header\">\n" +
                "          <span><i class=\"fa-solid fa-users\" style=\"color:var(--primary);\"></i> Danh Sách Client Online</span>\n" +
                "          <span id=\"onlineCount\" class=\"pill\">0 online</span>\n" +
                "        </div>\n" +
                "        <div class=\"card-body\">\n" +
                "          <ul id=\"usersList\" class=\"user-list\">\n" +
                "            <li style=\"text-align:center; padding:15px; color:#888;\">Đang quét...</li>\n" +
                "          </ul>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "\n" +
                "      <!-- Thẻ 4: Live Feed -->\n" +
                "      <div class=\"card\" style=\"grid-column: 1 / -1;\">\n" +
                "        <div class=\"card-header\">\n" +
                "          <span><i class=\"fa-solid fa-clock-rotate-left\" style=\"color:var(--primary);\"></i> Toàn Bộ Dòng Tin Nhắn (Admin Xem Tất Cả)</span>\n" +
                "        </div>\n" +
                "        <div class=\"card-body\">\n" +
                "          <div id=\"chatFeed\" class=\"chat-box\"></div>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "\n" +
                "    </div>\n" +
                "  </div>\n" +
                "\n" +
                "  <div id=\"toast\"></div>\n" +
                "\n" +
                "  <script>\n" +
                "    function showToast(msg, isErr) {\n" +
                "      const t = document.getElementById('toast');\n" +
                "      t.innerText = msg; t.style.background = isErr ? '#D32F2F' : '#2E7D32';\n" +
                "      t.style.display = 'block'; setTimeout(() => { t.style.display = 'none'; }, 3000);\n" +
                "    }\n" +
                "\n" +
                "    async function loadMulticastStatus() {\n" +
                "      try {\n" +
                "        const res = await fetch('/api/multicast/status');\n" +
                "        const data = await res.json();\n" +
                "        const pill = document.getElementById('mcastPill');\n" +
                "        const cardInfo = document.getElementById('cardInfo');\n" +
                "        const badge = document.getElementById('mcastStatusBadge');\n" +
                "        if (data.joined) {\n" +
                "          pill.innerText = 'ĐÃ VÀO NHÓM ' + data.group;\n" +
                "          pill.style.background = '#2E7D32';\n" +
                "          badge.innerHTML = `<span class=\"dot\"></span> Multicast: <b>${data.group}:${data.port}</b> (Active)`;\n" +
                "        } else {\n" +
                "          pill.innerText = 'CHƯA VÀO NHÓM';\n" +
                "          pill.style.background = '#757575';\n" +
                "          badge.innerHTML = `<span class=\"dot\" style=\"background:#9E9E9E\"></span> Multicast: Chưa tham gia`;\n" +
                "        }\n" +
                "        cardInfo.innerHTML = `Card mạng: <b>${data.card}</b> ${data.is_hotspot ? '<span style=\"color:#D84315; font-weight:bold;\">★ HOTSPOT</span>' : ''}`;\n" +
                "      } catch (e) {}\n" +
                "    }\n" +
                "\n" +
                "    async function loadNetworkCards() {\n" +
                "      try {\n" +
                "        const res = await fetch('/api/network/cards');\n" +
                "        const cards = await res.json();\n" +
                "        const sel = document.getElementById('cardSelect');\n" +
                "        sel.innerHTML = '';\n" +
                "        cards.forEach(c => {\n" +
                "          const opt = document.createElement('option');\n" +
                "          opt.value = c.ip;\n" +
                "          const hs = c.isHotspot ? '🔥 [HOTSPOT] ' : '📶 ';\n" +
                "          opt.innerText = `${hs}${c.ip} - ${c.displayName}`;\n" +
                "          if (c.isHotspot) opt.selected = true;\n" +
                "          sel.appendChild(opt);\n" +
                "        });\n" +
                "      } catch (e) {}\n" +
                "    }\n" +
                "\n" +
                "    async function joinMulticast() {\n" +
                "      const grp = document.getElementById('mcastGroupInput').value.trim();\n" +
                "      const cardSel = document.getElementById('cardSelect');\n" +
                "      const cardIp = cardSel ? cardSel.value : null;\n" +
                "      const res = await fetch('/api/multicast/join', {\n" +
                "        method: 'POST', headers: { 'Content-Type': 'application/json' },\n" +
                "        body: JSON.stringify({ group: grp, cardIp: cardIp })\n" +
                "      });\n" +
                "      if (res.ok) { showToast('✅ Đã vào nhóm ' + grp + (cardIp ? ' (' + cardIp + ')' : '')); loadMulticastStatus(); }\n" +
                "      else { const d = await res.json(); showToast('❌ ' + (d.error || 'Lỗi'), true); }\n" +
                "    }\n" +
                "\n" +
                "    async function leaveMulticast() {\n" +
                "      const res = await fetch('/api/multicast/leave', { method: 'POST' });\n" +
                "      if (res.ok) { showToast('⏹ Đã rời nhóm Multicast'); loadMulticastStatus(); }\n" +
                "    }\n" +
                "\n" +
                "    async function sendBroadcast() {\n" +
                "      const text = document.getElementById('broadcastInput').value.trim();\n" +
                "      if (!text) return showToast('Nhập tin nhắn!', true);\n" +
                "      const res = await fetch('/api/broadcast', {\n" +
                "        method: 'POST', headers: { 'Content-Type': 'application/json' },\n" +
                "        body: JSON.stringify({ message: text })\n" +
                "      });\n" +
                "      if (res.ok) showToast('✅ Đã phát toàn server!');\n" +
                "    }\n" +
                "\n" +
                "    async function sendMulticastOnly() {\n" +
                "      const text = document.getElementById('broadcastInput').value.trim();\n" +
                "      const grp = document.getElementById('mcastGroupInput').value.trim();\n" +
                "      if (!text) return showToast('Nhập tin nhắn!', true);\n" +
                "      const res = await fetch('/api/multicast/send', {\n" +
                "        method: 'POST', headers: { 'Content-Type': 'application/json' },\n" +
                "        body: JSON.stringify({ message: text, sender: 'Admin', group: grp })\n" +
                "      });\n" +
                "      if (res.ok) showToast('📡 Đã phát vào nhóm Multicast ' + grp);\n" +
                "    }\n" +
                "\n" +
                "    async function loadUsers() {\n" +
                "      try {\n" +
                "        const res = await fetch('/api/users');\n" +
                "        const data = await res.json();\n" +
                "        document.getElementById('onlineCount').innerText = `${data.total_online} online (${data.tcp_count} TCP, ${data.web_count} Web)`;\n" +
                "        const list = document.getElementById('usersList');\n" +
                "        list.innerHTML = '';\n" +
                "        data.tcp_users.forEach(u => {\n" +
                "          const mcTag = u.mcast_group !== 'null' ? `<small style=\"color:#2E7D32;\">[Nhóm ${u.mcast_group}]</small>` : '<small style=\"color:#888;\">[Không vào Mcast]</small>';\n" +
                "          list.innerHTML += `<li class=\"user-item\"><span><i class=\"fa-solid fa-desktop\" style=\"color:#C62828;\"></i> TCP Client #${u.id} ${mcTag}</span> <span class=\"pill\">TCP</span></li>`;\n" +
                "        });\n" +
                "        data.web_users.forEach(u => {\n" +
                "          const mcTag = u.joined_mcast ? `<small style=\"color:#2E7D32; font-weight:bold;\">[Nhóm ${u.group}]</small>` : '<small style=\"color:#888;\">[Không vào Mcast]</small>';\n" +
                "          list.innerHTML += `<li class=\"user-item\"><span><i class=\"fa-solid fa-globe\" style=\"color:#1565C0;\"></i> ${u.nickname} (#${u.id}) ${mcTag}</span> <span class=\"pill\" style=\"background:#1565C0;\">Web</span></li>`;\n" +
                "        });\n" +
                "      } catch (e) {}\n" +
                "    }\n" +
                "\n" +
                "    let lastId = 0;\n" +
                "    async function loadMessages() {\n" +
                "      try {\n" +
                "        const res = await fetch('/api/chat/messages?since=' + lastId);\n" +
                "        const msgs = await res.json();\n" +
                "        if (msgs.length > 0) {\n" +
                "          const box = document.getElementById('chatFeed');\n" +
                "          msgs.forEach(m => {\n" +
                "            lastId = Math.max(lastId, m.id);\n" +
                "            const div = document.createElement('div');\n" +
                "            div.className = 'chat-msg' + (m.type === 'MULTICAST' ? ' mcast' : '');\n" +
                "            const badge = m.type === 'MULTICAST' ? `<span style=\"background:#2E7D32; color:white; font-size:10px; padding:2px 5px; border-radius:3px;\">MULTICAST @${m.group}</span>` : `<span style=\"background:#EEE; font-size:10px; padding:2px 5px; border-radius:3px;\">${m.type}</span>`;\n" +
                "            div.innerHTML = `<small style=\"color:#888;\">[${m.time}]</small> <b>${m.sender}:</b> <span>${m.text}</span> ${badge}`;\n" +
                "            box.appendChild(div);\n" +
                "          });\n" +
                "          box.scrollTop = box.scrollHeight;\n" +
                "        }\n" +
                "      } catch (e) {}\n" +
                "    }\n" +
                "\n" +
                "    loadNetworkCards(); loadMulticastStatus(); loadUsers(); loadMessages();\n" +
                "    setInterval(loadUsers, 2000); setInterval(loadMessages, 1000);\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>";
    }

    // ========================================================================
    // GIAO DIỆN 2: WEB CHAT CLIENT (PHÂN BIỆT RẠCH RÒI PHÒNG CHUNG vs MULTICAST)
    // ========================================================================
    private static String renderWebChatClientHtml() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"vi\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "  <title>Phòng Chat Web | Chat Chung & Multicast</title>\n" +
                "  <link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">\n" +
                "  <style>\n" +
                "    :root { --primary: #C62828; --accent: #E53935; --bg: #F5F5F5; }\n" +
                "    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Segoe UI', system-ui, sans-serif; }\n" +
                "    body { background: var(--bg); display: flex; flex-direction: column; height: 100vh; }\n" +
                "    \n" +
                "    /* Topbar */\n" +
                "    .topbar {\n" +
                "      background: linear-gradient(135deg, #8E0000, var(--primary));\n" +
                "      color: white; padding: 12px 20px; display: flex; align-items: center; justify-content: space-between;\n" +
                "      box-shadow: 0 2px 10px rgba(0,0,0,0.15);\n" +
                "    }\n" +
                "    .topbar h2 { font-size: 18px; display: flex; align-items: center; gap: 8px; }\n" +
                "    .user-tag { background: rgba(255,255,255,0.2); padding: 4px 12px; border-radius: 15px; font-size: 13px; font-weight: 600; }\n" +
                "    \n" +
                "    /* Chat container */\n" +
                "    .chat-container { flex: 1; display: flex; flex-direction: column; max-width: 920px; width: 100%; margin: 12px auto; background: white; border-radius: 12px; box-shadow: 0 4px 15px rgba(0,0,0,0.06); overflow: hidden; }\n" +
                "    \n" +
                "    /* Channel Selector Bar */\n" +
                "    .channel-bar {\n" +
                "      background: #FFF5F5; border-bottom: 1px solid #FFCDD2; padding: 10px 18px;\n" +
                "      display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 10px;\n" +
                "    }\n" +
                "    .channel-buttons { display: flex; gap: 8px; }\n" +
                "    .btn-channel {\n" +
                "      padding: 6px 14px; border-radius: 20px; border: 1.5px solid var(--primary); background: white;\n" +
                "      color: var(--primary); font-size: 13px; font-weight: bold; cursor: pointer; transition: all 0.2s;\n" +
                "    }\n" +
                "    .btn-channel.active { background: var(--primary); color: white; }\n" +
                "    .btn-channel.mcast-active { background: #2E7D32; border-color: #2E7D32; color: white; }\n" +
                "    \n" +
                "    .chat-messages { flex: 1; padding: 18px; overflow-y: auto; display: flex; flex-direction: column; gap: 10px; background: #FFFBFB; }\n" +
                "    \n" +
                "    /* Bong bóng chat */\n" +
                "    .bubble { max-width: 75%; padding: 10px 14px; border-radius: 12px; font-size: 14px; line-height: 1.4; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }\n" +
                "    .bubble.mine { align-self: flex-end; background: var(--primary); color: white; border-bottom-right-radius: 2px; }\n" +
                "    .bubble.mine-mcast { align-self: flex-end; background: #2E7D32; color: white; border-bottom-right-radius: 2px; }\n" +
                "    .bubble.other { align-self: flex-start; background: white; border: 1px solid #FFCDD2; color: #212121; border-bottom-left-radius: 2px; }\n" +
                "    .bubble.mcast { align-self: flex-start; background: #E8F5E9; border: 1.5px solid #A5D6A7; color: #1B5E20; border-bottom-left-radius: 2px; }\n" +
                "    .bubble.sys { align-self: center; background: #FFEBEE; color: #C62828; font-size: 12px; font-weight: 600; border-radius: 20px; padding: 4px 12px; }\n" +
                "    .meta { font-size: 11px; opacity: 0.8; margin-bottom: 3px; display: block; }\n" +
                "    \n" +
                "    /* Input row */\n" +
                "    .input-bar { padding: 12px 18px; border-top: 1px solid #FFCDD2; display: flex; gap: 10px; background: white; }\n" +
                "    .input-bar input { flex: 1; padding: 12px 15px; border: 1.5px solid #FFCDD2; border-radius: 8px; font-size: 14px; outline: none; }\n" +
                "    .input-bar input:focus { border-color: var(--primary); }\n" +
                "    .input-bar button { background: var(--primary); color: white; border: none; padding: 0 20px; border-radius: 8px; font-weight: bold; cursor: pointer; display: flex; align-items: center; gap: 6px; }\n" +
                "    \n" +
                "    /* Modal */\n" +
                "    .modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.65); display: flex; align-items: center; justify-content: center; z-index: 1000; }\n" +
                "    .modal-box { background: white; border-radius: 14px; max-width: 450px; width: 90%; padding: 24px; box-shadow: 0 10px 25px rgba(0,0,0,0.3); border-top: 6px solid var(--primary); }\n" +
                "    .modal-box h3 { color: var(--primary); margin-bottom: 10px; font-size: 19px; }\n" +
                "    .modal-box p { font-size: 13px; color: #555; margin-bottom: 16px; line-height: 1.4; }\n" +
                "    .modal-group { margin-bottom: 14px; }\n" +
                "    .modal-group label { display: block; font-size: 13px; font-weight: 600; margin-bottom: 5px; color: #333; }\n" +
                "    .modal-group input { width: 100%; padding: 10px; border: 1.5px solid #FFCDD2; border-radius: 6px; font-size: 14px; }\n" +
                "    .modal-buttons { display: flex; gap: 10px; margin-top: 20px; }\n" +
                "    .btn-join { flex: 1; background: var(--primary); color: white; border: none; padding: 11px; border-radius: 8px; font-weight: bold; cursor: pointer; }\n" +
                "    .btn-skip { flex: 1; background: #EEEEEE; color: #555; border: none; padding: 11px; border-radius: 8px; font-weight: bold; cursor: pointer; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "\n" +
                "  <!-- MODAL: HỎI THAM GIA MULTICAST -->\n" +
                "  <div id=\"joinModal\" class=\"modal-overlay\">\n" +
                "    <div class=\"modal-box\">\n" +
                "      <h3><i class=\"fa-solid fa-satellite-dish\"></i> Thiết Lập Web Chat Client</h3>\n" +
                "      <p>Bạn có muốn <b>THAM GIA NHÓM MULTICAST UDP</b> để nhận các tin nhắn riêng của nhóm không?</p>\n" +
                "      \n" +
                "      <div class=\"modal-group\" id=\"nickGroupRow\">\n" +
                "        <label>Tên hiển thị (Nickname):</label>\n" +
                "        <input type=\"text\" id=\"modalNick\" value=\"Vy\" placeholder=\"Nhập tên của bạn...\">\n" +
                "      </div>\n" +
                "      \n" +
                "      <div class=\"modal-group\">\n" +
                "        <label>Địa chỉ nhóm Multicast (Lớp D: 224-239.x.x.x):</label>\n" +
                "        <input type=\"text\" id=\"modalGroup\" value=\"239.1.1.1\">\n" +
                "      </div>\n" +
                "      \n" +
                "      <div style=\"background:#FFF3E0; border-left:3px solid #FF9800; padding:8px 10px; font-size:12px; color:#E65100; margin-bottom:14px;\">\n" +
                "        💡 <b>Quy tắc Multicast (Chương 10):</b><br>\n" +
                "        - <b>CÓ THAM GIA:</b> Sẽ nhận được tin Multicast của nhóm đó.<br>\n" +
                "        - <b>TỪ CHỐI THAM GIA:</b> Vẫn chat phòng chung bình thường, nhưng <b>TUYỆT ĐỐI KHÔNG nhận</b> tin nhắn nhóm Multicast!\n" +
                "      </div>\n" +
                "      \n" +
                "      <div class=\"modal-buttons\">\n" +
                "        <button class=\"btn-join\" onclick=\"saveGroupChoice(true)\"><i class=\"fa-solid fa-check\"></i> CÓ, Tham Gia Nhóm</button>\n" +
                "        <button class=\"btn-skip\" onclick=\"saveGroupChoice(false)\"><i class=\"fa-solid fa-xmark\"></i> TỪ CHỐI, Chỉ Chat Chung</button>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "\n" +
                "  <!-- TOPBAR -->\n" +
                "  <div class=\"topbar\">\n" +
                "    <h2><i class=\"fa-solid fa-comments\"></i> Web Chat Client</h2>\n" +
                "    <div style=\"display:flex; gap:10px; align-items:center;\">\n" +
                "      <span id=\"mcastStatusTag\" class=\"user-tag\" style=\"background:#757575; cursor:pointer;\" onclick=\"openGroupModal()\">⚪ Chưa vào Multicast (Bấm để tham gia)</span>\n" +
                "      <span id=\"myNickTag\" class=\"user-tag\">Khách</span>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "\n" +
                "  <!-- CHAT CONTAINER -->\n" +
                "  <div class=\"chat-container\">\n" +
                "    <!-- THANH CHỌN KÊNH GỬI TIN -->\n" +
                "    <div class=\"channel-bar\">\n" +
                "      <div style=\"display:flex; align-items:center; gap:8px;\">\n" +
                "        <span style=\"font-size:13px; font-weight:bold; color:#666;\">Kênh gửi tin:</span>\n" +
                "        <div class=\"channel-buttons\">\n" +
                "          <button id=\"btnChanGeneral\" class=\"btn-channel active\" onclick=\"selectChannel('GENERAL')\"><i class=\"fa-solid fa-users\"></i> 💬 Chat Chung (Phòng chung)</button>\n" +
                "          <button id=\"btnChanMcast\" class=\"btn-channel\" onclick=\"selectChannel('MULTICAST')\"><i class=\"fa-solid fa-satellite-dish\"></i> 📡 Nhóm Multicast (<span id=\"btnChanMcastGroup\">239.1.1.1</span>)</button>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "      <span id=\"channelHint\" style=\"font-size:12px; color:#888;\">Mọi người trong phòng chung đều thấy</span>\n" +
                "    </div>\n" +
                "\n" +
                "    <div id=\"chatBox\" class=\"chat-messages\"></div>\n" +
                "    \n" +
                "    <div class=\"input-bar\">\n" +
                "      <input type=\"text\" id=\"msgInput\" placeholder=\"Nhập tin nhắn...\" onkeypress=\"handleKey(event)\">\n" +
                "      <button id=\"btnSend\" onclick=\"sendMsg()\"><i class=\"fa-solid fa-paper-plane\"></i> Gửi</button>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "\n" +
                "  <script>\n" +
                "    let myId = 0;\n" +
                "    let myNick = 'User';\n" +
                "    let joinedMcast = false;\n" +
                "    let currentGroup = '239.1.1.1';\n" +
                "    let activeChannel = 'GENERAL'; // 'GENERAL' hoặc 'MULTICAST'\n" +
                "    let lastId = 0;\n" +
                "\n" +
                "    async function saveGroupChoice(join) {\n" +
                "      const nickInput = document.getElementById('modalNick');\n" +
                "      if (nickInput.value.trim()) myNick = nickInput.value.trim();\n" +
                "      currentGroup = document.getElementById('modalGroup').value.trim() || '239.1.1.1';\n" +
                "      joinedMcast = join;\n" +
                "      \n" +
                "      document.getElementById('joinModal').style.display = 'none';\n" +
                "      document.getElementById('myNickTag').innerText = myNick;\n" +
                "      document.getElementById('btnChanMcastGroup').innerText = currentGroup;\n" +
                "      updateMcastTag();\n" +
                "      \n" +
                "      if (myId === 0) {\n" +
                "        // Đăng ký lần đầu\n" +
                "        const res = await fetch('/api/webclient/register', {\n" +
                "          method: 'POST', headers: { 'Content-Type': 'application/json' },\n" +
                "          body: JSON.stringify({ nickname: myNick, join_multicast: joinedMcast, multicast_group: currentGroup })\n" +
                "        });\n" +
                "        const data = await res.json();\n" +
                "        myId = data.id;\n" +
                "        startPolling();\n" +
                "      } else {\n" +
                "        // Cập nhật trạng thái đổi nhóm\n" +
                "        await fetch('/api/webclient/update-group', {\n" +
                "          method: 'POST', headers: { 'Content-Type': 'application/json' },\n" +
                "          body: JSON.stringify({ clientId: myId, join_multicast: joinedMcast, group: currentGroup })\n" +
                "        });\n" +
                "      }\n" +
                "      \n" +
                "      if (!joinedMcast && activeChannel === 'MULTICAST') {\n" +
                "        selectChannel('GENERAL');\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    function openGroupModal() {\n" +
                "      document.getElementById('nickGroupRow').style.display = 'none'; // Không cần sửa tên nữa\n" +
                "      document.getElementById('joinModal').style.display = 'flex';\n" +
                "    }\n" +
                "\n" +
                "    function updateMcastTag() {\n" +
                "      const tag = document.getElementById('mcastStatusTag');\n" +
                "      if (joinedMcast) {\n" +
                "        tag.style.background = '#2E7D32';\n" +
                "        tag.innerHTML = `🟢 Đã vào nhóm: <b>${currentGroup}</b> (Bấm đổi/rời)`;\n" +
                "      } else {\n" +
                "        tag.style.background = '#757575';\n" +
                "        tag.innerHTML = `⚪ Không tham gia Multicast (Bấm để tham gia)`;\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    function selectChannel(chan) {\n" +
                "      if (chan === 'MULTICAST' && !joinedMcast) {\n" +
                "        alert('⚠️ Bạn đang TỪ CHỐI tham gia nhóm Multicast! Hãy bấm nút phía trên để tham gia nhóm trước khi chat Multicast.');\n" +
                "        openGroupModal();\n" +
                "        return;\n" +
                "      }\n" +
                "      activeChannel = chan;\n" +
                "      const bGen = document.getElementById('btnChanGeneral');\n" +
                "      const bMcast = document.getElementById('btnChanMcast');\n" +
                "      const hint = document.getElementById('channelHint');\n" +
                "      const sendBtn = document.getElementById('btnSend');\n" +
                "      \n" +
                "      if (chan === 'MULTICAST') {\n" +
                "        bGen.className = 'btn-channel';\n" +
                "        bMcast.className = 'btn-channel mcast-active';\n" +
                "        hint.innerHTML = `<b style=\"color:#2E7D32;\">CHỈ AI ĐÃ VÀO NHÓM ${currentGroup} MỚI NHẬN ĐƯỢC</b>`;\n" +
                "        sendBtn.style.background = '#2E7D32';\n" +
                "        document.getElementById('msgInput').placeholder = 'Nhập tin nhắn gửi riêng cho nhóm Multicast ' + currentGroup + '...';\n" +
                "      } else {\n" +
                "        bGen.className = 'btn-channel active';\n" +
                "        bMcast.className = 'btn-channel';\n" +
                "        hint.innerText = 'Gửi vào phòng chung (Mọi người đều thấy)';\n" +
                "        sendBtn.style.background = '#C62828';\n" +
                "        document.getElementById('msgInput').placeholder = 'Nhập tin nhắn gửi vào phòng chung...';\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    async function sendMsg() {\n" +
                "      const input = document.getElementById('msgInput');\n" +
                "      const text = input.value.trim();\n" +
                "      if (!text) return;\n" +
                "      input.value = '';\n" +
                "      \n" +
                "      await fetch('/api/chat/send', {\n" +
                "        method: 'POST', headers: { 'Content-Type': 'application/json' },\n" +
                "        body: JSON.stringify({\n" +
                "          sender: myNick,\n" +
                "          message: text,\n" +
                "          channel: activeChannel,\n" +
                "          group: currentGroup\n" +
                "        })\n" +
                "      });\n" +
                "    }\n" +
                "\n" +
                "    function handleKey(e) {\n" +
                "      if (e.key === 'Enter') sendMsg();\n" +
                "    }\n" +
                "\n" +
                "    function startPolling() {\n" +
                "      setInterval(async () => {\n" +
                "        try {\n" +
                "          // Gửi kèm clientId để Server lọc tin Multicast: Chỉ trả về tin nếu ĐÃ JOIN NHÓM!\n" +
                "          const res = await fetch(`/api/chat/messages?since=${lastId}&clientId=${myId}`);\n" +
                "          const msgs = await res.json();\n" +
                "          if (msgs.length > 0) {\n" +
                "            const box = document.getElementById('chatBox');\n" +
                "            msgs.forEach(m => {\n" +
                "              lastId = Math.max(lastId, m.id);\n" +
                "              const div = document.createElement('div');\n" +
                "              \n" +
                "              if (m.type === 'SYSTEM') {\n" +
                "                div.className = 'bubble sys';\n" +
                "                div.innerText = m.text;\n" +
                "              } else if (m.sender === myNick) {\n" +
                "                div.className = m.type === 'MULTICAST' ? 'bubble mine-mcast' : 'bubble mine';\n" +
                "                const mcastTag = m.type === 'MULTICAST' ? `[Nhóm ${m.group}] ` : '';\n" +
                "                div.innerHTML = `<span class=\"meta\">Tôi • ${mcastTag}${m.time}</span>${escapeHtml(m.text)}`;\n" +
                "              } else if (m.type === 'MULTICAST') {\n" +
                "                div.className = 'bubble mcast';\n" +
                "                div.innerHTML = `<span class=\"meta\"><i class=\"fa-solid fa-satellite-dish\"></i> ${escapeHtml(m.sender)} [NHÓM ${m.group}] • ${m.time}</span>${escapeHtml(m.text)}`;\n" +
                "              } else {\n" +
                "                div.className = 'bubble other';\n" +
                "                div.innerHTML = `<span class=\"meta\"><i class=\"fa-solid fa-user\"></i> ${escapeHtml(m.sender)} [Phòng chung] • ${m.time}</span>${escapeHtml(m.text)}`;\n" +
                "              }\n" +
                "              box.appendChild(div);\n" +
                "            });\n" +
                "            box.scrollTop = box.scrollHeight;\n" +
                "          }\n" +
                "        } catch (e) {}\n" +
                "      }, 800);\n" +
                "    }\n" +
                "\n" +
                "    function escapeHtml(t) {\n" +
                "      const p = document.createElement('p');\n" +
                "      p.textContent = t; return p.innerHTML;\n" +
                "    }\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
