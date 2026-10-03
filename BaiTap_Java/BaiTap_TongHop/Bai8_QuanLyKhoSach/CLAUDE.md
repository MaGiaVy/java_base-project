# Bài 8: Chương Trình Quản Lý Kho Sách (Tổng Hợp)

## Mô tả yêu cầu

Xây dựng chương trình quản lý kho sách với lớp `Sach`:

- Mã sách
- Tên sách
- Tác giả
- Số lượng

### Áp dụng đầy đủ các kỹ thuật:

1. Thêm sách vào kho (`List<Sach>`).
2. Tìm sách theo mã (`Map<String, Sach>` hỗ trợ tra cứu O(1)).
3. Mượn sách (giảm số lượng).
4. Trả sách (tăng số lượng).
5. Lưu lịch sử mượn/trả bằng `Stack<String>`.
6. Quản lý danh sách người chờ mượn bằng `Queue<String>`.
7. Xử lý các trường hợp ngoại lệ (Exceptions):
   - Mã sách không tồn tại (`BookNotFoundException`).
   - Hết sách (`OutOfStockException`).
   - Dữ liệu nhập không hợp lệ (`InvalidInputException`).

## Cách biên dịch và chạy

```bash
javac -encoding UTF-8 *.java
java QuanLyKhoSach
```
