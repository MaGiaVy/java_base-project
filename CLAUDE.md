# CLAUDE.MD - Bộ Luật Giải Bài Tập Quản Lý Sinh Viên

**Stack:** Java Swing + PostgreSQL | Connection thủ công | JDBC thuần  
**Tất cả chức năng:** Load + Add/Edit/Delete + Search/Sort + Thống kê

---

## 🎯 CHIẾN LƯỢC CHUNG

- **Chia nhỏ từng Phase**: Không cố làm hết cùng lúc
- **Build từ dưới lên**: DB (sẵn) → DatabaseHelper → Model → CRUD → UI
- **Test ngay**: Sau mỗi bước, không đợi đến cuối
- **Exception handling đầy đủ**: try-catch ở mọi nơi

---

## 📋 THỨ TỰ THỰC HIỆN

### **Phase 1: Connection Setup**

```
✅ Tạo class DatabaseHelper.java
   - Connection string PostgreSQL
   - Hàm getConnection() → trả Connection
   - Hàm closeConnection()
✅ Test kết nối: In ra console "Connected!"
```

### **Phase 2: Model Class**

```
✅ Tạo class SinhVien.java
   - maSV, hoTen, lop, ngaySinh, diemTB
   - Constructor, Getter/Setter
   - toString() để debug
```

### **Phase 3: DAO (Data Access Object)**

```
✅ Tạo class SinhVienDAO.java
   - getAllSinhVien() → List<SinhVien>
   - addSinhVien(sv) → void
   - updateSinhVien(sv) → void
   - deleteSinhVien(maSV) → void
   - searchSinhVien(keyword) → List<SinhVien>
   - sortByName() → List<SinhVien>
   - sortByDiem() → List<SinhVien>
   - getTopStudent() → SinhVien
   - getCountSinhVien() → int
   - getAvgDiem() → double
```

### **Phase 4: UI - Main Frame**

```
✅ Tạo JFrame chính
✅ Layout:
   - JMenuBar: File, Chức năng, Về
   - JPanel trên: Input fields (maSV, hoTen, lop, ngaySinh, diemTB)
   - JPanel giữa: JTable hiển thị danh sách
   - JPanel dưới: Buttons (Thêm, Sửa, Xóa, Làm mới)
   - JPanel phải: Search, Sort, Thống kê
```

### **Phase 5: CRUD Operations**

```
✅ Thêm (Add):
   - Validate input
   - Check MaSV unique
   - Điểm 0-10
   - Gọi sinhVienDAO.addSinhVien()
   - Refresh table

✅ Hiển thị (Read):
   - Gọi sinhVienDAO.getAllSinhVien()
   - Bind vào JTable

✅ Sửa (Update):
   - Click row → Hiển thị lên TextBox
   - Chỉnh sửa → Validate
   - Gọi sinhVienDAO.updateSinhVien()
   - Refresh table

✅ Xóa (Delete):
   - Click row → JOptionPane confirm
   - Gọi sinhVienDAO.deleteSinhVien()
   - Refresh table
```

### **Phase 6: Search & Sort**

```
✅ Tìm kiếm:
   - TextBox nhập từ khóa
   - Gọi sinhVienDAO.searchSinhVien(keyword)
   - WHERE MaSV LIKE '%...%' OR HoTen LIKE '%...%'
   - Hiển thị lên table

✅ Sắp xếp:
   - Button "Theo Tên" → sortByName()
   - Button "Theo Điểm" → sortByDiem()
   - ORDER BY HoTen ASC / DiemTB DESC
```

### **Phase 7: Statistics**

```
✅ Thống kê:
   - Số lượng SV: COUNT(*)
   - Điểm TB chung: AVG(DiemTB)
   - SV điểm cao nhất: MAX(DiemTB)
   - Hiển thị trong panel riêng hoặc dialog
```

---

## 💻 CODE TEMPLATE

### **DatabaseHelper.java**

