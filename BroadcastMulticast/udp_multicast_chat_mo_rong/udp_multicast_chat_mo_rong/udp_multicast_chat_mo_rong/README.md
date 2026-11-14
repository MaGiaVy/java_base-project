# UDP Multicast Chat Java - bài tập mở rộng đầy đủ

Project này mở rộng bài UDP Multicast `239.1.1.1:8000` thành chương trình **chat nhóm 2 chiều**. Mỗi máy chạy cùng một chương trình `MulticastChat`: vừa gửi, vừa nhận.

## 1. Đã làm đủ 6 mục bài tập mở rộng

1. **Chat nhóm**: mọi máy trong cùng phòng có thể gửi và nhận.
2. **Tên hiển thị**: nhập tên khi chạy, ví dụ `An`, `Binh`, `Peer1`.
3. **Nhiều phòng bằng nhiều nhóm multicast**:
   - Phòng 1 - Java: `239.1.1.1:8000`
   - Phòng 2 - Network: `239.1.1.2:8000`
   - Phòng 3 - IT1: `239.1.1.3:8000`
4. **Chuyển nhóm khi đang chạy**: `/join 1`, `/join 2`, `/join 3`.
5. **Hiển thị thời gian**: mỗi tin có dạng `[HH:mm:ss]`.
6. **Phát hiện Datagram bị mất + gửi lại**:
   - Mỗi người gửi đánh số `#0001`, `#0002`, ... riêng cho từng phòng.
   - Nếu máy nhận thấy bị nhảy số, nó gửi `NACK` yêu cầu người gửi phát lại gói thiếu.
   - Có lệnh `/loss 3` để cố tình bỏ qua gói thứ 3, 6, 9... ở lần nhận đầu tiên nhằm demo chức năng này dễ dàng.

`TTL = 1`, nên multicast được giới hạn trong LAN theo đúng mô hình bài học.

---

## 2. File trong project

- `MulticastChat.java`: chương trình chat chính, chạy trên tất cả máy thành viên.
- `NonMember.java`: máy đối chứng, cùng Wi-Fi nhưng **không join multicast group**.
- `ShowNetworkInfo.java`: xem card mạng + IPv4 + khả năng multicast.
- `compile.bat`: compile trên Windows.
- `run_chat.bat`: chạy chat nhanh trên Windows.
- `run_non_member.bat`: chạy máy đối chứng.
- `allow_udp_8000_windows.bat`: mở firewall UDP 8000 (Run as administrator).
- `compile.sh`, `run_chat.sh`: script Linux/macOS.

---

## 3. Chuẩn bị

Trên các máy dùng để chat:

1. Cài **JDK 17 trở lên**. Project đã được kiểm tra bằng JDK 21 và compile với target Java 17.
2. Các máy kết nối **cùng một Wi-Fi/LAN**.
3. Tránh Guest Wi-Fi hoặc mạng bật AP/Client Isolation.
4. Windows: chuột phải `allow_udp_8000_windows.bat` -> **Run as administrator**.
5. Copy cả folder project sang các máy.

Kiểm tra Java:

```bat
java -version
javac -version
```

---

## 4. Compile

### Cách dễ nhất trên Windows

Double-click:

```text
compile.bat
```

Hoặc mở CMD/PowerShell trong folder và chạy:

```bat
javac MulticastChat.java ShowNetworkInfo.java NonMember.java
```

---

## 5. Test 2 máy trước

### Máy 1

```bat
java MulticastChat An
```

Chọn:

```text
1
```

Máy 1 sẽ join phòng Java `239.1.1.1:8000`.

### Máy 2

```bat
java MulticastChat Binh
```

Cũng chọn:

```text
1
```

Bây giờ hai máy đều là thành viên cùng multicast group. Gõ tin ở Máy 1, Máy 2 nhận; gõ tin ở Máy 2, Máy 1 nhận.

Ví dụ:

```text
[13:30:01] [Ban #0001] Hello Binh
```

Máy kia sẽ thấy:

```text
[13:30:01] [TIN An #0001] Hello Binh
```

---

## 6. Chạy bài 5 máy trước thầy

Tất cả 5 máy vẫn kết nối cùng Wi-Fi/LAN.

### Máy 1

```bat
java MulticastChat May1
```

Chọn phòng `1`.

### Máy 2

```bat
java MulticastChat May2
```

Chọn phòng `1`.

### Máy 3

```bat
java MulticastChat May3
```

Chọn phòng `1`.

### Máy 4

```bat
java MulticastChat May4
```

Chọn phòng `1`.

### Máy 5 - không tham gia multicast

Để nhìn trực quan, có thể chạy:

```bat
java NonMember May5
```

Máy 5 vẫn ở cùng Wi-Fi nhưng không tạo `MulticastSocket` và không gọi `joinGroup()`, nên không phải thành viên multicast.

Khi một trong Máy 1-4 gửi tin, các máy đang ở cùng phòng sẽ thấy tin. Máy 5 không thấy.

> Với bản mở rộng, không còn chia cứng một máy Sender và ba máy Receiver. Bốn máy thành viên đều có thể gửi và nhận. Điều này biến ví dụ multicast thành ứng dụng chat nhóm thật sự.

---

## 7. Lệnh trong chương trình

