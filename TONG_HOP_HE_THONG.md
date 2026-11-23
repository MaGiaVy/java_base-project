# Tổng hợp các hệ thống đã xây dựng trong `d:\baitap1`

Tài liệu này tóm tắt chức năng, giao thức và công nghệ của từng hệ thống bạn đã từng build, kèm gợi ý cách ghép vào đồ án app tựa Zalo.

---

## Bảng tóm tắt nhanh

| # | Thư mục | Hệ thống / Giao thức | Làm gì chính | Ghép vào Zalo dùng để làm gì |
|---|---|---|---|---|
| 1 | `TcpDemo` | **TCP Socket** (đa luồng) | Server lắng nghe cổng 5000, phục vụ nhiều client đồng thời, broadcast tin nhắn | Hiểu nền tảng 1 thread/client |
| 2 | `TCP_secure` | **TCP + TLS 1.2/1.3** (`SSLSocket`) | Chat đa người dùng có mã hoá đường truyền, đăng nhập băm mật khẩu BCrypt, gói tin JSON | **Xương sống** của Zalo: đăng nhập, chat 1-1, chat nhóm realtime |
| 3 | `BroadcastMulticast` | **UDP Broadcast & Multicast** | Chat trong mạng LAN không cần server trung tâm; chat nhiều phòng nhóm multicast (`239.1.1.x`), phát hiện mất gói (`NACK`) và gửi lại | **Tự tìm server trong mạng LAN** (khỏi gõ IP); nền tảng UDP cho gọi thoại |
| 4 | `TcpChatHttpServer` | **TCP + HTTP REST + UDP Multicast** | Ghép 3 giao thức trong 1 server: chat chung (TCP & Web), chat nhóm (UDP Multicast), REST API có đủ mã trạng thái HTTP (`200`, `201`, `400`, `404`, `405`, `415`, `500`) | Mô hình server đa giao thức, mở thêm bản Web nếu cần |
| 5 | `Chuong12` & `Chuong12_HAnh` | **HTTP/1.1 Socket Client & Web Server** | Trình duyệt mini + Web Crawler dùng `Socket`/`SSLSocket` tự gửi `GET / HTTP/1.1`, tải mã nguồn HTML/CSS/JS về máy, chạy Web Server tĩnh (mô phỏng IIS) | Tải/xem trước link (link preview), phục vụ file tĩnh qua HTTP |
| 6 | `Chuong13` | **HTTP RESTful Web API** | Server REST API `/api/students` + Swing Client gọi API bằng `HttpClient`, đủ 4 thao tác CRUD (`GET`, `POST`, `PUT`, `DELETE`) trả JSON | Gọi API lấy dữ liệu/cấu hình, hiểu chuẩn REST & mã trạng thái HTTP |
| 7 | `FTP`, `FTP_Vỹ`, `iis_ftp` | **FTP (RFC 959)** | Server & Client truyền file 2 kênh (kênh lệnh + kênh dữ liệu), đăng nhập, liệt kê/tải lên/tải xuống file, đo tốc độ và thanh tiến trình | **Gửi ảnh và file** trong Zalo qua kênh socket dữ liệu riêng, có thanh tiến trình |
| 8 | `MailClient` | **SMTP / POP3 / IMAP** (Socket + JavaMail) | Gửi mail (SMTP cổng 465 SSL / 587 STARTTLS), nhận & xoá mail (POP3/IMAP), đóng gói MIME tiếng Việt & đính kèm file/thư mục | **Gửi mã OTP qua email** khi đăng ký hoặc quên mật khẩu |
| 9 | `IoT` | **MQTT Broker & Client** (TCP + WebSocket) | Tự viết MQTT Broker thuần Java (cổng 1883 TCP & 9001 WebSocket RFC 6455) theo mô hình Publish/Subscribe; kết nối ESP32 (Wokwi), Java Client và Web Client | Ý tưởng **Publish/Subscribe** theo phòng chat (mỗi nhóm = 1 topic) và cách xử lý WebSocket |
| 10 | `phan8` | **Java Swing + PostgreSQL (JDBC)** | App desktop quản lý sinh viên theo kiến trúc phân lớp Model – DAO – UI, đăng nhập, CRUD, tìm kiếm, sắp xếp, vẽ biểu đồ JFreeChart | **Lưu trữ dữ liệu** (user, bạn bè, nhóm, tin nhắn) vào PostgreSQL theo mô hình DAO |

