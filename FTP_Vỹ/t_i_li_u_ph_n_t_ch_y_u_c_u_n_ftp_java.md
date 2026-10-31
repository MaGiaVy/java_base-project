# TÀI LIỆU PHÂN TÍCH YÊU CẦU: LẬP TRÌNH FTP CLIENT - SERVER VỚI JAVA

* **Môn học:** Lập trình mạng (Network Programming) - Trường Đại học Sài Gòn (SGU)
* **Giảng viên:** Thầy Trần Minh Nhật
* **Mục tiêu cốt lõi:** Tự xây dựng hoàn chỉnh cả **FTP Client** và **FTP Server** bằng ngôn ngữ **Java** sử dụng **TCP Socket** tuân thủ đúng chuẩn giao thức FTP quốc tế (RFC 959), đồng thời thực hiện kiểm thử chéo (Cross-testing) với các phần mềm thực tế là **FileZilla** và **Windows IIS FTP Server**.

---

## PHẦN 1: TÓM TẮT LÝ THUYẾT & KỸ THUẬT CỐT LÕI (TỪ SLIDE BÀI GIẢNG)

Hệ thống truyền tập tin FTP không hoạt động trên một kết nối duy nhất mà bắt buộc phải duy trì **2 kết nối TCP song song**:

### 1. Cơ chế Hai kết nối (Dual TCP Connections)
* **Control Connection (Kết nối điều khiển - TCP Port 21):**
  * Thiết lập ngay khi Client kết nối tới Server và duy trì trong suốt phiên làm việc cho đến khi gửi lệnh `QUIT`.
  * Chỉ dùng để gửi **FTP Command** (từ Client) và nhận **FTP Reply** (từ Server) dưới dạng văn bản (ASCII/UTF-8).
  * **Lưu ý sống còn:** Mọi lệnh gửi đi bắt buộc phải kết thúc bằng ký tự xuống dòng chuẩn mạng là `\r\n` (CRLF).
* **Data Connection (Kết nối dữ liệu - Cổng động):**
  * Chỉ được tạo ra khi thực hiện các lệnh có truyền dữ liệu thực tế: `LIST` (xem danh sách), `RETR` (tải file xuống), `STOR` (tải file lên).
  * Mỗi lần truyền dữ liệu sẽ mở một Data Connection mới; ngay sau khi truyền xong (báo hiệu EOF), kết nối này phải được đóng lại để Server trả về mã hoàn tất `226`.

### 2. Chế độ Passive Mode (`PASV`) & Công thức tính Port
Để tương thích tốt với NAT/Firewall, hệ thống ưu tiên sử dụng **Passive Mode**:
1. Client gửi lệnh `PASV\r\n` qua Control Connection (Port 21).
2. Server mở một `ServerSocket` ở một cổng ngẫu nhiên và trả về phản hồi có dạng:
   `227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)`
   *(Ví dụ: `227 Entering Passive Mode (192,168,1,10,195,80)`)*
3. Client phải phân tích (parse) 6 con số trong ngoặc đơn để lấy địa chỉ IP và tính toán cổng dữ liệu (Data Port):
   * **Địa chỉ IP:** $h_1.h_2.h_3.h_4 \rightarrow 192.168.1.10$
   * **Công thức tính Port (16-bit):**
     $$\text{Port} = p_1 \times 256 + p_2$$
     *(Ví dụ: $\text{Port} = 195 \times 256 + 80 = 50000$)*
4. Client chủ động tạo một `Socket` thứ hai kết nối tới `IP:Port` vừa tính được, sau đó mới gửi lệnh `LIST`, `RETR`, hoặc `STOR` trên Control Connection.

### 3. Chế độ truyền Nhị phân (`TYPE I` - Binary Mode)
* Trước khi Upload (`STOR`) hoặc Download (`RETR`) bất kỳ file nào (đặc biệt là PDF, hình ảnh, file nén, bộ cài đặt...), Client cần gửi lệnh `TYPE I\r\n` (Server trả về `200 Type set to I`).
* Chế độ này truyền nguyên trạng từng byte (`FileInputStream` / `FileOutputStream`), tránh lỗi hỏng file do hệ điều hành tự động biến đổi ký tự xuống dòng như ở chế độ văn bản (`TYPE A`).

### 4. Tập lệnh FTP và Mã phản hồi (Reply Codes) bắt buộc cài đặt
* **Nhóm xác thực & Phiên:**
  * `USER <username>` $\rightarrow$ Nhận `331 Password required`
  * `PASS <password>` $\rightarrow$ Nhận `230 Login successful` (hoặc `530 Login incorrect`)
  * `QUIT` $\rightarrow$ Nhận `221 Goodbye`
* **Nhóm điều hướng thư mục (Chỉ dùng Control Connection):**
  * `PWD` (Xem thư mục hiện tại) $\rightarrow$ Nhận `257 "/path"`
  * `CWD <path>` (Chuyển thư mục) $\rightarrow$ Nhận `250 Directory changed`
  * `CDUP` (Về thư mục cha) $\rightarrow$ Nhận `200` hoặc `250`
