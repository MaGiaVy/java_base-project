import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * ==============================================================================
 * UNICAST SENDER - Gửi thông điệp 1 - 1
 * ------------------------------------------------------------------------------
 * Giảng viên: ThS. Trần Minh Nhật - Đại học Sài Gòn (SGU)
 * Rule 1: Sử dụng DatagramSocket, chỉ định rõ IP máy nhận cụ thể.
 * ==============================================================================
 */
public class UnicastSender {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║                   UDP UNICAST SENDER (1 - 1)                   ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");

        try {
            // 1. Tạo DatagramSocket
            DatagramSocket socket = new DatagramSocket();
            System.out.println("[Sender] ✓ Khởi tạo DatagramSocket thành công.");

            // 2. Nhập IP máy nhận cụ thể
            System.out.print("Nhập IP máy nhận (Mặc định 127.0.0.1): ");
            String receiverIp = scanner.nextLine().trim();
            if (receiverIp.isEmpty()) {
                receiverIp = "127.0.0.1";
            }
            int port = 7000;

            // 3. Chuẩn bị thông điệp
            System.out.print("Nhập thông điệp gửi Unicast: ");
            String message = scanner.nextLine().trim();
            if (message.isEmpty()) {
                message = "Xin chào máy nhận Unicast từ SGU!";
            }
            byte[] data = message.getBytes(StandardCharsets.UTF_8);

            // 4. Tạo DatagramPacket hướng tới IP và Port cụ thể
            InetAddress recipientAddress = InetAddress.getByName(receiverIp);
            DatagramPacket packet = new DatagramPacket(data, data.length, recipientAddress, port);

            System.out.println("[Sender] 📤 Đang gửi Unicast tới " + receiverIp + ":" + port);
            System.out.println("[Sender] Thông điệp: \"" + message + "\"");

            // 5. Gửi gói tin
            socket.send(packet);
            System.out.println("[Sender] ✓ Đã gửi Unicast thành công!");

            // 6. Đóng socket
            socket.close();
            System.out.println("[Sender] Đã đóng socket.");
        } catch (Exception e) {
            System.err.println("❌ Lỗi Unicast Sender: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
