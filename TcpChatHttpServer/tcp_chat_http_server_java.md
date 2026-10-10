# Chương 11 & 12: TCP Chat Multi-threading + HTTP Server/Client

> Ghép chương 11 (TCP quản lý nhiều Client + Broadcast) và chương 12 (HTTP API) thành một sản phẩm hoàn chỉnh: **Chat Server qua HTTP Web API** + **Chat Console qua TCP**.

---

## Phần 1: TCP Chat Server (Chương 11)

### 1.1 Kiến trúc

```
┌─────────┐  TCP   ┌─────────────────────────────┐
│ Client 1├────────┤                             │
└─────────┘        │   TCP Server (Port 5000)    │
                   │  - Accept nhiều Client      │
┌─────────┐        │  - Gán Client ID            │
│ Client 2├────────┤  - Broadcast Message        │
└─────────┘        │  - Thread-safe List         │
                   │                             │
┌─────────┐        └─────────────────────────────┘
│ Client 3├────────┐
└─────────┘        │ + HTTP Server (Port 8080)
                   │   - API xem danh sách client
                   │   - API xem tin nhắn (tuỳ chọn)
                   │
```

### 1.2 Danh sách Client Thread-safe

```java
import java.util.concurrent.ConcurrentHashMap;

// Lưu tất cả client đang kết nối
// Key: Client ID, Value: ClientHandler (chứa Socket + ID)
static final ConcurrentHashMap<Integer, ClientHandler> clients = 
    new ConcurrentHashMap<>();
```

**Tại sao dùng `ConcurrentHashMap`?**
- Nhiều luồng (Client 1, 2, 3...) cùng lúc thêm/xóa client.
- `HashMap` thường không an toàn → dễ crash khi thay đổi.
- `ConcurrentHashMap` cho phép đọc/ghi đồng thời an toàn.

### 1.3 Code Server (Full)

```java
import java.io.*;
import java.util.Scanner;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public class TcpChatServer {
    static final ConcurrentHashMap<Integer, ClientHandler> clients = 
        new ConcurrentHashMap<>();
    static final ConcurrentLinkedQueue<String> messageHistory = 
        new ConcurrentLinkedQueue<>();  // Lưu lịch sử 100 tin nhắn gần nhất
    static int nextClientId = 0;
    static final Object idLock = new Object();

    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = new ServerSocket(5000);
        ExecutorService executor = Executors.newCachedThreadPool();

        System.out.println("=== TCP CHAT SERVER ===");
        System.out.println("Server đang chạy trên cổng 5000...");

        while (true) {
            Socket socket = serverSocket.accept();
            int clientId;
            synchronized (idLock) {
                clientId = ++nextClientId;
            }
            System.out.println("[+] Client #" + clientId + " kết nối từ " + 
                socket.getInetAddress().getHostAddress());
            
            ClientHandler handler = new ClientHandler(socket, clientId);
            clients.put(clientId, handler);
            executor.execute(handler);
        }
    }

    // Gửi tin nhắn tới TẤT CẢ client
    static void broadcastMessage(String message) {
        messageHistory.offer(message);  // Thêm vào lịch sử
        if (messageHistory.size() > 100)  // Giữ tối đa 100 tin
            messageHistory.poll();

        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        for (ClientHandler handler : clients.values()) {
            try {
                handler.send(data);
            } catch (IOException e) {
                System.out.println("[-] Lỗi gửi tới Client #" + handler.clientId);
            }
        }
    }

    // Xóa client
    static void removeClient(int clientId) {
        clients.remove(clientId);
        broadcastMessage("[Thông báo] Client #" + clientId + " đã ngắt kết nối");
        System.out.println("[-] Client #" + clientId + " ngắt kết nối");
    }
}

class ClientHandler implements Runnable {
    Socket socket;
    int clientId;
    BufferedReader reader;
    BufferedWriter writer;

    ClientHandler(Socket socket, int clientId) throws IOException {
        this.socket = socket;
        this.clientId = clientId;
        this.reader = new BufferedReader(
            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(
            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public void run() {
        try {
            // Gửi ID cho client
            send(("Bạn là Client #" + clientId + "\n").getBytes(StandardCharsets.UTF_8));
            TcpChatServer.broadcastMessage("[Thông báo] Client #" + clientId + " đã vào phòng");

            // Vòng nhận tin từ client
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;

                String formatted = "[#" + clientId + "] " + line;
                System.out.println(formatted);
                TcpChatServer.broadcastMessage(formatted);
            }
        } catch (IOException e) {
            System.out.println("[!] Lỗi khi xử lý Client #" + clientId + ": " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
            }
            TcpChatServer.removeClient(clientId);
        }
    }

    void send(byte[] data) throws IOException {
        String msg = new String(data, StandardCharsets.UTF_8);
        writer.write(msg);
        if (!msg.endsWith("\n")) writer.write("\n");
        writer.flush();
    }
}
```

