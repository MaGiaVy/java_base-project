# 🚀 HỆ THỐNG ĐA GIAO THỨC: TCP CHAT, HTTP REST API & UDP MULTICAST HUB

Hệ thống kết hợp toàn diện kiến thức mạng máy tính nâng cao:
- **Chương 10**: UDP Multicast (Lớp D `239.1.1.1`, Port 8000, `MulticastSocket`, `joinGroup`, `leaveGroup`, TTL=1).
- **Chương 11**: TCP Socket Multi-Client (Port 5000, `ServerSocket`, `ConcurrentHashMap`, Thread-safe, Broadcast).
- **Chương 12**: HTTP Server & REST API (`com.sun.net.httpserver.HttpServer`, Port 8080) + `HttpChatClient` (`java.net.http.HttpClient`).
- **NetworkHelper**: Tự động nhận diện và ưu tiên card **Mobile Hotspot** (`192.168.137.x` / `Local Area Connection*`), lọc bỏ card ảo VMware/VirtualBox.
- **Trực quan hóa HTTP Status Code**: Hiển thị thời gian thực mã phản hồi HTTP (`200 OK`, `201 Created`, `400`, `404`, `405`, `415`) ngay trên Web Dashboard và Web Chat Client!

---

## 🏛️ 1. Mô hình hoạt động & Cầu nối 3 chiều

```
┌─────────────────────────────────┐        TCP        ┌────────────────────────────────────────────────────────┐
│  Desktop Client (Swing/Console) ├──── Port 5000 ───►│                                                        │
│  - TcpChatClient                │                   │                  ChatAndWebServer                      │
└─────────────────────────────────┘                   │                                                        │
                                                      │  1. TCP Server (Port 5000)                             │
┌─────────────────────────────────┐       HTTP API    │  2. HTTP Web Server (Port 8080)                        │
│  HTTP Client (Console)          ├──── Port 8080 ───►│     - Admin Dashboard: http://localhost:8080/          │
│  - HttpChatClient (Chương 12)   │                   │     - Web Chat Client: http://localhost:8080/chat      │
└─────────────────────────────────┘                   │     - REST API Chuẩn: /api/clients, /api/message...    │
                                                      │                                                        │
┌─────────────────────────────────┐       HTTP/AJAX   │  3. Multicast Hub (Port 8000, Group 239.1.1.1)         │
│  Web Chat Client (Browser)      ├──── Port 8080 ───►│                                                        │
│  - http://localhost:8080/chat   │                   │     ▲ Cầu nối 3 chiều đồng bộ thông suốt tin nhắn      │
└─────────────────────────────────┘                   │                                                        │
                                                      │                                                        │
┌─────────────────────────────────┐    UDP Multicast  │                                                        │
│  Multicast Peers (LAN/Hotspot)  ├──── Port 8000 ───►│                                                        │
│  - MulticastGroupChat / Máy khác│                   └────────────────────────────────────────────────────────┘
└─────────────────────────────────┘
```

---

## 📂 2. Cấu trúc mã nguồn

- `ChatAndWebServer.java`: Server trung tâm tích hợp 3 giao thức (TCP 5000, HTTP 8080, Multicast 8000).
- `HttpChatClient.java`: Client dòng lệnh gọi REST API qua `HttpClient` (Chương 12).
- `TcpChatClient.java`: Client giao diện Java Swing GUI + Console dòng lệnh (Chương 11).
- `NetworkHelper.java`: Module nhận diện card mạng ưu tiên Mobile Hotspot (192.168.137.x).
- `run_server.bat`: Khởi động Server 1-click.
- `run_http_client.bat`: Khởi động HTTP Chat Client (gọi REST API).
- `open_web_chat.bat`: Mở Web Chat Client trên trình duyệt.
- `run_client.bat`: Mở TCP Chat Client (GUI Swing).
- `run_client_console.bat`: Mở TCP Chat Client (Console).

---

## 🌐 3. Danh sách Endpoint REST API & HTTP Response Status Codes

