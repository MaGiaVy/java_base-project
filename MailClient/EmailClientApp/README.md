# ỨNG DỤNG MAIL CLIENT BẰNG JAVA (SOCKET + JAVAMAIL)
### TRƯỜNG ĐẠI HỌC SÀI GÒN (SAIGON UNIVERSITY - SGU)
**Môn học:** Lập Trình Mạng (Network Programming)  
**Giảng viên:** ThS. Trần Minh Nhật  
**Phong cách giao diện:** Classic Green Vintage UI (Retro Desktop Computing)

---

## 1. TỔNG QUAN DỰ ÁN

Dự án triển khai hoàn chỉnh 100% các yêu cầu trong giáo trình `MAIL - Java.pptx` / `MAIL-Java.md`, hỗ trợ song song 2 cơ chế (Engine) chuyển đổi linh hoạt:

1. **Engine 1 - SOCKET (Mức thấp):**
   - **Gửi thư (SMTP):** Triển khai trực tiếp trên TCP Socket và `SSLSocket`, tuân thủ chuẩn 8 bước ESMTP, kết thúc bằng `\r\n.\r\n`, xử lý dot-stuffing, xác thực `AUTH LOGIN` (Base64), hỗ trợ cả cổng 465 (Implicit SSL) và 587 (Explicit STARTTLS nâng cấp socket động).
   - **Nhận thư (POP3):** Kết nối cổng 110/995, xử lý `USER`, `PASS`, `STAT`, `LIST`, `RETR` kèm kỹ thuật gỡ dot-unstuffing (`..` $\to$ `.`), và lệnh `DELE` để xóa thư.
   - **Nhận thư (IMAP):** Kết nối cổng 143/993, quản lý câu lệnh có tag (`A1 LOGIN`, `A2 SELECT INBOX`, `A3 FETCH ... BODY.PEEK[]`), cơ chế literal `{N}` chuẩn RFC 3501, và lệnh `STORE +FLAGS (\Deleted)` kết hợp `EXPUNGE` để xóa thư.
   - **MIME & Tiếng Việt:** Đóng gói tiêu đề theo chuẩn RFC 2047 (`=?UTF-8?B?...?=`), chia dòng Base64 chuẩn 76 ký tự kết thúc CRLF, hỗ trợ đính kèm nhiều file và **đóng gói thư mục dạng MIME Nested (multipart lồng multipart)**.

2. **Engine 2 - JAVAMAIL (Mức cao):**
   - Sử dụng thư viện chuẩn `javax.mail-1.6.2.jar` và `jakarta.activation-1.2.2.jar`.
   - Gửi thư HTML, đa file đính kèm, thư mục lồng nhau, nhận thư INBOX, xóa thư và tải đính kèm.

3. **Cơ chế Độ tin cậy & Bảo mật:**
   - **Phân loại mã phản hồi:** 2xx (thành công), 3xx (trung gian), 4xx (tạm thời $\to$ **Retry**), 5xx (vĩnh viễn $\to$ **Không Retry**).
   - **Exponential Backoff:** Tự động thử lại tối đa 3 lần với khoảng chờ nhân đôi ($1000\text{ms} \to 2000\text{ms}$).
   - **Bảo mật:** Không bao giờ ghi mật khẩu dạng thường hoặc dạng Base64 vào file log `logs/mail-client.log`. Chống tấn công Header Injection.
   - **Đa máy chủ:** Mặc định cấu hình Gmail (App Password), đồng thời có hộp thoại **"⚙ Cấu hình Server..."** để nhập tùy ý IP/Host, Port và Bảo mật (None/SSL/STARTTLS) cho Mail Server phòng lab SGU.

4. **Giao diện Classic Green Vintage UI:**
   - Phối màu xanh rêu cổ điển thanh lịch (`#234935`, `#2D5A43`, `#F4F6F0`).
   - Bố cục 2 tab: **Soạn Thư** và **Hộp Thư Đến** với bảng Split Pane.
   - Tích hợp **bộ xem HTML trực quan** (`JEditorPane`) kèm tab xem mã nguồn thô.
   - Nút **"Lưu đính kèm về máy..."** cho phép trích xuất nhị phân file đính kèm lưu ra đĩa.
   - Khung Console Terminal ở đáy cửa sổ hiển thị trực tiếp dòng giao dịch mạng `C:` và `S:`.
   - Đa luồng với `SwingWorker` chống đơ giao diện.

---

## 2. CẤU TRÚC THƯ MỤC

