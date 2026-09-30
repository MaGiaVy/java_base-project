# UDP UNICAST / BROADCAST / MULTICAST DEMO

**Giảng viên:** Trần Minh Nhật  
**Trường Đại học Sài Gòn (Saigon University)**

---

## CHƯƠNG 9: XÂY DỰNG UDP UNICAST / BROADCAST / MULTICAST DEMO

**Gửi thông điệp qua Unicast, Broadcast, và Multicast bằng Java**

---

## 1. Mục tiêu bài học

1. Hiểu rõ **3 kiểu gửi dữ liệu UDP**: Unicast, Broadcast, Multicast
2. Cấu hình `DatagramSocket` cho Unicast
3. Bật `setBroadcast(true)` để gửi Broadcast
4. Sử dụng `MulticastSocket` và `joinGroup()` để Multicast
5. **Chứng minh sự khác biệt** giữa Broadcast và Multicast qua demo interactive
6. Xây dựng 2 chương trình độc lập: **Sender** và **Receiver** với menu lựa chọn

---

## 2. So sánh 3 kiểu gửi UDP

| Tiêu chí | **Unicast** | **Broadcast** | **Multicast** |
|----------|-----------|--------------|--------------|
| **Đích đến** | 1 máy cụ thể | Tất cả máy trên mạng | Nhóm máy đã đăng ký |
| **Địa chỉ** | IP cụ thể (VD: 192.168.1.5) | 255.255.255.255 | Lớp D (239.1.1.1) |
| **Class Java** | `DatagramSocket` | `DatagramSocket` + `setBroadcast(true)` | `MulticastSocket` + `joinGroup()` |
| **Yêu cầu phía nhận** | Lắng nghe port | Lắng nghe port | **Gọi joinGroup()** mới nhận |
| **Hiệu quả** | Cao (chỉ 1 máy) | Thấp (tất cả) | Trung bình (chọn máy) |
| **Ứng dụng** | Chat 1-1 | Thông báo bắt buộc | Nhóm học tập, phòng họp |

---

## 3. Kiến trúc Demo

```
┌──────────────────────────────────────────┐
│         NetDemoSender.java               │
│  ┌──────────────────────────────────┐   │
│  │ Menu:                             │   │
│  │ [1] Gửi Unicast                  │   │
│  │ [2] Gửi Broadcast                │   │
│  │ [3] Gửi Multicast                │   │
│  └──────────────────────────────────┘   │
└────────┬─────────────────────────────────┘
         │
    MẠNG LAN (UDP Port 7000)
         │
         ├────────────────────────────────────────────┐
         │                                            │
┌────────▼───────────────────────┐      ┌────────────▼──────────────┐
│   NetDemoReceiver #1           │      │  NetDemoReceiver #2       │
│ ┌───────────────────────────┐  │      │ ┌─────────────────────┐   │
│ │ Menu:                     │  │      │ │ Menu:               │   │
│ │ [1] Lắng nghe Unicast     │  │      │ │ [1] Lắng nghe       │   │
│ │ [2] Lắng nghe Broadcast   │  │      │ │     Unicast         │   │
│ │ [3] Lắng nghe Multicast   │  │      │ │ [2] Lắng nghe       │   │
│ │     + joinGroup() YES/NO   │  │      │ │     Broadcast       │   │
│ └───────────────────────────┘  │      │ │ [3] Lắng nghe       │   │
│ IP: 192.168.1.2                │      │ │     Multicast + YES │   │
└────────────────────────────────┘      │ │ IP: 192.168.1.3    │   │
                                        │ └─────────────────────┘   │
                                        └────────────────────────────┘
```

---

## 4. Quy tắc bắt buộc (Rules)

### **Rule 1: Unicast**
```
✓ Sử dụng DatagramSocket
✓ Chỉ định IP cụ thể của máy nhận
✓ Ví dụ: 192.168.1.2:7000
```

### **Rule 2: Broadcast**
```
✓ Sử dụng DatagramSocket
✓ PHẢI gọi setBroadcast(true) trước khi gửi
✓ Gửi đến 255.255.255.255
✓ Tất cả máy trên mạng sẽ nhận (nếu lắng nghe port)
```

