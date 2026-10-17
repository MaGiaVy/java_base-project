# Chương 12: Lập trình Mạng - HTTP và HTTP Server (Java)

**Giảng viên:** TRẦN MINH NHẬT - Trường Đại học Sài Gòn (Saigon University)

---

## Slide 1: Tổng quan Lập trình Mạng

- **Đơn vị:** Trường Đại học Sài Gòn (Saigon University)
- **Môn học:** Lập trình mạng (Network Programming)
- **Các nội dung cốt lõi:**
  - Mạng máy tính
  - Giao thức TCP/IP
  - Mô hình Client - Server (Client, Mobile Client, Server, Internet)
  - Socket Programming
  - Bảo mật mạng
- **Kiến trúc phân tầng giao thức:**
  - **Tầng Ứng dụng (Application):** HTTP, FTP, SMTP, DNS, ...
  - **Tầng Giao vận (Transport):** TCP, UDP
  - **Tầng Liên mạng (Internet):** IP, ICMP, ARP
  - **Tầng Liên kết (Link):** Ethernet, Wi-Fi, ...
- **Mục tiêu:** Trao đổi dữ liệu & Xây dựng ứng dụng mạng.

---

## Slide 2: Bài 12 - HTTP và HTTP Server

- **Từ khóa chính:** Request • Response • Method • Status Code • Header • Body
- **Mô hình tương tác:**
  - **HTTP Client:** Tạo và gửi HTTP Request (`METHOD` • `URL` • `HEADER` • `BODY`).
  - **HTTP Server:** Nhận Request $\rightarrow$ Xử lý $\rightarrow$ Trả HTTP Response (`STATUS CODE` • `HEADER` • `BODY`).
- **Mục tiêu chính:** Hiểu giao thức HTTP, xây dựng Server bằng Java/C#, xử lý Request từ Client, và trả Response đúng chuẩn.

---

## Slide 3: Mục tiêu bài học

1. **Phân tích HTTP Request:** Method, URL, Header, Body.
2. **Tạo HTTP Response:** Status Code, Header, Body.
3. **Xử lý GET và POST:** Routing theo URL và Method.
4. **Xây dựng HTTP Server:** Nhận Request $\rightarrow$ Xử lý $\rightarrow$ Trả Response.
- **Sau bài học:** Tự xây dựng Web Server và kiểm thử trên trình duyệt.

---

## Slide 4: Mô hình giao tiếp HTTP

### Luồng tương tác từng bước
1. **01. Nhập URL:** `http://localhost:8080/`
2. **02. Gửi Request:** `GET /index.html HTTP/1.1`
3. **03. Server xử lý:** Tìm tài nguyên • Chạy chương trình.
4. **04. Trả Response:** `HTTP/1.1 200 OK` (Hiển thị HTML trên trình duyệt).

### Nguyên lý hoạt động
- **Mô hình Request - Response:** Client khởi tạo Request $\rightarrow$ Server trả đúng một Response.
- HTTP truyền dữ liệu trên nền kết nối TCP.

---

## Slide 5: Cấu trúc HTTP Request

### HTTP Request mẫu
```http
POST /api/students HTTP/1.1
Host: localhost:8080
Content-Type: application/json
Content-Length: 22

{"name":"An","age":20}
```

### Các thành phần chính
1. **01. REQUEST LINE:** Method + URL + HTTP Version (VD: `POST /api/students HTTP/1.1`)
2. **02. HEADERS:** Thông tin mô tả Request (VD: `Host`, `Content-Type`, `Content-Length`)
3. **03. BLANK LINE:** Dòng trống phân cách giữa Header và Body.
4. **04. BODY:** Dữ liệu Client gửi lên Server (Thường xuất hiện trong `POST` và `PUT`).

> **Công thức cấu trúc:**
> `REQUEST = REQUEST LINE + HEADERS + BLANK LINE + BODY`

---

## Slide 6: Các HTTP Method phổ biến

