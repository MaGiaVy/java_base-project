# Kế hoạch triển khai: Chọn nhóm Multicast & Chuẩn hóa toàn bộ Chữ Đen (UI & Logic)

> **Dành cho Agentic Workers:** Thực thi kế hoạch này theo từng bước kiểm thử cụ thể. Các bước sử dụng cú pháp checkbox (`- [ ]`).

**Mục tiêu:** Cung cấp tính năng chọn nhóm Multicast (Join/Leave) linh hoạt trên `UnifiedChatGUI`, sửa toàn bộ chữ trên giao diện thành chữ đen đậm nét trên nền pastel tương phản cao, khắc phục triệt để lỗi gửi trùng lặp broadcast và đồng bộ toàn bộ các file GUI trong dự án.

**Kiến trúc:** 
1. `UnifiedChatGUI.java`: Bổ sung ComboBox chọn nhóm Multicast (hỗ trợ nhập IP tùy ý) + Nút Bật/Tắt "Tham gia nhóm / Rời nhóm" động. Thêm bộ đệm `receivedMsgIds` để chống duplicate packet. Phân biệt rõ kênh `[BCAST]` và `[MCAST]` trong payload. Chuyển đổi toàn bộ style HTML và Component sang chữ đen trên nền pastel.
2. `NetworkDemo.java`: Đổi `C_TEXT = Color.BLACK`, chuyển các nút sang nền pastel có viền rõ ràng.
3. `BroadcastSenderGUI.java`, `BroadcastReceiverGUI.java`, `MulticastSenderGUI.java`, `MulticastReceiverGUI.java`: Đổi chữ toàn bộ nút sang chữ đen, nền pastel, sửa lỗi `MulticastSocket` bind trước khi set reuse address.

---

### Task 1: Cải tiến `UnifiedChatGUI.java` - Tính năng chọn nhóm Multicast và Chữ Đen

**Files:**
- Modify: `UnifiedChatGUI.java`

- [ ] **Bước 1.1: Bổ sung ComboBox nhóm Multicast và Nút Tham gia / Rời nhóm trên giao diện**
  - Thay `txtMcastIP` bằng `JComboBox<String> cbMcastGroup` có sẵn các nhóm:
    * `239.1.1.1` (Nhóm 1 - Mặc định)
    * `239.1.1.2` (Nhóm 2)
    * `239.1.1.3` (Nhóm 3)
    * `239.255.0.1` (Nhóm VIP)
  - Đặt `cbMcastGroup.setEditable(true)` để người dùng có thể tự gõ IP Multicast bất kỳ.
  - Thêm nút `btnMcastToggle` kế bên: hiển thị `🚪 Rời nhóm` (nền pastel cam `#FFE0B2`, chữ đen) khi đang ở trong nhóm, hoặc `✅ Vào nhóm` (nền pastel tím `#E1BEE7`, chữ đen) khi đã rời nhóm.

- [ ] **Bước 1.2: Triển khai logic Join / Leave Multicast Group linh hoạt khi ứng dụng đang chạy**
  - Khai báo biến trạng thái `private volatile boolean inMcastGroup = false;` và `private InetAddress currentMcastGroup = null;`.
  - Viết hàm `joinMulticastGroup(String groupIP)`: kiểm tra IP hợp lệ, nếu đang ở nhóm khác thì rời nhóm cũ trước, sau đó `mcastSocket.joinGroup(mcastGroupAddress, currentCard.nif)`, cập nhật `inMcastGroup = true`, cập nhật giao diện và in thông báo hệ thống.
  - Viết hàm `leaveMulticastGroup()`: gọi `mcastSocket.leaveGroup(...)`, cập nhật `inMcastGroup = false`, cập nhật nút bấm sang "✅ Vào nhóm", thông báo: *"🚪 Đã rời nhóm Multicast. Hiện tại bạn SẼ KHÔNG nhận được tin nhắn Multicast, nhưng VẪN nhận được tin Broadcast gửi toàn mạng."*
  - Bắt sự kiện khi người dùng chọn nhóm mới trong `cbMcastGroup`: tự động chuyển sang nhóm mới nếu đang ở trạng thái tham gia.

