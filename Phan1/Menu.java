import java.util.Scanner;

public class Menu {

    // Khai báo biến toàn cục
    private static Scanner scanner = new Scanner(System.in);
    private static int choice;
    private static double radius;
    private static double length;
    private static double width;
    private static int number;
    private static int n;

    public static void main(String[] args) {
        boolean running = true;

        while (running) {
            displayMenu();
            choice = getUserChoice();

            switch (choice) {
                case 1:
                    calculateCirclePerimeter();
                    break;
                case 2:
                    calculateRectangleArea();
                    break;
                case 3:
                    checkPrimeNumber();
                    break;
                case 4:
                    printMultiplicationTable();
                    break;
                case 0:
                    System.out.println("Tạm biệt!");
                    running = false;
                    break;
                default:
                    System.out.println("Lựa chọn không hợp lệ!");
            }
        }

        scanner.close();
    }

    // Hiển thị menu
    private static void displayMenu() {
        System.out.println("\n" + "=".repeat(24));
        System.out.println("           MENU");
        System.out.println("=".repeat(24));
        System.out.println("1. Tính chu vi hình tròn");
        System.out.println("2. Tính diện tích hình chữ nhật");
        System.out.println("3. Kiểm tra số nguyên tố");
        System.out.println("4. In bảng cửu chương");
        System.out.println("0. Thoát");
        System.out.println("=".repeat(24));
    }

    // Nhập lựa chọn từ người dùng
    private static int getUserChoice() {
        try {
            System.out.print("Nhập lựa chọn của bạn: ");
            return scanner.nextInt();
        } catch (Exception e) {
            scanner.nextLine(); // Xóa buffer
            System.out.println("Vui lòng nhập số hợp lệ!");
            return -1;
        }
    }

    // Tính chu vi hình tròn
    private static void calculateCirclePerimeter() {
        try {
            System.out.print("Nhập bán kính: ");
            radius = scanner.nextDouble();

            if (radius < 0) {
                System.out.println("Bán kính phải lớn hơn 0!");
                return;
            }

            double perimeter = 2 * Math.PI * radius;
            System.out.printf("Chu vi hình tròn: %.2f%n", perimeter);
        } catch (Exception e) {
            scanner.nextLine();
            System.out.println("Vui lòng nhập số hợp lệ!");
        }
    }

    // Tính diện tích hình chữ nhật
    private static void calculateRectangleArea() {
        try {
            System.out.print("Nhập chiều dài: ");
            length = scanner.nextDouble();
            System.out.print("Nhập chiều rộng: ");
            width = scanner.nextDouble();

            if (length < 0 || width < 0) {
                System.out.println("Chiều dài và chiều rộng phải lớn hơn 0!");
                return;
            }

            double area = length * width;
            System.out.printf("Diện tích hình chữ nhật: %.2f%n", area);
        } catch (Exception e) {
            scanner.nextLine();
            System.out.println("Vui lòng nhập số hợp lệ!");
        }
    }

    // Kiểm tra số nguyên tố
    private static void checkPrimeNumber() {
        try {
            System.out.print("Nhập số cần kiểm tra: ");
            number = scanner.nextInt();

            if (number < 2) {
                System.out.println(number + " không phải số nguyên tố");
                return;
            }

            boolean isPrime = true;
            for (int i = 2; i <= Math.sqrt(number); i++) {
                if (number % i == 0) {
                    isPrime = false;
                    break;
                }
            }

            if (isPrime) {
                System.out.println(number + " là số nguyên tố");
            } else {
                System.out.println(number + " không là số nguyên tố");
            }
        } catch (Exception e) {
            scanner.nextLine();
            System.out.println("Vui lòng nhập số hợp lệ!");
        }
    }

    // In bảng cửu chương
    private static void printMultiplicationTable() {
        try {
            System.out.print("Nhập số để in bảng cửu chương (1-10): ");
            n = scanner.nextInt();

            if (n < 1 || n > 10) {
                System.out.println("Vui lòng nhập số từ 1 đến 10");
                return;
            }

            System.out.println("\n--- Bảng cửu chương số " + n + " ---");
            for (int i = 1; i <= 10; i++) {
                System.out.printf("%d x %d = %d%n", n, i, n * i);
            }
        } catch (Exception e) {
            scanner.nextLine();
            System.out.println("Vui lòng nhập số hợp lệ!");
        }
    }
}
