# UDP Broadcast vs Multicast trong Java

> Tổng hợp từ Chương 9 (UDP Broadcast) và Chương 10 (UDP Multicast) của bài giảng Lập trình mạng Java, kèm 2 demo chạy được trên nhiều máy trong cùng WiFi.

---

## 1. Phân biệt nhanh

| Tiêu chí | Unicast | Broadcast | Multicast |
|---|---|---|---|
| Gửi tới | 1 máy | **Mọi** máy trong LAN | Chỉ máy **đã tham gia nhóm** |
| Địa chỉ đích | IP của máy nhận | `255.255.255.255` hoặc `x.x.x.255` | Lớp D: `224.0.0.0 – 239.255.255.255` (bài dùng `239.1.1.1`) |
| Máy nhận cần làm gì? | Không | Không (chỉ cần mở cổng) | **Phải `joinGroup`** |
| Lớp Java gửi | `DatagramSocket` | `DatagramSocket` + `setBroadcast(true)` | `MulticastSocket` (+ `setTimeToLive`) |
| Lớp Java nhận | `DatagramSocket` | `DatagramSocket` | `MulticastSocket` + `joinGroup` |
| Qua Router? | Có | Thường **không** | Có thể, nếu TTL > 1 và router hỗ trợ |
| Lưu lượng thừa | Không | **Nhiều** (máy không quan tâm cũng phải xử lý) | Ít |
| Dùng khi | Chat 1-1 | Thông báo / **tìm kiếm** thiết bị trong LAN | Phát cho một nhóm (lớp học, stream, chat nhóm) |

**Cách nhớ:**
- **Broadcast** = hét cho cả phòng nghe, ai ở trong phòng cũng nghe.
- **Multicast** = nói trong group chat, ai vào group mới nghe.

**Hai điểm khác biệt trong code (quan trọng nhất):**
1. Broadcast: sender gọi `socket.setBroadcast(true)`, receiver **không cần** làm gì thêm.
2. Multicast: receiver phải `joinGroup(...)`, sender dùng địa chỉ nhóm lớp D.

---

## 2. Broadcast

### 2.1 Hai loại địa chỉ broadcast

- **Limited broadcast:** `255.255.255.255`. Chỉ ở mạng hiện tại, không bao giờ qua router.
- **Directed (subnet) broadcast:** ví dụ mạng `192.168.1.0/24` → `192.168.1.255`.

Lưu ý khi chạy thật: máy có nhiều card mạng (WiFi, VMware, WSL, Docker...) thì `255.255.255.255` có thể bị gửi ra **nhầm card**. Cách chắc ăn là gửi tới broadcast của từng card (có ở demo bên dưới).

### 2.2 Receiver

```java
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

public class BroadcastReceiver {
    public static void main(String[] args) throws Exception {
        int port = 7000;
        // Bind cổng, không bind IP cụ thể để nhận được broadcast
        try (DatagramSocket socket = new DatagramSocket(port)) {
            socket.setBroadcast(true);
            byte[] buffer = new byte[1024];
            System.out.println("Đang chờ broadcast tại cổng " + port + "...");

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // chờ tới khi có 1 datagram

                String msg = new String(packet.getData(), 0, packet.getLength(),
                        StandardCharsets.UTF_8);
                System.out.println("[" + packet.getAddress().getHostAddress()
                        + ":" + packet.getPort() + "] " + msg);
            }
        }
    }
}
```

Giải thích từng bước:
1. `new DatagramSocket(7000)`: mở socket UDP và gắn vào cổng 7000 (cổng quy ước giữa sender và receiver).
2. `receive(packet)`: dừng (block) cho tới khi có datagram tới.
3. `packet.getLength()`: dùng độ dài thực, không dùng `buffer.length`, nếu không sẽ bị rác ở đuôi chuỗi.
4. Giải mã bằng UTF-8 để tiếng Việt không bị lỗi.

### 2.3 Sender

```java
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

public class BroadcastSender {
    public static void main(String[] args) throws Exception {
        byte[] data = "Thông báo từ máy giảng viên".getBytes(StandardCharsets.UTF_8);

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true); // BẮT BUỘC, thiếu là lỗi "Permission denied"

            InetAddress addr = InetAddress.getByName("255.255.255.255");
            DatagramPacket packet = new DatagramPacket(data, data.length, addr, 7000);
            socket.send(packet);
            System.out.println("Đã gửi broadcast");
        }
    }
}
```

