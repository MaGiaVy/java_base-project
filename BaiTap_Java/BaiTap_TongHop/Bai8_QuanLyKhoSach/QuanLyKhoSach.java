import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;
import java.util.Stack;

// Ngoại lệ khi mã sách không tồn tại
class BookNotFoundException extends Exception {
    public BookNotFoundException(String message) {
        super(message);
    }
}

// Ngoại lệ khi sách trong kho đã hết
class OutOfStockException extends Exception {
    public OutOfStockException(String message) {
        super(message);
    }
}

// Ngoại lệ dữ liệu nhập không hợp lệ
class InvalidInputException extends Exception {
    public InvalidInputException(String message) {
        super(message);
    }
}

public class QuanLyKhoSach {
    private static Scanner scanner = new Scanner(System.in);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 1. Kho sách lưu bằng List<Sach>
    private static List<Sach> listSach = new ArrayList<>();

    // 2. Tra cứu nhanh bằng Map (Dictionary<string, Sach>) theo Mã Sách
    private static Map<String, Sach> mapSach = new HashMap<>();

    // 5. Lưu lịch sử mượn/trả bằng Stack<String>
    private static Stack<String> lichSuMuonTra = new Stack<>();

    // 6. Quản lý danh sách người chờ mượn bằng Queue<String>
    private static Queue<String> hangDoiCho = new LinkedList<>();

