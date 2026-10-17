# MiniBrowser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây dựng `MiniBrowser.java` — một trình duyệt web mini bằng Swing, tải HTML qua `HttpClient` trên luồng nền và hiển thị trong `JEditorPane`, đi kèm hệ thống Chat TCP + HTTP (Chương 11 & 12) làm bối cảnh.

**Architecture:** Một file duy nhất `MiniBrowser.java` chứa toàn bộ giao diện và tầng mạng. Giao diện (`JFrame`) chỉ gọi `load(url)`; `load` chuẩn hóa URL, chạy `SwingWorker` gọi `HttpClient.send()` ở luồng nền, rồi đẩy HTML vào `JEditorPane` trên Event Dispatch Thread. Lần bấm Go mới sẽ hủy lần tải cũ.

**Tech Stack:** Java 17 (tối thiểu Java 11), `javax.swing`, `java.net.http.HttpClient`, `SwingWorker`. Không dùng thư viện ngoài.

**Spec:** Yêu cầu trong tin nhắn gốc (mục Giao diện, Xử lý mạng, Yêu cầu kỹ thuật). Bối cảnh hệ thống: `tcp_chat_http_server_java.md`.

## Global Constraints

- Toàn bộ mã nguồn nằm trong một file: `MiniBrowser.java`.
- Giao diện có `JTextField` (địa chỉ), `JButton` "Go", `JEditorPane` (`setContentType("text/html")`, không cho chỉnh sửa) trong `JScrollPane`.
- Gọi mạng bằng `java.net.http.HttpClient` với phương thức `GET`; không làm đơ giao diện khi đang tải.
- URL mặc định: `http://localhost/P1.HTM`.
- Hệ thống chat (Chương 11–12) giữ nguyên như file gốc; plan này không thay đổi `TcpChatServer`, `TcpChatClient`, `HttpChatClient`.

## Review Focus

1. URL không có `http://` (ví dụ `localhost/P1.HTM`) → tự thêm scheme và tải bình thường.
2. Server không chạy hoặc sai cổng → hiển thị thông báo lỗi trong vùng nội dung, giao diện không treo.
3. Trang trả về HTTP 404 hoặc 500 → hiển thị mã lỗi, không hiển thị trang trống.
4. Trang tiếng Việt khai báo charset trong header (ví dụ `windows-1258`) → giải mã đúng theo charset đó.
5. Bấm Go liên tiếp nhiều lần → chỉ kết quả của lần bấm cuối cùng được hiển thị.

Các dòng trên được kiểm tra thủ công ở Task 4.

---

## Phần 1: Bối cảnh (không thuộc phạm vi code mới)

Hệ thống chat gồm `TcpChatServer` (cổng TCP 5000, broadcast qua `ConcurrentHashMap`), `HttpServer` (cổng 8080, các API `/api/clients`, `/api/message`, `/api/history`) và các client TCP/HTTP. Plan này chỉ thêm `MiniBrowser`, không sửa hệ thống chat.

## Task 1: Khung giao diện

**Files:**
- Create: `MiniBrowser.java`

**Interfaces:**
- Consumes: không có.
- Produces: `public class MiniBrowser extends JFrame`; `private void load(String input)` (điểm vào duy nhất để tải trang, dùng ở các task sau).

- [ ] **Step 1: Tạo lớp với thanh địa chỉ, nút Go, vùng hiển thị**

Khai báo `JTextField addressField` (mặc định `http://localhost/P1.HTM`), `JButton goButton` ("Go"), `JEditorPane editor` (`setContentType("text/html")`, `setEditable(false)`), đặt trong `JScrollPane`. Bố cục: thanh địa chỉ ở `NORTH`, vùng hiển thị ở `CENTER`.

- [ ] **Step 2: Gắn sự kiện Go và Enter**

Cả `goButton` và `addressField` gọi `load(addressField.getText())`.

- [ ] **Step 3: Compile và chạy để kiểm tra giao diện**

Run: `javac MiniBrowser.java`
Expected: không có lỗi; `MiniBrowser.class` được tạo.

- [ ] **Step 4: Commit**

```bash
git add MiniBrowser.java
git commit -m "feat: add MiniBrowser Swing window"
```

## Task 2: Tải trang bằng HttpClient trên luồng nền

**Files:**
- Modify: `MiniBrowser.java`

**Interfaces:**
- Consumes: `load(String)` từ Task 1.
- Produces: `private static String normalize(String input)` (trả về URL đầy đủ hoặc `null`); `private static String fetch(String url) throws IOException, InterruptedException` (trả về HTML đã giải mã charset, ném `IOException` nếu mã trạng thái ≥ 400); `private SwingWorker<String, Void> currentLoad`.

- [ ] **Step 1: Thêm `normalize`**

Thêm `http://` nếu thiếu scheme; trả về `null` nếu chuỗi rỗng hoặc `URI.create` thất bại hoặc không có host.

- [ ] **Step 2: Thêm `fetch` với `HttpClient`**

`HttpClient` dùng `connectTimeout(5s)`, `followRedirects(NORMAL)`; request `GET` với `timeout(15s)`; body đọc bằng `BodyHandlers.ofByteArray()`. Nếu `statusCode() >= 400`, ném `IOException("HTTP " + code)`. Charset lấy từ `Content-Type` (`charset=...`), mặc định UTF-8.

