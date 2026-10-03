# Yêu cầu nộp bài

- Mã nguồn (Source Code).
- Ảnh kết quả chạy chương trình.
- Báo cáo ngắn mô tả:

**Bài 4\. Nhập và xuất mảng**

Yêu cầu:

- Nhập số phần tử n.
- Nhập mảng số nguyên.
- Xuất mảng ra màn hình.

**Bài 10\. Quản lý điểm sinh viên**

Viết chương trình quản lý điểm của **n sinh viên**.

Mỗi sinh viên gồm:

- Họ tên
- Điểm Toán
- Điểm Lập trình

Thực hiện các chức năng:

1. Nhập danh sách sinh viên.
2. Hiển thị danh sách.
3. Tính điểm trung bình của từng sinh viên (viết bằng Method).
4. Tìm sinh viên có điểm trung bình cao nhất.
5. Sắp xếp danh sách theo điểm trung bình giảm dần.
6. Tìm sinh viên theo tên.
7. Thống kê số sinh viên đạt (điểm trung bình ≥ 5).

**Bài 8\. Chương trình quản lý thư viện**

Thiết kế các lớp:

TaiLieu  
│  
├── MaTL  
├── TenTL  
└── NamXB  
 │  
 ├──────────────┐  
 │ │  
 Sach TapChi

Yêu cầu:

Thực hiện các chức năng:

1. Thêm tài liệu.
2. Hiển thị danh sách.
3. Tìm theo mã.
4. Xóa tài liệu.
5. Sắp xếp theo năm xuất bản.
6. Thống kê số lượng từng loại tài liệu.

Áp dụng:

- Class
- Constructor
- Property
- Inheritance
- Polymorphism
- List\<T\>

## **Bài 1\. Xử lý lỗi nhập dữ liệu**

Viết chương trình nhập hai số nguyên và thực hiện phép chia.

Yêu cầu:

- Sử dụng try-catch để xử lý:
  - Nhập sai kiểu dữ liệu.
  - Chia cho 0\.
- Hiển thị thông báo lỗi phù hợp.

---

## **Bài 2\. Kiểm tra dữ liệu bằng throw**

Viết chương trình nhập thông tin sinh viên:

- Mã sinh viên
- Họ tên
- Điểm trung bình

Yêu cầu:

- Nếu điểm \< 0 hoặc \> 10 thì phát sinh ngoại lệ bằng throw.
- Hiển thị thông báo lỗi và yêu cầu nhập lại.

---

## **Bài 3\. Sử dụng finally**

Viết chương trình đọc dữ liệu từ một tập tin văn bản.

Yêu cầu:

- Nếu tập tin không tồn tại, thông báo lỗi.
- Luôn đóng tập tin trong khối finally.

---

# Phần 2\. List\<T\>

## **Bài 4\. Quản lý danh sách sinh viên**

Sử dụng List\<SinhVien\> để thực hiện:

1. Thêm sinh viên.
2. Hiển thị danh sách.
3. Tìm sinh viên theo mã.
4. Cập nhật điểm trung bình.
5. Xóa sinh viên theo mã.
6. Sắp xếp theo điểm trung bình giảm dần.

---

# Phần 3\. Dictionary\<TKey,TValue\>

## **Bài 5\. Quản lý danh bạ**

Sử dụng:

Dictionary\<string, string\>  
Trong đó:

- Khóa: Số điện thoại.
- Giá trị: Họ tên.

Thực hiện:

1. Thêm liên hệ.
2. Tìm theo số điện thoại.
3. Cập nhật tên.
4. Xóa liên hệ.
5. Hiển thị toàn bộ danh bạ.

---

# Phần 4\. Queue\<T\>

## **Bài 6\. Mô phỏng hệ thống lấy số thứ tự**

Sử dụng Queue\<string\>.

Thực hiện:

1. Thêm khách hàng vào hàng đợi.
2. Phục vụ khách hàng đầu tiên.
3. Hiển thị khách hàng đang chờ.
4. Hiển thị số lượng khách còn lại.

---

# Phần 5\. Stack\<T\>

## **Bài 7\. Quản lý lịch sử thao tác**

Sử dụng Stack\<string\> để lưu các thao tác của người dùng.

Thực hiện:

1. Thêm thao tác mới.
2. Hoàn tác (Undo) thao tác gần nhất.
3. Hiển thị danh sách thao tác còn lại trong ngăn xếp.

---

# Phần 6\. Bài tập tổng hợp

## **Bài 8\. Chương trình quản lý kho sách**

Xây dựng chương trình quản lý kho sách với lớp Sach gồm:

- Mã sách
- Tên sách
- Tác giả
- Số lượng

Yêu cầu:

1. Thêm sách vào kho (List\<Sach\>).
2. Tìm sách theo mã (Dictionary hỗ trợ tra cứu nhanh).
3. Mượn sách (giảm số lượng).
4. Trả sách (tăng số lượng).
5. Lưu lịch sử mượn/trả bằng Stack.
6. Quản lý danh sách người chờ mượn bằng Queue.
7. Xử lý các trường hợp ngoại lệ:
   - Mã sách không tồn tại.
   - Hết sách.
   - Dữ liệu nhập không hợp lệ.

**Bài 1\. Ghi dữ liệu vào tập tin**

Viết chương trình nhập thông tin sinh viên gồm:

- Mã sinh viên
- Họ tên
- Lớp
- Điểm trung bình

Yêu cầu:

- Ghi thông tin vào tập tin **SinhVien.txt**.
- Mỗi sinh viên lưu trên một dòng.

Ví dụ:

SV001;Nguyen Van A;CNTT01;8.5  
SV002;Tran Thi B;CNTT02;7.8

---

**Bài 2\. Đọc dữ liệu từ tập tin**

Đọc dữ liệu từ **SinhVien.txt**.

Yêu cầu:

- Hiển thị toàn bộ danh sách sinh viên.
- Đếm số lượng sinh viên trong tập tin.

---

**Bài 3\. Tìm kiếm dữ liệu**

Từ tập tin **SinhVien.txt**:

- Tìm sinh viên theo mã.
- Tìm sinh viên theo họ tên.
- Hiển thị kết quả tìm kiếm.

**Bài 8\. Chương trình quản lý sinh viên**

Xây dựng chương trình quản lý sinh viên với các chức năng:

1. Thêm sinh viên.
2. Hiển thị danh sách.
3. Tìm kiếm theo mã hoặc họ tên.
4. Cập nhật thông tin sinh viên.
5. Xóa sinh viên.
6. Lưu dữ liệu vào **Text File**.
7. Đọc dữ liệu từ **Text File** khi khởi động chương trình.
