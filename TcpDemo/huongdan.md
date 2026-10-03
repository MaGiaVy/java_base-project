# 🌐 TỔNG KẾT TCP SOCKET SERVER-CLIENT (JAVA)

---

## 📋 TỔNG QUÁT

**TCP Socket** = Giao thức để 2 chương trình (Server & Client) giao tiếp với nhau qua Network

```
    Server (Máy chủ)              Client (Máy khách)
    ┌──────────────┐             ┌──────────────┐
    │  Lắng nghe   │             │  Kết nối tới │
    │  port 5000   │◄───────────►│  Server IP   │
    │  Nhận dữ liệu│◄───────────►│  port 5000   │
    │  Gửi phản hồi│◄───────────►│  Gửi dữ liệu │
    └──────────────┘             └──────────────┘
```

---

## 🔄 FLOW HOẠT ĐỘNG

```
SERVER SIDE:
1. Tạo ServerSocket (lắng nghe port 5000)
2. Chờ client kết nối → server.accept()
3. Nhận dữ liệu từ client → BufferedReader.readLine()
4. Gửi phản hồi cho client → PrintWriter.println()
5. Đóng kết nối → client.close()

CLIENT SIDE:
1. Tạo Socket (kết nối Server: 127.0.0.1:5000)
2. Gửi dữ liệu tới server → PrintWriter.println()
3. Nhận phản hồi từ server → BufferedReader.readLine()
4. Đóng kết nối → socket.close()
```

---

## 💻 CODE DETAIL

### **1. SERVER - Khởi động lắng nghe**

```java
import java.net.*;
import java.io.*;

public class TcpServer {
    public static void main(String[] args) {
        try {
            // 1. Tạo ServerSocket lắng nghe port 5000
            ServerSocket serverSocket = new ServerSocket(5000, 50,
                InetAddress.getByName("127.0.0.1"));

            System.out.println("Server đã khởi động, lắng nghe port 5000...");

            // 2. Chờ client kết nối
            Socket client = serverSocket.accept();  // BLOCKING - chờ client

            System.out.println("Client kết nối từ: " + client.getInetAddress());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

**Giải thích:**

- `ServerSocket(5000, 50, IP)` = Tạo socket lắng nghe port 5000
  - 5000 = port
  - 50 = max 50 client chờ
  - 127.0.0.1 = localhost (chỉ trên máy này)
- `serverSocket.accept()` = Chờ client kết nối (BLOCKING)

---

### **2. SERVER - Nhận dữ liệu**

```java
Socket client = serverSocket.accept();

// Nhận dữ liệu từ client
BufferedReader reader = new BufferedReader(
    new InputStreamReader(client.getInputStream(),
    StandardCharsets.UTF_8)
);

String message = reader.readLine();  // Đọc 1 dòng từ client
System.out.println("Client gửi: " + message);
// Output: "Client gửi: Xin chào Server"
```

**Giải thích:**

- `client.getInputStream()` = Lấy stream nhận dữ liệu từ client
- `InputStreamReader()` = Chuyển bytes thành characters
- `BufferedReader()` = Đọc từng dòng
- `StandardCharsets.UTF_8` = Encoding UTF-8 (để hiển thị dấu tiếng Việt)
- `readLine()` = Đọc tới khi gặp `\n` (enter)

---

### **3. SERVER - Gửi phản hồi**

```java
// Gửi phản hồi cho client
PrintWriter writer = new PrintWriter(
    client.getOutputStream(), true, StandardCharsets.UTF_8
);

writer.println("Server đã nhận: " + message);
System.out.println("Server đã gửi phản hồi");

// Đóng kết nối
client.close();
```

**Giải thích:**

- `client.getOutputStream()` = Lấy stream gửi dữ liệu tới client
- `PrintWriter()` = Ghi dữ liệu (2 params: output stream, auto flush)
- `true` = auto flush (tự động gửi ngay lập tức)
- `println()` = Ghi 1 dòng + thêm `\n` cuối
- `client.close()` = Đóng kết nối với client

---

### **4. CLIENT - Kết nối tới Server**

```java
import java.net.*;
import java.io.*;