```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseHelper {
    private static final String URL = "jdbc:postgresql://localhost:5432/QuanLySinhVien";
    private static final String USER = "postgres";
    private static final String PASSWORD = ""; // Đổi password nếu cần

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("PostgreSQL Driver không tìm thấy!");
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        try {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (SQLException e) {
            System.err.println("Lỗi kết nối DB: " + e.getMessage());
            throw e;
        }
    }

    public static void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                System.err.println("Lỗi đóng connection: " + e.getMessage());
            }
        }
    }
}
```

### **SinhVien.java**

```java
import java.time.LocalDate;

public class SinhVien {
    private String maSV;
    private String hoTen;
    private String lop;
    private LocalDate ngaySinh;
    private double diemTB;

    // Constructor
    public SinhVien(String maSV, String hoTen, String lop, LocalDate ngaySinh, double diemTB) {
        this.maSV = maSV;
        this.hoTen = hoTen;
        this.lop = lop;
        this.ngaySinh = ngaySinh;
        this.diemTB = diemTB;
    }

    // Getter & Setter
    public String getMaSV() { return maSV; }
    public void setMaSV(String maSV) { this.maSV = maSV; }

    public String getHoTen() { return hoTen; }
    public void setHoTen(String hoTen) { this.hoTen = hoTen; }

    public String getLop() { return lop; }
    public void setLop(String lop) { this.lop = lop; }

    public LocalDate getNgaySinh() { return ngaySinh; }
    public void setNgaySinh(LocalDate ngaySinh) { this.ngaySinh = ngaySinh; }

    public double getDiemTB() { return diemTB; }
    public void setDiemTB(double diemTB) { this.diemTB = diemTB; }

    @Override
    public String toString() {
        return "SinhVien{" +
                "maSV='" + maSV + '\'' +
                ", hoTen='" + hoTen + '\'' +
                ", lop='" + lop + '\'' +
                ", ngaySinh=" + ngaySinh +
                ", diemTB=" + diemTB +
                '}';
    }
}
```

### **SinhVienDAO.java (Core CRUD)**