### **Rule 3: Multicast**
```
✓ Sử dụng MulticastSocket (không phải DatagramSocket)
✓ Gửi đến IP lớp D (224.0.0.0 - 239.255.255.255)
✓ Demo dùng 239.1.1.1:8000
✓ Máy nhận PHẢI gọi joinGroup() mới nhận được
✓ Không gọi joinGroup() = không nhận (CHỨNG MINH KHÁC BIỆT VỚI BROADCAST)
```

---

## 5. Unicast - Gửi 1-1

### **5.1 Sender gửi Unicast**

```java
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class UnicastSender {
    public static void main(String[] args) throws Exception {
        // 1. Tạo DatagramSocket
        DatagramSocket socket = new DatagramSocket();
        System.out.println("[Sender] Tạo UDP Unicast Socket");
        
        // 2. Nhập IP máy nhận
        System.out.print("Nhập IP máy nhận (VD: 192.168.1.2): ");
        String receiverIp = "192.168.1.2";  // Hoặc lấy từ scanner
        int port = 7000;
        
        // 3. Chuẩn bị thông điệp
        String message = "Xin chào máy nhận Unicast!";
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        
        // 4. Tạo DatagramPacket
        InetAddress recipientAddress = InetAddress.getByName(receiverIp);
        DatagramPacket packet = new DatagramPacket(data, data.length, 
            recipientAddress, port);
        
        System.out.println("[Sender] Đang gửi Unicast tới " + 
            receiverIp + ":" + port);
        System.out.println("[Sender] Thông điệp: " + message);
        
        // 5. Gửi gói tin
        socket.send(packet);
        
        System.out.println("[Sender] ✓ Đã gửi Unicast thành công!");
        socket.close();
    }
}
```

### **5.2 Receiver nhận Unicast**

```java
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;

public class UnicastReceiver {
    public static void main(String[] args) throws Exception {
        int port = 7000;
        
        // 1. Tạo DatagramSocket lắng nghe port 7000
        DatagramSocket socket = new DatagramSocket(port);
        System.out.println("[Receiver] Đang lắng nghe Unicast tại port " + port);
        
        // 2. Chuẩn bị buffer nhận dữ liệu
        byte[] buffer = new byte[1024];
        
        // 3. Chờ và nhận Datagram
        System.out.println("[Receiver] Chờ dữ liệu...");
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        
        // 4. Giải mã dữ liệu
        String message = new String(packet.getData(), 0, packet.getLength(),
            StandardCharsets.UTF_8);
        
        System.out.println("[Receiver] ✓ Nhận được Unicast!");
        System.out.println("[Receiver] Từ: " + packet.getAddress().getHostAddress() + 
            ":" + packet.getPort());
        System.out.println("[Receiver] Nội dung: " + message);
        
        socket.close();
    }
}
```

---

## 6. Broadcast - Gửi tất cả

### **6.1 Sender gửi Broadcast**

```java
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class BroadcastSender {
    public static void main(String[] args) throws Exception {
        // 1. Tạo DatagramSocket
        DatagramSocket socket = new DatagramSocket();
        
        // 2. **PHẢI bật setBroadcast(true)**
        socket.setBroadcast(true);
        System.out.println("[Sender] ✓ Đã bật cờ Broadcast");
        
        int port = 7000;
        
        // 3. Chuẩn bị thông điệp
        String message = "Thông báo: Kiểm tra hệ thống!";
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        
        // 4. Tạo packet gửi đến 255.255.255.255 (Broadcast)
        InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
        DatagramPacket packet = new DatagramPacket(data, data.length,
            broadcastAddress, port);
        
        System.out.println("[Sender] Đang gửi Broadcast tới 255.255.255.255:" + port);
        System.out.println("[Sender] Thông điệp: " + message);
        
        // 5. Gửi gói tin
        socket.send(packet);
        
        System.out.println("[Sender] ✓ Đã gửi Broadcast thành công!");
        System.out.println("[Sender] Lưu ý: TẤT CẢ máy trên mạng nhận được!");
        
        socket.close();
    }
}
```