```
EmailClientApp/
├── src/
│   ├── EmailClientApp.java        # Điểm khởi chạy chương trình (main)
│   ├── MainFrame.java             # Giao diện Swing Classic Green Vintage
│   ├── ServerConfigDialog.java    # Dialog cấu hình tuỳ chỉnh Server (Host, Port, Security)
│   ├── MailService.java           # Interface trừu tượng hoá các nghiệp vụ email
│   ├── Engine.java                # Enum: SOCKET | JAVAMAIL
│   ├── ReceiveProtocol.java       # Enum: POP3 | IMAP
│   ├── Security.java              # Enum: NONE | SSL | STARTTLS
│   ├── AccountConfig.java         # Quản lý tài khoản & thông số kết nối Server
│   ├── OutgoingMail.java          # Đối tượng thư gửi (To, Subject, Body, HTML, File/Folder)
│   ├── ReceivedMail.java          # Đối tượng thư nhận (ID, From, Subject, Date, Body, HTML, Attachments)
│   ├── AttachmentItem.java        # Quản lý nhị phân file/folder đính kèm và lưu ra đĩa
│   ├── MailException.java         # Quản lý ngoại lệ & phân loại transient/permanent
│   ├── ReplyCode.java             # Phân loại mã phản hồi SMTP 2xx/3xx/4xx/5xx
│   ├── Retrier.java               # Cơ chế thử lại Exponential Backoff
│   ├── MailLog.java               # Hệ thống ghi log ra file và giao diện
│   ├── MailParser.java            # Bộ giải mã thư MIME, RFC 2047, trích xuất đính kèm
│   ├── MimeBuilder.java           # Dựng cấu trúc MIME, Base64 76 cột, MIME Nested cho thư mục
│   ├── LineConnection.java        # Quản lý Socket TCP/SSL/STARTTLS an toàn byte-level
│   ├── SmtpSocketClient.java      # Client SMTP thuần Socket chuẩn 8 bước
│   ├── Pop3SocketClient.java      # Client POP3 thuần Socket (STAT, LIST, RETR, DELE)
│   ├── ImapSocketClient.java      # Client IMAP thuần Socket (LOGIN, SELECT, FETCH, STORE, EXPUNGE)
│   ├── SocketMailService.java     # Cài đặt MailService bằng Socket
│   ├── JavaMailErrors.java        # Chuẩn hoá ngoại lệ cho JavaMail
│   ├── JavaMailService.java       # Cài đặt MailService bằng JavaMail API
│   └── MailServiceFactory.java    # Factory tạo đối tượng MailService
├── test/                          # Bộ 53 bài kiểm thử tự động JUnit 5
│   ├── ScriptedServer.java        # Mock server giả lập kết nối TCP Socket cục bộ
│   └── *Test.java                 # 15 lớp kiểm thử đơn vị & tích hợp
├── lib/                           # Thư viện JAR (javax.mail, activation, junit standalone)
├── attachments/                   # File mẫu đính kèm thử nghiệm
├── bin/                           # Bytecode .class sau khi biên dịch
├── logs/                          # mail-client.log ghi lại giao dịch C: và S:
├── setup-libs.bat                 # Script tự động tải thư viện JAR
├── build.bat                      # Script biên dịch toàn bộ dự án
├── test.bat                       # Script chạy 53 bài kiểm thử tự động
└── run.bat                        # Script khởi chạy ứng dụng GUI
```

---

## 3. HƯỚNG DẪN CÀI ĐẶT & CHẠY ỨNG DỤNG

### Bước 1: Yêu cầu môi trường
- Máy tính đã cài đặt **Java JDK 8 trở lên** (JDK 8, 11, 17, 21+ đều tương thích).
- Biến môi trường `JAVA_HOME` và lệnh `javac`, `java` đã được thêm vào PATH.

### Bước 2: Tải thư viện
Chạy script để tự động tải 3 thư viện JAR vào thư mục `lib/`:
```bat
setup-libs.bat
```

### Bước 3: Chạy bộ kiểm thử tự động (53 Tests PASS)
Bộ kiểm thử tích hợp mock server cục bộ `ScriptedServer`, giúp kiểm tra 100% chức năng mà **không cần kết nối internet hay tài khoản Gmail thật**:
```bat
test.bat
```
Hoặc chạy riêng một lớp kiểm thử cụ thể:
```bat
test.bat SmtpSocketClientTest
test.bat Pop3SocketClientTest
test.bat ImapSocketClientTest
```