* **Nhóm truyền dữ liệu (Cần cả Control Connection + Data Connection):**
  * `PASV` (Yêu cầu mở cổng thụ động) $\rightarrow$ Nhận `227 (h1,h2,h3,h4,p1,p2)`
  * `LIST` (Liệt kê danh sách file/thư mục) $\rightarrow$ Nhận `150` $\rightarrow$ Truyền danh sách qua Data Socket $\rightarrow$ Đóng Data Socket $\rightarrow$ Nhận `226 Transfer complete`
  * `RETR <filename>` (Download file từ Server về Client) $\rightarrow$ Nhận `150` $\rightarrow$ Đọc byte từ Data Socket $\rightarrow$ Nhận `226`
  * `STOR <filename>` (Upload file từ Client lên Server) $\rightarrow$ Nhận `150` $\rightarrow$ Ghi byte vào Data Socket $\rightarrow$ Đóng Data Socket $\rightarrow$ Nhận `226`
* **Nhóm quản lý File/Thư mục (Chỉ dùng Control Connection):**
  * `DELE <filename>` (Xóa file) $\rightarrow$ Nhận `250`
  * `MKD <folder>` (Tạo thư mục) $\rightarrow$ Nhận `257`
  * `RMD <folder>` (Xóa thư mục) $\rightarrow$ Nhận `250`
  * `RNFR <old_name>` đi kèm `RNTO <new_name>` (Đổi tên file/thư mục) $\rightarrow$ Nhận `350` sau `RNFR`, và `250` sau `RNTO`.

> **Lưu ý quan trọng về ngôn ngữ lập trình trong Slide:**
> Trong một số slide (như Slide 17, 21-28), code minh họa có lẫn cú pháp của C# (`TcpListener`, `TcpClient`, `StreamReader`, `StreamWriter`, `async/await`). Khi làm bài tập bằng **Java**, sinh viên cần chuyển đổi hoàn toàn sang thư viện chuẩn của Java (`java.net.ServerSocket`, `java.net.Socket`, `BufferedReader`, `PrintWriter`/`BufferedWriter`, `InputStream`/`OutputStream` và xử lý đa luồng bằng `Thread`/`Runnable`).

---

## PHẦN 2: GIẢI MÃ SƠ ĐỒ TRÊN BẢNG & Ý TƯỞNG YÊU CẦU CỦA THẦY

### 1. Sơ đồ mô hình kiểm thử chéo (Cross-Testing Matrix)
Trên bảng, thầy chia hệ thống thành 2 phía (Client bên trái, Server bên phải) với sự tham gia của 2 phần mềm chuẩn thực tế và 2 chương trình tự code, nối với nhau theo hình chữ X kèm yêu cầu **"down / up"** (Download và Upload):

```text
[ PHÍA CLIENT ]                                  [ PHÍA SERVER ]

1. FileZilla (FTP Client chuẩn)   <=========>    1. Windows IIS (FTP Server chuẩn)
               \                                 /
                \     (Thực hiện Down / Up)     /
                 \                             /
                  \                           /
                   \                         /
2. FTP Client (Tự code bằng Java) <=========>    2. FTP Server (Tự code bằng Java)
```

### 2. Tại sao thầy lại yêu cầu mô hình chữ X này?
Nếu sinh viên chỉ tự viết `Java Client` nói chuyện với `Java Server` của chính mình, sinh viên rất dễ "tự chế" ra giao thức riêng (ví dụ: gửi chuỗi tự đặt `"GET_FILE abc.txt"` và truyền thẳng qua 1 socket duy nhất) thay vì tuân thủ chuẩn FTP RFC 959.

Việc ép hệ thống phải kết nối chéo với **FileZilla** và **Windows IIS** là bài kiểm tra khách quan nhất để chứng minh:
1. **Java FTP Client của bạn viết đúng chuẩn:** Có khả năng giao tiếp, đăng nhập, bật Passive Mode và Upload/Download file mượt mà với một Server thương mại thực tế (**IIS FTP Server**).
2. **Java FTP Server của bạn viết đúng chuẩn:** Có khả năng lắng nghe trên Port 21, phản hồi đúng mã chuẩn 3 chữ số, mở đúng Data Port khi nhận lệnh `PASV` để một phần mềm chuyên dụng như **FileZilla** có thể đăng nhập, hiển thị cây thư mục và kéo/thả file (Upload/Download) mà không báo lỗi.

### 3. Chi tiết 4 Kịch bản Kiểm thử bắt buộc phải vượt qua

