# Hướng dẫn chi tiết Logic hoạt động - Đồ án Quản lý Sinh viên (Phần 8)

Tài liệu này giải thích chi tiết các file chứa **Logic xử lý chính** (không bao gồm giao diện UI). Các file cốt lõi nằm trong thư mục `d:\baitap1\phan8\swing\`.

Luồng hoạt động của chương trình được chia làm 3 thành phần chính:
1. **SinhVien.java (Model)**: Khuôn mẫu để tạo ra một sinh viên trong Java.
2. **DatabaseHelper.java (Connection)**: Cầu nối mở đường truyền từ Java tới PostgreSQL.
3. **SinhVienDAO.java (Data Access Object)**: Nơi chứa toàn bộ các câu lệnh SQL để Thêm, Xóa, Sửa, Lấy dữ liệu.

---

## 1. File: `SinhVien.java` (Khuôn mẫu dữ liệu - Model)

Đây là file định nghĩa đối tượng sinh viên. Bất cứ khi nào lấy dữ liệu từ Database lên, ta sẽ đóng gói nó vào đối tượng này.

```java
public class SinhVien {
    // Các biến private (thuộc tính) để lưu thông tin sinh viên
    // Tại sao lại là private? 
    // -> Để bảo vệ dữ liệu, không cho các file khác sửa trực tiếp (Tính Đóng gói trong OOP)
    private String maSV;
    private String hoTen;
    private String lop;
    private java.sql.Date ngaySinh;
    private double diemTB;

    // CONSTRUCTOR (Hàm khởi tạo)
    // Dùng để tạo ra 1 sinh viên mới và gán giá trị ngay lúc tạo.
    // Ví dụ: SinhVien sv = new SinhVien("SV01", "An", ...);
    public SinhVien(String maSV, String hoTen, String lop, java.sql.Date ngaySinh, double diemTB) {
        this.maSV = maSV; 
        this.hoTen = hoTen;
        this.lop = lop;
        this.ngaySinh = ngaySinh;
        this.diemTB = diemTB;
    }

    // GETTER: Vì biến là private, nên ta phải dùng hàm get() để nơi khác có thể "đọc" dữ liệu.
    public String getMaSV() {
        return maSV;
    }

    // SETTER: Tương tự, dùng hàm set() để nơi khác có thể "sửa" dữ liệu.
    public void setHoTen(String hoTen) {
        this.hoTen = hoTen;
    }
    
