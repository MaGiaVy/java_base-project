# NỘI DUNG SLIDE BÀI GIẢNG: LẬP TRÌNH FTP VỚI JAVA
* **Trường:** Đại học Sài Gòn (SGU) - Môn Lập trình mạng (Network Programming)
* **Giảng viên:** Trần Minh Nhật
* **Tài liệu gốc:** `FTP - Java.pptx` (Slide 1 đến Slide 30)

---

## SLIDE 1: LẬP TRÌNH FTP VỚI JAVA
* **Tên bài học:** File Transfer Protocol – Truyền tập tin theo mô hình Client–Server (Java)
* **Mô hình tổng quan:**
  * **FTP Client (Java):** Sử dụng thư viện Socket.
  * **FTP Server (Java):** Sử dụng ServerSocket.
  * **Control Connection:** Kết nối điều khiển chạy trên TCP Port 21 (truyền Lệnh / Phản hồi - Command / Reply).
  * **Data Connection:** Kết nối dữ liệu (truyền dữ liệu file: UPLOAD & DOWNLOAD).
* **Nội dung sẽ học:**
  1. Giao thức FTP
  2. Lệnh / Phản hồi (Command / Reply)
  3. Đăng nhập (Login)
  4. Danh sách tập tin (LIST)
  5. Tải xuống (DOWNLOAD - RETR)
  6. Tải lên (UPLOAD - STOR)
  7. Lập trình FTP Client bằng Java
  8. Lập trình FTP Server bằng Java
* **Mục tiêu:** Hiểu giao thức FTP và tự xây dựng chương trình FTP Client–Server bằng Java trên TCP Socket.

---

## SLIDE 2: MỤC TIÊU BÀI HỌC JAVA
Sau bài học, sinh viên có thể:
1. **Hiểu giao thức FTP:**
   * Khái niệm và chức năng của FTP.
   * Mô hình FTP Client – FTP Server.
   * Vai trò của TCP trong FTP.
2. **Hiểu cơ chế kết nối:**
   * Control Connection – TCP Port 21.
   * Data Connection dùng để truyền dữ liệu.
   * Phân biệt Active Mode và Passive Mode.
3. **Hiểu các lệnh FTP:**
   * Các lệnh: `USER`, `PASS`, `PWD`, `CWD`, `LIST`.
   * Truyền file: `RETR`, `STOR`.
   * Hiểu các mã phản hồi: `220`, `331`, `230`, `150`, `226`...
4. **Lập trình FTP bằng Java:**
   * Sử dụng Socket TCP (`java.net`).
   * Xử lý giao tiếp Client – Server.
   * Gửi lệnh và nhận phản hồi từ Server.
   * Truyền và nhận file (tải lên, tải xuống).
5. **Xây dựng ứng dụng hoàn chỉnh:**
   * Xây dựng FTP Client đầy đủ chức năng.
   * Xây dựng FTP Server xử lý nhiều Client.
   * Kiểm tra, xử lý lỗi và bảo mật cơ bản.
* **Kết quả cuối bài:** Sinh viên có thể **tự xây dựng FTP Client và FTP Server bằng Java dựa trên TCP Socket** (thực hiện các chức năng: Login USER/PASS, LIST danh sách, Download RETR, Upload STOR, Logout QUIT).

---

## SLIDE 3: FTP LÀ GÌ?
FTP (File Transfer Protocol) là giao thức dùng để truyền tập tin giữa Client và Server thông qua mạng TCP/IP.

1. **Chức năng chính:**
   * **Upload:** Client $\rightarrow$ Server
   * **Download:** Server $\rightarrow$ Client
   * Xem danh sách file / thư mục
   * Di chuyển giữa các thư mục
   * Tạo, xóa, đổi tên file / thư mục
   * Xác thực bằng Username / Password
2. **Đặc điểm quan trọng:**
   * Hoạt động ở tầng **Application** (Ứng dụng).
   * Sử dụng giao thức truyền tải **TCP**.
   * **Port 21:** Control Connection.
   * Dữ liệu được truyền qua **Data Connection**.
   * Có hai chế độ: **Active Mode** và **Passive Mode**.
* **Ghi nhớ:** FTP không chỉ sử dụng một kết nối TCP: một kết nối để điều khiển (**Control Connection**) và một kết nối riêng để truyền dữ liệu (**Data Connection**).

---

## SLIDE 4: MÔ HÌNH HOẠT ĐỘNG FTP (JAVA)
* **FTP Client (Java):**
  * Chương trình Java phía người dùng.
  * Gửi lệnh FTP đến Server.
  * Upload / Download file.
* **FTP Server (Java):**
  * Lắng nghe kết nối từ Client.
  * Xác thực Username / Password.
  * Quản lý file và thư mục.
  * Xử lý các FTP Command.
