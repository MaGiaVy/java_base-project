import java.time.LocalDate;
import java.util.List;

/**
 * TestAll.java - Script kiểm thử tự động
 * Kiểm tra từng chức năng theo yêu cầu CLAUDE.md
 */
public class TestAll {

    private static SinhVienDAO dao = new SinhVienDAO();
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("==============================================");
        System.out.println("  QA TEST - KIEM TRA TOAN BO CHUC NANG");
        System.out.println("==============================================\n");

        // Phase 1: Test ket noi DB
        testDBConnection();

        // Phase 2: Test Model
        testModel();

        // Phase 3: Test DAO - CRUD
        testDAO_Read();
        testDAO_Add();
        testDAO_Update();
        testDAO_Delete();

        // Phase 6: Test Search & Sort
        testSearch();
        testSortByName();
        testSortByDiem();

        // Phase 7: Test Statistics
        testStats();

        // Phase 5: Test Validation (logic trong SinhVien)
        testValidation();

        // Ket qua
        System.out.println("\n==============================================");
        System.out.println("  KET QUA: " + passed + " PASSED / " + failed + " FAILED");
        System.out.println("  Tong: " + (passed + failed) + " test cases");
        System.out.println("==============================================");
    }

    // ===== PHASE 1: DB CONNECTION =====
    static void testDBConnection() {
        System.out.println("--- PHASE 1: DB CONNECTION ---");
        try {
            java.sql.Connection conn = DatabaseHelper.getConnection();
            check("Ket noi PostgreSQL", conn != null && !conn.isClosed());
            DatabaseHelper.closeConnection(conn);
            check("Dong ket noi", true);
        } catch (Exception e) {
            check("Ket noi PostgreSQL", false);
            System.err.println("  LOI: " + e.getMessage());
        }
        System.out.println();
    }

    // ===== PHASE 2: MODEL =====
    static void testModel() {
        System.out.println("--- PHASE 2: MODEL (SinhVien) ---");
        SinhVien sv = new SinhVien("TEST01", "Nguyen Van Test", "IT01",
                LocalDate.of(2003, 1, 15), 8.5);
        check("Constructor + getMaSV()", sv.getMaSV().equals("TEST01"));
        check("getHoTen()", sv.getHoTen().equals("Nguyen Van Test"));
        check("getLop()", sv.getLop().equals("IT01"));
        check("getNgaySinh()", sv.getNgaySinh().equals(LocalDate.of(2003, 1, 15)));
        check("getDiemTB()", sv.getDiemTB() == 8.5);
        check("toString() khong null", sv.toString() != null && !sv.toString().isEmpty());

        // Test setter
        sv.setHoTen("Tran Thi B");
        check("setHoTen() + getHoTen()", sv.getHoTen().equals("Tran Thi B"));
        System.out.println();
    }

    // ===== PHASE 3: DAO - READ =====
    static void testDAO_Read() {
        System.out.println("--- PHASE 3: DAO - getAllSinhVien() ---");
        List<SinhVien> list = dao.getAllSinhVien();
        check("getAllSinhVien() tra ve list", list != null);
        check("List co du lieu (size > 0)", list.size() > 0);
        if (!list.isEmpty()) {
            SinhVien first = list.get(0);
            check("SV dau tien co MaSV", first.getMaSV() != null && !first.getMaSV().isEmpty());
            check("SV dau tien co HoTen", first.getHoTen() != null && !first.getHoTen().isEmpty());
        }
        System.out.println();
    }

    // ===== PHASE 3: DAO - ADD =====
    static void testDAO_Add() {
        System.out.println("--- PHASE 5: DAO - addSinhVien() ---");
        // Xoa truoc de tranh trung MaSV
        dao.deleteSinhVien("QA_TEST");

        SinhVien sv = new SinhVien("QA_TEST", "QA Tester", "QA01",
                LocalDate.of(2000, 6, 15), 7.5);
        boolean added = dao.addSinhVien(sv);
        check("Them sinh vien moi (QA_TEST)", added);

        // Them trung MaSV phai that bai
        boolean addDuplicate = dao.addSinhVien(sv);
        check("Them trung MaSV -> FAIL (dung)", !addDuplicate);
        System.out.println();
    }

    // ===== PHASE 3: DAO - UPDATE =====
    static void testDAO_Update() {
        System.out.println("--- PHASE 5: DAO - updateSinhVien() ---");
        SinhVien sv = new SinhVien("QA_TEST", "QA Tester Updated", "QA02",
                LocalDate.of(2000, 6, 15), 9.0);
        boolean updated = dao.updateSinhVien(sv);
        check("Sua sinh vien (QA_TEST)", updated);

        // Verify: doc lai tu DB
        List<SinhVien> list = dao.searchSinhVien("QA_TEST");
        if (!list.isEmpty()) {
            SinhVien found = list.get(0);
            check("HoTen da duoc cap nhat", found.getHoTen().equals("QA Tester Updated"));
            check("Lop da duoc cap nhat", found.getLop().equals("QA02"));
            check("DiemTB da duoc cap nhat", found.getDiemTB() == 9.0);
        } else {
            check("Tim thay SV sau khi sua", false);
        }
        System.out.println();
    }

    // ===== PHASE 3: DAO - DELETE =====
    static void testDAO_Delete() {
        System.out.println("--- PHASE 5: DAO - deleteSinhVien() ---");
        boolean deleted = dao.deleteSinhVien("QA_TEST");
        check("Xoa sinh vien (QA_TEST)", deleted);

        // Verify: khong tim thay nua
        List<SinhVien> list = dao.searchSinhVien("QA_TEST");
        check("SV da bi xoa khong con trong DB", list.isEmpty());

        // Xoa MaSV khong ton tai
        boolean deleteFake = dao.deleteSinhVien("KHONG_TON_TAI_999");
        check("Xoa MaSV khong ton tai -> FAIL (dung)", !deleteFake);
        System.out.println();
    }

    // ===== PHASE 6: SEARCH =====
    static void testSearch() {
        System.out.println("--- PHASE 6: SEARCH ---");
        // Tim kiem chung
        List<SinhVien> results = dao.searchSinhVien("SV");
        check("searchSinhVien('SV') tra ve list", results != null);
        System.out.println("  -> Tim thay " + results.size() + " ket qua voi tu khoa 'SV'");

        // Tim kiem khong co ket qua
        List<SinhVien> empty = dao.searchSinhVien("XYZXYZXYZ_KHONG_TON_TAI");
        check("Tim tu khoa khong ton tai -> rong", empty.isEmpty());
        System.out.println();
    }

    // ===== PHASE 6: SORT BY NAME =====
    static void testSortByName() {
        System.out.println("--- PHASE 6: SORT BY NAME ---");
        List<SinhVien> sorted = dao.sortByName();
        check("sortByName() tra ve list", sorted != null && !sorted.isEmpty());

        // Kiem tra thu tu A-Z
        boolean inOrder = true;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).getHoTen().compareToIgnoreCase(sorted.get(i - 1).getHoTen()) < 0) {
                inOrder = false;
                break;
            }
        }
        check("Sap xep theo ten A-Z dung thu tu", inOrder);
        System.out.println();
    }

    // ===== PHASE 6: SORT BY DIEM =====
    static void testSortByDiem() {
        System.out.println("--- PHASE 6: SORT BY DIEM ---");
        List<SinhVien> sorted = dao.sortByDiem();
        check("sortByDiem() tra ve list", sorted != null && !sorted.isEmpty());

        // Kiem tra thu tu giam dan
        boolean inOrder = true;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).getDiemTB() > sorted.get(i - 1).getDiemTB()) {
                inOrder = false;
                break;
            }
        }
        check("Sap xep theo diem giam dan dung thu tu", inOrder);
        System.out.println();
    }

    // ===== PHASE 7: STATISTICS =====
    static void testStats() {
        System.out.println("--- PHASE 7: STATISTICS ---");
        int count = dao.getCountSinhVien();
        check("getCountSinhVien() > 0", count > 0);
        System.out.println("  -> Tong SV: " + count);

        double avg = dao.getAvgDiem();
        check("getAvgDiem() trong khoang 0-10", avg >= 0 && avg <= 10);
        System.out.println("  -> Diem TB: " + String.format("%.2f", avg));

        SinhVien top = dao.getTopStudent();
        check("getTopStudent() khong null", top != null);
        if (top != null) {
            check("Top student co diem >= avg", top.getDiemTB() >= avg);
            System.out.println("  -> Top SV: " + top.getHoTen() + " (" + top.getDiemTB() + ")");
        }
        System.out.println();
    }

    // ===== VALIDATION =====
    static void testValidation() {
        System.out.println("--- PHASE 5: VALIDATION ---");
        check("Diem 0 hop le", SinhVien.kiemTraDiem(0));
        check("Diem 10 hop le", SinhVien.kiemTraDiem(10));
        check("Diem 5.5 hop le", SinhVien.kiemTraDiem(5.5));
        check("Diem -1 KHONG hop le", !SinhVien.kiemTraDiem(-1));
        check("Diem 11 KHONG hop le", !SinhVien.kiemTraDiem(11));
        check("Diem 100 KHONG hop le", !SinhVien.kiemTraDiem(100));
        System.out.println();
    }

    // ===== HELPER =====
    static void check(String testName, boolean result) {
        if (result) {
            System.out.println("  [PASS] " + testName);
            passed++;
        } else {
            System.out.println("  [FAIL] " + testName + "  <--- LOI!");
            failed++;
        }
    }
}
