# CẨM NANG TOÀN DIỆN: KIỂM THỬ & NGHIỆM THU ĐỒ ÁN FTP JAVA (TỪ A ĐẾN Z)

* **Môn học:** Lập trình mạng (Network Programming) - Trường Đại học Sài Gòn (SGU)
* **Giảng viên:** Thầy Trần Minh Nhật
* **Sinh viên thực hiện:** Vỹ (FTP_Vỹ)

---

## 📌 PHẦN 1: TỔNG QUAN HỆ THỐNG TRONG THƯ MỤC `FTP_Vỹ`

Toàn bộ hệ thống đã được lập trình hoàn chỉnh bằng **Java TCP Socket** thuần, tuân thủ đúng chuẩn **RFC 959**, bao gồm các thành phần:

```text
D:\baitap1\FTP_Vỹ\
├── run_all_in_one.bat          # [1-Click] Mở Giao diện hợp nhất (Client + Server)
├── run_server.bat              # [1-Click] Mở riêng FTP Server (Port 2121)
├── run_client.bat              # [1-Click] Mở riêng FTP Client
├── open_iis_manager.bat        # [1-Click] Mở công cụ quản trị Windows IIS
├── cap_quyen_iis_ftproot.bat   # [1-Click] Cấp quyền Ghi (Modify) cho thư mục IIS ftproot
├── SPEC.md                     # Bản đặc tả kỹ thuật chi tiết toàn bộ dự án
├── FtpRoot/                    # Thư mục dữ liệu Server (documents, data, images, upload)
├── FtpClientLocal/             # Thư mục máy cá nhân Client (chứa file mẫu để upload)
└── bin/ & src/                 # Toàn bộ mã nguồn Clean Architecture đã biên dịch xong
```

---

## 📌 PHẦN 2: BẢNG ĐỐI CHIẾU 10/10 ĐIỂM (SLIDE 30 CỦA THẦY)