### 2.4 Mẫu "Broadcast để tìm, Unicast để trả lời"

Đây là pattern kinh điển để tự tìm peer: máy mới vào mạng chưa biết IP ai, nên **broadcast** hỏi "ai đang online?". Các máy kia **unicast** trả lời thẳng về địa chỉ + cổng lấy từ `packet.getAddress()` / `packet.getPort()`.

---

## 3. Multicast

### 3.1 Địa chỉ nhóm

- Dải lớp D: `224.0.0.0 – 239.255.255.255`.
- `239.x.x.x` là dải dùng nội bộ (administratively scoped), an toàn để làm bài.
- Endpoint nhóm = `239.1.1.1:8000` → **IP định nhóm, Port định ứng dụng**.
- Địa chỉ nhóm không thuộc về máy nào. Nó chỉ là "tên phòng".

### 3.2 Receiver (tham gia nhóm)

```java
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;

public class MulticastReceiver {
    public static void main(String[] args) throws Exception {
        InetAddress group = InetAddress.getByName("239.1.1.1");
        InetSocketAddress groupAddr = new InetSocketAddress(group, 8000);

        // Thay bằng IP WiFi của máy bạn (xem ipconfig / ip a).
        // KHÔNG để null: null = hệ điều hành tự chọn, dễ rơi vào card ảo.
        NetworkInterface nif = NetworkInterface.getByInetAddress(
                InetAddress.getByName("192.168.1.5"));

        try (MulticastSocket socket = new MulticastSocket(8000)) {
            socket.joinGroup(groupAddr, nif); // THAM GIA NHÓM
            System.out.println("Đã tham gia nhóm 239.1.1.1:8000");

            byte[] buffer = new byte[1024];
            try {
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength(),
                            StandardCharsets.UTF_8);
                    System.out.println("[" + packet.getAddress().getHostAddress() + "] " + msg);
                }
            } finally {
                socket.leaveGroup(groupAddr, nif); // RỜI NHÓM (tương đương dropGroup trong slide)
            }
        }
    }
}
```

### 3.3 Sender

```java
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;

public class MulticastSender {
    public static void main(String[] args) throws Exception {
        byte[] data = "Thông báo cho nhóm học tập".getBytes(StandardCharsets.UTF_8);
        InetAddress group = InetAddress.getByName("239.1.1.1");

        try (MulticastSocket socket = new MulticastSocket()) {
            // Chỉ định card WiFi (IP của máy bạn), nếu không gói có thể đi ra card ảo
            socket.setNetworkInterface(NetworkInterface.getByInetAddress(
                    InetAddress.getByName("192.168.1.5")));
            socket.setTimeToLive(1); // 1 = chỉ trong LAN, không qua router
            DatagramPacket packet = new DatagramPacket(data, data.length, group, 8000);
            socket.send(packet);
            System.out.println("Đã gửi multicast");
        }
    }
}
```

> Sender **không cần** `joinGroup`. Chỉ ai muốn **nhận** mới phải join.

**TTL là gì?** Mỗi lần qua một router, TTL giảm 1; về 0 thì gói bị bỏ. `TTL = 1` nghĩa là chỉ ở trong mạng nội bộ.

---

## 4. Những chỗ trong slide cần sửa khi gõ thật

| Trong slide | Vấn đề | Cách sửa |
|---|---|---|
| Ch.10 mục 4: `DatagramSocket.setTimeToLive(1)` | `DatagramSocket` **không có** method này, không compile | Dùng `MulticastSocket.setTimeToLive(1)` |
| `joinGroup(InetAddress)` | Bị deprecated từ JDK 14 | Dùng `joinGroup(SocketAddress, NetworkInterface)` |
| Ch.10 mục 7: `try { } finally { }` không có `catch`/`throws` | Không compile vì `joinGroup`, `getByName` ném checked exception | Thêm `throws Exception` ở `main` |
| Ch.9 mục 7: `DatagramPacket packet = new DatagramPacket(..., packet.getAddress(), ...)` | Dùng chính biến `packet` đang khai báo, lỗi | Đặt tên khác, ví dụ `reply` |

---

## 5. Demo hoàn chỉnh

### 5.1 Demo A: Tìm peer bằng Broadcast, trả lời bằng Unicast

File `PeerDiscovery.java`. Mỗi máy chạy 1 bản, gõ `find` để tìm các máy còn lại.