* **Luồng kết nối:**
  1. **Control Connection (TCP Port 21):**
     * Dùng để gửi lệnh và nhận phản hồi (ví dụ: `USER student` $\rightarrow$ `331 Password required`, `PASS 123456` $\rightarrow$ `230 Login successful`).
     * Các mã phản hồi: `1xx` (Thông báo), `2xx` (Thành công), `3xx` (Cần thao tác thêm), `4xx` (Lỗi tạm thời), `5xx` (Lỗi vĩnh viễn).
  2. **Data Connection (Khi truyền dữ liệu):**
     * Dùng để truyền dữ liệu (file hoặc danh sách thư mục).
     * `LIST` $\rightarrow$ Server gửi danh sách file/thư mục cho Client.
     * `RETR` $\rightarrow$ Download file từ Server về Client.
     * `STOR` $\rightarrow$ Upload file từ Client lên Server.
* **Ghi nhớ:** Control Connection (TCP 21) thường duy trì trong suốt phiên FTP, còn Data Connection chỉ được tạo ra khi cần truyền dữ liệu và sẽ đóng lại sau mỗi lần truyền.

---

## SLIDE 5: HAI KẾT NỐI CỦA FTP
FTP sử dụng hai kết nối TCP riêng biệt: Control Connection và Data Connection.

### Bảng So Sánh Hai Kết Nối:
| Tiêu chí | Control Connection | Data Connection |
| :--- | :--- | :--- |
| **Mục đích** | Truyền Command / Reply | Truyền dữ liệu (file, danh sách) |
| **Port phía Server** | TCP 21 (mặc định) | Port phụ thuộc Active / Passive |
| **Thời điểm** | Thiết lập khi bắt đầu phiên FTP, duy trì đến khi `QUIT` | Tạo khi cần truyền dữ liệu, truyền xong thì đóng |
| **Dữ liệu truyền** | `USER`, `PASS`, `PWD`, `LIST`, `RETR`, `STOR`... | Danh sách file, nội dung file |
| **Đặc điểm** | Luôn có trong suốt phiên FTP | Có thể có nhiều lần trong phiên |

* **Ghi nhớ:** `LIST`, `RETR`, `STOR` được gửi qua Control Connection nhưng dữ liệu của các lệnh này được truyền qua Data Connection.

---

## SLIDE 6: CONTROL CONNECTION – TCP PORT 21
Control Connection là kết nối TCP dùng để gửi lệnh từ FTP Client và nhận phản hồi từ FTP Server.

1. **Thiết lập kết nối:** FTP Server mặc định lắng nghe tại **TCP Port 21**.
2. **Client gửi FTP Command:** Các lệnh gửi dưới dạng chuỗi văn bản (kết thúc bằng `\r\n` CRLF):
   * `USER student\r\n`
   * `PASS 123456\r\n`
   * `PWD\r\n`
   * `CWD /data\r\n`
   * `TYPE I\r\n`
   * `PASV\r\n`
   * `LIST\r\n`
   * `RETR report.pdf\r\n`
   * `STOR data.txt\r\n`
   * `QUIT\r\n`
3. **Server trả về FTP Reply:** Mỗi phản hồi bắt đầu bằng mã 3 chữ số:
   * `220` Service ready
   * `331` Password required
   * `230` Login successful
   * `250` Requested action okay
   * `150` Opening data connection
   * `226` Transfer complete
   * `221` Goodbye
4. **Điểm cần nhớ:**
   * Control Connection dùng TCP Port 21 (phía Server).
   * Truyền lệnh (Command) và phản hồi (Reply).
   * Thường duy trì trong toàn bộ phiên FTP.
   * Không trực tiếp truyền nội dung file.
   * **Ghi nhớ:** FTP Command kết thúc bằng `\r\n` (CRLF). Đây là điểm quan trọng khi tự xây dựng FTP Client/Server bằng Java/C#.

---

## SLIDE 7: DATA CONNECTION
Data Connection là kết nối TCP riêng của FTP dùng để truyền dữ liệu thực tế giữa FTP Client và FTP Server.

1. **Data Connection truyền gì?**
   * `LIST`: Danh sách file và thư mục.
   * `RETR`: Nội dung file từ Server $\rightarrow$ Client (Download).
   * `STOR`: Nội dung file từ Client $\rightarrow$ Server (Upload).
2. **Quy trình các lệnh:**
   * **LIST:** Control Connection nhận `150 Opening data connection` $\rightarrow$ Truyền danh sách qua Data Connection $\rightarrow$ Đóng Data Connection $\rightarrow$ Control Connection nhận `226 Transfer complete`.
   * **RETR:** Control Connection nhận `150` $\rightarrow$ Truyền nội dung file qua Data Connection $\rightarrow$ `226 Transfer complete`.
   * **STOR:** Control Connection nhận `150` $\rightarrow$ Truyền nội dung file qua Data Connection $\rightarrow$ `226 Transfer complete`.