| HTTP Method | Hành động | Ví dụ Request Line | Mô tả chi tiết |
|---|---|---|---|
| **GET** | Đọc dữ liệu | `GET /api/students HTTP/1.1` | Lấy danh sách sinh viên. Thường **không có Body**. |
| **POST** | Tạo dữ liệu mới | `POST /api/students HTTP/1.1` | Thêm một sinh viên mới. Thường **có Body**. |
| **PUT** | Cập nhật dữ liệu | `PUT /api/students/10 HTTP/1.1` | Cập nhật thông tin sinh viên ID 10. Thường **có Body**. |
| **DELETE** | Xóa dữ liệu | `DELETE /api/students/10 HTTP/1.1` | Xóa sinh viên ID 10. Xác định tài nguyên bằng URL. |

> **Nguyên tắc:** CÙNG MỘT TÀI NGUYÊN – KHÁC METHOD $\rightarrow$ KHÁC HÀNH ĐỘNG.

---

## Slide 7: URL, Header và Body của Request

### Phân tích cấu trúc URL
Ví dụ: `http://localhost:8080/api/students?id=10`

- **Scheme:** `http`
- **Host:** `localhost`
- **Port:** `8080`
- **Path:** `/api/students`
- **Query String:** `id=10` (Truyền tham số ngay trên URL)

### Phân biệt Headers và Body
- **Headers:**
  - `Content-Type`: Hướng dẫn cách giải mã dữ liệu trong Body.
  - `Content-Length`: Xác định số byte của dữ liệu Body.
- **Body:** `{"name":"An", "age":20}`

> **Tóm tắt:** URL xác định tài nguyên • Header mô tả metadata • Body chứa dữ liệu truyền tải.

---

## Slide 8: Cấu trúc HTTP Response

### HTTP Response mẫu
```http
HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 22

{"id":10,"name":"An"}
```

### Các thành phần chính
1. **01. STATUS LINE:** HTTP Version + Status Code + Reason Phrase (VD: `HTTP/1.1 200 OK`)
2. **02. HEADERS:** Thông tin mô tả Response (`Content-Type`, `Content-Length`)
3. **03. BLANK LINE:** Dòng trống phân cách giữa Header và Body.
4. **04. BODY:** Dữ liệu Server trả về Client (Có thể là HTML, JSON, hình ảnh hoặc tệp tin).

> **Công thức cấu trúc:**
> `RESPONSE = STATUS LINE + HEADERS + BLANK LINE + BODY`

---

## Slide 9: HTTP Status Code

| Nhóm Mã | Tên Nhóm | Mã Phổ Biến | Ý Nghĩa |
|---|---|---|---|
| **2xx** | Thành công | `200 OK`, `201 Created` | Request được tiếp nhận và xử lý thành công. |
| **3xx** | Chuyển hướng | `301 Moved Permanently`, `302 Found` | Client được chuyển hướng tới một URL khác. |
| **4xx** | Lỗi phía Client | `400 Bad Request`, `404 Not Found` | Request bị sai cú pháp hoặc tài nguyên không tồn tại. |
| **5xx** | Lỗi phía Server | `500 Internal Server Error`, `503 Service Unavailable` | Server gặp lỗi nội bộ, không thể xử lý Request. |

### Mô phỏng phản hồi Status Code
- Truy cập `GET /students/10`:
  - Tìm thấy dữ liệu $\rightarrow$ Trả `200 OK`
  - Không tìm thấy $\rightarrow$ Trả `404 Not Found`
  - Gặp lỗi hệ thống $\rightarrow$ Trả `500 Internal Server Error`

> Status Code giúp Client biết chính xác kết quả xử lý của Request.

---

## Slide 10: Content-Type và Response Body

Header `Content-Type` báo cho Client/Trình duyệt biết cách hiển thị dữ liệu trong Body:

- **Văn bản thuần:** `Content-Type: text/plain; charset=utf-8`
  - Body: `Xin chào!` $\rightarrow$ Trình duyệt hiển thị chuỗi văn bản UTF-8.
