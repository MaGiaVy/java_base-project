# 🚀 JAVA TCP CHAT SERVER & HTTP REST API BACKEND

Dự án tích hợp kết hợp kiến thức **Chương 11 (TCP Multi-Client Socket)** và **Chương 12 (HTTP Server & REST API)** của môn Lập trình mạng Java.

---

## 📌 1. Kiến trúc hệ thống

```
                                  ┌────────────────────────────────────────────────────────┐
                                  │                  ChatAndWebServer                      │
 ┌────────────────┐               │                                                        │
 │ TCP Client #1  │◄──Port 5000──►│  [Module 1: TCP Chat Server]                           │
 └────────────────┘               │   - ServerSocket(5000)                                 │
 ┌────────────────┐               │   - ExecutorService (CachedThreadPool)                 │
 │ TCP Client #2  │◄──Port 5000──►│   - Cấp phát ID: AtomicInteger                         │
 └────────────────┘               │                                                        │
                                  │         ▲                       ▲                      │
                                  │         │ (Shared Memory)       │                      │
                                  │         ▼                       ▼                      │
                                  │   ┌──────────────────────────────────────────────┐     │
                                  │   │ ConcurrentHashMap<Integer, ClientHandler>    │     │
                                  │   └──────────────────────────────────────────────┘     │
                                  │         ▲                                              │
                                  │         │ (Đọc users / Gửi broadcast)                  │
                                  │         ▼                                              │
 ┌────────────────┐               │                                                        │
 │ Web Browser    │◄──Port 8080──►│  [Module 2: HTTP Web Server]                           │
 │ Postman / curl │               │   - com.sun.net.httpserver.HttpServer (8080)           │
 └────────────────┘               │   - GET  /              -> Web Dashboard (Màu đỏ)      │
                                  │   - GET  /api/status    -> 200 OK                      │
                                  │   - GET  /api/users     -> 200 OK JSON list            │
                                  │   - POST /api/broadcast -> 201 Created                 │
                                  └────────────────────────────────────────────────────────┘
```

### Điểm kết nối chung:
- `ConcurrentHashMap<Integer, ClientHandler> clients`: Lưu danh sách các client đang online an toàn cho đa luồng.
- Khi người dùng gửi `POST /api/broadcast`, HTTP Handler truy cập vào `clients` và gọi hàm `broadcast()` của TCP Server để gửi thông báo tức thì tới tất cả các TCP Client.

---

## 📂 2. Cấu trúc thư mục

- `ChatAndWebServer.java`: Server 2-trong-1 (chạy cả TCP port 5000 và HTTP port 8080 trên 2 luồng song song).
- `TcpChatClient.java`: Client kết nối TCP Chat, hỗ trợ cả giao diện đồ họa Swing và dòng lệnh Console.
- `run_server.bat`: Khởi động Server (tự động biên dịch nếu cần).
- `run_client.bat`: Mở cửa sổ chat đồ họa (có thể nhấp đúp nhiều lần để mở nhiều client chat với nhau).
- `run_client_console.bat`: Mở client ở chế độ dòng lệnh Console.

---

## 🌐 3. Danh sách Endpoint HTTP REST API

| Method | Endpoint | Mô tả | Mã phản hồi |
|---|---|---|---|
| `GET` | `/` | Web Dashboard quản trị màu đỏ rực rỡ, tự cập nhật danh sách client và form gửi broadcast | `200 OK` (HTML) |
| `GET` | `/api/status` | Kiểm tra tình trạng hoạt động của Server | `200 OK` (JSON) |
| `GET` | `/api/users` | Lấy danh sách các ClientID đang online | `200 OK` (JSON) |
| `POST` | `/api/broadcast` | Gửi tin nhắn thông báo toàn hệ thống tới các TCP Client | `201 Created` (JSON) |

**Body mẫu cho `POST /api/broadcast`:**
```json
{
  "message": "Hệ thống sẽ bảo trì trong 5 phút nữa"
}
```

---

## 🏃 4. Hướng dẫn chạy & Kiểm thử

### Bước 1: Khởi động Server
Nhấp đúp vào `run_server.bat` hoặc mở terminal chạy:
```cmd
java -cp . ChatAndWebServer
```

### Bước 2: Mở Client Chat TCP
Nhấp đúp vào `run_client.bat` 2-3 lần để mở 2-3 cửa sổ chat, thử gõ tin nhắn qua lại giữa các client.

### Bước 3: Kiểm thử Web Dashboard
Mở trình duyệt truy cập:
```
http://localhost:8080/
```
- Bạn sẽ thấy giao diện quản trị màu đỏ với số lượng user online.
- Nhập tin nhắn vào ô broadcast rồi nhấn **GỬI BROADCAST NGAY**.
- Tất cả các cửa sổ TCP Client sẽ lập tức nhận được dòng tin:
  `[THÔNG BÁO TỪ WEB HTTP]: ...`

### Bước 4: Test bằng Postman hoặc curl
- **Kiểm tra trạng thái:**
  ```cmd
  curl http://localhost:8080/api/status
  ```
- **Lấy danh sách client:**
  ```cmd
  curl http://localhost:8080/api/users
  ```
- **Gửi broadcast qua API:**
  ```cmd
  curl -X POST http://localhost:8080/api/broadcast -H "Content-Type: application/json" -d "{\"message\":\"Thong bao tu Postman\"}"
  ```
