import java.net.*;
import java.util.*;

/**
 * NetworkHelper.java
 * Tiện ích dò tìm và ưu tiên card mạng Mobile Hotspot (192.168.137.x hoặc Wi-Fi Direct / Local Area Connection*).
 * Kế thừa trực tiếp từ thư mục BroadcastMulticast.
 */
public class NetworkHelper {

    public static class CardInfo {
        public NetworkInterface nif;
        public String ip;
        public String broadcastIp;
        public boolean isHotspot;
        public String displayName;
        public String name;

        public CardInfo(NetworkInterface nif, String ip, String broadcastIp, boolean isHotspot) {
            this.nif = nif;
            this.ip = ip;
            this.broadcastIp = broadcastIp;
            this.isHotspot = isHotspot;
            this.name = nif.getName();
            this.displayName = nif.getDisplayName();
        }

        @Override
        public String toString() {
            String prefix = isHotspot ? "🔥 [HOTSPOT] " : "📶 ";
            return prefix + ip + "  (" + displayName + ")";
        }
    }

    private static final String[] VIRTUAL_BAD = {
            "vmware", "virtualbox", "vethernet", "hyper-v", "docker", "wsl",
            "tap", "tun", "vpn", "bluetooth", "loopback"
    };

    /**
     * Lấy danh sách tất cả card mạng IPv4 hợp lệ, sắp xếp ưu tiên Hotspot lên đầu tiên.
     */
    public static List<CardInfo> getAvailableCards() {
        List<CardInfo> list = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en != null && en.hasMoreElements()) {
                NetworkInterface ni = en.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;

                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress addr = ia.getAddress();
                    if (!(addr instanceof Inet4Address)) continue;
                    String ip = addr.getHostAddress();
                    if (ip.startsWith("127.") || ip.startsWith("169.254.")) continue;

                    InetAddress bcast = ia.getBroadcast();
                    String bcastStr = bcast != null ? bcast.getHostAddress() : "255.255.255.255";

                    String dName = (ni.getDisplayName() + " " + ni.getName()).toLowerCase();
                    boolean isBadVirtual = false;
                    for (String b : VIRTUAL_BAD) {
                        if (dName.contains(b)) {
                            isBadVirtual = true;
                            break;
                        }
                    }

                    // Nhận diện Hotspot:
                    // 1. IP mạng hotspot Windows mặc định: 192.168.137.x
                    // 2. Tên chứa "wi-fi direct", "hotspot", hoặc "local area connection*" (nhưng không phải vmware)
                    boolean isHotspot = ip.startsWith("192.168.137.")
                            || dName.contains("wi-fi direct")
                            || dName.contains("hotspot")
                            || (dName.contains("local area connection*") && !isBadVirtual);

                    list.add(new CardInfo(ni, ip, bcastStr, isHotspot));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Sắp xếp: Hotspot lên đầu -> Wi-Fi thật -> các card khác -> card ảo VMware cuối
        list.sort((a, b) -> {
            if (a.isHotspot != b.isHotspot) return a.isHotspot ? -1 : 1;
            boolean aBad = isVirtual(a);
            boolean bBad = isVirtual(b);
            if (aBad != bBad) return aBad ? 1 : -1;
            return a.ip.compareTo(b.ip);
        });

        return list;
    }

    private static boolean isVirtual(CardInfo c) {
        String s = (c.displayName + " " + c.name).toLowerCase();
        for (String b : VIRTUAL_BAD) {
            if (s.contains(b)) return true;
        }
        return false;
    }

    /**
     * Tìm card theo IP hoặc tên hoặc tự chọn ưu tiên Hotspot.
     */
    public static CardInfo pickCard(String wanted) throws SocketException {
        List<CardInfo> cards = getAvailableCards();
        if (cards.isEmpty()) {
            throw new SocketException("Không tìm thấy card mạng IPv4 nào đang hoạt động!");
        }

        if (wanted != null && !wanted.trim().isEmpty()) {
            String w = wanted.trim();
            for (CardInfo c : cards) {
                if (c.ip.equalsIgnoreCase(w)) return c;
            }
            for (CardInfo c : cards) {
                if (c.name.equalsIgnoreCase(w)
                        || c.displayName.equalsIgnoreCase(w)
                        || c.displayName.toLowerCase().contains(w.toLowerCase())) {
                    return c;
                }
            }
            try {
                NetworkInterface direct = NetworkInterface.getByInetAddress(InetAddress.getByName(w));
                if (direct != null) {
                    return new CardInfo(direct, w, "255.255.255.255", w.startsWith("192.168.137."));
                }
            } catch (Exception ignored) {}
        }

        return cards.get(0);
    }
}