- **Trang HTML:** `Content-Type: text/html; charset=utf-8`
  - Body: `<h1>Xin chào</h1>` $\rightarrow$ Trình duyệt phân tích và render thẻ HTML.
- **Dữ liệu JSON:** `Content-Type: application/json`
  - Body: `{"id":10,"name":"An"}` $\rightarrow$ Trình duyệt/JS phân tích thành Object.
- **Hình ảnh:** `Content-Type: image/png`
  - Body: PNG binary bytes $\rightarrow$ Trình duyệt giải mã và hiển thị ảnh.

> ⚠️ **Cảnh báo:** Header phải phù hợp với dữ liệu thực tế trong Body. Sai `Content-Type` khiến Client xử lý sai nội dung (VD: Hiện mã HTML thô thay vì hiển thị giao diện).

---

## Slide 11: Luồng xử lý của HTTP Server

1. **01. LISTEN:** Lắng nghe các kết nối đến (VD: `http://localhost:8080/`).
2. **02. NHẬN REQUEST:** Đọc thông tin kết nối từ Client.
3. **03. ĐỌC METHOD + URL:** Phân tích thông tin Request (VD: `GET /api/time`).
4. **04. ROUTING:** Chọn hàm xử lý thích hợp (`Router` $\rightarrow$ `Handler`).
   - Nếu không có route khớp $\rightarrow$ Trả `404 Not Found`.
   - Nếu xảy ra lỗi nội bộ $\rightarrow$ Trả `500 Internal Server Error`.
5. **05. XỬ LÝ:** Đọc dữ liệu, tính toán và chuẩn bị kết quả.
6. **06. TẠO RESPONSE:** Thiết lập Status Code, Headers và Body (VD: `HTTP/1.1 200 OK`, `Content-Type: application/json`, `{"time":"10:30"}`).
7. **07. GỬI VÀ ĐÓNG RESPONSE:** Gửi kết quả về Client và đóng kết nối stream.

> Tiếp tục quay lại bước **01** để nhận Request mới. **Mỗi Request phải nhận được đúng một Response.**

---

## Slide 12: Kiến trúc cơ bản của HTTP Server

- **Listener (`HttpListener` / `HttpServer`):** Lắng nghe yêu cầu kết nối tại cổng chỉ định (`http://localhost:8080/`).
- **Context (`HttpListenerContext` / `HttpExchange`):** Đại diện cho một phiên làm việc Request - Response.
  - **Request Object (`HttpListenerRequest` / `getRequest`):** Chứa `HttpMethod`, `Url`, `Headers`, `InputStream`.
  - **Response Object (`HttpListenerResponse` / `getResponse`):** Chứa `StatusCode`, `ContentType`, `OutputStream`.
- **Luồng:** `Listener` $\rightarrow$ Khởi tạo `Context` $\rightarrow$ Đọc `Request` $\rightarrow$ Ghi `Response`.

---

## Slide 13: Code Java Khởi động HTTP Server

Sử dụng thư viện chuẩn `com.sun.net.httpserver` có sẵn trong Java Development Kit (JDK):

```java
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import java.io.IOException;

// 1. KHỞI TẠO SERVER
public class SimpleHttpServer {
    public static void main(String[] args) throws IOException {
        // Tạo server lắng nghe ở cổng 8080
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        // Đăng ký context đường dẫn gốc "/"
        server.createContext("/", new MyHandler());
        
        // Bắt đầu server
        server.start();
        System.out.println("Server: http://localhost:8080/");
    }
}

// 2. NHẬN REQUEST VÀ TRẢ RESPONSE
class MyHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            System.out.println(method + " " + path);

            String response = "HTTP Server đang hoạt động!\n";
            byte[] data = response.getBytes("UTF-8");

            // Thiết lập Header
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
            
            // Gửi Status Code 200 và độ dài dữ liệu
            exchange.sendResponseHeaders(200, data.length);
            
            // Ghi dữ liệu ra Response Body
            exchange.getResponseBody().write(data);
        } finally {
            // Đóng kết nối
            exchange.close();
        }
    }
}
```

