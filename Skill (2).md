Hãy viết 2 class Java có thể chạy độc lập: `NetDemoSender.java` và `NetDemoReceiver.java`.

* **NetDemoSender:** Tạo một menu console đơn giản cho phép người dùng chọn 1 trong 3 mode để gửi thông điệp: \[1\] Gửi Unicast, \[2\] Gửi Broadcast, \[3\] Gửi Multicast.  
* **NetDemoReceiver:** Tạo menu console để chọn mode lắng nghe tương ứng.  
  * *Yêu cầu đặc biệt:* Nếu user chọn mode \[3\] Multicast, chương trình phải hỏi thêm: *"Bạn có muốn gọi hàm joinGroup() để gia nhập nhóm không? (Y/N)"*.  
* **Log console:** Thêm các dòng `System.out.println` thật chi tiết ở cả bên gửi và bên nhận để minh họa dòng chảy của Datagram (ví dụ: "Đã bật cờ Broadcast...", "Đang chờ gói tin nhưng cố tình không gia nhập nhóm..."). Code cần có comment tiếng Việt rõ ràng từng bước.