### **6.2 Receiver nhận Broadcast**

```java
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.nio.charset.StandardCharsets;

public class BroadcastReceiver {
    public static void main(String[] args) throws Exception {
        int port = 7000;
        
        // 1. Tạo DatagramSocket lắng nghe port 7000
        DatagramSocket socket = new DatagramSocket(port);
        
        // 2. Cho phép nhận broadcast (không bắt buộc, nhưng tốt)
        socket.setBroadcast(true);
        System.out.println("[Receiver] Đang lắng nghe Broadcast tại port " + port);
        System.out.println("[Receiver] *** Broadcast: Tất cả máy trên mạng đều nhận ***");
        
        // 3. Chờ Broadcast
        byte[] buffer = new byte[1024];
        while (true) {
            System.out.println("[Receiver] Chờ dữ liệu...");
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            
            String message = new String(packet.getData(), 0, packet.getLength(),
                StandardCharsets.UTF_8);
            
            System.out.println("[Receiver] ✓ Nhận được Broadcast!");
            System.out.println("[Receiver] Từ: " + 
                packet.getAddress().getHostAddress());
            System.out.println("[Receiver] Nội dung: " + message);
            System.out.println("---");
        }
    }
}
```

---

## 7. Multicast - Gửi nhóm

### **7.1 Sender gửi Multicast**

```java
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class MulticastSender {
    public static void main(String[] args) throws Exception {
        // 1. Tạo DatagramSocket (KHÔNG phải MulticastSocket)
        DatagramSocket socket = new DatagramSocket();
        
        int port = 8000;
        
        // 2. Chuẩn bị thông điệp
        String message = "Thông báo cho nhóm Multicast 239.1.1.1";
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        
        // 3. Tạo packet gửi đến địa chỉ Multicast 239.1.1.1
        InetAddress multicastAddress = InetAddress.getByName("239.1.1.1");
        DatagramPacket packet = new DatagramPacket(data, data.length,
            multicastAddress, port);
        
        System.out.println("[Sender] Đang gửi Multicast tới 239.1.1.1:" + port);
        System.out.println("[Sender] Thông điệp: " + message);
        System.out.println("[Sender] Lưu ý: CHỈ máy joinGroup() mới nhận!");
        
        // 4. Gửi gói tin
        socket.send(packet);
        
        System.out.println("[Sender] ✓ Đã gửi Multicast thành công!");
        
        socket.close();
    }
}
```

### **7.2 Receiver nhận Multicast (với joinGroup)**

```java
import java.net.*;
import java.nio.charset.StandardCharsets;

public class MulticastReceiver {
    public static void main(String[] args) throws Exception {
        int port = 8000;
        String groupAddress = "239.1.1.1";
        
        System.out.println("[Receiver] === DEMO MULTICAST ===");
        System.out.println("[Receiver] Nhóm Multicast: " + groupAddress + ":" + port);
        System.out.println();
        
        // *** CHỨNG MINH KHÁC BIỆT: Hỏi người dùng có muốn joinGroup() không ***
        System.out.print("❓ Bạn có muốn gia nhập nhóm Multicast không? (Y/N): ");
        String choice = "Y";  // Hoặc lấy từ Scanner
        
        MulticastSocket socket = new MulticastSocket(port);
        socket.setReuseAddress(true);
        
        InetAddress group = InetAddress.getByName(groupAddress);
        
        if (choice.equalsIgnoreCase("Y")) {
            // TRƯỜNG HỢP 1: GỌI joinGroup()
            socket.joinGroup(group);
            System.out.println("[Receiver] ✓ ĐÃ gọi joinGroup() - Tham gia nhóm");
            System.out.println("[Receiver] Giờ sẽ nhận được thông điệp Multicast");
        } else {
            // TRƯỜNG HỢP 2: KHÔNG gọi joinGroup()
            System.out.println("[Receiver] ✗ KHÔNG gọi joinGroup() - Không tham gia");
            System.out.println("[Receiver] [CHỨNG MINH] " +
                "Ngay cả có Datagram gửi đến, sẽ KHÔNG nhận được!");
        }
        
        System.out.println("[Receiver] Đang lắng nghe tại port " + port + "...");
        
        // Chờ Multicast
        byte[] buffer = new byte[1024];
        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            
            try {
                socket.receive(packet);
                
                String message = new String(packet.getData(), 0, packet.getLength(),
                    StandardCharsets.UTF_8);
                
                System.out.println("[Receiver] ✓ NHẬN ĐƯỢC Multicast!");
                System.out.println("[Receiver] Từ: " + 
                    packet.getAddress().getHostAddress());
                System.out.println("[Receiver] Nội dung: " + message);
                System.out.println("---");
            } catch (Exception e) {
                if (choice.equalsIgnoreCase("N")) {
                    System.out.println("[Receiver] ⏱️ Chờ lâu mà không nhận..." +
                        " (Như dự đoán vì không joinGroup!)");
                }
                System.out.println("[Receiver] Lỗi: " + e.getMessage());
                break;
            }
        }
        
        socket.close();
    }
}
```