```text
/rooms      xem 3 phòng
/join 1     vào phòng Java      239.1.1.1
/join 2     vào phòng Network   239.1.1.2
/join 3     vào phòng IT1       239.1.1.3
/status     xem trạng thái hiện tại
/loss 3     giả lập mất mỗi gói có số thứ tự chia hết cho 3
/loss 0     tắt giả lập mất gói
/help       xem hướng dẫn
/exit       leaveGroup và đóng socket
```

Có thể dùng tên phòng:

```text
/join Java
/join Network
/join IT1
```

---

## 8. Demo tính năng chuyển phòng

Ví dụ:

- Máy 1 và Máy 2 đang ở phòng `1`.
- Máy 3 nhập:

```text
/join 2
```

Máy 3 sẽ `leaveGroup(239.1.1.1)` rồi `joinGroup(239.1.1.2)`.

Sau đó tin của phòng 1 không hiện trên Máy 3 nữa. Muốn quay lại:

```text
/join 1
```

---

## 9. Demo phát hiện Datagram bị mất và gửi lại

Đây là cách demo chắc chắn nhất, không cần chờ mạng thật sự làm mất UDP.

### Trên Máy 2

```text
/loss 3
```

### Trên Máy 1, gửi liên tục 4 tin

```text
Tin 1
Tin 2
Tin 3
Tin 4
```

Máy 2 sẽ cố tình bỏ lần nhận đầu tiên của `#0003`.

Khi `#0004` đến, Máy 2 phát hiện đang thiếu `#0003` và hiện đại loại:

```text
[MO PHONG MAT GOI] Bo qua goi May1 #0003 lan dau.
[TIN May1 #0004] Tin 4
[PHAT HIEN MAT] Thieu goi May1 #0003..#0003 -> gui NACK.
[GUI LAI May1 #0003] Tin 3
```

Máy 1 cũng hiện:

```text
[GUI LAI] Da gui lai #0003 theo NACK.
```

Tắt mô phỏng:

```text
/loss 0
```

### Ý nghĩa

UDP gốc không tự bảo đảm giao hàng. Phần này là **logic ứng dụng bổ sung**: số thứ tự -> phát hiện khoảng trống -> NACK -> phát lại gói còn lưu trong cache.

---

## 10. Nếu chương trình chọn sai card mạng

Chạy:

```bat
java ShowNetworkInfo
```

Tìm IPv4 của Wi-Fi/Ethernet đang dùng, ví dụ:

```text
IPv4 : 192.168.1.12
Multicast : true
```

Sau đó chỉ định IP khi chạy:

```bat
java MulticastChat An 192.168.1.12
```

Hoặc double-click `run_chat.bat` và nhập IPv4 đó.

---

## 11. Nếu hai máy không nhận nhau

Kiểm tra theo thứ tự:

1. Có cùng Wi-Fi/LAN không?
2. Có phải Guest Wi-Fi không?
3. AP/Client Isolation có bật không?
4. Windows Firewall đã mở UDP 8000 chưa?
5. `java ShowNetworkInfo` có báo `Multicast: true` trên card đang dùng không?
6. Máy có VPN/VMware/VirtualBox làm chương trình chọn nhầm card không? Nếu có, truyền IPv4 thật khi chạy.
7. Thử mạng khác/hotspot riêng nếu access point đang lọc multicast.

---

## 12. Kịch bản demo ngắn trước thầy

1. Cho 5 máy vào cùng LAN.
2. Máy 1-4 chạy `MulticastChat`, cùng vào phòng 1 `239.1.1.1:8000`.
3. Máy 5 chạy `NonMember` để chứng minh nó cùng mạng nhưng không join group.
4. Máy 1 gửi tin -> Máy 2, 3, 4 nhận; Máy 5 không nhận.
5. Máy 2 trả lời -> các thành viên còn lại nhận, chứng minh đây là chat hai chiều.
6. Máy 3 `/join 2` -> chuyển sang group `239.1.1.2`; tin phòng 1 không còn hiện trên Máy 3.
7. Máy 3 `/join 1` quay lại.
8. Máy 2 `/loss 3`, Máy 1 gửi 4 tin -> demo phát hiện `#0003` bị thiếu và yêu cầu gửi lại.
9. `/exit` -> chương trình `leaveGroup()` và đóng socket.

## 13. Câu trả lời ngắn nếu thầy hỏi

**Multicast khác Broadcast ở đâu?**  
Broadcast gửi đến mọi máy trong miền broadcast. Multicast gửi đến một địa chỉ nhóm và chỉ thành viên đã join group mới nhận ở tầng ứng dụng/mạng hỗ trợ multicast.

**239.1.1.1 là gì?**  
Địa chỉ IPv4 multicast dùng để định danh một nhóm nhận. Project dùng `239.1.1.1`, `.2`, `.3` cho ba phòng.

**8000 là gì?**  
Cổng UDP của ứng dụng.

**TTL = 1 để làm gì?**  
Giới hạn Datagram multicast trong LAN, không đi qua router.

**UDP bị mất gói thì sao?**  
UDP không tự retransmit. Project mở rộng đánh số gói, phát hiện khoảng trống rồi gửi NACK để yêu cầu người gửi phát lại từ cache.