3. **Vòng đời Data Connection:**
   * FTP Command (`LIST` / `RETR` / `STOR`) $\rightarrow$ Tạo Data Connection $\rightarrow$ Truyền dữ liệu $\rightarrow$ Đóng Data Connection $\rightarrow$ `226 Transfer Complete`.
* **Ghi nhớ:** Mỗi lần truyền dữ liệu thường sử dụng một Data Connection riêng.

---

## SLIDE 8: ACTIVE MODE VÀ PASSIVE MODE
FTP hỗ trợ 2 cách thiết lập Data Connection: **Active Mode (PORT)** và **Passive Mode (PASV)**.

### 1. Active Mode – Lệnh PORT:
* Client mở port và yêu cầu Server kết nối ngược về Client.
* **Các bước:**
  1. Client gửi `PORT 192,168,1,10,195,80`.
  2. Server trả về `200 PORT command successful`.
  3. Server chủ động tạo Data Connection kết nối đến Client.
* **Đặc điểm:** Server chủ động kết nối đến Client. Có thể gặp khó khăn với NAT/Firewall phía Client.

### 2. Passive Mode – Lệnh PASV:
* Server mở port và Client chủ động kết nối đến Server.
* **Các bước:**
  1. Client gửi `PASV`.
  2. Server trả về `227 Entering Passive Mode (192,168,1,100,195,80)`.
  3. Client tính Port: $\text{Port} = p_1 \times 256 + p_2 = 195 \times 256 + 80 = 50000$.
  4. Client chủ động tạo Data Connection kết nối đến Server (IP: `192.168.1.100`, Port: `50000`).
* **Đặc điểm:** Client chủ động kết nối đến Server. Vượt qua NAT/Firewall dễ dàng.

### 3. Bảng So Sánh PORT vs PASV:
| Tiêu chí | Active Mode (PORT) | Passive Mode (PASV) |
| :--- | :--- | :--- |
| **Lệnh Client gửi** | `PORT h1,h2,h3,h4,p1,p2` | `PASV` |
| **Bên mở port chờ** | Client | Server |
| **Bên tạo Data Connection** | Server kết nối đến Client | Client kết nối đến Server |
| **Hướng kết nối** | Server $\rightarrow$ Client | Client $\rightarrow$ Server |
| **Khả năng đi qua NAT/Firewall** | Khó hơn | Thuận lợi hơn |
| **Sử dụng hiện nay** | Ít phổ biến | Phổ biến (khuyến nghị) |

* **Khuyến nghị:** Trong các ứng dụng FTP Client/Server, nên ưu tiên sử dụng **Passive Mode (PASV)** để tương thích tốt hơn với mạng có NAT/Firewall.

---

## SLIDE 9: QUY TRÌNH MỘT PHIÊN FTP
Một phiên làm việc FTP trải qua 5 giai đoạn:
$$\text{CONNECT} \longrightarrow \text{LOGIN} \longrightarrow \text{COMMAND} \longrightarrow \text{TRANSFER} \longrightarrow \text{QUIT}$$

1. **CONNECT (Kết nối Server):** Client tạo Control Connection đến TCP Port 21 của Server $\rightarrow$ Server đáp `220 Service Ready`.
2. **LOGIN (Đăng nhập):** Gửi `USER student` $\rightarrow$ `331 Password required` $\rightarrow$ Gửi `PASS 123456` $\rightarrow$ `230 Login successful`.
3. **COMMAND (Gửi lệnh FTP):** `PWD`, `CWD /data`, `TYPE I`, `PASV`, `LIST`, `RETR report.pdf`, `STOR data.txt`...
4. **TRANSFER (Truyền dữ liệu):**
   * Bật PASV $\rightarrow$ Nhận `227 Entering Passive Mode` $\rightarrow$ Client mở Data Connection.
   * Gửi lệnh `LIST`/`RETR`/`STOR` qua Control Connection.
   * Server đáp `150 Opening Data Connection` $\rightarrow$ Truyền dữ liệu qua Data Connection $\rightarrow$ Truyền xong đóng Data Connection $\rightarrow$ Server đáp `226 Transfer Complete`.
5. **QUIT (Kết thúc phiên FTP):** Gửi `QUIT` $\rightarrow$ Server đáp `221 Goodbye` $\rightarrow$ Đóng Control Connection.

---

## SLIDE 10: CẤU TRÚC FTP COMMAND (JAVA)
FTP Client điều khiển FTP Server bằng các lệnh dạng văn bản (text command) được gửi qua Control Connection (TCP Port 21).

