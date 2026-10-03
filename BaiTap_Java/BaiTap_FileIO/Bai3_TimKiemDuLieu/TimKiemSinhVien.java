import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class TimKiemSinhVien {
    private static final String FILE_NAME = "SinhVien.txt";
    private static Scanner scanner = new Scanner(System.in);

    // Class đại diện bản ghi đọc từ file
    static class Record {
        String maSV;
        String hoTen;
        String lop;
        String diemTB;

        public Record(String maSV, String hoTen, String lop, String diemTB) {
            this.maSV = maSV;
            this.hoTen = hoTen;
            this.lop = lop;
            this.diemTB = diemTB;
        }

        public void inThongTin() {
            System.out.printf("Mã SV: %-10s | Họ tên: %-22s | Lớp: %-10s | ĐTB: %s\n", maSV, hoTen, lop, diemTB);
        }
    }

    public static void main(String[] args) {
        int luaChon = -1;
        do {
            System.out.println("\n========== BÀI 3: TÌM KIẾM DỮ LIỆU TỪ TẬP TIN SinhVien.txt ==========");
            System.out.println("1. Tìm sinh viên theo mã sinh viên");
            System.out.println("2. Tìm sinh viên theo họ tên");
            System.out.println("3. Hiển thị toàn bộ tập tin");
            System.out.println("0. Thoát");
            System.out.print("Chọn chức năng (0-3): ");

            try {
                luaChon = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Vui lòng nhập số!");
                continue;
            }

            switch (luaChon) {
                case 1:
                    timTheoMa();
                    break;
                case 2:
                    timTheoHoTen();
                    break;
                case 3:
                    hienThiTatCa();
                    break;
                case 0:
                    System.out.println("Tạm biệt!");
                    break;
                default:
                    System.out.println("Lựa chọn không hợp lệ!");
            }
        } while (luaChon != 0);
    }

    private static List<Record> docTatCaRecords() {
        List<Record> list = new ArrayList<>();
        File file = new File(FILE_NAME);
        if (!file.exists()) {
            System.out.println(">> Tập tin '" + FILE_NAME + "' chưa tồn tại!");
            return list;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;
                String[] parts = line.split(";");
                if (parts.length >= 4) {
                    list.add(new Record(parts[0].trim(), parts[1].trim(), parts[2].trim(), parts[3].trim()));
                }
            }
        } catch (IOException e) {
            System.err.println("Lỗi đọc file: " + e.getMessage());
        }
        return list;
    }

    private static void timTheoMa() {
        List<Record> list = docTatCaRecords();
        if (list.isEmpty())
            return;

        System.out.print("Nhập mã sinh viên cần tìm: ");
        String ma = scanner.nextLine().trim();
        boolean timThay = false;

        System.out.println("\n>> KẾT QUẢ TÌM KIẾM THEO MÃ '" + ma + "':");
        for (Record r : list) {
            if (r.maSV.equalsIgnoreCase(ma)) {
                r.inThongTin();
                timThay = true;
            }
        }
        if (!timThay) {
            System.out.println(">> Không tìm thấy sinh viên nào với mã: " + ma);
        }
    }

    private static void timTheoHoTen() {
        List<Record> list = docTatCaRecords();
        if (list.isEmpty())
            return;

        System.out.print("Nhập họ tên (hoặc từ khóa tên) cần tìm: ");
        String key = scanner.nextLine().trim().toLowerCase();
        boolean timThay = false;

        System.out.println("\n>> KẾT QUẢ TÌM KIẾM THEO TÊN '" + key + "':");
        for (Record r : list) {
            if (r.hoTen.toLowerCase().contains(key)) {
                r.inThongTin();
                timThay = true;
            }
        }
        if (!timThay) {
            System.out.println(">> Không tìm thấy sinh viên nào có tên chứa: " + key);
        }
    }

    private static void hienThiTatCa() {
        List<Record> list = docTatCaRecords();
        if (list.isEmpty())
            return;

        System.out.println("\n----------------- TOÀN BỘ DANH SÁCH TỪ FILE -----------------");
        for (int i = 0; i < list.size(); i++) {
            System.out.printf("%2d. ", i + 1);
            list.get(i).inThongTin();
        }
        System.out.println("-------------------------------------------------------------");
    }
}
