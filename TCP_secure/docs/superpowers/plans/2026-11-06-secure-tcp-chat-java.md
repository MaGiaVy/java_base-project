# Kế hoạch Triển khai Hệ thống Secure TCP Multi-Client Chat bằng Java (TLS 1.2 / 1.3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây dựng hệ thống Chat TCP bảo mật đa người dùng (Multi-Client) bằng Java (JDK 17+) hoàn chỉnh theo tài liệu giảng dạy `Chuong16.1 - Java (1).pptx` (SGU), hỗ trợ mã hóa TLS 1.2/1.3, xác thực mật khẩu băm BCrypt, cơ chế chống xung đột đa luồng SendLock, giao diện kép (CLI + Swing GUI) và bộ kiểm thử tự động đạt 100% 6 test case bảo mật (TC01 - TC06).

**Architecture:** Kiến trúc Client-Server hướng dịch vụ với kênh truyền TLS (`SSLServerSocket` / `SSLSocket`). Server quản lý phiên tập trung qua `ConcurrentHashMap<Integer, ClientSession>` và phân định khung tin bằng ký tự `\n` (Message Framing). Mỗi kết nối client được gán một `ReentrantLock` độc lập (`sendLock`) để bảo vệ luồng ghi tránh xé lẻ gói tin khi broadcast. Xác thực tài khoản độc lập qua `UserStore` với mật khẩu băm BCrypt. Phía Client kiểm tra chặt chẽ chứng chỉ máy chủ qua `ServerCertificateValidator` (kiểm tra Hostname/SAN, hạn sử dụng, và Trust Chain).

**Tech Stack:**
- Ngôn ngữ: Java 17+ (LTS)
- Quản lý dự án: Apache Maven (Multi-module project: `model`, `server`, `client`)
- Thư viện JSON: Jackson Databind (`com.fasterxml.jackson.core:jackson-databind:2.17.0`)
- Bảo mật & Băm mật khẩu: jBCrypt (`org.mindrot:jbcrypt:0.4`)
- Giao diện người dùng: Java Swing (GUI) kết hợp Terminal Console (CLI)
- Kiểm thử tự động: JUnit Jupiter 5.10.2 + Automated Java Test Runner

**Spec:** Tài liệu slide `Chuong16.1 - Java (1).pptx` (35 slides bài giảng và thực hành Bảo mật mạng - Lập trình mạng SGU).

---

## Global Constraints

1. **Bảo mật Khóa riêng tư:** File Keystore `server.p12` chứa Private Key tuyệt đối chỉ đặt tại thư mục Server (`server/certificates/`), không bao giờ copy sang Client. Client chỉ chứa Public Certificate `server.crt`.
2. **Kênh truyền bắt buộc TLS:** Mọi kết nối trao đổi thông tin đăng nhập và nội dung chat phải đi qua TLS 1.2 hoặc TLS 1.3. Kết nối Plain TCP không mã hóa tới port 5000 phải bị từ chối ngay lập tức.
3. **Chính sách không ghi log dữ liệu nhạy cảm:** Mật khẩu thô (plaintext password) không bao giờ được ghi vào file log hoặc in ra console ở bất kỳ tầng nào.
4. **Kiểm soát trạng thái nghiêm ngặt:** Chỉ cho phép gửi tin nhắn chat khi phiên đạt trạng thái `AUTHENTICATED`. Chưa đăng nhập mà gửi tin sẽ bị Server từ chối/bỏ qua.
5. **Định dạng thông điệp:** Mỗi thông điệp là một chuỗi JSON hợp lệ kết thúc bằng ký tự xuống dòng `\n`. Kích thước tối đa mỗi dòng giới hạn ở mức 4 KB để chống tấn công DoS/tràn bộ nhớ.

---

## Review Focus

