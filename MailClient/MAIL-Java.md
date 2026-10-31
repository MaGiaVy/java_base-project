# GIÁO TRÌNH LẬP TRÌNH MẠNG: XÂY DỰNG ỨNG DỤNG MAIL CLIENT BẰNG JAVA

**Trường Đại học Sài Gòn (Saigon University)**  
**Giảng viên:** TRẦN MINH NHẬT  
**Môn học:** Lập Trình Mạng (Network Programming)  
**Tài liệu xuất từ:** `MAIL - Java.pptx`

---

## MỤC LỤC
1. [Tổng Quan & Mục Tiêu Bài Học](#1-tổng-quan--mục-tiêu-bài-học)
2. [Nguyên Lý Hoạt Động & Kiến Trúc Hệ Thống Email](#2-nguyên-lý-hoạt-động--kiến-trúc-hệ-thống-email)
3. [So Sánh Các Giao Thức Email: SMTP, POP3, IMAP](#3-so-sánh-các-giao-thức-email-smtp-pop3-imap)
4. [Giao Thức SMTP (Simple Mail Transfer Protocol)](#4-giao-thức-smtp-simple-mail-transfer-protocol)
   - [Khái niệm, vai trò & đặc điểm](#khái-niệm-vai-trò--đặc-điểm)
   - [Cấu trúc lệnh & Phản hồi (Command -> Reply Code)](#cấu-trúc-lệnh--phản-hồi-command---reply-code)
   - [Quy trình gửi Email 8 bước bằng SMTP](#quy-trình-gửi-email-8-bước-bằng-smtp)
5. [Lập Trình SMTP Client Bằng Java Socket](#5-lập-trình-smtp-client-bằng-java-socket)
   - [Kết nối TCP Socket & Truyền nhận lệnh](#kết-nối-tcp-socket--truyền-nhận-lệnh)
   - [Mã hóa Base64 & Xác thực AUTH LOGIN](#mã-hóa-base64--xác-thực-auth-login)
   - [Định dạng dữ liệu lệnh DATA & Kỹ thuật Dot-Stuffing](#định-dạng-dữ-liệu-lệnh-data--kỹ-thuật-dot-stuffing)
   - [Mã nguồn Java SmtpMailClient hoàn chỉnh](#mã-nguồn-java-smtpmailclient-hoàn-chỉnh)
6. [Bảo Mật Kết Nối: SSL/TLS & STARTTLS](#6-bảo-mật-kết-nối-ssltls--starttls)
   - [So sánh cổng 25, 587 và 465](#so-sánh-cổng-25-587-và-465)
   - [Mã nguồn Java SmtpSslClient (Port 465)](#mã-nguồn-java-smtpsslclient-port-465)
7. [Cấu Trúc MIME & Xử Lý Email Đa Phương Tiện](#7-cấu-trúc-mime--xử-lý-email-đa-phương-tiện)
   - [MIME Version, Content-Type & Boundary](#mime-version-content-type--boundary)
   - [Xử lý File đính kèm (Multipart/Mixed & Base64 Split)](#xử-lý-file-đính-kèm-multipartmixed--base64-split)
   - [Xử lý nhiều File đính kèm & Thư mục (MIME Nested)](#xử-lý-nhiều-file-đính-kèm--thư-mục-mime-nested)
8. [Nhận Email Với POP3 & IMAP Bằng Java Socket](#8-nhận-email-với-pop3--imap-bằng-java-socket)
   - [Quy trình nhận Email bằng POP3](#quy-trình-nhận-email-bằng-pop3)
   - [Mã nguồn Java Pop3Client hoàn chỉnh](#mã-nguồn-java-pop3client-hoàn-chỉnh)
9. [Xử Lý Lỗi, Thử Lại (Retry) & Tối Ưu Ứng Dụng](#9-xử-lý-lỗi-thử-lại-retry--tối-ưu-ứng-dụng)
   - [Phân loại Reply Code (2xx, 3xx, 4xx, 5xx)](#phân-loại-reply-code-2xx-3xx-4xx-5xx)
   - [Chiến lược Exponential Backoff & Retry Logic](#chiến-lược-exponential-backoff--retry-logic)
10. [Xây Dựng Mail Client Hoàn Chỉnh Với JavaMail (`javax.mail`)](#10-xây-dựng-mail-client-hoàn-chỉnh-với-javamail-javaxmail)
    - [Cấu trúc thư mục dự án & Cấu hình](#cấu-trúc-thư-mục-dự-án--cấu-hình)
    - [Mã nguồn JavaMail gửi HTML + Attachment + Log + Retry](#mã-nguồn-javamail-gửi-html--attachment--log--retry)

---

## 1. TỔNG QUAN & MỤC TIÊU BÀI HỌC

### Mục Tiêu Chính
- **Hiểu rõ nguyên lý hoạt động của Email** và các giao thức mạng cốt lõi (SMTP, POP3, IMAP).
- **Thực hành giao thức SMTP** để khởi tạo, đóng gói và gửi Email từ mức thấp (TCP Socket).
- **Thực hành giao thức POP3/IMAP** để đọc, quản lý và tải Email từ Mailbox Server.
- **Xây dựng Mail Client bằng Java**: Sử dụng `Socket`, `InputStream`/`OutputStream`, `BufferedReader`/`PrintWriter`, mã hóa `SSL/TLS` và thư viện cao cấp `javax.mail` (JavaMail API).
- **Xử lý nội dung Email phức tạp**: Định dạng văn bản mã hóa UTF-8, cấu trúc HTML, MIME Multipart, mã hóa file đính kèm bằng Base64.
- **Xử lý ngoại lệ & bảo mật**: Phân tích reply code, cơ chế thử lại (retry), App Password và mã hóa kênh truyền an toàn.

---

## 2. NGUYÊN LÝ HOẠT ĐỘNG & KIẾN TRÚC HỆ THỐNG EMAIL

Hệ thống Email hoạt động theo mô hình Client-Server không đồng bộ, bao gồm 3 thành phần chính:
1. **Mail Client (MUA - Mail User Agent):** Soạn thảo, gửi, tải và đọc Email (ví dụ: Java Application, Outlook, Thunderbird).
2. **SMTP Server (MTA - Mail Transfer Agent):** Tiếp nhận Email từ Client và chuyển tiếp qua Internet đến Mail Server đích.
3. **Mailbox Server (MDA - Mail Delivery Agent):** Tiếp nhận Email đến, lưu trữ vào Mailbox (Inbox, Sent, Draft, Trash) và phục vụ truy xuất qua POP3/IMAP.

### Luồng Truyền Email Qua Internet
```
[Người gửi] --> (Mail Client)
                     |
                 (SMTP: 25/587/465)
                     v
             [SMTP Server gửi]
                     |
                (INTERNET)
                     v
          [Mailbox Server nhận]
                     |
               (POP3: 110/995 hoặc IMAP: 143/993)
                     v
 [Người nhận] <-- (Mail Client)
```

---

## 3. SO SÁNH CÁC GIAO THỨC EMAIL: SMTP, POP3, IMAP

| Tiêu chí | SMTP (Simple Mail Transfer Protocol) | POP3 (Post Office Protocol v3) | IMAP (Internet Message Access Protocol) |
| :--- | :--- | :--- | :--- |
| **Chức năng chính** | Gửi và chuyển tiếp Email giữa Client - Server & Server - Server. | Nhận và tải Email từ Mailbox Server về máy Client. | Truy cập và đồng bộ hóa Email trực tiếp trên Mailbox Server. |
| **Hướng truyền** | Một chiều (Client -> Server) | Một chiều (Server -> Client) | Hai chiều (Đồng bộ 2 chiều) |
| **Cổng mặc định (Thường)** | Port **25** (MTA-MTA) | Port **110** | Port **143** |
| **Cổng an toàn (SSL/TLS)** | Port **587** (STARTTLS) / Port **465** (SMTPS) | Port **995** (POP3S) | Port **993** (IMAPS) |
| **Lưu trữ trên Server** | Không lưu trữ dài hạn (Chuyển tiếp đến đích). | Thường xóa Email trên Server sau khi đã tải về máy. | Giữ nguyên và đồng bộ tất cả Email, thư mục trên Server. |
| **Quản lý đa thiết bị** | Không áp dụng | Hạn chế (Dễ lệch trạng thái đọc/xóa trên các máy) | Rất tốt (Đồng bộ hoàn hảo trên nhiều thiết bị) |

---

## 4. GIAO THỨC SMTP (SIMPLE MAIL TRANSFER PROTOCOL)

### Khái Niệm, Vai Trò & Đặc Điểm
- **Đặc điểm:** Giao thức tầng ứng dụng (Application Layer), chạy trên nền TCP.
- **Mô hình làm việc:** Dạng TEXT phản hồi tương tác **Command / Reply** (Client gửi câu lệnh dạng chữ, Server đáp lại bằng mã số 3 chữ số).
- **Định dạng tin nhắn:** Các câu lệnh và kết thúc dòng bắt buộc sử dụng chuỗi kí tự **CRLF** (`\r\n`).

### Cấu Trúc Lệnh & Phản Hồi (Command -> Reply Code)
Cấu trúc tổng quát lệnh SMTP:
```text
COMMAND [tham_số]

```

Các câu lệnh SMTP thông dụng:
- `EHLO <client_domain>`: Chào hỏi server và truy vấn các tính năng nâng cao (ESMTP).
- `HELO <client_domain>`: Chào hỏi phiên bản SMTP tiêu chuẩn cũ.
- `STARTTLS`: Yêu cầu nâng cấp kết nối TCP thường sang mã hóa TLS.
- `AUTH LOGIN`: Bắt đầu quá trình xác thực tài khoản (Username/Password dạng Base64).
- `MAIL FROM:<nguoi_gui@domain.com>`: Khai báo địa chỉ Email người gửi.
- `RCPT TO:<nguoi_nhan@domain.com>`: Khai báo địa chỉ Email người nhận.
- `DATA`: Bắt đầu gửi nội dung Email (Header + Body).
- `.`: Một dấu chấm duy nhất trên một dòng riêng biệt (`
.
`) dùng để kết thúc lệnh DATA.
- `RSET`: Hủy bỏ phiên giao dịch hiện tại, xóa thông tin MAIL FROM & RCPT TO.
- `NOOP`: Kiểm tra kết nối (Keep-alive).
- `QUIT`: Đóng phiên kết nối SMTP.

### Quy Trình Gửi Email 8 Bước Bằng SMTP
1. **Connect:** Thiết lập kết nối TCP Socket tới SMTP Server (Port 25/587/465).
2. **EHLO / HELO:** Chào mừng và gửi tên miền/hostname Client.
3. **AUTH (AUTH LOGIN / PLAIN):** Xác thực tài khoản người dùng nếu Server yêu cầu.
4. **MAIL FROM:** Khai báo thông tin địa chỉ người gửi.
5. **RCPT TO:** Khai báo thông tin địa chỉ người nhận.
6. **DATA:** Phát tín hiệu chuẩn bị gửi nội dung email.
7. **Body Content & End of DATA:** Truyền Header, dòng trống, nội dung text/MIME và kết thúc bằng `
.
`.
8. **QUIT:** Gửi lệnh thoát và đóng Socket.

---

## 5. LẬP TRÌNH SMTP CLIENT BẰNG JAVA SOCKET

### Mã Nguồn Java SmtpMailClient Hoàn Chỉnh

```java
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class SmtpMailClient {
    private String host = "smtp.example.com";
    private int port = 587; // 587 (STARTTLS) hoặc 465 (TLS)
    private String username = "user@example.com";
    private String password = "user_password_secret";
    private String from = "user@example.com";
    private String to = "receiver@example.com";

    public void sendEmail(String subject, String body) throws Exception {
        System.out.println("Connecting to " + host + ":" + port + "...");
        Socket socket = new Socket(host, port);
        
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter writer = new PrintWriter(
            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

        // Đọc Banner chào mừng từ Server (Reply 220)
        String response = readLine(reader);
        System.out.println("S: " + response);

        // 2. Gửi EHLO
        sendCommand(writer, reader, "EHLO " + InetAddress.getLocalHost().getHostName());

        // 3. Nâng cấp STARTTLS (nếu dùng cổng 587)
        if (port == 587) {
            sendCommand(writer, reader, "STARTTLS");
            sendCommand(writer, reader, "EHLO " + InetAddress.getLocalHost().getHostName());
        }

        // 4. Xác thực AUTH LOGIN
        sendCommand(writer, reader, "AUTH LOGIN");
        sendCommand(writer, reader, base64Encode(username));
        sendCommand(writer, reader, base64Encode(password));

        // 5. Khai báo MAIL FROM & RCPT TO
        sendCommand(writer, reader, "MAIL FROM:<" + from + ">");
        sendCommand(writer, reader, "RCPT TO:<" + to + ">");

        // 6. Gửi lệnh DATA và Nội dung Email
        sendCommand(writer, reader, "DATA");
        
        String dataPayload = buildEmailPayload(from, to, subject, body);
        writer.print(dataPayload);
        writer.flush();
        
        String dataResponse = readLine(reader);
        System.out.println("S: " + dataResponse);

        // 7. Đóng kết nối
        sendCommand(writer, reader, "QUIT");
        socket.close();
        System.out.println("Email sent successfully!");
    }

    private void sendCommand(PrintWriter writer, BufferedReader reader, String cmd) throws Exception {
        System.out.println("C: " + cmd);
        writer.print(cmd + "
");
        writer.flush();
        String line = readLine(reader);
        System.out.println("S: " + line);
    }

    private String readLine(BufferedReader reader) throws Exception {
        return reader.readLine();
    }

    private String base64Encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private String buildEmailPayload(String from, String to, String subject, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("From: ").append(from).append("
");
        sb.append("To: ").append(to).append("
");
        sb.append("Subject: ").append(subject).append("
");
        sb.append("MIME-Version: 1.0
");
        sb.append("Content-Type: text/plain; charset=utf-8
");
        sb.append("
"); // Dòng trống ngăn cách Header và Body
        
        // Kỹ thuật Dot-stuffing xử lý dòng bắt đầu bằng dấu chấm
        String[] lines = body.split("
");
        for (String line : lines) {
            if (line.startsWith(".")) {
                sb.append("."); // Thêm 1 dấu chấm phòng ngừa bị ngắt lệnh DATA
            }
            sb.append(line).append("
");
        }
        
        sb.append(".
"); // Dấu chấm kết thúc truyền dữ liệu DATA
        return sb.toString();
    }

    public static void main(String[] args) {
        try {
            SmtpMailClient client = new SmtpMailClient();
            client.sendEmail("Test Subject Java Socket", "Xin chào! Đây là nội dung Email thử nghiệm.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

---

## 6. BẢO MẬT KẾT NỐI: SSL/TLS & STARTTLS

### So Sánh Cổng & Phương Thức Mã Hóa
1. **Port 25:** Kết nối Plaintext không mã hóa.
2. **Port 587 (STARTTLS):** Thiết lập kết nối TCP Plaintext trước, sau đó phát lệnh `STARTTLS` để nâng cấp kênh truyền thành mã hóa an toàn.
3. **Port 465 (SMTPS):** Mở kết nối mã hóa **SSL/TLS trực tiếp** ngay từ thời điểm khởi tạo TCP Socket.

### Mã Nguồn Java SmtpSslClient (Port 465 - Implicit TLS)

```java
import java.io.*;
import java.net.*;
import java.util.Base64;
import javax.net.ssl.*;

public class SmtpSslClient {
    public static void main(String[] args) {
        String host = "smtp.example.com";
        int port = 465;
        String username = "user@example.com";
        String password = "user_password_secret";

        try {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), "UTF-8"));
            PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            System.out.println("S: " + reader.readLine());

            sendCommand(writer, reader, "EHLO client.local");
            sendCommand(writer, reader, "AUTH LOGIN");
            sendCommand(writer, reader, Base64.getEncoder().encodeToString(username.getBytes()));
            sendCommand(writer, reader, Base64.getEncoder().encodeToString(password.getBytes()));

            sendCommand(writer, reader, "MAIL FROM:<" + username + ">");
            sendCommand(writer, reader, "RCPT TO:<receiver@example.com>");
            sendCommand(writer, reader, "DATA");

            writer.print("From: " + username + "
");
            writer.print("To: receiver@example.com
");
            writer.print("Subject: Test Email qua SSL Port 465

");
            writer.print("Noi dung email qua SSLSocket an toan.
.
");
            writer.flush();
            System.out.println("S: " + reader.readLine());

            sendCommand(writer, reader, "QUIT");
            socket.close();
            System.out.println("Sent mail via SMTPS SSLSocket!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sendCommand(PrintWriter writer, BufferedReader reader, String cmd) throws IOException {
        writer.print(cmd + "
");
        writer.flush();
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println("S: " + line);
            if (line.length() >= 4 && line.charAt(3) == ' ') break;
        }
    }
}
```

---

## 7. CẤU TRÚC MIME & XỬ LÝ EMAIL ĐA PHƯƠNG TIỆN

### MIME (Multipurpose Internet Mail Extensions)
MIME hỗ trợ gửi Email chứa tiếng Việt, HTML, hình ảnh và tập tin đính kèm.

- `MIME-Version: 1.0`
- `Content-Type`: `text/plain`, `text/html`, `image/png`, `application/pdf`, `multipart/mixed`.
- `Content-Transfer-Encoding`: `7bit`, `8bit`, `base64`.

### Xử Lý File Đính Kèm (Multipart/Mixed & Chia Dòng Base64 76 Ký Tự)

```java
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

public class MimeAttachmentHelper {

    public static String encodeFileToBase64Mime(String filePath) throws IOException {
        byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
        String base64Raw = Base64.getEncoder().encodeToString(fileBytes);
        
        StringBuilder formattedBase64 = new StringBuilder();
        int chunkSize = 76;
        for (int i = 0; i < base64Raw.length(); i += chunkSize) {
            int end = Math.min(chunkSize, base64Raw.length() - i);
            formattedBase64.append(base64Raw, i, i + end).append("
");
        }
        return formattedBase64.toString();
    }

    public static String createMultipartPayload(String from, String to, String subject, 
                                               String textBody, String filePath, String fileName) throws IOException {
        String boundary = "----=_Part_" + System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();

        sb.append("From: ").append(from).append("
");
        sb.append("To: ").append(to).append("
");
        sb.append("Subject: ").append(subject).append("
");
        sb.append("MIME-Version: 1.0
");
        sb.append("Content-Type: multipart/mixed; boundary="").append(boundary).append(""

");

        // Part 1: Text Body
        sb.append("--").append(boundary).append("
");
        sb.append("Content-Type: text/plain; charset=utf-8
");
        sb.append("Content-Transfer-Encoding: 8bit

");
        sb.append(textBody).append("

");

        // Part 2: File Attachment
        sb.append("--").append(boundary).append("
");
        sb.append("Content-Type: application/pdf; name="").append(fileName).append(""
");
        sb.append("Content-Transfer-Encoding: base64
");
        sb.append("Content-Disposition: attachment; filename="").append(fileName).append(""

");
        sb.append(encodeFileToBase64Mime(filePath)).append("
");

        sb.append("--").append(boundary).append("--
.
");

        return sb.toString();
    }
}
```

---

## 8. NHẬN EMAIL VỚI POP3 & IMAP BẰNG JAVA SOCKET

### Quy Trình Nhận Email Bằng POP3
1. **Kết nối Socket:** Port 110 hoặc 995 (POP3S).
2. **Đăng nhập:** `USER <username>` và `PASS <password>`.
3. **Thống kê:** `STAT`
4. **Danh sách Email:** `LIST`
5. **Tải Email:** `RETR <stt_email>`
6. **Thoát:** `QUIT`

### Mã Nguồn Java Pop3Client Hoàn Chỉnh

```java
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Pop3Client {
    private String host;
    private int port;
    private String username;
    private String password;

    public Pop3Client(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public void run() {
        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(
                 new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
             PrintWriter writer = new PrintWriter(
                 new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII), true)) {

            System.out.println("S: " + reader.readLine());

            sendCommand(writer, reader, "USER " + username);
            sendCommand(writer, reader, "PASS " + password);
            sendCommand(writer, reader, "STAT");

            System.out.println("--- DANH SÁCH EMAIL ---");
            List<String> listResponse = sendMultiLineCommand(writer, reader, "LIST");
            for (String line : listResponse) {
                System.out.println(line);
            }

            System.out.println("--- NỘI DUNG EMAIL SỐ 1 ---");
            List<String> emailLines = sendMultiLineCommand(writer, reader, "RETR 1");
            for (String line : emailLines) {
                System.out.println(line);
            }

            sendCommand(writer, reader, "QUIT");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String sendCommand(PrintWriter writer, BufferedReader reader, String cmd) throws IOException {
        System.out.println("C: " + cmd);
        writer.print(cmd + "
");
        writer.flush();
        String response = reader.readLine();
        System.out.println("S: " + response);
        return response;
    }

    private List<String> sendMultiLineCommand(PrintWriter writer, BufferedReader reader, String cmd) throws IOException {
        List<String> lines = new ArrayList<>();
        String status = sendCommand(writer, reader, cmd);
        if (!status.startsWith("+OK")) {
            return lines;
        }
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.equals(".")) {
                break;
            }
            lines.add(line);
        }
        return lines;
    }

    public static void main(String[] args) {
        Pop3Client pop3 = new Pop3Client("pop3.example.com", 110, "user@example.com", "secret_pass");
        pop3.run();
    }
}
```

---

## 9. XỬ LÝ LỖI, THỬ LẠI (RETRY) & TỐI ƯU ỨNG DỤNG

### Phân Loại Mã Phản Hồi (Reply Code)
- **Mã 2xx (Thành công):** Lệnh hoàn thành (`220 Ready`, `250 OK`, `235 Auth Successful`).
- **Mã 3xx (Trung gian):** Cần thông tin bổ sung (`334 Auth Prompt`, `354 Start Input`).
- **Mã 4xx (Lỗi tạm thời):** Lỗi mạng/server (`421 Service Not Available`, `450 Mailbox Busy`). -> **Thực hiện Retry**.
- **Mã 5xx (Lỗi vĩnh viễn):** Sai mật khẩu, không tồn tại mail (`535 Auth Failed`, `550 Mailbox Not Found`). -> **Không Retry**.

---

## 10. XÂY DỰNG MAIL CLIENT HOÀN CHỈNH VỚI JAVAMAIL (javax.mail)

### Cấu Trúc Thư Mục Dự Án
```
EmailClientApp/
├── src/
│   └── EmailClientApp.java
├── lib/
│   ├── javax.mail-1.6.2.jar
│   └── activation-1.2.2.jar
└── attachments/
    ├── report.pdf
    └── image.png
```

### Mã Nguồn JavaMail API (Gửi HTML + Attachment)

```java
import java.io.File;
import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;

public class EmailClientApp {
    public static void main(String[] args) {
        String host = "smtp.example.com";
        int port = 587;
        final String username = "user@example.com";
        final String password = "user_password_secret";

        String toEmail = "receiver@example.com";
        String subject = "Báo cáo doanh thu tháng 8 - Mail Client Java";

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username, "Phòng Kế Toán"));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
            message.setSubject(subject, "UTF-8");

            MimeBodyPart htmlPart = new MimeBodyPart();
            String htmlContent = "<h2 style='color:blue;'>BÁO CÁO KẾT QUẢ KINH DOANH</h2>"
                + "<p>Xin chào,</p>"
                + "<p>Gửi bạn file báo cáo kết quả doanh thu chi tiết trong đính kèm.</p>"
                + "<br><p>Trân trọng,<br><b>Java Mail Client App</b></p>";
            htmlPart.setContent(htmlContent, "text/html; charset=utf-8");

            MimeBodyPart attachmentPart = new MimeBodyPart();
            File attachmentFile = new File("attachments/report.pdf");
            if (attachmentFile.exists()) {
                attachmentPart.attachFile(attachmentFile);
            }

            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(htmlPart);
            if (attachmentFile.exists()) {
                multipart.addBodyPart(attachmentPart);
            }

            message.setContent(multipart);

            Transport.send(message);
            System.out.println("Gửi Email thành công qua JavaMail API!");

        } catch (Exception e) {
            System.err.println("Lỗi trong quá trình gửi Email: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
```

### Lệnh Biên Dịch & Chạy Chương Trình
- **Biên dịch:** `javac -cp ".;lib/*" src/EmailClientApp.java -d bin/`
- **Chạy ứng dụng:** `java -cp "bin;lib/*" EmailClientApp`

---
*Tài liệu được tổng hợp chi tiết và tự động chuyển đổi sang Markdown chuẩn hóa từ bộ Slide MAIL - Java.pptx.*