    public static void main(String[] args) {
        // Dữ liệu mẫu ban đầu
        khoiTaoDuLieuMau();

        int luaChon = -1;
        do {
            System.out.println("\n========== BÀI 8: CHƯƠNG TRÌNH QUẢN LÝ KHO SÁCH (TỔNG HỢP) ==========");
            System.out.println("1. Thêm sách mới vào kho (List<Sach>)");
            System.out.println("2. Hiển thị toàn bộ kho sách");
            System.out.println("3. Tìm sách theo mã (Tra cứu nhanh qua Map/Dictionary)");
            System.out.println("4. Mượn sách (Giảm số lượng, ghi nhận vào Stack)");
            System.out.println("5. Trả sách (Tăng số lượng, ghi nhận vào Stack)");
            System.out.println("6. Xem lịch sử mượn/trả sách (Stack)");
            System.out.println("7. Thêm người vào danh sách chờ mượn (Queue)");
            System.out.println("8. Phục vụ người chờ mượn sách đầu tiên (Queue)");
            System.out.println("9. Xem danh sách hàng đợi người chờ (Queue)");
            System.out.println("0. Thoát");
            System.out.print("Chọn chức năng (0-9): ");

            try {
                luaChon = Integer.parseInt(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Vui lòng nhập số!");
                continue;
            }

            switch (luaChon) {
                case 1:
                    themSachMoi();
                    break;
                case 2:
                    hienThiKhoSach();
                    break;
                case 3:
                    timSachTheoMa();
                    break;
                case 4:
                    muonSach();
                    break;
                case 5:
                    traSach();
                    break;
                case 6:
                    xemLichSu();
                    break;
                case 7:
                    themNguoiCho();
                    break;
                case 8:
                    phucVuNguoiCho();
                    break;
                case 9:
                    xemHangDoiCho();
                    break;
                case 0:
                    System.out.println("Tạm biệt!");
                    break;
                default:
                    System.out.println("Lựa chọn không hợp lệ!");
            }
        } while (luaChon != 0);
    }

    private static void khoiTaoDuLieuMau() {
        themSachVaoHeThong(new Sach("B01", "Lập Trình Java Nâng Cao", "Nguyễn Văn A", 3));
        themSachVaoHeThong(new Sach("B02", "Cấu Trúc Dữ Liệu & Giải Thuật", "Trần Văn B", 1));
        themSachVaoHeThong(new Sach("B03", "Cơ Sở Dữ Liệu PostgreSQL", "Lê Văn C", 0)); // Thử nghiệm hết sách
        themSachVaoHeThong(new Sach("B04", "Trí Tuệ Nhân Tạo Cơ Bản", "Phạm Văn D", 5));

        hangDoiCho.offer("Nguyễn Hoàng Long (chờ sách B03)");
        hangDoiCho.offer("Trần Minh Quân (chờ sách B02)");
    }

    private static void themSachVaoHeThong(Sach s) {
        listSach.add(s);
        mapSach.put(s.getMaSach().toUpperCase(), s);
    }

    // 1. Thêm sách
    private static void themSachMoi() {
        try {
            System.out.print("Nhập mã sách: ");
            String ma = scanner.nextLine().trim();
            if (ma.isEmpty()) {
                throw new InvalidInputException("Mã sách không được để trống!");
            }
            if (mapSach.containsKey(ma.toUpperCase())) {
                throw new InvalidInputException("Mã sách '" + ma + "' đã tồn tại trong kho!");
            }

            System.out.print("Nhập tên sách: ");
            String ten = scanner.nextLine().trim();
            if (ten.isEmpty()) {
                throw new InvalidInputException("Tên sách không được để trống!");
            }

            System.out.print("Nhập tác giả: ");
            String tacGia = scanner.nextLine().trim();
            if (tacGia.isEmpty()) {
                throw new InvalidInputException("Tên tác giả không được để trống!");
            }

            System.out.print("Nhập số lượng ban đầu: ");
            int soLuong;
            try {
                soLuong = Integer.parseInt(scanner.nextLine().trim());
                if (soLuong < 0) {
                    throw new InvalidInputException("Số lượng sách không được âm!");
                }
            } catch (NumberFormatException e) {
                throw new InvalidInputException("Số lượng phải là một số nguyên hợp lệ!");
            }

            Sach sachMoi = new Sach(ma, ten, tacGia, soLuong);
            themSachVaoHeThong(sachMoi);
            System.out.println(">> Thêm sách mới vào kho thành công: " + sachMoi);

        } catch (InvalidInputException e) {
            System.err.println(">> LỖI DỮ LIỆU NHẬP: " + e.getMessage());
        }
    }

    // 2. Hiển thị kho sách
    private static void hienThiKhoSach() {
        if (listSach.isEmpty()) {
            System.out.println(">> Kho sách hiện đang trống!");
            return;
        }
        System.out.println("\n----------------- DANH SÁCH SÁCH TRONG KHO (List<Sach>) -----------------");
        for (int i = 0; i < listSach.size(); i++) {
            System.out.printf("%2d. %s\n", i + 1, listSach.get(i));
        }
        System.out.println("-------------------------------------------------------------------------");
        System.out.println(">> Tổng số đầu sách: " + listSach.size());
    }

    // 3. Tìm sách theo mã bằng Map
    private static void timSachTheoMa() {
        try {
            System.out.print("Nhập mã sách cần tìm (tra cứu O(1) qua Map): ");
            String ma = scanner.nextLine().trim().toUpperCase();

            Sach s = mapSach.get(ma);
            if (s == null) {
                throw new BookNotFoundException("Không tìm thấy sách nào có mã: '" + ma + "'!");
            }

            System.out.println(">> TÌM THẤY SÁCH THÀNH CÔNG:");
            System.out.println("   " + s);
        } catch (BookNotFoundException e) {
            System.err.println(">> LỖI TRA CỨU: " + e.getMessage());
        }
    }

    // 4. Mượn sách
    private static void muonSach() {
        try {
            System.out.print("Nhập mã sách muốn mượn: ");
            String ma = scanner.nextLine().trim().toUpperCase();

            Sach s = mapSach.get(ma);
            if (s == null) {
                throw new BookNotFoundException("Mã sách '" + ma + "' không tồn tại trong hệ thống!");
            }

            if (s.getSoLuong() <= 0) {
                throw new OutOfStockException("Sách '" + s.getTenSach() + "' (Mã: " + ma
                        + ") ĐÃ HẾT TRONG KHO! Vui lòng chờ người khác trả hoặc vào hàng đợi.");
            }

            System.out.print("Nhập tên người mượn: ");
            String nguoiMuon = scanner.nextLine().trim();
            if (nguoiMuon.isEmpty()) {
                throw new InvalidInputException("Tên người mượn không được rỗng!");
            }

            // Giảm số lượng
            s.setSoLuong(s.getSoLuong() - 1);

            // Ghi nhận vào Stack lịch sử
            String log = String.format("[%s] MƯỢN: %s mượn cuốn '%s' (Mã: %s). Còn lại: %d",
                    LocalDateTime.now().format(TIME_FMT), nguoiMuon, s.getTenSach(), ma, s.getSoLuong());
            lichSuMuonTra.push(log);

            System.out.println(">> MƯỢN SÁCH THÀNH CÔNG!");
            System.out.println("   " + log);

        } catch (BookNotFoundException | OutOfStockException | InvalidInputException e) {
            System.err.println(">> LỖI KHI MƯỢN SÁCH: " + e.getMessage());
        }
    }

    // 5. Trả sách
    private static void traSach() {
        try {
            System.out.print("Nhập mã sách muốn trả: ");
            String ma = scanner.nextLine().trim().toUpperCase();

            Sach s = mapSach.get(ma);
            if (s == null) {
                throw new BookNotFoundException("Mã sách '" + ma + "' không tồn tại trong kho!");
            }

            System.out.print("Nhập tên người trả: ");
            String nguoiTra = scanner.nextLine().trim();
            if (nguoiTra.isEmpty()) {
                throw new InvalidInputException("Tên người trả không được rỗng!");
            }

            // Tăng số lượng
            s.setSoLuong(s.getSoLuong() + 1);

            // Ghi nhận vào Stack lịch sử
            String log = String.format("[%s] TRẢ : %s trả cuốn '%s' (Mã: %s). Hiện có: %d",
                    LocalDateTime.now().format(TIME_FMT), nguoiTra, s.getTenSach(), ma, s.getSoLuong());
            lichSuMuonTra.push(log);

            System.out.println(">> TRẢ SÁCH THÀNH CÔNG!");
            System.out.println("   " + log);

        } catch (BookNotFoundException | InvalidInputException e) {
            System.err.println(">> LỖI KHI TRẢ SÁCH: " + e.getMessage());
        }
    }

    // 6. Xem lịch sử mượn/trả (Stack)
    private static void xemLichSu() {
        if (lichSuMuonTra.isEmpty()) {
            System.out.println(">> Chưa có lịch sử mượn/trả nào trong ngăn xếp!");
            return;
        }

        System.out.println("\n----------------- LỊCH SỬ MƯỢN / TRẢ SÁCH (Stack<T>) -----------------");
        System.out.println("(Thứ tự từ thao tác gần đây nhất xuống cũ hơn)");
        for (int i = lichSuMuonTra.size() - 1; i >= 0; i--) {
            System.out.printf("%2d. %s\n", (lichSuMuonTra.size() - i), lichSuMuonTra.get(i));
        }
        System.out.println("-----------------------------------------------------------------------");
    }

    // 7. Thêm người vào danh sách chờ (Queue)
    private static void themNguoiCho() {
        System.out.print("Nhập tên người chờ mượn: ");
        String ten = scanner.nextLine().trim();
        if (ten.isEmpty()) {
            System.out.println("Tên không được để trống!");
            return;
        }

        System.out.print("Nhập mã sách muốn đăng ký chờ: ");
        String ma = scanner.nextLine().trim().toUpperCase();

        String thongTin = String.format("%s (chờ sách mã %s)", ten, ma);
        hangDoiCho.offer(thongTin);
        System.out.println(">> Đã thêm vào hàng đợi chờ: " + thongTin);
        System.out.println(">> Vị trí của bạn trong hàng đợi: số " + hangDoiCho.size());
    }

    // 8. Phục vụ người chờ mượn (Queue)
    private static void phucVuNguoiCho() {
        if (hangDoiCho.isEmpty()) {
            System.out.println(">> Hàng đợi chờ mượn sách hiện đang trống!");
            return;
        }

        String nguoiDauTien = hangDoiCho.poll();
        System.out.println("\n************************************************");
        System.out.println(">> MỜI NGƯỜI CHỜ TIẾP THEO ĐẾN QUẦY THỦ TỤC:");
        System.out.println("   " + nguoiDauTien);
        System.out.println("************************************************");
        System.out.println(">> Số người còn lại trong hàng đợi: " + hangDoiCho.size());
    }

    // 9. Xem danh sách hàng đợi (Queue)
    private static void xemHangDoiCho() {
        if (hangDoiCho.isEmpty()) {
            System.out.println(">> Danh sách hàng đợi người chờ đang trống!");
            return;
        }

        System.out.println("\n----------------- DANH SÁCH NGƯỜI CHỜ MƯỢN SÁCH (Queue<T>) -----------------");
        int stt = 1;
        for (String ng : hangDoiCho) {
            System.out.printf("Thứ tự %2d. %s\n", stt++, ng);
        }
        System.out.println("----------------------------------------------------------------------------");
        System.out.println(">> Người tiếp theo được gọi: " + hangDoiCho.peek());
    }
}