1. **Xung đột luồng ghi khi nhiều client chat cùng lúc:** Kiểm tra việc broadcast tin nhắn tới hàng chục client không gây đè nén, xé lẻ byte (interleaved bytes) nhờ `sendLock.lock()` và `finally { sendLock.unlock() }`.
2. **Tấn công giả mạo tên miền (Hostname Mismatch):** Client kết nối tới địa chỉ IP/Domain không nằm trong SAN (Subject Alternative Name) của `server.crt` phải kích hoạt `CertificateException` và hủy phiên tức thì.
3. **Client ngắt kết nối đột ngột (Hard Disconnect / Crash):** Khi một client bị kill tiến trình đột ngột, Server phải bắt được `IOException`/`SocketException`, gỡ client khỏi `ConcurrentHashMap`, giải phóng `sendLock`, và broadcast thông báo `user_left` cho các client còn lại mà không làm sập Server.
4. **Dò quét tài khoản (Username Enumeration):** Khi đăng nhập sai (dù tài khoản không tồn tại hay mật khẩu không khớp), Server trả về cùng một mã thông báo `LOGIN_FAILED` chung, không báo chi tiết "Tài khoản không tồn tại" để bảo vệ quyền riêng tư.
5. **Tấn công Plain TCP Bypass:** Kết nối socket thông thường không bắt tay SSL gửi dữ liệu thô tới cổng 5000 phải kích hoạt ngoại lệ `SSLHandshakeException` và bị đóng kết nối ngay lập tức.

---

## Cấu trúc Thư mục Dự án

```
SecureTcpChat/
├── pom.xml                                  (Parent Maven POM cấu hình chung)
├── certificates/                            (Script tạo chứng chỉ & keystore)
│   ├── generate_certs.bat                   (Script Windows tự động tạo cert bằng keytool)
│   └── generate_certs.ps1                   (Script PowerShell tự động)
├── model/                                   (Mô-đun chứa đối tượng dùng chung)
│   ├── pom.xml
│   └── src/main/java/com/example/model/
│       ├── ChatMessage.java                 (Lớp ánh xạ thông điệp JSON)
│       └── MessageType.java                 (Enum các loại bản tin: LOGIN, MESSAGE, SYSTEM,...)
├── server/                                  (Mô-đun Server)
│   ├── pom.xml
│   ├── certificates/
│   │   ├── server.p12                       (Keystore PKCS#12 chứa Private Key + Cert)
│   │   └── server.crt                       (Public Certificate)
│   └── src/main/java/com/example/server/
│       ├── Program.java                     (Hàm main khởi chạy Server)
│       ├── ChatServer.java                  (SSLServerSocket, Accept Loop, Broadcast, Registry)
│       ├── ClientSession.java               (Đối tượng lưu trạng thái Client & ReentrantLock)
│       ├── ClientHandler.java               (Thread xử lý nhận và phân tích JSON stream)
│       └── UserStore.java                   (Quản lý xác thực BCrypt & lưu trữ users.json)
├── client/                                  (Mô-đun Client)
│   ├── pom.xml
│   ├── certificates/
│   │   └── server.crt                       (Public Certificate tin cậy)
│   └── src/main/java/com/example/client/
│       ├── Program.java                     (Hàm main khởi chạy Client: CLI hoặc GUI)
│       ├── ChatClient.java                  (Quản lý kết nối SSLSocket, gửi nhận tin nhắn)
│       ├── ServerCertificateValidator.java  (Kiểm tra SAN, Hostname, Hạn dùng, TrustChain)
│       ├── cli/
│       │   └── ConsoleChatClient.java       (Giao diện điều khiển dòng lệnh)
│       └── ui/
│           ├── LoginDialog.java             (Hộp thoại đăng nhập Swing)
│           └── ChatWindow.java              (Cửa sổ phòng chat Swing đa chức năng)
├── scripts/                                 (Scripts hỗ trợ kiểm thử & demo)
│   ├── run_server.bat                       (Khởi động Server)
│   ├── run_client_cli.bat                   (Khởi động 1 Client CLI)
│   ├── run_client_gui.bat                   (Khởi động 1 Client GUI)
│   ├── run_3_clients_demo.bat               (Tự động mở 1 Server + 3 Client: An, Bình, Chi)
│   └── run_security_tests.bat               (Chạy toàn bộ 6 bài kiểm thử TC01 -> TC06)
└── README.md                                (Tài liệu báo cáo kỹ thuật & hướng dẫn chi tiết)
```

---

## Chi tiết Kế hoạch Triển khai (Tasks)

### Task 1: Khởi tạo Cấu trúc Dự án Maven & Parent POM

**Files:**
- Create: `D:\baitap1\TCP_secure\pom.xml`
- Create: `D:\baitap1\TCP_secure\model\pom.xml`
- Create: `D:\baitap1\TCP_secure\server\pom.xml`
- Create: `D:\baitap1\TCP_secure\client\pom.xml`
- Create: `D:\baitap1\TCP_secure\.gitignore`