```java
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class PeerDiscovery {
    static final int PORT = 7000;
    static final String DISCOVER = "DISCOVER_CHAT";
    static final String REPLY = "CHAT_PEER|";

    public static void main(String[] args) throws Exception {
        String name = args.length > 0 ? args[0] : InetAddress.getLocalHost().getHostName();
        Set<String> myIps = localIps();

        DatagramSocket socket = new DatagramSocket(null);
        socket.setReuseAddress(true);          // cho phép chạy nhiều bản trên 1 máy để test
        socket.bind(new InetSocketAddress(PORT));
        socket.setBroadcast(true);

        Thread listener = new Thread(() -> listen(socket, name, myIps));
        listener.setDaemon(true);
        listener.start();

        System.out.println("Tên: " + name + " | gõ 'find' để tìm peer, 'quit' để thoát");
        Scanner sc = new Scanner(System.in);
        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.equals("quit")) break;
            if (line.equals("find")) sendDiscover(socket);
        }
        socket.close();
    }

    // Luồng nhận: xử lý cả lời hỏi (broadcast) lẫn lời đáp (unicast)
    static void listen(DatagramSocket socket, String name, Set<String> myIps) {
        byte[] buf = new byte[1024];
        try {
            while (!socket.isClosed()) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                String ip = p.getAddress().getHostAddress();

                if (msg.equals(DISCOVER)) {
                    if (myIps.contains(ip)) continue;          // bỏ qua broadcast của chính mình
                    byte[] d = (REPLY + name).getBytes(StandardCharsets.UTF_8);
                    DatagramPacket reply = new DatagramPacket(d, d.length,
                            p.getAddress(), p.getPort());      // UNICAST về người hỏi
                    socket.send(reply);
                    System.out.println("[Có người tìm] " + ip);
                } else if (msg.startsWith(REPLY)) {
                    System.out.println("[Tìm thấy peer] " + msg.substring(REPLY.length())
                            + " @ " + ip);
                }
            }
        } catch (Exception e) {
            if (!socket.isClosed()) e.printStackTrace();
        }
    }

    // Gửi DISCOVER tới địa chỉ broadcast của TỪNG card mạng
    static void sendDiscover(DatagramSocket socket) throws Exception {
        byte[] d = DISCOVER.getBytes(StandardCharsets.UTF_8);
        int sent = 0;
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback()) continue;
            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                InetAddress bc = ia.getBroadcast();            // IPv6 thì null
                if (bc == null) continue;
                socket.send(new DatagramPacket(d, d.length, bc, PORT));
                System.out.println("Đã broadcast tới " + bc.getHostAddress()
                        + " (" + ni.getDisplayName() + ")");
                sent++;
            }
        }
        if (sent == 0) System.out.println("Không có card mạng nào có địa chỉ broadcast!");
    }

    static Set<String> localIps() throws SocketException {
        Set<String> s = new HashSet<>();
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces()))
            for (InetAddress a : Collections.list(ni.getInetAddresses()))
                s.add(a.getHostAddress());
        return s;
    }
}
```

**Luồng hoạt động:**
1. Máy A gõ `find` → gửi `DISCOVER_CHAT` tới `x.x.x.255:7000` (**broadcast**).
2. Mọi máy trong WiFi có chương trình đang mở cổng 7000 đều nhận.
3. Mỗi máy B, C tự trả `CHAT_PEER|tên` thẳng về IP:port của A (**unicast**).
4. A in ra danh sách peer tìm được.

### 5.2 Demo B: Chat nhóm bằng Multicast

File `MulticastChat.java`. Ai chạy là vào chung "phòng" `239.1.1.1:8000`.