---

## Chi tiết từng hệ thống

### 1. TCP cơ bản (`TcpDemo`)
- **Giao thức:** TCP (`ServerSocket`, `Socket`), cổng `5000`.
- **Chức năng:**
  - Server mở cổng chờ nhiều client kết nối cùng lúc (mỗi client chạy trên một luồng riêng).
  - Đếm số client đang kết nối an toàn đa luồng (`AtomicInteger`).
  - Nhận tin từ một client và phát lại (broadcast) cho tất cả client khác qua `CopyOnWriteArrayList`.
  - Client console (`TcpClient`) nhập và nhận tin nhắn 2 chiều.

### 2. TCP bảo mật (`TCP_secure`)
- **Giao thức:** TCP mã hoá **TLS 1.2 / 1.3** (`SSLServerSocket`, `SSLSocket`), đóng gói tin dạng JSON trên từng dòng (`\n`).
- **Công nghệ:** Java 17+, Maven đa module (`model`, `server`, `client`), Jackson, jBCrypt, Swing GUI + Console CLI.
- **Chức năng:**
  - Mã hoá toàn bộ dữ liệu truyền giữa client và server bằng chứng chỉ PKCS#12 (`server.p12`).
  - Phía client xác thực chứng chỉ server (`ServerCertificateValidator`: kiểm tra Hostname/SAN, hạn dùng, chuỗi tin cậy).
  - Xác thực tài khoản bằng mật khẩu băm **BCrypt** (`UserStore`), không lưu mật khẩu dạng rõ.
  - Quản lý phiên tập trung bằng `ConcurrentHashMap`, chống đè dữ liệu khi nhiều luồng cùng gửi bằng `ReentrantLock`.
  - Có sẵn 6 test case bảo mật tự động (TC01–TC06).

### 3. UDP Broadcast & Multicast (`BroadcastMulticast`)
- **Giao thức:** UDP (`DatagramSocket`, `MulticastSocket`, địa chỉ lớp D `239.1.1.x:8000`, `TTL = 1` trong mạng LAN).
- **Chức năng:**
  - **Broadcast (`BroadcastSenderGUI`, `BroadcastReceiverGUI`, `LanChat`):** phát tin tới toàn bộ máy trong cùng mạng LAN.
  - **Multicast (`MulticastChat`, `MulticastChatGUI`, `UnifiedChatGUI`):**
    - Chat nhóm 2 chiều không cần server trung tâm; chọn tên hiển thị.
    - Hỗ trợ nhiều phòng theo nhiều nhóm multicast (Java `239.1.1.1`, Network `239.1.1.2`, IT1 `239.1.1.3`) và chuyển phòng động (`/join 1|2|3`).
    - Đánh số thứ tự gói tin (`#0001`, `#0002`...), phát hiện gói bị mất và gửi yêu cầu `NACK` để phát lại; có lệnh `/loss` để giả lập mất gói khi demo.
    - Xem thông tin card mạng (`ShowNetworkInfo`, `NetworkHelper`) và kiểm tra máy không tham gia nhóm (`NonMember`) sẽ không nhận được tin.

### 4. Hệ thống ghép TCP + HTTP + UDP Multicast (`TcpChatHttpServer`)
- **Giao thức:** Kết hợp đồng thời TCP (`5000`), HTTP REST API (`HttpServer`), và UDP Multicast (`239.x.x.x`).
- **Chức năng:**
  - Kênh chat chung hoạt động xuyên suốt giữa TCP Client và Web Client.
  - Kênh chat nhóm riêng bằng UDP Multicast (chỉ thành viên đã tham gia nhóm mới nhận tin).
  - Cung cấp REST API chuẩn (`GET /api/clients`, `POST /api/message`, `GET /api/history`) và xử lý đầy đủ mã trạng thái HTTP (`200`, `201`, `204`, `400`, `404`, `405`, `415`, `500`).
  - Có cả trình duyệt mini (`MiniBrowser`) và client HTTP (`HttpChatClient`, `MyHttpClient`).