1. **Cấu trúc tổng quát:**
   $$\text{COMMAND} \quad [\text{space}] \quad \text{parameter} \quad \backslash\text{r}\backslash\text{n}$$
   * **COMMAND:** Tên lệnh FTP (ví dụ: `USER`, `PASS`, `LIST`).
   * **[space]:** Dấu cách (1 khoảng trắng).
   * **parameter:** Tham số của lệnh (có thể không có, ví dụ: `student`).
   * **\r\n:** Kết thúc dòng lệnh (CRLF bắt buộc).
2. **Lệnh không có tham số:** `PWD\r\n`, `PASV\r\n`, `QUIT\r\n`.
3. **Lệnh có tham số:** `USER student\r\n`, `CWD /data\r\n`, `RETR report.pdf\r\n`, `STOR sensor.txt\r\n`.
4. **Gửi FTP Command bằng Java:**
   ```java
   String command = "USER student\r\n";
   byte[] data = command.getBytes(StandardCharsets.ASCII);
   OutputStream out = socket.getOutputStream();
   out.write(data);
   out.flush();
   ```
   * Hàm dùng chung:
   ```java
   public void sendCommand(Socket socket, String command) throws IOException {
       byte[] data = command.getBytes(StandardCharsets.ASCII);
       socket.getOutputStream().write(data);
       socket.getOutputStream().flush();
   }
   ```
* **Ghi nhớ:** Mỗi FTP Command phải kết thúc bằng `\r\n` (CRLF). Sử dụng mã ASCII. Server sẽ không xử lý lệnh nếu thiếu CRLF.

---

## SLIDE 11: CÁC LỆNH FTP THƯỜNG DÙNG
1. **Nhóm đăng nhập:**
   * `USER <username>`: Gửi tên đăng nhập.
   * `PASS <password>`: Gửi mật khẩu.
   * `QUIT`: Kết thúc phiên FTP.
2. **Nhóm thư mục:**
   * `PWD`: Xem thư mục hiện tại (nhận `257 "/path"`).
   * `CWD <path>`: Chuyển thư mục (nhận `250 Directory changed`).
   * `CDUP`: Lùi về thư mục cha.
   * `MKD <folder>`: Tạo thư mục mới.
   * `RMD <folder>`: Xóa thư mục.
3. **Nhóm truyền dữ liệu (cần Data Connection):**
   * `LIST`: Lấy danh sách file/thư mục.
   * `RETR <filename>`: Download file từ Server về Client.
   * `STOR <filename>`: Upload file từ Client lên Server.
4. **Nhóm quản lý file:**
   * `DELE <filename>`: Xóa file.
   * `RNFR <old_name>`: Chọn file cần đổi tên (nhận `350 Ready for RNTO`).
   * `RNTO <new_name>`: Đặt tên mới (nhận `250 Rename successful`).
5. **Nhóm thiết lập truyền dữ liệu:**
   * `TYPE I`: Binary Mode (chuyển file nhị phân nguyên trạng).
   * `TYPE A`: ASCII Mode (chuyển file văn bản).
   * `PASV`: Passive Mode (Client chủ động kết nối).
   * `PORT`: Active Mode (Server chủ động kết nối).

---

## SLIDE 12: FTP REPLY CODE (JAVA)
Sau khi nhận FTP Command, Server trả về FTP Reply. Mỗi phản hồi bắt đầu bằng **mã 3 chữ số** giúp Client biết trạng thái xử lý.

1. **Cấu trúc FTP Reply:**
   $$\text{xyz} \quad \text{Message} \backslash\text{r}\backslash\text{n}$$
   * **xyz:** Mã 3 chữ số.
   * **Message:** Nội dung mô tả.
   * **\r\n:** Kết thúc dòng (CRLF).
2. **Ý nghĩa chữ số đầu tiên:**
   * `1xx`: Đã nhận lệnh, đang xử lý $\rightarrow$ Chờ phản hồi tiếp theo.
   * `2xx`: Thực hiện thành công $\rightarrow$ Tiếp tục.
   * `3xx`: Cần thêm thông tin $\rightarrow$ Gửi thêm lệnh/dữ liệu.
   * `4xx`: Lỗi tạm thời $\rightarrow$ Có thể thử lại.
   * `5xx`: Lỗi vĩnh viễn $\rightarrow$ Sửa lệnh hoặc điều kiện.
3. **Một số mã Reply thường gặp:**
   * `200` Command okay
   * `220` Service ready
   * `221` Goodbye
   * `226` Transfer complete
   * `230` Login successful
   * `250` Requested file action okay
   * `331` Password required
   * `421` Service not available
   * `425` Can't open data connection
   * `500` Syntax error
   * `530` Not logged in
   * `550` File unavailable / Permission denied
