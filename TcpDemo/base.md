# LẬP TRÌNH MẠNG - NETWORK PROGRAMMING

**TRƯỜNG ĐẠI HỌC SÀI GÒN - SAIGON UNIVERSITY (SGU)**
**Giảng viên:** TRẦN MINH NHẬT

**Các chủ đề chính:**

- Mạng máy tính
- Giao thức TCP/IP
- Client Server
- Socket Programming
- Bảo mật mạng

---

## BÀI 6: TCP SERVER PHỤC VỤ NHIỀU CLIENT (JAVA)

### Sử dụng Thread trong Java

**Môn:** Lập trình mạng

**Mô hình hoạt động:**

- **TCP Server (:5000)** lắng nghe các kết nối.
- **Client 1 (127.0.0.1)** <----> THREAD / TASK 1
- **Client 2 (127.0.0.1)** <----> THREAD / TASK 2
- **Client 3 (127.0.0.1)** <----> THREAD / TASK 3

**Đoạn mã cơ bản:**

```java
ServerSocket serverSocket = new ServerSocket(5000);
System.out.println("TCP Server đang chạy trên port 5000...");
while (true) {
    Socket clientSocket = serverSocket.accept();
    // Tạo một thread (task) để xử lý từng client
    Thread task = new Thread(new ClientHandler(clientSocket));
    task.start();
}
Đặc điểm:

Mỗi client được xử lý bằng một thread riêng: Cho phép nhiều client kết nối đồng thời.

Server luôn lắng nghe trên port 5000: Sử dụng ServerSocket và accept() trong vòng lặp.

Dễ mở rộng và xử lý nhiều client: Mô hình đa luồng (Thread per Client).

💡 Mỗi client = 1 Thread (Task): TCP Server có thể phục vụ nhiều client cùng lúc.

MỤC TIÊU BÀI HỌC
Giải thích hạn chế của Server một Client.

Mô tả kiến trúc Server nhiều Client.

Liên tục gọi hàm chấp nhận kết nối (Accept).

Tạo một Task/Thread riêng cho mỗi Client.

Xây dựng hàm xử lý Client độc lập.

Quản lý kết nối và giải phóng tài nguyên.

HẠN CHẾ CỦA SERVER MỘT CLIENT
Tình trạng: Khi Client 1 ĐANG XỬ LÝ, thì Client 2 và Client 3 PHẢI CHỜ.

Quy trình tuần tự: Accept một Client ➡️ Nhận dữ liệu ➡️ Gửi phản hồi ➡️ Đóng Server.

Nhược điểm:

Chỉ phục vụ một Client.

Client khác phải chờ.

Server dừng sau khi xử lý xong.

Không phù hợp với dịch vụ thực tế.

⚠️ Xử lý tuần tự làm các Client không thể giao tiếp đồng thời.

KIẾN TRÚC SERVER NHIỀU CLIENT
Listening Socket: Liên tục chờ kết nối từ các Client.

Khi Client kết nối (Connect), Listening Socket thực hiện ACCEPT.

Sinh ra các Task độc lập:

ACCEPT ➡️ TASK 1 ➡️ HandleClient(1)

ACCEPT ➡️ TASK 2 ➡️ HandleClient(2)

ACCEPT ➡️ TASK 3 ➡️ HandleClient(3)

Quy trình:

Listening Socket tiếp tục chờ kết nối.

Mỗi Client được giao cho một Task.

Các Client được xử lý độc lập.

Server không dừng sau một kết nối.

KHỞI ĐỘNG SERVER NHIỀU CLIENT (JAVA)
Java
import java.net.*;
import java.io.*;

public class TcpServer {
    public static void main(String[] args) {
        int port = 5000;
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server dang lang nghe tai port " + port + "...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                // Tạo một thread để xử lý từng client
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
Hoạt động của ServerSocket:

Lắng nghe trên mọi giao diện IPv4 của máy (0.0.0.0:5000 - IPv4 Any).

Loopback: 127.0.0.1

LAN: 192.168.1.10

Local IPv4: 10.0.0.5

Một port dùng cho mọi địa chỉ IPv4 cục bộ (0.0.0.0).

Server vẫn cần accept() trước khi xử lý client.

Mỗi client kết nối sẽ được xử lý bằng một thread riêng.

LIÊN TỤC CHẤP NHẬN CLIENT (JAVA)
Quy trình vòng lặp vô hạn:

ACCEPT (Nhận Client mới): serverSocket.accept()

TẠO TASK (Tạo thread xử lý): new ClientHandler(...)

HANDLE CLIENT (Xử lý Client độc lập): task.start()

QUAY LẠI ACCEPT (Chờ Client tiếp theo trong vòng lặp)

Vòng lặp vô hạn giúp Server không dừng.

Mỗi Client được xử lý bởi một thread riêng (xử lý song song).

Client cũ không chặn việc Accept Client mới.

HÀM XỬ LÝ TỪNG CLIENT (JAVA)
Java
import java.net.Socket;
import java.io.*;

public class ClientHandler implements Runnable {
    private Socket clientSocket;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
            PrintWriter writer = new PrintWriter(
                clientSocket.getOutputStream(), true);

            System.out.println("Bắt đầu xử lý Client: " +
                               clientSocket.getInetAddress());

            // Nhận và gửi dữ liệu
            String message;
            while ((message = reader.readLine()) != null) {
                System.out.println("Client: " + message);
                writer.println("Server: " + message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { clientSocket.close(); } catch (IOException e) {}
        }
    }
}
Mỗi Task gọi một ClientHandler (thread riêng, xử lý độc lập).

getInputStream() / getOutputStream() tạo luồng trao đổi dữ liệu.

Tự động đóng kết nối trong khối finally (hoặc dùng try-with-resources).

TẠO LUỒNG ĐỌC VÀ GHI (JAVA)
Tạo luồng đọc (StreamReader):

Java
BufferedReader reader =
    new BufferedReader(
        new InputStreamReader(
            clientSocket.getInputStream(),
            StandardCharsets.UTF_8
        )
    );
Tạo luồng ghi (StreamWriter):

Java
PrintWriter writer =
    new PrintWriter(
        new OutputStreamWriter(
            clientSocket.getOutputStream(),
            StandardCharsets.UTF_8
        ),
        true // AutoFlush = true
    );
BufferedReader — NHẬN DỮ LIỆU.

PrintWriter — GỬI DỮ LIỆU. (AutoFlush = true ➡️ gửi ngay).

Reader và Writer dùng chung một NetworkStream.

Hai phía phải sử dụng cùng Encoding (ví dụ: UTF-8, tránh lỗi tiếng Việt).

Mỗi HandleClient (Task) có Reader và Writer riêng.

TRAO ĐỔI NHIỀU THÔNG ĐIỆP (JAVA)
Java
BufferedReader reader = new BufferedReader(
    new InputStreamReader(socket.getInputStream(), "UTF-8"));
PrintWriter writer = new PrintWriter(
    new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

String message;
while ((message = reader.readLine()) != null) {
    if (message.equals("exit")) {
        break;
    }
    System.out.println("Client: " + message);
    writer.println("Echo: " + message);
}
writer.close();
Một kết nối gửi được nhiều thông điệp.

Mỗi thông điệp nhận một phản hồi Echo.

exit chỉ kết thúc Client hiện tại.

Các Task khác vẫn tiếp tục hoạt động.

ĐÓNG KẾT NỐI AN TOÀN
Khi Client gửi "exit" ➡️ HandleClient kết thúc.

Cần giải phóng tài nguyên của Client hiện tại (Socket, InputStream, OutputStream).

Đóng kết nối an toàn (sử dụng .close() trong khối finally hoặc try-with-resources) giúp hạn chế rò rỉ Socket và luồng.

Lưu ý: Chỉ giải phóng tài nguyên của Client hiện tại, KHÔNG gọi dừng Server toàn cục trong HandleClient.

Listening Server và các Task khác vẫn hoạt động bình thường.

CHẠY THỬ NHIỀU CLIENT
Các bước thực hiện:

Chạy TcpServer.

Mở ba cửa sổ Client App (vd: Telnet hoặc ứng dụng TcpClient tự viết).

Kết nối đến port 5000.

Gửi thông điệp từ từng Client (vd: "Hello", "Network", "C#").

Kiểm tra phản hồi Echo.

Nhập "exit" để đóng từng Client.

Kiểm tra Port và Kết nối (trên Command Prompt):

DOS
netstat -ano | findstr :5000
➡️ Kết quả sẽ hiển thị một dòng LISTENING và nhiều kết nối ESTABLISHED.

TỔNG KẾT VÀ BÀI TẬP
Quy trình Server nhiều Client:
START ➡️ ACCEPT CLIENT ➡️ TASK.RUN (Tạo Thread cho mỗi Client có NetworkStream riêng) ➡️ HANDLE CLIENT ➡️ READ / WRITE ➡️ CLOSE CLIENT (Giải phóng tài nguyên kết nối) ➡️ KẾT THÚC TASK.
(Listening Server không dừng khi Client thoát và tiếp tục Accept).

Bài tập thực hành:

Hiển thị số thứ tự của mỗi Client.

Đếm số Client đang kết nối.

Ghi thời gian kết nối và ngắt kết nối.

Server chuyển thông điệp thành chữ HOA.

Mở rộng thành Chat Server gửi cho mọi Client.

🎯 Sản phẩm: TCP Server phục vụ đồng thời nhiều Client tại port 5000.
```
