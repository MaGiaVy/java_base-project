import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SinhVienDAO {

    // Helper: Chuyển ResultSet thành SinhVien
    private SinhVien mapRow(ResultSet rs) throws SQLException {
        return new SinhVien(
                rs.getString("MaSV"),
                rs.getString("HoTen"),
                rs.getString("Lop"),
                rs.getDate("NgaySinh") != null ? rs.getDate("NgaySinh").toLocalDate() : null,
                rs.getDouble("DiemTB"));
    }

    // READ: Lấy tất cả sinh viên
    public List<SinhVien> getAllSinhVien() {
        List<SinhVien> list = new ArrayList<>();
        String sql = "SELECT * FROM SinhVien ORDER BY MaSV";

        try (Connection conn = DatabaseHelper.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(mapRow(rs));
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
            pstmt.setDate(4, sv.getNgaySinh() != null ? Date.valueOf(sv.getNgaySinh()) : null);
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
            pstmt.setDate(3, sv.getNgaySinh() != null ? Date.valueOf(sv.getNgaySinh()) : null);
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

    // SEARCH: Tìm kiếm theo MaSV, HoTen, Lop
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
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi searchSinhVien: " + e.getMessage());
        }

        return list;
    }

    // SORT: Theo tên (A-Z)
    public List<SinhVien> sortByName() {
        List<SinhVien> list = new ArrayList<>();
        String sql = "SELECT * FROM SinhVien ORDER BY SUBSTRING(HoTen FROM '[^ ]+$') ASC, HoTen ASC";

        try (Connection conn = DatabaseHelper.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("Lỗi sortByName: " + e.getMessage());
        }

        return list;
    }

    // SORT: Theo điểm (cao → thấp)
    public List<SinhVien> sortByDiem() {
        List<SinhVien> list = new ArrayList<>();
        String sql = "SELECT * FROM SinhVien ORDER BY DiemTB DESC";

        try (Connection conn = DatabaseHelper.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(mapRow(rs));
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
                return mapRow(rs);
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
