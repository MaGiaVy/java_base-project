import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UDP Multicast Chat - bai tap mo rong.
 *
 * Tinh nang:
 * 1) Chat nhom 2 chieu: moi may vua gui vua nhan.
 * 2) Ten hien thi.
 * 3) Nhieu phong = nhieu dia chi multicast.
 * 4) Chuyen phong khi dang chay.
 * 5) Hien thi thoi gian thong diep.
 * 6) So thu tu + phat hien gap + NACK de gui lai Datagram bi mat.
 *
 * Cach chay:
 *   javac *.java
 *   java MulticastChat <ten> [localIPv4]
 *
 * Vi du:
 *   java MulticastChat An
 *   java MulticastChat Binh 192.168.1.12
 */
public class MulticastChat {
    private static final int PORT = 8000;
    private static final int TTL = 1;
    private static final int MAX_PACKET = 16 * 1024;
    private static final int MAX_RESEND_RANGE = 50;
    private static final int CACHE_LIMIT = 500;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Map<String, Room> ROOMS = new LinkedHashMap<>();
    static {
        ROOMS.put("1", new Room("1", "Java", "239.1.1.1"));
        ROOMS.put("2", new Room("2", "Network", "239.1.1.2"));
        ROOMS.put("3", new Room("3", "IT1", "239.1.1.3"));
    }

    private final String displayName;
    private final String nodeId = UUID.randomUUID().toString();
    private final NetworkInterface networkInterface;
    private final MulticastSocket socket;
    private final Object roomLock = new Object();

    private volatile Room currentRoom;
    private volatile boolean running = true;
    private final AtomicInteger simulateDropEvery = new AtomicInteger(0);
    private final Set<String> simulatedDrops = ConcurrentHashMap.newKeySet();

    private final Map<String, AtomicLong> seqByRoom = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentHashMap<Long, ChatMessage>> sentCacheByRoom = new ConcurrentHashMap<>();
    private final Map<String, SenderTracker> trackerBySender = new ConcurrentHashMap<>();

