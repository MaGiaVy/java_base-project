package com.zalolite.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

/**
 * [CỔNG/GIAO THỨC] Bộ phản hồi tự động tìm kiếm máy chủ trong mạng LAN qua UDP Multicast / Broadcast.
 * - Mở MulticastSocket trên cổng UDP 9009 (mặc định) và tham gia nhóm Multicast 239.1.1.90.
 * - Khi nhận gói dò tìm "ZALOLITE_DISCOVER" từ Client, trả lời trực tiếp bằng UDP unicast
 *   chuỗi "ZALOLITE_SERVER:<tcpPort>:<filePort>" để Client tự biết IP và cổng Server mà không cần nhập tay.
 */
public class DiscoveryResponder {

    /** Địa chỉ nhóm UDP Multicast dùng cho LAN discovery. */
    public static final String MULTICAST_GROUP = "239.1.1.90";
    /** Cổng UDP mặc định lắng nghe gói dò tìm từ Client. */
    public static final int DEFAULT_DISCOVERY_PORT = 9009;
    /** Chuỗi định danh gói dò tìm Client gửi đi. */
    public static final String PROBE = "ZALOLITE_DISCOVER";
    /** Tiền tố gói phản hồi Server gửi lại (kèm tcpPort:filePort). */
    public static final String REPLY_PREFIX = "ZALOLITE_SERVER:";

    private final int discoveryPort;
    private final int tcpPort;
    private final int filePort;

    private volatile boolean running;
    private MulticastSocket socket;
    private Thread thread;

    /** Khởi tạo DiscoveryResponder với cổng UDP mặc định 9009. */
    public DiscoveryResponder(int tcpPort, int filePort) {
        this(DEFAULT_DISCOVERY_PORT, tcpPort, filePort);
    }

    /** Khởi tạo DiscoveryResponder với cổng UDP discovery, cổng TCP chat và cổng FileServer tùy chỉnh. */
    public DiscoveryResponder(int discoveryPort, int tcpPort, int filePort) {
        this.discoveryPort = discoveryPort;
        this.tcpPort = tcpPort;
        this.filePort = filePort;
    }

    /** [CỔNG/GIAO THỨC] Mở MulticastSocket trên cổng UDP discoveryPort, tham gia nhóm multicast và chạy luồng lắng nghe nền. */
    public synchronized void startAsync() throws IOException {
        if (running) {
            return;
        }
        // [CỔNG/GIAO THỨC] Bind UDP MulticastSocket trên cổng discoveryPort (mặc định 9009)
        MulticastSocket ms = new MulticastSocket(discoveryPort);
        joinMulticastSilently(ms);
        this.socket = ms;
        this.running = true;
        this.thread = new Thread(this::listenLoop, "discovery-responder");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    /** [CỔNG/GIAO THỨC] [XỬ LÝ] Tham gia nhóm multicast 239.1.1.90 trên mọi card mạng đang bật và hỗ trợ multicast. */
    private void joinMulticastSilently(MulticastSocket ms) {
        try {
            InetSocketAddress group = new InetSocketAddress(InetAddress.getByName(MULTICAST_GROUP), ms.getLocalPort());
            boolean joinedAny = false;
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces != null && ifaces.hasMoreElements()) {
                NetworkInterface nif = ifaces.nextElement();
                try {
                    if (nif.isUp() && !nif.isLoopback() && nif.supportsMulticast()) {
                        ms.joinGroup(group, nif);
                        joinedAny = true;
                    }
                } catch (IOException ignored) {
                    // Ignore interfaces that reject multicast join.
                }
            }
            if (!joinedAny) {
                ms.joinGroup(group, null);
            }
        } catch (IOException ignored) {
            // Still responds to unicast/broadcast UDP on the bound port.
        }
    }

    /** [NHẬN] [GỬI] Vòng lặp lắng nghe gói UDP dò tìm và gửi phản hồi chứa cổng TCP/File về đúng IP:port của Client. */
    private void listenLoop() {
        byte[] buf = new byte[256];
        byte[] replyBytes = (REPLY_PREFIX + tcpPort + ":" + filePort).getBytes(StandardCharsets.UTF_8);
        while (running) {
            try {
                // [NHẬN] Chờ nhận DatagramPacket UDP từ Client (multicast, broadcast hoặc unicast)
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                // [XỬ LÝ] Giải mã nội dung UTF-8 và kiểm tra có đúng lệnh dò tìm ZALOLITE_DISCOVER không
                String payload = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
                if (PROBE.equals(payload)) {
                    // [GỬI] Gửi gói UDP unicast trả lời "ZALOLITE_SERVER:<tcpPort>:<filePort>" về đúng địa chỉ người hỏi
                    DatagramPacket reply = new DatagramPacket(replyBytes, replyBytes.length, packet.getAddress(), packet.getPort());
                    socket.send(reply);
                }
            } catch (SocketException e) {
                if (!running) {
                    break;
                }
            } catch (IOException ignored) {
                // Keep listening on transient I/O errors.
            }
        }
    }

    /** Trả về cổng UDP thực tế socket đang lắng nghe. */
    public int getLocalPort() {
        MulticastSocket s = this.socket;
        return s != null ? s.getLocalPort() : discoveryPort;
    }

    /** Dừng dịch vụ discovery: đóng MulticastSocket và ngắt luồng lắng nghe. */
    public synchronized void stop() {
        running = false;
        if (socket != null) {
            socket.close();
            socket = null;
        }
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }
}