4. **Ví dụ kiểm tra Reply Code trong Java:**
   ```java
   BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
   String reply = reader.readLine();
   int code = Integer.parseInt(reply.substring(0, 3));
   if (code >= 200 && code < 300) {
       System.out.println("Thành công: " + reply);
   } else if (code >= 300 && code < 400) {
       System.out.println("Cần thêm thông tin: " + reply);
   } else if (code >= 400 && code < 500) {
       System.out.println("Lỗi tạm thời: " + reply);
   } else {
       System.out.println("Lỗi vĩnh viễn: " + reply);
   }
   ```

---

## SLIDE 13: VÍ DỤ PHIÊN GIAO TIẾP FTP
Tóm tắt các bước trao đổi thực tế qua Control Connection và Data Connection:
1. **Kết nối và đăng nhập:**
   * Client $\xrightarrow{\text{Connect :21}}$ Server đáp `220 Service ready`.
   * Client $\xrightarrow{\text{USER student}}$ Server đáp `331 Password required`.
   * Client $\xrightarrow{\text{PASS 123456}}$ Server đáp `230 Login successful`.
2. **Xem thư mục hiện tại:**
   * Client $\xrightarrow{\text{PWD}}$ Server đáp `257 "/home/student"`.
3. **Chuyển sang Binary Mode:**
   * Client $\xrightarrow{\text{TYPE I}}$ Server đáp `200 Type set to I`.
4. **Yêu cầu Passive Mode:**
   * Client $\xrightarrow{\text{PASV}}$ Server đáp `227 Entering Passive Mode (192,168,1,10,195,80)`.
   * Client tính Port: $195 \times 256 + 80 = 50000$.
5. **Lấy danh sách file (LIST):**
   * Client tạo Data Connection tới Port 50000.
   * Client $\xrightarrow{\text{LIST}}$ Server đáp `150 Opening data connection`.
   * Server truyền dữ liệu danh sách file qua Data Connection $\rightarrow$ Đóng Data Connection.
   * Server đáp `226 Transfer complete`.
6. **Download file (RETR):**
   * Client phát lệnh PASV mới $\rightarrow$ Nhận Port mới $\rightarrow$ Mở Data Connection.
   * Client $\xrightarrow{\text{RETR report.pdf}}$ Server đáp `150`.
   * Server truyền dữ liệu `report.pdf` qua Data Connection $\rightarrow$ Đóng Data Connection.
   * Server đáp `226 Transfer complete`.
7. **Kết thúc phiên:**
   * Client $\xrightarrow{\text{QUIT}}$ Server đáp `221 Goodbye` $\rightarrow$ Đóng Control Connection.

---

## SLIDE 14: THIẾT KẾ FTP CLIENT BẰNG JAVA
Sử dụng thư viện Socket chuẩn hoặc `Apache Commons Net` (`org.apache.commons.net.ftp.FTPClient`).

1. **Các lớp Java chính:**
   * `FTPClient`: Kết nối đến FTP Server qua TCP.
   * `InputStream`: Truyền và nhận dữ liệu qua TCP.
   * `BufferedReader`: Đọc FTP Reply (text) từ Server.
   * `OutputStream`: Gửi FTP Command (text) đến Server.
2. **Ví dụ khởi tạo và gửi nhận:**
   ```java
   BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
   BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8));

   public String sendCommandAndReadReply(BufferedWriter writer, BufferedReader reader, String command) throws IOException {
       writer.write(command + "\r\n");
       writer.flush();
       return reader.readLine();
   }
   ```

---

## SLIDE 15: KẾT NỐI FTP SERVER BẰNG JAVA
1. **Khai báo và kết nối:**
   ```java
   import org.apache.commons.net.ftp.FTPClient;

   FTPClient client = new FTPClient();
   client.connect("192.168.1.100", 21);
   ```
2. **Lấy InputStream & đọc Reply 220 đầu tiên:**
   ```java
   InputStream controlStream = client.getInputStream();
   BufferedReader reader = new BufferedReader(new InputStreamReader(controlStream, StandardCharsets.UTF_8));
   String reply = reader.readLine(); // "220 Service ready"
   ```
3. **Kiểm tra kết nối và xử lý lỗi:**
   * Các lỗi thường gặp: `Connection refused` (Server chưa chạy), `Timeout` (Server không phản hồi), `Unknown host` (Sai IP), `Network unreachable` (Lỗi mạng/firewall).

---

## SLIDE 16: ĐỌC PHẢN HỒI TỪ SERVER (JAVA)
1. **Đọc một dòng reply:** `String reply = reader.readLine();`
2. **Lấy Reply Code (3 chữ số đầu tiên):** `int code = Integer.parseInt(reply.substring(0, 3));`
3. **Xử lý theo Switch-Case:**
   ```java
   switch (code) {
       case 220: System.out.println("Server sẵn sàng"); break;
       case 331: System.out.println("Cần nhập Password"); break;
       case 230: System.out.println("Đăng nhập thành công"); break;
       case 550: System.out.println("File không tồn tại"); break;
       default: System.out.println("Code: " + code); break;
   }
   ```

