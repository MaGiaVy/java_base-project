import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MulticastGroupChat {
    static final int PORT = 8000;
    static final String[] GROUPS = {"239.1.1.1", "239.1.1.2", "239.1.1.3"};

    static MulticastSocket socket;
    static NetworkInterface nif;
    static String nick;
    static final Set<Integer> joined = Collections.synchronizedSet(new LinkedHashSet<>());
    static int current = -1;   // nhóm đang gửi tin tới

    public static void main(String[] args) throws Exception {
        nick = args.length > 0 ? args[0] : "user" + new Random().nextInt(100);
        nif = MulticastChat.pickInterface(args.length > 1 ? args[1] : null);
        System.out.println("Dùng card: " + nif.getDisplayName());

        socket = new MulticastSocket(PORT);
        socket.setNetworkInterface(nif);
        socket.setTimeToLive(1);

        Thread rx = new Thread(MulticastGroupChat::receiveLoop);
        rx.setDaemon(true);
        rx.start();

        Scanner sc = new Scanner(System.in);
        System.out.print("Bạn có muốn vào nhóm không? (y/n): ");
        if (sc.nextLine().trim().equalsIgnoreCase("y")) {
            printGroups();
            System.out.print("Chọn nhóm: ");
            join(parseGroup(sc.nextLine()));
        }
        System.out.println("Lệnh: /groups  /join N  /leave N  /to N  /quit. Gõ chữ thường để chat.");

        while (sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            if (line.equals("/quit")) break;
            if (line.equals("/groups")) { printGroups(); continue; }
            if (line.startsWith("/join "))  { join(parseGroup(line.substring(6)));  continue; }
            if (line.startsWith("/leave ")) { leave(parseGroup(line.substring(7))); continue; }
            if (line.startsWith("/to ")) {
                int g = parseGroup(line.substring(4));
                if (g >= 0 && joined.contains(g)) {
                    current = g;
                    System.out.println("Đang chat ở nhóm " + (g + 1));
                } else if (g >= 0) {
                    System.out.println("Bạn chưa vào nhóm " + (g + 1));
                }
                continue;
            }
            if (current < 0) {
                System.out.println("Bạn chưa vào nhóm nào, gõ /join N");
                continue;
            }
            send(current, nick + ": " + line);
        }
        for (int g : new ArrayList<>(joined)) leave(g);
        socket.close();
    }

    static void join(int g) throws Exception {
        if (g < 0) return;
        if (joined.contains(g)) { System.out.println("Bạn đã ở nhóm " + (g + 1)); return; }
        socket.joinGroup(new InetSocketAddress(GROUPS[g], PORT), nif);   // VÀO NHÓM
        joined.add(g);
        current = g;
        send(g, "*** " + nick + " đã vào nhóm ***");
    }

    static void leave(int g) throws Exception {
        if (g < 0) return;
        if (!joined.contains(g)) { System.out.println("Bạn chưa ở nhóm " + (g + 1)); return; }
        send(g, "*** " + nick + " đã rời nhóm ***");
        socket.leaveGroup(new InetSocketAddress(GROUPS[g], PORT), nif);  // RỜI NHÓM
        joined.remove(g);
        if (current == g) {
            current = -1;
            for (int x : new ArrayList<>(joined)) current = x;
        }
    }

    // Gói tin có dạng "chỉ_số_nhóm|nội dung"
    static void send(int g, String text) throws Exception {
        byte[] d = (g + "|" + text).getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(d, d.length, InetAddress.getByName(GROUPS[g]), PORT));
    }

    static void receiveLoop() {
        byte[] buf = new byte[1024];
        try {
            while (!socket.isClosed()) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                int bar = msg.indexOf('|');
                if (bar < 0) continue;
                int g;
                try { g = Integer.parseInt(msg.substring(0, bar)); }
                catch (NumberFormatException e) { continue; }
                if (!joined.contains(g)) continue;      // không ở nhóm này thì bỏ qua
                System.out.println("[Nhóm " + (g + 1) + "] " + msg.substring(bar + 1));
            }
        } catch (Exception e) {
            if (!socket.isClosed()) e.printStackTrace();
        }
    }

    static int parseGroup(String s) {
        try {
            int n = Integer.parseInt(s.trim());
            if (n >= 1 && n <= GROUPS.length) return n - 1;
        } catch (NumberFormatException ignored) { }
        System.out.println("Nhóm không hợp lệ (1-" + GROUPS.length + ")");
        return -1;
    }

    static void printGroups() {
        for (int i = 0; i < GROUPS.length; i++)
            System.out.println("  " + (i + 1) + ". Nhóm " + (i + 1) + " (" + GROUPS[i] + ")"
                    + (joined.contains(i) ? "  <- đã vào" : ""));
    }
}
