import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

public class QuanLyDiem {
    private static Scanner scanner = new Scanner(System.in);
    private static List<SinhVien> danhSach = new ArrayList<>();

    public static void main(String[] args) {
        int luaChon = -1;
        do {
            System.out.println("\n========== BÀI 10: QUẢN LÝ ĐIỂM SINH VIÊN ==========");
            System.out.println("1. Nhập danh sách sinh viên");
            System.out.println("2. Hiển thị danh sách");
            System.out.println("3. Tính và xem điểm trung bình của từng sinh viên");
            System.out.println("4. Tìm sinh viên có điểm trung bình cao nhất");
            System.out.println("5. Sắp xếp danh sách theo điểm trung bình giảm dần");
            System.out.println("6. Tìm sinh viên theo tên");
            System.out.println("7. Thống kê số sinh viên đạt (ĐTB >= 5)");
            System.out.println("0. Thoát");
            System.out.print("Chọn chức năng (0-7): ");

            try {
                luaChon = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Vui lòng nhập số!");
                continue;
            }

            switch (luaChon) {
                case 1:
                    nhapDanhSach();
                    break;
                case 2:
                    hienThiDanhSach(danhSach);
                    break;
                case 3:
                    hienThiDiemTB();
                    break;
                case 4:
                    timDiemCaoNhat();
                    break;
                case 5:
                    sapXepGiamDan();
                    break;
                case 6:
                    timTheoTen();
                    break;
                case 7:
                    thongKeDat();
                    break;
                case 0:
                    System.out.println("Tạm biệt!");
                    break;
                default:
                    System.out.println("Lựa chọn không hợp lệ!");
            }
        } while (luaChon != 0);
    }

    private static double nhapDiem(String mon) {
        while (true) {
            try {
                System.out.printf("  Nhập điểm %s (0 - 10): ", mon);
                double d = Double.parseDouble(scanner.nextLine().trim());
                if (d >= 0 && d <= 10)
                    return d;
                System.out.println("  Điểm phải từ 0 đến 10!");
            } catch (NumberFormatException e) {
                System.out.println("  Giá trị không hợp lệ! Vui lòng nhập số thực.");
            }
        }
    }

    private static void nhapDanhSach() {
        System.out.print("Nhập số lượng sinh viên n: ");
        int n = 0;
        try {
            n = Integer.parseInt(scanner.nextLine().trim());
            if (n <= 0) {
                System.out.println("Số lượng phải lớn hơn 0!");
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("Vui lòng nhập số nguyên!");
            return;
        }

        for (int i = 0; i < n; i++) {
            System.out.printf("\n--- Nhập sinh viên thứ %d ---\n", i + 1);
            System.out.print("  Họ tên: ");
            String hoTen = scanner.nextLine().trim();
            double diemToan = nhapDiem("Toán");
            double diemLapTrinh = nhapDiem("Lập trình");
            danhSach.add(new SinhVien(hoTen, diemToan, diemLapTrinh));
        }
        System.out.println(">> Đã thêm " + n + " sinh viên vào danh sách!");
    }

    private static void hienThiDanhSach(List<SinhVien> list) {
        if (list.isEmpty()) {
            System.out.println(">> Danh sách đang trống!");
            return;
        }
        System.out.println("\n----------------- DANH SÁCH SINH VIÊN -----------------");
        for (int i = 0; i < list.size(); i++) {
            System.out.printf("%2d. %s\n", i + 1, list.get(i));
        }
        System.out.println("-------------------------------------------------------");
    }

    private static void hienThiDiemTB() {
        if (danhSach.isEmpty()) {
            System.out.println(">> Danh sách đang trống!");
            return;
        }
        System.out.println("\n--- ĐIỂM TRUNG BÌNH TỪNG SINH VIÊN ---");
        for (SinhVien sv : danhSach) {
            System.out.printf("Họ tên: %-20s | Điểm TB: %.2f\n", sv.getHoTen(), sv.tinhDiemTB());
        }
    }

    private static void timDiemCaoNhat() {
        if (danhSach.isEmpty()) {
            System.out.println(">> Danh sách đang trống!");
            return;
        }
        double maxDiem = danhSach.stream().mapToDouble(SinhVien::tinhDiemTB).max().orElse(0.0);
        System.out.printf("\n>> Điểm trung bình cao nhất: %.2f\n", maxDiem);
        System.out.println("Các sinh viên đạt điểm cao nhất:");
        for (SinhVien sv : danhSach) {
            if (Math.abs(sv.tinhDiemTB() - maxDiem) < 0.0001) {
                System.out.println(" - " + sv);
            }
        }
    }

    private static void sapXepGiamDan() {
        if (danhSach.isEmpty()) {
            System.out.println(">> Danh sách đang trống!");
            return;
        }
        danhSach.sort(Comparator.comparingDouble(SinhVien::tinhDiemTB).reversed());
        System.out.println(">> Đã sắp xếp theo điểm trung bình giảm dần!");
        hienThiDanhSach(danhSach);
    }

    private static void timTheoTen() {
        if (danhSach.isEmpty()) {
            System.out.println(">> Danh sách đang trống!");
            return;
        }
        System.out.print("Nhập tên sinh viên cần tìm: ");
        String key = scanner.nextLine().trim().toLowerCase();
        List<SinhVien> ketQua = new ArrayList<>();
        for (SinhVien sv : danhSach) {
            if (sv.getHoTen().toLowerCase().contains(key)) {
                ketQua.add(sv);
            }
        }
        if (ketQua.isEmpty()) {
            System.out.println(">> Không tìm thấy sinh viên nào phù hợp!");
        } else {
            System.out.println(">> Kết quả tìm kiếm:");
            hienThiDanhSach(ketQua);
        }
    }

    private static void thongKeDat() {
        if (danhSach.isEmpty()) {
            System.out.println(">> Danh sách đang trống!");
            return;
        }
        long count = danhSach.stream().filter(sv -> sv.tinhDiemTB() >= 5.0).count();
        System.out.printf(">> Số lượng sinh viên ĐẠT (ĐTB >= 5.0): %d / %d sinh viên (%.1f%%)\n",
                count, danhSach.size(), (count * 100.0 / danhSach.size()));
    }
}
