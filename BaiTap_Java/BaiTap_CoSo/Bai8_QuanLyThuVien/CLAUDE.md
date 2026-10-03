# Bài 8: Chương Trình Quản Lý Thư Viện

## Mô tả yêu cầu

Thiết kế các lớp:

```
TaiLieu (MaTL, TenTL, NamXB)
 ├── Sach (TacGia, SoTrang)
 └── TapChi (SoPhatHanh, ThangPhatHanh)
```

### Các chức năng:

1. Thêm tài liệu (Sách / Tạp chí).
2. Hiển thị danh sách.
3. Tìm theo mã tài liệu.
4. Xóa tài liệu theo mã.
5. Sắp xếp theo năm xuất bản.
6. Thống kê số lượng từng loại tài liệu.

Áp dụng: Class, Constructor, Getter/Setter, Kế thừa (Inheritance), Đa hình (Polymorphism), List<T>.

## Cách biên dịch và chạy

```bash
javac -encoding UTF-8 *.java
java QuanLyThuVien
```