**Interfaces:**
- Consumes: JDK 17, Maven compiler plugin, dependency Jackson 2.17.0, jBCrypt 0.4, JUnit 5.10.2.
- Produces: Hệ thống build Maven đa mô-đun (`model`, `server`, `client`) có thể build qua lệnh `mvn clean install`.

- [x] **Step 1: Viết Parent `pom.xml` với `packaging=pom` và định nghĩa modules `model`, `server`, `client`**
  - Khai báo dependencyManagement: `com.fasterxml.jackson.core:jackson-databind:2.17.0`, `org.mindrot:jbcrypt:0.4`, `org.junit.jupiter:junit-jupiter:5.10.2`.
- [x] **Step 2: Viết `model/pom.xml`**
  - Chứa Jackson Databind và JUnit.
- [x] **Step 3: Viết `server/pom.xml`**
  - Phụ thuộc vào `model`, Jackson, jBCrypt. Cấu hình `exec-maven-plugin` trỏ vào `com.example.server.Program`.
- [x] **Step 4: Viết `client/pom.xml`**
  - Phụ thuộc vào `model`, Jackson. Cấu hình `exec-maven-plugin` trỏ vào `com.example.client.Program`.
- [x] **Step 5: Kiểm tra biên dịch**
  - Chạy lệnh: `mvn clean compile`
  - Đảm bảo: `BUILD SUCCESS` trên cả 3 modules.

---

### Task 2: Xây dựng Giao thức Thông điệp Dùng chung (`model`)

**Files:**
- Create: `D:\baitap1\TCP_secure\model\src\main\java\com\example\model\MessageType.java`
- Create: `D:\baitap1\TCP_secure\model\src\main\java\com\example\model\ChatMessage.java`
- Test: `D:\baitap1\TCP_secure\model\src\test\java\com\example\model\ChatMessageTest.java`

**Interfaces:**
- Consumes: Jackson `ObjectMapper`.
- Produces:
  - `MessageType`: enum gồm `LOGIN("login")`, `LOGIN_RESULT("login_result")`, `MESSAGE("message")`, `SYSTEM("system")`, `DISCONNECT("disconnect")`, `USER_LEFT("user_left")`, `PING("ping")`.
  - `ChatMessage`: POJO có đầy đủ các trường `type`, `username`, `password`, `content`, `success`, `clientId`.
  - `ChatMessage.fromJson(String json) -> ChatMessage`
  - `ChatMessage.toJson() -> String` (kèm ký tự `\n` ở cuối).

- [x] **Step 1: Viết kiểm thử đơn vị cho việc serialize & deserialize ChatMessage**
  - Kiểm tra serialize từ object sang chuỗi JSON và deserialize ngược lại.
  - Kiểm tra đảm bảo chuỗi serialized kết thúc bằng `\n`.
- [x] **Step 2: Chạy kiểm thử để xác nhận thất bại (TDD Red)**
  - Chạy: `mvn -pl model test` -> Thất bại do chưa có class.
- [x] **Step 3: Cài đặt `MessageType.java` và `ChatMessage.java`**
  - Định nghĩa chuẩn các getters, setters, static helpers `createLogin`, `createLoginResult`, `createChat`, `createSystem`.
- [x] **Step 4: Chạy kiểm thử để xác nhận thành công (TDD Green)**
  - Chạy: `mvn -pl model test` -> PASS 100%.

---

### Task 3: Script Tạo Chứng chỉ TLS & Keystore (`keytool`)

**Files:**
- Create: `D:\baitap1\TCP_secure\certificates\generate_certs.bat`
- Create: `D:\baitap1\TCP_secure\certificates\generate_certs.ps1`
- Test & Output: `D:\baitap1\TCP_secure\server\certificates\server.p12`, `D:\baitap1\TCP_secure\server\certificates\server.crt`, `D:\baitap1\TCP_secure\client\certificates\server.crt`

**Interfaces:**
- Consumes: Lệnh `keytool` từ JDK.
- Produces:
  - `server.p12`: PKCS12 Keystore, RSA 2048-bit, pass `LabOnly_ChangeMe`, CN=localhost, SAN=DNS:localhost,IP:127.0.0.1.
  - `server.crt`: X.509 Public Certificate xuất từ `server.p12`.
  - Tự động sao chép `server.crt` sang `client/certificates/server.crt` (nhưng KHÔNG chép `server.p12`).

