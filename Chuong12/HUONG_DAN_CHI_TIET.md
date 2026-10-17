# HƯỚNG DẪN & BÁO CÁO DỰ ÁN: HTTP SERVER & MINI CHROME ĐA TAB (CHƯƠNG 12)

Dự án bài tập Lập trình Mạng đã được hoàn thành 100% bám sát theo nội dung bài giảng **Chương 12 (HTTP & HTTP Server Java)** của Thầy Trần Minh Nhật (Đại học Sài Gòn), bổ sung toàn diện các tính năng vượt trội: **Cào Web (Web Crawler)**, **Phê Duyệt Quản Trị Viên (Admin Approval)**, **Trình Duyệt Đa Tab (Multi-Tab Chrome)** và **Đồng Bộ Windows IIS**.

---

## 1. Cấu Trúc Dự Án Đã Xây Dựng

```
d:\baitap1\Chuong12\
│
├── webroot1\                           # Thư mục web Server 1 (Mô phỏng IIS - Port 8080)
│   ├── P1.HTM                          # Trang chủ Server 1 (Giao diện Đỏ - Trắng)
│   ├── P2.HTM                          # Trang kiến trúc HTTP & Gói tin
│   └── crawled\                        # Thư mục chứa các trang web cào về phát trên Server 1
│
├── webroot2\                           # Thư mục web Server 2 (App Server - Port 8081)
│   ├── P3.HTM                          # Trang dịch vụ mạng Server 2
│   ├── P4.HTM                          # Trang bảng dữ liệu thống kê
│   └── crawled\                        # Thư mục chứa các trang web cào về phát trên Server 2
│
├── src\
│   ├── crawler\
│   │   └── WebCrawlerEngine.java       # Lõi cào web Socket/SSLSocket thuần, gỡ chunk hex, gắn <base href>
│   │
│   ├── server\
│   │   ├── HttpServerCore.java         # Lõi Server (com.sun.net.httpserver, router /api/ingest, đa luồng)
│   │   ├── Server1Console.java         # Console Server 1 (Port 8080) chuẩn Slide 13
│   │   ├── Server2Console.java         # Console Server 2 (Port 8081) chuẩn Slide 13
│   │   └── ServerDashboardGUI.java     # Bảng điều khiển Server GUI (Start/Stop, Hộp thoại Phê duyệt Quản trị)
│   │
│   └── client\
│       ├── BrowserTabPanel.java        # Component quản lý 1 Tab duyệt web độc lập (URL, Lịch sử, View Source)
│       ├── HttpClientHelper.java       # Module gửi GET, POST JSON (java.net.http.HttpClient, timeout 5s)
│       └── MiniChromeBrowser.java      # Trình duyệt Chrome Thu Nhỏ Đa Tab (Multi-Tab + Nút Cào Web)
│
├── bin\                                # Thư mục chứa các file .class sau khi biên dịch
│
├── build.bat                           # Script biên dịch toàn bộ dự án (bao gồm cả crawler)
├── run_servers_console.bat             # Mở đồng thời Server 1 & Server 2 dạng 2 cửa sổ Console
├── run_server_gui.bat                  # Mở Bảng điều khiển Server Dashboard GUI
├── run_client.bat                      # Mở trình duyệt Chrome thu nhỏ (Client GUI)
└── start_all.bat                       # Script 1-click khởi chạy toàn bộ hệ thống
```

---

## 2. Các Tính Năng Nổi Bật Vượt Trội

### 🌐 A. Giao Diện Đỏ - Trắng (Red & White Theme)
- Phối màu chuẩn: Màu đỏ thẫm Ruby (`#DC2626`), đỏ sẫm (`#991B1B`), nền trắng ngà dịu mắt (`#F8FAFC`, `#FFFFFF`), viền xám sáng (`#E2E8F0`).
- Toàn bộ nút bấm sử dụng component `StyledButton` tự vẽ nền và chữ trắng 100%, khắc phục triệt để lỗi "trắng tinh" của Windows LookAndFeel.

### 🖥️ B. Mô Hình 2 Server Độc Lập Chuẩn Sơ Đồ Bảng
- **Server 1 (IIS)**: Cổng 8080, phục vụ `P1.HTM` và `P2.HTM`.
- **Server 2 (App)**: Cổng 8081, phục vụ `P3.HTM` và `P4.HTM`.
- Hỗ trợ nhấp link liên kết chéo mượt mà giữa 2 Server. Khi truy cập sai file, trả về mã **404 Not Found** chuẩn mực.

### 📑 C. Trình Duyệt Đa Tab (Multi-Tab Chrome)
- Cho phép mở nhiều tab duyệt web song song bằng nút **➕ Tab Mới** trên thanh tiêu đề.
- Mỗi tab có tiêu đề hiển thị tên trang web, nút đóng tab **`×`** trực quan.
- Mỗi tab hoạt động độc lập: Thanh URL riêng, nút Back/Forward/Reload riêng, và nút **📄 Xem Mã Nguồn (View Source)** riêng.

### 🕷️ D. Tính Năng Cào Web (Web Crawler Qua Socket & SSLSocket)
- Nhấn nút **`🕷️ Cào Web`** trên thanh công cụ của Mini Chrome.
- Nhập bất kỳ URL nào ngoài Internet (ví dụ: `https://example.com`, `https://tuoitre.vn`).
- `WebCrawlerEngine` kết nối Socket/SSLSocket thuần, gửi HTTP GET, bóc tách và khử mã hex **Chunked Transfer-Encoding**, đồng thời chèn thẻ `<base href="...">` để bảo toàn hình ảnh, style của trang gốc.