### 5. HTTP/1.1 Socket Client, Web Crawler & Web Server (`Chuong12`, `Chuong12_HAnh`)
- **Giao thức:** HTTP/1.1 và HTTPS trực tiếp qua `Socket` / `SSLSocket` và `com.sun.net.httpserver.HttpServer`.
- **Chức năng:**
  - **Web Client / Crawler (`WebClient`, `WebCrawlerEngine`):** tự gửi yêu cầu `GET / HTTP/1.1` ở tầng socket, tự giải mã `Chunked Transfer-Encoding` ở mức byte, tải mã nguồn HTML/CSS/JS của website ngoài internet về lưu cục bộ và đồng bộ lên thư mục host (IIS).
  - **Mini Browser (`MiniChromeBrowser`, `BrowserTabPanel`, `ClientGUI`):** trình duyệt nhiều tab nhúng WebView (JavaFX trong Swing) để hiển thị trang tải về.
  - **Web Server (`HttpServerCore`, `Server1Console`, `Server2Console`, `ServerDashboardGUI`):** tự dựng web server phục vụ trang HTML tĩnh trên nhiều cổng (`8080`, `8081`) mô phỏng IIS / máy chủ ứng dụng.

### 6. RESTful Web API (`Chuong13`)
- **Giao thức:** HTTP/1.1 REST API + JSON.
- **Chức năng:**
  - Server và Swing Client tích hợp chung trong `Chuong13WebApiApp`.
  - Quản lý tài nguyên `/api/students` với đầy đủ CRUD: `GET` (tất cả / theo ID), `POST` (`201 Created`), `PUT` (`200 OK` / `404`), `DELETE` (`204 No Content` / `404`).
  - Dùng `java.net.http.HttpClient` phía client và `HttpServer` phía server.

### 7. Hệ thống truyền file FTP (`FTP`, `FTP_Vỹ`, `iis_ftp`)
- **Giao thức:** FTP chuẩn **RFC 959** trên TCP Socket (tách riêng kênh điều khiển và kênh truyền dữ liệu).
- **Chức năng (`FTP_Vỹ` là bản hoàn chỉnh nhất):**
  - **FTP Server (`FtpServer`, `ClientHandler`, `FtpSession`, `UserManager`, `SecurityValidator`):** quản lý tài khoản, phiên làm việc, chống truy cập vượt thư mục gốc (Path Traversal).
  - **FTP Client (`FtpClient`, `SmartListParser`):** kết nối, đăng nhập, duyệt thư mục, tải file lên (Upload), tải file xuống (Download), tạo/xoá file & thư mục.
  - Theo dõi tiến trình truyền dữ liệu thời gian thực (`TransferProgressListener`: số byte, phần trăm, tốc độ).
  - Hỗ trợ kiểm thử chéo (client mình kết nối IIS FTP hoặc FileZilla và ngược lại; thư mục `iis_ftp` dùng cho kiểm thử IIS).

### 8. Ứng dụng Email Client (`MailClient/EmailClientApp`)
- **Giao thức:** **SMTP** (gửi thư, cổng 465 SSL / 587 STARTTLS), **POP3** (nhận thư, cổng 110/995), **IMAP** (nhận & đồng bộ thư, cổng 143/993).
- **Chức năng:**
  - Chạy song song 2 cơ chế chuyển đổi được: **Socket thuần** (`SmtpSocketClient`, `Pop3SocketClient`, `ImapSocketClient`, `LineConnection`) và **JavaMail** (`JavaMailService`).
  - Tự xử lý giao thức ở tầng byte: `AUTH LOGIN` (Base64), dot-stuffing / dot-unstuffing, lệnh IMAP có tag & literal `{N}`.
  - Đóng gói MIME tiếng Việt (RFC 2047), hỗ trợ đính kèm nhiều file và đính kèm cả thư mục (MIME multipart lồng nhau).
  - Ghi log giao thức chi tiết, có bộ kiểm thử tự động (`test/*Test.java`).

