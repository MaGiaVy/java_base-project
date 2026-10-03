import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

public class QuanLyDanhBa {
    private static Scanner scanner = new Scanner(System.in);
    // Sử dụng LinkedHashMap tương đương Dictionary<string, string> trong C#, duy
    // trì thứ tự chèn
    private static Map<String, String> danhBa = new LinkedHashMap<>();

    public static void main(String[] args) {
        // Dữ liệu mẫu
        danhBa.put("0901234567", "Nguyễn Văn An");
        danhBa.put("0912345678", "Trần Thị Bình");
        danhBa.put("0987654321", "Lê Hoàng Cường");

        int luaChon = -1;
        do {
            System.out.println("\n========== BÀI 5: QUẢN LÝ DANH BẠ (Dictionary / Map) ==========");
            System.out.println("1. Thêm liên hệ mới");
            System.out.println("2. Tìm liên hệ theo số điện thoại (Khóa)");
            System.out.println("3. Cập nhật tên theo số điện thoại");
            System.out.println("4. Xóa liên hệ");
            System.out.println("5. Hiển thị toàn bộ danh bạ");
            System.out.println("0. Thoát");
            System.out.print("Chọn chức năng (0-5): ");

            try {
                luaChon = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Vui lòng nhập số!");
                continue;
            }

            switch (luaChon) {
                case 1:
                    themLienHe();
                    break;
                case 2:
                    timTheoSDT();
                    break;
                case 3:
                    capNhatTen();
                    break;
                case 4:
                    xoaLienHe();
                    break;
                case 5:
                    hienThiDanhBa();
                    break;
                case 0:
                    System.out.println("Tạm biệt!");
                    break;
                default:
                    System.out.println("Lựa chọn không hợp lệ!");
            }
        } while (luaChon != 0);
    }

    private static void themLienHe() {
        System.out.print("Nhập số điện thoại (Khóa): ");
        String sdt = scanner.nextLine().trim();
        if (danhBa.containsKey(sdt)) {
            System.out.println(">> Số điện thoại này đã tồn tại trong danh bạ (Chủ sở hữu: " + danhBa.get(sdt) + ")!");
            return;
        }

        System.out.print("Nhập họ tên: ");
        String hoTen = scanner.nextLine().trim();
        danhBa.put(sdt, hoTen);
        System.out.println(">> Thêm liên hệ thành công!");
    }

    private static void timTheoSDT() {
        System.out.print("Nhập số điện thoại cần tìm: ");
        String sdt = scanner.nextLine().trim();
        if (danhBa.containsKey(sdt)) {
            System.out.println(">> Tìm thấy liên hệ:");
            System.out.printf("   Số điện thoại: %s  --->  Họ tên: %s\n", sdt, danhBa.get(sdt));
        } else {
            System.out.println(">> Không tìm thấy số điện thoại: " + sdt);
        }
    }

    private static void capNhatTen() {
        System.out.print("Nhập số điện thoại cần cập nhật họ tên: ");
        String sdt = scanner.nextLine().trim();
        if (!danhBa.containsKey(sdt)) {
            System.out.println(">> Số điện thoại không tồn tại trong danh bạ!");
            return;
        }

        System.out.println("Tên hiện tại: " + danhBa.get(sdt));
        System.out.print("Nhập họ tên mới: ");
        String tenMoi = scanner.nextLine().trim();
        danhBa.put(sdt, tenMoi);
        System.out.println(">> Cập nhật tên thành công!");
    }

    private static void xoaLienHe() {
        System.out.print("Nhập số điện thoại cần xóa: ");
        String sdt = scanner.nextLine().trim();
        if (danhBa.containsKey(sdt)) {
            String ten = danhBa.remove(sdt);
            System.out.printf(">> Đã xóa liên hệ '%s' (%s) khỏi danh bạ!\n", ten, sdt);
        } else {
            System.out.println(">> Không tìm thấy số điện thoại: " + sdt);
        }
    }

    private static void hienThiDanhBa() {
        if (danhBa.isEmpty()) {
            System.out.println(">> Danh bạ đang trống!");
            return;
        }
        System.out.println("\n----------------- TOÀN BỘ DANH BẠ ĐIỆN THOẠI -----------------");
        System.out.printf("%-5s | %-16s | %-25s\n", "STT", "Số điện thoại (Key)", "Họ tên (Value)");
        System.out.println("---------------------------------------------------------------");
        int stt = 1;
        for (Map.Entry<String, String> entry : danhBa.entrySet()) {
            System.out.printf("%-5d | %-16s | %-25s\n", stt++, entry.getKey(), entry.getValue());
        }
        System.out.println("---------------------------------------------------------------");
        System.out.println(">> Tổng số liên hệ: " + danhBa.size());
    }
}
