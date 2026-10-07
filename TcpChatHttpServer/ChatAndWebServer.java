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

/**
 * ============================================================================
 * CHƯƠNG TRÌNH KẾT HỢP: JAVA TCP CHAT SERVER & HTTP REST API BACKEND
 * ============================================================================
 * - Module 1: TCP Chat Server (Port 5000) - Quản lý nhiều client, đa luồng, broadcast.
 * - Module 2: HTTP Web Server (Port 8080) - REST API & Web Dashboard màu đỏ rực rỡ.
 * - Điểm kết nối chung: Quản lý danh sách Client bằng ConcurrentHashMap an toàn cho luồng.
 * ============================================================================
 */
public class ChatAndWebServer {

    // Cổng dịch vụ
    public static final int TCP_PORT = 5000;
    public static final int HTTP_PORT = 8080;

    // Bộ sinh Client ID tự tăng, an toàn đa luồng
    private static final AtomicInteger clientIdCounter = new AtomicInteger(0);

    // Danh sách các Client đang kết nối: Key = ClientID, Value = ClientHandler
    // Dùng ConcurrentHashMap để cả luồng TCP và luồng HTTP truy xuất đồng thời an toàn
    private static final ConcurrentHashMap<Integer, ClientHandler> clients = new ConcurrentHashMap<>();

    // Lịch sử tin nhắn gần nhất (tối đa 50 tin)
    private static final ConcurrentLinkedQueue<String> messageHistory = new ConcurrentLinkedQueue<>();
    private static final int MAX_HISTORY = 50;

    // Thời điểm server khởi động
    private static final LocalDateTime startTime = LocalDateTime.now();

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("   🚀 KHỞI ĐỘNG HỆ THỐNG: TCP CHAT SERVER & HTTP BACKEND");
        System.out.println("==================================================================");

        // Khởi động TCP Server trên 1 Thread riêng
        Thread tcpThread = new Thread(ChatAndWebServer::startTcpServer, "TCP-Server-Thread");
        tcpThread.start();

        // Khởi động HTTP Server trên 1 Thread riêng
        Thread httpThread = new Thread(ChatAndWebServer::startHttpServer, "HTTP-Server-Thread");
        httpThread.start();

        System.out.println("[INFO] Cả 2 Server đã được khởi chạy song song trên 2 luồng độc lập!");
        System.out.println("[INFO] TCP Chat Server lắng nghe tại : " + TCP_PORT);
        System.out.println("[INFO] HTTP Web Server lắng nghe tại : http://localhost:" + HTTP_PORT + "/");
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
                // Chấp nhận client kết nối tới (Blocking call)
                Socket socket = serverSocket.accept();

                // Cấp phát ClientID duy nhất
                int clientId = clientIdCounter.incrementAndGet();

                // Tạo đối tượng xử lý riêng cho client này
                ClientHandler handler = new ClientHandler(socket, clientId);
                clients.put(clientId, handler);

                System.out.println("🟢 [TCP Connect] Client #" + clientId + " kết nối từ " +
                        socket.getInetAddress().getHostAddress() + ":" + socket.getPort() +
                        " (Tổng online: " + clients.size() + ")");