### 1.4 Code Client TCP (Full)

```java
import java.io.*;
import java.util.Scanner;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class TcpChatClient {
    Socket socket;
    BufferedReader reader;
    BufferedWriter writer;

    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;

        TcpChatClient client = new TcpChatClient();
        client.connect(host, port);
    }

    void connect(String host, int port) throws Exception {
        socket = new Socket(host, port);
        reader = new BufferedReader(
            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(
            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

        System.out.println("=== TCP CHAT CLIENT ===");
        System.out.println("Đã kết nối tới " + host + ":" + port);

        // Luồng nhận tin (nền)
        Thread receiveThread = new Thread(this::receiveLoop);
        receiveThread.setDaemon(true);
        receiveThread.start();

        // Luồng gửi tin (chính)
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.equalsIgnoreCase("/quit")) break;
            send(line);
        }

        socket.close();
        System.out.println("Đã ngắt kết nối");
    }

    void send(String message) throws IOException {
        writer.write(message);
        writer.newLine();
        writer.flush();
    }

    void receiveLoop() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        } catch (IOException e) {
            System.out.println("[Lỗi] Mất kết nối: " + e.getMessage());
        }
    }
}
```

**Cách chạy:**
```bash
# Terminal 1: Khởi động server
java TcpChatServer

# Terminal 2: Client 1
java TcpChatClient localhost 5000

# Terminal 3: Client 2
java TcpChatClient localhost 5000

# Terminal 4: Client 3 (tuỳ chọn)
java TcpChatClient localhost 5000
```

---

## Phần 2: HTTP Server (Chương 12)

### 2.1 Tích hợp HTTP vào TCP Server

Thêm vào file `TcpChatServer.java`:

```java
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// Thêm vào main() của TcpChatServer, sau khi khởi động TCP server
public static void startHttpServer() throws Exception {
    HttpServer httpServer = HttpServer.create(new java.net.InetSocketAddress(8080), 0);
    httpServer.createContext("/", new RootHandler());
    httpServer.createContext("/api/clients", new ClientListHandler());
    httpServer.createContext("/api/message", new MessagePostHandler());
    httpServer.createContext("/api/history", new HistoryHandler());
    httpServer.setExecutor(Executors.newCachedThreadPool());
    httpServer.start();

    System.out.println("HTTP Server khởi động trên cổng 8080");
    System.out.println("  GET  http://localhost:8080/              - Trang chủ");
    System.out.println("  GET  http://localhost:8080/api/clients   - Danh sách client");
    System.out.println("  POST http://localhost:8080/api/message   - Gửi tin nhắn (JSON)");
    System.out.println("  GET  http://localhost:8080/api/history   - Lịch sử tin nhắn");
}
```

Thêm vào `main()`:

```java
ExecutorService executor = Executors.newCachedThreadPool();

// Khởi động HTTP Server trong một luồng riêng
executor.execute(() -> {
    try {
        startHttpServer();
    } catch (Exception e) {
        e.printStackTrace();
    }
});

// Phần còn lại của TCP Server
while (true) {
    // ...
}
```