```java
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SinhVienDAO {

    // READ: Lấy tất cả sinh viên
    public List<SinhVien> getAllSinhVien() {
        List<SinhVien> list = new ArrayList<>();
        String sql = "SELECT * FROM SinhVien ORDER BY MaSV";

        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                SinhVien sv = new SinhVien(
                    rs.getString("MaSV"),
                    rs.getString("HoTen"),
                    rs.getString("Lop"),
                    rs.getDate("NgaySinh").toLocalDate(),
                    rs.getDouble("DiemTB")
                );
                list.add(sv);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi getAllSinhVien: " + e.getMessage());
        }

        return list;
    }

    // CREATE: Thêm sinh viên
    public boolean addSinhVien(SinhVien sv) {
        String sql = "INSERT INTO SinhVien (MaSV, HoTen, Lop, NgaySinh, DiemTB) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, sv.getMaSV());
            pstmt.setString(2, sv.getHoTen());
            pstmt.setString(3, sv.getLop());
            pstmt.setDate(4, Date.valueOf(sv.getNgaySinh()));
            pstmt.setDouble(5, sv.getDiemTB());

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi addSinhVien: " + e.getMessage());
            return false;
        }
    }

    // UPDATE: Sửa sinh viên
    public boolean updateSinhVien(SinhVien sv) {
        String sql = "UPDATE SinhVien SET HoTen = ?, Lop = ?, NgaySinh = ?, DiemTB = ? WHERE MaSV = ?";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, sv.getHoTen());
            pstmt.setString(2, sv.getLop());
            pstmt.setDate(3, Date.valueOf(sv.getNgaySinh()));
            pstmt.setDouble(4, sv.getDiemTB());
            pstmt.setString(5, sv.getMaSV());

            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi updateSinhVien: " + e.getMessage());
            return false;
        }
    }

    // DELETE: Xóa sinh viên
    public boolean deleteSinhVien(String maSV) {
        String sql = "DELETE FROM SinhVien WHERE MaSV = ?";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, maSV);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;

        } catch (SQLException e) {
            System.err.println("Lỗi deleteSinhVien: " + e.getMessage());
            return false;
        }
    }

    // SEARCH: Tìm kiếm
    public List<SinhVien> searchSinhVien(String keyword) {
        List<SinhVien> list = new ArrayList<>();
        String sql = "SELECT * FROM SinhVien WHERE MaSV ILIKE ? OR HoTen ILIKE ? OR Lop ILIKE ?";

        try (Connection conn = DatabaseHelper.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String searchKey = "%" + keyword + "%";
            pstmt.setString(1, searchKey);
            pstmt.setString(2, searchKey);
            pstmt.setString(3, searchKey);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                SinhVien sv = new SinhVien(
                    rs.getString("MaSV"),
                    rs.getString("HoTen"),
                    rs.getString("Lop"),
                    rs.getDate("NgaySinh").toLocalDate(),
                    rs.getDouble("DiemTB")
                );
                list.add(sv);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi searchSinhVien: " + e.getMessage());
        }

        return list;
    }

    // SORT: Theo tên
    public List<SinhVien> sortByName() {
        List<SinhVien> list = new ArrayList<>();
        String sql = "SELECT * FROM SinhVien ORDER BY HoTen ASC";

        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                SinhVien sv = new SinhVien(
                    rs.getString("MaSV"),
                    rs.getString("HoTen"),
                    rs.getString("Lop"),
                    rs.getDate("NgaySinh").toLocalDate(),
                    rs.getDouble("DiemTB")
                );
                list.add(sv);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi sortByName: " + e.getMessage());
        }

        return list;
    }

    // SORT: Theo điểm
    public List<SinhVien> sortByDiem() {
        List<SinhVien> list = new ArrayList<>();
        String sql = "SELECT * FROM SinhVien ORDER BY DiemTB DESC";

        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                SinhVien sv = new SinhVien(
                    rs.getString("MaSV"),
                    rs.getString("HoTen"),
                    rs.getString("Lop"),
                    rs.getDate("NgaySinh").toLocalDate(),
                    rs.getDouble("DiemTB")
                );
                list.add(sv);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi sortByDiem: " + e.getMessage());
        }

        return list;
    }

    // STATS: Sinh viên điểm cao nhất
    public SinhVien getTopStudent() {
        String sql = "SELECT * FROM SinhVien ORDER BY DiemTB DESC LIMIT 1";

        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return new SinhVien(
                    rs.getString("MaSV"),
                    rs.getString("HoTen"),
                    rs.getString("Lop"),
                    rs.getDate("NgaySinh").toLocalDate(),
                    rs.getDouble("DiemTB")
                );
            }
        } catch (SQLException e) {
            System.err.println("Lỗi getTopStudent: " + e.getMessage());
        }

        return null;
    }

    // STATS: Số lượng sinh viên
    public int getCountSinhVien() {
        String sql = "SELECT COUNT(*) as count FROM SinhVien";

        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            System.err.println("Lỗi getCountSinhVien: " + e.getMessage());
        }

        return 0;
    }

    // STATS: Điểm trung bình chung
    public double getAvgDiem() {
        String sql = "SELECT AVG(DiemTB) as avg FROM SinhVien";

        try (Connection conn = DatabaseHelper.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getDouble("avg");
            }
        } catch (SQLException e) {
            System.err.println("Lỗi getAvgDiem: " + e.getMessage());
        }

        return 0.0;
    }
}
```

---

## 🎨 UI DESIGN - Modern Swing Layout

### **Color Scheme**

```
Primary:    #2196F3 (Blue)
Accent:     #FF5722 (Orange)
Success:    #4CAF50 (Green)
Danger:     #F44336 (Red)
Background: #F5F5F5 (Light Gray)
Text:       #212121 (Dark)
Border:     #BDBDBD (Gray)
```

### **Typography**