---

## 8. NetDemoSender - Chương trình gửi với Menu

```java
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class NetDemoSender {
    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║    UDP DEMO - SENDER (Chương trình gửi) ║");
        System.out.println("║   Unicast / Broadcast / Multicast      ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();
        
        BufferedReader console = new BufferedReader(
            new InputStreamReader(System.in));
        
        System.out.println("Chọn chế độ gửi:");
        System.out.println("[1] Gửi Unicast (1 máy cụ thể)");
        System.out.println("[2] Gửi Broadcast (Tất cả máy trên mạng)");
        System.out.println("[3] Gửi Multicast (Nhóm máy 239.1.1.1)");
        System.out.print("Lựa chọn (1-3): ");
        
        String choice = console.readLine().trim();
        
        if (choice.equals("1")) {
            sendUnicast(console);
        } else if (choice.equals("2")) {
            sendBroadcast(console);
        } else if (choice.equals("3")) {
            sendMulticast(console);
        } else {
            System.out.println("❌ Lựa chọn không hợp lệ!");
        }
    }
    
    // === UNICAST ===
    private static void sendUnicast(BufferedReader console) throws Exception {
        System.out.println("\n[MODE: UNICAST]");
        System.out.println("📤 Gửi thông điệp đến 1 máy cụ thể");
        System.out.println();
        
        System.out.print("Nhập IP máy nhận (VD: 192.168.1.2): ");
        String receiverIp = console.readLine().trim();
        
        System.out.print("Nhập thông điệp: ");
        String message = console.readLine().trim();
        
        try {
            DatagramSocket socket = new DatagramSocket();
            System.out.println("[Sender] ✓ Tạo UDP Unicast Socket");
            
            int port = 7000;
            InetAddress recipientAddress = InetAddress.getByName(receiverIp);
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            
            DatagramPacket packet = new DatagramPacket(data, data.length,
                recipientAddress, port);
            
            System.out.println("[Sender] 📤 Đang gửi Unicast tới " + 
                receiverIp + ":" + port);
            System.out.println("[Sender] Thông điệp: \"" + message + "\"");
            
            socket.send(packet);
            System.out.println("[Sender] ✓ Đã gửi Unicast thành công!");
            
            socket.close();
        } catch (Exception e) {
            System.out.println("❌ Lỗi: " + e.getMessage());
        }
    }
    
    // === BROADCAST ===
    private static void sendBroadcast(BufferedReader console) throws Exception {
        System.out.println("\n[MODE: BROADCAST]");
        System.out.println("📢 Gửi thông điệp đến TẤT CẢ máy trên mạng");
        System.out.println();
        
        System.out.print("Nhập thông điệp: ");
        String message = console.readLine().trim();
        
        try {
            DatagramSocket socket = new DatagramSocket();
            
            // *** BẮTBUỘC: setBroadcast(true) ***
            socket.setBroadcast(true);
            System.out.println("[Sender] ✓ Đã bật cờ Broadcast");
            
            int port = 7000;
            InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            
            DatagramPacket packet = new DatagramPacket(data, data.length,
                broadcastAddress, port);
            
            System.out.println("[Sender] 📢 Đang gửi Broadcast tới 255.255.255.255:" + port);
            System.out.println("[Sender] Thông điệp: \"" + message + "\"");
            System.out.println("[Sender] ⚠️  Tất cả máy trên mạng sẽ nhận!");
            
            socket.send(packet);
            System.out.println("[Sender] ✓ Đã gửi Broadcast thành công!");
            
            socket.close();
        } catch (Exception e) {
            System.out.println("❌ Lỗi: " + e.getMessage());
        }
    }
    
    // === MULTICAST ===
    private static void sendMulticast(BufferedReader console) throws Exception {
        System.out.println("\n[MODE: MULTICAST]");
        System.out.println("🎯 Gửi thông điệp đến nhóm (239.1.1.1)");
        System.out.println();
        
        System.out.print("Nhập thông điệp: ");
        String message = console.readLine().trim();
        
        try {
            DatagramSocket socket = new DatagramSocket();
            
            int port = 8000;
            InetAddress multicastAddress = InetAddress.getByName("239.1.1.1");
            byte[] data = message.getBytes(StandardCharsets.UTF_8);
            
            DatagramPacket packet = new DatagramPacket(data, data.length,
                multicastAddress, port);
            
            System.out.println("[Sender] 🎯 Đang gửi Multicast tới 239.1.1.1:" + port);
            System.out.println("[Sender] Thông điệp: \"" + message + "\"");
            System.out.println("[Sender] 💡 CHỈ máy joinGroup(239.1.1.1) mới nhận!");
            
            socket.send(packet);
            System.out.println("[Sender] ✓ Đã gửi Multicast thành công!");
            
            socket.close();
        } catch (Exception e) {
            System.out.println("❌ Lỗi: " + e.getMessage());
        }
    }
}
```

