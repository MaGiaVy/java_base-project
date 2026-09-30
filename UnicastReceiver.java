import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * ==============================================================================
 * UNICAST RECEIVER - Nhận thông điệp 1 - 1
 * ------------------------------------------------------------------------------
 * Giảng viên: ThS. Trần Minh Nhật - Đại học Sài Gòn (SGU)
 * Lắng nghe tại port 7000 bằng DatagramSocket
 * ==============================================================================
 */
public class UnicastReceiver {
    public static void main(String[] args) {
        int port = 7000;
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║                  UDP UNICAST RECEIVER (1 - 1)                  ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");

        try {
            // 1. Tạo DatagramSocket lắng nghe port 7000
            DatagramSocket socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(port));

            System.out.println("[Receiver] Đang lắng nghe Unicast tại port " + port);

            // 2. Chuẩn bị buffer nhận dữ liệu
            byte[] buffer = new byte[2048];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            // 3. Chờ và nhận Datagram
            System.out.println("[Receiver] Chờ dữ liệu từ Sender...");
            socket.receive(packet);

            // 4. Giải mã dữ liệu
            String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

            System.out.println("[Receiver] ✓ Nhận được Unicast!");
            System.out.println("[Receiver]   • Từ máy   : " + packet.getAddress().getHostAddress() + ":" + packet.getPort());
            System.out.println("[Receiver]   • Chiều dài: " + packet.getLength() + " bytes");
            System.out.println("[Receiver]   • Nội dung : \"" + message + "\"");

            socket.close();
            System.out.println("[Receiver] Đã đóng socket.");
        } catch (Exception e) {
            System.err.println("❌ Lỗi Unicast Receiver: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
