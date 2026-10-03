import java.util.Scanner;
import java.util.Stack;

public class LichSuThaoTac {
    private static Scanner scanner = new Scanner(System.in);
    // Sử dụng Stack<String> để lưu lịch sử thao tác của người dùng (LIFO: Last In
    // First Out)
    private static Stack<String> nganXepThaoTac = new Stack<>();

    public static void main(String[] args) {
        // Khởi tạo một số thao tác mẫu
        nganXepThaoTac.push("Mở ứng dụng");
        nganXepThaoTac.push("Nhập văn bản: 'Xin chào Java'");
        nganXepThaoTac.push("Đổi phông chữ thành Arial");
        nganXepThaoTac.push("Chèn hình ảnh logo.png");

        int luaChon = -1;
        do {
            System.out.println("\n========== BÀI 7: QUẢN LÝ LỊCH SỬ THAO TÁC (Stack<T>) ==========");
            System.out.println("1. Thêm thao tác mới (Lưu vào lịch sử)");
            System.out.println("2. Hoàn tác (Undo thao tác gần nhất)");
            System.out.println("3. Hiển thị danh sách thao tác còn lại trong ngăn xếp");
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
                    themThaoTac();
                    break;
                case 2:
                    hoanTac();
                    break;
                case 3:
                    hienThiNganXep();
                    break;
                case 0:
                    System.out.println("Tạm biệt!");
                    break;
                default:
                    System.out.println("Lựa chọn không hợp lệ!");
            }
        } while (luaChon != 0);
    }

    private static void themThaoTac() {
        System.out.print("Nhập nội dung thao tác mới: ");
        String thaoTac = scanner.nextLine().trim();
        if (thaoTac.isEmpty()) {
            System.out.println("Nội dung thao tác không được rỗng!");
            return;
        }

        nganXepThaoTac.push(thaoTac);
        System.out.println(">> Đã ghi nhận thao tác: \"" + thaoTac + "\" vào ngăn xếp.");
    }

    private static void hoanTac() {
        if (nganXepThaoTac.isEmpty()) {
            System.out.println(">> Ngăn xếp trống! Không có thao tác nào để hoàn tác (Undo).");
            return;
        }

        // Pop lấy phần tử ở đỉnh ngăn xếp (thao tác mới nhất)
        String thaoTacVuaUndo = nganXepThaoTac.pop();
        System.out.println("\n------------------------------------------------");
        System.out.println(">> ĐÃ HOÀN TÁC (UNDO) THAO TÁC: \"" + thaoTacVuaUndo + "\"");
        System.out.println("------------------------------------------------");
        if (!nganXepThaoTac.isEmpty()) {
            System.out.println(">> Trạng thái hiện tại đang ở thao tác: \"" + nganXepThaoTac.peek() + "\"");
        } else {
            System.out.println(">> Đã hoàn tác hết tất cả các thao tác!");
        }
    }

    private static void hienThiNganXep() {
        if (nganXepThaoTac.isEmpty()) {
            System.out.println(">> Ngăn xếp lịch sử thao tác hiện đang trống!");
            return;
        }

        System.out.println("\n----------------- DANH SÁCH THAO TÁC TRONG NGĂN XẾP -----------------");
        System.out.println("(Thứ tự từ đỉnh ngăn xếp [thao tác mới nhất] xuống đáy)");
        for (int i = nganXepThaoTac.size() - 1; i >= 0; i--) {
            String danhDau = (i == nganXepThaoTac.size() - 1) ? " [ĐỈNH STACK - Sẽ Undo trước]" : "";
            System.out.printf("  %2d. %s%s\n", (nganXepThaoTac.size() - i), nganXepThaoTac.get(i), danhDau);
        }
        System.out.println("---------------------------------------------------------------------");
        System.out.println(">> Tổng số thao tác hiện có: " + nganXepThaoTac.size());
    }
}