    private MulticastChat(String displayName, String localIp) throws Exception {
        this.displayName = displayName;
        this.networkInterface = chooseInterface(localIp);
        if (networkInterface == null) {
            throw new IllegalStateException(
                    "Khong tim thay card mang IPv4 dang hoat dong va ho tro multicast. " +
                    "Hay chay ShowNetworkInfo va truyen localIPv4 khi khoi dong.");
        }

        this.socket = new MulticastSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(PORT));
        socket.setNetworkInterface(networkInterface);
        socket.setTimeToLive(TTL);
    }

    public static void main(String[] args) {
        String name = args.length >= 1 ? args[0].trim() : "";
        String localIp = args.length >= 2 ? args[1].trim() : null;

        if (name.isBlank()) {
            System.out.println("Cach chay: java MulticastChat <ten> [localIPv4]");
            System.out.println("Vi du    : java MulticastChat An");
            return;
        }

        MulticastChat app = null;
        try {
            app = new MulticastChat(name, localIp);
            app.run();
        } catch (Exception e) {
            System.err.println("LOI: " + e.getMessage());
            System.err.println("Goi y: javac *.java -> java ShowNetworkInfo -> mo firewall UDP 8000.");
        } finally {
            if (app != null) app.shutdown();
        }
    }

    private void run() throws Exception {
        printBanner();

        Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);
        printRooms();
        System.out.print("Chon phong luc dau [1]: ");
        String first = scanner.hasNextLine() ? scanner.nextLine().trim() : "1";
        if (first.isBlank()) first = "1";
        Room firstRoom = resolveRoom(first);
        if (firstRoom == null) firstRoom = ROOMS.get("1");
        switchRoom(firstRoom);

        Thread receiverThread = new Thread(this::receiveLoop, "multicast-receiver");
        receiverThread.setDaemon(true);
        receiverThread.start();

        printHelp();

        while (running && scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("/")) {
                handleCommand(line);
            } else {
                sendChat(line);
            }
        }
    }

    private void printBanner() {
        System.out.println("============================================================");
        System.out.println(" UDP MULTICAST CHAT - BAI TAP MO RONG");
        System.out.println("============================================================");
        System.out.println("Ten hien thi : " + displayName);
        System.out.println("Card mang    : " + networkInterface.getDisplayName());
        System.out.println("Port         : " + PORT);
        System.out.println("TTL          : " + TTL + " (chi trong LAN)");
        System.out.println();
    }

    private void printRooms() {
        System.out.println("Phong co san:");
        for (Room r : ROOMS.values()) {
            System.out.printf("  %s. %-10s -> %s:%d%n", r.id, r.name, r.group, PORT);
        }
        System.out.println();
    }

    private void printHelp() {
        System.out.println("Lenh:");
        System.out.println("  <noi dung>     Gui tin nhan vao phong hien tai");
        System.out.println("  /rooms         Xem danh sach phong");
        System.out.println("  /join 1        Chuyen sang phong 1 (co the dung ten: /join Java)");
        System.out.println("  /status        Xem phong/card mang hien tai");
        System.out.println("  /loss 3        Gia lap bo mat moi goi thu 3 de demo NACK/gui lai");
        System.out.println("  /loss 0        Tat gia lap mat goi");
        System.out.println("  /help          Xem lai huong dan");
        System.out.println("  /exit          Roi nhom va thoat");
        System.out.println();
        System.out.println("Bat dau chat. Moi dong khong bat dau bang '/' se duoc gui.");
        System.out.println();
    }

    private void handleCommand(String line) {
        String[] parts = line.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length >= 2 ? parts[1].trim() : "";

        try {
            switch (cmd) {
                case "/rooms" -> printRooms();
                case "/help" -> printHelp();
                case "/status" -> printStatus();
                case "/join" -> {
                    Room room = resolveRoom(arg);
                    if (room == null) {
                        System.out.println("Khong tim thay phong '" + arg + "'. Go /rooms de xem.");
                    } else {
                        switchRoom(room);
                    }
                }
                case "/loss" -> {
                    int n = Integer.parseInt(arg);
                    if (n < 0) throw new NumberFormatException();
                    simulateDropEvery.set(n);
                    simulatedDrops.clear();
                    if (n == 0) {
                        System.out.println("Da tat gia lap mat goi.");
                    } else {
                        System.out.println("Da bat gia lap: bo qua lan nhan DAU TIEN cua moi goi co seq chia het cho " + n + ".");
                        System.out.println("Khi goi ke tiep den, chuong trinh se phat hien gap va yeu cau gui lai.");
                    }
                }
                case "/exit" -> running = false;
                default -> System.out.println("Lenh khong hop le. Go /help.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Dung: /loss <so>, vi du /loss 3 hoac /loss 0");
        } catch (Exception e) {
            System.out.println("Khong thuc hien duoc lenh: " + e.getMessage());
        }
    }

    private void printStatus() {
        Room r = currentRoom;
        System.out.println("--- STATUS ---");
        System.out.println("Ten       : " + displayName);
        System.out.println("Card mang : " + networkInterface.getDisplayName());
        System.out.println("Phong     : " + (r == null ? "chua tham gia" : r.name + " (" + r.group + ":" + PORT + ")"));
        System.out.println("TTL       : " + TTL);
        System.out.println("Gia lap mat goi moi N: " + simulateDropEvery.get());
    }

    private Room resolveRoom(String token) {
        if (token == null || token.isBlank()) return null;
        Room byId = ROOMS.get(token.trim());
        if (byId != null) return byId;
        for (Room r : ROOMS.values()) {
            if (r.name.equalsIgnoreCase(token.trim())) return r;
        }
        return null;
    }

    private void switchRoom(Room newRoom) throws Exception {
        synchronized (roomLock) {
            if (currentRoom != null && currentRoom.id.equals(newRoom.id)) {
                System.out.println("Ban dang o phong " + newRoom.name + ".");
                return;
            }

            if (currentRoom != null) {
                InetAddress oldGroup = InetAddress.getByName(currentRoom.group);
                socket.leaveGroup(new InetSocketAddress(oldGroup, PORT), networkInterface);
                System.out.println("<- Da roi phong " + currentRoom.name + " (" + currentRoom.group + ")");
            }

            InetAddress group = InetAddress.getByName(newRoom.group);
            socket.joinGroup(new InetSocketAddress(group, PORT), networkInterface);
            currentRoom = newRoom;
            trackerBySender.clear();
            simulatedDrops.clear();

            System.out.println("-> Da tham gia phong " + newRoom.name + " | " + newRoom.group + ":" + PORT);
            System.out.println();
        }
    }

    private void sendChat(String text) throws Exception {
        Room room = currentRoom;
        if (room == null) {
            System.out.println("Ban chua tham gia phong. Dung /join 1");
            return;
        }

        long seq = seqByRoom.computeIfAbsent(room.id, k -> new AtomicLong(0)).incrementAndGet();
        long epoch = System.currentTimeMillis();
        ChatMessage msg = new ChatMessage(room.id, nodeId, seq, epoch, displayName, text);

        sentCacheByRoom
                .computeIfAbsent(room.id, k -> new ConcurrentHashMap<>())
                .put(seq, msg);
        trimOwnCache(room.id, seq);

        sendRaw(msg.toWire("MSG"), room);
        System.out.printf("[%s] [Ban #%04d] %s%n", formatTime(epoch), seq, text);
    }

    private void trimOwnCache(String roomId, long newestSeq) {
        ConcurrentHashMap<Long, ChatMessage> cache = sentCacheByRoom.get(roomId);
        if (cache == null || cache.size() <= CACHE_LIMIT) return;
        long cutoff = newestSeq - CACHE_LIMIT;
        cache.keySet().removeIf(k -> k < cutoff);
    }

    private void receiveLoop() {
        byte[] buffer = new byte[MAX_PACKET];
        while (running && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String wire = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                handleIncoming(wire);
            } catch (Exception e) {
                if (running && !socket.isClosed()) {
                    System.out.println("[Nhan] Loi: " + e.getMessage());
                }
            }
        }
    }

    private void handleIncoming(String wire) {
        try {
            String[] p = wire.split("\\|", -1);
            if (p.length == 0) return;
            String type = p[0];

            if ("NACK".equals(type)) {
                handleNack(p);
                return;
            }

            if (!"MSG".equals(type) && !"RESEND".equals(type)) return;
            if (p.length != 7) return;

            String roomId = p[1];
            Room room = currentRoom;
            if (room == null || !room.id.equals(roomId)) return;

            String senderId = p[2];
            long seq = Long.parseLong(p[3]);
            long epoch = Long.parseLong(p[4]);
            String senderName = decode(p[5]);
            String text = decode(p[6]);

            // Tin cua chinh minh da duoc in ngay luc gui.
            if (nodeId.equals(senderId)) return;

            boolean isResend = "RESEND".equals(type);
            if (!isResend && shouldSimulateDrop(senderId, seq)) {
                System.out.printf("[MO PHONG MAT GOI] Bo qua goi %s #%04d lan dau.%n", senderName, seq);
                return;
            }

            SenderTracker tracker = trackerBySender.computeIfAbsent(senderId, k -> new SenderTracker());
            long missingFrom = -1;
            long missingTo = -1;
            boolean late;

            synchronized (tracker) {
                if (tracker.seen.contains(seq)) return;

                if (tracker.maxSeen < 0) {
                    tracker.maxSeen = seq;
                    tracker.seen.add(seq);
                    late = false;
                } else {
                    long oldMax = tracker.maxSeen;
                    if (seq > oldMax + 1) {
                        missingFrom = oldMax + 1;
                        missingTo = seq - 1;
                    }
                    late = seq <= oldMax;
                    tracker.seen.add(seq);
                    if (seq > tracker.maxSeen) tracker.maxSeen = seq;
                    cleanupSeen(tracker);
                }
            }

            String tag = late || isResend ? "GUI LAI" : "TIN";
            System.out.printf("[%s] [%s %s #%04d] %s%n", formatTime(epoch), tag, senderName, seq, text);

            if (missingFrom >= 0) {
                long cappedTo = Math.min(missingTo, missingFrom + MAX_RESEND_RANGE - 1);
                System.out.printf("[PHAT HIEN MAT] Thieu goi %s #%04d..#%04d -> gui NACK.%n",
                        senderName, missingFrom, cappedTo);
                sendNack(room, senderId, missingFrom, cappedTo);
            }
        } catch (Exception ignored) {
            // Bo qua datagram khong dung dinh dang cua ung dung.
        }
    }

    private boolean shouldSimulateDrop(String senderId, long seq) {
        int n = simulateDropEvery.get();
        if (n <= 0 || seq % n != 0) return false;
        String key = senderId + ":" + seq;
        return simulatedDrops.add(key);
    }

    private void cleanupSeen(SenderTracker tracker) {
        if (tracker.seen.size() < 300) return;
        long keepFrom = Math.max(0, tracker.maxSeen - 200);
        tracker.seen.removeIf(v -> v < keepFrom);
    }

    private void sendNack(Room room, String targetSenderId, long from, long to) {
        try {
            String wire = String.join("|",
                    "NACK",
                    room.id,
                    nodeId,
                    targetSenderId,
                    Long.toString(from),
                    Long.toString(to));
            sendRaw(wire, room);
        } catch (Exception e) {
            System.out.println("[NACK] Khong gui duoc yeu cau gui lai: " + e.getMessage());
        }
    }

    private void handleNack(String[] p) {
        try {
            if (p.length != 6) return;
            String roomId = p[1];
            String requesterId = p[2];
            String targetSenderId = p[3];
            long from = Long.parseLong(p[4]);
            long to = Long.parseLong(p[5]);

            if (!nodeId.equals(targetSenderId) || nodeId.equals(requesterId)) return;
            Room room = currentRoom;
            if (room == null || !room.id.equals(roomId)) return;

            ConcurrentHashMap<Long, ChatMessage> cache = sentCacheByRoom.get(roomId);
            if (cache == null) return;

            long safeTo = Math.min(to, from + MAX_RESEND_RANGE - 1);
            for (long seq = from; seq <= safeTo; seq++) {
                ChatMessage msg = cache.get(seq);
                if (msg != null) {
                    sendRaw(msg.toWire("RESEND"), room);
                    System.out.printf("[GUI LAI] Da gui lai #%04d theo NACK.%n", seq);
                    try { Thread.sleep(8); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                }
            }
        } catch (Exception e) {
            System.out.println("[RESEND] Loi: " + e.getMessage());
        }
    }

    private void sendRaw(String wire, Room room) throws Exception {
        byte[] data = wire.getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_PACKET) {
            throw new IllegalArgumentException("Tin nhan qua dai.");
        }
        InetAddress group = InetAddress.getByName(room.group);
        DatagramPacket packet = new DatagramPacket(data, data.length, group, PORT);
        synchronized (socket) {
            socket.send(packet);
        }
    }

    private void shutdown() {
        running = false;
        if (socket == null || socket.isClosed()) return;
        try {
            synchronized (roomLock) {
                if (currentRoom != null) {
                    InetAddress group = InetAddress.getByName(currentRoom.group);
                    socket.leaveGroup(new InetSocketAddress(group, PORT), networkInterface);
                    System.out.println("Da roi phong " + currentRoom.name + ".");
                }
            }
        } catch (Exception ignored) {
        }
        socket.close();
        System.out.println("Da dong socket. Tam biet!");
    }

    private static String formatTime(long epochMillis) {
        LocalDateTime t = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
        return t.format(TIME_FMT);
    }

    private static String encode(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String s) {
        return new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8);
    }

    private static NetworkInterface chooseInterface(String localIp) throws Exception {
        if (localIp != null && !localIp.isBlank()) {
            InetAddress localAddress = InetAddress.getByName(localIp);
            NetworkInterface ni = NetworkInterface.getByInetAddress(localAddress);
            if (ni == null) throw new IllegalArgumentException("Khong tim thay card mang cua IP " + localIp);
            if (!ni.isUp() || !ni.supportsMulticast()) {
                throw new IllegalArgumentException("Card mang cua IP " + localIp + " khong san sang cho multicast.");
            }
            return ni;
        }

        Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
        if (all == null) return null;

        // Loai card ao (VMware, VirtualBox, Hyper-V, VPN...): gui qua card ao thi may khac khong nhan duoc.
        NetworkInterface best = null;
        int bestScore = Integer.MIN_VALUE;
        for (NetworkInterface ni : Collections.list(all)) {
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual() || !ni.supportsMulticast()) continue;
            boolean hasIpv4 = false, siteLocal = false;
            for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                if (addr instanceof Inet4Address && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                    hasIpv4 = true;
                    if (addr.isSiteLocalAddress()) siteLocal = true;
                }
            }
            if (!hasIpv4) continue;
            String text = (ni.getName() + " " + ni.getDisplayName()).toLowerCase();
            int score = siteLocal ? 10 : 0;
            if (text.contains("wi-fi") || text.contains("wifi") || text.contains("wlan") || text.contains("wireless")) score += 20;
            if (text.contains("ethernet") || text.startsWith("eth") || text.startsWith("en")) score += 15;
            if (text.contains("virtual") || text.contains("vmware") || text.contains("vbox") || text.contains("hyper-v")
                    || text.contains("vethernet") || text.contains("docker") || text.contains("wsl") || text.contains("vpn")
                    || text.contains("tap") || text.contains("tun") || text.contains("radmin") || text.contains("hamachi")
                    || text.contains("zerotier") || text.contains("tailscale") || text.contains("bluetooth")
                    || text.contains("pseudo")) score -= 100;
            if (score > bestScore) { bestScore = score; best = ni; }
        }
        return best;
    }

    private record Room(String id, String name, String group) {}

    private record ChatMessage(String roomId, String senderId, long seq, long epoch,
                               String senderName, String text) {
        String toWire(String type) {
            return String.join("|",
                    type,
                    roomId,
                    senderId,
                    Long.toString(seq),
                    Long.toString(epoch),
                    encode(senderName),
                    encode(text));
        }
    }

    private static class SenderTracker {
        long maxSeen = -1;
        final Set<Long> seen = ConcurrentHashMap.newKeySet();
    }
}
