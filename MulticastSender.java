import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * ==============================================================================
 * MULTICAST SENDER - Gửi thông điệp đến Nhóm máy đăng ký (Lớp D)
 * ------------------------------------------------------------------------------
 * Giảng viên: ThS. Trần Minh Nhật - Đại học Sài Gòn (SGU)
 * Rule 3: Bắt buộc sử dụng MulticastSocket và gửi tới một địa chỉ IP thuộc lớp D (239.1.1.1).
 * ==============================================================================
 */
public class MulticastSender {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║                UDP MULTICAST SENDER (1 - GROUP)                ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");

        try {
            // 1. Tạo MulticastSocket (Bắt buộc theo Rule 3)
            MulticastSocket socket = new MulticastSocket();
            System.out.println("[Sender] ✓ Khởi tạo MulticastSocket thành công (Rule 3).");

            int port = 8000;
            String groupIp = "239.1.1.1";

            // 2. Chuẩn bị thông điệp
            System.out.print("Nhập thông điệp gửi nhóm Multicast (239.1.1.1): ");
            String message = scanner.nextLine().trim();
            if (message.isEmpty()) {
                message = "Thông báo đặc biệt cho nhóm Multicast 239.1.1.1!";
            }
            byte[] data = message.getBytes(StandardCharsets.UTF_8);

            // 3. Tạo packet gửi đến địa chỉ Multicast Lớp D 239.1.1.1
            InetAddress multicastAddress = InetAddress.getByName(groupIp);
            DatagramPacket packet = new DatagramPacket(data, data.length, multicastAddress, port);

            System.out.println("[Sender] 🎯 Đang gửi Multicast tới " + groupIp + ":" + port);
            System.out.println("[Sender] Thông điệp: \"" + message + "\"");
            System.out.println("[Sender] 💡 Lưu ý quan trọng: CHỈ máy nào gọi joinGroup() mới nhận được dữ liệu này!");

            // 4. Gửi gói tin
            socket.send(packet);

            System.out.println("[Sender] ✓ Đã gửi Multicast thành công!");

            // 5. Đóng socket
            socket.close();
            System.out.println("[Sender] Đã đóng socket.");
        } catch (Exception e) {
            System.err.println("❌ Lỗi Multicast Sender: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
