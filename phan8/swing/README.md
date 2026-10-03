# 🎓 Đồ Án Quản Lý Sinh Viên (Java Swing + PostgreSQL)

Đây là chương trình Quản Lý Sinh Viên giao diện đồ họa (GUI) được xây dựng bằng Java Swing kết nối với cơ sở dữ liệu PostgreSQL. Chương trình áp dụng mô hình kiến trúc phân lớp chuẩn (Model - DAO - UI) giúp code gọn gàng, dễ hiểu và dễ bảo trì.

## 🚀 Các Tính Năng Chính

- **Đăng nhập hệ thống**: Bảo vệ bằng form đăng nhập (`admin` / `123`).
- **Quản lý danh sách (CRUD)**:
  - Xem danh sách sinh viên dưới dạng bảng (JTable).
  - Thêm sinh viên mới, cập nhật thông tin, xóa sinh viên.
- **Tìm kiếm & Sắp xếp**:
  - Tìm kiếm sinh viên theo từ khóa (Mã SV, Họ tên, Lớp).
  - Sắp xếp danh sách theo Tên (chữ cái cuối) hoặc theo Điểm trung bình.
- **Thống kê & Báo cáo trực quan**:
  - Tóm tắt tổng số sinh viên, điểm trung bình toàn khóa, sinh viên điểm cao nhất.
  - Tích hợp thư viện **JFreeChart** để vẽ:
    - _Biểu đồ cột_: Phổ điểm sinh viên (Yếu, Trung bình, Khá, Giỏi).
    - _Biểu đồ tròn_: Tỉ lệ phân bố sinh viên theo từng lớp.

## 🛠️ Công Nghệ & Thư Viện Sử Dụng

- **Ngôn ngữ**: Java 8+
- **Giao diện**: Java Swing (thiết kế theo phong cách Material Design hiện đại)
- **Cơ sở dữ liệu**: PostgreSQL
- **Thư viện ngoài (External JARs)**:
  - `postgresql-42.7.5.jar` (Driver kết nối Database)
  - `jfreechart-1.5.3.jar` (Thư viện vẽ biểu đồ thống kê)

## 📂 Cấu Trúc Thư Mục (Code)

Mã nguồn được đặt trong thư mục `phan8/swing/` và chia thành các thành phần sau:

### 1. Thành phần Dữ liệu (Model & DB)

- `SinhVien.java`: Lớp đại diện (Model) cho đối tượng Sinh Viên.
- `DatabaseHelper.java`: Thiết lập kết nối (Connection) tới PostgreSQL.
- `SinhVienDAO.java`: Chứa toàn bộ câu lệnh SQL (Thêm, Xóa, Sửa, Lấy dữ liệu) bằng `PreparedStatement`.

### 2. Thành phần Giao diện (View & Controller)

- `LoginFrame.java`: Giao diện mở đầu, yêu cầu đăng nhập.
- `MainFrame.java`: Giao diện cửa sổ chính (4 Tabs: Danh sách, Thêm/Sửa, Tìm kiếm, Thống kê). Nơi kết nối giữa UI và DAO.
- `Constants.java`: Lưu trữ các hằng số màu sắc, font chữ thiết kế.
- `UIHelper.java`: Các hàm tiện ích hỗ trợ vẽ nút bấm (bo góc), bảng (bảng sọc xen kẽ) đẹp mắt hơn.

### 3. Kiểm thử (Testing)

- `TestAll.java`: Kịch bản test tự động gồm 38 test case để đảm bảo các chức năng DAO chạy đúng 100%.

## ⚙️ Hướng Dẫn Chạy Chương Trình

1. Đảm bảo đã cài đặt PostgreSQL và có database tên `SinhVien` (tài khoản `postgres`, mật khẩu cấu hình trong `DatabaseHelper.java`).
2. Tải đủ 2 file thư viện `.jar` (`postgresql` và `jfreechart`) vào cùng thư mục.
3. Mở Terminal / Command Prompt tại thư mục `phan8/swing`:

**Biên dịch mã nguồn:**

```bash
javac -encoding UTF-8 -cp ".;postgresql-42.7.5.jar;jfreechart-1.5.3.jar" *.java
```

**Khởi chạy chương trình (Bắt đầu từ Form Đăng Nhập):**

```bash
java -cp ".;postgresql-42.7.5.jar;jfreechart-1.5.3.jar" LoginFrame
```

_Tài khoản đăng nhập mặc định: `admin` / `123`_