---

## SLIDE 17: GỬI LỆNH USER VÀ PASS (JAVA)
1. **Quy trình đăng nhập:**
   * Nhận `220 Service Ready`.
   * Gửi `USER student\r\n` $\rightarrow$ Nhận `331 Password required`.
   * Gửi `PASS 123456\r\n` $\rightarrow$ Nhận `230 Login successful` (hoặc `530 Login incorrect`).
2. **Hàm Login tham khảo:**
   ```java
   public boolean login(BufferedWriter writer, BufferedReader reader, String username, String password) throws IOException {
       writer.write("USER " + username + "\r\n");
       writer.flush();
       String reply1 = reader.readLine();
       if (!reply1.startsWith("331")) return false;

       writer.write("PASS " + password + "\r\n");
       writer.flush();
       String reply2 = reader.readLine();
       return reply2.startsWith("230");
   }
   ```

---

## SLIDE 18: CÁC LỆNH ĐIỀU HƯỚNG: PWD, CWD, CDUP (JAVA)
1. **PWD (Print Working Directory):** Xem thư mục hiện tại. Server đáp `257 "/home/student"`.
2. **CWD (Change Working Directory):** Chuyển thư mục. Server đáp `250 Directory changed` (hoặc `550 Directory unavailable` nếu lỗi).
3. **CDUP (Change to Parent Directory):** Về thư mục cha. Server đáp `200 Command okay` hoặc `250`.
* **Ghi nhớ:** Các lệnh `PWD`, `CWD`, `CDUP` chỉ gửi Command/Reply trên Control Connection (TCP 21), không sử dụng Data Connection.

---

## SLIDE 19: THIẾT LẬP BINARY MODE – TYPE I (JAVA)
1. **Lệnh TYPE I:** Đặt chế độ truyền file nhị phân (Binary Mode). Server đáp `200 Type set to I`.
2. **Vì sao cần Binary Mode?**
   * Truyền nguyên trạng từng byte của file.
   * Không bị biến đổi ký tự xuống dòng (`\r\n` $\leftrightarrow$ `\n`) gây hư hỏng file.
   * Áp dụng bắt buộc cho: JPG, PNG, PDF, MP4, MP3, ZIP, EXE, Database,...
3. **Vị trí thiết lập:** Cần gửi lệnh `TYPE I` trước khi thực hiện các lệnh `RETR` (Download) hoặc `STOR` (Upload).

---

## SLIDE 20: PASSIVE MODE – PASV (JAVA)
1. **Lệnh PASV:** Gửi `PASV\r\n`. Server đáp `227 Entering Passive Mode (192,168,1,10,195,80)`.
2. **Tách chuỗi phân tích IP & Port:**
   ```java
   String reply = "227 Entering Passive Mode (192,168,1,10,195,80)";
   int start = reply.indexOf('(');
   int end = reply.indexOf(')');
   String data = reply.substring(start + 1, end);
   String[] parts = data.split(",");

   String ip = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
   int p1 = Integer.parseInt(parts[4]);
   int p2 = Integer.parseInt(parts[5]);
   int port = p1 * 256 + p2; // 195 * 256 + 80 = 50000
   ```
3. **Tạo Data Socket:** Client chủ động tạo `Socket dataSocket = new Socket(ip, port);`.

---

## SLIDE 21: PHÂN TÍCH PHẢN HỒI PASV (JAVA)
* **Công thức tính Port (16-bit):**
  $$\text{Port} = p_1 \times 256 + p_2$$
  * $p_1$ là High Byte (byte cao, dịch trái 8 bit: $p_1 \times 2^8 = p_1 \times 256$).
  * $p_2$ là Low Byte (byte thấp).
  * Ví dụ: $p_1 = 195, p_2 = 80 \implies \text{Port} = 195 \times 256 + 80 = 49920 + 80 = 50000$.

---

## SLIDE 22: LỆNH LIST (JAVA)
1. **Quy trình LIST trong Passive Mode:**
   1. Client gửi `PASV\r\n`.
   2. Server trả về `227 (h1,h2,h3,h4,p1,p2)`.
   3. Client tính IP/Port và kết nối `dataSocket`.
   4. Client gửi `LIST\r\n` qua Control Connection.
   5. Server trả về `150 Opening data connection`.
   6. Client đọc dữ liệu danh sách thư mục/file từ `dataSocket.getInputStream()`.
   7. Server đóng `dataSocket` khi truyền xong.
   8. Server trả về `226 Transfer complete` qua Control Connection.

