import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * ==============================================================================
 * MULTICAST RECEIVER - Nhận thông điệp Multicast (Chứng minh khác biệt với Broadcast)
 * ------------------------------------------------------------------------------
 * Giảng viên: ThS. Trần Minh Nhật - Đại học Sài Gòn (SGU)
 * Rule 4: Chứng minh khác biệt cốt lõi:
 *         - Cho phép chọn "Tham gia nhóm" (joinGroup()) hoặc "Không tham gia".
 *         - Chứng minh: Lắng nghe cùng port 8000 nhưng không gọi joinGroup() sẽ KHÔNG nhận được dữ liệu.
 * ==============================================================================
 */
public class MulticastReceiver {
    @SuppressWarnings("deprecation")
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int port = 8000;
        String groupAddress = "239.1.1.1";

        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║               UDP MULTICAST RECEIVER (DEMO RULE 4)             ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println("[Receiver] Nhóm Multicast Lớp D: " + groupAddress + ":" + port);
        System.out.println();

        // *** CHỨNG MINH KHÁC BIỆT THEO RULE 4 ***
        System.out.print("❓ Bạn có muốn gọi hàm joinGroup() để gia nhập nhóm không? (Y/N): ");
        String choice = scanner.nextLine().trim();

        try {
            MulticastSocket socket = new MulticastSocket(port);
            socket.setReuseAddress(true);
            InetAddress group = InetAddress.getByName(groupAddress);

            boolean joined = false;
            if (choice.equalsIgnoreCase("Y") || choice.equalsIgnoreCase("YES")) {
                // TRƯỜNG HỢP 1: GỌI joinGroup()
                socket.joinGroup(group);
                joined = true;
                System.out.println("[Receiver] ✓ ĐÃ gọi hàm socket.joinGroup(" + groupAddress + ")");
                System.out.println("[Receiver] Card mạng (NIC) đã gửi bản tin IGMP Report để tham gia nhóm.");
                System.out.println("[Receiver] Giờ đây bạn SẼ NHẬN ĐƯỢC thông điệp từ nhóm Multicast.");
                System.out.println("[Receiver] Đang lắng nghe tại port " + port + "...");
            } else {
                // TRƯỜNG HỢP 2: KHÔNG gọi joinGroup()
                System.out.println("[Receiver] ✗ KHÔNG gọi joinGroup() - Cố tình từ chối gia nhập nhóm!");
                System.out.println("[Receiver] ⚠️  [CHỨNG MINH KHÁC BIỆT VỚI BROADCAST]:");
                System.out.println("[Receiver]    Dù cổng " + port + " đang mở, gói tin Multicast gửi đến sẽ KHÔNG được nhận!");
                System.out.println("[Receiver] Đang chờ gói tin nhưng cố tình không gia nhập nhóm...");
                System.out.println("[Receiver] Chờ tối đa 10 giây (Timeout = 10000ms)...");
                socket.setSoTimeout(10000);
            }

            byte[] buffer = new byte[2048];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            try {
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

                System.out.println("\n[Receiver] 🎯 ✓ NHẬN ĐƯỢC THÀNH CÔNG GÓI TIN MULTICAST!");
                System.out.println("[Receiver]   • Từ máy nguồn : " + packet.getAddress().getHostAddress() + ":" + packet.getPort());
                System.out.println("[Receiver]   • Nội dung     : \"" + message + "\"");
                System.out.println("[Receiver] => Giải thích: Nhận được vì chương trình đã gọi joinGroup()!");

                if (joined) {
                    socket.leaveGroup(group);
                }
            } catch (SocketTimeoutException e) {
                System.out.println("\n[Receiver] ⏱️ Timeout - Hết thời gian chờ, KHÔNG nhận được dữ liệu!");
                if (!joined) {
                    System.out.println("[Receiver] 💡 KẾT QUẢ PHÂN TÍCH: Vì bạn KHÔNG gọi joinGroup()!");
                    System.out.println("[Receiver] => CHỨNG MINH HOÀN TẤT:");
                    System.out.println("[Receiver] • Broadcast: Tất cả máy cùng port đều nhận.");
                    System.out.println("[Receiver] • Multicast: Chỉ máy nào gọi joinGroup() mới nhận!");
                }
            }

            socket.close();
            System.out.println("[Receiver] Đã đóng socket an toàn.");
        } catch (Exception e) {
            System.err.println("❌ Lỗi: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
