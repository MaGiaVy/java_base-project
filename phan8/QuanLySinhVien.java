import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class QuanLySinhVien {

    private static List<SinhVien> danhSach;
    private static Scanner scanner;

    public static void main(String[] args) {
        danhSach = new ArrayList<>();
        scanner = new Scanner(System.in);

        // Thêm dữ liệu mẫu
        themDuLieuMau();

        boolean running = true;

        while (running) {
            hienThiMenu();
            int choice = nhapLuaChon();

            switch (choice) {
                case 1:
                    hienThiDanhSach();
                    break;
                case 2:
                    themSinhVien();
                    break;
                case 3:
                    suaThongTinSinhVien();
                    break;
                case 4:
                    xoaSinhVien();
                    break;
                case 5:
                    timKiemSinhVien();
                    break;
                case 6:
                    sapXepTheoDiem();
                    break;
                case 7:
                    thongKesoLuong();
                    break;
                case 8:
                    thongKeDiemTrungBinh();
                    break;
                case 9:
                    lamMoiDuLieu();
                    break;
                case 0:
                    System.out.println("Cảm ơn bạn đã sử dụng chương trình!");
                    running = false;
                    break;
                default:
                    System.out.println("❌ Lựa chọn không hợp lệ!");
            }
        }

        scanner.close();
    }

    // ===== HÀM HIỂN THỊ MENU =====
    private static void hienThiMenu() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("                  QUẢN LÝ SINH VIÊN");
        System.out.println("=".repeat(70));
        System.out.println("1.  Hiển thị danh sách sinh viên");
        System.out.println("2.  Thêm sinh viên");
        System.out.println("3.  Sửa thông tin sinh viên");
        System.out.println("4.  Xóa sinh viên");
        System.out.println("5.  Tìm kiếm sinh viên");
        System.out.println("6.  Sắp xếp theo điểm trung bình");
        System.out.println("7.  Thống kê số lượng sinh viên");
        System.out.println("8.  Thống kê điểm trung bình của lớp");
        System.out.println("9.  Làm mới dữ liệu");
        System.out.println("0.  Thoát");
        System.out.println("=".repeat(70));
    }

    // ===== NHẬP LỰA CHỌN =====
    private static int nhapLuaChon() {
        try {
            System.out.print("Nhập lựa chọn: ");
            int choice = scanner.nextInt();
            scanner.nextLine(); // Xóa buffer
            return choice;
        } catch (Exception e) {
            scanner.nextLine();
            System.out.println("❌ Vui lòng nhập số hợp lệ!");
            return -1;
        }
    }

    // ===== 1. HIỂN THỊ DANH SÁCH =====
    private static void hienThiDanhSach() {
        if (danhSach.isEmpty()) {
            System.out.println("❌ Danh sách trống!");
            return;
        }

        System.out.println("\n" + "=".repeat(70));
        System.out.println("                   DANH SÁCH SINH VIÊN");
        System.out.println("=".repeat(70));
        System.out.printf("%-10s | %-25s | %-10s | %-8s%n",
                "Mã SV", "Họ Tên", "Lớp", "Điểm TB");
        System.out.println("-".repeat(70));

        for (int i = 0; i < danhSach.size(); i++) {
            System.out.println((i + 1) + ". " + danhSach.get(i));
        }

        System.out.println("=".repeat(70));
        System.out.println("Tổng số sinh viên: " + danhSach.size());
    }

    // ===== 2. THÊM SINH VIÊN =====
    private static void themSinhVien() {
        try {
            System.out.println("\n--- THÊM SINH VIÊN ---");

            // Nhập mã sinh viên
            System.out.print("Nhập mã sinh viên: ");
            String maSV = scanner.nextLine();

            // Kiểm tra mã đã tồn tại chưa
            for (SinhVien sv : danhSach) {
                if (sv.getMaSV().equals(maSV)) {
                    System.out.println("❌ Mã sinh viên đã tồn tại!");
                    return;
                }
            }

            // Nhập họ tên
            System.out.print("Nhập họ tên: ");
            String hoTen = scanner.nextLine();

            // Nhập lớp
            System.out.print("Nhập lớp: ");
            String lop = scanner.nextLine();

            // Nhập điểm trung bình
            System.out.print("Nhập điểm trung bình (0-10): ");
            double diemTB = scanner.nextDouble();
            scanner.nextLine();

            // Kiểm tra điểm
            if (!SinhVien.kiemTraDiem(diemTB)) {
                System.out.println("❌ Điểm phải từ 0 đến 10!");
                return;
            }

            // Thêm sinh viên vào danh sách
            danhSach.add(new SinhVien(maSV, hoTen, lop, diemTB, ngaysinh));
            System.out.println("✅ Thêm sinh viên thành công!");

        } catch (Exception e) {
            scanner.nextLine();
            System.out.println("❌ Lỗi nhập dữ liệu!");
        }
    }

    // ===== 3. SỬA THÔNG TIN SINH VIÊN =====
    private static void suaThongTinSinhVien() {
        try {
            System.out.println("\n--- SỬA THÔNG TIN SINH VIÊN ---");

            System.out.print("Nhập mã sinh viên cần sửa: ");
            String maSV = scanner.nextLine();

            // Tìm sinh viên
            SinhVien sv = timSinhVienTheoMa(maSV);
            if (sv == null) {
                System.out.println("❌ Không tìm thấy sinh viên!");
                return;
            }

            System.out.println("\nThông tin hiện tại: " + sv);

            System.out.println("\nChọn thông tin cần sửa:");
            System.out.println("1. Họ tên");
            System.out.println("2. Lớp");
            System.out.println("3. Điểm trung bình");
            System.out.println("0. Hủy");

            int choice = nhapLuaChon();

            switch (choice) {
                case 1:
                    System.out.print("Nhập họ tên mới: ");
                    String hoTenMoi = scanner.nextLine();
                    sv.setHoTen(hoTenMoi);
                    System.out.println("✅ Cập nhật thành công!");
                    break;
                case 2:
                    System.out.print("Nhập lớp mới: ");
                    String lopMoi = scanner.nextLine();
                    sv.setLop(lopMoi);
                    System.out.println("✅ Cập nhật thành công!");
                    break;
                case 3:
                    System.out.print("Nhập điểm trung bình mới (0-10): ");
                    double diemMoi = scanner.nextDouble();
                    scanner.nextLine();

                    if (!SinhVien.kiemTraDiem(diemMoi)) {
                        System.out.println("❌ Điểm phải từ 0 đến 10!");
                        return;
                    }

                    sv.setDiemTB(diemMoi);
                    System.out.println("✅ Cập nhật thành công!");
                    break;
                case 0:
                    System.out.println("Đã hủy!");
                    break;
                default:
                    System.out.println("❌ Lựa chọn không hợp lệ!");
            }
        } catch (Exception e) {
            scanner.nextLine();
            System.out.println("❌ Lỗi nhập dữ liệu!");
        }
    }

    // ===== 4. XÓA SINH VIÊN =====
    private static void xoaSinhVien() {
        try {
            System.out.println("\n--- XÓA SINH VIÊN ---");

            System.out.print("Nhập mã sinh viên cần xóa: ");
            String maSV = scanner.nextLine();

            // Tìm và xóa
            for (int i = 0; i < danhSach.size(); i++) {
                if (danhSach.get(i).getMaSV().equals(maSV)) {
                    System.out.println("Bạn có chắc chắn muốn xóa? (y/n): ");
                    String confirm = scanner.nextLine();

                    if (confirm.equalsIgnoreCase("y")) {
                        danhSach.remove(i);
                        System.out.println("✅ Xóa thành công!");
                    } else {
                        System.out.println("Đã hủy xóa!");
                    }
                    return;
                }
            }

            System.out.println("❌ Không tìm thấy sinh viên!");
        } catch (Exception e) {
            System.out.println("❌ Lỗi xóa dữ liệu!");
        }
    }

    // ===== 5. TÌM KIẾM SINH VIÊN =====
    private static void timKiemSinhVien() {
        try {
            System.out.println("\n--- TÌM KIẾM SINH VIÊN ---");
            System.out.println("1. Tìm theo mã");
            System.out.println("2. Tìm theo họ tên");
            System.out.print("Chọn cách tìm kiếm: ");

            int choice = scanner.nextInt();
            scanner.nextLine();

            if (choice == 1) {
                timTheoMa();
            } else if (choice == 2) {
                timTheoHoTen();
            } else {
                System.out.println("❌ Lựa chọn không hợp lệ!");
            }
        } catch (Exception e) {
            scanner.nextLine();
            System.out.println("❌ Lỗi tìm kiếm!");
        }
    }

    private static void timTheoMa() {
        System.out.print("Nhập mã sinh viên: ");
        String maSV = scanner.nextLine();

        SinhVien sv = timSinhVienTheoMa(maSV);
        if (sv != null) {
            System.out.println("\n✅ Tìm thấy:");
            System.out.println(sv);
        } else {
            System.out.println("❌ Không tìm thấy sinh viên!");
        }
    }

    private static void timTheoHoTen() {
        System.out.print("Nhập họ tên: ");
        String hoTen = scanner.nextLine();

        List<SinhVien> ketQua = new ArrayList<>();
        for (SinhVien sv : danhSach) {
            if (sv.getHoTen().toLowerCase().contains(hoTen.toLowerCase())) {
                ketQua.add(sv);
            }
        }

        if (ketQua.isEmpty()) {
            System.out.println("❌ Không tìm thấy sinh viên!");
        } else {
            System.out.println("\n✅ Tìm thấy " + ketQua.size() + " kết quả:");
            System.out.println("-".repeat(70));
            for (SinhVien sv : ketQua) {
                System.out.println(sv);
            }
        }
    }

    // Hàm hỗ trợ tìm sinh viên theo mã
    private static SinhVien timSinhVienTheoMa(String maSV) {
        for (SinhVien sv : danhSach) {
            if (sv.getMaSV().equals(maSV)) {
                return sv;
            }
        }
        return null;
    }

    // ===== 6. SẮP XẾP THEO ĐIỂM =====
    private static void sapXepTheoDiem() {
        if (danhSach.isEmpty()) {
            System.out.println("❌ Danh sách trống!");
            return;
        }

        // Bubble sort - Sắp xếp giảm dần
        for (int i = 0; i < danhSach.size() - 1; i++) {
            for (int j = 0; j < danhSach.size() - i - 1; j++) {
                if (danhSach.get(j).getDiemTB() < danhSach.get(j + 1).getDiemTB()) {
                    // Hoán đổi
                    SinhVien temp = danhSach.get(j);
                    danhSach.set(j, danhSach.get(j + 1));
                    danhSach.set(j + 1, temp);
                }
            }
        }

        System.out.println("✅ Sắp xếp thành công! (Giảm dần theo điểm)");
        hienThiDanhSach();
    }

    // ===== 7. THỐNG KÊ SỐ LƯỢNG =====
    private static void thongKesoLuong() {
        if (danhSach.isEmpty()) {
            System.out.println("❌ Danh sách trống!");
            return;
        }

        System.out.println("\n" + "=".repeat(50));
        System.out.println("              THỐNG KÊ SỐ LƯỢNG");
        System.out.println("=".repeat(50));

        int tong = danhSach.size();

        // Thống kê theo lớp
        java.util.Map<String, Integer> soLuongTheoLop = new java.util.HashMap<>();
        for (SinhVien sv : danhSach) {
            String lop = sv.getLop();
            soLuongTheoLop.put(lop, soLuongTheoLop.getOrDefault(lop, 0) + 1);
        }

        System.out.println("Tổng số sinh viên: " + tong);
        System.out.println("\nThống kê theo lớp:");
        for (String lop : soLuongTheoLop.keySet()) {
            System.out.println("  " + lop + ": " + soLuongTheoLop.get(lop) + " sinh viên");
        }

        // Thống kê số sinh viên đạt
        int soSinhVienDat = 0;
        int soSinhVienKhongDat = 0;

        for (SinhVien sv : danhSach) {
            if (sv.getDiemTB() >= 5) {
                soSinhVienDat++;
            } else {
                soSinhVienKhongDat++;
            }
        }

        System.out.println("\nThống kê điểm:");
        System.out.println("  Sinh viên đạt (≥ 5): " + soSinhVienDat);
        System.out.println("  Sinh viên không đạt (< 5): " + soSinhVienKhongDat);

        System.out.println("=".repeat(50));
    }

    // ===== 8. THỐNG KÊ ĐIỂM TRUNG BÌNH CỦA LỚP =====
    private static void thongKeDiemTrungBinh() {
        if (danhSach.isEmpty()) {
            System.out.println("❌ Danh sách trống!");
            return;
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.println("        THỐNG KÊ ĐIỂM TRUNG BÌNH CỦA LỚP");
        System.out.println("=".repeat(60));

        // Nhóm sinh viên theo lớp
        java.util.Map<String, List<SinhVien>> svTheoLop = new java.util.HashMap<>();
        for (SinhVien sv : danhSach) {
            String lop = sv.getLop();
            svTheoLop.putIfAbsent(lop, new ArrayList<>());
            svTheoLop.get(lop).add(sv);
        }

        // Tính điểm TB cho mỗi lớp
        for (String lop : svTheoLop.keySet()) {
            List<SinhVien> svCuaLop = svTheoLop.get(lop);
            double tongDiem = 0;

            for (SinhVien sv : svCuaLop) {
                tongDiem += sv.getDiemTB();
            }

            double diemTBCuaLop = tongDiem / svCuaLop.size();

            System.out.printf("Lớp %-10s: Điểm TB = %.2f (Tổng %d sinh viên)%n",
                    lop, diemTBCuaLop, svCuaLop.size());
        }

        // Tính điểm TB chung của toàn trường
        double tongDiemToanTruong = 0;
        for (SinhVien sv : danhSach) {
            tongDiemToanTruong += sv.getDiemTB();
        }

        double diemTBToanTruong = tongDiemToanTruong / danhSach.size();
        System.out.println("-".repeat(60));
        System.out.printf("Điểm TB chung của toàn trường: %.2f%n", diemTBToanTruong);

        System.out.println("=".repeat(60));
    }

    // ===== 9. LÀM MỚI DỮ LIỆU =====
    private static void lamMoiDuLieu() {
        System.out.println("\n⚠️  Bạn có chắc chắn muốn xóa toàn bộ dữ liệu? (y/n): ");
        String confirm = scanner.nextLine();

        if (confirm.equalsIgnoreCase("y")) {
            danhSach.clear();
            System.out.println("✅ Đã xóa toàn bộ dữ liệu!");
        } else {
            System.out.println("Đã hủy!");
        }
    }

    // ===== THÊM DỮ LIỆU MẪU =====
    private static void themDuLieuMau() {
        danhSach.add(new SinhVien("SV001", "Nguyễn Văn A", "CNTT01", 8.5));
        danhSach.add(new SinhVien("SV002", "Trần Thị B", "CNTT01", 7.8));
        danhSach.add(new SinhVien("SV003", "Lê Minh C", "CNTT02", 9.0));
        danhSach.add(new SinhVien("SV004", "Phạm Thanh D", "CNTT02", 6.5));
        danhSach.add(new SinhVien("SV005", "Hoàng Hữu E", "CNTT03", 7.2));
        danhSach.add(new SinhVien("SV006", "Võ Anh F", "CNTT03", 8.9));
        danhSach.add(new SinhVien("SV007", "Đỗ Khánh G", "CNTT01", 7.5));
        danhSach.add(new SinhVien("SV008", "Bùi Văn H", "CNTT02", 6.8));
        danhSach.add(new SinhVien("SV009", "Dương Tú I", "CNTT03", 8.3));
        danhSach.add(new SinhVien("SV010", "Cao Thúy J", "CNTT01", 9.2));

        System.out.println("✅ Đã tải 10 sinh viên mẫu!");
    }
}