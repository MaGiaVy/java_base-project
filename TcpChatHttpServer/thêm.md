1. ### **1\. HTTP Server (Máy chủ phân phối file HTML)**

2. Đoạn mã này sử dụng thư viện com.sun.net.httpserver.HttpServer có sẵn trong JDK để khởi tạo một Web Server lắng nghe tại cổng 8080\. Server sẽ nhận Request, trích xuất đường dẫn URL và đọc file tương ứng (P3.HTM hoặc P4.HTM) từ thư mục chứa mã nguồn để trả về cho Client.  
3. **Chuẩn bị:** Bạn cần tạo sẵn 2 file văn bản có tên P3.HTM và P4.HTM (nội dung tuỳ ý, ví dụ \<h1\>Day la P3\</h1\>) nằm cùng thư mục với file code Java này.  
4. Java  
5. import com.sun.net.httpserver.HttpServer;  
6. import com.sun.net.httpserver.HttpHandler;  
7. import com.sun.net.httpserver.HttpExchange;  
8.   
9. import java.io.File;  
10. import java.io.IOException;  
11. import java.io.OutputStream;  
12. import java.net.InetSocketAddress;  
13. import java.nio.file.Files;  
14.   
15. public class MyHttpServer {  
16.     public static void main(String\[\] args) throws IOException {  
17.         // Khởi tạo Server lắng nghe tại cổng 8080  
18.         HttpServer server \= HttpServer.create(new InetSocketAddress(8080), 0);  
19.           
20.         // Tạo context xử lý mọi request gửi đến  
21.         server.createContext("/", new FileHandler());  
22.           
23.         server.start();  
24.         System.out.println("Server đang chạy tại http\://localhost:8080/");  
25.         System.out.println("Hãy đảm bảo bạn đã tạo file P3.HTM và P4.HTM cùng thư mục.");  
26.     }  
27.   
28.     static class FileHandler implements HttpHandler {  
29.         @Override  
30.         public void handle(HttpExchange exchange) throws IOException {  
31.             // Lấy đường dẫn từ URL (ví dụ: /P3.HTM)  
32.             String path \= exchange.getRequestURI().getPath();  
33.               
34.             // Bỏ dấu "/" ở đầu để lấy tên file thực tế  
35.             String fileName \= path.equals("/") ? "index.html" : path.substring(1);  
36.             File file \= new File(fileName);  
37.   
38.             if (file.exists() && \!file.isDirectory()) {  
39.                 // Nếu tìm thấy file, đọc nội dung và trả về Status 200 OK  
40.                 byte\[\] responseBytes \= Files.readAllBytes(file.toPath());  
41.                 exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");  
42.                 exchange.sendResponseHeaders(200, responseBytes.length);  
43.                   
44.                 OutputStream os \= exchange.getResponseBody();  
45.                 os.write(responseBytes);  
46.                 os.close();  
47.             } else {  
48.                 // Nếu không tìm thấy file, trả về lỗi 404 Not Found  
49.                 String errorMsg \= "\<h1\>404 Not Found \- Khong tim thay file " \+ fileName \+ "\</h1\>";  
50.                 exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");  
51.                 exchange.sendResponseHeaders(404, errorMsg.getBytes().length);  
52.                   
53.                 OutputStream os \= exchange.getResponseBody();  
54.                 os.write(errorMsg.getBytes());  
55.                 os.close();  
56.             }  
57.         }  
58.     }  
59. }  
60. 

61. ### **2\. HTTP Client (Ứng dụng trích xuất nội dung web)**

