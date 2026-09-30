import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * ==============================================================================
 * UDP DEMO - SENDER (Chương trình gửi dữ liệu qua mạng UDP)
 * ------------------------------------------------------------------------------
 * Giảng viên hướng dẫn: ThS. Trần Minh Nhật - Đại học Sài Gòn (SGU)
 * 
 * Tuân thủ tuyệt đối các quy tắc trong rule.md & skill.md:
 * 1. Unicast: Dùng DatagramSocket, yêu cầu nhập/chỉ định IP máy nhận cụ thể.
 * 2. Broadcast: Dùng DatagramSocket, bắt buộc gọi setBroadcast(true) trước khi gửi,
 *               gửi đến 255.255.255.255.
 * 3. Multicast: Bắt buộc dùng MulticastSocket, gửi đến địa chỉ IP Lớp D (239.1.1.1).
 * ==============================================================================
 */
public class NetDemoSender {

    private static final int UNICAST_PORT = 7000;
    private static final int BROADCAST_PORT = 7000;
    private static final int MULTICAST_PORT = 8000;
    private static final String MULTICAST_GROUP_IP = "239.1.1.1";
    private static final String BROADCAST_IP = "255.255.255.255";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println();
            System.out.println("╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║            UDP DEMO - SENDER (CHƯƠNG TRÌNH GỬI)                ║");
            System.out.println("║        Minh họa 3 cơ chế truyền dữ liệu qua giao thức UDP      ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");
            System.out.println("Vui lòng chọn chế độ gửi:");
            System.out.println("  [1] Gửi Unicast   (Truyền 1 - 1: Gửi đến 1 máy cụ thể trong mạng)");
            System.out.println("  [2] Gửi Broadcast (Truyền 1 - Tất cả: Gửi đến toàn bộ máy trong mạng LAN)");
            System.out.println("  [3] Gửi Multicast (Truyền 1 - Nhóm: Gửi đến nhóm IP Lớp D 239.1.1.1)");
            System.out.println("  [0] Thoát chương trình");
            System.out.println("────────────────────────────────────────────────────────────────");
            System.out.print("👉 Lựa chọn của bạn (0-3): ");

            if (!scanner.hasNextLine()) {
                break;
            }
            String choice = scanner.nextLine().replace("\uFEFF", "").trim();

            if ("0".equals(choice)) {
                System.out.println("\n[Sender] Cảm ơn bạn đã sử dụng chương trình. Tạm biệt!");
                break;
            }

