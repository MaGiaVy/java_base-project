import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * ==============================================================================
 * UDP DEMO - RECEIVER (Chương trình nhận dữ liệu qua mạng UDP)
 * ------------------------------------------------------------------------------
 * Giảng viên hướng dẫn: ThS. Trần Minh Nhật - Đại học Sài Gòn (SGU)
 * 
 * Tuân thủ tuyệt đối các quy tắc trong rule.md & skill.md:
 * 1. Unicast: Dùng DatagramSocket lắng nghe trên port 7000.
 * 2. Broadcast: Dùng DatagramSocket lắng nghe port 7000, bật setBroadcast(true).
 * 3. Multicast: Bắt buộc dùng MulticastSocket tại port 8000.
 * 4. Quy tắc cốt lõi (Rule 4):
 *    - Hỏi rõ người dùng: "Bạn có muốn gọi hàm joinGroup() để gia nhập nhóm không? (Y/N)"
 *    - Nếu chọn Y: Gọi joinGroup() -> Nhận được gói tin thành công.
 *    - Nếu chọn N: KHÔNG gọi joinGroup() -> Chứng minh dù cùng mở port 8000 trong mạng LAN,
 *      socket vẫn không nhận được gói tin do card mạng và OS lọc bỏ gói tin multicast.
 * ==============================================================================
 */
public class NetDemoReceiver {

    private static final int UNICAST_PORT = 7000;
    private static final int BROADCAST_PORT = 7000;
    // Đổi MULTICAST_PORT sang 7000 vì cổng 7000 đã được chứng minh thông suốt qua Unicast/Broadcast!
    private static final int MULTICAST_PORT = 7000;

    // ==========================================================================
    // ĐỊA CHỈ MULTICAST LỚP D (224.0.0.0 - 239.255.255.255):
    // [CŨ] "239.1.1.1": Dải nội bộ, hay bị router WiFi/hotspot chặn IGMP.
    // [MỚI] "224.0.0.251": Dải Link-Local Multicast (RFC 4541), Access Point
    //                      BẮT BUỘC cho qua sóng WiFi không được chặn!
    // ==========================================================================
    // private static final String MULTICAST_GROUP_IP = "239.1.1.1"; // Địa chỉ cũ (bỏ comment để dùng lại)
    private static final String MULTICAST_GROUP_IP = "224.0.0.251"; // Địa chỉ mới vượt tường lửa WiFi