| Kịch bản | Cặp kết nối | Nội dung thực hiện & Tiêu chí nghiệm thu |
| :--- | :--- | :--- |
| **Kịch bản 1: Chuẩn đối chiếu (Baseline)** | **FileZilla Client** $\leftrightarrow$ **IIS FTP Server** | - Bật và cấu hình dịch vụ FTP Server trên Windows IIS.<br>- Dùng phần mềm FileZilla kết nối vào IIS để Upload/Download file mẫu.<br>- Quan sát cửa sổ Log của FileZilla để nắm rõ thứ tự các lệnh và mã phản hồi chuẩn thực tế. |
| **Kịch bản 2: Nghiệm thu Java Client** | **FTP Client (Java)** $\leftrightarrow$ **IIS FTP Server** | - Chạy chương trình **Java FTP Client** tự viết, kết nối vào địa chỉ IP của **IIS FTP Server** (Port 21).<br>- Thực hiện đăng nhập, xem danh sách (`LIST`), **Download (`RETR`)** và **Upload (`STOR`)** file thành công với IIS. |
| **Kịch bản 3: Nghiệm thu Java Server** | **FileZilla Client** $\leftrightarrow$ **FTP Server (Java)** | - Tắt IIS (hoặc đổi port để tránh đụng Port 21), chạy chương trình **Java FTP Server** tự viết.<br>- Mở phần mềm **FileZilla**, gõ IP/Port của Java Server để kết nối.<br>- FileZilla phải đăng nhập được, hiển thị đúng danh sách file trên giao diện, và kéo thả **Upload / Download** file thành công. |
| **Kịch bản 4: Tích hợp toàn hệ thống** | **FTP Client (Java)** $\leftrightarrow$ **FTP Server (Java)** | - Cho **Java FTP Client** kết nối trực tiếp vào **Java FTP Server**.<br>- Kiểm thử toàn bộ các tính năng: Đa luồng nhiều Client cùng lúc, Login, `PWD`, `CWD`, `CDUP`, `LIST`, **Download (`RETR`)**, **Upload (`STOR`)**, xóa/đổi tên/tạo thư mục và bảo mật đường dẫn. |

---

## PHẦN 3: CẤU TRÚC PROJECT, YÊU CẦU AN TOÀN & THANG ĐIỂM (SLIDE 30)

### 1. Cấu trúc thư mục Project gợi ý
```text
FtpProject/
├── FtpClient/
│   └── src/
│       ├── FtpClient.java        # Lớp xử lý kết nối Control & Data Socket, gửi lệnh, nhận file
│       └── FtpConsole.java       # Giao diện Console hoặc GUI cho người dùng thao tác
├── FtpServer/
│   └── src/
│       ├── FtpServer.java        # ServerSocket lắng nghe tại Port 21, chấp nhận nhiều Client
│       ├── ClientHandler.java    # Luồng (Runnable/Thread) xử lý riêng từng Client kết nối tới
│       └── FtpSession.java       # Lưu trạng thái của từng Client (isLoggedIn, currentDir, dataSocket...)
└── FtpRoot/                      # Thư mục gốc trên Server (Client không được phép truy cập ra ngoài)
    ├── documents/
    ├── images/
    └── upload/
```

### 2. Yêu cầu An toàn & Xử lý lỗi trên Server
* **Giới hạn FTP Root & Chống Path Traversal:** Tuyệt đối không cho phép Client dùng các đường dẫn như `../../Windows/System32` hoặc đường dẫn tuyệt đối để thoát ra ngoài thư mục `FtpRoot`. Cần dùng `Path.normalize()` và kiểm tra `path.startsWith(rootPath)` trước mọi thao tác đọc/ghi.
* **Đa luồng (Multi-threading):** Mỗi Client kết nối vào Server phải được chạy trên một `Thread` độc lập, có `FtpSession` riêng để không bị chồng chéo trạng thái đăng nhập, thư mục hiện tại hay cổng dữ liệu (`PASV`).
* **Quản lý tài nguyên & Timeout:** Thiết lập `setSoTimeout()` để tránh treo kết nối; luôn đóng `Data Socket` và `FileStream` trong khối `try-with-resources` hoặc `finally`.

### 3. Bảng tiêu chí đánh giá (Thang điểm 10 - Slide 30)
1. **Kết nối và Login (`USER`, `PASS`, `QUIT`):** 1.5 điểm
2. **Điều hướng thư mục (`PWD`, `CWD`, `CDUP`):** 1.0 điểm
3. **Liệt kê danh sách (`LIST` qua Data Connection):** 1.0 điểm
4. **Download file (`RETR`):** 1.5 điểm
5. **Upload file (`STOR`):** 1.5 điểm
6. **Quản lý file/thư mục (`DELE`, `MKD`, `RMD`, `RNFR`, `RNTO`):** 1.0 điểm
7. **Hỗ trợ nhiều Client đồng thời (Multi-client):** 1.0 điểm
8. **Xử lý lỗi, Timeout, bảo mật (FTP Root, chống Path Traversal, phân quyền):** 1.0 điểm
9. **Ghi Log hệ thống & Giao diện (nếu có):** 0.5 điểm
* **TỔNG CỘNG:** **10.0 điểm**