---

## 9. NetDemoReceiver - Chương trình nhận với Menu

```java
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class NetDemoReceiver {
    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║   UDP DEMO - RECEIVER (Chương trình nhận) ║");
        System.out.println("║   Unicast / Broadcast / Multicast      ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();
        
        BufferedReader console = new BufferedReader(
            new InputStreamReader(System.in));
        
        System.out.println("Chọn chế độ lắng nghe:");
        System.out.println("[1] Nhận Unicast");
        System.out.println("[2] Nhận Broadcast");
        System.out.println("[3] Nhận Multicast (với joinGroup)");
        System.out.print("Lựa chọn (1-3): ");
        
        String choice = console.readLine().trim();
        
        if (choice.equals("1")) {
            receiveUnicast();
        } else if (choice.equals("2")) {
            receiveBroadcast();
        } else if (choice.equals("3")) {
            receiveMulticast(console);
        } else {
            System.out.println("❌ Lựa chọn không hợp lệ!");
        }
    }
    
    // === UNICAST ===
    private static void receiveUnicast() throws Exception {
        int port = 7000;
        System.out.println("\n[MODE: UNICAST]");
        System.out.println("📥 Nhận thông điệp từ 1 máy cụ thể");
        System.out.println("[Receiver] Đang lắng nghe Unicast tại port " + port);
        
        DatagramSocket socket = new DatagramSocket(port);
        byte[] buffer = new byte[1024];
        
        System.out.println("[Receiver] Chờ dữ liệu Unicast...");
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        
        String message = new String(packet.getData(), 0, packet.getLength(),
            StandardCharsets.UTF_8);
        
        System.out.println("[Receiver] ✓ NHẬN ĐƯỢC Unicast!");
        System.out.println("[Receiver] Từ: " + packet.getAddress().getHostAddress() + 
            ":" + packet.getPort());
        System.out.println("[Receiver] Nội dung: \"" + message + "\"");
        
        socket.close();
    }
    
    // === BROADCAST ===
    private static void receiveBroadcast() throws Exception {
        int port = 7000;
        System.out.println("\n[MODE: BROADCAST]");
        System.out.println("📢 Nhận thông điệp từ TẤT CẢ máy trên mạng");
        System.out.println("[Receiver] Đang lắng nghe Broadcast tại port " + port);
        System.out.println("[Receiver] (Bất kỳ máy nào gửi tới 255.255.255.255 " +
            "đều được nhận)");
        
        DatagramSocket socket = new DatagramSocket(port);
        socket.setBroadcast(true);
        byte[] buffer = new byte[1024];
        
        System.out.println("[Receiver] Chờ dữ liệu Broadcast...");
        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            
            String message = new String(packet.getData(), 0, packet.getLength(),
                StandardCharsets.UTF_8);
            
            System.out.println("[Receiver] ✓ NHẬN ĐƯỢC Broadcast!");
            System.out.println("[Receiver] Từ: " + 
                packet.getAddress().getHostAddress());
            System.out.println("[Receiver] Nội dung: \"" + message + "\"");
            System.out.println("---");
        }
    }
    
    // === MULTICAST (VỚI JOINGROUP) ===
    private static void receiveMulticast(BufferedReader console) throws Exception {
        String groupAddress = "239.1.1.1";
        int port = 8000;
        
        System.out.println("\n[MODE: MULTICAST]");
        System.out.println("🎯 Nhận thông điệp từ nhóm (239.1.1.1)");
        System.out.println("[Receiver] Nhóm Multicast: " + groupAddress + ":" + port);
        System.out.println();
        
        // *** CHỨNG MINH: Hỏi có muốn joinGroup() không ***
        System.out.print("❓ Bạn có muốn gia nhập nhóm Multicast không? (Y/N): ");
        String choice = console.readLine().trim();
        
        System.out.println();
        
        MulticastSocket socket = new MulticastSocket(port);
        socket.setReuseAddress(true);
        InetAddress group = InetAddress.getByName(groupAddress);
        
        boolean joined = false;
        
        if (choice.equalsIgnoreCase("Y") || choice.equalsIgnoreCase("YES")) {
            // TRƯỜNG HỢP 1: GỌI joinGroup()
            socket.joinGroup(group);
            joined = true;
            System.out.println("[Receiver] ✓ ĐÃ gọi joinGroup()");
            System.out.println("[Receiver] Tham gia nhóm Multicast 239.1.1.1");
            System.out.println("[Receiver] Giờ sẽ nhận được thông điệp Multicast");
        } else {
            // TRƯỜNG HỢP 2: KHÔNG gọi joinGroup()
            System.out.println("[Receiver] ✗ KHÔNG gọi joinGroup()");
            System.out.println("[Receiver] ⚠️  [CHỨNG MINH KHÁC BIỆT]");
            System.out.println("[Receiver] Kể cả Datagram gửi đến cũng");
            System.out.println("[Receiver] KHÔNG thể nhận được!");
        }
        
        System.out.println();
        System.out.println("[Receiver] Đang lắng nghe Multicast tại port " + port + "...");
        
        byte[] buffer = new byte[1024];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                
                // Timeout 10 giây để demo
                socket.setSoTimeout(10000);
                socket.receive(packet);
                
                String message = new String(packet.getData(), 0, packet.getLength(),
                    StandardCharsets.UTF_8);
                
                System.out.println("[Receiver] ✓ NHẬN ĐƯỢC Multicast!");
                System.out.println("[Receiver] Từ: " + 
                    packet.getAddress().getHostAddress());
                System.out.println("[Receiver] Nội dung: \"" + message + "\"");
                System.out.println("---");
                
            } catch (SocketTimeoutException e) {
                System.out.println("[Receiver] ⏱️  Timeout - Không nhận được dữ liệu");
                
                if (!joined) {
                    System.out.println("[Receiver] 💡 Lý do: Vì bạn không gọi joinGroup()!");
                    System.out.println("[Receiver] Như vậy chứng minh được: ");
                    System.out.println("[Receiver] Broadcast ≠ Multicast!");
                    System.out.println("[Receiver]");
                    System.out.println("[Receiver] • Broadcast: Tất cả máy đều nhận");
                    System.out.println("[Receiver] • Multicast: Chỉ máy joinGroup() mới nhận");
                }
                break;
            }
        }
        
        socket.close();
    }
}
```