```
Title:      Font("Segoe UI", 18, Bold)
Header:     Font("Segoe UI", 12, Bold)
Body:       Font("Segoe UI", 11, Plain)
Button:     Font("Segoe UI", 11, Bold)
```

### **Layout Structure (TabPane + Panels)**

```
┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
┃ 🎓 QUẢN LÝ SINH VIÊN - HỆ THỐNG QUẢN LÝ                    ┃
┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫
┃ [📋 Danh Sách] [➕ Thêm/Sửa] [🔍 Tìm Kiếm] [📊 Thống Kê]  ┃
┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┫
│                                                              │
│  TAB 1: DANH SÁCH SINH VIÊN                                │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ [🔄 Làm mới]  [📥 Import]  [📤 Export]              │  │
│  ├──────────────────────────────────────────────────────┤  │
│  │ Mã SV     │ Họ Tên        │ Lớp  │ Ngày Sinh │ Điểm │  │
│  ├───────────┼───────────────┼──────┼───────────┼──────┤  │
│  │ SV001     │ Nguyễn Văn A  │ IT01 │ 01/01/05  │ 8.5  │  │
│  │ SV002     │ Trần Thị B    │ IT02 │ 02/02/05  │ 9.0  │  │
│  │ ...       │ ...           │ ...  │ ...       │ ...  │  │
│  └──────────────────────────────────────────────────────┘  │
│  Status: Tổng 50 sinh viên                                │
│                                                              │
│  TAB 2: THÊM / CHỈNH SỬA SINH VIÊN                         │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ ┌─ Thông Tin Sinh Viên ──────────────────────────┐  │  │
│  │ │ Mã SV:*      [___________]                     │  │  │
│  │ │ Họ tên:*     [___________]                     │  │  │
│  │ │ Lớp:*        [___________]                     │  │  │
│  │ │ Ngày sinh:*  [___________]  📅                 │  │  │
│  │ │ Điểm TB:*    [___________] (0-10)             │  │  │
│  │ └─────────────────────────────────────────────────┘  │  │
│  │  [✅ Lưu]  [🔄 Làm mới]  [❌ Hủy]                    │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  TAB 3: TÌM KIẾM                                           │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Nhập từ khóa: [___________________] 🔍              │  │
│  │ Tìm kiếm theo: ☑ Mã SV  ☑ Họ tên  ☑ Lớp           │  │
│  │ Kết quả: 5 sinh viên                               │  │
│  │ ┌──────────────────────────────────────────────────┐ │  │
│  │ │ SV001 │ Nguyễn Văn A │ IT01 │ 01/01/05 │ 8.5   │ │  │
│  │ │ ...   │ ...          │ ...  │ ...      │ ...   │ │  │
│  │ └──────────────────────────────────────────────────┘ │  │
│  │ [📋 Sắp xếp A-Z] [📊 Sắp xếp theo Điểm]            │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
│  TAB 4: THỐNG KÊ & BIỂU ĐỒ                                 │
│  ┌─────────────────────┬───────────────────────────────┐   │
│  │ ┌─ Tóm tắt ────┐    │                               │   │
│  │ │ 👥 Tổng SV:  │    │   📊 BIỂU ĐỒ ĐIỂM           │   │
│  │ │    50 người  │    │                               │   │
│  │ │              │    │   ▁▂▃▄▅▆▇█ (Histogram)      │   │
│  │ │ 📈 Điểm TB:  │    │                               │   │
│  │ │    7.5/10    │    │                               │   │
│  │ │              │    │                               │   │
│  │ │ 🏆 Cao nhất: │    │                               │   │
│  │ │    9.8 (A)   │    │   📈 PHÂN BỐ LỚPSV           │   │
│  │ │              │    │                               │   │
│  │ │ 📊 Top 3 SV: │    │   ◉ 35% IT01  ◉ 40% IT02    │   │
│  │ │  1. A (9.8)  │    │   ◉ 25% IT03  (Pie Chart)   │   │
│  │ │  2. B (9.5)  │    │                               │   │
│  │ │  3. C (9.3)  │    │                               │   │
│  │ └──────────────┘    │                               │   │
│  └─────────────────────┴───────────────────────────────┘   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### **Modern UI Features**

- ✅ Tab navigation (JTabbedPane)
- ✅ Material Design colors
- ✅ Icons trên buttons
- ✅ JTable + Column sorter
- ✅ Date picker (JDateChooser)
- ✅ Charts (JFreeChart)
- ✅ Status bar hiển thị info
- ✅ Responsive layout (GroupLayout hoặc MigLayout)

---

## ⚠️ LỖI THƯỜNG GẶP

| Vấn đề                     | Giải Pháp                                                                 |
| -------------------------- | ------------------------------------------------------------------------- |
| **PostgreSQL Driver lỗi**  | Thêm `postgresql-VERSION.jar` vào classpath (Maven/Gradle)                |
| **Connection refused**     | Kiểm tra: DB chạy? URL đúng? User/password đúng?                          |
| **MaSV trùng**             | INSERT → catch `SQLException` cho constraint violation                    |
| **Điểm ngoài 0-10**        | Validate trước khi INSERT/UPDATE                                          |
| **JTable không update**    | Gọi `loadTable()` sau CRUD, hoặc dùng `TableModel.fireTableDataChanged()` |
| **LocalDate parse lỗi**    | Dùng `DateTimeFormatter` hoặc `java.sql.Date`                             |
| **Foreign Key violations** | Check 9 bảng còn lại, có ràng buộc gì không?                              |

---

## ✅ CHECKLIST HOÀN THÀNH

- [ ] DatabaseHelper test được
- [ ] SinhVien model OK
- [ ] SinhVienDAO: getAllSinhVien() work
- [ ] JFrame main UI OK, layout rõ ràng
- [ ] Thêm sinh viên (validate, unique MaSV)
- [ ] Sửa sinh viên (load → edit → update)
- [ ] Xóa sinh viên (confirm → delete)
- [ ] Tìm kiếm (MaSV/HoTen/Lop)
- [ ] Sắp xếp (Tên, Điểm)
- [ ] Thống kê (SL, TB, Max)
- [ ] Exception handling đầy đủ
- [ ] Refresh table sau mỗi thao tác
- [ ] UI sạch sẽ, rõ ràng
- [ ] Code comment đủ

---

---

## 📊 CHARTS & STATISTICS - JFreeChart Integration

### **Thêm Dependency (Maven)**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.jfree</groupId>
    <artifactId>jfreechart</artifactId>
    <version>1.5.3</version>
</dependency>
```