                // Giao việc cho ThreadPool xử lý luồng riêng
                clientThreadPool.execute(handler);
            }
        } catch (IOException e) {
            System.err.println("❌ [TCP Server Lỗi]: " + e.getMessage());
        } finally {
            clientThreadPool.shutdown();
        }
    }

    /**
     * Broadcast tin nhắn tới tất cả client TCP đang kết nối.
     * @param message Nội dung tin nhắn
     * @param excludeClientId ClientId không gửi (ví dụ người gửi), truyền null nếu gửi cho tất cả
     */
    public static void broadcast(String message, Integer excludeClientId) {
        // Lưu vào lịch sử
        messageHistory.offer(message);
        if (messageHistory.size() > MAX_HISTORY) {
            messageHistory.poll();
        }

        // In ra console server
        System.out.println("[Broadcast] " + message);

        // Duyệt danh sách client thread-safe
        for (Map.Entry<Integer, ClientHandler> entry : clients.entrySet()) {
            int targetId = entry.getKey();
            ClientHandler handler = entry.getValue();

            if (excludeClientId != null && targetId == excludeClientId) {
                continue; // Không gửi lại cho chính người vừa chat
            }

            try {
                handler.sendMessage(message);
            } catch (IOException e) {
                System.err.println("[-] Gặp lỗi khi gửi tới Client #" + targetId + ", ngắt kết nối.");
                removeClient(targetId);
            }
        }
    }

    /**
     * Xóa client khi ngắt kết nối và thông báo cho các client còn lại.
     */
    public static void removeClient(int clientId) {
        ClientHandler handler = clients.remove(clientId);
        if (handler != null) {
            handler.closeQuietly();
            String leaveMsg = "[Hệ thống] Client #" + clientId + " đã rời phòng chat. (Còn lại: " + clients.size() + ")";
            System.out.println("🔴 [TCP Disconnect] " + leaveMsg);
            broadcast(leaveMsg, null);
        }
    }

    /**
     * Lớp xử lý luồng riêng cho mỗi Client TCP
     */
    static class ClientHandler implements Runnable {
        private final Socket socket;
        private final int clientId;
        private BufferedReader reader;
        private BufferedWriter writer;
        private volatile boolean isRunning = true;

        public ClientHandler(Socket socket, int clientId) throws IOException {
            this.socket = socket;
            this.clientId = clientId;
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        public int getClientId() {
            return clientId;
        }

        public String getRemoteAddress() {
            return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
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
                // 1. Chào mừng client vừa vào
                sendMessage("=================================================");
                sendMessage("🎉 CHÀO MỪNG BẠN ĐẾN VỚI PHÒNG CHAT TCP JAVA!");
                sendMessage("👉 ID của bạn là: #" + clientId);
                sendMessage("👉 Gõ nội dung rồi nhấn Enter để gửi.");
                sendMessage("👉 Gõ /quit để thoát.");
                sendMessage("=================================================");

                // Báo cho các client khác biết có người mới vào
                String joinMsg = "[Hệ thống] Client #" + clientId + " đã tham gia phòng chat. (Online: " + clients.size() + ")";
                broadcast(joinMsg, clientId);

                // 2. Vòng lặp nhận tin nhắn từ Client
                String line;
                while (isRunning && (line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    if (line.equalsIgnoreCase("/quit") || line.equalsIgnoreCase("exit")) {
                        sendMessage("[Hệ thống] Tạm biệt bạn!");
                        break;
                    }

                    // Broadcast tin nhắn của client tới tất cả mọi người
                    String chatMsg = "[Client #" + clientId + "]: " + line;
                    broadcast(chatMsg, null); // Gửi cả cho người gửi để xác nhận tin
                }
            } catch (IOException e) {
                // Client đột ngột đóng kết nối hoặc mạng gián đoạn
            } finally {
                isRunning = false;
                removeClient(clientId);
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
    // MODULE 2: HTTP REST API & WEB SERVER (Chương 12)
    // ========================================================================
    private static void startHttpServer() {
        try {
            HttpServer httpServer = HttpServer.create(new InetSocketAddress(HTTP_PORT), 0);

            // Đăng ký các Endpoint (Routing)
            httpServer.createContext("/", new WebDashboardHandler());
            httpServer.createContext("/api/status", new StatusApiHandler());
            httpServer.createContext("/api/users", new UsersApiHandler());
            httpServer.createContext("/api/broadcast", new BroadcastApiHandler());

            // Thiết lập ThreadPool xử lý các HTTP Request
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.start();

            System.out.println("✅ [HTTP Server] Đang chạy tại http://localhost:" + HTTP_PORT + "/");
        } catch (IOException e) {
            System.err.println("❌ [HTTP Server Lỗi]: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // HTTP HANDLERS
    // ------------------------------------------------------------------------

    /**
     * GET / -> Trang Web Dashboard quản trị hiện đại, phong cách ĐỎ CHỦ ĐẠO
     */
    static class WebDashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // Nếu người dùng gọi đúng path gốc "/"
            if (!"/".equals(exchange.getRequestURI().getPath())) {
                sendJsonResponse(exchange, 404, "{\"error\": \"Not Found\"}");
                return;
            }

            String html = generateRedDashboardHtml();
            sendHtmlResponse(exchange, 200, html);
        }
    }

    /**
     * GET /api/status -> Trả về Status 200 OK
     */
    static class StatusApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\": \"Method Not Allowed. Use GET.\"}");
                return;
            }

            String json = "{\n" +
                    "  \"status\": \"OK\",\n" +
                    "  \"message\": \"Server đang hoạt động tốt!\",\n" +
                    "  \"uptime_since\": \"" + startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\",\n" +
                    "  \"tcp_port\": " + TCP_PORT + ",\n" +
                    "  \"http_port\": " + HTTP_PORT + ",\n" +
                    "  \"online_count\": " + clients.size() + "\n" +
                    "}";

            sendJsonResponse(exchange, 200, json);
        }
    }

    /**
     * GET /api/users -> Truy cập ConcurrentHashMap, trả về JSON danh sách ClientID đang online
     */
    static class UsersApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\": \"Method Not Allowed. Use GET.\"}");
                return;
            }

            // Đọc danh sách Client từ ConcurrentHashMap
            List<Integer> userIds = new ArrayList<>(clients.keySet());
            Collections.sort(userIds);

            StringBuilder json = new StringBuilder("{\n");
            json.append("  \"status\": \"OK\",\n");
            json.append("  \"online_count\": ").append(userIds.size()).append(",\n");
            json.append("  \"users\": [");
            for (int i = 0; i < userIds.size(); i++) {
                int id = userIds.get(i);
                ClientHandler h = clients.get(id);
                String addr = h != null ? h.getRemoteAddress() : "unknown";
                json.append("{\"id\": ").append(id)
                    .append(", \"address\": \"").append(addr).append("\"}");
                if (i < userIds.size() - 1) json.append(", ");
            }
            json.append("]\n}");

            sendJsonResponse(exchange, 200, json.toString());
        }
    }

    /**
     * POST /api/broadcast -> Nhận Request Body JSON {"message": "..."}, gọi hàm Broadcast của TCP Server
     */
    static class BroadcastApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, 405, "{\"error\": \"Method Not Allowed. Use POST.\"}");
                return;
            }

            // Đọc body request
            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();

            // Trích xuất trường message hoặc text từ JSON (tự parse không cần thư viện ngoài)
            String message = extractJsonStringField(body, "message");
            if (message == null || message.isEmpty()) {
                message = extractJsonStringField(body, "text");
            }

            if (message == null || message.isEmpty()) {
                sendJsonResponse(exchange, 400, "{\"error\": \"Bad Request. JSON body phải chứa trường 'message'! Ví dụ: {\\\"message\\\": \\\"Hello\\\"}\"}");
                return;
            }

            // Gọi hàm broadcast của TCP Server (gửi đến tất cả các client đang kết nối)
            String broadcastText = "[THÔNG BÁO TỪ WEB HTTP]: " + message;
            broadcast(broadcastText, null);

            // Trả về Status 201 Created theo đúng yêu cầu
            String responseJson = "{\n" +
                    "  \"status\": \"Created\",\n" +
                    "  \"code\": 201,\n" +
                    "  \"broadcast_message\": \"" + escapeJson(message) + "\",\n" +
                    "  \"recipients_count\": " + clients.size() + ",\n" +
                    "  \"timestamp\": \"" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\"\n" +
                    "}";

            sendJsonResponse(exchange, 201, responseJson);
        }
    }

    // ------------------------------------------------------------------------
    // TIỆN ÍCH HELPER CHO HTTP
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
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendHtmlResponse(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
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
        if (quoteStart == -1) return null;

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

    // ------------------------------------------------------------------------
    // GIAO DIỆN WEB DASHBOARD (MÀU ĐỎ CHỦ ĐẠO - RED CRIMSON THEME)
    // ------------------------------------------------------------------------
    private static String generateRedDashboardHtml() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"vi\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "  <title>Trung Tâm Quản Trị Chat & API | Java Network</title>\n" +
                "  <link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css\">\n" +
                "  <style>\n" +
                "    :root {\n" +
                "      --primary: #C62828;\n" +
                "      --primary-dark: #8E0000;\n" +
                "      --primary-light: #FF5F52;\n" +
                "      --primary-bg: #FFF5F5;\n" +
                "      --accent: #E53935;\n" +
                "      --bg: #F8F9FA;\n" +
                "      --card-bg: #FFFFFF;\n" +
                "      --text: #212121;\n" +
                "      --text-muted: #757575;\n" +
                "      --border: #FFCDD2;\n" +
                "      --success: #2E7D32;\n" +
                "    }\n" +
                "    * { box-sizing: border-box; margin: 0; padding: 0; font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; }\n" +
                "    body { background-color: var(--bg); color: var(--text); padding-bottom: 40px; }\n" +
                "    \n" +
                "    /* Header Đỏ */\n" +
                "    .header {\n" +
                "      background: linear-gradient(135deg, var(--primary-dark) 0%, var(--primary) 50%, var(--accent) 100%);\n" +
                "      color: white;\n" +
                "      padding: 30px 20px;\n" +
                "      box-shadow: 0 4px 15px rgba(198, 40, 40, 0.3);\n" +
                "      text-align: center;\n" +
                "    }\n" +
                "    .header h1 { font-size: 26px; font-weight: 700; margin-bottom: 8px; }\n" +
                "    .header p { font-size: 14px; opacity: 0.9; }\n" +
                "    .badge-status {\n" +
                "      display: inline-flex; align-items: center; gap: 8px;\n" +
                "      background: rgba(255, 255, 255, 0.2); padding: 6px 14px; border-radius: 20px;\n" +
                "      margin-top: 12px; font-size: 13px; font-weight: 600;\n" +
                "    }\n" +
                "    .status-dot { width: 10px; height: 10px; background: #00E676; border-radius: 50%; box-shadow: 0 0 8px #00E676; }\n" +
                "    \n" +
                "    .container { max-width: 1050px; margin: -20px auto 0; padding: 0 15px; }\n" +
                "    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(310px, 1fr)); gap: 20px; }\n" +
                "    \n" +
                "    /* Card UI */\n" +
                "    .card {\n" +
                "      background: var(--card-bg);\n" +
                "      border-radius: 12px;\n" +
                "      box-shadow: 0 3px 12px rgba(0,0,0,0.06);\n" +
                "      border: 1px solid var(--border);\n" +
                "      overflow: hidden;\n" +
                "      transition: transform 0.2s, box-shadow 0.2s;\n" +
                "    }\n" +
                "    .card:hover { transform: translateY(-3px); box-shadow: 0 6px 18px rgba(198, 40, 40, 0.12); }\n" +
                "    .card-header {\n" +
                "      background: var(--primary-bg);\n" +
                "      border-bottom: 2px solid var(--border);\n" +
                "      padding: 14px 18px;\n" +
                "      display: flex; align-items: center; gap: 10px;\n" +
                "      color: var(--primary-dark);\n" +
                "      font-size: 16px; font-weight: 700;\n" +
                "    }\n" +
                "    .card-header i { font-size: 18px; color: var(--primary); }\n" +
                "    .card-body { padding: 18px; }\n" +
                "    \n" +
                "    /* Users List */\n" +
                "    .user-list { list-style: none; max-height: 220px; overflow-y: auto; }\n" +
                "    .user-item {\n" +
                "      display: flex; align-items: center; justify-content: space-between;\n" +
                "      padding: 10px 12px;\n" +
                "      border-bottom: 1px dashed var(--border);\n" +
                "      font-size: 14px;\n" +
                "    }\n" +
                "    .user-item:last-child { border-bottom: none; }\n" +
                "    .user-pill {\n" +
                "      background: var(--primary); color: white;\n" +
                "      padding: 3px 10px; border-radius: 12px; font-weight: bold; font-size: 12px;\n" +
                "    }\n" +
                "    \n" +
                "    /* Broadcast Form */\n" +
                "    .form-group { margin-bottom: 14px; }\n" +
                "    .form-group label { display: block; font-size: 13px; font-weight: 600; margin-bottom: 6px; color: var(--primary-dark); }\n" +
                "    .form-control {\n" +
                "      width: 100%; padding: 10px 12px; border: 1.5px solid var(--border); border-radius: 8px;\n" +
                "      font-size: 14px; outline: none; transition: border-color 0.2s;\n" +
                "    }\n" +
                "    .form-control:focus { border-color: var(--primary); box-shadow: 0 0 0 3px rgba(229, 57, 53, 0.15); }\n" +
                "    .btn-red {\n" +
                "      background: linear-gradient(135deg, var(--primary) 0%, var(--accent) 100%);\n" +
                "      color: white; border: none; padding: 11px 18px; border-radius: 8px;\n" +
                "      font-size: 14px; font-weight: 700; cursor: pointer; width: 100%;\n" +
                "      display: flex; align-items: center; justify-content: center; gap: 8px;\n" +
                "      box-shadow: 0 3px 8px rgba(198, 40, 40, 0.3);\n" +
                "      transition: opacity 0.2s, transform 0.1s;\n" +
                "    }\n" +
                "    .btn-red:hover { opacity: 0.95; transform: scale(1.01); }\n" +
                "    .btn-red:active { transform: scale(0.99); }\n" +
                "    \n" +
                "    /* API Info */\n" +
                "    .api-tag { display: inline-block; padding: 2px 7px; border-radius: 4px; font-size: 11px; font-weight: bold; margin-right: 6px; }\n" +
                "    .api-get { background: #E3F2FD; color: #1565C0; }\n" +
                "    .api-post { background: #FFEBEE; color: var(--primary); }\n" +
                "    .api-item {\n" +
                "      padding: 9px 0; border-bottom: 1px solid #F0F0F0; font-size: 13px;\n" +
                "      display: flex; align-items: center; justify-content: space-between;\n" +
                "    }\n" +
                "    .api-item a { color: var(--primary); text-decoration: none; font-weight: 600; }\n" +
                "    .api-item a:hover { text-decoration: underline; }\n" +
                "    \n" +
                "    /* Alert Toast */\n" +
                "    #toast {\n" +
                "      position: fixed; bottom: 20px; right: 20px;\n" +
                "      background: #2E7D32; color: white;\n" +
                "      padding: 12px 20px; border-radius: 8px;\n" +
                "      box-shadow: 0 4px 12px rgba(0,0,0,0.2);\n" +
                "      display: none; z-index: 9999; font-weight: 600;\n" +
                "    }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "\n" +
                "  <div class=\"header\">\n" +
                "    <h1><i class=\"fa-solid fa-server\"></i> JAVA CHAT SERVER & HTTP BACKEND</h1>\n" +
                "    <p>Hệ thống tích hợp TCP Multi-Client (Chương 11) & HTTP REST Server (Chương 12)</p>\n" +
                "    <div class=\"badge-status\">\n" +
                "      <span class=\"status-dot\"></span> TCP Port: <b>5000</b> &nbsp;|&nbsp; HTTP Port: <b>8080</b>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "\n" +
                "  <div class=\"container\">\n" +
                "    <div class=\"grid\">\n" +
                "      \n" +
                "      <!-- Card 1: Broadcast -->\n" +
                "      <div class=\"card\">\n" +
                "        <div class=\"card-header\">\n" +
                "          <i class=\"fa-solid fa-bullhorn\"></i> Gửi Broadcast Tới TCP Clients\n" +
                "        </div>\n" +
                "        <div class=\"card-body\">\n" +
                "          <p style=\"font-size: 13px; color: var(--text-muted); margin-bottom: 12px;\">\n" +
                "            Gửi thông báo từ Web Server tới tất cả client đang kết nối qua TCP Socket (Port 5000).\n" +
                "          </p>\n" +
                "          <div class=\"form-group\">\n" +
                "            <label>Nội dung thông báo (Message):</label>\n" +
                "            <textarea id=\"bcastMsg\" rows=\"3\" class=\"form-control\" placeholder=\"Nhập thông báo bảo trì hoặc tin nhắn toàn hệ thống...\">Hệ thống sẽ bảo trì sau 5 phút nữa!</textarea>\n" +
                "          </div>\n" +
                "          <button class=\"btn-red\" onclick=\"sendBroadcast()\">\n" +
                "            <i class=\"fa-solid fa-paper-plane\"></i> GỬI BROADCAST NGAY\n" +
                "          </button>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "\n" +
                "      <!-- Card 2: Danh sách Clients Online -->\n" +
                "      <div class=\"card\">\n" +
                "        <div class=\"card-header\">\n" +
                "          <i class=\"fa-solid fa-users\"></i> Danh Sách Client TCP Online \n" +
                "          <span id=\"countPill\" style=\"margin-left:auto; font-size:12px; background:var(--primary); color:white; padding:2px 8px; border-radius:10px;\">0 online</span>\n" +
                "        </div>\n" +
                "        <div class=\"card-body\">\n" +
                "          <ul id=\"userList\" class=\"user-list\">\n" +
                "            <li style=\"color: var(--text-muted); text-align: center; padding: 20px;\">Đang quét danh sách client...</li>\n" +
                "          </ul>\n" +
                "          <button onclick=\"fetchUsers()\" style=\"margin-top: 10px; width: 100%; background: none; border: 1px dashed var(--primary); color: var(--primary); padding: 7px; border-radius: 6px; cursor: pointer; font-weight: 600;\">\n" +
                "            <i class=\"fa-solid fa-arrows-rotate\"></i> Làm mới danh sách\n" +
                "          </button>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "\n" +
                "      <!-- Card 3: REST API Documentation -->\n" +
                "      <div class=\"card\">\n" +
                "        <div class=\"card-header\">\n" +
                "          <i class=\"fa-solid fa-code\"></i> Danh Sách Endpoint REST API\n" +
                "        </div>\n" +
                "        <div class=\"card-body\">\n" +
                "          <div class=\"api-item\">\n" +
                "            <div><span class=\"api-tag api-get\">GET</span> <a href=\"/api/status\" target=\"_blank\">/api/status</a></div>\n" +
                "            <span style=\"color:var(--text-muted); font-size:12px;\">Kiểm tra trạng thái</span>\n" +
                "          </div>\n" +
                "          <div class=\"api-item\">\n" +
                "            <div><span class=\"api-tag api-get\">GET</span> <a href=\"/api/users\" target=\"_blank\">/api/users</a></div>\n" +
                "            <span style=\"color:var(--text-muted); font-size:12px;\">Danh sách Client ID</span>\n" +
                "          </div>\n" +
                "          <div class=\"api-item\">\n" +
                "            <div><span class=\"api-tag api-post\">POST</span> <b>/api/broadcast</b></div>\n" +
                "            <span style=\"color:var(--text-muted); font-size:12px;\">Gửi tin (JSON body)</span>\n" +
                "          </div>\n" +
                "          <div style=\"margin-top: 12px; background: #FFF3E0; border-left: 3px solid #FF9800; padding: 8px 10px; font-size: 12px; color: #E65100;\">\n" +
                "            💡 <b>Cú pháp JSON POST:</b><br>\n" +
                "            <code>{\"message\": \"Nội dung tin nhắn\"}</code>\n" +
                "          </div>\n" +
                "        </div>\n" +
                "      </div>\n" +
                "\n" +
                "    </div>\n" +
                "  </div>\n" +
                "\n" +
                "  <div id=\"toast\"></div>\n" +
                "\n" +
                "  <script>\n" +
                "    function showToast(msg, isError) {\n" +
                "      const t = document.getElementById('toast');\n" +
                "      t.innerText = msg;\n" +
                "      t.style.background = isError ? '#D32F2F' : '#2E7D32';\n" +
                "      t.style.display = 'block';\n" +
                "      setTimeout(() => { t.style.display = 'none'; }, 3000);\n" +
                "    }\n" +
                "\n" +
                "    // Lấy danh sách users từ API /api/users\n" +
                "    async function fetchUsers() {\n" +
                "      try {\n" +
                "        const res = await fetch('/api/users');\n" +
                "        const data = await res.json();\n" +
                "        const list = document.getElementById('userList');\n" +
                "        const countPill = document.getElementById('countPill');\n" +
                "        \n" +
                "        countPill.innerText = data.online_count + ' online';\n" +
                "        list.innerHTML = '';\n" +
                "        \n" +
                "        if (!data.users || data.users.length === 0) {\n" +
                "          list.innerHTML = '<li style=\"color: var(--text-muted); text-align: center; padding: 18px;\">Chưa có client TCP nào kết nối.<br><small>Hãy chạy TcpChatClient hoặc telnet localhost 5000</small></li>';\n" +
                "          return;\n" +
                "        }\n" +
                "        \n" +
                "        data.users.forEach(u => {\n" +
                "          const li = document.createElement('li');\n" +
                "          li.className = 'user-item';\n" +
                "          li.innerHTML = `<div><i class=\"fa-solid fa-laptop\" style=\"color:var(--primary); margin-right:8px;\"></i> <b>Client #${u.id}</b> <small style=\"color:var(--text-muted);\">(${u.address})</small></div> <span class=\"user-pill\">Active</span>`;\n" +
                "          list.appendChild(li);\n" +
                "        });\n" +
                "      } catch (e) {\n" +
                "        console.error(e);\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    // Gửi broadcast qua POST /api/broadcast\n" +
                "    async function sendBroadcast() {\n" +
                "      const msgInput = document.getElementById('bcastMsg');\n" +
                "      const msg = msgInput.value.trim();\n" +
                "      if (!msg) {\n" +
                "        showToast('Vui lòng nhập nội dung tin nhắn!', true);\n" +
                "        return;\n" +
                "      }\n" +
                "      try {\n" +
                "        const res = await fetch('/api/broadcast', {\n" +
                "          method: 'POST',\n" +
                "          headers: { 'Content-Type': 'application/json' },\n" +
                "          body: JSON.stringify({ message: msg })\n" +
                "        });\n" +
                "        if (res.status === 201) {\n" +
                "          showToast('✅ Đã phát broadcast thành công tới các TCP Client!');\n" +
                "        } else {\n" +
                "          const err = await res.json();\n" +
                "          showToast('Lỗi: ' + (err.error || 'Không gửi được'), true);\n" +
                "        }\n" +
                "      } catch (e) {\n" +
                "        showToast('Lỗi kết nối tới Server!', true);\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    // Tự động cập nhật danh sách client mỗi 2 giây\n" +
                "    fetchUsers();\n" +
                "    setInterval(fetchUsers, 2000);\n" +
                "  </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