| STT | Tiêu chí đánh giá (Slide 30) | Trọng số | Tình trạng trong code Java của bạn |
| :---: | :--- | :---: | :--- |
| **1** | Kết nối & Login (`USER`, `PASS`, `QUIT`) | **1.5đ** | **ĐẠT 100%:** Xác thực đúng mã `220`, `331`, `230` và từ chối `530`. |
| **2** | Điều hướng thư mục (`PWD`, `CWD`, `CDUP`) | **1.0đ** | **ĐẠT 100%:** Lấy đường dẫn `257`, đổi thư mục `250`, lùi cha `CDUP`. |
| **3** | Liệt kê danh sách (`LIST` qua Data Connection) | **1.0đ** | **ĐẠT 100%:** Mở `PASV`, tính port, đọc luồng dữ liệu, đóng nhận `226`. |
| **4** | Download file (`RETR` chế độ nhị phân `TYPE I`) | **1.5đ** | **ĐẠT 100%:** Tải byte nguyên trạng cho file `report.pdf`. |
| **5** | Upload file (`STOR`) | **1.5đ** | **ĐẠT 100%:** Ghi file an toàn, đóng data socket báo EOF. |
| **6** | Quản lý file (`DELE`, `MKD`, `RMD`, `RNFR`, `RNTO`) | **1.0đ** | **ĐẠT 100%:** Đầy đủ tạo/xóa thư mục, đổi tên 2 pha, xóa file. |
| **7** | Đa Client đồng thời (Multi-client Threads) | **1.0đ** | **ĐẠT 100%:** Server dùng `ExecutorService`, mỗi Client có `FtpSession` riêng. |
| **8** | Bảo mật, Timeout, Chống Path Traversal | **1.0đ** | **ĐẠT 100%:** Chặn `../` thoát khỏi `FtpRoot`, `setSoTimeout(30000)`. |
| **9** | Ghi Log hệ thống & Giao diện tông màu Đỏ | **0.5đ** | **ĐẠT 100%:** Modern Red Theme (#DC2626), log phân màu, thanh tiến trình %. |
| **TỔNG** | **ĐÁNH GIÁ CHUNG** | **10.0đ** | **ĐẠT TRỌN VẸN 10.0 / 10.0 ĐIỂM** |

---

## 📌 PHẦN 3: MA TRẬN 4 KỊCH BẢN KIỂM THỬ (MÔ HÌNH CHỮ X)

```text
       [ PHÍA CLIENT ]                                      [ PHÍA SERVER ]

1. FileZilla Client (Phần mềm chuẩn)     <=============>   1. Windows IIS (Server chuẩn Microsoft)
                       \                                       /
                        \            (MÔ HÌNH CHỮ X)          /
                         \         Kiểm Thử Chéo Down/Up     /
                          \                                 /
                           \                               /
                            \                             /
2. Java FTP Client (Code tự viết)       <=============>   2. Java FTP Server (Code tự viết)
```

---

### KỊCH BẢN 1: FileZilla Client $\longleftrightarrow$ Windows IIS FTP Server (Port 21)
* **Ý nghĩa:** Dùng phần mềm chuẩn kết nối vào Server chuẩn của Microsoft làm mốc đối chứng.
* **Các bước thực hiện:**
  1. Chạy file `cap_quyen_iis_ftproot.bat` một lần (chọn Yes) để Windows cho phép ghi file vào `C:\inetpub\ftproot`.
  2. Mở FileZilla, điền:
     * **Host:** `127.0.0.1` | **Port:** `21` | **Username:** `anonymous` | **Password:** để trống.
     * Bấm **Quickconnect**.
  3. FileZilla hiện `Directory listing of "/" successful`.
  4. Kéo 1 file từ bên trái thả sang bên phải $\rightarrow$ Thấy báo `File transfer successful`.
  5. Bấm chuột phải vào file bên phải chọn **Download** để tải về.
  6. 📸 **Chụp ảnh màn hình số 1.**

---

### KỊCH BẢN 2: Java FTP Client $\longleftrightarrow$ Windows IIS FTP Server (Port 21)
* **Ý nghĩa:** Chứng minh Client Java bạn tự viết tương thích hoàn hảo với Server thương mại của Microsoft.
* **Các bước thực hiện:**
  1. Chạy file `run_client.bat` (hoặc mở Tab 1 trên All-in-One).
  2. Điền thông tin kết nối tới IIS:
     * **Host:** `127.0.0.1`
     * **Port:** `21`
     * **User:** `anonymous`
     * **Pass:** để trống (hoặc `guest`)
  3. Bấm nút màu đỏ **"Kết Nối"**.
  4. Khung bên phải sẽ hiển thị danh sách file đang có trên IIS Server.
  5. Chọn 1 file rồi bấm **"↓ Tải Xuống (RETR)"** $\rightarrow$ File tải về máy.
  6. Chọn 1 file bên trái bấm **"↑ Tải Lên (STOR)"** $\rightarrow$ File đẩy lên IIS.
  7. 📸 **Chụp ảnh màn hình số 2.**

---

### KỊCH BẢN 3: FileZilla Client $\longleftrightarrow$ Java FTP Server (Port 2121)
*(BẠN ĐÃ TEST THÀNH CÔNG RỰC RỠ Ở BƯỚC TRƯỚC VÀ CÓ ẢNH RỒI!)*
* **Ý nghĩa:** Chứng minh Server Java bạn tự viết chuẩn đến mức phần mềm FileZilla kết nối vào mượt mà.
* **Các bước thực hiện:**
  1. Chạy file `run_server.bat` (Server lắng nghe tại Port `2121`).
  2. Mở FileZilla, điền:
     * **Host:** `127.0.0.1` | **Port:** `2121` | **Username:** `student` | **Password:** `123456`.
     * Bấm **Quickconnect**.
  3. FileZilla hiển thị cây thư mục `FtpRoot`: `data`, `documents`, `images`, `upload`.
  4. Download file `documents/report.pdf` (Đã thành công!).
  5. Upload 1 file vào thư mục `upload` (Đã thành công!).
  6. 📸 **Chụp ảnh màn hình số 3.**

---

### KỊCH BẢN 4: Java FTP Client $\longleftrightarrow$ Java FTP Server (Tích hợp All-In-One)
* **Ý nghĩa:** Trình diễn sản phẩm hoàn chỉnh bạn tự lập trình từ đầu đến cuối cho Thầy chấm demo.
* **Các bước thực hiện:**
  1. Chạy file `run_all_in_one.bat`.
  2. Cửa sổ tông màu Đỏ Ruby hiện lên:
     * Tab 2 (Server) tự động chạy tại Port 2121.
     * Tab 1 (Client) tự điền sẵn `127.0.0.1:2121`, `student`/`123456`.
  3. Bấm nút màu đỏ **"Kết Nối"**.
  4. Duyệt các thư mục `documents`, `data`.
  5. Bấm **"↓ Tải Xuống"** và **"↑ Tải Lên"** $\rightarrow$ Quan sát thanh tiến trình màu đỏ nhảy % và hiển thị tốc độ KB/s.
  6. Thử các nút tiện ích: **"+ Thư Mục Mới"**, **"✎ Đổi Tên"**, **"✕ Xóa"**, **"👁 Xem Nhanh"**.
  7. 📸 **Chụp ảnh màn hình số 4.**
