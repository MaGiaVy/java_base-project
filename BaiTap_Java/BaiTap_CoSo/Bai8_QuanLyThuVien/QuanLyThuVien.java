import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

public class QuanLyThuVien {
    private static Scanner scanner = new Scanner(System.in);
    private static List<TaiLieu> danhSach = new ArrayList<>();

    public static void main(String[] args) {
        // Dữ liệu mẫu ban đầu để tiện thử nghiệm
        danhSach.add(new Sach("TL01", "Lap Trinh Java Co Ban", 2022, "Nguyen Van An", 350));
        danhSach.add(new TapChi("TL02", "Tap Chi Khoa Hoc & Doi Song", 2023, 45, 10));
        danhSach.add(new Sach("TL03", "Cau Truc Du Lieu & Giai Thuat", 2020, "Tran Van Binh", 420));
        danhSach.add(new TapChi("TL04", "Tap Chi Cong Nghe Thong Tin", 2021, 12, 6));

        int luaChon = -1;
        do {
            System.out.println("\n========== BÀI 8: QUẢN LÝ THƯ VIỆN ==========");
            System.out.println("1. Thêm tài liệu (Sách / Tạp chí)");
            System.out.println("2. Hiển thị danh sách tài liệu");
            System.out.println("3. Tìm kiếm theo mã tài liệu");
            System.out.println("4. Xóa tài liệu theo mã");
            System.out.println("5. Sắp xếp tài liệu theo năm xuất bản");
            System.out.println("6. Thống kê số lượng từng loại tài liệu");
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
                    themTaiLieu();
                    break;
                case 2:
                    hienThiDanhSach(danhSach);
                    break;
                case 3:
                    timTheoMa();
                    break;
                case 4:
                    xoaTaiLieu();
                    break;
                case 5:
                    sapXepTheoNamXB();
                    break;
                case 6:
                    thongKeLoai();
                    break;
                case 0:
                    System.out.println("Tạm biệt!");
                    break;
                default:
                    System.out.println("Lựa chọn không hợp lệ!");
            }
        } while (luaChon != 0);
    }

    private static void themTaiLieu() {
        System.out.println("\n--- Chọn loại tài liệu cần thêm ---");
        System.out.println("1. Sách");
        System.out.println("2. Tạp chí");
        System.out.print("Chọn (1-2): ");
        int loai;
        try {
            loai = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Lựa chọn không hợp lệ!");
            return;
        }

        System.out.print("Nhập mã tài liệu: ");
        String ma = scanner.nextLine().trim();
        for (TaiLieu tl : danhSach) {
            if (tl.getMaTL().equalsIgnoreCase(ma)) {
                System.out.println(">> Mã tài liệu đã tồn tại trong thư viện!");
                return;
            }
        }

        System.out.print("Nhập tên tài liệu: ");
        String ten = scanner.nextLine().trim();
        int namXB;
        while (true) {
            try {
                System.out.print("Nhập năm xuất bản: ");
                namXB = Integer.parseInt(scanner.nextLine().trim());
                if (namXB > 0 && namXB <= 2026)
                    break;
                System.out.println("Năm xuất bản không hợp lệ!");
            } catch (NumberFormatException e) {
                System.out.println("Vui lòng nhập số nguyên!");
            }
        }

        if (loai == 1) {
            System.out.print("Nhập tên tác giả: ");
            String tacGia = scanner.nextLine().trim();
            int soTrang;
            while (true) {
                try {
                    System.out.print("Nhập số trang: ");
                    soTrang = Integer.parseInt(scanner.nextLine().trim());
                    if (soTrang > 0)
                        break;
                    System.out.println("Số trang phải > 0!");
                } catch (NumberFormatException e) {
                    System.out.println("Vui lòng nhập số nguyên!");
                }
            }
            danhSach.add(new Sach(ma, ten, namXB, tacGia, soTrang));
            System.out.println(">> Thêm Sách thành công!");
        } else if (loai == 2) {
            int soPH, thangPH;
            while (true) {
                try {
                    System.out.print("Nhập số phát hành: ");
                    soPH = Integer.parseInt(scanner.nextLine().trim());
                    break;
                } catch (NumberFormatException e) {
                    System.out.println("Vui lòng nhập số nguyên!");
                }
            }
            while (true) {
                try {
                    System.out.print("Nhập tháng phát hành (1-12): ");
                    thangPH = Integer.parseInt(scanner.nextLine().trim());
                    if (thangPH >= 1 && thangPH <= 12)
                        break;
                    System.out.println("Tháng phải từ 1 đến 12!");
                } catch (NumberFormatException e) {
                    System.out.println("Vui lòng nhập số nguyên!");
                }
            }
            danhSach.add(new TapChi(ma, ten, namXB, soPH, thangPH));
            System.out.println(">> Thêm Tạp chí thành công!");
        } else {
            System.out.println("Loại không hợp lệ!");
        }
    }

    private static void hienThiDanhSach(List<TaiLieu> list) {
        if (list.isEmpty()) {
            System.out.println(">> Thư viện hiện không có tài liệu nào!");
            return;
        }
        System.out.println("\n----------------- DANH SÁCH TÀI LIỆU TRONG THƯ VIỆN -----------------");
        for (int i = 0; i < list.size(); i++) {
            System.out.printf("%2d. ", i + 1);
            list.get(i).hienThiThongTin();
        }
        System.out.println("----------------------------------------------------------------------");
    }

    private static void timTheoMa() {
        if (danhSach.isEmpty()) {
            System.out.println(">> Thư viện trống!");
            return;
        }
        System.out.print("Nhập mã tài liệu cần tìm: ");
        String ma = scanner.nextLine().trim();
        for (TaiLieu tl : danhSach) {
            if (tl.getMaTL().equalsIgnoreCase(ma)) {
                System.out.println(">> Tìm thấy tài liệu:");
                tl.hienThiThongTin();
                return;
            }
        }
        System.out.println(">> Không tìm thấy tài liệu với mã: " + ma);
    }

    private static void xoaTaiLieu() {
        if (danhSach.isEmpty()) {
            System.out.println(">> Thư viện trống!");
            return;
        }
        System.out.print("Nhập mã tài liệu cần xóa: ");
        String ma = scanner.nextLine().trim();
        boolean daXoa = danhSach.removeIf(tl -> tl.getMaTL().equalsIgnoreCase(ma));
        if (daXoa) {
            System.out.println(">> Xóa tài liệu thành công!");
        } else {
            System.out.println(">> Không tìm thấy mã tài liệu để xóa!");
        }
    }

    private static void sapXepTheoNamXB() {
        if (danhSach.isEmpty()) {
            System.out.println(">> Thư viện trống!");
            return;
        }
        danhSach.sort(Comparator.comparingInt(TaiLieu::getNamXB));
        System.out.println(">> Đã sắp xếp tài liệu theo năm xuất bản tăng dần!");
        hienThiDanhSach(danhSach);
    }

    private static void thongKeLoai() {
        long demSach = danhSach.stream().filter(tl -> tl instanceof Sach).count();
        long demTapChi = danhSach.stream().filter(tl -> tl instanceof TapChi).count();
        System.out.println("\n=== THỐNG KÊ TÀI LIỆU ===");
        System.out.printf("- Số lượng Sách: %d cuốn\n", demSach);
        System.out.printf("- Số lượng Tạp chí: %d cuốn\n", demTapChi);
        System.out.printf("- Tổng cộng: %d tài liệu\n", danhSach.size());
    }
}