---

## SLIDE 23: DOWNLOAD FILE – RETR (JAVA)
1. **Quy trình Download:**
   1. Gửi `TYPE I\r\n` $\rightarrow$ Nhận `200`.
   2. Gửi `PASV\r\n` $\rightarrow$ Nhận `227`, tính IP & Port, mở `dataSocket`.
   3. Gửi `RETR report.pdf\r\n` qua Control Connection.
   4. Nhận `150 Opening data connection` (nếu lỗi báo `550 File unavailable`).
   5. Đọc từng byte từ `dataSocket.getInputStream()` và ghi vào `FileOutputStream` ở Client.
   6. Khi hết dữ liệu (EOF), đóng `dataSocket` và ghi file.
   7. Nhận `226 Transfer complete` qua Control Connection.

---

## SLIDE 24: UPLOAD FILE – STOR (JAVA)
1. **Quy trình Upload:**
   1. Gửi `TYPE I\r\n` $\rightarrow$ Nhận `200`.
   2. Gửi `PASV\r\n` $\rightarrow$ Nhận `227`, tính IP & Port, mở `dataSocket`.
   3. Gửi `STOR data.txt\r\n` qua Control Connection.
   4. Nhận `150 Opening data connection` (nếu lỗi báo `550 Permission denied`).
   5. Đọc từ `FileInputStream` của file local và ghi byte vào `dataSocket.getOutputStream()`.
   6. Sau khi ghi xong hết file, đóng `dataSocket.getOutputStream()` (để báo hiệu EOF cho Server).
   7. Nhận `226 Transfer complete` qua Control Connection.

---

## SLIDE 25: CÁC THAO TÁC QUẢN LÝ FILE (JAVA)
Các lệnh quản lý file/thư mục chỉ gửi qua Control Connection (TCP 21), không cần Data Connection:
* **DELE <filename>:** Xóa file. Mặc định nhận `250 File deleted`. Lỗi: `550 File unavailable`.
* **MKD <folder>:** Tạo thư mục. Nhận `257 "folder" created`.
* **RMD <folder>:** Xóa thư mục. Nhận `250 Directory removed`. Lỗi: `550 Remove directory failed` (thư mục không rỗng hoặc không có quyền).
* **Đổi tên file (RNFR & RNTO):**
  1. Gửi `RNFR old.txt\r\n` $\rightarrow$ Server đáp `350 Ready for RNTO`.
  2. Gửi `RNTO new.txt\r\n` $\rightarrow$ Server đáp `250 Rename successful`.

---

## SLIDE 26: XÂY DỰNG FTP SERVER (JAVA)
FTP Server sử dụng `ServerSocket` (trong Java) hoặc `TcpListener` lắng nghe trên TCP Port 21 (Control Connection) và chấp nhận các Client kết nối.

1. **Khởi tạo và lắng nghe:**
   ```java
   import java.net.ServerSocket;
   import java.net.Socket;

   public class FtpServer {
       public static void main(String[] args) {
           try (ServerSocket serverSocket = new ServerSocket(21)) {
               System.out.println("FTP Server đang chạy tại Port 21...");
               while (true) {
                   Socket clientSocket = serverSocket.accept();
                   System.out.println("Client kết nối từ: " + clientSocket.getInetAddress());
                   new Thread(new ClientHandler(clientSocket)).start();
               }
           } catch (Exception e) {
               e.printStackTrace();
           }
       }
   }
   ```
2. **Xử lý từng Client trên Thread riêng:** Đảm bảo Server phục vụ nhiều Client đồng thời mà không bị treo.

---

## SLIDE 27: XỬ LÝ FTP COMMAND TRÊN SERVER (JAVA)
Sau khi nhận lệnh từ Client qua Control Connection, FTP Server phân tích Command, tách tham số và gọi hàm xử lý tương ứng.

1. **Đọc và tách lệnh:**
   ```java
   String line = reader.readLine();
   String[] parts = line.split(" ", 2);
   String command = parts[0].toUpperCase();
   String parameter = parts.length > 1 ? parts[1] : "";
   ```
2. **Switch-Case điều hướng:**
   ```java
   switch (command) {
       case "USER": handleUser(parameter); break;
       case "PASS": handlePass(parameter); break;
       case "PWD": handlePwd(); break;
       case "CWD": handleCwd(parameter); break;
       case "PASV": handlePasv(); break;
       case "LIST": handleList(); break;
       case "RETR": handleRetr(parameter); break;
       case "STOR": handleStor(parameter); break;
       case "QUIT": sendReply("221 Goodbye"); break;
       default: sendReply("502 Command not implemented"); break;
   }
   ```

---