            try {
                switch (choice) {
                    case "1":
                        sendUnicast(scanner);
                        break;
                    case "2":
                        sendBroadcast(scanner);
                        break;
                    case "3":
                        sendMulticast(scanner);
                        break;
                    default:
                        System.out.println("❌ Lựa chọn không hợp lệ! Vui lòng chọn từ 0 đến 3.");
                }
            } catch (Exception e) {
                System.err.println("❌ Có lỗi xảy ra trong quá trình gửi: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * CHẾ ĐỘ 1: GỬI UNICAST (1 - 1)
     * Quy tắc Rule 1: Dùng DatagramSocket, chỉ định rõ IP máy nhận cụ thể.
     */
    private static void sendUnicast(Scanner scanner) throws Exception {
        System.out.println("\n========================================================");
        System.out.println("             [CHẾ ĐỘ 1: GỬI UDP UNICAST]");
        System.out.println("========================================================");
        System.out.println("ℹ️  Đặc điểm: Gói tin chỉ đi từ 1 máy gửi đến DUY NHẤT 1 máy nhận.");

        // 1. Nhập IP máy nhận cụ thể (theo Rule 1)
        System.out.print("Nhập IP máy nhận (Nhấn Enter để dùng mặc định 127.0.0.1): ");
        String receiverIp = scanner.hasNextLine() ? scanner.nextLine().trim() : "127.0.0.1";
        if (receiverIp.isEmpty()) {
            receiverIp = "127.0.0.1";
        }

        // 2. Nhập nội dung thông điệp
        System.out.print("Nhập thông điệp cần gửi: ");
        String message = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
        if (message.isEmpty()) {
            message = "Xin chào, đây là thông điệp Unicast gửi đích danh!";
        }

        System.out.println("\n--- BẮT ĐẦU QUÁ TRÌNH GỬI UNICAST ---");

        // Bước 1: Khởi tạo DatagramSocket
        System.out.println("[Sender - Unicast] Bước 1: Khởi tạo DatagramSocket...");
        DatagramSocket socket = new DatagramSocket();
        System.out.println("[Sender - Unicast] ✓ DatagramSocket đã được tạo tại cổng ngẫu nhiên của HĐH.");

        // Bước 2: Chuyển chuỗi sang mảng byte
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        InetAddress recipientAddress = InetAddress.getByName(receiverIp);

        // Bước 3: Đóng gói thành DatagramPacket với IP và Port cụ thể
        System.out.println("[Sender - Unicast] Bước 2: Đóng gói DatagramPacket...");
        DatagramPacket packet = new DatagramPacket(data, data.length, recipientAddress, UNICAST_PORT);
        System.out.println("[Sender - Unicast]   • Đích đến (IP:Port): " + receiverIp + ":" + UNICAST_PORT);
        System.out.println("[Sender - Unicast]   • Kích thước payload : " + data.length + " bytes");
        System.out.println("[Sender - Unicast]   • Nội dung thông điệp: \"" + message + "\"");

        // Bước 4: Gửi gói tin qua mạng
        System.out.println("[Sender - Unicast] Bước 3: Đang gửi gói tin UDP qua card mạng...");
        socket.send(packet);
        System.out.println("[Sender - Unicast] ✓ ĐÃ GỬI UNICAST THÀNH CÔNG!");
        System.out.println("[Sender - Unicast] Lưu ý: Chỉ duy nhất máy có IP " + receiverIp + " mới nhận được gói tin này.");

        // Bước 5: Đóng socket giải phóng tài nguyên
        socket.close();
        System.out.println("[Sender - Unicast] Bước 4: Đã đóng socket an toàn.");
    }

    /**
     * CHẾ ĐỘ 2: GỬI BROADCAST (1 - TẤT CẢ)
     * Quy tắc Rule 2: Bắt buộc dùng DatagramSocket, phải gọi setBroadcast(true)
     * trước khi gửi.
     * Hỗ trợ đầy đủ cả 2 loại theo bài giảng chuyên sâu:
     *   - Loại A: Broadcast Vật lý (Limited Broadcast: 255.255.255.255)
     *   - Loại B: Broadcast Logic  (Directed Subnet Broadcast: địa chỉ mạng con)
     */
    private static void sendBroadcast(Scanner scanner) throws Exception {
        System.out.println("\n========================================================");
        System.out.println("            [CHẾ ĐỘ 2: GỬI UDP BROADCAST]");
        System.out.println("========================================================");
        System.out.println("Vui lòng chọn loại Broadcast bạn muốn demo:");
        System.out.println("  [1] Broadcast VẬT LÝ (Physical / Limited Broadcast: 255.255.255.255)");
        System.out.println("      -> Gói tin bị chặn tuyệt đối ở Router, chỉ tồn tại trong dây cáp/Wi-Fi vật lý.");
        System.out.println("  [2] Broadcast LOGIC  (Logical / Directed Broadcast: Địa chỉ mạng con)");
        System.out.println("      -> Gói tin có thể được Router định tuyến từ mạng khác tới mạng con đích.");
        System.out.print("👉 Lựa chọn loại Broadcast (1-2, mặc định 1): ");

        String bType = scanner.hasNextLine() ? scanner.nextLine().replace("\uFEFF", "").trim() : "1";
        if (bType.isEmpty()) {
            bType = "1";
        }

        String targetBroadcastIp = BROADCAST_IP; // Mặc định là 255.255.255.255
        String broadcastTypeName = "VẬT LÝ (Limited Broadcast)";

        if ("2".equals(bType)) {
            broadcastTypeName = "LOGIC (Directed Subnet Broadcast)";
            // Tự động tìm địa chỉ Subnet Broadcast của máy hiện tại
            String detectedSubnetBroadcast = detectSubnetBroadcast();
            System.out.println("🔍 Phát hiện địa chỉ Broadcast Logic trên máy bạn: " + detectedSubnetBroadcast);
            System.out.print("Nhập địa chỉ Subnet Broadcast (Nhấn Enter để dùng " + detectedSubnetBroadcast + "): ");
            String inputIp = scanner.hasNextLine() ? scanner.nextLine().replace("\uFEFF", "").trim() : "";
            targetBroadcastIp = inputIp.isEmpty() ? detectedSubnetBroadcast : inputIp;
        }

        // Nhập nội dung thông điệp
        System.out.print("Nhập thông điệp Broadcast cần phát sóng: ");
        String message = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
        if (message.isEmpty()) {
            message = "THÔNG BÁO KHẨN (" + broadcastTypeName + "): Kiểm tra toàn bộ mạng!";
        }

        System.out.println("\n--- BẮT ĐẦU QUÁ TRÌNH GỬI " + broadcastTypeName.toUpperCase() + " ---");

        // Bước 1: Khởi tạo DatagramSocket
        System.out.println("[Sender - Broadcast] Bước 1: Khởi tạo DatagramSocket...");
        DatagramSocket socket = new DatagramSocket();

        // Bước 2: BẮT BUỘC gọi setBroadcast(true) theo Rule 2
        System.out.println("[Sender - Broadcast] Bước 2: Kích hoạt cờ cho phép phát sóng Broadcast...");
        socket.setBroadcast(true);
        System.out.println("[Sender - Broadcast] ✓ ĐÃ BẬT CỜ socket.setBroadcast(true) (Quy tắc cốt lõi Rule 2)!");

        // Bước 3: Chuẩn bị dữ liệu và địa chỉ broadcast đích
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        InetAddress broadcastAddress = InetAddress.getByName(targetBroadcastIp);

        // Bước 4: Đóng gói DatagramPacket phát sóng
        System.out.println("[Sender - Broadcast] Bước 3: Đóng gói DatagramPacket phát sóng...");
        DatagramPacket packet = new DatagramPacket(data, data.length, broadcastAddress, BROADCAST_PORT);
        System.out.println("[Sender - Broadcast]   • Phân loại Broadcast: " + broadcastTypeName);
        System.out.println("[Sender - Broadcast]   • Địa chỉ phát sóng  : " + targetBroadcastIp + ":" + BROADCAST_PORT);
        System.out.println("[Sender - Broadcast]   • Kích thước payload : " + data.length + " bytes");
        System.out.println("[Sender - Broadcast]   • Nội dung thông điệp: \"" + message + "\"");

        // Bước 5: Gửi gói tin Broadcast
        System.out.println("[Sender - Broadcast] Bước 4: Đang phát sóng DatagramPacket ra mạng...");
        socket.send(packet);
        System.out.println("[Sender - Broadcast] ✓ ĐÃ GỬI BROADCAST THÀNH CÔNG!");
        System.out.println("[Sender - Broadcast] 📢 Toàn bộ máy đang mở port " + BROADCAST_PORT + " sẽ nhận được gói tin này.");

        // Bước 6: Đóng socket
        socket.close();
        System.out.println("[Sender - Broadcast] Bước 5: Đã đóng socket an toàn.");
    }

    /**
     * Hàm phụ trợ: Tự động dò tìm địa chỉ Subnet Broadcast logic của máy hiện tại
     */
    private static String detectSubnetBroadcast() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (java.net.InterfaceAddress addr : ni.getInterfaceAddresses()) {
                    if (addr.getBroadcast() != null) {
                        return addr.getBroadcast().getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "192.168.1.255"; // Giá trị fallback phổ biến
    }

    /**
     * CHẾ ĐỘ 3: GỬI MULTICAST (1 - NHÓM ĐĂNG KÝ)
     * Quy tắc Rule 3: Bắt buộc dùng MulticastSocket và gửi tới địa chỉ IP Lớp D (239.1.1.1).
     */
    private static void sendMulticast(Scanner scanner) throws Exception {
        System.out.println("\n========================================================");
        System.out.println("            [CHẾ ĐỘ 3: GỬI UDP MULTICAST]");
        System.out.println("========================================================");
        System.out.println("ℹ️  Đặc điểm: Gói tin gửi tới một địa chỉ nhóm IP Lớp D (224.0.0.0 - 239.255.255.255).");
        System.out.println("ℹ️  Chỉ các máy tính đã GIA NHẬP NHÓM (joinGroup) mới nhận được dữ liệu!");

        // Nhập nội dung thông điệp
        System.out.print("Nhập thông điệp gửi tới nhóm Multicast (239.1.1.1): ");
        String message = scanner.hasNextLine() ? scanner.nextLine().trim() : "";
        if (message.isEmpty()) {
            message = "Thông điệp mật dành riêng cho thành viên nhóm Multicast 239.1.1.1!";
        }

        System.out.println("\n--- BẮT ĐẦU QUÁ TRÌNH GỬI MULTICAST ---");

        // Bước 1: Khởi tạo MulticastSocket (Tuân thủ nghiêm ngặt Rule 3)
        System.out.println("[Sender - Multicast] Bước 1: Khởi tạo MulticastSocket...");
        MulticastSocket socket = new MulticastSocket();
        System.out.println("[Sender - Multicast] ✓ Đã khởi tạo MulticastSocket (Tuân thủ Rule 3, không dùng DatagramSocket thuần).");

        // Bước 2: Chuẩn bị địa chỉ nhóm Lớp D
        byte[] data = message.getBytes(StandardCharsets.UTF_8);
        InetAddress groupAddress = InetAddress.getByName(MULTICAST_GROUP_IP);

        // Bước 3: Đóng gói DatagramPacket hướng tới địa chỉ nhóm Lớp D
        System.out.println("[Sender - Multicast] Bước 2: Đóng gói DatagramPacket tới nhóm Lớp D...");
        DatagramPacket packet = new DatagramPacket(data, data.length, groupAddress, MULTICAST_PORT);
        System.out.println("[Sender - Multicast]   • Địa chỉ nhóm Multicast (Lớp D): " + MULTICAST_GROUP_IP + ":" + MULTICAST_PORT);
        System.out.println("[Sender - Multicast]   • Kích thước payload           : " + data.length + " bytes");
        System.out.println("[Sender - Multicast]   • Nội dung thông điệp          : \"" + message + "\"");

        // Bước 4: Gửi gói tin Multicast
        System.out.println("[Sender - Multicast] Bước 3: Đang gửi gói tin Multicast tới nhóm...");
        socket.send(packet);
        System.out.println("[Sender - Multicast] ✓ ĐÃ GỬI MULTICAST THÀNH CÔNG!");
        System.out.println("[Sender - Multicast] 💡 BẢN CHẤT CỐT LÕI (RULE 4):");
        System.out.println("[Sender - Multicast]    Gói tin này CHỈ được chuyển giao cho ứng dụng ở các máy");
        System.out.println("[Sender - Multicast]    đã thực sự gọi hàm joinGroup(239.1.1.1). Máy nào dù mở port " 
                + MULTICAST_PORT + " nhưng KHÔNG joinGroup() thì sẽ HOÀN TOÀN KHÔNG NHẬN ĐƯỢC!");

        // Bước 5: Đóng socket
        socket.close();
        System.out.println("[Sender - Multicast] Bước 4: Đã đóng MulticastSocket an toàn.");
    }
}
