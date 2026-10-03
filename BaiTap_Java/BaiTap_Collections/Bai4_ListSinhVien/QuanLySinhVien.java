import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

public class QuanLySinhVien {
    private static Scanner scanner = new Scanner(System.in);
    private static List<SinhVien> danhSach = new ArrayList<>();

    public static void main(String[] args) {
        // Dữ liệu mẫu
        danhSach.add(new SinhVien("SV01", "Nguyễn Văn An", 8.5));
        danhSach.add(new SinhVien("SV02", "Trần Thị Bình", 7.2));
        danhSach.add(new SinhVien("SV03", "Lê Hoàng Cường", 9.0));
        danhSach.add(new SinhVien("SV04", "Phạm Ngọc Dung", 6.8));

        int luaChon = -1;
        do {
            System.out.println("\n========== BÀI 4: QUẢN LÝ DANH SÁCH SINH VIÊN (List<T>) ==========");
            System.out.println("1. Thêm sinh viên");
            System.out.println("2. Hiển thị danh sách");
            System.out.println("3. Tìm sinh viên theo mã");
            System.out.println("4. Cập nhật điểm trung bình");
            System.out.println("5. Xóa sinh viên theo mã");
            System.out.println("6. Sắp xếp theo điểm trung bình giảm dần");
            System.out.println("0. Thoát");
            System.out.print("Chọn chức năng (0-6): ");

            try {
                luaChon = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Vui lòng nhập số!");
                continue;
            }

            switch (luaChon) {
                case 1:
                    themSinhVien();
                    break;
                case 2:
                    hienThiDanhSach(danhSach);
                    break;
                case 3:
                    timTheoMa();
                    break;
                case 4:
                    capNhatDiem();
                    break;
                case 5:
                    xoaSinhVien();
                    break;
                case 6:
                    sapXepGiamDan();
                    break;
                case 0:
                    System.out.println("Tạm biệt!");
                    break;
                default:
                    System.out.println("Lựa chọn không hợp lệ!");
            }
        } while (luaChon != 0);
    }

    private static void themSinhVien() {
        System.out.print("Nhập mã sinh viên: ");
        String ma = scanner.nextLine().trim();
        for (SinhVien sv : danhSach) {
            if (sv.getMaSV().equalsIgnoreCase(ma)) {
                System.out.println(">> Lỗi: Mã sinh viên đã tồn tại!");
                return;
            }
        }

        System.out.print("Nhập họ tên: ");
        String ten = scanner.nextLine().trim();

        double diem;
        while (true) {
            try {
                System.out.print("Nhập điểm trung bình (0.0 - 10.0): ");
                diem = Double.parseDouble(scanner.nextLine().trim());
                if (diem >= 0 && diem <= 10)
                    break;
                System.out.println("Điểm phải từ 0 đến 10!");
            } catch (NumberFormatException e) {
                System.out.println("Vui lòng nhập số thực hợp lệ!");
            }
        }

        danhSach.add(new SinhVien(ma, ten, diem));
        System.out.println(">> Thêm sinh viên thành công!");
    }

    private static void hienThiDanhSach(List<SinhVien> list) {
        if (list.isEmpty()) {
            System.out.println(">> Danh sách sinh viên đang trống!");
            return;
        }
        System.out.println("\n--------------------- DANH SÁCH SINH VIÊN ---------------------");
        for (int i = 0; i < list.size(); i++) {
            System.out.printf("%2d. %s\n", i + 1, list.get(i));
        }
        System.out.println("----------------------------------------------------------------");
    }

    private static void timTheoMa() {
        if (danhSach.isEmpty()) {
            System.out.println(">> Danh sách trống!");
            return;
        }
        System.out.print("Nhập mã sinh viên cần tìm: ");
        String ma = scanner.nextLine().trim();
        for (SinhVien sv : danhSach) {
            if (sv.getMaSV().equalsIgnoreCase(ma)) {
                System.out.println(">> Tìm thấy sinh viên:");
                System.out.println("   " + sv);
                return;
            }
        }
        System.out.println(">> Không tìm thấy sinh viên với mã: " + ma);
    }

    private static void capNhatDiem() {
        if (danhSach.isEmpty()) {
            System.out.println(">> Danh sách trống!");
            return;
        }
        System.out.print("Nhập mã sinh viên cần cập nhật điểm: ");
        String ma = scanner.nextLine().trim();
        SinhVien target = null;
        for (SinhVien sv : danhSach) {
            if (sv.getMaSV().equalsIgnoreCase(ma)) {
                target = sv;
                break;
            }
        }

        if (target == null) {
            System.out.println(">> Không tìm thấy sinh viên với mã: " + ma);
            return;
        }

        System.out.println("Sinh viên hiện tại: " + target);
        double diemMoi;
        while (true) {
            try {
                System.out.print("Nhập điểm trung bình mới (0.0 - 10.0): ");
                diemMoi = Double.parseDouble(scanner.nextLine().trim());
                if (diemMoi >= 0 && diemMoi <= 10)
                    break;
                System.out.println("Điểm phải từ 0 đến 10!");
            } catch (NumberFormatException e) {
                System.out.println("Vui lòng nhập số thực hợp lệ!");
            }
        }
        target.setDiemTB(diemMoi);
        System.out.println(">> Cập nhật điểm thành công: " + target);
    }

    private static void xoaSinhVien() {
        if (danhSach.isEmpty()) {
            System.out.println(">> Danh sách trống!");
            return;
        }
        System.out.print("Nhập mã sinh viên cần xóa: ");
        String ma = scanner.nextLine().trim();
        boolean removed = danhSach.removeIf(sv -> sv.getMaSV().equalsIgnoreCase(ma));
        if (removed) {
            System.out.println(">> Đã xóa sinh viên có mã: " + ma);
        } else {
            System.out.println(">> Không tìm thấy mã sinh viên để xóa!");
        }
    }

    private static void sapXepGiamDan() {
        if (danhSach.isEmpty()) {
            System.out.println(">> Danh sách trống!");
            return;
        }
        danhSach.sort(Comparator.comparingDouble(SinhVien::getDiemTB).reversed());
        System.out.println(">> Đã sắp xếp theo điểm trung bình giảm dần!");
        hienThiDanhSach(danhSach);
    }
}
