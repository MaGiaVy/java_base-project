import java.util.List;

public class TestVerifySort {
    public static void main(String[] args) {
        SinhVienDAO dao = new SinhVienDAO();
        List<SinhVien> sorted = dao.sortByName();
        System.out.println("=== SAP XEP THEO TEN (chu cuoi) ===");
        for (int i = 0; i < sorted.size(); i++) {
            String hoTen = sorted.get(i).getHoTen();
            String[] parts = hoTen.split(" ");
            String ten = parts[parts.length - 1];
            System.out.println((i + 1) + ". " + hoTen + "  -->  Ten: " + ten);
        }
    }
}
