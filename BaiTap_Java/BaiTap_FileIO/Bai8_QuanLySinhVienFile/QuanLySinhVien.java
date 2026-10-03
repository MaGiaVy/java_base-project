import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class QuanLySinhVien {
    private static final String FILE_NAME = "SinhVien_Data.txt";
    private static Scanner scanner = new Scanner(System.in);
    private static List<SinhVien> danhSach = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("========== BÀI 8: CHƯƠNG TRÌNH QUẢN LÝ SINH VIÊN (FILE I/O) ==========");
        // 7. Đọc dữ liệu từ Text File khi khởi động chương trình
        docDuLieuTuFile();

        int luaChon = -1;
        do {
            System.out.println("\n----------------- MENU CHỨC NĂNG -----------------");
            System.out.println("1. Thêm sinh viên mới");
            System.out.println("2. Hiển thị danh sách sinh viên");
            System.out.println("3. Tìm kiếm theo mã hoặc họ tên");
            System.out.println("4. Cập nhật thông tin sinh viên");
            System.out.println("5. Xóa sinh viên");
            System.out.println("6. Lưu dữ liệu vào Text File (" + FILE_NAME + ")");
            System.out.println("7. Tải lại dữ liệu từ Text File");
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
                    themSinhVien();
                    break;
                case 2:
                    hienThiDanhSach(danhSach);
                    break;
                case 3:
                    timKiemSinhVien();
                    break;
                case 4:
                    capNhatSinhVien();
                    break;
                case 5:
                    xoaSinhVien();
                    break;
                case 6:
                    luuDuLieuVaoFile();
                    break;
                case 7:
                    docDuLieuTuFile();
                    break;
                case 0:
                    System.out.println(">> Tự động lưu dữ liệu trước khi thoát...");
                    luuDuLieuVaoFile();
                    System.out.println("Tạm biệt!");
                    break;
                default:
                    System.out.println("Lựa chọn không hợp lệ!");
            }
        } while (luaChon != 0);
    }

    // 7. Đọc file khi khởi động
    private static void docDuLieuTuFile() {
        File file = new File(FILE_NAME);
        if (!file.exists()) {
            System.out.println(">> Chưa tìm thấy file '" + FILE_NAME + "'. Khởi tạo dữ liệu mẫu ban đầu...");
            danhSach.clear();
            danhSach.add(new SinhVien("SV001", "Nguyễn Văn An", "CNTT01", 8.5));
            danhSach.add(new SinhVien("SV002", "Trần Thị Bình", "CNTT02", 7.8));
            danhSach.add(new SinhVien("SV003", "Lê Văn Cường", "CNTT01", 9.2));
            luuDuLieuVaoFile();
            return;
        }

        danhSach.clear();
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;
                String[] parts = line.split(";");
                if (parts.length >= 4) {
                    try {
                        String ma = parts[0].trim();
                        String ten = parts[1].trim();
                        String lop = parts[2].trim();
                        double diem = Double.parseDouble(parts[3].trim().replace(',', '.'));
                        danhSach.add(new SinhVien(ma, ten, lop, diem));
                        count++;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            System.out.printf(">> Đã đọc thành công %d sinh viên từ file '%s' khi khởi động!\n", count, FILE_NAME);
        } catch (IOException e) {
            System.err.println("Lỗi khi đọc file: " + e.getMessage());
        }
    }

    // 6. Lưu file
    private static void luuDuLieuVaoFile() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME, false))) {
            for (SinhVien sv : danhSach) {
                writer.write(sv.toFileLine());
                writer.newLine();
            }
            System.out.printf(">> Đã lưu %d sinh viên vào tập tin '%s' thành công!\n", danhSach.size(), FILE_NAME);
        } catch (IOException e) {
            System.err.println("Lỗi khi lưu dữ liệu vào file: " + e.getMessage());
        }
    }

    // 1. Thêm sinh viên
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

        System.out.print("Nhập lớp: ");
        String lop = scanner.nextLine().trim();

        double diem;
        while (true) {
            try {
                System.out.print("Nhập điểm trung bình (0.0 - 10.0): ");
                diem = Double.parseDouble(scanner.nextLine().trim());
                if (diem >= 0 && diem <= 10)
                    break;
                System.out.println("Điểm phải từ 0 đến 10!");
            } catch (NumberFormatException e) {
                System.out.println("Vui lòng nhập số thực!");
            }
        }

        danhSach.add(new SinhVien(ma, ten, lop, diem));
        System.out.println(">> Thêm sinh viên thành công!");
    }

    // 2. Hiển thị danh sách
    private static void hienThiDanhSach(List<SinhVien> list) {
        if (list.isEmpty()) {
            System.out.println(">> Danh sách sinh viên đang trống!");
            return;
        }
        System.out.println("\n----------------- DANH SÁCH SINH VIÊN HIỆN TẠI -----------------");
        for (int i = 0; i < list.size(); i++) {
            System.out.printf("%2d. %s\n", i + 1, list.get(i));
        }
        System.out.println("----------------------------------------------------------------");
        System.out.println(">> Tổng số sinh viên: " + list.size());
    }

    // 3. Tìm kiếm theo mã hoặc họ tên
    private static void timKiemSinhVien() {
        if (danhSach.isEmpty()) {
            System.out.println(">> Danh sách trống!");
            return;
        }
        System.out.print("Nhập mã sinh viên HOẶC họ tên cần tìm: ");
        String key = scanner.nextLine().trim().toLowerCase();

        List<SinhVien> ketQua = new ArrayList<>();
        for (SinhVien sv : danhSach) {
            if (sv.getMaSV().toLowerCase().contains(key) || sv.getHoTen().toLowerCase().contains(key)) {
                ketQua.add(sv);
            }
        }

        if (ketQua.isEmpty()) {
            System.out.println(">> Không tìm thấy sinh viên nào khớp với từ khóa: " + key);
        } else {
            System.out.println(">> Kết quả tìm kiếm:");
            hienThiDanhSach(ketQua);
        }
    }

    // 4. Cập nhật thông tin sinh viên
    private static void capNhatSinhVien() {
        if (danhSach.isEmpty()) {
            System.out.println(">> Danh sách trống!");
            return;
        }
        System.out.print("Nhập mã sinh viên cần cập nhật: ");
        String ma = scanner.nextLine().trim();

        SinhVien target = null;
        for (SinhVien sv : danhSach) {
            if (sv.getMaSV().equalsIgnoreCase(ma)) {
                target = sv;
                break;
            }
        }

        if (target == null) {
            System.out.println(">> Không tìm thấy sinh viên có mã: " + ma);
            return;
        }

        System.out.println("Thông tin hiện tại: " + target);
        System.out.print("Nhập họ tên mới (bỏ trống nếu giữ nguyên): ");
        String tenMoi = scanner.nextLine().trim();
        if (!tenMoi.isEmpty())
            target.setHoTen(tenMoi);

        System.out.print("Nhập lớp mới (bỏ trống nếu giữ nguyên): ");
        String lopMoi = scanner.nextLine().trim();
        if (!lopMoi.isEmpty())
            target.setLop(lopMoi);

        System.out.print("Nhập điểm TB mới (bỏ trống nếu giữ nguyên): ");
        String diemStr = scanner.nextLine().trim();
        if (!diemStr.isEmpty()) {
            try {
                double diemMoi = Double.parseDouble(diemStr);
                if (diemMoi >= 0 && diemMoi <= 10) {
                    target.setDiemTB(diemMoi);
                } else {
                    System.out.println("Điểm không hợp lệ, giữ nguyên điểm cũ!");
                }
            } catch (NumberFormatException e) {
                System.out.println("Điểm không hợp lệ, giữ nguyên điểm cũ!");
            }
        }

        System.out.println(">> Cập nhật thông tin thành công: " + target);
    }

    // 5. Xóa sinh viên
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
            System.out.println(">> Không tìm thấy sinh viên có mã: " + ma);
        }
    }
}