## SLIDE 28: FTP SERVER NHIỀU CLIENT (JAVA)
Mô hình hỗ trợ nhiều Client đồng thời bằng Đa luồng (Multi-threading):
1. **Mỗi Client có một session riêng (`FtpSession`):**
   * Username / Password
   * `isLoggedIn` (trạng thái đăng nhập)
   * `currentDirectory` (thư mục làm việc hiện tại)
   * `dataSocket` / `passiveServerSocket` (kết nối dữ liệu riêng)
2. **Độc lập kết nối:** Thao tác của Client 1 không ảnh hưởng đến Client 2 và Client 3.
3. **Các dữ liệu tách theo Session:**
   * Control Connection: Riêng cho từng Client.
   * Data Connection: Riêng cho từng phiên truyền file.

---

## SLIDE 29: AN TOÀN VÀ XỬ LÝ LỖI FTP (JAVA)
1. **Timeout:** Thiết lập `socket.setSoTimeout(30000)` (30 giây) để đóng kết nối nếu Client không gửi lệnh, tránh treo tài nguyên.
2. **Chống Path Traversal (Bảo mật đường dẫn):**
   * Không cho phép Client dùng `../` hoặc đường dẫn tuyệt đối để thoát khỏi thư mục gốc `FtpRoot`.
   * **Xử lý Java:**
     ```java
     Path root = Paths.get("FtpRoot").toAbsolutePath();
     Path resolved = root.resolve(clientPath).normalize();
     if (!resolved.startsWith(root)) {
         sendReply("550 Access denied");
         return;
     }
     ```
3. **Kiểm tra quyền file:** Kiểm tra file có tồn tại, có quyền đọc/ghi trước khi thực hiện `RETR`, `STOR`, `DELE`.
4. **FTPS (FTP over TLS/SSL):** Truyền FTP gốc ở dạng rõ (Plaintext) dễ bị bắt gói tin (sniffer USER/PASS). Trong thực tế sản xuất cần dùng FTPS (mã hóa SSL/TLS).

---

## SLIDE 30: BÀI THỰC HÀNH TỔNG HỢP (JAVA)
Xây dựng **FTP Client + FTP Server** hoàn chỉnh bằng Java sử dụng TCP Socket.

### 1. Cấu Trúc Project Gợi Ý:
```text
FtpProject/
├── FtpClient/
│   └── src/
│       ├── FtpClient.java        # Xử lý kết nối Socket, gửi lệnh, nhận file
│       └── FtpConsole.java       # Giao diện Console/GUI người dùng
├── FtpServer/
│   └── src/
│       ├── FtpServer.java        # ServerSocket lắng nghe Port 21
│       ├── ClientHandler.java    # Luồng (Thread) xử lý riêng từng Client
│       └── FtpSession.java       # Lưu trạng thái session (isLoggedIn, currentDir...)
└── FtpRoot/                      # Thư mục gốc lưu trữ trên Server
    ├── documents/
    ├── images/
    └── upload/
```

### 2. Kịch Bản Kiểm Thứ Bắt Buộc:
| Kịch bản | Mô tả lệnh | Kết quả mong đợi (Reply) |
| :--- | :--- | :--- |
| **Login đúng** | `USER student` + `PASS 123456` | `230 Login successful` |
| **Login sai** | `PASS sai_pass` | `530 Login incorrect` |
| **PWD** | Yêu cầu thư mục hiện tại | `257 "/home"` |
| **LIST** | Liệt kê file/thư mục | `150` ... `226 Transfer complete` |
| **RETR** | Download file có tồn tại | `150` ... `226` |
| **STOR** | Upload file thành công | `150` ... `226` |
| **File không tồn tại**| `RETR file_x.txt` | `550 File unavailable` |
| **Sai quyền** | Thao tác không có quyền | `550 Permission denied` |
| **QUIT** | Đóng kết nối | `221 Goodbye` |
| **Nhiều Client** | 2 Client thao tác đồng thời | Hoạt động độc lập, không ảnh hưởng |

### 3. Tiêu Chí Đánh Giá Thang Điểm 10:
1. **Kết nối và Login (USER, PASS, QUIT):** 1.5 điểm
2. **Điều hướng thư mục (PWD, CWD, CDUP):** 1.0 điểm
3. **Liệt kê danh sách (LIST):** 1.0 điểm
4. **Download file (RETR):** 1.5 điểm
5. **Upload file (STOR):** 1.5 điểm
6. **Quản lý file/thư mục (DELE, MKD, RMD, RNFR, RNTO):** 1.0 điểm
7. **Hỗ trợ nhiều Client (Multi-client):** 1.0 điểm
8. **Xử lý lỗi, Timeout, bảo mật (FtpRoot, Path Traversal):** 1.0 điểm
9. **Log, Giao diện (nếu có):** 0.5 điểm
* **TỔNG CỘNG:** **10.0 điểm**