- [x] **Step 1: Viết script `generate_certs.bat` và `generate_certs.ps1`**
  - Chạy `keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -keystore server.p12 -storetype PKCS12 -validity 365 -dname "CN=localhost, OU=Lab, O=SecureChat, L=Hanoi, C=VN" -ext "SAN=DNS:localhost,IP:127.0.0.1" -storepass LabOnly_ChangeMe -keypass LabOnly_ChangeMe`.
  - Chạy `keytool -exportcert -alias server -keystore server.p12 -storetype PKCS12 -file server.crt -rfc -storepass LabOnly_ChangeMe`.
  - Sao chép vào thư mục đích của `server` và `client`.
- [x] **Step 2: Thực thi script sinh chứng chỉ**
  - Kiểm tra sự tồn tại của `server.p12` và `server.crt`.
  - Dùng `keytool -list -keystore server/certificates/server.p12 -storepass LabOnly_ChangeMe` xác nhận chứng chỉ hợp lệ.

---

### Task 4: Quản lý Người dùng & Băm Mật khẩu BCrypt (`UserStore`)

**Files:**
- Create: `D:\baitap1\TCP_secure\server\src\main\java\com\example\server\UserStore.java`
- Create: `D:\baitap1\TCP_secure\server\src\main\resources\users.json`
- Test: `D:\baitap1\TCP_secure\server\src\test\java\com\example\server\UserStoreTest.java`

**Interfaces:**
- Consumes: `org.mindrot.jbcrypt.BCrypt`, file `users.json`.
- Produces:
  - `boolean UserStore.authenticate(String username, String rawPassword)`
  - `boolean UserStore.registerUser(String username, String rawPassword)`
  - Nạp sẵn 3 tài khoản mặc định theo slide:
    * `an` / `pass123`
    * `binh` / `pass456`
    * `chi` / `pass789`
  - Cơ chế băm: `BCrypt.hashpw(rawPassword, BCrypt.gensalt(12))` và kiểm tra bằng `BCrypt.checkpw`.

- [x] **Step 1: Viết kiểm thử `UserStoreTest`**
  - Test đăng nhập đúng với tài khoản `an` / `pass123` -> Trả về `true`.
  - Test đăng nhập sai mật khẩu `an` / `wrongpass` -> Trả về `false`.
  - Test đăng nhập với tài khoản không tồn tại `unknown` -> Trả về `false`.
  - Test đăng ký tài khoản mới và đăng nhập lại bằng mật khẩu mới -> Thành công.
- [x] **Step 2: Chạy kiểm thử để xác nhận thất bại (TDD Red)**
  - Chạy: `mvn -pl server test -Dtest=UserStoreTest` -> Fail.
- [x] **Step 3: Cài đặt `UserStore.java`**
  - Xử lý đọc/ghi file `users.json` thread-safe.
  - Đảm bảo phương thức `authenticate` không ném ngoại lệ làm dừng server khi gặp username lạ.
- [x] **Step 4: Chạy kiểm thử để xác nhận thành công (TDD Green)**
  - Chạy: `mvn -pl server test -Dtest=UserStoreTest` -> PASS.

---

### Task 5: Triển khai Lõi Server Bảo mật (`ChatServer`, `ClientSession`, `ClientHandler`)

**Files:**
- Create: `D:\baitap1\TCP_secure\server\src\main\java\com\example\server\ClientSession.java`
- Create: `D:\baitap1\TCP_secure\server\src\main\java\com\example\server\ClientHandler.java`
- Create: `D:\baitap1\TCP_secure\server\src\main\java\com\example\server\ChatServer.java`
- Create: `D:\baitap1\TCP_secure\server\src\main\java\com\example\server\Program.java`
- Test: `D:\baitap1\TCP_secure\server\src\test\java\com\example\server\ChatServerTest.java`