---

## 10. Hướng dẫn chạy Demo

### **Terminal 1 - Khởi động Receiver (chế độ Multicast)**
```bash
$ javac NetDemoReceiver.java
$ java NetDemoReceiver
╔════════════════════════════════════════╗
║   UDP DEMO - RECEIVER (Chương trình nhận) ║
║   Unicast / Broadcast / Multicast      ║
╚════════════════════════════════════════╝

Chọn chế độ lắng nghe:
[1] Nhận Unicast
[2] Nhận Broadcast
[3] Nhận Multicast (với joinGroup)
Lựa chọn (1-3): 3

[MODE: MULTICAST]
🎯 Nhận thông điệp từ nhóm (239.1.1.1)
[Receiver] Nhóm Multicast: 239.1.1.1:8000

❓ Bạn có muốn gia nhập nhóm Multicast không? (Y/N): N

[Receiver] ✗ KHÔNG gọi joinGroup()
[Receiver] ⚠️  [CHỨNG MINH KHÁC BIỆT]
[Receiver] Kể cả Datagram gửi đến cũng
[Receiver] KHÔNG thể nhận được!

[Receiver] Đang lắng nghe Multicast tại port 8000...
```

### **Terminal 2 - Khởi động Sender (chế độ Multicast)**
```bash
$ javac NetDemoSender.java
$ java NetDemoSender
╔════════════════════════════════════════╗
║    UDP DEMO - SENDER (Chương trình gửi) ║
║   Unicast / Broadcast / Multicast      ║
╚════════════════════════════════════════╝

Chọn chế độ gửi:
[1] Gửi Unicast (1 máy cụ thể)
[2] Gửi Broadcast (Tất cả máy trên mạng)
[3] Gửi Multicast (Nhóm máy 239.1.1.1)
Lựa chọn (1-3): 3

[MODE: MULTICAST]
🎯 Gửi thông điệp đến nhóm (239.1.1.1)

Nhập thông điệp: Xin chào nhóm Multicast!

[Sender] 🎯 Đang gửi Multicast tới 239.1.1.1:8000
[Sender] Thông điệp: "Xin chào nhóm Multicast!"
[Sender] 💡 CHỈ máy joinGroup(239.1.1.1) mới nhận!
[Sender] ✓ Đã gửi Multicast thành công!
```

