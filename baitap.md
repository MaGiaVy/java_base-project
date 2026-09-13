* 

**Bài 8\. Chương trình quản lý sinh viên**

Xây dựng chương trình quản lý sinh viên với các chức năng:

1. Thêm sinh viên.   
2. Hiển thị danh sách.   
3. Tìm kiếm theo mã hoặc họ tên.   
4. Cập nhật thông tin sinh viên.   
5. Xóa sinh viên.   
6. Lưu dữ liệu vào **Text File**.   
7. Đọc dữ liệu từ **Text File** khi khởi động chương trình. 

### Bài 1\. Tạo Form đăng nhập

Thiết kế Form gồm:

* Label: Tên đăng nhập   
* TextBox: nhập username   
* Label: Mật khẩu   
* TextBox: nhập password   
* Button: Đăng nhập, Thoát 

Yêu cầu:

* Nếu username \= admin và password \= 123, hiển thị “Đăng nhập thành công”.   
* Ngược lại hiển thị “Sai tên đăng nhập hoặc mật khẩu”. 

---

### Bài 2\. Form tính toán cơ bản

Thiết kế giao diện gồm:

* 2 TextBox nhập số a, b   
* 4 Button: Cộng, Trừ, Nhân, Chia   
* Label hiển thị kết quả 

Yêu cầu:

* Xử lý lỗi nhập sai dữ liệu.   
* Không cho phép chia cho 0\. 

---

### Bài 3\. Quản lý danh sách sinh viên

Thiết kế Form gồm:

* TextBox: Mã SV, Họ tên, Lớp, Điểm TB   
* Button: Thêm, Sửa, Xóa, Xóa trắng   
* ListBox hoặc DataGridView hiển thị danh sách 

Yêu cầu:

* Thêm sinh viên vào danh sách.   
* Chọn sinh viên để sửa hoặc xóa.   
* Kiểm tra điểm từ 0 đến 10\.

# Phần 1\. Tạo cơ sở dữ liệu

## **Bài 1\. Tạo CSDL và bảng SinhVien**

Tạo cơ sở dữ liệu **QuanLySinhVien**.

Tạo bảng **SinhVien** gồm các trường:

| Tên trường | Kiểu dữ liệu |
| ----- | ----- |
| MaSV | varchar(10) |
| HoTen | nvarchar(50) |
| Lop | nvarchar(20) |
| NgaySinh | date |
| DiemTB | float |

Yêu cầu:

* Đặt MaSV là khóa chính.   
* Nhập ít nhất **10 bản ghi** để phục vụ kiểm thử. 

---

# Phần 2\. Kết nối My SQL

## **Bài 2\. Hiển thị dữ liệu**

Thiết kế giao diện gồm:

* DataGridView   
* Button **Hiển thị** 

Yêu cầu:

* Kết nối MySQL bằng SqlConnection.   
* Đọc dữ liệu từ bảng **SinhVien**.   
* Hiển thị toàn bộ dữ liệu trên DataGridView. 

---

# Phần 3\. Thêm dữ liệu

## **Bài 3\. Thêm sinh viên**

Thiết kế Form gồm:

* Mã sinh viên   
* Họ tên   
* Lớp   
* Ngày sinh   
* Điểm trung bình 

Button:

* Thêm 

Yêu cầu:

* Kiểm tra dữ liệu hợp lệ.   
* Thêm bản ghi vào SQL Server.   
* Cập nhật lại DataGridView. 

---

# Phần 4\. Cập nhật dữ liệu

## **Bài 4\. Sửa thông tin sinh viên**

Yêu cầu:

* Chọn một dòng trên DataGridView.   
* Hiển thị dữ liệu lên các TextBox.   
* Cho phép chỉnh sửa.   
* Lưu thay đổi xuống SQL Server. 

---

# Phần 5\. Xóa dữ liệu

## **Bài 5\. Xóa sinh viên**

Yêu cầu:

* Chọn sinh viên.   
* Hiển thị hộp thoại xác nhận.   
* Xóa dữ liệu khỏi My SQL.   
* Cập nhật lại danh sách. 

---

# Phần 6\. Tìm kiếm

## **Bài 6\. Tìm kiếm sinh viên**

Thiết kế:

* TextBox nhập từ khóa.   
* Button **Tìm kiếm**. 

Yêu cầu:

Tìm theo:

* Mã sinh viên.   
* Họ tên.   
* Lớp. 

Hiển thị kết quả trên DataGridView.

---

# Phần 7\. Sắp xếp và thống kê

## **Bài 7\. Thống kê dữ liệu**

Thực hiện:

* Sắp xếp theo tên.   
* Sắp xếp theo điểm trung bình.   
* Hiển thị số lượng sinh viên.   
* Tính điểm trung bình của lớp.   
* Hiển thị sinh viên có điểm cao nhất. 

---

# Phần 8\. Bài tập tổng hợp

## **Bài 8\. Chương trình quản lý sinh viên**

Xây dựng chương trình hoàn chỉnh gồm các chức năng:

1. Hiển thị danh sách sinh viên.   
2. Thêm sinh viên.   
3. Sửa thông tin sinh viên.   
4. Xóa sinh viên.   
5. Tìm kiếm theo mã hoặc họ tên.   
6. Sắp xếp theo điểm trung bình.   
7. Thống kê số lượng sinh viên.   
8. Thống kê điểm trung bình của lớp.   
9. Làm mới dữ liệu.   
10. Thoát chương trình.