### Quy trình chạy & kiểm thử
1. **Bước 1:** Chạy chương trình Java `SimpleHttpServer`.
2. **Bước 2:** Mở trình duyệt web bất kỳ.
3. **Bước 3:** Nhập địa chỉ `http://localhost:8080/`.

---

## Slide 14: Code Routing và Trả Response (Java)

Thực hiện điều hướng (routing) dựa trên `Method` + `URL Path`:

```java
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class RouterHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange context) throws IOException {
        String method = context.getRequestMethod();
        String path = context.getRequestURI().getPath();

        if (method.equals("GET") && path.equals("/")) {
            sendResponse(context, 200, "text/html; charset=utf-8", "<h1>HTTP Server</h1>");
        } else if (method.equals("GET") && path.equals("/api/time")) {
            String json = "{\"time\":\"" + 
                java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) + 
                "\"}";
            sendResponse(context, 200, "application/json", json);
        } else {
            sendResponse(context, 404, "text/plain; charset=utf-8", "404 Not Found");
        }
    }

    // Hàm bổ trợ gửi Response tiêu chuẩn
    private void sendResponse(HttpExchange context, int statusCode, String contentType, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);

        context.getResponseHeaders().add("Content-Type", contentType);
        context.sendResponseHeaders(statusCode, data.length);

        try (OutputStream os = context.getResponseBody()) {
            os.write(data);
        }
    }
}
```

> **Nguyên tắc Routing:** `ROUTING = METHOD + URL`

---

## Slide 15: Kiểm thử và Bài tập mở rộng

### Ca kiểm thử ứng dụng
- **Ca kiểm thử 1:** `GET /` $\rightarrow$ **200 OK** (`text/html`) $\rightarrow$ Body: `<h1>HTTP Server</h1>`
- **Ca kiểm thử 2:** `GET /api/time` $\rightarrow$ **200 OK** (`application/json`) $\rightarrow$ Body: `{"time":"10:30:00"}`
- **Ca kiểm thử 3:** `GET /abc` $\rightarrow$ **404 Not Found** (`text/plain`) $\rightarrow$ Body: `404 Not Found`

### Bài tập mở rộng tự luyện
1. **01.** Thêm route `POST /api/message` để đọc dữ liệu từ Request Body.
2. **02.** Trả về danh sách sinh viên dưới dạng JSON array.
3. **03.** Phục vụ tệp tĩnh HTML và CSS từ thư mục cục bộ.
4. **04.** Xử lý nhiều Request đồng thời bằng cách tích hợp `ExecutorService` (Multi-threading).

---

## Slide 16: Code Java cho HTTP Client (GET Request)

Sử dụng thư viện `java.net.http.HttpClient` (Java 11+):

```java
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

public class HttpClientExample {
    public static void main(String[] args) throws Exception {
        // Tạo Client
        HttpClient client = HttpClient.newHttpClient();

        // Tạo Request
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/api/time"))
            .GET()
            .build();

        // Gửi Request và nhận Response dạng Chuỗi
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // In kết quả
        System.out.println("Status: " + response.statusCode());
        System.out.println("Content-Type: " + response.headers().firstValue("Content-Type").orElse(""));
        System.out.println("Body: " + response.body());
    }
}
```

### Kết quả chạy chương trình
```text
> Status: 200
> Content-Type: application/json
> Body: {"time":"10:30:00"}
```

> Client gửi Request $\rightarrow$ Đọc Status, Header và Body từ Response.

---

## Slide 17: HTTP Client gửi POST JSON (Java)

```java
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;

public class HttpClientPostJsonExample {

    static class Message {
        public String text;
    }

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        Message message = new Message();
        message.text = "Xin chào Server!";

        // Chuyển đối tượng thành chuỗi JSON
        String jsonPayload = "{\"text\":\"" + message.text + "\"}";

        // Xây dựng POST Request với Content-Type application/json
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/api/message"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .build();

        // Gửi Request
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // In kết quả
        System.out.println("Status: " + response.statusCode());
        System.out.println("Body: " + response.body());
    }
}
```