### 2.2 HTTP Handlers

```java
// 1. GET / (Trang chủ)
class RootHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String html = "<html><head><title>Chat Server</title></head>" +
            "<body><h1>TCP Chat Server + HTTP API</h1>" +
            "<p>API Endpoints:</p>" +
            "<ul>" +
            "<li>GET /api/clients - Danh sách client đang kết nối</li>" +
            "<li>POST /api/message - Gửi tin nhắn (JSON: {\"text\":\"...\"})</li>" +
            "<li>GET /api/history - Lịch sử 20 tin nhắn gần nhất</li>" +
            "</ul>" +
            "</body></html>";
        sendResponse(exchange, 200, "text/html; charset=UTF-8", html);
    }
}

// 2. GET /api/clients (Danh sách client)
class ClientListHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (ClientHandler handler : TcpChatServer.clients.values()) {
            if (!first) json.append(",");
            json.append("{\"id\":").append(handler.clientId).append("}");
            first = false;
        }
        json.append("]");

        sendResponse(exchange, 200, "application/json", json.toString());
    }
}

// 3. POST /api/message (Gửi tin nhắn)
class MessagePostHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.startsWith("application/json")) {
            sendResponse(exchange, 415, "text/plain", "Content-Type must be application/json");
            return;
        }

        // Đọc body
        BufferedReader br = new BufferedReader(
            new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        String body = sb.toString();

        // Parse JSON đơn giản
        String text = "";
        if (body.contains("\"text\"")) {
            int start = body.indexOf("\"text\"") + 7;
            int end = body.indexOf("\"", start);
            text = body.substring(start, end);
        }

        if (text.isEmpty()) {
            sendResponse(exchange, 400, "application/json", 
                "{\"error\":\"text field is required\"}");
            return;
        }

        TcpChatServer.broadcastMessage("[HTTP] " + text);
        sendResponse(exchange, 201, "application/json", 
            "{\"message\":\"Message sent\"}");
    }
}

// 4. GET /api/history (Lịch sử)
class HistoryHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
            return;
        }

        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (String msg : TcpChatServer.messageHistory) {
            if (!first) json.append(",");
            // Escape quotes trong JSON
            String escaped = msg.replace("\\", "\\\\").replace("\"", "\\\"");
            json.append("{\"message\":\"").append(escaped).append("\"}");
            first = false;
        }
        json.append("]");

        sendResponse(exchange, 200, "application/json", json.toString());
    }
}

// Hàm helper gửi Response
static void sendResponse(HttpExchange exchange, int statusCode, 
        String contentType, String body) throws IOException {
    byte[] data = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", contentType);
    exchange.sendResponseHeaders(statusCode, data.length);
    try (OutputStream os = exchange.getResponseBody()) {
        os.write(data);
    }
    exchange.close();
}
```

---

## Phần 3: HTTP Client (Chương 12)

### 3.1 HTTP Client - Gọi API

```java
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;

import java.util.Scanner;

public class HttpChatClient {
    static final String BASE_URL = "http://localhost:8080";
    static HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    public static void main(String[] args) throws Exception {
        System.out.println("=== HTTP Chat Client ===");

        while (true) {
            System.out.println("\n1. Xem danh sách client");
            System.out.println("2. Gửi tin nhắn");
            System.out.println("3. Xem lịch sử");
            System.out.println("4. Thoát");
            System.out.print("Chọn: ");

            Scanner scanner = new Scanner(System.in);
            String choice = scanner.nextLine().trim();

            try {
                switch (choice) {
                    case "1":
                        listClients();
                        break;
                    case "2":
                        System.out.print("Tin nhắn: ");
                        String msg = scanner.nextLine();
                        sendMessage(msg);
                        break;
                    case "3":
                        showHistory();
                        break;
                    case "4":
                        System.out.println("Thoát");
                        return;
                    default:
                        System.out.println("Lựa chọn không hợp lệ");
                }
            } catch (Exception e) {
                System.out.println("[Lỗi] " + e.getMessage());
            }
        }
    }

    static void listClients() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/clients"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        System.out.println("\nDanh sách client:");
        System.out.println(response.body());
    }

    static void sendMessage(String text) throws Exception {
        String json = "{\"text\":\"" + text.replace("\"", "\\\"") + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/message"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        System.out.println("Status: " + response.statusCode());
        System.out.println("Response: " + response.body());
    }

    static void showHistory() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/api/history"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        System.out.println("\nLịch sử (20 tin gần nhất):");
        System.out.println(response.body());
    }
}
```