```java
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MulticastChat {
    static final String GROUP = "239.1.1.1";
    static final int PORT = 8000;

    public static void main(String[] args) throws Exception {
        String nick = args.length > 0 ? args[0] : "user" + new Random().nextInt(100);
        String wanted = args.length > 1 ? args[1] : null;   // tên card mạng, tuỳ chọn

        InetAddress group = InetAddress.getByName(GROUP);
        InetSocketAddress groupAddr = new InetSocketAddress(group, PORT);
        NetworkInterface nif = pickInterface(wanted);
        System.out.println("Dùng card: " + nif.getDisplayName());

        MulticastSocket socket = new MulticastSocket(PORT);
        socket.setNetworkInterface(nif);   // gửi ra đúng card WiFi
        socket.setTimeToLive(1);           // chỉ trong LAN
        socket.joinGroup(groupAddr, nif);  // vào phòng

        Thread receiver = new Thread(() -> {
            byte[] buf = new byte[1024];
            try {
                while (!socket.isClosed()) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    socket.receive(p);
                    String msg = new String(p.getData(), 0, p.getLength(),
                            StandardCharsets.UTF_8);
                    System.out.println(msg + "   (" + p.getAddress().getHostAddress() + ")");
                }
            } catch (Exception e) {
                if (!socket.isClosed()) e.printStackTrace();
            }
        });
        receiver.setDaemon(true);
        receiver.start();

        send(socket, groupAddr, "*** " + nick + " đã vào phòng ***");
        System.out.println("Gõ tin nhắn rồi Enter. Gõ /quit để rời phòng.");

        Scanner sc = new Scanner(System.in);
        try {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (line.equals("/quit")) break;
                if (!line.isEmpty()) send(socket, groupAddr, nick + ": " + line);
            }
        } finally {
            send(socket, groupAddr, "*** " + nick + " đã rời phòng ***");
            socket.leaveGroup(groupAddr, nif);   // rời nhóm
            socket.close();
        }
    }

    static void send(MulticastSocket s, InetSocketAddress to, String text) throws Exception {
        byte[] d = text.getBytes(StandardCharsets.UTF_8);
        s.send(new DatagramPacket(d, d.length, to));
    }

    // In ra các card khả dụng và chọn card WiFi.
    // wanted: tên card, một phần tên hiển thị, hoặc IP WiFi của máy (vd 192.168.1.5). null = tự chọn.
    static NetworkInterface pickInterface(String wanted) throws SocketException {
        String[] bad = {"vmware", "virtualbox", "vethernet", "hyper-v", "docker", "wsl",
                "tap", "tun", "vpn", "bluetooth", "virtual"};
        String[] good = {"wi-fi", "wifi", "wlan", "wireless", "en0"};
        List<NetworkInterface> cands = new ArrayList<>();

        System.out.println("Các card mạng khả dụng:");
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback() || !ni.supportsMulticast()) continue;
            String ip = null;
            for (InetAddress a : Collections.list(ni.getInetAddresses()))
                if (a instanceof Inet4Address) ip = a.getHostAddress();
            if (ip == null) continue;

            System.out.println("  - " + ni.getName() + " | " + ni.getDisplayName() + " | " + ip);
            cands.add(ni);
            if (wanted != null && (ni.getName().equalsIgnoreCase(wanted)
                    || ip.equals(wanted)
                    || ni.getDisplayName().toLowerCase().contains(wanted.toLowerCase())))
                return ni;
        }
        if (wanted != null) throw new SocketException("Không thấy card: " + wanted);
        if (cands.isEmpty()) throw new SocketException("Không có card mạng phù hợp");

        // Tự chọn: bỏ card ảo, ưu tiên card có tên giống WiFi
        NetworkInterface fallback = null;
        for (NetworkInterface ni : cands) {
            String n = (ni.getName() + " " + ni.getDisplayName()).toLowerCase();
            if (containsAny(n, bad)) continue;
            if (containsAny(n, good)) return ni;
            if (fallback == null) fallback = ni;
        }
        return fallback != null ? fallback : cands.get(0);
    }

    static boolean containsAny(String s, String[] keys) {
        for (String k : keys) if (s.contains(k)) return true;
        return false;
    }
}
```

Tin nhắn của chính mình cũng hiện lại vì multicast mặc định có loopback. Đây là bình thường, không phải lỗi.

### 5.3 Demo C: Chat nhiều nhóm, hỏi người dùng có vào nhóm không

File `MulticastGroupChat.java`. Khi chạy chương trình hỏi *"Bạn có muốn vào nhóm không?"*, nếu có thì chọn nhóm 1/2/3 (mỗi nhóm một địa chỉ multicast riêng). Ai chưa vào nhóm nào thì không nhận gì.

Dùng chung hàm `pickInterface` của `MulticastChat`, nên biên dịch hai file cùng lúc.

