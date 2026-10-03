import java.util.Scanner;

// Lớp ngoại lệ tự định nghĩa cho điểm không hợp lệ
class InvalidDiemException extends Exception {
    public InvalidDiemException(String message) {
        super(message);
    }
}

public class KiemTraSinhVien {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== BÀI 2: KIỂM TRA DỮ LIỆU BẰNG THROW ===");

        System.out.print("Nhập mã sinh viên: ");
        String maSV = scanner.nextLine().trim();

        System.out.print("Nhập họ tên sinh viên: ");
        String hoTen = scanner.nextLine().trim();

        double diemTB = 0.0;
        while (true) {
            try {
                System.out.print("Nhập điểm trung bình (0.0 - 10.0): ");
                String input = scanner.nextLine().trim();
                diemTB = Double.parseDouble(input);

                // Kiểm tra và phát sinh ngoại lệ bằng throw theo yêu cầu đề bài
                if (diemTB < 0 || diemTB > 10) {
                    throw new InvalidDiemException(
                            "Điểm không hợp lệ: " + diemTB + ". Điểm phải nằm trong đoạn [0, 10]!");
                }

                // Nếu hợp lệ thì thoát vòng lặp
                break;
            } catch (NumberFormatException e) {
                System.out.println(">> Lỗi: Dữ liệu điểm phải là số thực! Vui lòng nhập lại.");
            } catch (InvalidDiemException e) {
                System.out.println(">> Bắt ngoại lệ bằng throw: " + e.getMessage());
                System.out.println(">> Vui lòng nhập lại điểm!");
            }
        }

        System.out.println("\n--- THÔNG TIN SINH VIÊN ĐÃ NHẬP HỢP LỆ ---");
        System.out.println("Mã sinh viên   : " + maSV);
        System.out.println("Họ tên         : " + hoTen);
        System.out.printf("Điểm trung bình: %.2f\n", diemTB);
        scanner.close();
    }
}