### **Kết quả - Terminal 1 (Receiver không joinGroup)**
```
[Receiver] ⏱️  Timeout - Không nhận được dữ liệu
[Receiver] 💡 Lý do: Vì bạn không gọi joinGroup()!
[Receiver] Như vậy chứng minh được: 
[Receiver] Broadcast ≠ Multicast!

[Receiver] • Broadcast: Tất cả máy đều nhận
[Receiver] • Multicast: Chỉ máy joinGroup() mới nhận
```

✅ **CHỨNG MINH THÀNH CÔNG!**

---

## 11. Tóm tắt so sánh

```
UNICAST:
  Sender → chỉ định IP receiver
  Receiver → chỉ lắng nghe port
  ✓ Chỉ 1 máy nhận

BROADCAST:
  Sender → setBroadcast(true), gửi 255.255.255.255
  Receiver → chỉ lắng nghe port
  ✓ Tất cả máy trên mạng nhận

MULTICAST (KHÁC BIỆT):
  Sender → gửi tới 239.1.1.1 (hoặc IP Multicast khác)
  Receiver → PHẢI gọi joinGroup()
  ✓ Chỉ máy joinGroup() mới nhận
  ✓ Máy không joinGroup() KHÔNG nhận (CHỨNG MINH)
```

---

**Bài học hoàn tất! 🎓**
