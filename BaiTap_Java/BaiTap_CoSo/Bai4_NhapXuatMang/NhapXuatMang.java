import java.util.Scanner;

public class NhapXuatMang {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== BÀI 4: NHẬP VÀ XUẤT MẢNG ===");
        int n = 0;
        while (true) {
            try {
                System.out.print("Nhập số phần tử n (n > 0): ");
                n = Integer.parseInt(scanner.nextLine().trim());
                if (n > 0)
                    break;
                System.out.println("Vui lòng nhập số nguyên dương!");
            } catch (NumberFormatException e) {
                System.out.println("Giá trị không hợp lệ! Vui lòng nhập số nguyên.");
            }
        }

        int[] arr = new int[n];
        System.out.println("--- Nhập các phần tử của mảng ---");
        for (int i = 0; i < n; i++) {
            while (true) {
                try {
                    System.out.printf("arr[%d] = ", i);
                    arr[i] = Integer.parseInt(scanner.nextLine().trim());
                    break;
                } catch (NumberFormatException e) {
                    System.out.println("Giá trị không hợp lệ! Vui lòng nhập lại số nguyên.");
                }
            }
        }

        System.out.println("\n--- Xuất mảng ra màn hình ---");
        System.out.print("Các phần tử trong mảng: ");
        for (int i = 0; i < n; i++) {
            System.out.print(arr[i] + (i < n - 1 ? ", " : ""));
        }
        System.out.println();
        scanner.close();
    }
}