Hoặc download `.jar` thêm vào classpath.

### **Chart Types**

#### **1️⃣ Histogram - Phân bố điểm**

```java
// Hiển thị phân bố điểm (0-2, 2-4, 4-6, 6-8, 8-10)
private void createScoreHistogram() {
    DefaultCategoryDataset dataset = new DefaultCategoryDataset();

    // Đếm SV theo khoảng điểm từ DB
    int range0_2 = countStudentsByScoreRange(0, 2);
    int range2_4 = countStudentsByScoreRange(2, 4);
    int range4_6 = countStudentsByScoreRange(4, 6);
    int range6_8 = countStudentsByScoreRange(6, 8);
    int range8_10 = countStudentsByScoreRange(8, 10);

    dataset.addValue(range0_2, "Sinh viên", "0-2");
    dataset.addValue(range2_4, "Sinh viên", "2-4");
    dataset.addValue(range4_6, "Sinh viên", "4-6");
    dataset.addValue(range6_8, "Sinh viên", "6-8");
    dataset.addValue(range8_10, "Sinh viên", "8-10");

    JFreeChart chart = ChartFactory.createBarChart(
        "Phân bố Điểm Trung Bình",
        "Khoảng Điểm",
        "Số Sinh Viên",
        dataset,
        PlotOrientation.VERTICAL,
        false, true, false
    );

    ChartPanel chartPanel = new ChartPanel(chart);
    chartPanel.setPreferredSize(new Dimension(500, 300));
    panelChart1.add(chartPanel);
}

private int countStudentsByScoreRange(double min, double max) {
    String sql = "SELECT COUNT(*) FROM SinhVien WHERE DiemTB >= " + min + " AND DiemTB < " + max;
    try (Connection conn = DatabaseHelper.getConnection();
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery(sql)) {
        if (rs.next()) return rs.getInt(1);
    } catch (SQLException e) {
        e.printStackTrace();
    }
    return 0;
}
```