| Method | Endpoint | Mô tả | Response Status Code | Ghi chú Headers & Validation |
|---|---|---|---|---|
| `GET` | `/` | Web Admin Dashboard | **200 OK**, `404`, `405` | Trả về HTML giao diện quản trị |
| `GET` | `/chat` | Web Chat Client cho người dùng | **200 OK**, `405` | Trả về HTML chat thời gian thực |
| `GET` | `/api/clients` | **[Chuẩn C12]** Lấy danh sách ID client | **200 OK**, `405` | Trả về JSON: `[{"id":1},{"id":2}]` |
| `POST` | `/api/message` | **[Chuẩn C12]** Gửi tin nhắn và broadcast | **201 Created**, `400`, `405`, `415` | Body: `{"text":"..."}`, Content-Type: `application/json` |
| `GET` | `/api/history` | **[Chuẩn C12]** Lấy lịch sử 20 tin nhắn | **200 OK**, `405` | Trả về JSON mảng tin nhắn kèm ID và timestamp |
| `GET` | `/api/status` | Tình trạng server, trạng thái Multicast | **200 OK**, `405` | Trả về JSON port, online count, card mạng |
| `GET` | `/api/users` | Danh sách user chi tiết (TCP & Web) | **200 OK**, `405` | Trả về JSON `tcp_users` và `web_users` |
| `POST` | `/api/broadcast` | Phát thông báo toàn hệ thống | **201 Created**, `400`, `405`, `415` | Body: `{"message":"..."}` |
| `GET` | `/api/multicast/status`| Xem trạng thái nhóm Multicast | **200 OK**, `405` | Trả về IP nhóm, port, card mạng |
| `POST` | `/api/multicast/join` | Tham gia vào nhóm Multicast mới | **200 OK**, `400`, `405`, `415`, `500`| Body: `{"group":"239.1.1.1"}` (lớp D) |
| `POST` | `/api/multicast/leave`| Rời khỏi nhóm Multicast | **200 OK**, `405` | Thay đổi state -> yêu cầu POST |
| `POST` | `/api/multicast/send` | Bắn gói tin ra nhóm Multicast | **201 Created**, `400`, `405`, `415` | Bắn UDP socket ra card Hotspot |
| `GET` | `/api/chat/messages` | Lấy tin nhắn theo `since` & `clientId` | **200 OK**, `405` | Lọc tin Multicast theo quyền tham gia nhóm |
| `POST` | `/api/chat/send` | Gửi tin nhắn từ Web Client | **201 Created**, `400`, `405`, `415` | Body: `{"sender":"...", "message":"..."}` |
| `POST` | `/api/webclient/register` | Đăng ký user Web Client mới | **201 Created**, `405`, `415` | Sinh ID và lưu session Web |
| `POST` | `/api/webclient/update-group` | Cập nhật nhóm Multicast Web Client | **200 OK**, `400`, `404`, `405`, `415`| Báo `404 Not Found` nếu ID không tồn tại |
| `GET` | `/api/network/cards` | Quét danh sách card mạng IPv4 | **200 OK**, `405` | Ưu tiên card Hotspot 192.168.137.x |

---

## 🚦 4. Bảng Quy Chuẩn Mã Phản Hồi (HTTP Response Status Codes)

1. **`200 OK`**: Yêu cầu `GET` đọc dữ liệu hoặc thực thi thành công.
2. **`201 Created`**: Tạo mới thành công tài nguyên (`POST /api/message`, `POST /api/chat/send`, `POST /api/broadcast`, `POST /api/webclient/register`).
3. **`204 No Content`**: Phản hồi thành công cho preflight request CORS `OPTIONS`.
4. **`400 Bad Request`**: Dữ liệu gửi lên bị thiếu trường bắt buộc (`text`, `message`) hoặc IP multicast không thuộc lớp D (224-239.x.x.x).
5. **`404 Not Found`**: Đường dẫn URL không tồn tại hoặc không tìm thấy Client ID.
6. **`405 Method Not Allowed`**: Gửi sai phương thức HTTP (ví dụ gửi `GET` vào endpoint yêu cầu `POST`), trả về kèm header `Allow`.
7. **`415 Unsupported Media Type`**: Gửi `POST` nhưng thiếu header `Content-Type: application/json`.
8. **`500 Internal Server Error`**: Bắt lỗi ngoại lệ nội bộ và trả về JSON chuẩn thay vì làm gián đoạn kết nối.

---

## 🏃 5. Hướng dẫn kiểm thử thực tế

### Bước 1: Khởi động Server
Chạy `run_server.bat` hoặc lệnh:
```cmd
java -Dfile.encoding=UTF-8 ChatAndWebServer
```

### Bước 2: Kiểm thử HTTP Chat Client (Chương 12)
Chạy `run_http_client.bat` hoặc lệnh:
```cmd
java -Dfile.encoding=UTF-8 HttpChatClient
```
Menu hiển thị:
- `1. Xem danh sách client`: Gửi `GET /api/clients` -> In `Status Code: 200 OK`.
- `2. Gửi tin nhắn`: Gửi `POST /api/message` -> In `Status Code: 201 Created`.
- `3. Xem lịch sử`: Gửi `GET /api/history` -> In `Status Code: 200 OK`.

### Bước 3: Kiểm thử Trực quan hóa HTTP Status Code trên Web Dashboard
Mở trình duyệt truy cập: `http://localhost:8080/`
- Xem thẻ **"📊 HTTP REST API Inspector & Status Code Tester"**:
  - Bấm **Test 200 OK**: Gọi `GET /api/clients` -> Hiện bảng log status `200 OK`.
  - Bấm **Test 201 Created**: Gửi `POST /api/message` -> Hiện bảng log status `201 Created`.
  - Bấm **Test 400 Bad Request**: Gửi text rỗng -> Hiện status `400 Bad Request`.
  - Bấm **Test 405 Method Not Allowed**: Gửi `GET /api/broadcast` -> Hiện status `405 Method Not Allowed`.
  - Bấm **Test 415 Media Type**: Gửi thiếu header JSON -> Hiện status `415 Unsupported Media Type`.
  - Bấm **Test 404 Not Found**: Gọi URL sai -> Hiện status `404 Not Found`.

### Bước 4: Kiểm thử Web Chat Client
Chạy `open_web_chat.bat` hoặc truy cập `http://localhost:8080/chat`:
- Nhìn góc trên topbar và thanh trạng thái dưới cùng: hiển thị liên tục mã trạng thái HTTP API (`HTTP 200 OK`).
- Khi gõ tin nhắn và bấm Gửi: hiển thị tức thì trạng thái `HTTP 201 Created (Thành công)`!

### Bước 5: Kiểm thử Desktop Client (TCP)
Chạy `run_client.bat` (Giao diện Swing) hoặc `run_client_console.bat` (Console):
- Chat đồng bộ xuyên suốt cả 3 kênh: TCP, HTTP REST API và UDP Multicast.
