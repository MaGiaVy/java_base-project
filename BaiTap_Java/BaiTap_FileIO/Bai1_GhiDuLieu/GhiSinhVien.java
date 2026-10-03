import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;

public class GhiSinhVien {
    private static final String FILE_NAME = "SinhVien.txt";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== BÀI 1: GHI DỮ LIỆU SINH VIÊN VÀO TẬP TIN ===");

        System.out.print("Nhập số lượng sinh viên cần ghi: ");
        int n = 0;
        try {
            n = Integer.parseInt(scanner.nextLine().trim());
            if (n <= 0) {
                System.out.println("Số lượng phải > 0!");
                scanner.close();
                return;
            }
        } catch (NumberFormatException e) {
            System.out.println("Vui lòng nhập số nguyên!");
            scanner.close();
            return;
        }

        // Mở file ở chế độ ghi tiếp (append = true)
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME, true))) {
            for (int i = 0; i < n; i++) {
                System.out.printf("\n--- Nhập sinh viên thứ %d ---\n", i + 1);
                System.out.print("Mã sinh viên: ");
                String maSV = scanner.nextLine().trim();

                System.out.print("Họ tên: ");
                String hoTen = scanner.nextLine().trim();

                System.out.print("Lớp: ");
                String lop = scanner.nextLine().trim();

                double diemTB;
                while (true) {
                    try {
                        System.out.print("Điểm trung bình (0.0 - 10.0): ");
                        diemTB = Double.parseDouble(scanner.nextLine().trim());
                        if (diemTB >= 0 && diemTB <= 10)
                            break;
                        System.out.println("Điểm phải từ 0 đến 10!");
                    } catch (NumberFormatException e) {
                        System.out.println("Vui lòng nhập số thực hợp lệ!");
                    }
                }

                // Định dạng dòng: MaSV;HoTen;Lop;DiemTB
                String line = String.format("%s;%s;%s;%.2f", maSV, hoTen, lop, diemTB);
                writer.write(line);
                writer.newLine();
                System.out.println(">> Đã ghi sinh viên vào file: " + line);
            }
            System.out.println("\n>> Ghi toàn bộ dữ liệu vào tập tin '" + FILE_NAME + "' thành công!");
        } catch (IOException e) {
            System.err.println("Lỗi khi ghi tập tin: " + e.getMessage());
        }

        scanner.close();
    }
}