#### **2️⃣ Pie Chart - Phân bố theo lớp**

```java
private void createClassDistribution() {
    DefaultPieDataset dataset = new DefaultPieDataset();

    // Lấy tất cả lớp và đếm SV mỗi lớp
    String sql = "SELECT Lop, COUNT(*) as count FROM SinhVien GROUP BY Lop";
    try (Connection conn = DatabaseHelper.getConnection();
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery(sql)) {

        while (rs.next()) {
            String lop = rs.getString("Lop");
            int count = rs.getInt("count");
            dataset.setValue(lop, count);
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }

    JFreeChart chart = ChartFactory.createPieChart(
        "Phân bố Sinh Viên theo Lớp",
        dataset,
        true, true, false
    );

    ChartPanel chartPanel = new ChartPanel(chart);
    chartPanel.setPreferredSize(new Dimension(500, 300));
    panelChart2.add(chartPanel);
}
```

#### **3️⃣ Line Chart - Xu hướng điểm theo lớp**

```java
private void createScoreTrendByClass() {
    DefaultCategoryDataset dataset = new DefaultCategoryDataset();

    // Lấy điểm TB cho mỗi lớp
    String sql = "SELECT Lop, AVG(DiemTB) as avg FROM SinhVien GROUP BY Lop";
    try (Connection conn = DatabaseHelper.getConnection();
         Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery(sql)) {

        while (rs.next()) {
            String lop = rs.getString("Lop");
            double avg = rs.getDouble("avg");
            dataset.addValue(avg, "Điểm TB", lop);
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }

    JFreeChart chart = ChartFactory.createLineChart(
        "Điểm Trung Bình theo Lớp",
        "Lớp",
        "Điểm TB",
        dataset,
        PlotOrientation.VERTICAL,
        true, true, false
    );

    ChartPanel chartPanel = new ChartPanel(chart);
    chartPanel.setPreferredSize(new Dimension(500, 300));
    panelChart3.add(chartPanel);
}
```

### **Statistics Panel - Code Template**

```java
private void updateStatisticsPanel() {
    SinhVienDAO dao = new SinhVienDAO();

    // Tổng sinh viên
    int totalCount = dao.getCountSinhVien();
    labelTotalStudents.setText(String.valueOf(totalCount));

    // Điểm TB chung
    double avgScore = dao.getAvgDiem();
    labelAvgScore.setText(String.format("%.2f", avgScore));

    // SV điểm cao nhất
    SinhVien topStudent = dao.getTopStudent();
    if (topStudent != null) {
        labelTopStudent.setText(topStudent.getHoTen() + " (" + topStudent.getDiemTB() + ")");
    }

    // Top 5 SV cao nhất
    List<SinhVien> top5 = dao.sortByDiem().stream()
        .limit(5)
        .collect(Collectors.toList());

    StringBuilder top5Text = new StringBuilder("<html>");
    for (int i = 0; i < top5.size(); i++) {
        SinhVien sv = top5.get(i);
        top5Text.append((i + 1)).append(". ").append(sv.getHoTen())
                .append(" (").append(sv.getDiemTB()).append(")<br>");
    }
    top5Text.append("</html>");
    labelTop5.setText(top5Text.toString());

    // Tạo biểu đồ
    createScoreHistogram();
    createClassDistribution();
    createScoreTrendByClass();
}
```

