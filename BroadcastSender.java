import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * ==============================================================================
 * BROADCAST SENDER - Gửi thông điệp đến TẤT CẢ các máy trong mạng LAN
 * ------------------------------------------------------------------------------
 * Giảng viên: ThS. Trần Minh Nhật - Đại học Sài Gòn (SGU)
 * Rule 2: Bắt buộc dùng DatagramSocket, phải gọi setBroadcast(true) trước khi gửi,
 *         gửi đến địa chỉ broadcast đại diện (255.255.255.255).
 * ==============================================================================
 */
public class BroadcastSender {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║                 UDP BROADCAST SENDER (1 - ALL)                 ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");

        try {
            // 1. Tạo DatagramSocket
            DatagramSocket socket = new DatagramSocket();

            // 2. BẮT BUỘC BẬT setBroadcast(true) theo Rule 2
            socket.setBroadcast(true);
            System.out.println("[Sender] ✓ Đã bật cờ Broadcast: socket.setBroadcast(true)");

            int port = 7000;

            // 3. Chuẩn bị thông điệp
            System.out.print("Nhập thông điệp phát sóng Broadcast: ");
            String message = scanner.nextLine().trim();
            if (message.isEmpty()) {
                message = "Thông báo khẩn: Kiểm tra toàn bộ hệ thống phòng Lab!";
            }
            byte[] data = message.getBytes(StandardCharsets.UTF_8);

            // 4. Tạo packet gửi đến 255.255.255.255 (Địa chỉ phát thanh toàn mạng)
            InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");
            DatagramPacket packet = new DatagramPacket(data, data.length, broadcastAddress, port);

            System.out.println("[Sender] 📢 Đang gửi Broadcast tới 255.255.255.255:" + port);
            System.out.println("[Sender] Thông điệp: \"" + message + "\"");

            // 5. Gửi gói tin
            socket.send(packet);

            System.out.println("[Sender] ✓ Đã gửi Broadcast thành công!");
            System.out.println("[Sender] Lưu ý: TẤT CẢ các máy trong mạng LAN mở port " + port + " đều nhận được!");

            socket.close();
            System.out.println("[Sender] Đã đóng socket.");
        } catch (Exception e) {
            System.err.println("❌ Lỗi Broadcast Sender: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