public class TcpClient {
    public static void main(String[] args) {
        String serverIp = "127.0.0.1";  // IP Server
        int serverPort = 5000;           // Port Server

        try {
            // Kết nối tới Server
            Socket socket = new Socket(serverIp, serverPort);
            System.out.println("Đã kết nối đến Server");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

**Giải thích:**

- `Socket(IP, Port)` = Kết nối tới Server
- 127.0.0.1 = localhost (máy này)
- 5000 = port Server đang lắng nghe

---

### **5. CLIENT - Gửi dữ liệu**

```java
Socket socket = new Socket("127.0.0.1", 5000);

// Gửi dữ liệu tới server
PrintWriter writer = new PrintWriter(
    socket.getOutputStream(), true, StandardCharsets.UTF_8
);

writer.println("Xin chào Server");
System.out.println("Đã gửi: Xin chào Server");
```

**Giải thích:**

- Giống server, dùng `PrintWriter` để gửi
- `println()` = Ghi dòng + enter

---

### **6. CLIENT - Nhận phản hồi**

```java
// Nhận phản hồi từ server
BufferedReader reader = new BufferedReader(
    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
);

String response = reader.readLine();
System.out.println("Server gửi: " + response);
// Output: "Server gửi: Server đã nhận: Xin chào Server"

// Đóng kết nối
socket.close();
```

---

## 🎯 COMPLETE FLOW EXAMPLE

### **Server Code (TcpServer.java)**

```java
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class TcpServer {
    public static void main(String[] args) {
        try {
            // 1. Khởi động server
            ServerSocket serverSocket = new ServerSocket(5000, 50,
                InetAddress.getByName("127.0.0.1"));
            System.out.println("Server lắng nghe port 5000...");

            // 2. Chờ client kết nối
            Socket client = serverSocket.accept();
            System.out.println("Client kết nối: " + client.getInetAddress());

            // 3. Nhận dữ liệu
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8)
            );
            String message = reader.readLine();
            System.out.println("Client gửi: " + message);

            // 4. Gửi phản hồi
            PrintWriter writer = new PrintWriter(
                client.getOutputStream(), true, StandardCharsets.UTF_8
            );
            writer.println("Server đã nhận: " + message);

            // 5. Đóng
            client.close();
            serverSocket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

### **Client Code (TcpClient.java)**

```java
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class TcpClient {
    public static void main(String[] args) {
        try {
            // 1. Kết nối tới server
            Socket socket = new Socket("127.0.0.1", 5000);
            System.out.println("Đã kết nối Server");

            // 2. Gửi dữ liệu
            PrintWriter writer = new PrintWriter(
                socket.getOutputStream(), true, StandardCharsets.UTF_8
            );
            writer.println("Xin chào Server");
            System.out.println("Đã gửi: Xin chào Server");

            // 3. Nhận phản hồi
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
            );
            String response = reader.readLine();
            System.out.println("Server gửi: " + response);

            // 4. Đóng
            socket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

---

## 🏃 CHẠY CHƯƠNG TRÌNH

**Terminal 1 (Server):**

```bash
javac TcpServer.java
java TcpServer
# Output: Server lắng nghe port 5000...
# (chờ client kết nối)
```

**Terminal 2 (Client):**

```bash
javac TcpClient.java
java TcpClient
# Output: Đã kết nối Server
#         Đã gửi: Xin chào Server
#         Server gửi: Server đã nhận: Xin chào Server
```

**Terminal 1 (Server - kết quả):**

```
Server lắng nghe port 5000...
Client kết nối: /127.0.0.1
Client gửi: Xin chào Server
```

---

## 📚 BÀI TẬP THỰC HÀNH

### **Bài 1: Echo Server**

Tạo server nhận dữ liệu từ client và gửi lại nguyên văn

**Yêu cầu:**

- Server nhận message từ client
- Server in ra: "Nhận được: [message]"
- Server gửi lại: "Echo: [message]"
- Client in ra: "Server trả lời: [response]"

**Test:**

- Client gửi "Hôm nay là thứ mấy?"
- Server trả lời "Echo: Hôm nay là thứ mấy?"

---

### **Bài 2: Calculator Server**

Tạo server thực hiện phép tính

**Yêu cầu:**

- Client gửi: "10 + 5" (cách nhau bởi space)
- Server tính toán
- Server trả lời: "15"
- Hỗ trợ +, -, \*, /

**Test:**

- "10 + 5" → 15
- "20 - 8" → 12
- "4 \* 5" → 20
- "100 / 4" → 25

---

### **Bài 3: Multi-Client Server**

Tạo server xử lý nhiều client cùng lúc (dùng Thread)

**Yêu cầu:**

- Server có thể chấp nhận 3 client cùng lúc
- Mỗi client có 1 thread riêng
- Khi client gửi message → server gửi lại
- Khi client gửi "exit" → đóng kết nối

**Hint:**

```java
while (true) {
    Socket client = serverSocket.accept();
    new Thread(new ClientHandler(client)).start();  // Tạo thread mới
}

class ClientHandler implements Runnable {
    private Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        // Xử lý client trong thread này
    }
}
```

---

### **Bài 4: Chat Client-Server**

Tạo chương trình chat 2 chiều

**Yêu cầu:**

- Client có thể gửi message bất cứ lúc nào
- Server nhận và gửi lại kèm timestamp
- Format: "[HH:mm:ss] Client: message"
- Gõ "quit" để thoát

**Test:**

```
Client: Xin chào
Server: [14:30:45] Client: Xin chào
Client: Đây là tin nhắn thứ 2
Server: [14:30:50] Client: Đây là tin nhắn thứ 2
```

---

### **Bài 5: Student Info Server**

Server quản lý thông tin sinh viên

**Yêu cầu:**

- Server lưu 5 sinh viên (SV01-SV05) với tên và điểm
- Client gửi MaSV
- Server trả lời: "SV01: Nguyễn Văn A - 8.5"
- Nếu không tồn tại: "Sinh viên không tồn tại"

**Test:**

```
Client: SV02
Server: SV02: Trần Thị B - 9.0

Client: SV99
Server: Sinh viên không tồn tại
```

---

## 🔑 KEY POINTS

| Khái niệm          | Giải thích                   |
| ------------------ | ---------------------------- |
| **ServerSocket**   | Server lắng nghe, chờ client |
| **Socket**         | Kết nối từ client tới server |
| **InputStream**    | Nhận dữ liệu (từ socket)     |
| **OutputStream**   | Gửi dữ liệu (từ socket)      |
| **BufferedReader** | Đọc text từ stream           |
| **PrintWriter**    | Ghi text vào stream          |
| **Port**           | "Cửa" của server (0-65535)   |
| **127.0.0.1**      | Localhost (máy hiện tại)     |
| **accept()**       | Server chờ client (blocking) |
| **readLine()**     | Đọc tới `\n`                 |

---

## ⚠️ COMMON ERRORS

| Lỗi                              | Nguyên nhân         | Fix                              |
| -------------------------------- | ------------------- | -------------------------------- |
| `Connection refused`             | Server chưa chạy    | Chạy server trước client         |
| `Port already in use`            | Port 5000 đang dùng | Dùng port khác hoặc kill process |
| `Dấu tiếng Việt lỗi`             | Encoding sai        | Thêm `StandardCharsets.UTF_8`    |
| `BufferedReader.readLine() null` | Client đóng kết nối | Check client.close()             |
| `Cannot bind to port`            | Không đủ quyền      | Dùng port > 1024                 |

---

**Hoàn tất! Giờ bạn đã hiểu TCP Socket 100%! 🚀**