---

## Phần 4: Tương tác toàn bộ hệ thống

### 4.1 Kiểm tra toàn bộ

**Terminal 1: TCP Server (kèm HTTP Server)**
```bash
javac TcpChatServer.java
java TcpChatServer
```

**Terminal 2, 3, 4: TCP Client**
```bash
javac TcpChatClient.java
java TcpChatClient localhost 5000
java TcpChatClient localhost 5000
java TcpChatClient localhost 5000
```

**Terminal 5: HTTP Client (tuỳ chọn)**
```bash
javac HttpChatClient.java
java HttpChatClient
```

**Browser hoặc curl (tuỳ chọn):**
```bash
# Xem trang chủ
curl http://localhost:8080/

# Danh sách client
curl http://localhost:8080/api/clients

# Gửi tin nhắn
curl -X POST http://localhost:8080/api/message \
  -H "Content-Type: application/json" \
  -d '{"text":"Xin chào từ HTTP!"}'

# Lịch sử
curl http://localhost:8080/api/history
```

### 4.2 Luồng tin nhắn

```
[TCP Client 1] gõ "Xin chào"
    ↓
[TCP Server] nhận → Broadcast tới Client 2, 3 + lưu vào history
    ↓
[TCP Client 2] nhận "[#1] Xin chào"
[TCP Client 3] nhận "[#1] Xin chào"

[HTTP Client] gũi {"text": "Xin chào từ HTTP"}
    ↓
[HTTP Server] → Broadcast tới Client 1, 2, 3 + lưu history
    ↓
[TCP Client 1] nhận "[HTTP] Xin chào từ HTTP"
```

---

## Phần 5: Tóm tắt khái niệm

| Chương | Nội dung | Cấu trúc dữ liệu | Thread-safe | Ghi chú |
|---|---|---|---|---|
| **11** | TCP Multi-threading | `ConcurrentHashMap<ID, Handler>` | ✓ Có | Xử lý nhiều client cùng lúc |
| **11** | Broadcast Message | Vòng lặp `for (handler : clients)` | ✓ Thread-safe | Xóa client nếu lỗi |
| **12** | HTTP GET | Đọc danh sách từ `ConcurrentHashMap` | ✓ Tự động | Trả JSON |
| **12** | HTTP POST | Nhận JSON → Parse → Broadcast | ✓ Atomic | Trả JSON + Status |
| **12** | HTTP Client | `HttpClient.send()` | ✓ Có | Timeout, SSL hỗ trợ |

---

## Lỗi thường gặp

1. **`Address already in use`** → Port 5000 hoặc 8080 đã bị dùng, dùng port khác.
2. **Client kết nối nhưng không nhận tin** → Kiểm tra `receiveLoop()` có chạy không, hoặc socket close.
3. **HTTP 500 khi gửi tin** → Kiểm tra JSON format, escape quotes.
4. **`ConcurrentModificationException`** → Dùng `ConcurrentHashMap`, không dùng `HashMap`.

---

## Mở rộng (Tuỳ chọn)

- **Lưu chat vào file** (append-only log).
- **Xác thực client** (username/password).
- **Phòng chat riêng** (mỗi phòng một broadcast list).
- **WebSocket** (thay HTTP polling bằng WebSocket).