**Interfaces:**
- Consumes: `SSLServerSocket`, `SSLSocket`, `KeyStore`, `KeyManagerFactory`, `UserStore`, `ChatMessage`, `ConcurrentHashMap`, `ReentrantLock`.
- Produces:
  - `ClientSession`: giữ `id`, `SSLSocket`, `username`, `authenticated` (volatile), và `sendLock` (`ReentrantLock`). Có method `send(ChatMessage msg)` an toàn đa luồng.
  - `ChatServer.start(int port)`: nạp `server.p12`, tạo `SSLContext` (TLS 1.2 / TLS 1.3), mở `SSLServerSocket`, chấp nhận kết nối lặp (Accept Loop) và bàn giao cho Executor pool.
  - `ChatServer.broadcast(ChatMessage msg, Integer excludeClientId)`: gửi tin tới toàn bộ client authenticated với bảo vệ `sendLock`.
  - `ChatServer.removeClient(int clientId, String reason)`: dọn dẹp idempotent, đóng socket, xóa khỏi registry, broadcast `user_left`.
  - Giới hạn dòng đọc 4 KB (`MAX_LINE_LENGTH = 4096`).

- [x] **Step 1: Cài đặt `ClientSession.java` với `ReentrantLock sendLock`**
  - Phương thức `send(ChatMessage msg)`: lock trước khi ghi vào OutputStream, flush, unlock trong `finally`.
- [x] **Step 2: Cài đặt `ClientHandler.java`**
  - Đọc từ `BufferedReader.readLine()`. Kiểm tra kích thước dòng không vượt quá 4 KB.
  - Parse JSON qua Jackson `ChatMessage`.
  - Xử lý chuyển đổi trạng thái: `LOGIN` -> kiểm tra `UserStore` -> trả về `LOGIN_RESULT`. Nếu sai: đóng kết nối. Nếu đúng: gán `authenticated = true`, broadcast thông báo `SYSTEM: [username] đã tham gia`.
  - Chỉ cho phép xử lý message `MESSAGE` khi `authenticated == true`.
  - Xử lý `DISCONNECT` hoặc `IOException` -> gọi `ChatServer.removeClient()`.
- [x] **Step 3: Cài đặt `ChatServer.java` và `Program.java`**
  - Khởi tạo `SSLContext` hỗ trợ TLSv1.2, TLSv1.3.
  - Cổng mặc định: 5000.
  - Log an toàn: Định dạng `[PASS]`, `[BLOCK]`, `[INFO]`, không in mật khẩu.
- [x] **Step 4: Viết kiểm thử tích hợp Server bắt tay TLS và xác thực**
  - Chạy `mvn -pl server test` -> PASS.

---

### Task 6: Triển khai Lõi Client Bảo mật (`ChatClient`, `ServerCertificateValidator`)

**Files:**
- Create: `D:\baitap1\TCP_secure\client\src\main\java\com\example\client\ServerCertificateValidator.java`
- Create: `D:\baitap1\TCP_secure\client\src\main\java\com\example\client\ChatClient.java`
- Test: `D:\baitap1\TCP_secure\client\src\test\java\com\example\client\ChatClientIntegrationTest.java`

**Interfaces:**
- Consumes: `SSLSocketFactory`, `TrustManagerFactory`, `X509TrustManager`, `ChatMessage`.
- Produces:
  - `ServerCertificateValidator.validate(SSLSocket socket, String expectedHost)`: kiểm tra SAN (DNS, IP) hoặc CN khớp với `expectedHost`, kiểm tra hạn dùng `checkValidity()`.
  - `ChatClient.connect(String host, int port)`: nạp `server.crt` vào in-memory TrustStore, thiết lập kết nối SSL, gọi `startHandshake()`, thực hiện validation.
  - `ChatClient.login(String username, String password) -> boolean`: gửi `LOGIN`, đợi `LOGIN_RESULT`.
  - `ChatClient.sendMessage(String text)`: gửi tin nhắn chat.
  - `ChatClient.startReceiveLoop(Consumer<ChatMessage> onMessageReceived, Runnable onDisconnected)`: đọc ngầm không làm đơ UI.
  - `ChatClient.disconnect()`: gửi `DISCONNECT` và đóng tài nguyên an toàn.

- [x] **Step 1: Cài đặt `ServerCertificateValidator.java`**
  - Hỗ trợ duyệt `getSubjectAlternativeNames()` kiểm tra DNS name và IP Address.
  - Hỗ trợ fallback kiểm tra Common Name (CN).
  - Ném `CertificateException` nếu không khớp hoặc hết hạn.