```java
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MulticastGroupChat {
    static final int PORT = 8000;
    static final String[] GROUPS = {"239.1.1.1", "239.1.1.2", "239.1.1.3"};

    static MulticastSocket socket;
    static NetworkInterface nif;
    static String nick;
    static final Set<Integer> joined = Collections.synchronizedSet(new LinkedHashSet<>());
    static int current = -1;   // nhóm đang gửi tin tới

    public static void main(String[] args) throws Exception {
        nick = args.length > 0 ? args[0] : "user" + new Random().nextInt(100);
        nif = MulticastChat.pickInterface(args.length > 1 ? args[1] : null);
        System.out.println("Dùng card: " + nif.getDisplayName());

        socket = new MulticastSocket(PORT);
        socket.setNetworkInterface(nif);
        socket.setTimeToLive(1);

        Thread rx = new Thread(MulticastGroupChat::receiveLoop);
        rx.setDaemon(true);
        rx.start();

        Scanner sc = new Scanner(System.in);
        System.out.print("Bạn có muốn vào nhóm không? (y/n): ");
        if (sc.nextLine().trim().equalsIgnoreCase("y")) {
            printGroups();
            System.out.print("Chọn nhóm: ");
            join(parseGroup(sc.nextLine()));
        }
        System.out.println("Lệnh: /groups  /join N  /leave N  /to N  /quit. Gõ chữ thường để chat.");

        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            if (line.equals("/quit")) break;
            if (line.equals("/groups")) { printGroups(); continue; }
            if (line.startsWith("/join "))  { join(parseGroup(line.substring(6)));  continue; }
            if (line.startsWith("/leave ")) { leave(parseGroup(line.substring(7))); continue; }
            if (line.startsWith("/to ")) {
                int g = parseGroup(line.substring(4));
                if (g >= 0 && joined.contains(g)) {
                    current = g;
                    System.out.println("Đang chat ở nhóm " + (g + 1));
                } else if (g >= 0) {
                    System.out.println("Bạn chưa vào nhóm " + (g + 1));
                }
                continue;
            }
            if (current < 0) {
                System.out.println("Bạn chưa vào nhóm nào, gõ /join N");
                continue;
            }
            send(current, nick + ": " + line);
        }
        for (int g : new ArrayList<>(joined)) leave(g);
        socket.close();
    }

    static void join(int g) throws Exception {
        if (g < 0) return;
        if (joined.contains(g)) { System.out.println("Bạn đã ở nhóm " + (g + 1)); return; }
        socket.joinGroup(new InetSocketAddress(GROUPS[g], PORT), nif);   // VÀO NHÓM
        joined.add(g);
        current = g;
        send(g, "*** " + nick + " đã vào nhóm ***");
    }

    static void leave(int g) throws Exception {
        if (g < 0) return;
        if (!joined.contains(g)) { System.out.println("Bạn chưa ở nhóm " + (g + 1)); return; }
        send(g, "*** " + nick + " đã rời nhóm ***");
        socket.leaveGroup(new InetSocketAddress(GROUPS[g], PORT), nif);  // RỜI NHÓM
        joined.remove(g);
        if (current == g) {
            current = -1;
            for (int x : new ArrayList<>(joined)) current = x;
        }
    }

    // Gói tin có dạng "chỉ_số_nhóm|nội dung". Phải gắn nhãn nhóm vì cùng 1 socket/cổng
    // có thể nhận gói của nhóm khác (nếu chương trình khác trên máy đã join nhóm đó).
    static void send(int g, String text) throws Exception {
        byte[] d = (g + "|" + text).getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(d, d.length, InetAddress.getByName(GROUPS[g]), PORT));
    }

    static void receiveLoop() {
        byte[] buf = new byte[1024];
        try {
            while (!socket.isClosed()) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                int bar = msg.indexOf('|');
                if (bar < 0) continue;
                int g;
                try { g = Integer.parseInt(msg.substring(0, bar)); }
                catch (NumberFormatException e) { continue; }
                if (!joined.contains(g)) continue;      // không ở nhóm này thì bỏ qua
                System.out.println("[Nhóm " + (g + 1) + "] " + msg.substring(bar + 1));
            }
        } catch (Exception e) {
            if (!socket.isClosed()) e.printStackTrace();
        }
    }

    static int parseGroup(String s) {
        try {
            int n = Integer.parseInt(s.trim());
            if (n >= 1 && n <= GROUPS.length) return n - 1;
        } catch (NumberFormatException ignored) { }
        System.out.println("Nhóm không hợp lệ (1-" + GROUPS.length + ")");
        return -1;
    }

    static void printGroups() {
        for (int i = 0; i < GROUPS.length; i++)
            System.out.println("  " + (i + 1) + ". Nhóm " + (i + 1) + " (" + GROUPS[i] + ")"
                    + (joined.contains(i) ? "  <- đã vào" : ""));
    }
}
```