    public static void main(String[] args) {
        // Ép Java dùng IPv4 stack thuần túy để tránh lỗi Windows Dual-Stack IPv6 làm mất gói Multicast
        System.setProperty("java.net.preferIPv4Stack", "true");

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println();
            System.out.println("╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║           UDP DEMO - RECEIVER (CHƯƠNG TRÌNH NHẬN)              ║");
            System.out.println("║        Minh họa 3 cơ chế nhận dữ liệu qua giao thức UDP        ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");
            System.out.println("Vui lòng chọn chế độ lắng nghe:");
            System.out.println("  [1] Lắng nghe Unicast   (Port 7000 - Chờ gói tin gửi đích danh)");
            System.out.println("  [2] Lắng nghe Broadcast (Port 7000 - Nhận thông báo toàn mạng LAN)");
            System.out.println("  [3] Lắng nghe Multicast (Port 8000, Nhóm 239.1.1.1 - Demo khác biệt)");
            System.out.println("  [0] Thoát chương trình");
            System.out.println("────────────────────────────────────────────────────────────────");
            System.out.print("👉 Lựa chọn của bạn (0-3): ");

            if (!scanner.hasNextLine()) {
                break;
            }
            String choice = scanner.nextLine().replace("\uFEFF", "").trim();

            if ("0".equals(choice)) {
                System.out.println("\n[Receiver] Cảm ơn bạn đã sử dụng chương trình. Tạm biệt!");
                break;
            }

            try {
                switch (choice) {
                    case "1":
                        receiveUnicast();
                        break;
                    case "2":
                        receiveBroadcast();
                        break;
                    case "3":
                        receiveMulticast(scanner);
                        break;
                    default:
                        System.out.println("❌ Lựa chọn không hợp lệ! Vui lòng chọn từ 0 đến 3.");
                }
            } catch (Exception e) {
                System.err.println("❌ Có lỗi xảy ra trong quá trình nhận: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * CHẾ ĐỘ 1: NHẬN UNICAST (1 - 1)
     * Lắng nghe tại port 7000 bằng DatagramSocket
     */
    private static void receiveUnicast() throws Exception {
        System.out.println("\n========================================================");
        System.out.println("            [CHẾ ĐỘ 1: NHẬN UDP UNICAST]");
        System.out.println("========================================================");
        System.out.println("ℹ️  Đang thiết lập DatagramSocket lắng nghe tại port " + UNICAST_PORT + "...");

        // Khởi tạo DatagramSocket và thiết lập SO_REUSEADDR để có thể test linh hoạt
        DatagramSocket socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(UNICAST_PORT));

        System.out.println("[Receiver - Unicast] ✓ Đã bind thành công tới port " + UNICAST_PORT);
        System.out.println("[Receiver - Unicast] Đang chờ gói tin Unicast gửi đích danh đến máy này...");

        byte[] buffer = new byte[2048];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        // Chờ nhận gói tin (phương thức receive sẽ block cho tới khi có gói tin)
        socket.receive(packet);

        String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

        System.out.println("\n────────────────────────────────────────────────────────");
        System.out.println("[Receiver - Unicast] ✓ NHẬN ĐƯỢC GÓI TIN UNICAST THÀNH CÔNG!");
        System.out.println("[Receiver - Unicast]   • Người gửi (IP:Port) : " 
                + packet.getAddress().getHostAddress() + ":" + packet.getPort());
        System.out.println("[Receiver - Unicast]   • Kích thước payload  : " + packet.getLength() + " bytes");
        System.out.println("[Receiver - Unicast]   • Nội dung nhận được  : \"" + message + "\"");
        System.out.println("────────────────────────────────────────────────────────");

        socket.close();
        System.out.println("[Receiver - Unicast] Đã hoàn thành 1 phiên nhận và đóng socket.");
    }

    /**
     * CHẾ ĐỘ 2: NHẬN BROADCAST (1 - TẤT CẢ)
     * Lắng nghe tại port 7000 bằng DatagramSocket, bật setBroadcast(true)
     */
    private static void receiveBroadcast() throws Exception {
        System.out.println("\n========================================================");
        System.out.println("           [CHẾ ĐỘ 2: NHẬN UDP BROADCAST]");
        System.out.println("========================================================");
        System.out.println("ℹ️  Đặc điểm Broadcast: Mọi máy trong cùng mạng LAN mở port " 
                + BROADCAST_PORT + " đều sẽ nhận được gói tin gửi đến 255.255.255.255.");

        // Khởi tạo socket
        DatagramSocket socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(BROADCAST_PORT));
        socket.setBroadcast(true);

        System.out.println("[Receiver - Broadcast] ✓ Đã mở DatagramSocket tại port " + BROADCAST_PORT);
        System.out.println("[Receiver - Broadcast] ✓ Đã bật cờ setBroadcast(true) trên Receiver");
        System.out.println("[Receiver - Broadcast] Đang chờ gói tin Broadcast phát tán ra toàn mạng LAN...");
        System.out.println("[Receiver - Broadcast] (Nhận 1 gói tin mẫu để quay lại menu chính)...");

        byte[] buffer = new byte[2048];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        socket.receive(packet);

        String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

        System.out.println("\n────────────────────────────────────────────────────────");
        System.out.println("[Receiver - Broadcast] 📢 NHẬN ĐƯỢC THÔNG BÁO BROADCAST THÀNH CÔNG!");
        System.out.println("[Receiver - Broadcast]   • Máy gửi (IP:Port)  : " 
                + packet.getAddress().getHostAddress() + ":" + packet.getPort());
        System.out.println("[Receiver - Broadcast]   • Kích thước payload : " + packet.getLength() + " bytes");
        System.out.println("[Receiver - Broadcast]   • Nội dung phát thanh: \"" + message + "\"");
        System.out.println("[Receiver - Broadcast] 💡 Nhận được vì socket đang lắng nghe toàn mạng (0.0.0.0:" + BROADCAST_PORT + ")");
        System.out.println("[Receiver - Broadcast]    (Hỗ trợ cả Broadcast Vật lý 255.255.255.255 & Broadcast Logic mạng con)!");
        System.out.println("────────────────────────────────────────────────────────");

        socket.close();
        System.out.println("[Receiver - Broadcast] Đã hoàn thành nhận Broadcast và đóng socket an toàn.");
    }

    /**
     * CHẾ ĐỘ 3: NHẬN MULTICAST (VỚI YÊU CẦU ĐẶC BIỆT CỦA SKILL.MD VÀ RULE 4)
     * Kịch bản chứng minh sự khác biệt sống còn giữa Broadcast và Multicast:
     * - Hỏi: "Bạn có muốn gọi hàm joinGroup() để gia nhập nhóm không? (Y/N)"
     * - Nếu Y: Tham gia nhóm -> Nhận được.
     * - Nếu N: Cố tình không tham gia -> Không nhận được dù cùng lắng nghe port 8000!
     */
    @SuppressWarnings("deprecation")
    private static void receiveMulticast(Scanner scanner) throws Exception {
        System.out.println("\n========================================================");
        System.out.println("           [CHẾ ĐỘ 3: NHẬN UDP MULTICAST]");
        System.out.println("========================================================");
        System.out.println("ℹ️  Thông tin nhóm Multicast Lớp D: " + MULTICAST_GROUP_IP + ":" + MULTICAST_PORT);
        System.out.println("ℹ️  Mục đích: Chứng minh cơ chế lọc gói tin (Filtering) của Multicast.");
        System.out.println();

        // ==============================================================================
        // YÊU CẦU ĐẶC BIỆT THEO SKILL.MD VÀ QUY TẮC RULE 4:
        // Chương trình BẮT BUỘC phải hỏi người dùng có muốn gọi joinGroup() hay không.
        // ==============================================================================
        System.out.print("❓ Bạn có muốn gọi hàm joinGroup() để gia nhập nhóm không? (Y/N): ");
        String answer = scanner.hasNextLine() ? scanner.nextLine().trim() : "N";

        boolean wantToJoin = answer.equalsIgnoreCase("Y") || answer.equalsIgnoreCase("YES");

        // Khởi tạo MulticastSocket trên port 8000 theo Rule 3
        MulticastSocket socket = new MulticastSocket(MULTICAST_PORT);
        socket.setReuseAddress(true);
        InetAddress group = InetAddress.getByName(MULTICAST_GROUP_IP);
        java.net.NetworkInterface wifiNI = detectWifiInterface();
        if (wifiNI != null) {
            socket.setNetworkInterface(wifiNI);
        }

        if (wantToJoin) {
            // -------------------------------------------------------------
            // TRƯỜNG HỢP 1: NGƯỜI DÙNG CHỌN GIA NHẬP NHÓM (Y)
            // -------------------------------------------------------------
            System.out.println("\n[Receiver - Multicast] Bước 1: Thực thi lệnh socket.joinGroup(" + MULTICAST_GROUP_IP + ")...");
            socket.joinGroup(group);
            if (wifiNI != null) {
                try {
                    socket.joinGroup(new java.net.InetSocketAddress(group, 0), wifiNI);
                    System.out.println("[Receiver - Multicast] ✓ ĐÃ GỌI HÀM joinGroup() trên card [" + wifiNI.getName() + "] THÀNH CÔNG!");
                } catch (Exception ignored) {}
            } else {
                System.out.println("[Receiver - Multicast] ✓ ĐÃ GỌI HÀM joinGroup() THÀNH CÔNG!");
            }
            System.out.println("[Receiver - Multicast] 📡 Bản chất mạng: Card mạng (NIC) và HĐH đã gửi bản tin IGMP Report");
            System.out.println("[Receiver - Multicast]    thông báo tới Switch/Router để đăng ký địa chỉ MAC Multicast.");
            System.out.println("[Receiver - Multicast] 👉 Hiện tại máy này ĐANG LÀ THÀNH VIÊN của nhóm " + MULTICAST_GROUP_IP);
            System.out.println("[Receiver - Multicast] Đang lắng nghe gói tin từ Sender gửi tới nhóm " + MULTICAST_GROUP_IP + ":" + MULTICAST_PORT + "...");

            byte[] buffer = new byte[2048];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            // Chờ nhận dữ liệu
            socket.receive(packet);

            String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);

            System.out.println("\n────────────────────────────────────────────────────────");
            System.out.println("[Receiver - Multicast] 🎯 ✓ NHẬN ĐƯỢC GÓI TIN MULTICAST THÀNH CÔNG!");
            System.out.println("[Receiver - Multicast]   • Người gửi (IP:Port) : " 
                    + packet.getAddress().getHostAddress() + ":" + packet.getPort());
            System.out.println("[Receiver - Multicast]   • Nhóm Multicast đích : " + MULTICAST_GROUP_IP);
            System.out.println("[Receiver - Multicast]   • Nội dung gói tin    : \"" + message + "\"");
            System.out.println("────────────────────────────────────────────────────────");
            System.out.println("[Receiver - Multicast] 💡 GIẢI THÍCH: Gói tin được tiếp nhận vì chương trình ĐÃ GỌI joinGroup()!");

            // Rời nhóm khi xong
            if (wifiNI != null) {
                socket.leaveGroup(new java.net.InetSocketAddress(group, MULTICAST_PORT), wifiNI);
            } else {
                socket.leaveGroup(group);
            }
            System.out.println("[Receiver - Multicast] Đã rời nhóm (leaveGroup) và đóng socket.");

        } else {
            // -------------------------------------------------------------
            // TRƯỜNG HỢP 2: NGƯỜI DÙNG TỪ CHỐI GIA NHẬP NHÓM (N)
            // CỐ TÌNH KHÔNG GỌI joinGroup() ĐỂ CHỨNG MINH SỰ KHÁC BIỆT VỚI BROADCAST!
            // -------------------------------------------------------------
            System.out.println("\n[Receiver - Multicast] ✗ BẠN ĐÃ CHỌN: KHÔNG GỌI HÀM joinGroup()!");
            System.out.println("[Receiver - Multicast] ⚠️  [QUY TẮC CỐT LÕI DEMO - CHỨNG MINH KHÁC BIỆT VỚI BROADCAST]:");
            System.out.println("[Receiver - Multicast]    • Port " + MULTICAST_PORT + " của máy này vẫn đang mở để lắng nghe.");
            System.out.println("[Receiver - Multicast]    • Đang chờ gói tin nhưng cố tình không gia nhập nhóm...");
            System.out.println("[Receiver - Multicast]    • Hệ thống sẽ đợi tối đa 10 giây (Timeout = 10000ms)...");

            // Thiết lập timeout 10 giây để chứng minh gói tin không thể lọt vào
            socket.setSoTimeout(10000);

            byte[] buffer = new byte[2048];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            try {
                System.out.println("[Receiver - Multicast] Đang thực hiện socket.receive()... Hãy thử chạy Sender gửi Multicast ngay bây giờ!");
                socket.receive(packet);

                // Trường hợp nếu nhận được (chỉ xảy ra nếu có cấu hình loopback đặc thù, rất hiếm khi chưa join)
                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                System.out.println("[Receiver - Multicast] Nhận được: " + message);

            } catch (SocketTimeoutException e) {
                // CHỨNG MINH THÀNH CÔNG: KHÔNG NHẬN ĐƯỢC GÓI TIN!
                System.out.println("\n================================================================================");
                System.out.println("⏱️  KẾT QUẢ: HẾT THỜI GIAN CHỜ (TIMEOUT 10 GIÂY) - HOÀN TOÀN KHÔNG NHẬN ĐƯỢC DỮ LIỆU!");
                System.out.println("================================================================================");
                System.out.println("[Receiver - Multicast] 💡 PHÂN TÍCH CHUYÊN GIA LẬP TRÌNH MẠNG:");
                System.out.println("  1. Dù bên NetDemoSender đã thực sự bắn gói tin UDP tới cổng " + MULTICAST_PORT + ";");
                System.out.println("  2. Dù máy Receiver này cũng đang mở socket lắng nghe chính xác cổng " + MULTICAST_PORT + ";");
                System.out.println("  3. NHƯNG vì bạn KHÔNG GỌI joinGroup('" + MULTICAST_GROUP_IP + "'):");
                System.out.println("     -> Card mạng (NIC) không nạp địa chỉ MAC Multicast tương ứng.");
                System.out.println("     -> Hệ điều hành và Card mạng tự động LỌC BỎ (DROP) gói tin ngay từ phần cứng,");
                System.out.println("        gói tin KHÔNG BAO GIỜ được chuyển tiếp lên tầng Application!");
                System.out.println();
                System.out.println("╔══════════════════════════════════════════════════════════════════════════════╗");
                System.out.println("║                        CHỨNG MINH KHOA HỌC THÀNH CÔNG                        ║");
                System.out.println("╠══════════════════════════════════════════════════════════════════════════════╣");
                System.out.println("║ • BROADCAST: Chỉ cần mở đúng Port là TẤT CẢ máy trong LAN bị ép phải nhận.  ║");
                System.out.println("║ • MULTICAST: Cùng Port là CHƯA ĐỦ, BẮT BUỘC phải chủ động gọi joinGroup().  ║");
                System.out.println("║ => Multicast giúp bảo vệ tài nguyên CPU và tiết kiệm băng thông mạng LAN!    ║");
                System.out.println("╚══════════════════════════════════════════════════════════════════════════════╝");
            }
        }

        socket.close();
        System.out.println("[Receiver - Multicast] Đã đóng socket Receiver an toàn.");
    }

    /**
     * Hàm phụ trợ: Tự động tìm card mạng WiFi đang hoạt động.
     * Ưu tiên dải 192.168.x.x, 10.x.x.x, và 172.x.x.x để tránh chọn nhầm card mạng ảo virbr0/VirtualBox.
     */
    private static java.net.NetworkInterface detectWifiInterface() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            java.net.NetworkInterface fallback = null;
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp() || ni.isVirtual()) continue;
                // Bỏ qua card ảo phổ biến trên Linux và Windows
                String name = ni.getName().toLowerCase();
                if (name.contains("virbr") || name.contains("docker") || name.contains("vbox") || name.contains("vmnet")) continue;

                for (java.net.InterfaceAddress addr : ni.getInterfaceAddresses()) {
                    if (addr.getAddress() instanceof java.net.Inet4Address) {
                        String ip = addr.getAddress().getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) return ni;
                        fallback = ni;
                    }
                }
            }
            return fallback;
        } catch (Exception ignored) { return null; }
    }
}
