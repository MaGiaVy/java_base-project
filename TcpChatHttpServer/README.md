# 🚀 HỆ THỐNG ĐA GIAO THỨC: TCP CHAT, HTTP REST API & UDP MULTICAST HUB

Hệ thống kết hợp toàn diện kiến thức:
- **Chương 10**: UDP Multicast (Lớp D `239.1.1.1`, Port 8000, `MulticastSocket`, `joinGroup`, `leaveGroup`, TTL=1).
- **Chương 11**: TCP Socket Multi-Client (Port 5000, `ServerSocket`, `ConcurrentHashMap`, Thread-safe, Broadcast).
- **Chương 12**: HTTP Server & REST API (`com.sun.net.httpserver.HttpServer`, Port 8080).
- **NetworkHelper**: Kế thừa trực tiếp từ thư mục `BroadcastMulticast`, tự động nhận diện và ưu tiên card **Mobile Hotspot** (`192.168.137.x` / `Local Area Connection*`), lọc bỏ card ảo VMware.

---

## 🏛️ 1. Mô hình hoạt động & Cầu nối 3 chiều

```
┌─────────────────────────────────┐        TCP        ┌────────────────────────────────────────────────────────┐
│  Desktop Client (Swing/Console) ├──── Port 5000 ───►│                                                        │
│  - TcpChatClient                │                   │                  ChatAndWebServer                      │
└─────────────────────────────────┘                   │                                                        │
                                                      │  1. TCP Server (Port 5000)                             │
┌─────────────────────────────────┐       HTTP/AJAX   │  2. HTTP Web Server (Port 8080)                        │
│  Web Chat Client (Browser)      ├──── Port 8080 ───►│     - Admin Dashboard: http://localhost:8080/          │
│  - http://localhost:8080/chat   │                   │     - Web Chat Client: http://localhost:8080/chat      │
└─────────────────────────────────┘                   │  3. Multicast Hub (Port 8000, Group 239.1.1.1)         │
                                                      │                                                        │
┌─────────────────────────────────┐    UDP Multicast  │     ▲ Cầu nối 3 chiều đồng bộ thông suốt tin nhắn      │
│  Multicast Peers (LAN/Hotspot)  ├──── Port 8000 ───►│                                                        │
│  - MulticastGroupChat / Máy khác│                   └────────────────────────────────────────────────────────┘
└─────────────────────────────────┘
```

---

## 📂 2. Cấu trúc mã nguồn

- `ChatAndWebServer.java`: Server trung tâm tích hợp cả 3 giao thức (TCP 5000, HTTP 8080, Multicast 8000).
- `NetworkHelper.java`: Module nhận diện card mạng ưu tiên Mobile Hotspot (192.168.137.x).
- `TcpChatClient.java`: Client giao diện Java Swing GUI + Console dòng lệnh.
- `run_server.bat`: Khởi động Server 1-click.
- `open_web_chat.bat`: Mở Web Chat Client trên trình duyệt.
- `run_client.bat`: Mở TCP Chat Client (GUI).
- `run_client_console.bat`: Mở TCP Chat Client (Console).

---

## 🌐 3. Danh sách Endpoint REST API

| Method | Endpoint | Mô tả |
|---|---|---|
| `GET` | `/` | Web Admin Dashboard (Tone đỏ chủ đạo, quản lý Multicast, xem online, gửi broadcast) |
| `GET` | `/chat` | Web Chat Client cho người dùng (có Modal hỏi tham gia Multicast, bong bóng chat) |
| `GET` | `/api/status` | Tình trạng server, trạng thái Multicast, card mạng đang dùng |
| `GET` | `/api/users` | Danh sách các user đang online (cả TCP clients lẫn Web clients) |
| `POST` | `/api/broadcast` | Phát thông báo toàn hệ thống (tới TCP, Web và Multicast) |
| `GET` | `/api/multicast/status` | Xem trạng thái nhóm Multicast hiện tại |
| `POST` | `/api/multicast/join` | Tham gia vào nhóm Multicast mới `{"group": "239.1.1.1"}` |
| `POST` | `/api/multicast/leave` | Rời khỏi nhóm Multicast |
| `POST` | `/api/multicast/send` | Bắn gói tin ra nhóm Multicast `{"message": "..."}` |
| `GET` | `/api/chat/messages?since=ID` | Lấy danh sách tin nhắn mới theo thời gian thực |
| `POST` | `/api/chat/send` | Gửi tin nhắn từ Web Client `{"sender": "Vy", "message": "..."}` |
| `POST` | `/api/webclient/register` | Đăng ký user Web Client mới kèm lựa chọn Multicast |

---

## 🏃 4. Hướng dẫn kiểm thử thực tế

### Bước 1: Khởi động Server
Chạy `run_server.bat` hoặc lệnh:
```cmd
java -cp . ChatAndWebServer
```

### Bước 2: Kiểm thử Web Chat Client (mở nhiều tab trình duyệt)
Chạy `open_web_chat.bat` hoặc mở trình duyệt truy cập:
```
http://localhost:8080/chat
```
1. **Hộp thoại (Modal) xuất hiện ngay lập tức**:
   - Nhập Tên hiển thị (Nickname), ví dụ: `Vy`.
   - Hệ thống hỏi: **"Bạn có muốn tham gia nhóm Multicast không?"**.
   - Có thể nhập IP nhóm (mặc định `239.1.1.1`).
   - Bấm **"CÓ, Tham Gia Nhóm"** hoặc **"KHÔNG, Chỉ Chat Web"**.
2. Mở thêm 1 tab nữa tại `http://localhost:8080/chat` đặt tên là `Hùng`:
   - Chat qua lại giữa 2 tab trình duyệt hoàn toàn theo thời gian thực!

### Bước 3: Kiểm thử đồng bộ với TCP Desktop Client
Chạy `run_client.bat` để mở cửa sổ chat Java:
- Gõ tin nhắn ở TCP Desktop Client -> Lập tức xuất hiện ở cả 2 tab Web Chat!
- Gõ tin nhắn ở Web Chat -> Lập tức xuất hiện ở TCP Desktop Client!

### Bước 4: Kiểm thử Multicast từ Web Admin Dashboard
Truy cập `http://localhost:8080/`:
- Xem trạng thái card mạng Hotspot.
- Thử bấm **Tham Gia Nhóm** hoặc **Rời Nhóm** Multicast trực tiếp trên giao diện web.
- Nhập tin nhắn và bấm **"Chỉ gửi ra Multicast UDP"** hoặc **"Phát Broadcast Ngay"**.
