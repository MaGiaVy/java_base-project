import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class DocSinhVien {
    private static final String FILE_NAME = "SinhVien.txt";

    public static void main(String[] args) {
        System.out.println("=== BÀI 2: ĐỌC DỮ LIỆU TỪ TẬP TIN SinhVien.txt ===");

        File file = new File(FILE_NAME);
        if (!file.exists()) {
            System.out.println(">> Tập tin '" + FILE_NAME + "' không tồn tại trong thư mục hiện tại!");
            System.out.println(">> Vui lòng chạy Bài 1 để tạo file hoặc tạo file SinhVien.txt trước.");
            return;
        }

        int count = 0;
        System.out.println("\n----------------- TOÀN BỘ DANH SÁCH SINH VIÊN TỪ FILE -----------------");
        System.out.printf("%-5s | %-10s | %-22s | %-10s | %-8s\n", "STT", "Mã SV", "Họ tên", "Lớp", "Điểm TB");
        System.out.println("-----------------------------------------------------------------------");

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty())
                    continue;

                // Tách theo dấu chấm phẩy ;
                String[] parts = line.split(";");
                if (parts.length >= 4) {
                    count++;
                    String maSV = parts[0].trim();
                    String hoTen = parts[1].trim();
                    String lop = parts[2].trim();
                    String diemTB = parts[3].trim();
                    System.out.printf("%-5d | %-10s | %-22s | %-10s | %-8s\n", count, maSV, hoTen, lop, diemTB);
                }
            }
            System.out.println("-----------------------------------------------------------------------");
            System.out.println(">> TỔNG SỐ LƯỢNG SINH VIÊN TRONG TẬP TIN: " + count + " sinh viên.");

        } catch (IOException e) {
            System.err.println("Lỗi khi đọc tập tin: " + e.getMessage());
        }
    }
}
