# TỔNG HỢP CHI TIẾT YÊU CẦU ĐỀ TÀI: LẬP TRÌNH HTTP SERVER & CLIENT (CHROME THU NHỎ)

Document này tổng hợp toàn bộ chi tiết từ sơ đồ kiến trúc trên bảng, bài giảng **Chương 12 - Java**, và phân tích yêu cầu thực hành cho bài tập lập trình mạng.

---

## I. TỔNG QUAN VỀ SƠ ĐỒ KIẾN TRÚC TRÊN BẢNG

Hình chụp bảng mô tả **mô hình giao tiếp Client - Server đa máy chủ / đa tài nguyên** thông qua giao thức HTTP.

### 1. Phân tích các thành phần trong sơ đồ
* **Phía Client (Bên trái):**
  * **Client / Firefox:** Đóng vai trò là ứng dụng phía người dùng (User Agent) phát ra các yêu cầu HTTP Request. Trong sơ đồ có 2 biểu tượng khoanh tròn đại diện cho đối tượng Client và Trình duyệt Firefox.
* **Phía Server (Bên phải):**
  * **Web Server IIS (web):** Máy chủ web đóng vai trò phục vụ các trang web nhóm 1. Trên máy chủ này lưu trữ các tệp HTML: `P1.HTM` và `P2.HTM`.
  * **Server (Máy chủ ứng dụng/máy chủ khác):** Máy chủ đóng vai trò phục vụ các trang web nhóm 2. Trên máy chủ này lưu trữ các tệp HTML: `P3.HTM` và `P4.HTM`.

### 2. Chi tiết các kết nối (Cái gì nối với cái gì?)

Mô hình thiết lập các luồng tương tác hai chiều (Request - Response) qua mạng:

```
                  +-----------------------------------+
                  |        CÁC LUỒNG KẾT NỐI          |
                  +-----------------------------------+

[ Client / Firefox ] <====== HTTP (Request / Response) ======> [ Web Server IIS ]
                                                                 (Chứa P1.HTM, P2.HTM)

[ Client / Firefox ] <====== HTTP (Request / Response) ======> [ Server ]
                                                                 (Chứa P3.HTM, P4.HTM)
```

* **Luồng 1 (Client <-> Web Server IIS):**
  * **Mối nối:** Trình duyệt Firefox / Client gửi HTTP Request (phương thức `GET`) tới địa chỉ IP/Port của **Web Server IIS**.
  * **Tài nguyên yêu cầu:** Yêu cầu các tệp trang web `P1.HTM` hoặc `P2.HTM`.
  * **Phản hồi:** IIS Server đọc tệp `P1.HTM`/`P2.HTM` từ đĩa cứng, đóng gói vào HTTP Response (Status `200 OK`, `Content-Type: text/html`) và gửi trả về cho Client.
* **Luồng 2 (Client <-> Server):**
  * **Mối nối:** Trình duyệt Firefox / Client gửi HTTP Request tới địa chỉ IP/Port của **Server**.
  * **Tài nguyên yêu cầu:** Yêu cầu các tệp trang web `P3.HTM` hoặc `P4.HTM`.
  * **Phản hồi:** Server tìm và trả về tệp `P3.HTM`/`P4.HTM` cho Client hiển thị.

---

## II. GIẢI THÍCH SỰ THỐNG NHẤT GIỮA CÁC YÊU CẦU

Có hai câu thoại/góc nhìn về đề tài:
1. **Bạn học nói:** *"Server lấy file HTML xong gửi cho Client, Client mở lên được."*
2. **Thầy yêu cầu:** *"Làm một 'Chrome thu nhỏ' để duyệt web HTML của mình."*

### Phân tích tính nhất quán:
* **Không hề mâu thuẫn:** 
  * Ý kiến của bạn học mô tả **nguyên lý vận hành chuẩn của giao thức HTTP** (Luồng truyền nhận dữ liệu).
  * Ý kiến của Thầy quy định **sản phẩm phần mềm bạn phải tự viết bằng code Java**.
* **Bản chất:** Thay vì dùng trình duyệt Chrome/Firefox có sẵn của hệ điều hành (như trong sơ đồ trên bảng) để gọi tới IIS, Thầy muốn bạn **tự lập trình ra cả 2 đầu**:
  * 自 Tự viết **HTTP Server bằng Java** (thay thế cho IIS Server).
  * 自 Tự viết **HTTP Client có giao diện GUI bằng Java** (đóng vai trò như một "Chrome thu nhỏ" thay thế cho Firefox).

---

## III. CHI TIẾT CÁC PHẦN CẦN LẬP TRÌNH (CODE SPECIFICATION)

Để hoàn thành đề tài này, bạn cần xây dựng một dự án Java gồm 2 mô-đun chính:

```
                            +----------------------------------+
                            |     MÔ HÌNH DỰ ÁN JAVA BẠN CODE  |
                            +----------------------------------+

┌────────────────────────────────────────┐                ┌────────────────────────────────────────┐
│      HTTP CLIENT ("Chrome thu nhỏ")    │                │              HTTP SERVER               │
│                                        │                │                                        │
│  [URL Bar: http://localhost:8080/P1]   │                │   [Java HttpServer: Port 8080]          │
│  [Nút "Truy cập (Go)"]                 │ --- HTTP Request ---> [Xử lý Request Handler]          │
│                                        │ (GET /P1.HTM)  │   [Đọc tệp P1.HTM từ thư mục /web]     │
│  [Khung Render Giao diện HTML        ] │                │                                        │
│  [Hiển thị trang P1.HTM              ] │ <--- HTTP Response -- [Đóng gói Status 200 + HTML Body]│
└────────────────────────────────────────┘ (Content-Type: │└────────────────────────────────────────┘
                                            text/html)
```

---

### PHẦN 1: LẬP TRÌNH HTTP SERVER (PHÍA SERVER)

Thực hiện viết ứng dụng Server bằng Java (sử dụng thư viện `com.sun.net.httpserver` theo kiến thức Chương 12).

#### Yêu cầu kỹ thuật chi tiết:
1. **Lắng nghe cổng (Binding Port):**
   * Khởi tạo `HttpServer` lắng nghe tại địa chỉ `localhost` (hoặc `0.0.0.0`) và một Cổng cụ thể (ví dụ: `8080`).
2. **Quản lý thư mục chứa file HTML (Web Root):**
   * Tạo thư mục `webroot/` lưu trữ các tệp HTML mẫu: `P1.HTM`, `P2.HTM`, `P3.HTM`, `P4.HTM`.
3. **Xử lý Request (Routing & Context):**
   * Đăng ký Context handler (ví dụ: `/` hoặc `/static`).
   * Phân tích HTTP Method (chỉ chấp nhận `GET`).
   * Lấy đường dẫn URI người dùng yêu cầu (Ví dụ: `/P1.HTM`).
4. **Đọc tệp và đóng gói Response:**
   * Kiểm tra xem tệp có tồn tại trong thư mục `webroot/` hay không.
   * **Nếu tồn tại:** 
     * Đọc nội dung tệp dưới dạng byte array (`byte[]`).
     * Thiết lập Response Headers: `Content-Type: text/html; charset=UTF-8`, `Content-Length`.
     * Gửi Response Code: `200 OK`.
     * Gửi dữ liệu HTML qua `OutputStream`.
   * **Nếu không tồn tại:** 
     * Trả về Response Code `404 Not Found` kèm thông báo lỗi HTML đơn giản.

---

### PHẦN 2: LẬP TRÌNH "CHROME THU NHỎ" (PHÍA CLIENT GUI)

Thực hiện viết ứng dụng Client có giao diện người dùng bằng Java Swing hoặc JavaFX.

#### Yêu cầu kỹ thuật chi tiết:
1. **Thiết kế Giao diện (GUI Design):**
   * **Thanh công cụ top:**
     * `JTextField` đóng vai trò URL Bar (Ô nhập địa chỉ web, ví dụ: `http://localhost:8080/P1.HTM`).
     * `JButton` nút "Truy cập" hoặc "Go".
   * **Thành phần hiển thị nội dung (Browser Body):**
     * Dùng `JEditorPane` (Java Swing) thiết lập `setEditable(false)` và `setContentType("text/html")`.
     * *(Hoặc nâng cao: Dùng `WebView` trong JavaFX để hỗ trợ render CSS/HTML đầy đủ hơn).*
2. **Xử lý sự kiện khi bấm nút "Truy cập":**
   * Lấy chuỗi URL từ ô nhập liệu.
   * Sử dụng `java.net.http.HttpClient` (hoặc `HttpURLConnection`) để tạo HTTP GET Request tới URL đó.
   * Bắt các ngoại lệ mạng (Timeout, Connection Refused nếu Server chưa bật).
3. **Hiển thị trang Web:**
   * Lấy dữ liệu chuỗi HTML nhận được từ HTTP Response Body.
   * Nạp chuỗi HTML vào `JEditorPane` bằng phương thức `setText(htmlContent)` hoặc `pageEditor.setPage(url)`.
   * Trình duyệt thu nhỏ sẽ tự động phân tích các thẻ HTML (`<h1>`, `<p>`, `<table>`, `<b>`,...) và hiển thị giao diện đồ họa tương ứng cho người dùng.

---

## IV. TỔNG KẾT LUỒNG CHẠY THỬ NGHỆM (TESTING WORKFLOW)

1. **Bước 1:** Khởi chạy chương trình **HTTP Server** trên Java. Server báo trạng thái: `Server started on port 8080...`
2. **Bước 2:** Mở ứng dụng **"Chrome thu nhỏ" (Client GUI)**.
3. **Bước 3:** Trên thanh URL của Chrome thu nhỏ, nhập: `http://localhost:8080/P1.HTM` rồi bấm **Go**.
4. **Bước 4:** 
   * Client gửi HTTP GET Request tới Server.
   * Server đọc tệp `P1.HTM` gửi lại cho Client.
   * Chrome thu nhỏ nhận mã HTML của `P1.HTM` và render hiển thị giao diện trang web lên màn hình.
