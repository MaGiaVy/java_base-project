**Rules (Quy tắc lập trình bắt buộc):**

1. **Đối với Unicast:** Sử dụng DatagramSocket. Mã nguồn máy gửi phải yêu cầu nhập hoặc chỉ định rõ IP của một máy nhận cụ thể.  
2. **Đối với Broadcast:** Bắt buộc sử dụng DatagramSocket, phải gọi phương thức setBroadcast(true) trước khi gửi, và gửi đến địa chỉ broadcast đại diện (ví dụ: 255.255.255.255 hoặc địa chỉ broadcast của mạng con).  
3. **Đối với Multicast:** Bắt buộc sử dụng MulticastSocket và gửi tới một địa chỉ IP thuộc lớp D (ví dụ: 239.1.1.1).  
4. **Quy tắc cốt lõi để demo:** Kịch bản nhận Multicast bắt buộc phải làm rõ được sự khác biệt với Broadcast. AI phải code chương trình Nhận (Receiver) sao cho người dùng có quyền chọn "Tham gia nhóm" (joinGroup()) hoặc "Không tham gia". Phải chứng minh được: Máy nằm trong mạng LAN lắng nghe cùng port nhưng nếu không gọi joinGroup() thì hoàn toàn không nhận được dữ liệu.

