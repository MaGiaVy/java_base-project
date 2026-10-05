import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MulticastChat {
    static final String GROUP = "239.1.1.1";
    static final int PORT = 8000;

    public static void main(String[] args) throws Exception {
        String nick = args.length > 0 ? args[0] : "user" + new Random().nextInt(100);
        String wanted = args.length > 1 ? args[1] : null;   // tên card mạng, tuỳ chọn

        InetAddress group = InetAddress.getByName(GROUP);
        InetSocketAddress groupAddr = new InetSocketAddress(group, PORT);
        NetworkInterface nif = pickInterface(wanted);
        System.out.println("Dùng card: " + nif.getDisplayName());

        MulticastSocket socket = new MulticastSocket(PORT);
        socket.setNetworkInterface(nif);   // gửi ra đúng card WiFi
        socket.setTimeToLive(1);           // chỉ trong LAN
        socket.joinGroup(groupAddr, nif);  // vào phòng

        Thread receiver = new Thread(() -> {
            byte[] buf = new byte[1024];
            try {
                while (!socket.isClosed()) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    socket.receive(p);
                    String msg = new String(p.getData(), 0, p.getLength(),
                            StandardCharsets.UTF_8);
                    System.out.println(msg + "   (" + p.getAddress().getHostAddress() + ")");
                }
            } catch (Exception e) {
                if (!socket.isClosed()) e.printStackTrace();
            }
        });
        receiver.setDaemon(true);
        receiver.start();

        send(socket, groupAddr, "*** " + nick + " đã vào phòng ***");
        System.out.println("Gõ tin nhắn rồi Enter. Gõ /quit để rời phòng.");

        Scanner sc = new Scanner(System.in);
        try {
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (line.equals("/quit")) break;
                if (!line.isEmpty()) send(socket, groupAddr, nick + ": " + line);
            }
        } finally {
            send(socket, groupAddr, "*** " + nick + " đã rời phòng ***");
            socket.leaveGroup(groupAddr, nif);   // rời nhóm
            socket.close();
        }
    }

    static void send(MulticastSocket s, InetSocketAddress to, String text) throws Exception {
        byte[] d = text.getBytes(StandardCharsets.UTF_8);
        s.send(new DatagramPacket(d, d.length, to));
    }

    // In ra các card khả dụng và chọn card WiFi.
    // wanted: tên card, một phần tên hiển thị, hoặc IP WiFi của máy (vd 192.168.137.1). null = tự chọn.
    static NetworkInterface pickInterface(String wanted) throws Exception {
        String[] bad = {"vmware", "virtualbox", "vethernet", "hyper-v", "docker", "wsl",
                "tap", "tun", "vpn", "bluetooth", "virtual"};
        String[] good = {"wi-fi", "wifi", "wlan", "wireless", "en0"};
        List<NetworkInterface> cands = new ArrayList<>();

        System.out.println("Các card mạng khả dụng:");
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback() || !ni.supportsMulticast()) continue;
            String ip = null;
            for (InetAddress a : Collections.list(ni.getInetAddresses()))
                if (a instanceof Inet4Address) ip = a.getHostAddress();
            if (ip == null) continue;

            System.out.println("  - " + ni.getName() + " | " + ni.getDisplayName() + " | " + ip);
            cands.add(ni);
            if (wanted != null && (ni.getName().equalsIgnoreCase(wanted)
                    || ip.equals(wanted)
                    || ni.getDisplayName().toLowerCase().contains(wanted.toLowerCase())))
                return ni;
        }
        // Truyền vào IP thì lấy thẳng card mang IP đó (chắc nhất, kể cả card hotspot)
        if (wanted != null && wanted.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            NetworkInterface byIp = NetworkInterface.getByInetAddress(InetAddress.getByName(wanted));
            if (byIp != null) return byIp;
            throw new SocketException("Không có card nào mang IP " + wanted);
        }
        if (wanted != null) throw new SocketException("Không thấy card: " + wanted);
        if (cands.isEmpty()) throw new SocketException("Không có card mạng phù hợp");

        // Tự chọn: bỏ card ảo, ưu tiên card có tên giống WiFi
        NetworkInterface fallback = null;
        for (NetworkInterface ni : cands) {
            String n = (ni.getName() + " " + ni.getDisplayName()).toLowerCase();
            if (containsAny(n, bad)) continue;
            if (containsAny(n, good)) return ni;
            if (fallback == null) fallback = ni;
        }
        return fallback != null ? fallback : cands.get(0);
    }

    static boolean containsAny(String s, String[] keys) {
        for (String k : keys) if (s.contains(k)) return true;
        return false;
    }
}