- [x] **Step 2: Cài đặt `ChatClient.java`**
  - Tích hợp vòng lặp nhận tin nhắn chạy trên luồng phụ (`Thread` hoặc `CompletableFuture`).
  - Đảm bảo đóng socket an toàn khi disconnect.
- [x] **Step 3: Viết kiểm thử tích hợp Client kết nối Server, xác thực và chat**
  - Khởi động mock server TLS, client kết nối và trao đổi tin nhắn.
  - Chạy: `mvn -pl client test` -> PASS.

---

### Task 7: Xây dựng Giao diện Người dùng Kép (Console CLI & Java Swing GUI)

**Files:**
- Create: `D:\baitap1\TCP_secure\client\src\main\java\com\example\client\cli\ConsoleChatClient.java`
- Create: `D:\baitap1\TCP_secure\client\src\main\java\com\example\client\ui\LoginDialog.java`
- Create: `D:\baitap1\TCP_secure\client\src\main\java\com\example\client\ui\ChatWindow.java`
- Create: `D:\baitap1\TCP_secure\client\src\main\java\com\example\client\Program.java` (hỗ trợ tham số `--cli` hoặc mặc định mở GUI)

**Interfaces:**
- Consumes: `ChatClient`, Java Swing (`JFrame`, `JTextArea`, `JTextField`, `JButton`, `JList`).
- Produces:
  - `ConsoleChatClient`: Chạy trực tiếp trong command prompt/terminal, nhập host, port, username, password; gõ tin nhắn gửi ngay, hiển thị tin nhắn nhận được theo thời gian thực.
  - `LoginDialog`: Hộp thoại Swing chuyên nghiệp, nhập Server IP, Port, Username, Password (trường `JPasswordField` che dấu sao).
  - `ChatWindow`: Cửa sổ phòng chat Swing:
    * Khung hiển thị tin nhắn có cuộn tự động.
    * Danh sách thành viên online (được cập nhật qua message `SYSTEM` / `USER_LEFT`).
    * Ô nhập tin nhắn & nút [Gửi] (hỗ trợ ấn phím Enter).
    * Hiển thị trạng thái bảo mật: "🔒 Kênh truyền bảo mật: TLS 1.3 - 256-bit".
    * Đóng cửa sổ sẽ tự động ngắt kết nối an toàn.

- [x] **Step 1: Cài đặt `ConsoleChatClient.java`**
  - Chạy độc lập qua terminal, tương thích UTF-8 tiếng Việt.
- [x] **Step 2: Cài đặt `LoginDialog.java` và `ChatWindow.java`**
  - Đảm bảo `SwingUtilities.invokeLater()` khi cập nhật UI từ background receive thread (tránh deadlock/đơ giao diện theo đúng Slide 33 & 34).
- [x] **Step 3: Cài đặt `client/Program.java`**
  - Nếu có tham số `--cli` -> chạy Console; nếu không có tham số -> khởi chạy Swing GUI.
- [x] **Step 4: Kiểm tra khởi động UI**
  - Test chạy giao diện hiển thị đúng chuẩn.

---

### Task 8: Bộ Kiểm thử Tự động 6 Kịch bản Bảo mật (TC01 -> TC06) & Script Demo

**Files:**
- Create: `D:\baitap1\TCP_secure\server\src\test\java\com\example\server\SecurityTestRunner.java`
- Create: `D:\baitap1\TCP_secure\scripts\run_server.bat`
- Create: `D:\baitap1\TCP_secure\scripts\run_client_cli.bat`
- Create: `D:\baitap1\TCP_secure\scripts\run_client_gui.bat`
- Create: `D:\baitap1\TCP_secure\scripts\run_3_clients_demo.bat`
- Create: `D:\baitap1\TCP_secure\scripts\run_security_tests.bat`
- Create: `D:\baitap1\TCP_secure\README.md`

**Chi tiết 6 Kịch bản Kiểm thử Bảo mật (Đối chiếu chính xác Slide 35):**
1. **TC01 - Đăng nhập đúng:**
   - Hành động: Gửi `LOGIN` với `an:pass123`.
   - Kết quả mong đợi: Nhận `LOGIN_RESULT (success=true)` + gán `clientId`, gửi/nhận tin nhắn thành công $\rightarrow$ **`[PASS]`**.