### 🛡️ E. Cơ Chế Phê Duyệt Quản Trị Viên (Admin Approval Queue)
- Khi cào xong, Client gửi yêu cầu nạp `POST /api/ingest` tới Server mục tiêu (Server 1 hoặc Server 2).
- Trên màn hình **Server Dashboard GUI**, Quản trị viên sẽ nhận được hộp thoại cảnh báo:
  > *"Client yêu cầu nạp trang web [tên_file] từ [URL nguồn]. Bạn có CHẤP NHẬN cho phép máy chủ nạp và phát hành trang này?"*
- Nếu bấm **[✅ CHẤP NHẬN NẠP]**: Trang web được lưu vào thư mục `crawled/` của Server, phản hồi **201 Created**, và Mini Chrome tự động mở tab mới phát trang đó ngay lập tức!
- Nếu bấm **[❌ TỪ CHỐI]**: Server phản hồi **403 Forbidden**, chặn đứng trang web không hợp lệ.
- Hỗ trợ tùy chọn **Đồng bộ sang IIS (`C:\inetpub\site1`)** nếu máy tính có bật IIS của Windows.

### 💬 F. Giao Tiếp Server & Chat API (Slide 17 & 18)
- Tab cố định **💬 Giao Tiếp Server & Chat API**:
  - Gửi `GET /api/time` lấy thời gian hệ thống dạng JSON.
  - Gửi `POST /api/message` với body JSON, nhận phản hồi **201 Created** từ Server.
  - Khung soi gói tin Raw Inspector hiển thị trọn vẹn Header và Payload.

---

## 3. Hướng Dẫn Sử Dụng & Demo Cho Giảng Viên

### Cách 1: Chạy 1-Click Tiện Lợi Nhất (Khuyên Dùng)
1. Trong thư mục `d:\baitap1\Chuong12`, nhấp đúp chuột vào file:
   👉 **`start_all.bat`**
2. Cửa sổ **Server Dashboard GUI** xuất hiện $\rightarrow$ Bấm nút **"Bật Cả Hai Server"** (Cổng 8080 & 8081 chuyển sang xanh).
3. Cửa sổ **Mini Chrome** tự động mở lên:
   - Duyệt các trang `P1.HTM`, `P2.HTM`, `P3.HTM`, `P4.HTM`.
   - Bấm nút **"➕ Tab Mới"** để mở thêm tab duyệt nhiều trang cùng lúc.
   - Bấm **"📄 Xem Mã Nguồn (View Source)"** để chứng minh luồng nhận mã HTML từ Server.
   - Chuyển sang Tab **"💬 Giao Tiếp Server"** để demo Chat và hỏi giờ qua API.

### Demo Tính Năng Cào Web & Phê Duyệt Quản Trị:
1. Trên Mini Chrome, bấm nút **"🕷️ Cào Web (Web Crawler)"**.
2. Nhập URL: `https://example.com` (hoặc bất kỳ trang nào).
3. Chọn Server đích: `Server 1 (Cổng 8080)` $\rightarrow$ Bấm **"🚀 Bắt Đầu Cào & Nạp Server"**.
4. Chuyển sang cửa sổ **Server Dashboard GUI**: Một hộp thoại màu đỏ sẽ bật lên hỏi ý kiến Quản trị viên $\rightarrow$ Bấm **"✅ CHẤP NHẬN NẠP"**.
5. Mini Chrome sẽ tự động mở ngay một Tab mới và hiển thị trang web vừa cào tại địa chỉ: `http://localhost:8080/crawled/crawled_example.htm`!

---

## 4. Kết Quả Kiểm Thử Thực Tế (Verification Results)

| Kịch Bản Kiểm Thử | Mục Tiêu | Kết Quả Thực Tế | Trạng Thái |
|---|---|---|:---:|
| `GET http://localhost:8080/P1.HTM` | Lấy trang chủ Server 1 | Status **200 OK**, `text/html; charset=UTF-8` | ✅ ĐẠT |
| `GET http://localhost:8080/P2.HTM` | Lấy trang kiến trúc HTTP Server 1 | Status **200 OK**, `text/html; charset=UTF-8` | ✅ ĐẠT |
| `GET http://localhost:8081/P3.HTM` | Lấy trang dịch vụ Server 2 | Status **200 OK**, `text/html; charset=UTF-8` | ✅ ĐẠT |
| `GET http://localhost:8081/P4.HTM` | Lấy trang bảng thống kê Server 2 | Status **200 OK**, `text/html; charset=UTF-8` | ✅ ĐẠT |
| `GET http://localhost:8080/api/time` | Lấy thời gian Server dạng JSON | Status **200 OK**, Body chứa `time` và `server` | ✅ ĐẠT |
| `POST http://localhost:8080/api/message` | Gửi tin nhắn JSON lên Server | Status **201 Created**, Server phản hồi chuỗi JSON | ✅ ĐẠT |
| `WebCrawlerEngine.crawl()` | Cào web ngoài qua SSLSocket thuần | Tải xong, khử mã hex chunked, chèn `<base href>` chuẩn | ✅ ĐẠT |
| `POST /api/ingest` | Tiếp nhận và nạp trang cào | Status **201 Created**, lưu file vào `webroot/crawled/` | ✅ ĐẠT |
| `GET /crawled/crawled_example.htm` | Server phát lại trang đã cào | Status **200 OK**, hiển thị mượt mà trên Tab mới | ✅ ĐẠT |
| Duyệt Đa Tab (Multi-Tab) | Mở nhiều tab bằng nút `+`, đóng bằng `x` | Đa Tab hoạt động độc lập, không xung đột | ✅ ĐẠT |
| Độc lập môi trường (Zero Dependency) | Biên dịch qua `javac` thuần | Biên dịch 0 lỗi, không phụ thuộc Maven/JavaFX | ✅ ĐẠT |
