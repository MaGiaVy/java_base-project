import java.util.Scanner;

public class PhepChia {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== BÀI 1: XỬ LÝ LỖI NHẬP DỮ LIỆU VÀ PHÉP CHIA ===");

        while (true) {
            try {
                System.out.print("\nNhập số nguyên thứ nhất (tử số): ");
                int a = Integer.parseInt(scanner.nextLine().trim());

                System.out.print("Nhập số nguyên thứ hai (mẫu số): ");
                int b = Integer.parseInt(scanner.nextLine().trim());

                if (b == 0) {
                    throw new ArithmeticException("Lỗi: Không thể chia cho 0!");
                }

                int ketQuaNguyen = a / b;
                double ketQuaThuc = (double) a / b;
                System.out.printf(">> Kết quả phép chia: %d / %d = %.4f (phép chia nguyên: %d)\n",
                        a, b, ketQuaThuc, ketQuaNguyen);
                break;
            } catch (NumberFormatException e) {
                System.out.println(">> Lỗi: Nhập sai kiểu dữ liệu! Vui lòng nhập số nguyên hợp lệ.");
            } catch (ArithmeticException e) {
                System.out.println(">> " + e.getMessage());
            } catch (Exception e) {
                System.out.println(">> Lỗi không xác định: " + e.getMessage());
            }
        }

        System.out.println("Chương trình kết thúc thành công.");
        scanner.close();
    }
}