2. **TC02 - Sai mật khẩu:**
   - Hành động: Gửi `LOGIN` với `an:wrongpass`.
   - Kết quả mong đợi: Nhận `LOGIN_RESULT (success=false)`, đóng kết nối, không tiết lộ tài khoản có tồn tại hay không $\rightarrow$ **`[PASS]`**.
3. **TC03 - Chứng chỉ sai tên miền (Hostname Mismatch):**
   - Hành động: Client kết nối tới Hostname giả mạo (vd: `evil.com` hoặc IP không có trong SAN của cert).
   - Kết quả mong đợi: `ServerCertificateValidator` phát hiện `Hostname mismatch`, ngắt kết nối TLS $\rightarrow$ **`[PASS]`**.
4. **TC04 - Chứng chỉ không tin cậy / Hết hạn:**
   - Hành động: Client kết nối tới Server dùng chứng chỉ tự ký lạ không nằm trong `server.crt` tin cậy của Client.
   - Kết quả mong đợi: Client từ chối chứng chỉ (`SSLHandshakeException`) $\rightarrow$ **`[PASS]`**.
5. **TC05 - Kết nối Plain TCP không qua TLS:**
   - Hành động: Mở `java.net.Socket` thường và gửi chuỗi byte văn bản thô tới cổng 5000.
   - Kết quả mong đợi: Server không hoàn tất TLS handshake, từ chối và ngắt kết nối an toàn $\rightarrow$ **`[PASS]`**.
6. **TC06 - Client ngắt kết nối đột ngột:**
   - Hành động: Client đăng nhập thành công rồi bị ngắt socket đột ngột không qua `DISCONNECT`.
   - Kết quả mong đợi: Server bắt được ngoại lệ, xóa client khỏi `ConcurrentHashMap`, giải phóng `sendLock`, các client khác vẫn hoạt động ổn định $\rightarrow$ **`[PASS]`**.

- [x] **Step 1: Cài đặt lớp `SecurityTestRunner.java`**
  - Chạy tuần tự từ TC01 đến TC06.
  - In bảng kết quả tổng kết trực quan định dạng Terminal:
    ```
    ========================================================================================
    | TC   | Kịch bản kiểm thử                  | Kết quả mong đợi           | Trạng thái  |
    ========================================================================================
    | TC01 | Đăng nhập đúng                     | LOGIN_SUCCESS, vào chat    | [PASS]      |
    | TC02 | Sai mật khẩu                       | LOGIN_FAILED, đóng phiên   | [PASS]      |
    | TC03 | Chứng chỉ sai tên miền             | Handshake fail, từ chối    | [PASS]      |
    | TC04 | Chứng chỉ không tin cậy            | Client từ chối kết nối     | [PASS]      |
    | TC05 | Kết nối Plain TCP không TLS        | Server đóng kết nối ngay   | [PASS]      |
    | TC06 | Client ngắt kết nối đột ngột       | Server dọn dẹp, các client | [PASS]      |
    |      |                                    | khác hoạt động ổn định     |             |
    ========================================================================================
    TỔNG KẾT: 6/6 TEST CASES ĐẠT [PASS] (100%)
    ```
- [x] **Step 2: Viết các file script `.bat` khởi chạy tiện lợi**
  - `run_3_clients_demo.bat`: Tự động khởi động Server ở một cửa sổ, sau đó mở 3 cửa sổ Client (An, Bình, Chi) tự động kết nối và đăng nhập.
  - `run_security_tests.bat`: Chạy kiểm thử tự động toàn bộ 6 test case chỉ với 1 click.
- [x] **Step 3: Viết tài liệu `README.md`**
  - Hướng dẫn cài đặt, kiến trúc, bảng test case, hướng dẫn chạy và chụp ảnh minh chứng bài thực hành.

---

## Hướng dẫn Bàn giao & Thực thi vào ngày mai

Khi bạn quay lại vào ngày mai, bạn chỉ cần nhắn:
> **"Thực hiện kế hoạch đi bạn"** hoặc **"Bắt đầu làm theo plan"**

Hệ thống sẽ tự động kích hoạt chế độ thực thi nhiệm vụ theo đúng trình tự từ Task 1 đến Task 8, biên dịch toàn bộ mã nguồn, sinh chứng chỉ, và chạy kiểm thử tự động xác nhận 100% 6 Test Case đều `[PASS]`.