**Test nhanh với 3 máy A, B, C:**
1. A chọn `y` rồi vào nhóm 1. B chọn `y` rồi vào nhóm 2. C chọn `n`.
2. A chat → chỉ A thấy (B ở nhóm khác, C chưa vào nhóm nào).
3. B gõ `/join 1` → B vào thêm nhóm 1, từ giờ nhận tin của A. Gõ `/to 1` để gửi vào nhóm 1.
4. C gõ `/join 1` → C bắt đầu nhận tin nhóm 1. Gõ `/leave 1` → C ngừng nhận.

Với Broadcast thì không có bước nào ở trên: ai mở cổng là nhận, không chọn, không rời.

---

## 6. Chạy thử trên nhiều máy cùng WiFi

### 6.1 Biên dịch & chạy

```bash
javac PeerDiscovery.java MulticastChat.java MulticastGroupChat.java

# Demo A (mỗi máy 1 bản)
java PeerDiscovery MayCuaVy

# Demo B (mỗi máy 1 bản)
java MulticastChat Vy
# Demo C (hỏi vào nhóm, chọn nhóm 1/2/3)
java MulticastGroupChat Vy 192.168.1.5

# Chương trình sẽ in danh sách card + IP. Nếu chọn sai, chỉ định card bằng
# tên hoặc IP WiFi của máy (cách chắc nhất, xem bằng ipconfig / ip a):
java MulticastChat Vy 192.168.1.5
```

Xem tên card mạng: Windows `ipconfig /all`, Linux/Mac `ip a` hoặc `ifconfig`. Có thể thêm đoạn in `NetworkInterface.getNetworkInterfaces()` để liệt kê tên.

### 6.2 Kịch bản test để thấy rõ khác biệt

Dùng 3 máy A, B, C:

| Bước | Hành động | Kết quả mong đợi |
|---|---|---|
| 1 | A, B, C chạy `PeerDiscovery`. A gõ `find` | A thấy cả B và C (**broadcast tới tất cả**) |
| 2 | A, B chạy `MulticastChat`. C **không** chạy | A, B chat được với nhau, C không nhận gì |
| 3 | C chạy `MulticastChat` | Từ giờ C mới nhận tin (**phải join mới nghe được**) |
| 4 | B gõ `/quit` | A và C vẫn chat, B ngừng nhận (đã `leaveGroup`) |

Bước 2 là chỗ phân biệt rõ nhất: C vẫn ở trong WiFi nhưng không nhận multicast vì chưa join.

### 6.3 Nếu không chạy được (rất hay gặp trên WiFi)

1. **Firewall chặn.** Windows hay đặt WiFi là mạng *Public* và chặn UDP vào. Cho phép Java (`java.exe`) hoặc mở UDP 7000 và 8000.
2. **AP / Client Isolation.** Một số router hoặc hotspot chặn các máy trong WiFi nói chuyện với nhau. Kiểm tra bằng cách `ping` IP máy kia trước. Không ping được thì broadcast/multicast cũng chắc chắn không được.
3. **Hotspot điện thoại có thể không forward broadcast/multicast** (tuỳ máy và hãng). Nếu máy này nhận, máy kia không, thử đổi sang router WiFi thật.
4. **Sai card mạng.** Máy có nhiều card ảo thì chỉ định tên card cho `MulticastChat`. Với broadcast thì dùng cách gửi theo từng card như `PeerDiscovery`.
5. **Multicast trên WiFi hay mất gói.** WiFi gửi multicast ở tốc độ thấp và không có ACK. Đợi vài giây hoặc gửi lại, không phải lúc nào cũng do code sai.
6. **Tắt VPN** khi test.
7. Chạy nhiều bản trên **cùng 1 máy** thì cần `setReuseAddress(true)` (đã có sẵn trong demo).

---

## 7. Tóm tắt để ôn

- **Broadcast**: `DatagramSocket` + `setBroadcast(true)` + gửi tới `255.255.255.255` / `x.x.x.255`. Mọi máy trong LAN nhận, không cần đăng ký. Dùng để **thông báo / tìm thiết bị**.
- **Multicast**: `MulticastSocket` + địa chỉ lớp D (`239.1.1.1`) + `joinGroup` để nhận + `setTimeToLive` để giới hạn phạm vi. Chỉ thành viên nhóm nhận. Dùng cho **nhóm**.
- **Pattern kinh điển**: Broadcast để hỏi, Unicast để đáp.
- Cả hai đều dùng **UDP**: không đảm bảo thứ tự, không đảm bảo tới nơi, không có kết nối.