### **Statistics UI (JPanel Layout)**

```java
private void initStatisticsTab() {
    JPanel panelStats = new JPanel(new BorderLayout());

    // Left panel: Summary
    JPanel leftPanel = new JPanel();
    leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
    leftPanel.setBorder(BorderFactory.createTitledBorder("📊 Tóm tắt"));
    leftPanel.setPreferredSize(new Dimension(200, 400));

    labelTotalStudents = new JLabel("👥 Tổng SV: 0");
    labelTotalStudents.setFont(new Font("Segoe UI", Font.BOLD, 12));

    labelAvgScore = new JLabel("📈 Điểm TB: 0.00");
    labelAvgScore.setFont(new Font("Segoe UI", Font.BOLD, 12));

    labelTopStudent = new JLabel("🏆 Cao nhất: -");
    labelTopStudent.setFont(new Font("Segoe UI", Font.BOLD, 12));

    labelTop5 = new JLabel("<html>Top 5<br>...<br></html>");
    labelTop5.setFont(new Font("Segoe UI", Font.PLAIN, 10));

    leftPanel.add(labelTotalStudents);
    leftPanel.add(Box.createVerticalStrut(20));
    leftPanel.add(labelAvgScore);
    leftPanel.add(Box.createVerticalStrut(20));
    leftPanel.add(labelTopStudent);
    leftPanel.add(Box.createVerticalStrut(20));
    leftPanel.add(new JLabel("📊 Top 5 SV:"));
    leftPanel.add(labelTop5);

    // Right panel: Charts
    JPanel rightPanel = new JPanel(new GridLayout(2, 1));

    panelChart1 = new JPanel();
    panelChart2 = new JPanel();

    rightPanel.add(panelChart1);
    rightPanel.add(panelChart2);

    panelStats.add(leftPanel, BorderLayout.WEST);
    panelStats.add(rightPanel, BorderLayout.CENTER);

    tabbedPane.addTab("📊 Thống Kê", panelStats);
}
```

---

## 🚀 PRO TIPS

### **1. Parameterized Query (tránh SQL Injection)**

```java
// ❌ KHÔNG
String sql = "SELECT * FROM SinhVien WHERE MaSV = '" + maSV + "'";

// ✅ ĐÚNG
String sql = "SELECT * FROM SinhVien WHERE MaSV = ?";
PreparedStatement pstmt = conn.prepareStatement(sql);
pstmt.setString(1, maSV);
```

### **2. Try-with-resources (auto close)**

```java
try (Connection conn = DatabaseHelper.getConnection();
     PreparedStatement pstmt = conn.prepareStatement(sql)) {
    // Code here
} catch (SQLException e) {
    e.printStackTrace();
}
```

### **3. DefaultTableModel để update JTable**

```java
DefaultTableModel model = new DefaultTableModel();
model.setColumnIdentifiers(new String[]{"MaSV", "HoTen", "Lop", "NgaySinh", "DiemTB"});

for (SinhVien sv : list) {
    model.addRow(new Object[]{sv.getMaSV(), sv.getHoTen(), sv.getLop(), sv.getNgaySinh(), sv.getDiemTB()});
}

jTable1.setModel(model);
```

### **4. Abstract TableModel cho performance**

```java
class SinhVienTableModel extends AbstractTableModel {
    private List<SinhVien> data;
    // ... override getRowCount(), getColumnCount(), getValueAt()
}
```

### **5. Dispose Connection trong finally hoặc try-with-resources**

- Không để Connection mở mãi
- Dùng try-with-resources tự động close

---

---

## 🎨 UI DESIGN PROMPT

**Để AI thiết kế giao diện modern & đẹp, sử dụng file `UI_DESIGN_PROMPT.md`**

Cách dùng:

1. Copy nội dung từ `UI_DESIGN_PROMPT.md`
2. Paste vào chat với Claude
3. Request: "Viết code MainFrame.java theo prompt này"
4. Claude sẽ tạo giao diện professional với:
   - Color scheme Material Design
   - 4 tabs (List, Add/Edit, Search, Stats)
   - Modern buttons & styling
   - Ready to integrate với DAO

