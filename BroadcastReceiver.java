import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * ==============================================================================
 * BROADCAST RECEIVER - Nhận thông điệp Broadcast
 * ------------------------------------------------------------------------------
 * Giảng viên: ThS. Trần Minh Nhật - Đại học Sài Gòn (SGU)
 * Lắng nghe tại port 7000, bật setBroadcast(true)
 * ==============================================================================
 */
public class BroadcastReceiver {
    public static void main(String[] args) {
        int port = 7000;
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║                UDP BROADCAST RECEIVER (1 - ALL)                ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");

        try {
            // 1. Tạo DatagramSocket lắng nghe port 7000
            DatagramSocket socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(port));

            // 2. Bật cờ broadcast
            socket.setBroadcast(true);
            System.out.println("[Receiver] Đang lắng nghe Broadcast tại port " + port);
            System.out.println("[Receiver] *** Broadcast: Bất kỳ máy nào gửi tới 255.255.255.255 đều nhận được ***");
            System.out.println("[Receiver] Đang chờ dữ liệu... (Nhấn Ctrl+C để dừng)");

            byte[] buffer = new byte[2048];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

                System.out.println("\n[Receiver] 📢 ✓ Nhận được Broadcast!");
                System.out.println("[Receiver]   • Từ máy nguồn : " + packet.getAddress().getHostAddress() + ":" + packet.getPort());
                System.out.println("[Receiver]   • Chiều dài    : " + packet.getLength() + " bytes");
                System.out.println("[Receiver]   • Nội dung     : \"" + message + "\"");
                System.out.println("----------------------------------------------------------------");
            }
        } catch (Exception e) {
            System.err.println("❌ Lỗi Broadcast Receiver: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
