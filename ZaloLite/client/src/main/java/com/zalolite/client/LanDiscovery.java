package com.zalolite.client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [CỔNG/GIAO THỨC] Tự động dò tìm ZaloLite Server trong mạng LAN (và localhost) bằng UDP.
 * Gửi gói tin thăm dò ("ZALOLITE_DISCOVER") tới cổng UDP 9009 qua cả 3 đích:
 *  - Multicast group 239.1.1.90
 *  - Broadcast 255.255.255.255
 *  - Localhost 127.0.0.1
 * Server lắng nghe sẽ phản hồi chuỗi "ZALOLITE_SERVER:<tcpPort>:<filePort>".
 */
public final class LanDiscovery {

    /** Nhóm UDP Multicast dùng để quảng bá tìm server trong LAN. */
    public static final String MULTICAST_GROUP = "239.1.1.90";
    /** Cổng UDP mặc định của dịch vụ LAN Discovery trên server. */
    public static final int DEFAULT_DISCOVERY_PORT = 9009;
    /** Chuỗi gói tin thăm dò (probe) client phát đi. */
    public static final String PROBE = "ZALOLITE_DISCOVER";
    /** Tiền tố chuỗi phản hồi hợp lệ từ server. */
    public static final String REPLY_PREFIX = "ZALOLITE_SERVER:";

    public record DiscoveredServer(String host, int tcpPort, int filePort) {
        @Override
        public String toString() {
            return host + ":" + tcpPort;
        }
    }

    private LanDiscovery() {}

    /** Dò tìm server trên cổng UDP mặc định 9009 trong khoảng {@code timeoutMs}. */
    public static List<DiscoveredServer> discover(int timeoutMs) {
        return discover(DEFAULT_DISCOVERY_PORT, timeoutMs);
    }

    /**
     * [CỔNG/GIAO THỨC] Mở DatagramSocket cổng tạm (ephemeral port), phát gói UDP thăm dò
     * và thu thập toàn bộ phản hồi từ các server trong mạng cho tới khi hết timeoutMs.
     */
    public static List<DiscoveredServer> discover(int discoveryPort, int timeoutMs) {
        Map<String, DiscoveredServer> found = new LinkedHashMap<>();
        byte[] probe = PROBE.getBytes(StandardCharsets.UTF_8);

        // [CỔNG/GIAO THỨC] Tạo DatagramSocket trên cổng UDP ngẫu nhiên, bật cờ broadcast
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            // [GỬI] Phát gói UDP probe tới Multicast, Broadcast LAN và Loopback localhost
            sendProbe(socket, probe, MULTICAST_GROUP, discoveryPort);
            sendProbe(socket, probe, "255.255.255.255", discoveryPort);
            sendProbe(socket, probe, "127.0.0.1", discoveryPort);

            long deadline = System.currentTimeMillis() + Math.max(1, timeoutMs);
            byte[] buf = new byte[256];
            while (true) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    break;
                }
                socket.setSoTimeout((int) remaining);
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    // [NHẬN] Chờ nhận gói UDP phản hồi từ server trả về
                    socket.receive(packet);
                } catch (SocketTimeoutException e) {
                    break;
                }
                String reply = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
                DiscoveredServer server = parseReply(packet.getAddress().getHostAddress(), reply);
                if (server != null) {
                    found.putIfAbsent(server.host() + ":" + server.tcpPort(), server);
                }
            }
        } catch (IOException ignored) {
            // Trả về danh sách server đã tìm được trước khi xảy ra lỗi socket.
        }
        return new ArrayList<>(found.values());
    }

    /** [GỬI] Gửi một DatagramPacket UDP chứa chuỗi probe tới địa chỉ host:port. */
    private static void sendProbe(DatagramSocket socket, byte[] probe, String host, int port) {
        try {
            socket.send(new DatagramPacket(probe, probe.length, InetAddress.getByName(host), port));
        } catch (IOException ignored) {
            // Một số card mạng có thể không hỗ trợ broadcast/multicast, bỏ qua đích đó.
        }
    }

    /** Phân tích gói phản hồi "ZALOLITE_SERVER:<tcpPort>:<filePort>" thành đối tượng DiscoveredServer. */
    private static DiscoveredServer parseReply(String host, String reply) {
        if (!reply.startsWith(REPLY_PREFIX)) {
            return null;
        }
        String[] parts = reply.substring(REPLY_PREFIX.length()).split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            int tcpPort = Integer.parseInt(parts[0].trim());
            int filePort = Integer.parseInt(parts[1].trim());
            if (tcpPort <= 0 || filePort <= 0) {
                return null;
            }
            return new DiscoveredServer(host, tcpPort, filePort);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