### 9. Hệ thống IoT – MQTT Broker & Client (`IoT`)
- **Giao thức:** **MQTT v3.1 / v3.1.1** trên TCP (`1883`) và **WebSocket RFC 6455** (`9001`).
- **Chức năng:**
  - **Java MQTT Broker (`MqttBrokerServer`, `BrokerFrame`):** tự viết broker thuần JDK, nhận kết nối từ thiết bị/client, quản lý topic và định tuyến gói tin Publish/Subscribe, lưu lịch sử dữ liệu (`DatabaseManager`).
  - **Java Desktop Client (`MqttManager`, `MainFrame`):** dùng Eclipse Paho để nhận dữ liệu cảm biến và gửi lệnh điều khiển (bật/tắt LED).
  - **Web Client (`web/`) & Thiết bị mô phỏng (`wokwi/` ESP32 + `mosquitto/`):** nhận dữ liệu telemetry qua WebSocket và điều khiển thiết bị theo thời gian thực.

### 10. Ứng dụng Desktop + Cơ sở dữ liệu PostgreSQL (`phan8`)
- **Công nghệ:** Java Swing + JDBC (`postgresql`) + JFreeChart.
- **Chức năng:**
  - Kiến trúc 3 lớp rõ ràng: Model (`SinhVien`) – DAO (`SinhVienDAO`, `DatabaseHelper`) – UI (`LoginFrame`, `MainFrame`, `UIHelper`).
  - Form đăng nhập, quản lý CRUD trên `JTable`, tìm kiếm, sắp xếp, vẽ biểu đồ cột & biểu đồ tròn thống kê trực quan.

### Nền tảng Java cơ bản (đã luyện trước đó)
- `BaiTap_Collections`: dùng `List`, `Map`/`Dictionary`, `Queue`, `Stack` (ứng dụng cho danh bạ, hàng đợi tin nhắn, lịch sử thao tác).
- `BaiTap_FileIO`: đọc, ghi, tìm kiếm dữ liệu trong file.
- `BaiTap_ExceptionHandling`: xử lý lỗi nhập liệu, `throw`, custom exception, `finally` đóng tài nguyên an toàn.
- `BaiTap_CoSo` & `Phan1`: lập trình hướng đối tượng (kế thừa, đa hình), mảng, menu điều khiển.

---

## Tóm tắt cách trả lời thầy khi bảo vệ đồ án Zalo

> *"Ứng dụng Zalo thực tế không dùng một giao thức đơn lẻ mà kết hợp nhiều hệ thống con. Em tái sử dụng trực tiếp các kiến thức đã học trong môn:"*
> 1. **TCP + TLS (`TCP_secure`)** làm kênh chính cho đăng nhập (BCrypt) và nhắn tin 1-1 / nhóm realtime vì cần tin cậy, đúng thứ tự và bảo mật.
> 2. **Kênh TCP dữ liệu riêng (ý tưởng `FTP_Vỹ`)** để truyền ảnh và file dung lượng lớn mà không làm nghẽn luồng chat, kèm thanh tiến trình.
> 3. **SMTP (`MailClient`)** để gửi mã OTP qua email khi quên mật khẩu.
> 4. **UDP Multicast (`BroadcastMulticast`)** để client tự tìm server trong mạng LAN mà không cần gõ tay địa chỉ IP (và mở rộng gọi thoại qua UDP nếu có thời gian).
> 5. **Java Swing + PostgreSQL DAO (`phan8`)** để làm giao diện desktop và lưu trữ bền vững toàn bộ tài khoản, bạn bè, nhóm và lịch sử tin nhắn.
