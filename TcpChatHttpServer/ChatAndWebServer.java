import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * 4. Chuẩn hóa REST API (Chương 12):
 *    - Bổ sung các Endpoint chuẩn trong tài liệu:
 *      + GET  /api/clients (Status 200 OK)
 *      + POST /api/message (Status 201 Created)
 *      + GET  /api/history (Status 200 OK)
 *    - Kiểm tra chặt chẽ HTTP Response Status Codes:
 *      + 200 OK: Đọc dữ liệu thành công
 *      + 201 Created: Tạo mới tài nguyên thành công (tin nhắn, đăng ký, broadcast)
 *      + 204 No Content: Xử lý preflight CORS OPTIONS
 *      + 400 Bad Request: Dữ liệu gửi lên rỗng hoặc sai cú pháp
 *      + 404 Not Found: Đường dẫn không tồn tại hoặc không tìm thấy Client ID
 *      + 405 Method Not Allowed: Gửi sai HTTP Method (kèm header Allow)
 *      + 415 Unsupported Media Type: Thiếu header Content-Type: application/json
 *      + 500 Internal Server Error: Bắt ngoại lệ runtime toàn diện
 * 5. Hiển thị trực tiếp HTTP Status Codes trên cả Web Admin Dashboard và Web Chat Client!
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

            // Giao diện Web
            server.createContext("/", new AdminDashboardHandler());
            server.createContext("/chat", new WebChatClientHandler());

            // 1. CÁC ENDPOINT CHUẨN TRONG TÀI LIỆU (CHƯƠNG 12):
            server.createContext("/api/clients", new ApiClientsHandler());
            server.createContext("/api/message", new ApiMessageHandler());
            server.createContext("/api/history", new ApiHistoryHandler());

            // 2. CÁC ENDPOINT MỞ RỘNG (WEB CHAT & MULTICAST):
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
            System.out.println("   [API Chuẩn] GET  /api/clients   - Danh sách clients");
            System.out.println("   [API Chuẩn] POST /api/message   - Gửi tin nhắn");
            System.out.println("   [API Chuẩn] GET  /api/history   - Lịch sử tin nhắn");
        } catch (IOException e) {
            System.err.println("❌ [HTTP Server Lỗi]: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // HTTP HANDLERS (CHUẨN HÓA TOÀN DIỆN RESPONSE STATUS CODES)
    // ------------------------------------------------------------------------

    /**
     * GET / : Web Admin Dashboard & Phân phối file tĩnh HTML (P3.HTM, P4.HTM - Test chéo 4 đường)
     */
    static class AdminDashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            if (!checkMethod(exchange, "GET")) return;

            String path = exchange.getRequestURI().getPath();

            // 1. Nếu là trang chủ "/" -> Hiển thị Web Admin Dashboard
            if ("/".equals(path)) {
                sendHtmlResponse(exchange, 200, renderAdminDashboardHtml());
                return;
            }

            // 2. Hỗ trợ phân phối file tĩnh HTML (P3.html, P4.html, P3.HTM...) theo bài toán Test Chéo 4 Đường của thầy
            String fileName = path.startsWith("/") ? path.substring(1) : path;
            File file = new File(fileName);

            // Tự động tìm kiếm linh hoạt giữa .html và .htm
            if (!file.exists()) {
                if (fileName.toLowerCase().endsWith(".htm")) {
                    File alt = new File(fileName + "l");
                    if (alt.exists()) file = alt;
                } else if (fileName.toLowerCase().endsWith(".html")) {
                    File alt = new File(fileName.substring(0, fileName.length() - 1));
                    if (alt.exists()) file = alt;
                }
            }

            if (file.exists() && !file.isDirectory()) {
                byte[] responseBytes = Files.readAllBytes(file.toPath());
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, responseBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseBytes);
                }
                System.out.println("✅ [200 OK] Phân phối file tĩnh: " + file.getName() + " (" + responseBytes.length + " bytes)");
            } else {
                // Nếu không tìm thấy file, trả về lỗi 404 Not Found theo chuẩn bài toán trong thêm.md
                String errorMsg = "<h1>404 Not Found - Khong tim thay file " + escapeJson(fileName) + "</h1>";
                byte[] errBytes = errorMsg.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(404, errBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errBytes);
                }
                System.out.println("❌ [404 Not Found] Không tìm thấy file: " + fileName);
            }
        }
    }

    /**
     * GET /chat : Web Chat Client
     */
    static class WebChatClientHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            if (!checkMethod(exchange, "GET")) return;
            sendHtmlResponse(exchange, 200, renderWebChatClientHtml());
        }
    }

    /**
     * GET /api/clients : Trả về mảng JSON client theo chuẩn Chương 12
     */
    static class ApiClientsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            if (!checkMethod(exchange, "GET")) return;

            try {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                for (Integer id : tcpClients.keySet()) {
                    if (!first) json.append(",");
                    json.append("{\"id\":").append(id).append(",\"type\":\"TCP\"}");
                    first = false;
                }
                for (WebClientInfo w : webClients.values()) {
                    if (!first) json.append(",");
                    json.append("{\"id\":").append(w.id).append(",\"type\":\"WEB\",\"nickname\":\"").append(escapeJson(w.nickname)).append("\"}");
                    first = false;
                }
                json.append("]");
                sendJsonResponse(exchange, 200, json.toString());
            } catch (Throwable t) {
                sendErrorResponse(exchange, 500, "Lỗi máy chủ nội bộ: " + t.getMessage());
            }
        }
    }

    /**
     * POST /api/message : Nhận JSON {"text":"..."} và Broadcast theo chuẩn Chương 12
     */
    static class ApiMessageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            if (!checkMethod(exchange, "POST")) return;
            if (!checkJsonContentType(exchange)) return;

            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
                String text = extractJsonStringField(body, "text");
                if (text == null || text.isEmpty()) {
                    text = extractJsonStringField(body, "message");
                }

                if (text == null || text.trim().isEmpty()) {
                    sendErrorResponse(exchange, 400, "Trường 'text' là bắt buộc (text field is required)");
                    return;
                }

                broadcastGeneralMessage("HTTP Client", text);
                String json = "{\n" +
                        "  \"statusCode\": 201,\n" +
                        "  \"status\": \"Created\",\n" +
                        "  \"message\": \"Message sent\",\n" +
                        "  \"text\": \"" + escapeJson(text) + "\"\n" +
                        "}";
                sendJsonResponse(exchange, 201, json);
            } catch (Throwable t) {
                sendErrorResponse(exchange, 500, "Lỗi máy chủ nội bộ: " + t.getMessage());
            }
        }
    }

    /**
     * GET /api/history : Lấy lịch sử 20 tin nhắn gần nhất theo chuẩn Chương 12
     */
    static class ApiHistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            if (!checkMethod(exchange, "GET")) return;

            try {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                int start = Math.max(0, chatHistory.size() - 20);
                for (int i = start; i < chatHistory.size(); i++) {
                    ChatMessage m = chatHistory.get(i);
                    if (!first) json.append(",");
                    String formatted = "[" + m.sender + "]: " + m.text;
                    json.append("{\n")
                            .append("  \"id\": ").append(m.id).append(",\n")
                            .append("  \"sender\": \"").append(escapeJson(m.sender)).append("\",\n")
                            .append("  \"message\": \"").append(escapeJson(formatted)).append("\",\n")
                            .append("  \"text\": \"").append(escapeJson(m.text)).append("\",\n")
                            .append("  \"type\": \"").append(m.type).append("\",\n")
                            .append("  \"time\": \"").append(m.time).append("\"\n")
                            .append("}");
                    first = false;
                }
                json.append("]");
                sendJsonResponse(exchange, 200, json.toString());
            } catch (Throwable t) {
                sendErrorResponse(exchange, 500, "Lỗi máy chủ nội bộ: " + t.getMessage());
            }
        }
    }

    /**
     * GET /api/status : Tình trạng server, ports, multicast
     */
    static class ApiStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            if (!checkMethod(exchange, "GET")) return;

            try {
                String json = "{\n" +
                        "  \"statusCode\": 200,\n" +
                        "  \"status\": \"OK\",\n" +
                        "  \"message\": \"Server đang hoạt động tốt!\",\n" +
                        "  \"tcp_port\": " + TCP_PORT + ",\n" +
                        "  \"http_port\": " + HTTP_PORT + ",\n" +
                        "  \"multicast_port\": " + MULTICAST_PORT + ",\n" +
                        "  \"multicast_joined\": " + isMulticastJoined + ",\n" +
                        "  \"multicast_group\": \"" + currentMulticastGroup + "\",\n" +
                        "  \"network_card\": \"" + (multicastCard != null ? escapeJson(multicastCard.displayName) + " (" + multicastCard.ip + ")" : "Auto") + "\",\n" +
                        "  \"tcp_online\": " + tcpClients.size() + ",\n" +
                        "  \"web_online\": " + webClients.size() + "\n" +
                        "}";
                sendJsonResponse(exchange, 200, json);
            } catch (Throwable t) {
                sendErrorResponse(exchange, 500, "Lỗi máy chủ nội bộ: " + t.getMessage());
            }
        }
    }

    /**
     * GET /api/users : Danh sách chi tiết các user online
     */
    static class ApiUsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            if (!checkMethod(exchange, "GET")) return;

            try {
                StringBuilder json = new StringBuilder("{\n");
                json.append("  \"statusCode\": 200,\n");
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
            } catch (Throwable t) {
                sendErrorResponse(exchange, 500, "Lỗi máy chủ nội bộ: " + t.getMessage());
            }
        }
    }

    /**
     * POST /api/broadcast : Phát thông báo hệ thống (Status 201 Created)
     */
    static class ApiBroadcastHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            if (!checkMethod(exchange, "POST")) return;
            if (!checkJsonContentType(exchange)) return;

            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
                String msg = extractJsonStringField(body, "message");
                if (msg == null || msg.isEmpty()) msg = extractJsonStringField(body, "text");
                if (msg == null || msg.trim().isEmpty()) {
                    sendErrorResponse(exchange, 400, "Trường 'message' không được để trống (message is required)");
                    return;
                }

                broadcastSystemMessage("[THÔNG BÁO TỪ WEB HTTP]: " + msg);
                sendJsonResponse(exchange, 201, "{\n" +
                        "  \"statusCode\": 201,\n" +
                        "  \"status\": \"Created\",\n" +
                        "  \"message\": \"Đã broadcast thành công\",\n" +
                        "  \"recipients\": " + (tcpClients.size() + webClients.size()) + "\n" +
                        "}");
            } catch (Throwable t) {
                sendErrorResponse(exchange, 500, "Lỗi máy chủ nội bộ: " + t.getMessage());
            }
        }
    }

    /**
     * GET /api/multicast/status : Trạng thái nhóm multicast
     */
    static class ApiMulticastStatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            if (!checkMethod(exchange, "GET")) return;

            try {
                String cardDesc = multicastCard != null ? multicastCard.displayName + " (" + multicastCard.ip + ")" : "N/A";
                String json = "{\n" +
                        "  \"statusCode\": 200,\n" +
                        "  \"status\": \"OK\",\n" +
                        "  \"joined\": " + isMulticastJoined + ",\n" +
                        "  \"group\": \"" + currentMulticastGroup + "\",\n" +
                        "  \"port\": " + MULTICAST_PORT + ",\n" +
                        "  \"card\": \"" + escapeJson(cardDesc) + "\",\n" +
                        "  \"is_hotspot\": " + (multicastCard != null && multicastCard.isHotspot) + "\n" +
                        "}";
                sendJsonResponse(exchange, 200, json);
            } catch (Throwable t) {
                sendErrorResponse(exchange, 500, "Lỗi máy chủ nội bộ: " + t.getMessage());
            }
        }
    }

    /**
     * POST /api/multicast/join : Tham gia nhóm Multicast
     */
    static class ApiMulticastJoinHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            if (!checkMethod(exchange, "POST")) return;
            if (!checkJsonContentType(exchange)) return;

            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
                String group = extractJsonStringField(body, "group");
                String cardIp = extractJsonStringField(body, "cardIp");
                if (group == null || group.isEmpty()) group = "239.1.1.1";

                // Kiểm tra địa chỉ lớp D
                try {
                    InetAddress addr = InetAddress.getByName(group);
                    if (!addr.isMulticastAddress()) {
                        sendErrorResponse(exchange, 400, "Địa chỉ " + group + " không phải địa chỉ Multicast lớp D (224-239.x.x.x)");
                        return;
                    }
                } catch (Exception e) {
                    sendErrorResponse(exchange, 400, "Địa chỉ IP nhóm Multicast không hợp lệ: " + group);
                    return;
                }

                boolean ok = joinMulticastGroup(group, MULTICAST_PORT, cardIp);
                if (ok) {
                    broadcastSystemMessage("Hệ thống đã THAM GIA nhóm Multicast: " + group);
                    sendJsonResponse(exchange, 200, "{\n" +
                            "  \"statusCode\": 200,\n" +
                            "  \"status\": \"OK\",\n" +
                            "  \"message\": \"Đã tham gia nhóm " + group + "\",\n" +
                            "  \"group\": \"" + group + "\"\n" +
                            "}");
                } else {
                    sendErrorResponse(exchange, 500, "Không thể tham gia nhóm Multicast! Kiểm tra card mạng và quyền hệ thống.");
                }
            } catch (Throwable t) {
                sendErrorResponse(exchange, 500, "Lỗi máy chủ nội bộ: " + t.getMessage());
            }
        }
    }

    /**
     * POST /api/multicast/leave : Rời nhóm Multicast
     */
    static class ApiMulticastLeaveHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            if (!checkMethod(exchange, "POST")) return;

            try {
                leaveMulticastGroup();
                broadcastSystemMessage("Hệ thống đã RỜI KHỎI nhóm Multicast.");
                sendJsonResponse(exchange, 200, "{\n" +
                        "  \"statusCode\": 200,\n" +
                        "  \"status\": \"OK\",\n" +
                        "  \"message\": \"Đã rời khỏi nhóm Multicast\"\n" +
                        "}");
            } catch (Throwable t) {
                sendErrorResponse(exchange, 500, "Lỗi máy chủ nội bộ: " + t.getMessage());
            }
        }
    }

    /**
     * POST /api/multicast/send : Bắn tin multicast UDP (Status 201 Created)
     */
    static class ApiMulticastSendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            if (!checkMethod(exchange, "POST")) return;
            if (!checkJsonContentType(exchange)) return;

            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
                String text = extractJsonStringField(body, "message");
                if (text == null || text.isEmpty()) text = extractJsonStringField(body, "text");
                String sender = extractJsonStringField(body, "sender");
                String group = extractJsonStringField(body, "group");
                if (sender == null || sender.isEmpty()) sender = "Admin Dashboard";
                if (group == null || group.isEmpty()) group = currentMulticastGroup;

                if (text == null || text.trim().isEmpty()) {
                    sendErrorResponse(exchange, 400, "Trường 'message' không được để trống!");
                    return;
                }

                broadcastMulticastMessage(sender, text, group);
                sendJsonResponse(exchange, 201, "{\n" +
                        "  \"statusCode\": 201,\n" +
                        "  \"status\": \"Created\",\n" +
                        "  \"message\": \"Đã phát Multicast thành công vào nhóm " + group + "\",\n" +
                        "  \"group\": \"" + group + "\"\n" +
                        "}");
            } catch (Throwable t) {
                sendErrorResponse(exchange, 500, "Lỗi máy chủ nội bộ: " + t.getMessage());
            }
        }
    }

    /**
     * GET /api/chat/messages?since=ID&clientId=ID
     */
    static class ApiChatMessagesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            if (!checkMethod(exchange, "GET")) return;

            try {
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

                WebClientInfo client = clientId != null ? webClients.get(clientId) : null;

                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                for (ChatMessage m : chatHistory) {
                    if (m.id > since) {
                        if ("MULTICAST".equals(m.type)) {
                            if (client != null) {
                                if (!client.joinedMulticast || !m.targetGroup.equals(client.multicastGroup)) {
                                    continue; // Người không vào nhóm sẽ không nhận được
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
            } catch (Throwable t) {
                sendErrorResponse(exchange, 500, "Lỗi máy chủ nội bộ: " + t.getMessage());
            }
        }
    }

    /**
     * POST /api/chat/send : Gửi tin nhắn từ Web Client (Status 201 Created)
     */
    static class ApiChatSendHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            if (!checkMethod(exchange, "POST")) return;
            if (!checkJsonContentType(exchange)) return;

            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
                String sender = extractJsonStringField(body, "sender");
                String text = extractJsonStringField(body, "message");
                if (text == null || text.isEmpty()) text = extractJsonStringField(body, "text");
                String channel = extractJsonStringField(body, "channel");
                String group = extractJsonStringField(body, "group");

                if (sender == null || sender.isEmpty()) sender = "Web Guest";
                if (text == null || text.trim().isEmpty()) {
                    sendErrorResponse(exchange, 400, "Nội dung tin nhắn không được để trống (message is required)");
                    return;
                }

                if ("MULTICAST".equalsIgnoreCase(channel)) {
                    if (group == null || group.isEmpty()) group = currentMulticastGroup;
                    broadcastMulticastMessage(sender, text, group);
                } else {
                    broadcastGeneralMessage(sender, text);
                }

                sendJsonResponse(exchange, 201, "{\n" +
                        "  \"statusCode\": 201,\n" +
                        "  \"status\": \"Created\",\n" +
                        "  \"message\": \"Message sent successfully\"\n" +
                        "}");
            } catch (Throwable t) {
                sendErrorResponse(exchange, 500, "Lỗi máy chủ nội bộ: " + t.getMessage());
            }
        }
    }

    /**
     * POST /api/webclient/register : Đăng ký Web Client mới (Status 201 Created)
     */
    static class ApiWebClientRegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            if (!checkMethod(exchange, "POST")) return;
            if (!checkJsonContentType(exchange)) return;

            try {
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

                String resp = "{\n" +
                        "  \"statusCode\": 201,\n" +
                        "  \"status\": \"Created\",\n" +
                        "  \"id\": " + id + ",\n" +
                        "  \"nickname\": \"" + escapeJson(nick) + "\",\n" +
                        "  \"joined_mcast\": " + joinMcast + ",\n" +
                        "  \"multicast_group\": \"" + mGroup + "\"\n" +
                        "}";
                sendJsonResponse(exchange, 201, resp);
            } catch (Throwable t) {
                sendErrorResponse(exchange, 500, "Lỗi máy chủ nội bộ: " + t.getMessage());
            }
        }
    }

    /**
     * POST /api/webclient/update-group : Cập nhật trạng thái Multicast của Web Client
     */
    static class ApiWebClientUpdateGroupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            if (!checkMethod(exchange, "POST")) return;
            if (!checkJsonContentType(exchange)) return;

            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
                String idStr = extractJsonStringField(body, "clientId");
                String group = extractJsonStringField(body, "group");
                boolean join = body.toLowerCase().matches(".*\"join_multicast\"\\s*:\\s*true.*");

                if (idStr == null || idStr.isEmpty()) {
                    sendErrorResponse(exchange, 400, "Thiếu tham số 'clientId'");
                    return;
                }

                int id;
                try {
                    id = Integer.parseInt(idStr);
                } catch (NumberFormatException e) {
                    sendErrorResponse(exchange, 400, "clientId phải là số nguyên");
                    return;
                }

                WebClientInfo info = webClients.get(id);
                if (info == null) {
                    sendErrorResponse(exchange, 404, "Không tìm thấy Web Client với ID: " + id);
                    return;
                }

                info.joinedMulticast = join;
                if (group != null && !group.isEmpty()) info.multicastGroup = group;
                String statusText = join ? "đã vào nhóm Multicast " + info.multicastGroup : "đã rời nhóm Multicast";
                broadcastSystemMessage("User " + info.nickname + " " + statusText);
                sendJsonResponse(exchange, 200, "{\n" +
                        "  \"statusCode\": 200,\n" +
                        "  \"status\": \"OK\",\n" +
                        "  \"joined\": " + join + ",\n" +
                        "  \"group\": \"" + info.multicastGroup + "\"\n" +
                        "}");
            } catch (Throwable t) {
                sendErrorResponse(exchange, 500, "Lỗi máy chủ nội bộ: " + t.getMessage());
            }
        }
    }

    /**
     * GET /api/network/cards : Danh sách các card mạng
     */
    static class ApiNetworkCardsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1); return;
            }
            if (!checkMethod(exchange, "GET")) return;

            try {
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
            } catch (Throwable t) {
                sendErrorResponse(exchange, 500, "Lỗi máy chủ nội bộ: " + t.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------------
    // TIỆN ÍCH HTTP & STATUS RESPONSE HELPERS
    // ------------------------------------------------------------------------
    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept");
    }

    private static boolean checkMethod(HttpExchange exchange, String expectedMethod) throws IOException {
        if (!expectedMethod.equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", expectedMethod + ", OPTIONS");
            sendErrorResponse(exchange, 405, "Method Not Allowed. Yêu cầu phương thức: " + expectedMethod);
            return false;
        }
        return true;
    }

    private static boolean checkJsonContentType(HttpExchange exchange) throws IOException {
        String ct = exchange.getRequestHeaders().getFirst("Content-Type");
        if (ct == null || !ct.toLowerCase().contains("application/json")) {
            sendErrorResponse(exchange, 415, "Unsupported Media Type. Header Content-Type bắt buộc là application/json");
            return false;
        }
        return true;
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

    private static void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        String statusText = getHttpStatusText(statusCode);
        String json = "{\n" +
                "  \"statusCode\": " + statusCode + ",\n" +
                "  \"status\": \"" + escapeJson(statusText) + "\",\n" +
                "  \"error\": \"" + escapeJson(message) + "\"\n" +
                "}";
        sendJsonResponse(exchange, statusCode, json);
    }

    private static String getHttpStatusText(int code) {
        switch (code) {
            case 200: return "OK";
            case 201: return "Created";
            case 204: return "No Content";
            case 400: return "Bad Request";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 415: return "Unsupported Media Type";
            case 500: return "Internal Server Error";
            default: return "HTTP " + code;
        }
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
    // GIAO DIỆN 1: ADMIN WEB DASHBOARD (HIỂN THỊ RÕ HTTP RESPONSE STATUS)
    // ========================================================================
    private static String renderAdminDashboardHtml() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"vi\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "  <title>Admin Dashboard | TCP Chat, HTTP REST API & Multicast Hub</title>\n" +
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
                "    .container { max-width: 1140px; margin: -20px auto 0; padding: 0 15px; }\n" +
                "    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(330px, 1fr)); gap: 20px; }\n" +
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
                "    /* Bảng Status Code Inspector */\n" +
                "    .status-pill { padding: 3px 9px; border-radius: 12px; font-size: 11px; font-weight: bold; color: white; display: inline-flex; align-items: center; gap: 4px; }\n" +
                "    .status-200 { background: #2E7D32; }\n" +
                "    .status-201 { background: #1565C0; }\n" +
                "    .status-400 { background: #EF6C00; }\n" +
                "    .status-404 { background: #757575; }\n" +
                "    .status-405 { background: #C62828; }\n" +
                "    .status-415 { background: #6A1B9A; }\n" +
                "    .status-500 { background: #B71C1C; }\n" +
                "    .btn-test { padding: 7px 11px; border-radius: 6px; font-size: 12px; font-weight: 600; cursor: pointer; border: 1px solid #CCC; background: #FFF; transition: all 0.2s; }\n" +
                "    .btn-test:hover { background: #F5F5F5; transform: translateY(-1px); }\n" +
                "    .log-table { width: 100%; border-collapse: collapse; font-size: 12px; margin-top: 10px; }\n" +
                "    .log-table th { background: #F5F5F5; padding: 6px 8px; text-align: left; border-bottom: 1px solid #DDD; }\n" +
                "    .log-table td { padding: 6px 8px; border-bottom: 1px solid #EEE; font-family: 'Consolas', monospace; }\n" +
                "    \n" +
                "    #toast { position: fixed; bottom: 20px; right: 20px; background: #2E7D32; color: white; padding: 12px 20px; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.25); display: none; z-index: 9999; font-weight: 600; font-size: 14px; }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "\n" +
                "  <div class=\"header\">\n" +
                "    <h1><i class=\"fa-solid fa-tower-broadcast\"></i> TRUNG TÂM QUẢN TRỊ SERVER & REST API STATUS MONITOR</h1>\n" +
                "    <p>Hệ thống Đa Giao Thức: TCP Socket (Port 5000), HTTP REST API (Port 8080) & UDP Multicast (Port 8000)</p>\n" +
                "    <div class=\"badges\">\n" +
                "      <span class=\"badge\"><span class=\"dot\"></span> TCP Port: <b>5000</b></span>\n" +
                "      <span class=\"badge\"><span class=\"dot\"></span> HTTP Port: <b>8080</b></span>\n" +
                "      <span class=\"badge\" id=\"httpStatusBadge\"><span class=\"dot\"></span> HTTP API: <b id=\"globalHttpStatus\">200 OK</b></span>\n" +
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
                "              🔥 Khi bật Mobile Hotspot trên Laptop, chọn card <b>192.168.137.1 (Hotspot)</b>\n" +
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
                "            <input type=\"text\" id=\"broadcastInput\" class=\"form-control\" value=\"Thông báo: Hệ thống HTTP REST API hoạt động chuẩn xác!\">\n" +
                "          </div>\n" +
                "          <button class=\"btn-red\" onclick=\"sendBroadcast()\"><i class=\"fa-solid fa-bullhorn\"></i> Phát Toàn Server (POST /api/broadcast)</button>\n" +
                "          <button class=\"btn-outline\" onclick=\"sendMulticastOnly()\"><i class=\"fa-solid fa-satellite-dish\"></i> Chỉ Gửi Nhóm Multicast (POST /api/multicast/send)</button>\n" +
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
                "      <!-- Thẻ 4: HTTP Status Code Inspector & Tester (CHƯƠNG 12 TRỰC QUAN HÓA) -->\n" +
                "      <div class=\"card\" style=\"grid-column: 1 / -1;\">\n" +
                "        <div class=\"card-header\">\n" +
                "          <span><i class=\"fa-solid fa-code-branch\" style=\"color:var(--primary);\"></i> 📊 HTTP REST API Inspector & Status Code Tester (Chương 12)</span>\n" +
                "          <span id=\"lastCallTag\" class=\"status-pill status-200\">200 OK</span>\n" +
                "        </div>\n" +
                "        <div class=\"card-body\">\n" +
                "          <p style=\"font-size:13px; color:#555; margin-bottom:12px;\">\n" +
                "            Bấm các nút dưới đây để kiểm tra trực tiếp phản hồi mã <b>HTTP Response Status Codes</b> thực tế từ Server:\n" +
                "          </p>\n" +
                "          <div style=\"display:flex; gap:8px; flex-wrap:wrap; margin-bottom:14px;\">\n" +
                "            <button class=\"btn-test\" onclick=\"testApi('GET', '/api/clients', null, true)\" style=\"border-color:#2E7D32; color:#2E7D32;\">🟢 Test 200 OK (GET /api/clients)</button>\n" +
                "            <button class=\"btn-test\" onclick=\"testApi('POST', '/api/message', {text:'Xin chào từ Web!'}, true)\" style=\"border-color:#1565C0; color:#1565C0;\">🔵 Test 201 Created (POST /api/message)</button>\n" +
                "            <button class=\"btn-test\" onclick=\"testApi('POST', '/api/message', {text:''}, true)\" style=\"border-color:#EF6C00; color:#EF6C00;\">🟠 Test 400 Bad Request (POST text rỗng)</button>\n" +
                "            <button class=\"btn-test\" onclick=\"testApi('GET', '/api/broadcast', null, true)\" style=\"border-color:#C62828; color:#C62828;\">🔴 Test 405 Method Not Allowed (GET vào /api/broadcast)</button>\n" +
                "            <button class=\"btn-test\" onclick=\"testApi('POST', '/api/message', 'plain_text', false)\" style=\"border-color:#6A1B9A; color:#6A1B9A;\">🟣 Test 415 Media Type (Thiếu Header JSON)</button>\n" +
                "            <button class=\"btn-test\" onclick=\"testApi('GET', '/api/khong-ton-tai', null, true)\" style=\"border-color:#757575; color:#757575;\">⚪ Test 404 Not Found (URL sai)</button>\n" +
                "          </div>\n" +
                "          <p style=\"font-size:13px; font-weight:bold; color:var(--primary-dark); margin-bottom:8px;\">\n" +
                "            🔥 Kiểm thử Bài toán Test Chéo 4 Đường (Chương 12 &amp; thêm.md):\n" +
                "          </p>\n" +
                "          <div style=\"display:flex; gap:8px; flex-wrap:wrap; margin-bottom:14px;\">\n" +
                "            <a href=\"/P3.html\" target=\"_blank\" class=\"btn-test\" style=\"border-color:#2E7D32; color:#2E7D32; text-decoration:none; display:inline-flex; align-items:center; gap:5px;\">🔥 Đường 2: Mở P3.html (Java Server Port 8080)</a>\n" +
                "            <a href=\"/P4.html\" target=\"_blank\" class=\"btn-test\" style=\"border-color:#2E7D32; color:#2E7D32; text-decoration:none; display:inline-flex; align-items:center; gap:5px;\">📄 Mở P4.html (Java Server Port 8080)</a>\n" +
                "            <a href=\"http://localhost/P1.html\" target=\"_blank\" class=\"btn-test\" style=\"border-color:#1565C0; color:#1565C0; text-decoration:none; display:inline-flex; align-items:center; gap:5px;\">🔥 Đường 1: Mở P1.html (IIS Port 80)</a>\n" +
                "            <button class=\"btn-test\" onclick=\"testApi('GET', '/P3.html', null, false)\" style=\"border-color:#2E7D32; color:#2E7D32;\">⚡ Fetch P3.html (Status 200)</button>\n" +
                "          </div>\n" +
                "          <div style=\"max-height:160px; overflow-y:auto; border:1px solid #EEE; border-radius:6px;\">\n" +
                "            <table class=\"log-table\">\n" +
                "              <thead>\n" +
                "                <tr><th>Thời gian</th><th>Method</th><th>Endpoint</th><th>HTTP Status Code</th><th>Độ trễ</th><th>Nội dung JSON Phản hồi</th></tr>\n" +
                "              </thead>\n" +
                "              <tbody id=\"httpLogBody\"></tbody>\n" +
                "            </table>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "\n" +
                "      <!-- Thẻ 5: Live Feed Tin Nhắn -->\n" +
                "      <div class=\"card\" style=\"grid-column: 1 / -1;\">\n" +
                "        <div class=\"card-header\">\n" +
                "          <span><i class=\"fa-solid fa-clock-rotate-left\" style=\"color:var(--primary);\"></i> Lịch Sử Toàn Bộ Tin Nhắn (Admin Monitor)</span>\n" +
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
                "    function showToast(msg, status) {\n" +
                "      const t = document.getElementById('toast');\n" +
                "      t.innerText = msg;\n" +
                "      let bg = '#2E7D32';\n" +
                "      if (status >= 400 && status < 500) bg = '#EF6C00';\n" +
                "      if (status >= 500) bg = '#C62828';\n" +
                "      if (status === 201) bg = '#1565C0';\n" +
                "      t.style.background = bg;\n" +
                "      t.style.display = 'block';\n" +
                "      setTimeout(() => { t.style.display = 'none'; }, 3500);\n" +
                "    }\n" +
                "\n" +
                "    function logHttpCall(method, url, status, text, latency, respJson) {\n" +
                "      const tbody = document.getElementById('httpLogBody');\n" +
                "      const row = document.createElement('tr');\n" +
                "      const now = new Date().toTimeString().split(' ')[0];\n" +
                "      let badgeClass = 'status-' + status;\n" +
                "      if (!badgeClass) badgeClass = 'status-200';\n" +
                "      row.innerHTML = `<td>${now}</td><td><b>${method}</b></td><td>${url}</td><td><span class=\"status-pill ${badgeClass}\">${status} ${text}</span></td><td>${latency}ms</td><td style=\"max-width:350px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;\">${escapeHtml(respJson)}</td>`;\n" +
                "      tbody.insertBefore(row, tbody.firstChild);\n" +
                "      if (tbody.children.length > 20) tbody.removeChild(tbody.lastChild);\n" +
                "      document.getElementById('lastCallTag').className = 'status-pill ' + badgeClass;\n" +
                "      document.getElementById('lastCallTag').innerText = `${status} ${text}`;\n" +
                "      document.getElementById('globalHttpStatus').innerText = `${status} ${text}`;\n" +
                "    }\n" +
                "\n" +
                "    async function testApi(method, endpoint, bodyObj, withJsonHeader) {\n" +
                "      const start = performance.now();\n" +
                "      try {\n" +
                "        const opts = { method: method };\n" +
                "        if (withJsonHeader) opts.headers = { 'Content-Type': 'application/json' };\n" +
                "        if (bodyObj) opts.body = withJsonHeader ? JSON.stringify(bodyObj) : bodyObj;\n" +
                "        const res = await fetch(endpoint, opts);\n" +
                "        const latency = Math.round(performance.now() - start);\n" +
                "        const text = await res.text();\n" +
                "        logHttpCall(method, endpoint, res.status, res.statusText || getStatusText(res.status), latency, text);\n" +
                "        showToast(`[HTTP ${res.status} ${getStatusText(res.status)}] ${endpoint}`, res.status);\n" +
                "      } catch (e) {\n" +
                "        logHttpCall(method, endpoint, 0, 'Connection Error', 0, e.message);\n" +
                "        showToast(`❌ [Lỗi mạng]: ${e.message}`, 500);\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    function getStatusText(s) {\n" +
                "      const map = { 200:'OK', 201:'Created', 204:'No Content', 400:'Bad Request', 404:'Not Found', 405:'Method Not Allowed', 415:'Unsupported Media Type', 500:'Internal Server Error' };\n" +
                "      return map[s] || 'HTTP ' + s;\n" +
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
                "      const d = await res.json();\n" +
                "      logHttpCall('POST', '/api/multicast/join', res.status, getStatusText(res.status), 15, JSON.stringify(d));\n" +
                "      if (res.ok) { showToast('✅ [HTTP ' + res.status + ' OK] Đã vào nhóm ' + grp, 200); loadMulticastStatus(); }\n" +
                "      else { showToast('❌ [HTTP ' + res.status + '] ' + (d.error || 'Lỗi'), res.status); }\n" +
                "    }\n" +
                "\n" +
                "    async function leaveMulticast() {\n" +
                "      const res = await fetch('/api/multicast/leave', { method: 'POST' });\n" +
                "      const d = await res.json();\n" +
                "      logHttpCall('POST', '/api/multicast/leave', res.status, getStatusText(res.status), 10, JSON.stringify(d));\n" +
                "      if (res.ok) { showToast('⏹ [HTTP 200 OK] Đã rời nhóm Multicast', 200); loadMulticastStatus(); }\n" +
                "    }\n" +
                "\n" +
                "    async function sendBroadcast() {\n" +
                "      const text = document.getElementById('broadcastInput').value.trim();\n" +
                "      if (!text) return showToast('Nhập tin nhắn!', 400);\n" +
                "      const res = await fetch('/api/broadcast', {\n" +
                "        method: 'POST', headers: { 'Content-Type': 'application/json' },\n" +
                "        body: JSON.stringify({ message: text })\n" +
                "      });\n" +
                "      const d = await res.json();\n" +
                "      logHttpCall('POST', '/api/broadcast', res.status, getStatusText(res.status), 12, JSON.stringify(d));\n" +
                "      if (res.ok) showToast('✅ [HTTP ' + res.status + ' Created] Đã phát toàn server!', 201);\n" +
                "    }\n" +
                "\n" +
                "    async function sendMulticastOnly() {\n" +
                "      const text = document.getElementById('broadcastInput').value.trim();\n" +
                "      const grp = document.getElementById('mcastGroupInput').value.trim();\n" +
                "      if (!text) return showToast('Nhập tin nhắn!', 400);\n" +
                "      const res = await fetch('/api/multicast/send', {\n" +
                "        method: 'POST', headers: { 'Content-Type': 'application/json' },\n" +
                "        body: JSON.stringify({ message: text, sender: 'Admin', group: grp })\n" +
                "      });\n" +
                "      const d = await res.json();\n" +
                "      logHttpCall('POST', '/api/multicast/send', res.status, getStatusText(res.status), 12, JSON.stringify(d));\n" +
                "      if (res.ok) showToast('📡 [HTTP ' + res.status + ' Created] Đã phát vào nhóm Multicast ' + grp, 201);\n" +
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
                "    function escapeHtml(t) {\n" +
                "      if (!t) return '';\n" +
                "      return t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\"/g, '&quot;');\n" +
                "    }\n" +
                "\n" +
                "    loadNetworkCards(); loadMulticastStatus(); loadUsers(); loadMessages();\n" +
                "    setInterval(loadUsers, 2000); setInterval(loadMessages, 1000);\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>";
    }

    // ========================================================================
    // GIAO DIỆN 2: WEB CHAT CLIENT (HIỂN THỊ RÕ HTTP STATUS CODES TRỰC QUAN)
    // ========================================================================
    private static String renderWebChatClientHtml() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"vi\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "  <title>Phòng Chat Web | Chat Chung & Multicast (REST API Status)</title>\n" +
                "  <link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">\n" +
                "  <style>\n" +
                "    :root { --primary: #C62828; --accent: #E53935; --bg: #F5F5F5; }\n" +
                "    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Segoe UI', system-ui, sans-serif; }\n" +
                "    body { background: var(--bg); display: flex; flex-direction: column; height: 100vh; }\n" +
                "    \n" +
                "    .topbar {\n" +
                "      background: linear-gradient(135deg, #8E0000, var(--primary));\n" +
                "      color: white; padding: 12px 20px; display: flex; align-items: center; justify-content: space-between;\n" +
                "      box-shadow: 0 2px 10px rgba(0,0,0,0.15);\n" +
                "    }\n" +
                "    .topbar h2 { font-size: 18px; display: flex; align-items: center; gap: 8px; }\n" +
                "    .user-tag { background: rgba(255,255,255,0.2); padding: 4px 12px; border-radius: 15px; font-size: 13px; font-weight: 600; display: inline-flex; align-items: center; gap: 6px; }\n" +
                "    \n" +
                "    .chat-container { flex: 1; display: flex; flex-direction: column; max-width: 920px; width: 100%; margin: 12px auto; background: white; border-radius: 12px; box-shadow: 0 4px 15px rgba(0,0,0,0.06); overflow: hidden; }\n" +
                "    \n" +
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
                "    .bubble { max-width: 75%; padding: 10px 14px; border-radius: 12px; font-size: 14px; line-height: 1.4; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }\n" +
                "    .bubble.mine { align-self: flex-end; background: var(--primary); color: white; border-bottom-right-radius: 2px; }\n" +
                "    .bubble.mine-mcast { align-self: flex-end; background: #2E7D32; color: white; border-bottom-right-radius: 2px; }\n" +
                "    .bubble.other { align-self: flex-start; background: white; border: 1px solid #FFCDD2; color: #212121; border-bottom-left-radius: 2px; }\n" +
                "    .bubble.mcast { align-self: flex-start; background: #E8F5E9; border: 1.5px solid #A5D6A7; color: #1B5E20; border-bottom-left-radius: 2px; }\n" +
                "    .bubble.sys { align-self: center; background: #FFEBEE; color: #C62828; font-size: 12px; font-weight: 600; border-radius: 20px; padding: 4px 12px; }\n" +
                "    .meta { font-size: 11px; opacity: 0.8; margin-bottom: 3px; display: block; }\n" +
                "    \n" +
                "    .input-bar { padding: 10px 18px; border-top: 1px solid #FFCDD2; display: flex; gap: 10px; background: white; }\n" +
                "    .input-bar input { flex: 1; padding: 12px 15px; border: 1.5px solid #FFCDD2; border-radius: 8px; font-size: 14px; outline: none; }\n" +
                "    .input-bar input:focus { border-color: var(--primary); }\n" +
                "    .input-bar button { background: var(--primary); color: white; border: none; padding: 0 20px; border-radius: 8px; font-weight: bold; cursor: pointer; display: flex; align-items: center; gap: 6px; }\n" +
                "    \n" +
                "    .status-strip { background: #FFF9F9; border-top: 1px dashed #FFCDD2; padding: 6px 18px; font-size: 11.5px; color: #666; display: flex; justify-content: space-between; align-items: center; }\n" +
                "    .status-badge { padding: 2px 7px; border-radius: 10px; font-weight: bold; font-size: 11px; }\n" +
                "    .badge-200 { background: #E8F5E9; color: #2E7D32; }\n" +
                "    .badge-201 { background: #E3F2FD; color: #1565C0; }\n" +
                "    .badge-err { background: #FFEBEE; color: #C62828; }\n" +
                "    \n" +
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
                "      <span id=\"apiStatusTag\" class=\"user-tag\" style=\"background:#1B5E20;\"><i class=\"fa-solid fa-server\"></i> HTTP API: <b>200 OK</b></span>\n" +
                "      <span id=\"mcastStatusTag\" class=\"user-tag\" style=\"background:#757575; cursor:pointer;\" onclick=\"openGroupModal()\">⚪ Chưa vào Multicast (Bấm để tham gia)</span>\n" +
                "      <span id=\"myNickTag\" class=\"user-tag\">Khách</span>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "\n" +
                "  <!-- CHAT CONTAINER -->\n" +
                "  <div class=\"chat-container\">\n" +
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
                "    \n" +
                "    <!-- THANH HIỂN THỊ HTTP RESPONSE STATUS TRỰC TIẾP -->\n" +
                "    <div class=\"status-strip\">\n" +
                "      <div>\n" +
                "        <i class=\"fa-solid fa-signal\" style=\"color:#2E7D32;\"></i> HTTP API Status: \n" +
                "        <span id=\"chatHttpStatus\" class=\"status-badge badge-200\">HTTP 200 OK</span>\n" +
                "      </div>\n" +
                "      <div id=\"lastApiDetail\" style=\"font-size:11px;\">Polling tin nhắn mỗi 800ms (GET /api/chat/messages)</div>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "\n" +
                "  <script>\n" +
                "    let myId = 0;\n" +
                "    let myNick = 'User';\n" +
                "    let joinedMcast = false;\n" +
                "    let currentGroup = '239.1.1.1';\n" +
                "    let activeChannel = 'GENERAL';\n" +
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
                "        const res = await fetch('/api/webclient/register', {\n" +
                "          method: 'POST', headers: { 'Content-Type': 'application/json' },\n" +
                "          body: JSON.stringify({ nickname: myNick, join_multicast: joinedMcast, multicast_group: currentGroup })\n" +
                "        });\n" +
                "        const data = await res.json();\n" +
                "        myId = data.id;\n" +
                "        setHttpBadge(res.status, 'POST /api/webclient/register');\n" +
                "        startPolling();\n" +
                "      } else {\n" +
                "        const res = await fetch('/api/webclient/update-group', {\n" +
                "          method: 'POST', headers: { 'Content-Type': 'application/json' },\n" +
                "          body: JSON.stringify({ clientId: myId, join_multicast: joinedMcast, group: currentGroup })\n" +
                "        });\n" +
                "        setHttpBadge(res.status, 'POST /api/webclient/update-group');\n" +
                "      }\n" +
                "      \n" +
                "      if (!joinedMcast && activeChannel === 'MULTICAST') {\n" +
                "        selectChannel('GENERAL');\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    function setHttpBadge(status, action) {\n" +
                "      const badge = document.getElementById('chatHttpStatus');\n" +
                "      const topTag = document.getElementById('apiStatusTag');\n" +
                "      const detail = document.getElementById('lastApiDetail');\n" +
                "      \n" +
                "      if (status === 201) {\n" +
                "        badge.className = 'status-badge badge-201';\n" +
                "        badge.innerText = 'HTTP 201 Created (Thành công)';\n" +
                "        topTag.style.background = '#1565C0';\n" +
                "        topTag.innerHTML = `<i class=\"fa-solid fa-server\"></i> HTTP: <b>201 Created</b>`;\n" +
                "      } else if (status === 200) {\n" +
                "        badge.className = 'status-badge badge-200';\n" +
                "        badge.innerText = 'HTTP 200 OK (Sẵn sàng)';\n" +
                "        topTag.style.background = '#1B5E20';\n" +
                "        topTag.innerHTML = `<i class=\"fa-solid fa-server\"></i> HTTP: <b>200 OK</b>`;\n" +
                "      } else {\n" +
                "        badge.className = 'status-badge badge-err';\n" +
                "        badge.innerText = `HTTP ${status} Lỗi`;\n" +
                "        topTag.style.background = '#B71C1C';\n" +
                "        topTag.innerHTML = `<i class=\"fa-solid fa-circle-exclamation\"></i> HTTP: <b>${status} Error</b>`;\n" +
                "      }\n" +
                "      if (action) detail.innerText = action;\n" +
                "    }\n" +
                "\n" +
                "    function openGroupModal() {\n" +
                "      document.getElementById('nickGroupRow').style.display = 'none';\n" +
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
                "      try {\n" +
                "        const res = await fetch('/api/chat/send', {\n" +
                "          method: 'POST', headers: { 'Content-Type': 'application/json' },\n" +
                "          body: JSON.stringify({\n" +
                "            sender: myNick,\n" +
                "            message: text,\n" +
                "            channel: activeChannel,\n" +
                "            group: currentGroup\n" +
                "          })\n" +
                "        });\n" +
                "        setHttpBadge(res.status, `Gửi tin thành công (${res.status} Created) lúc ${new Date().toLocaleTimeString()}`);\n" +
                "        setTimeout(() => setHttpBadge(200, 'Polling tin nhắn mỗi 800ms'), 2500);\n" +
                "      } catch (e) {\n" +
                "        setHttpBadge(500, 'Lỗi kết nối tới Server');\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    function handleKey(e) {\n" +
                "      if (e.key === 'Enter') sendMsg();\n" +
                "    }\n" +
                "\n" +
                "    function startPolling() {\n" +
                "      setInterval(async () => {\n" +
                "        try {\n" +
                "          const res = await fetch(`/api/chat/messages?since=${lastId}&clientId=${myId}`);\n" +
                "          if (res.ok) {\n" +
                "            const msgs = await res.json();\n" +
                "            if (msgs.length > 0) {\n" +
                "              const box = document.getElementById('chatBox');\n" +
                "              msgs.forEach(m => {\n" +
                "                lastId = Math.max(lastId, m.id);\n" +
                "                const div = document.createElement('div');\n" +
                "                \n" +
                "                if (m.type === 'SYSTEM') {\n" +
                "                  div.className = 'bubble sys';\n" +
                "                  div.innerText = m.text;\n" +
                "                } else if (m.sender === myNick) {\n" +
                "                  div.className = m.type === 'MULTICAST' ? 'bubble mine-mcast' : 'bubble mine';\n" +
                "                  const mcastTag = m.type === 'MULTICAST' ? `[Nhóm ${m.group}] ` : '';\n" +
                "                  div.innerHTML = `<span class=\"meta\">Tôi • ${mcastTag}${m.time}</span>${escapeHtml(m.text)}`;\n" +
                "                } else if (m.type === 'MULTICAST') {\n" +
                "                  div.className = 'bubble mcast';\n" +
                "                  div.innerHTML = `<span class=\"meta\"><i class=\"fa-solid fa-satellite-dish\"></i> ${escapeHtml(m.sender)} [NHÓM ${m.group}] • ${m.time}</span>${escapeHtml(m.text)}`;\n" +
                "                } else {\n" +
                "                  div.className = 'bubble other';\n" +
                "                  div.innerHTML = `<span class=\"meta\"><i class=\"fa-solid fa-user\"></i> ${escapeHtml(m.sender)} [Phòng chung] • ${m.time}</span>${escapeHtml(m.text)}`;\n" +
                "                }\n" +
                "                box.appendChild(div);\n" +
                "              });\n" +
                "              box.scrollTop = box.scrollHeight;\n" +
                "            }\n" +
                "          }\n" +
                "        } catch (e) {}\n" +
                "      }, 800);\n" +
                "    }\n" +
                "\n" +
                "    function escapeHtml(t) {\n" +
                "      if (!t) return '';\n" +
                "      return t.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/\"/g, '&quot;');\n" +
                "    }\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