### Bước 4: Khởi chạy ứng dụng
```bat
run.bat
```
Hoặc bằng lệnh `javac` và `java` chuẩn theo đúng mục 10 của giáo trình:
```bat
javac -encoding UTF-8 -cp ".;lib/*" -d bin src/*.java
java -cp "bin;lib/*" EmailClientApp
```

---

## 4. HƯỚNG DẪN KIỂM THỬ THỰC TẾ TRÊN GMAIL

1. **Chuẩn bị tài khoản Gmail:**
   - Bật **Xác minh 2 bước** (2-Step Verification) trong tài khoản Google.
   - Tạo **Mật khẩu ứng dụng (App Password)** 16 ký tự (dạng `abcd efgh ijkl mnop`).
   - Vào Gmail $\to$ Cài đặt $\to$ **Chuyển tiếp và POP/IMAP** $\to$ Bật cả **POP** và **IMAP** $\to$ Lưu thay đổi.
2. **Khởi chạy ứng dụng:** Chạy `run.bat`.
3. **Thực hiện ma trận kiểm thử:**

| STT | Engine | Hành Động | Kết Quả Mong Đợi |
|:---:|:---:|:---|:---|
| 1 | `SOCKET` | Gửi email text kèm file đính kèm | Email tới hộp thư đến, tiêu đề tiếng Việt chuẩn xác, file đính kèm nguyên vẹn. |
| 2 | `SOCKET` | Gửi email HTML (tick chọn "Nội dung HTML") | Email hiển thị đúng các thẻ định dạng HTML trên Gmail. |
| 3 | `SOCKET` | Bấm "📁 + Thêm Thư Mục..." rồi gửi | Thư mục được đóng gói dạng MIME Nested đệ quy gửi đi thành công. |
| 4 | `JAVAMAIL` | Gửi email text + đính kèm qua JavaMail | Tương tự ca 1, gửi thành công qua JavaMail API. |
| 5 | `JAVAMAIL` | Gửi email HTML qua JavaMail | Tương tự ca 2, hiển thị định dạng HTML đẹp mắt. |
| 6 | `SOCKET` + `POP3` | Bấm "📥 Tải hộp thư" | Tải 20 thư mới nhất, hiển thị bảng, bấm vào xem được chi tiết và danh sách file đính kèm. |
| 7 | `SOCKET` + `IMAP` | Bấm "📥 Tải hộp thư" | Tải thư nhanh chóng qua IMAP, giữ nguyên trạng thái đọc của thư. |
| 8 | `JAVAMAIL` + `POP3` | Tải hộp thư qua JavaMail | Tải danh sách thư thành công qua Store POP3. |
| 9 | `JAVAMAIL` + `IMAP` | Tải hộp thư qua JavaMail | Tải danh sách thư thành công qua Store IMAP. |
| 10 | Bất kỳ | Chọn file trong danh sách đính kèm $\to$ Bấm "💾 Lưu đính kèm..." | Hộp thoại FileChooser hiện lên, lưu file nhị phân ra ổ đĩa thành công. |
| 11 | Bất kỳ | Chọn một thư $\to$ Bấm "🗑 Xóa thư đã chọn" | Thư bị xóa trên server (lệnh `DELE` hoặc `STORE \Deleted + EXPUNGE`), biến mất khỏi danh sách. |
| 12 | Bất kỳ | Chọn tab "🌐 Xem HTML Trực Quan" / "📄 Xem Mã Nguồn" | Chuyển đổi mượt mà giữa hiển thị render HTML và mã nguồn thô. |
| 13 | Bất kỳ | Nhập sai mật khẩu | Nhận thông báo lỗi 535, console log không thử lại (lỗi vĩnh viễn). |
| 14 | Bất kỳ | Rút mạng / tắt Wi-Fi khi đang gửi | Console log hiện thông báo lỗi tạm thời và tự động thử lại 3 lần với khoảng chờ nhân đôi. |

---

## 5. HƯỚNG DẪN KIỂM THỬ TRÊN MAIL SERVER PHÒNG LAB SGU

Nếu thực hành hoặc chấm điểm trên Mail Server nội bộ của trường ĐH Sài Gòn (mạng LAN hoặc localhost):
1. Bấm nút **"⚙ Cấu hình Server..."** trên thanh công cụ.
2. Nhập IP/Hostname máy chủ lab (ví dụ: `192.168.1.100` hoặc `localhost`).
3. Đặt cổng (SMTP: `25`, POP3: `110`, IMAP: `143`) và chọn Bảo mật là `NONE`.
4. Bấm **"Lưu cấu hình"**.
5. Nhập tài khoản và mật khẩu của máy chủ lab rồi thực hiện gửi/nhận bình thường!