- [ ] **Bước 1.3: Sửa lỗi gửi trùng lặp Broadcast & Phân biệt rõ kênh truyền**
  - Trong `sendBroadcast()`: Chỉ gửi tới 1 địa chỉ broadcast tốt nhất (ưu tiên `currentCard.broadcastIp`, nếu không có thì dùng `255.255.255.255`), không lặp gửi nhiều địa chỉ gây nhân đôi tin nhắn.
  - Thêm `ConcurrentHashMap<String, Long> receivedMsgIds`: Khi nhận tin nhắn, nếu `receivedMsgIds.putIfAbsent(incomingId, now) != null` thì lập tức `return;` bỏ qua tin trùng lặp.
  - Gắn tag định danh kênh vào payload:
    * Broadcast: `#MSG#<msgId>#[BCAST]#[Nick]: [Text]`
    * Multicast: `#MSG#<msgId>#[MCAST]#[Nick]: [Text]`
  - Trong `handleIncomingPacket`: Nhận diện chính xác 100% kênh `BROADCAST` hay `MULTICAST` qua tag này, kể cả khi dùng chung port.

- [ ] **Bước 1.4: Chuyển đổi toàn bộ chữ sang MÀU ĐEN (Black Text) trên nền Pastel**
  - Tất cả nút bấm (`btnConnect`, `btnSend`, `btnPing`, `btnClear`, `btnMcastToggle`):
    * Đặt `setForeground(Color.BLACK)` và `setFont(new Font("SansSerif", Font.BOLD, 12/13))`.
    * Đổi nền sang pastel: Xanh kết nối `#C8E6C9`, Đỏ ngắt `#FFCDD2`, Gửi `#BBDEFB`, Ping `#FFF9C4`, Xóa `#EEEEEE`. Thêm viền `BorderFactory.createLineBorder(...)` đậm nét.
  - Khung HTML hiển thị chat (`appendMyMessage`, `appendReceivedMessage`, `appendSystemMessage`):
    * Badge kênh: Chữ màu đen `color: #000000; font-weight: bold; border: 1px solid #757575;`, nền pastel nhạt.
    * Thời gian `[%s]`: Đổi thành `#212121` (đen đậm).
    * Tên người gửi và IP: Chữ màu đen/tối rõ nét `#000000`.
    * Nội dung tin nhắn: Chữ đen `#000000`.
    * Thông báo hệ thống: Chữ đen viền khung pastel rõ ràng.

---

### Task 2: Cập nhật chữ đen và màu Pastel cho `NetworkDemo.java`

**Files:**
- Modify: `NetworkDemo.java`

- [ ] **Bước 2.1: Thay đổi màu chữ và nền nút bấm trong Launcher**
  - Sửa `C_TEXT = Color.BLACK;`.
  - Cập nhật hàm `bigButton` và `smallButton`: Đặt viền nổi bật (Border), chữ màu đen in đậm.
  - Chuyển màu nền các nút sang pastel:
    * Broadcast: `#BBDEFB` (viền `#1976D2`)
    * Multicast: `#C8E6C9` (viền `#388E3C`)
    * Unified: `#B2DFDB` (viền `#00796B`)
    * Sender: `#FFE0B2` (viền `#F57C00`)
    * Receiver: `#E1BEE7` (viền `#7B1FA2`)
  - Chuyển màu tiêu đề và nhãn sang màu đen đậm.

---

### Task 3: Cập nhật chữ đen và sửa lỗi trong 4 file GUI con

**Files:**
- Modify: `BroadcastSenderGUI.java`
- Modify: `BroadcastReceiverGUI.java`
- Modify: `MulticastSenderGUI.java`
- Modify: `MulticastReceiverGUI.java`

- [ ] **Bước 3.1: Sửa chữ đen và nền pastel cho `BroadcastSenderGUI.java` & `BroadcastReceiverGUI.java`**
  - Nút `btnSend`, `btnSendAll`, `btnStart`, `btnStop`, `btnClear`: chuyển sang `setForeground(Color.BLACK)`, nền pastel, viền rõ.
- [ ] **Bước 3.2: Sửa lỗi khởi tạo `MulticastSocket` và chữ đen cho `MulticastReceiverGUI.java` & `MulticastSenderGUI.java`**
  - Trong `MulticastReceiverGUI.java`: Thay `new MulticastSocket(port)` bằng:
    ```java
    socket = new MulticastSocket(null);
    socket.setReuseAddress(true);
    socket.bind(new InetSocketAddress(port));
    ```
  - Chuyển toàn bộ nút bấm `btnJoin`, `btnLeave`, `btnClear`, `btnSend` sang chữ đen `Color.BLACK` trên nền pastel có viền rõ ràng.

---

### Task 4: Biên dịch và Xác minh Toàn bộ Dự án

- [ ] **Bước 4.1: Chạy biên dịch toàn bộ mã nguồn**
  - Lệnh: `javac -encoding UTF-8 *.java`
  - Đảm bảo biên dịch thành công không có lỗi (Exit code 0).
- [ ] **Bước 4.2: Kiểm tra khả năng chạy và đóng mở**
  - Xác nhận các class khởi động chuẩn xác.