**Prompt có phần:**

- 🎯 Requirements chi tiết
- 🖌️ Design Guidelines (spacing, colors, fonts)
- 💻 Code Structure recommendation
- 🎬 Advanced Features (optional)

---

**Cập nhật lần cuối:** Java Swing + PostgreSQL, Full CRUD + Stats + Charts

# CLAUDE.MD - Bài 1: Tạo Form Đăng Nhập

## 📋 PROJECT SPEC

- **Framework:** Java Swing (Desktop UI)
- **Chức năng:** Simple login form với hardcoded credentials
- **Credentials:** username = "admin", password = "123"
- **Thư viện:** Chỉ dùng Swing (không database)

---

## 🎯 LUẬT CHUNG

### **1. Form Components (Bắt buộc)**

```
✅ JFrame (Main window)
✅ 2x JLabel (Tên đăng nhập, Mật khẩu)
✅ 2x JTextField (username input)
✅ 1x JPasswordField (password input - ẩn text)
✅ 2x JButton (Đăng nhập, Thoát)
✅ 1x JLabel (Message display - hiển thị kết quả)
```

### **2. Layout**

- Dùng **GridLayout** hoặc **BorderLayout** hoặc **GroupLayout**
- Layout đơn giản, rõ ràng, tập trung
- Padding/margin hợp lý (10-15px)

### **3. Logic Đăng Nhập**

- **Valid:** username == "admin" AND password == "123"
  - Hiển thị: "Đăng nhập thành công" (text màu xanh hoặc JOptionPane)
  - Có thể: đóng form hoặc navigate sang trang khác
- **Invalid:** Bất kỳ trường nào sai
  - Hiển thị: "Sai tên đăng nhập hoặc mật khẩu" (text màu đỏ hoặc JOptionPane)
  - Clear password field
  - Focus vào username field

### **4. Button Actions**

- **Đăng nhập (Login):**
  - Lấy text từ JTextField
  - Validate
  - Hiển thị message
- **Thoát (Exit):**
  - Đóng frame (System.exit(0))

### **5. UX Enhancement (Tùy chọn)**

- Enter key trigger login button
- Focus vào username lúc startup
- Disable login button nếu fields rỗng (optional)
- Clear fields khi thất bại

---

## ✅ CHECKLIST

- [ ] JFrame title "Đăng Nhập"
- [ ] 2 JLabel + 2 JTextField/JPasswordField
- [ ] 2 Buttons (Đăng nhập, Thoát)
- [ ] 1 JLabel hiển thị kết quả
- [ ] Validation logic: admin / 123
- [ ] Success message (xanh) + failure message (đỏ)
- [ ] Clear password sau failed login
- [ ] Thoát button đóng app
- [ ] Focus/layout hợp lý
- [ ] Test: Đúng login, sai login, thoát

---

## 🎨 UI MOCKUP

```
┌─────────────────────────────────┐
│        🔐 ĐĂNG NHẬP             │
├─────────────────────────────────┤
│                                 │
│ Tên đăng nhập:                  │
│ [_________________________]      │
│                                 │
│ Mật khẩu:                       │
│ [_________________________]      │
│                                 │
│ [Đăng nhập]  [Thoát]            │
│                                 │
│ Kết quả: _______________        │
│                                 │
└─────────────────────────────────┘
```

---

## ⚡ KEY RULES

1. **Password Field:** Dùng JPasswordField, KHÔNG phải JTextField
2. **Message:** Hiển thị trên JLabel, hoặc JOptionPane (tuỳ)
3. **Hardcoded:** Không cần database, credentials fix cứng
4. **Case sensitive:** username/password phải match exact
5. **No trim():** Xử lý như-là (user có thể nhập spaces)

---

**Cập nhật lần cuối:** Bài tập 1 - Simple Login Form
