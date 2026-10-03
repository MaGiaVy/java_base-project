# 📚 TÀI LIỆU HƯỚNG DẪN THỰC HÀNH & BÁO CÁO LẬP TRÌNH MẠNG UDP
## SO SÁNH UNICAST, BROADCAST (VẬT LÝ & LOGIC) VÀ MULTICAST TRONG JAVA

---

## 📌 MỤC LỤC
1. [Bản chất mạng & Bảng so sánh 3 cơ chế](#1-bản-chất-mạng--bảng-so-sánh-3-cơ-chế)
2. [Phân biệt chi tiết: Broadcast Vật lý vs Broadcast Logic](#2-phân-biệt-chi-tiết-broadcast-vật-lý-vs-broadcast-logic)
3. [Cơ chế lọc phần cứng (Hardware Filtering) của Multicast](#3-cơ-chế-lọc-phần-cứng-hardware-filtering-của-multicast)
4. [Cấu trúc mã nguồn & Quy tắc lập trình](#4-cấu-trúc-mã-nguồn--quy-tắc-lập-trình)
5. [Kịch bản 1: Hướng dẫn tự chạy thử 1 mình (trên 1 máy tính)](#5-kịch-bản-1-hướng-dẫn-tự-chạy-thử-1-mình-trên-1-máy-tính)
6. [Kịch bản 2: Hướng dẫn chạy nhóm 3 người (trên 3 laptop)](#6-kịch-bản-2-hướng-dẫn-chạy-nhóm-3-người-trên-3-laptop)
7. [Bộ câu hỏi vấn đáp thường gặp & Câu trả lời mẫu](#7-bộ-câu-hỏi-vấn-đáp-thường-gặp--câu-trả-lời-mẫu)

---

## 1. BẢN CHẤT MẠNG & BẢNG SO SÁNH 3 CƠ CHẾ

| Tiêu chí | **Unicast** (1 - 1) | **Broadcast** (1 - Tất cả) | **Multicast** (1 - Nhóm chọn lọc) |
| :--- | :--- | :--- | :--- |
| **Đích đến** | Đúng 1 máy cụ thể | Toàn bộ máy trong mạng LAN | Nhóm máy có đăng ký tham gia |
| **Địa chỉ IP** | IP cụ thể (VD: `192.168.1.15`) | `255.255.255.255` hoặc Subnet IP | Lớp D (`224.0.0.0` - `239.255.255.255`) |
| **Địa chỉ MAC tầng 2**| MAC card mạng đích | `FF:FF:FF:FF:FF:FF` | Dải đặc biệt: `01:00:5E:xx:xx:xx` |
| **Class Java sử dụng**| `DatagramSocket` | `DatagramSocket` + `setBroadcast(true)` | `MulticastSocket` |
| **Yêu cầu phía nhận** | Mở đúng Port lắng nghe | Mở đúng Port lắng nghe | **BẮT BUỘC phải gọi `joinGroup()`** |
| **Tác động CPU mạng** | Nhẹ (chỉ máy đích xử lý) | Nặng (tất cả máy trong LAN bị ngắt CPU)| Tối ưu (chỉ máy đã join mới nhận) |
| **Ứng dụng thực tế** | Chat 1-1, duyệt web | DHCP xin IP, ARP tìm MAC | IPTV, họp trực tuyến Zoom, livestream |

---

## 2. PHÂN BIỆT CHI TIẾT: BROADCAST VẬT LÝ VS BROADCAST LOGIC

Đây là câu hỏi thường xuyên xuất hiện trong các bài thi vấn đáp mạng:

```
                              ┌────────────────────────────────────────┐
                              │            CÁC LOẠI BROADCAST          │
                              └───────────────────┬────────────────────┘
                                                  │
                   ┌──────────────────────────────┴──────────────────────────────┐
                   │                                                             │
┌──────────────────▼───────────────────┐               ┌─────────────────────────▼──────────────────────────┐
│   BROADCAST VẬT LÝ (Limited)         │               │     BROADCAST LOGIC (Directed Subnet)              │
│   • Địa chỉ: 255.255.255.255         │               │     • Địa chỉ: 192.168.1.255 (Subnet IP)           │
│   • Bị Router CHẶN ĐỨNG 100%         │               │     • CÓ THỂ được Router định tuyến từ xa          │
│   • Ví dụ: Cầm loa hét trong phòng   │               │     • Ví dụ: Gửi thư bưu điện ghi rõ số nhà        │
└──────────────────────────────────────┘               └────────────────────────────────────────────────────┘
```

### 🔴 1. Broadcast VẬT LÝ (Physical Broadcast / Limited Broadcast)
- **Địa chỉ IP:** `255.255.255.255` (Toàn bộ 32 bit đều là số 1).
- **Bản chất:** Gói tin bị giới hạn tuyệt đối trong đoạn dây mạng hoặc sóng Wi-Fi vật lý hiện tại. Router vật lý thấy gói tin này sẽ **loại bỏ (DROP) ngay lập tức**, không bao giờ cho đi qua cổng khác.
- **Ví dụ đời thực:** Bạn đứng giữa một phòng học cầm loa pin hét to. Chỉ những ai ngồi trong 4 bức tường vật lý của căn phòng đó nghe thấy. Phòng bên cạnh có tường (Router) ngăn cách sẽ không nghe thấy gì.
- **Ứng dụng:** Dùng cho giao thức **DHCP** khi máy tính mới cắm dây mạng, chưa biết mình thuộc mạng nào nên phát tín hiệu mù quáng `255.255.255.255` để xin cấp IP.

### 🔵 2. Broadcast LOGIC (Logical Broadcast / Directed Subnet Broadcast)
- **Địa chỉ IP:** Là địa chỉ Broadcast của một mạng con cụ thể (Subnet Broadcast), ví dụ: `192.168.1.255` hay `192.168.213.255`.
- **Bản chất:** Địa chỉ có phần mạng *(Network ID)* rõ ràng. Vì vậy, một máy tính ở mạng khác (từ xa qua Router) vẫn có thể gửi gói tin này đi qua Internet/Router. Khi gói tin đến đúng Router đích quản lý mạng con đó, Router mới phát tán cho tất cả máy trong mạng con nghe.
- **Ví dụ đời thực:** Bạn gửi một lá thư bưu điện ghi rõ: *"Kính gửi: Tất cả thành viên trong Căn hộ số 101, Chung cư Landmark"*. Bưu điện (Router) chuyển thư đi xuyên thành phố đến đúng căn hộ 101, rồi bảo vệ mới đọc to cho cả nhà cùng nghe.
- **Ứng dụng:** Tính năng **Wake-on-LAN** (Bật máy tính từ xa qua mạng).

---

## 3. CƠ CHẾ LỌC PHẦN CỨNG (HARDWARE FILTERING) CỦA MULTICAST

Sự khác biệt sống còn giữa Broadcast và Multicast nằm ở **tầng vật lý (Card mạng NIC)**:

1. **Khi nhận gói Broadcast:**
   - Địa chỉ MAC là `FF:FF:FF:FF:FF:FF`.
   - Card mạng của **mọi máy tính** đều bị ép phải nhận gói tin, tạo tín hiệu ngắt (Interrupt) bắt CPU hệ thống dừng việc đang làm để kiểm tra.
2. **Khi nhận gói Multicast:**
   - Nếu chương trình **ĐÃ GỌI `joinGroup()`:** Hệ điều hành nạp địa chỉ MAC multicast `01:00:5E:...` vào bộ lọc của Card mạng. Card mạng cho gói tin đi qua lên ứng dụng.
   - Nếu chương trình **KHÔNG GỌI `joinGroup()`:** Chip xử lý trên Card mạng (NIC) phát hiện gói tin không có trong danh sách đăng ký $\rightarrow$ **Vứt bỏ (DROP) ngay tại cổng phần cứng! CPU và hệ điều hành hoàn toàn không bị làm phiền!**

---

## 4. CẤU TRÚC MÃ NGUỒN & QUY TẮC LẬP TRÌNH

Dự án bao gồm 2 chương trình console chính:
- [`NetDemoSender.java`](file:///d:/baitap1/NetDemoSender.java): Bộ gửi tích hợp cả 3 chế độ (hỗ trợ cả Broadcast Vật lý và Broadcast Logic).
- [`NetDemoReceiver.java`](file:///d:/baitap1/NetDemoReceiver.java): Bộ nhận tương tác có menu hỏi `joinGroup()` (Y/N).

### Quy tắc kỹ thuật bắt buộc đã hiện thực:
1. **Rule 1 (Unicast):** Dùng `DatagramSocket`, nhập IP đích danh (hoặc `127.0.0.1`), gửi đến port `7000`.
2. **Rule 2 (Broadcast):** Bắt buộc gọi `socket.setBroadcast(true)` trước khi phát sóng.
3. **Rule 3 (Multicast):** Bắt buộc dùng `MulticastSocket`, gửi tới nhóm IP Lớp D `239.1.1.1:8000`.
4. **Rule 4 (Kịch bản chứng minh):** Người dùng có quyền chọn `Y` hoặc `N`. Nếu chọn `N`, socket lắng nghe nhưng không join, sau 10 giây báo Timeout chứng minh sự khác biệt hoàn toàn với Broadcast.

---

## 5. KỊCH BẢN 1: HƯỚNG DẪN TỰ CHẠY THỬ 1 MÌNH (TRÊN 1 MÁY TÍNH)

### 🛠️ Chuẩn bị
1. Biên dịch mã nguồn:
   ```powershell
   javac -encoding UTF-8 NetDemoSender.java NetDemoReceiver.java
   ```
2. Mở **3 cửa sổ Terminal song song** trong VS Code (dùng tính năng **Split Terminal** $\boxplus$):
   - **Terminal 1:** Đóng vai `Máy nhận A`
   - **Terminal 2:** Đóng vai `Máy nhận B`
   - **Terminal 3:** Đóng vai `Máy gửi (Sender)`

---

### 🧪 Bài test 1: Kiểm chứng UNICAST (1 - 1)
- **Terminal 1 (Máy A):** `java NetDemoReceiver` $\rightarrow$ Nhập `1` *(Lắng nghe Unicast)*.
- **Terminal 2 (Máy B):** `java NetDemoReceiver` $\rightarrow$ Nhập `1` *(Lắng nghe Unicast)*.
- **Terminal 3 (Sender):** `java NetDemoSender` $\rightarrow$ Nhập `1` $\rightarrow$ Nhấn **Enter** lấy IP `127.0.0.1` $\rightarrow$ Nhập tin nhắn $\rightarrow$ Gửi.
- **Hiện tượng:**
  - ✅ **Terminal 1:** Nhận được tin nhắn thành công.
  - ❌ **Terminal 2:** Hoàn toàn im lặng (chứng minh truyền 1 - 1).

---

### 🧪 Bài test 2: Kiểm chứng BROADCAST (1 - Tất cả)
- **Terminal 1 (Máy A):** `java NetDemoReceiver` $\rightarrow$ Nhập `2` *(Lắng nghe Broadcast)*.
- **Terminal 2 (Máy B):** `java NetDemoReceiver` $\rightarrow$ Nhập `2` *(Lắng nghe Broadcast)*.
- **Terminal 3 (Sender):** `java NetDemoSender` $\rightarrow$ Nhập `2` $\rightarrow$ Chọn `1` (Vật lý) hoặc `2` (Logic) $\rightarrow$ Gửi.
- **Hiện tượng:**
  - ✅ **CẢ HAI Terminal 1 VÀ 2:** Cùng lúc hiện thông báo nhận được tin nhắn!

---

### 🧪 Bài test 3: Kiểm chứng MULTICAST (Quy tắc Rule 4)
- **Terminal 1 (Máy A - Tham gia nhóm):** 
  - `java NetDemoReceiver` $\rightarrow$ Nhập `3` $\rightarrow$ Trả lời câu hỏi: Nhập **`Y`**.
- **Terminal 2 (Máy B - Cố tình KHÔNG tham gia nhóm):** 
  - `java NetDemoReceiver` $\rightarrow$ Nhập `3` $\rightarrow$ Trả lời câu hỏi: Nhập **`N`**.
- **Terminal 3 (Sender):** 
  - `java NetDemoSender` $\rightarrow$ Nhập `3` $\rightarrow$ Gửi thông điệp tới `239.1.1.1:8000`.
- **Hiện tượng:**
  - ✅ **Terminal 1 (chọn Y):** Nhận được thông điệp ngay lập tức!
  - ❌ **Terminal 2 (chọn N):** Đợi đủ 10 giây rồi in bảng Timeout xác nhận không nhận được gì do card mạng đã lọc bỏ gói tin!

---

## 6. KỊCH BẢN 2: HƯỚNG DẪN CHẠY NHÓM 3 NGƯỜI (TRÊN 3 LAPTOP)

### 🌐 Bước 0: Kết nối mạng LAN / Wi-Fi chung
1. 3 laptop kết nối vào **cùng một mạng Wi-Fi** (hoặc phát Wi-Fi từ điện thoại di động).
2. Từng bạn mở CMD gõ `ipconfig` để lấy địa chỉ IP:
   - **Bạn A (Sender):** Ví dụ `192.168.43.10`
   - **Bạn B (Receiver 1):** Ví dụ `192.168.43.20`
   - **Bạn C (Receiver 2):** Ví dụ `192.168.43.30`

---

### 📋 Kịch bản 3 Hồi biểu diễn trước lớp:

| Hồi | Thao tác Bạn A (Sender) | Thao tác Bạn B (Receiver 1) | Thao tác Bạn C (Receiver 2) | Kết quả quan sát & Lời giải thích |
| :--- | :--- | :--- | :--- | :--- |
| **Hồi 1: Unicast** | Chọn `1` $\rightarrow$ Nhập IP Bạn B: `192.168.43.20` $\rightarrow$ Gửi | Chọn `1` *(Lắng nghe)* | Chọn `1` *(Lắng nghe)* | • Máy B: Nhận được tin.<br>• Máy C: Không nhận được gì.<br>🗣️ *Giải thích:* Gói tin đi đích danh tới IP máy B. |
| **Hồi 2: Broadcast**| Chọn `2` $\rightarrow$ Chọn loại `1` hoặc `2` $\rightarrow$ Phát sóng | Chọn `2` *(Lắng nghe)* | Chọn `2` *(Lắng nghe)* | • CẢ Máy B và C cùng nổ thông báo nhận được tin!<br>🗣️ *Giải thích:* Gói tin phát tán toàn mạng LAN. |
| **Hồi 3: Multicast**| Chọn `3` $\rightarrow$ Gửi tới nhóm `239.1.1.1:8000` | Chọn `3` $\rightarrow$ Nhập **`Y`** *(Tham gia)* | Chọn `3` $\rightarrow$ Nhập **`N`** *(Từ chối)* | • Máy B (Y): Nhận được tin ngay.<br>• Máy C (N): Chờ 10s báo Timeout.<br>🗣️ *Giải thích:* Chứng minh Multicast khác Broadcast! |

---

## 7. BỘ CÂU HỎI VẤN ĐÁP THƯỜNG GẶP & CÂU TRẢ LỜI MẪU

### ❓ Câu 1: Tại sao máy nhận Broadcast không cần đăng ký mà máy nhận Multicast lại phải gọi `joinGroup()`?
> **Trả lời:** Broadcast mang địa chỉ MAC toàn số 1 (`FF:FF:FF:FF:FF:FF`), mọi card mạng đều bị ép phải xử lý. Ngược lại, Multicast mang địa chỉ nhóm Lớp D; chỉ khi ứng dụng gọi `joinGroup()`, hệ điều hành mới gửi bản tin IGMP Report để card mạng đăng ký mở cổng nhận luồng dữ liệu đó.

### ❓ Câu 2: Địa chỉ `255.255.255.255` khác gì với `192.168.1.255`?
> **Trả lời:** `255.255.255.255` là Quảng bá vật lý (Limited Broadcast), chỉ tồn tại trong dây cáp/Wi-Fi của mạng LAN hiện tại và bị Router chặn đứng 100%. Còn `192.168.1.255` là Quảng bá logic (Directed Subnet Broadcast), có thể được các Router định tuyến từ xa đi tới mạng con đích rồi mới phát tán.

### ❓ Câu 3: Khi nào nên dùng Multicast thay vì Broadcast?
> **Trả lời:** Khi cần gửi cùng một luồng dữ liệu lớn (như video livestream, họp trực tuyến) cho nhiều người cùng lúc mà không muốn làm nghẽn băng thông của những người không có nhu cầu trong mạng LAN.
