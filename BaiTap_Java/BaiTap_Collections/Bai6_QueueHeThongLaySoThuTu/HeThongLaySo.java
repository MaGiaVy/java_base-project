import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;

public class HeThongLaySo {
    private static Scanner scanner = new Scanner(System.in);
    // Queue<String> mô phỏng hàng đợi lấy số thứ tự của ngân hàng/phòng khám
    private static Queue<String> hangDoi = new LinkedList<>();
    private static int soThuTuTuDong = 1001;

    public static void main(String[] args) {
        // Khởi tạo vài khách hàng mẫu
        hangDoi.offer("STT-" + (soThuTuTuDong++) + ": Nguyễn Văn A");
        hangDoi.offer("STT-" + (soThuTuTuDong++) + ": Trần Thị B");
        hangDoi.offer("STT-" + (soThuTuTuDong++) + ": Lê Văn C");

        int luaChon = -1;
        do {
            System.out.println("\n========== BÀI 6: HỆ THỐNG LẤY SỐ THỨ TỰ (Queue<T>) ==========");
            System.out.println("1. Thêm khách hàng vào hàng đợi (Lấy số thứ tự)");
            System.out.println("2. Phục vụ khách hàng đầu tiên");
            System.out.println("3. Hiển thị danh sách khách hàng đang chờ");
            System.out.println("4. Hiển thị số lượng khách hàng còn lại");
            System.out.println("0. Thoát");
            System.out.print("Chọn chức năng (0-4): ");

            try {
                luaChon = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Vui lòng nhập số!");
                continue;
            }

            switch (luaChon) {
                case 1:
                    themKhachHang();
                    break;
                case 2:
                    phucVuKhachHang();
                    break;
                case 3:
                    hienThiHangDoi();
                    break;
                case 4:
                    hienThiSoLuong();
                    break;
                case 0:
                    System.out.println("Tạm biệt!");
                    break;
                default:
                    System.out.println("Lựa chọn không hợp lệ!");
            }
        } while (luaChon != 0);
    }

    private static void themKhachHang() {
        System.out.print("Nhập tên khách hàng lấy số: ");
        String ten = scanner.nextLine().trim();
        if (ten.isEmpty()) {
            System.out.println("Tên không được để trống!");
            return;
        }

        String veSo = "STT-" + (soThuTuTuDong++) + ": " + ten;
        hangDoi.offer(veSo); // Thêm vào cuối hàng đợi
        System.out.println(">> Đã phát số thành công: " + veSo);
        System.out.println(">> Số khách hàng đang chờ phía trước bạn: " + (hangDoi.size() - 1));
    }

    private static void phucVuKhachHang() {
        if (hangDoi.isEmpty()) {
            System.out.println(">> Hàng đợi trống! Hiện không có khách hàng nào đang chờ.");
            return;
        }

        // Lấy khách hàng ở đầu hàng đợi (FIFO: First In First Out)
        String khach = hangDoi.poll();
        System.out.println("\n************************************************");
        System.out.println(">> MỜI KHÁCH HÀNG VÀO QUẦY PHỤC VỤ: " + khach);
        System.out.println("************************************************");
        System.out.println(">> Số khách hàng còn lại trong hàng đợi: " + hangDoi.size());
    }

    private static void hienThiHangDoi() {
        if (hangDoi.isEmpty()) {
            System.out.println(">> Hàng đợi đang trống!");
            return;
        }

        System.out.println("\n----------------- DANH SÁCH KHÁCH HÀNG ĐANG CHỜ -----------------");
        int stt = 1;
        for (String kh : hangDoi) {
            System.out.printf("Vị trí %2d. %s\n", stt++, kh);
        }
        System.out.println("-----------------------------------------------------------------");
        System.out.println(">> Khách hàng tiếp theo sẽ được phục vụ: " + hangDoi.peek());
    }

    private static void hienThiSoLuong() {
        System.out.println(">> Số lượng khách hàng còn lại trong hàng đợi: " + hangDoi.size() + " người.");
    }
}
