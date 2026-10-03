import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;

public class DocFile {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== BÀI 3: SỬ DỤNG FINALLY ĐỂ ĐÓNG TẬP TIN ===");
        System.out.print("Nhập đường dẫn tập tin cần đọc (ví dụ: test.txt): ");
        String filePath = scanner.nextLine().trim();

        BufferedReader reader = null;
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                throw new FileNotFoundException("Tập tin '" + filePath + "' không tồn tại trên hệ thống!");
            }

            reader = new BufferedReader(new FileReader(file));
            System.out.println("\n>> Nội dung tập tin:");
            System.out.println("----------------------------------------");
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                lineCount++;
            }
            System.out.println("----------------------------------------");
            System.out.println(">> Đọc thành công " + lineCount + " dòng.");

        } catch (FileNotFoundException e) {
            System.err.println(">> Bắt ngoại lệ FileNotFoundException: " + e.getMessage());
        } catch (IOException e) {
            System.err.println(">> Lỗi khi đọc tập tin: " + e.getMessage());
        } finally {
            // Khối finally LUÔN LUÔN được thực thi để giải phóng tài nguyên
            System.out.println("\n[FINALLY] Bắt đầu thực thi khối finally...");
            if (reader != null) {
                try {
                    reader.close();
                    System.out.println("[FINALLY] Đã đóng luồng tập tin an toàn.");
                } catch (IOException e) {
                    System.err.println("[FINALLY] Lỗi khi đóng tập tin: " + e.getMessage());
                }
            } else {
                System.out.println("[FINALLY] Luồng tập tin chưa từng được mở hoặc đã rỗng.");
            }
            System.out.println("[FINALLY] Hoàn tất dọn dẹp tài nguyên.");
        }

        scanner.close();
    }
}