    // ... các getter/setter khác
}
```

---

## 2. File: `DatabaseHelper.java` (Mở kết nối tới PostgreSQL)

Không có file này, Java không thể nói chuyện với Database. Nhiệm vụ duy nhất của nó là mở một "đường ống" (Connection) tới CSDL.

```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseHelper {
    // 1. Khai báo URL kết nối (giao thức jdbc : loại database postgresql : máy chủ localhost : cổng 5432 : tên database)
    private static final String URL = "jdbc:postgresql://localhost:5432/SinhVien";
    // 2. Tài khoản và mật khẩu của PostgreSQL
    private static final String USER = "postgres";
    private static final String PASSWORD = "M@giavy265";

    // Hàm trả về 1 Connection (đường ống kết nối)
    public static Connection getConnection() throws SQLException {
        // DriverManager.getConnection() là hàm có sẵn của Java (cần file .jar của PostgreSQL để chạy)
        // Nó lấy URL, tài khoản, mật khẩu để đăng nhập vào DB và trả về kết nối đó.
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
```
**Tại sao cần hàm này?** Mỗi khi `SinhVienDAO` muốn Thêm/Xóa/Sửa, nó sẽ gọi `DatabaseHelper.getConnection()` để xin 1 đường ống kết nối vào Database.

---

## 3. File: `SinhVienDAO.java` (Trái tim của hệ thống xử lý)

**DAO** viết tắt của *Data Access Object* (Đối tượng truy cập dữ liệu). Tất cả lệnh SQL đều nằm ở đây.

### a. Lấy danh sách sinh viên (`getAllSinhVien`)
```java
public List<SinhVien> getAllSinhVien() {
    List<SinhVien> list = new ArrayList<>();
    // Câu lệnh SQL lấy tất cả dữ liệu
    String sql = "SELECT * FROM SinhVien"; 

    // try-with-resources: Tự động đóng đường ống (conn) và câu lệnh (stmt) sau khi chạy xong để khỏi tốn RAM.
    try (Connection conn = DatabaseHelper.getConnection();
         Statement stmt = conn.createStatement();
         // ResultSet là cái bảng kết quả trả về từ DB
         ResultSet rs = stmt.executeQuery(sql)) { 

        // Vòng lặp while: Cứ đọc từng dòng dữ liệu từ trên xuống dưới
        while (rs.next()) {
            // Lấy dữ liệu từng cột ở dòng hiện tại
            String maSV = rs.getString("MaSV"); 
            String hoTen = rs.getString("HoTen");
            String lop = rs.getString("Lop");
            Date ngaySinh = rs.getDate("NgaySinh");
            double diemTB = rs.getDouble("DiemTB");

            // Tạo thành đối tượng SinhVien Java và nhét vào List (danh sách)
            SinhVien sv = new SinhVien(maSV, hoTen, lop, ngaySinh, diemTB);
            list.add(sv);
        }
    } catch (SQLException e) {
        e.printStackTrace(); // In lỗi ra màn hình đen nếu kết nối thất bại
    }
    return list; // Trả danh sách về cho UI (Giao diện) để nó vẽ lên JTable
}
```

### b. Thêm một sinh viên mới (`addSinhVien`)
```java
public boolean addSinhVien(SinhVien sv) {
    // Dấu ? gọi là Tham số. Chúng ta KHÔNG nối chuỗi trực tiếp để tránh bị hack (SQL Injection)
    String sql = "INSERT INTO SinhVien (MaSV, HoTen, Lop, NgaySinh, DiemTB) VALUES (?, ?, ?, ?, ?)";
    
    // Dùng PreparedStatement thay vì Statement để có thể điền giá trị vào các dấu ?
    try (Connection conn = DatabaseHelper.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
         
        // Điền dữ liệu vào các dấu ? (Số 1 tương ứng với dấu ? thứ nhất)
        pstmt.setString(1, sv.getMaSV());
        pstmt.setString(2, sv.getHoTen());
        pstmt.setString(3, sv.getLop());
        pstmt.setDate(4, sv.getNgaySinh());
        pstmt.setDouble(5, sv.getDiemTB());
        
        // executeUpdate() dùng cho INSERT, UPDATE, DELETE.
        // Nó trả về số nguyên là số dòng bị thay đổi trong DB (nếu > 0 nghĩa là thêm thành công)
        int rowsAffected = pstmt.executeUpdate();
        return rowsAffected > 0;
        
    } catch (SQLException e) {
        e.printStackTrace();
        return false;
    }
}
```
**Tại sao lại dùng `PreparedStatement` thay vì `Statement`?**
Nếu dùng `Statement` nối chuỗi: `INSERT INTO... VALUES ('" + maSV + "')`. Lỡ người dùng nhập `maSV` là đoạn mã hack như `' OR 1=1; DROP TABLE SinhVien; --` thì database của bạn sẽ bị xóa. `PreparedStatement` (dấu `?`) tự động bọc chuỗi lại cực kỳ an toàn, hacker có nhập mã hack thì nó cũng chỉ coi đó là "chữ".

### c. Các hàm còn lại (Update, Delete, Search)
Đều dùng chung 1 logic giống hệt hàm `addSinhVien`:
1. Viết câu SQL có dấu `?` (VD: `UPDATE SinhVien SET HoTen = ? WHERE MaSV = ?`)
2. Mở `Connection`
3. Tạo `PreparedStatement`
4. Dùng `set...()` để điền giá trị cho dấu `?`
5. Gọi `executeUpdate()` để chạy lệnh.

---

## 4. Tóm tắt luồng đi của dữ liệu (Khi bạn bấm nút "Thêm" trên giao diện)

1. **Người dùng** gõ "SV01", "Gia Vỹ" vào màn hình (UI).
2. File `MainFrame` (UI) lấy các chữ đó gom lại thành: `SinhVien sv = new SinhVien("SV01", "Gia Vỹ",...);`
3. UI nhờ vả thằng DAO: `dao.addSinhVien(sv);`
4. File `SinhVienDAO` lấy `sv` đó, mượn đường truyền từ `DatabaseHelper`.
5. `SinhVienDAO` đút dữ liệu vào câu SQL `INSERT INTO...` rồi bắn qua đường truyền tới PostgreSQL.
6. PostgreSQL nhận lệnh, lưu vào ổ cứng, báo về cho Java: "Lưu xong rồi (true)".
7. UI nhận được chữ "true", bật cửa sổ Popup: "Thêm thành công!" và vẽ lại bảng sinh viên.