62. Đoạn mã này sử dụng java.net.http.HttpClient để tạo một HTTP Request theo phương thức GET. Chương trình cho phép bạn nhập URL linh hoạt từ bàn phím (có thể trỏ vào IIS hoặc trỏ vào Server tự code ở trên) và in ra Status Code cùng nội dung HTML nhận được.  
63. Java  
64. import java.net.URI;  
65. import java.net.http.HttpClient;  
66. import java.net.http.HttpRequest;  
67. import java.net.http.HttpResponse;  
68. import java.util.Scanner;  
69.   
70. public class MyHttpClient {  
71.     public static void main(String\[\] args) {  
72.         Scanner scanner \= new Scanner(System.in);  
73.           
74.         System.out.println("=== HTTP CLIENT \===");  
75.         System.out.print("Nhập URL cần tải (ví dụ: http\://localhost:8080/P3.HTM): ");  
76.         String urlString \= scanner.nextLine();  
77.   
78.         try {  
79.             // Khởi tạo HttpClient  
80.             HttpClient client \= HttpClient.newHttpClient();  
81.               
82.             // Xây dựng request GET đến URL vừa nhập  
83.             HttpRequest request \= HttpRequest.newBuilder()  
84.                     .uri(URI.create(urlString))  
85.                     .GET()  
86.                     .build();  
87.   
88.             // Gửi request và nhận response dạng String  
89.             HttpResponse\<String\> response \= client.send(request, HttpResponse.BodyHandlers.ofString());  
90.   
91.             System.out.println("\\n--- KẾT QUẢ TỪ SERVER \---");  
92.             System.out.println("Status Code: " \+ response.statusCode());  
93.             System.out.println("Nội dung HTML (Body):");  
94.             System.out.println(response.body());  
95.               
96.         } catch (Exception e) {  
97.             System.out.println("\\nLỗi kết nối: " \+ e.getMessage());  
98.             System.out.println("Vui lòng kiểm tra lại URL hoặc xem Server đã bật chưa.");  
99.         }  
100.           
101.         scanner.close();  
102.     }  
103. }  
104.   
105. Chạy file MyHttpServer.java trước để máy chủ bắt đầu lắng nghe. Sau đó, chạy file MyHttpClient.java và nhập URL http\://localhost:8080/P3.HTM để kiểm tra luồng giao tiếp giữa 2 chương trình bạn tự viết. Tương tự, bạn có thể nhập http\://localhost/P1.HTM để test kết nối chéo với IIS.  
106. 

Quá tuyệt vời\! Có IIS rồi thì bây giờ mình bắt tay vào thực hiện **kịch bản test chéo 4 đường** trên bảng của thầy luôn nha Vỹ.

Làm tuần tự các bước sau để chứng minh ứng dụng mình code là chuẩn HTTP nhé:

**Bước 1: Chuẩn bị "mồi" (Tạo các file HTML)**

* **Cho IIS:** Bạn vào thư mục `C:\inetpub\wwwroot`, tạo 2 file tên là `P1.HTM` và `P2.HTM`. Mở bằng Notepad gõ dòng chữ gì đó vào (ví dụ: `<h1>Day la P1 tu IIS</h1>`) rồi lưu lại.  
* **Cho Server tự code:** Vào thư mục chứa project Java của bạn (chỗ chứa file `MyHttpServer.java`), tạo 2 file `P3.HTM` và `P4.HTM` với nội dung tương tự (ví dụ: `<h1>Day la P3 tu Java Server</h1>`).

**Bước 2: Bật Web Server tự code lên**

* Bấm Run file `MyHttpServer.java` mình vừa cung cấp ở trên.  
* Cứ để cửa sổ console đó chạy ngầm (lúc này Server đang túc trực lắng nghe ở port 8080).

**Bước 3: Bắt đầu Test chéo 4 đường (Đúng y như sơ đồ thầy vẽ)** Bây giờ bạn mở trình duyệt Firefox (hoặc Chrome) và chuẩn bị chạy file `MyHttpClient.java` để bắt đầu bắn Request:

* 🔥 **Đường 1 (Firefox \<-\> IIS):** Mở trình duyệt gõ `http://localhost/P1.HTM`. Nếu thấy nội dung hiện ra \-\> IIS đã hoạt động hoàn hảo\! (Port 80 mặc định không cần gõ).  
* 🔥 **Đường 2 (Firefox \<-\> Java Server):** Vẫn trên trình duyệt, gõ `http://localhost:8080/P3.HTM`. Nếu trình duyệt hiện ra nội dung file P3 \-\> Chúc mừng, Server của bạn viết chuẩn đến mức trình duyệt quốc tế cũng "đọc hiểu".  
* 🔥 **Đường 3 (Java Client \<-\> Java Server):** Chạy file `MyHttpClient.java`, nhập vào console URL: `http://localhost:8080/P3.HTM`. Nếu console in ra mã HTML của P3 và Status 200 \-\> Client của bạn giao tiếp tốt với Server nhà làm.  
* 🔥 **Đường 4 (Java Client \<-\> IIS \- Thử thách cuối):** Chạy lại file `MyHttpClient.java`, lần này đổi URL thành: `http://localhost/P1.HTM`. Nếu console kéo được nội dung HTML của P1 từ IIS về thành công \-\> Client của bạn giao tiếp chuẩn HTTP với máy chủ chuyên nghiệp\!

Xong 4 đường này là coi như báo cáo kết quả mãn nhãn với thầy luôn\! Chạy thử xem có đường nào bị "tắc" không thì báo mình fix code cho nhé.