### Kết quả Console
```text
Status: 201
Body: {"message":"Đã nhận dữ liệu"}
```

---

## Slide 18: Server xử lý POST JSON (Java)

```java
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class ApiHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if (method.equals("POST") && path.equals("/api/message")) {
            // 1. Kiểm tra Header Content-Type
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.startsWith("application/json")) {
                String response = "415 Unsupported Media Type";
                exchange.sendResponseHeaders(415, response.getBytes(StandardCharsets.UTF_8).length);
                exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
                return;
            }

            // 2. Đọc Body từ Request InputStream
            InputStreamReader reader = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[1024];
            int n;
            while ((n = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, n);
            }
            String requestBody = sb.toString();

            // 3. Chuẩn bị dữ liệu trả về 201 Created
            String responseJson = "{\"message\":\"Đã nhận dữ liệu\"}";
            byte[] responseBytes = responseJson.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);

        } else {
            // Trả lỗi 404 Not Found cho các route khác
            String notFound = "404 Not Found";
            exchange.sendResponseHeaders(404, notFound.getBytes(StandardCharsets.UTF_8).length);
            exchange.getResponseBody().write(notFound.getBytes(StandardCharsets.UTF_8));
        }
    }
}
```

### 4 bước xử lý route POST JSON ở Server
1. **01. NHẬN POST:** Nhận request đường dẫn `/api/message`.
2. **02. KIỂM TRA HEADER:** Yêu cầu `Content-Type: application/json` (Không đúng $\rightarrow$ trả `415 Unsupported Media Type`).
3. **03. ĐỌC BODY:** Đọc từ `InputStream` $\rightarrow$ Lấy nội dung chuỗi JSON.
4. **04. TRẢ RESPONSE:** Gửi `201 Created` kèm JSON thông báo `{"message":"Đã nhận dữ liệu"}`.

---

## Slide 19: HTTP Client xử lý lỗi (Java)

Phân biệt giữa **HTTP Error**, **Lỗi kết nối** và **Timeout**:

```java
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpConnectTimeoutException;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;

public class HttpClientErrorExample {
    public static void main(String[] args) {
        // Cấu hình Client với connectTimeout 5s
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/api/time"))
                .GET()
                .timeout(Duration.ofSeconds(5)) // Timeout chờ response
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Có Response trả về từ Server
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                System.out.println("Thành công: " + response.body());
            } else {
                System.out.println("HTTP Error StatusCode: " + response.statusCode());
            }

        } catch (HttpConnectTimeoutException e) {
            System.out.println("Lỗi: Quá thời gian kết nối (Timeout) - " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Lỗi kết nối mạng / I/O - " + e.getMessage());
        } catch (InterruptedException e) {
            System.out.println("Lỗi: Tiến trình bị gián đoạn - " + e.getMessage());
        }
    }
}
```

### Phân loại tình huống xử lý lỗi

- **CÓ RESPONSE (Mã 2xx):** `statusCode` thuộc $200 - 299 \rightarrow$ Nhận và xử lý dữ liệu bình thường.
- **CÓ RESPONSE (Mã 4xx / 5xx):** Server vẫn phản hồi nhưng báo lỗi (404 Not Found, 500 Internal Error) $\rightarrow$ Đọc Status Code và Body lỗi để xử lý logic.
- **LỖI KẾT NỐI / TIMEOUT:** Không nhận được bất kỳ Response nào từ Server (Server dừng hoạt động, sai địa chỉ IP, đứt mạng) $\rightarrow$ Bắt các Exception (`HttpConnectTimeoutException`, `IOException`).

> **Ghi nhớ:** Luôn kiểm tra cả **Status Code** và bắt các **Exception** khi lập trình HTTP Client.
