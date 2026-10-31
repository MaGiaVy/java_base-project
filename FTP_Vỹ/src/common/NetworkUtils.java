package common;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiện ích mạng và giải mã phản hồi PASV/PORT
 * Bám sát Slide 8, 20, 21
 */
public class NetworkUtils {

    private static final Pattern PASV_PATTERN = Pattern.compile("\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\)");

    public static class Endpoint {
        public final String ip;
        public final int port;

        public Endpoint(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }
    }

    /**
     * Phân tích phản hồi PASV: 227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)
     * Port = p1 * 256 + p2 (Slide 20, 21)
     */
    public static Endpoint parsePasvReply(String pasvReply) {
        if (pasvReply == null) return null;
        Matcher m = PASV_PATTERN.matcher(pasvReply);
        if (m.find()) {
            String ip = m.group(1) + "." + m.group(2) + "." + m.group(3) + "." + m.group(4);
            int p1 = Integer.parseInt(m.group(5));
            int p2 = Integer.parseInt(m.group(6));
            int port = p1 * 256 + p2;
            return new Endpoint(ip, port);
        }
        return null;
    }

    /**
     * Định dạng chuỗi phản hồi 227 cho lệnh PASV phía Server
     */
    public static String formatPasvReply(String ip, int port) {
        String formattedIp = ip.replace('.', ',');
        int p1 = (port >> 8) & 0xFF;
        int p2 = port & 0xFF;
        return String.format("227 Entering Passive Mode (%s,%d,%d)", formattedIp, p1, p2);
    }

    /**
     * Phân tích tham số của lệnh PORT h1,h2,h3,h4,p1,p2
     */
    public static Endpoint parsePortCommand(String param) {
        if (param == null) return null;
        String[] parts = param.trim().split(",");
        if (parts.length >= 6) {
            try {
                String ip = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
                int p1 = Integer.parseInt(parts[4].trim());
                int p2 = Integer.parseInt(parts[5].trim());
                int port = p1 * 256 + p2;
                return new Endpoint(ip, port);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    /**
     * Lấy địa chỉ IP mạng nội bộ của máy tính đang chạy (hoặc 127.0.0.1)
     */
    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