- [ ] **Step 3: Thêm `SwingWorker` trong `load`**

`doInBackground` gọi `fetch(url)`. `done()` mở khóa nút Go, trả con trỏ về bình thường, gọi `get()` rồi `editor.setText(html)`. Trước khi tải mới, hủy `currentLoad` cũ bằng `cancel(true)`.

- [ ] **Step 4: Verification**

Run: `javac MiniBrowser.java && java MiniBrowser`
Expected: cửa sổ mở, giao diện không đơ trong khi tải (kéo cửa sổ được ngay khi bấm Go).

- [ ] **Step 5: Commit**

```bash
git add MiniBrowser.java
git commit -m "feat: load pages via HttpClient on background SwingWorker"
```

## Task 3: Hiển thị lỗi, liên kết và base URL

**Files:**
- Modify: `MiniBrowser.java`

**Interfaces:**
- Consumes: `load(String)`, `fetch(String)`, `normalize(String)` từ Task 2.
- Produces: `private void showError(String message)`; `private static String escapeHtml(String s)`; hyperlink listener gọi `load(e.getURL().toString())`.

- [ ] **Step 1: Thêm `showError`**

Hiển thị `<h3>Không thể mở trang</h3>` và nội dung lỗi đã escape bằng `escapeHtml` trong `editor`.

- [ ] **Step 2: Xử lý lỗi trong `done()`**

Bắt `ExecutionException` (kèm `getCause().getMessage()`) và `IOException` từ `URI.toURL()`; gọi `showError`. Bắt `InterruptedException` và khôi phục cờ interrupt.

- [ ] **Step 3: Đặt base URL cho trang**

Sau `setText(html)`, gọi `((HTMLDocument) editor.getDocument()).setBase(URI.create(url).toURL())` để link và ảnh tương đối được phân giải đúng.

- [ ] **Step 4: Thêm hyperlink listener**

Khi `HyperlinkEvent.EventType.ACTIVATED` và `getURL() != null`, gọi `load(e.getURL().toString())`.

- [ ] **Step 5: Commit**

```bash
git add MiniBrowser.java
git commit -m "feat: show errors, follow links, set base URL"
```

## Task 4: Kiểm thử thủ công và hướng dẫn chạy

**Files:**
- Create: `web/P1.HTM`, `web/P2.HTM` (trang thử nghiệm, không thuộc mã nguồn chính)

**Interfaces:**
- Consumes: toàn bộ `MiniBrowser` từ Task 1–3.
- Produces: không có API mới.

- [ ] **Step 1: Tạo trang thử**

`web/P1.HTM` có tiêu đề tiếng Việt, một link tương đối `<a href="P2.HTM">Sang trang 2</a>`. `web/P2.HTM` có nội dung ngắn và link quay lại.

- [ ] **Step 2: Phục vụ thư mục `web` ở cổng 80**

Run (Windows với XAMPP: đặt file vào `htdocs`) hoặc `sudo jwebserver -p 80 -d $(pwd)/web` (Linux/macOS).
Expected: `curl -I http://localhost/P1.HTM` trả về `HTTP/1.1 200 OK`.

- [ ] **Step 3: Kiểm tra Review Focus**

Thực hiện lần lượt, quan sát kết quả:
- Nhập `localhost/P1.HTM` → trang hiện ra (dòng 1).
- Tắt web server rồi bấm Go → thông báo "Không tải được trang" (dòng 2).
- Nhập `localhost/khongco.HTM` → thông báo `HTTP 404` (dòng 3).
- Trang có `<meta charset="windows-1258">` hoặc header tương ứng → tiếng Việt hiển thị đúng (dòng 4).
- Bấm Go nhiều lần liên tiếp → trang cuối cùng được hiển thị (dòng 5).
- Bấm link "Sang trang 2" → trang 2 tải và cập nhật thanh địa chỉ (Task 3).

- [ ] **Step 4: Hướng dẫn chạy**

```bash
javac MiniBrowser.java
java MiniBrowser
```

Yêu cầu: JDK 11 trở lên. Mã nguồn không hỗ trợ JavaScript và CSS phức tạp, vì `JEditorPane` chỉ render HTML 3.2/4 cơ bản.

- [ ] **Step 5: Commit**

```bash
git add web/ MiniBrowser.java
git commit -m "test: manual verification pages and run instructions"
```

---

## Self-Review

1. **Spec coverage:** Thanh địa chỉ, nút Go, `JEditorPane` không chỉnh sửa (Task 1); `HttpClient` GET và `setText` (Task 2); luồng nền bằng `SwingWorker` (Task 2); một file `MiniBrowser.java` và hướng dẫn chạy (Task 4). Đầy đủ.
2. **Step scan:** Mỗi bước có một hành động kiểm tra được.
3. **Type consistency:** `load(String)`, `normalize(String)`, `fetch(String)`, `showError(String)`, `escapeHtml(String)` dùng nhất quán giữa các task.
4. **Review Focus:** Mỗi dòng có bước kiểm tra tương ứng ở Task 4.
