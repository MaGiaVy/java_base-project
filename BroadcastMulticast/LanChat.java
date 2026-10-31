import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * GỘP BÀI 9 (UDP Broadcast) + BÀI 10 (UDP Multicast) - Chat LAN theo nhóm, có thể gửi cho tất cả
 *
 *  KÊNH NHÓM - MULTICAST (bài 10), cổng 8000:
 *    - Mỗi nhóm/phòng là một địa chỉ multicast: phòng N <-> 239.1.1.N
 *    - Gõ nội dung bình thường -> chỉ thành viên CÙNG PHÒNG nhận được
 *    - /join N chuyển phòng (leaveGroup phòng cũ, joinGroup phòng mới)
 *    - Thời gian + số thứ tự, phát hiện datagram bị mất và yêu cầu gửi lại (NACK)
 *
 *  KÊNH TẤT CẢ - BROADCAST (bài 9), cổng 7000:
 *    - /all nội dung -> gửi broadcast, MỌI MÁY trong LAN đều nhận (dù ở phòng nào)
 *    - Hiển thị IP + tên máy gửi, thời gian, số thứ tự
 *    - /find tự động tìm các peer online (broadcast DISCOVER_CHAT, peer phản hồi unicast)
 *    - Giới hạn tần suất broadcast: tối thiểu 1 giây/lần, tối đa 5 lần/10 giây, tối đa 512 byte
 *
 * Định dạng gói:
 *   Multicast: D|phòng|id|seq|thờiGian|loại|gửiLại|tên|nộiDung      (loại: C=chat, J=vào, L=rời)
 *              N|phòng|idYêuCầu|idGốc|seq                            (NACK xin gửi lại)
 *   Broadcast: ALL|id|seq|thờiGian|tên|tênMáy|phòng|nộiDung
 *              DISCOVER_CHAT|id
 *              CHAT_PEER|tên|tênMáy|phòng|id                           (phản hồi unicast)
 *
 * Chạy:  javac -encoding UTF-8 LanChat.java
 *        java LanChat                 (Khởi chạy giao diện đồ họa GUI)
 *        java LanChat "Alice"         (Khởi chạy với tên chỉ định)
 *        java LanChat --cli           (Khởi chạy chế độ dòng lệnh Console)
 *        java LanChat 192.168.1.5     (Chỉ định IP card mạng cần dùng)
 */
public class LanChat {

    // ---- Multicast (nhóm) ----
    static final int MC_PORT = 8000;
    static final String GROUP_PREFIX = "239.1.1.";
    static final int TTL = 1;
    static final int HISTORY_SIZE = 500;
    static final int MAX_NACK_RETRY = 3;

    // ---- Broadcast (tất cả) ----
    static final int BC_PORT = 7000;
    static final int MAX_BYTES = 512;
    static final long MIN_INTERVAL_MS = 1000;
    static final int MAX_IN_WINDOW = 5;
    static final long WINDOW_MS = 10_000;
    static final long DISCOVER_WAIT_MS = 1500;

    private final String id = UUID.randomUUID().toString().substring(0, 8);
    private volatile String name;
    private final String hostName;
    private final List<NetworkInterface> ifaces;
    private final List<InetAddress> broadcastAddrs;

    private final MulticastSocket mcSocket;     // nhận + gửi multicast
    private final DatagramSocket bcRecvSocket;  // nhận broadcast cổng 7000
    private final DatagramSocket bcSendSocket;  // gửi broadcast, nhận phản hồi unicast

    private volatile int room = 1;
    private volatile InetSocketAddress groupEP;
    private volatile int lossPercent = 0;
    private volatile boolean running = true;
    private final Random random = new Random();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss");

    // multicast: bên gửi
    private final Map<Integer, Integer> sendSeq = new ConcurrentHashMap<>();
    private final Map<Integer, Map<Integer, String>> history = new ConcurrentHashMap<>();
    // multicast: bên nhận
    private final Map<String, Integer> expectedSeq = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, Integer>> missing = new ConcurrentHashMap<>();
    private final Map<String, String> senderNames = new ConcurrentHashMap<>();

    // broadcast
    private int bcSeq = 0;
    private final Deque<Long> bcSentTimes = new ArrayDeque<>();
    private final Map<String, Integer> bcLastSeq = new ConcurrentHashMap<>();
    private final Map<String, Long> lastDiscover = new ConcurrentHashMap<>();
    private final Map<String, String[]> peers = new ConcurrentHashMap<>();   // id -> {ip, tên, tên máy, phòng}

    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "nack-retry");
        t.setDaemon(true);
        return t;
    });

    // Callback lắng nghe sự kiện để cập nhật GUI / CLI
    private ChatListener listener;

    public interface ChatListener {
        void onRoomMessage(long time, char kind, String who, String text, String note, int room);
        void onBroadcastMessage(long time, int seq, String who, String ip, String host, int room, String text, String note);
        void onSystemMessage(String text, String level); // "INFO", "WARN", "ERROR"
        void onPeerUpdated(String id, String ip, String name, String host, String room);
        void onPeersCleared();
        void onRoomChanged(int newRoom);
    }

    public LanChat(String name, String ifArg) throws Exception {
        this.name = clean(name);
        this.hostName = clean(InetAddress.getLocalHost().getHostName());
        this.ifaces = chooseInterfaces(ifArg);
        this.broadcastAddrs = findBroadcastAddresses(ifaces);

        mcSocket = new MulticastSocket(MC_PORT);
        mcSocket.setTimeToLive(TTL);

        bcRecvSocket = new DatagramSocket(null);
        bcRecvSocket.setReuseAddress(true);
        bcRecvSocket.bind(new InetSocketAddress(BC_PORT));

        bcSendSocket = new DatagramSocket();
        bcSendSocket.setBroadcast(true);
    }

    public void setListener(ChatListener listener) {
        this.listener = listener;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String newName) {
        if (newName != null && !newName.trim().isEmpty()) {
            this.name = clean(newName.trim());
            notifySystem("Đã đổi tên hiển thị thành: " + this.name, "INFO");
        }
    }
    public String getHostName() { return hostName; }
    public int getRoom() { return room; }
    public int getLossPercent() { return lossPercent; }
    public void setLossPercent(int p) {
        this.lossPercent = Math.max(0, Math.min(100, p));
        notifySystem("Giả lập mất " + this.lossPercent + "% gói chat nhóm khi gửi", "INFO");
    }
    public List<NetworkInterface> getInterfaces() { return ifaces; }

    static String clean(String s) { return s == null ? "" : s.replace("|", "/"); }

    // ======================================================================
    //                        KÊNH NHÓM - MULTICAST
    // ======================================================================

    public synchronized void joinRoom(int newRoom) throws Exception {
        if (newRoom < 1 || newRoom > 254) {
            notifySystem("Phòng không hợp lệ (hợp lệ: 1..254)", "WARN");
            return;
        }
        if (groupEP != null) {
            sendData(room, 'L', "đã rời phòng");
            for (NetworkInterface ni : ifaces) {
                try { mcSocket.leaveGroup(groupEP, ni); } catch (Exception ignored) { }
            }
            forgetRoomState(room);
        }
        room = newRoom;
        groupEP = new InetSocketAddress(InetAddress.getByName(GROUP_PREFIX + newRoom), MC_PORT);
        int joined = 0;
        for (NetworkInterface ni : ifaces) {
            try { mcSocket.joinGroup(groupEP, ni); joined++; } catch (Exception ignored) { }
        }
        if (joined == 0) throw new Exception("Không join được nhóm multicast trên card mạng nào");

        String msg = "Đã vào phòng " + newRoom + " (" + GROUP_PREFIX + newRoom + ":" + MC_PORT + ")";
        System.out.println("*** " + msg);
        notifySystem(msg, "INFO");
        if (listener != null) listener.onRoomChanged(newRoom);

        sendData(room, 'J', "đã vào phòng");
    }

    private void forgetRoomState(int r) {
        String prefix = r + "|";
        expectedSeq.keySet().removeIf(k -> k.startsWith(prefix));
        missing.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public void sendRoomChat(String text) {
        try {
            sendData(room, 'C', text);
            showRoom(System.currentTimeMillis(), 'C', name + " (bạn)", text, null);
        } catch (Exception e) {
            notifySystem("Lỗi gửi chat phòng: " + e.getMessage(), "ERROR");
        }
    }

    public void sendData(int r, char kind, String text) throws Exception {
        int seq = sendSeq.merge(r, 1, Integer::sum);
        long now = System.currentTimeMillis();
        String raw = "D|" + r + "|" + id + "|" + seq + "|" + now + "|" + kind + "|0|" + name + "|" + text;
        Map<Integer, String> h = history.computeIfAbsent(r, k -> new ConcurrentHashMap<>());
        h.put(seq, raw);
        h.remove(seq - HISTORY_SIZE);
        if (kind == 'C' && lossPercent > 0 && random.nextInt(100) < lossPercent) {
            String dropMsg = "    (giả lập: gói #" + seq + " bị \"mất\", không gửi đi)";
            System.out.println(dropMsg);
            notifySystem(dropMsg, "WARN");
            return;
        }
        sendMulticast(raw, r);
    }

    private synchronized void sendMulticast(String raw, int r) throws Exception {
        byte[] data = raw.getBytes(StandardCharsets.UTF_8);
        InetSocketAddress ep = new InetSocketAddress(InetAddress.getByName(GROUP_PREFIX + r), MC_PORT);
        for (NetworkInterface ni : ifaces) {
            try {
                if (ni != null) mcSocket.setNetworkInterface(ni);
                mcSocket.send(new DatagramPacket(data, data.length, ep));
            } catch (Exception ignored) { }
        }
    }

    private void multicastReceiveLoop() {
        byte[] buf = new byte[4096];
        while (running) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                mcSocket.receive(p);
                handleMulticast(new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8),
                        p.getAddress().getHostAddress());
            } catch (Exception e) {
                if (running && !(e instanceof NumberFormatException))
                    System.out.println("Lỗi nhận multicast: " + e.getMessage());
            }
        }
    }

    private void handleMulticast(String raw, String fromIp) throws Exception {
        if (raw.startsWith("D|")) {
            String[] f = raw.split("\\|", 9);
            if (f.length < 9) return;
            int r = Integer.parseInt(f[1]);
            String senderId = f[2];
            int seq = Integer.parseInt(f[3]);
            long time = Long.parseLong(f[4]);
            char kind = f[5].charAt(0);
            boolean isResend = f[6].equals("1");
            String who = f[7], text = f[8];
            if (senderId.equals(id) || r != room) return;   // bỏ gói của mình và của phòng khác

            String key = r + "|" + senderId;
            senderNames.put(key, who);
            Integer exp = expectedSeq.get(key);
            if (exp == null || seq == exp) {
                expectedSeq.put(key, seq + 1);
                showRoom(time, kind, who, text, null);
            } else if (seq > exp) {
                Map<Integer, Integer> miss = missing.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
                for (int s = exp; s < seq; s++) miss.put(s, 0);
                String warn = "!!! Phát hiện mất " + (seq - exp) + " datagram từ " + who + "@" + fromIp
                        + " (#" + exp + (seq - exp > 1 ? "..#" + (seq - 1) : "") + "), đang yêu cầu gửi lại...";
                System.out.println(warn);
                notifySystem(warn, "WARN");
                for (int s = exp; s < seq; s++) sendNack(r, senderId, s);
                expectedSeq.put(key, seq + 1);
                showRoom(time, kind, who, text, null);
            } else {
                Map<Integer, Integer> miss = missing.get(key);
                if (miss != null && miss.remove(seq) != null)
                    showRoom(time, kind, who, text, isResend ? "gửi lại #" + seq : "đến trễ #" + seq);
            }
        } else if (raw.startsWith("N|")) {
            String[] f = raw.split("\\|");
            if (f.length < 5 || !f[3].equals(id)) return;
            int r = Integer.parseInt(f[1]);
            Map<Integer, String> h = history.get(r);
            String old = (h == null) ? null : h.get(Integer.parseInt(f[4]));
            if (old != null) {
                String[] p = old.split("\\|", 9);
                p[6] = "1";
                sendMulticast(String.join("|", p), r);
            }
        }
    }

    private void sendNack(int r, String targetId, int seq) throws Exception {
        sendMulticast("N|" + r + "|" + id + "|" + targetId + "|" + seq, r);
    }

    private void retryMissing() {
        try {
            for (Map.Entry<String, Map<Integer, Integer>> e : missing.entrySet()) {
                String[] k = e.getKey().split("\\|");
                Iterator<Map.Entry<Integer, Integer>> it = e.getValue().entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Integer, Integer> m = it.next();
                    if (m.getValue() >= MAX_NACK_RETRY) {
                        String errMsg = "!!! Không khôi phục được gói #" + m.getKey() + " của "
                                + senderNames.getOrDefault(e.getKey(), k[1]);
                        System.out.println(errMsg);
                        notifySystem(errMsg, "ERROR");
                        it.remove();
                    } else {
                        m.setValue(m.getValue() + 1);
                        sendNack(Integer.parseInt(k[0]), k[1], m.getKey());
                    }
                }
            }
        } catch (Exception ignored) { }
    }

    private void showRoom(long time, char kind, String who, String text, String note) {
        String t = timeFmt.format(new Date(time));
        String tag = (note == null) ? "" : " (" + note + ")";
        if (kind == 'C') System.out.println("[" + t + "] [Phòng " + room + "] " + who + ": " + text + tag);
        else System.out.println("[" + t + "] [Phòng " + room + "] * " + who + " " + text + tag);

        if (listener != null) {
            listener.onRoomMessage(time, kind, who, text, note, room);
        }
    }

    // ======================================================================
    //                        KÊNH TẤT CẢ - BROADCAST
    // ======================================================================

    private synchronized String checkRate() {
        long now = System.currentTimeMillis();
        while (!bcSentTimes.isEmpty() && now - bcSentTimes.peekFirst() > WINDOW_MS) bcSentTimes.pollFirst();
        if (!bcSentTimes.isEmpty() && now - bcSentTimes.peekLast() < MIN_INTERVAL_MS)
            return "Broadcast quá nhanh! Chờ " + (MIN_INTERVAL_MS - (now - bcSentTimes.peekLast())) + " ms nữa.";
        if (bcSentTimes.size() >= MAX_IN_WINDOW)
            return "Đã broadcast " + MAX_IN_WINDOW + " lần trong " + (WINDOW_MS / 1000) + " giây. Chờ "
                    + ((WINDOW_MS - (now - bcSentTimes.peekFirst())) / 1000 + 1) + " giây nữa.";
        bcSentTimes.addLast(now);
        return null;
    }

    public boolean broadcast(String msg) {
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_BYTES) {
            String err = "Thông báo quá dài (" + data.length + " byte, tối đa " + MAX_BYTES + ").";
            System.out.println("!!! " + err);
            notifySystem(err, "ERROR");
            return false;
        }
        String err = checkRate();
        if (err != null) {
            System.out.println("!!! " + err);
            notifySystem(err, "WARN");
            return false;
        }
        for (InetAddress b : broadcastAddrs) {
            try { bcSendSocket.send(new DatagramPacket(data, data.length, b, BC_PORT)); } catch (Exception ignored) { }
        }
        return true;
    }

    public void sendToAll(String text) {
        long now = System.currentTimeMillis();
        String msg = "ALL|" + id + "|" + (bcSeq + 1) + "|" + now + "|" + name + "|" + hostName + "|" + room + "|" + text;
        if (broadcast(msg)) {
            bcSeq++;
            System.out.println("[" + timeFmt.format(new Date(now)) + "] [TẤT CẢ #" + bcSeq + "] " + name + " (bạn): " + text);
            if (listener != null) {
                listener.onBroadcastMessage(now, bcSeq, name + " (bạn)", "localhost", hostName, room, text, null);
            }
        }
    }

    public void discover() throws Exception {
        peers.clear();
        if (listener != null) listener.onPeersCleared();
        if (!broadcast("DISCOVER_CHAT|" + id)) return;
        notifySystem("Đang tìm các peer trong LAN...", "INFO");
        System.out.println("Đang tìm các peer trong LAN...");
        Thread.sleep(DISCOVER_WAIT_MS);
        printPeers();
    }

    public void printPeers() {
        String line = "+----+-----------------+--------------+------------------+-------+";
        System.out.println(line);
        System.out.println("| #  | Địa chỉ IP      | Tên          | Tên máy          | Phòng |");
        System.out.println(line);
        int i = 1;
        for (String[] p : peers.values())
            System.out.printf("| %-2d | %-15s | %-12s | %-16s | %-5s |%n", i++, p[0], cut(p[1], 12), cut(p[2], 16), p[3]);
        System.out.println(line);
        String found = "Tìm thấy " + peers.size() + " peer online.";
        System.out.println(found);
        notifySystem(found, "INFO");
    }

    static String cut(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "…"; }

    private void broadcastReceiveLoop() {
        byte[] buf = new byte[2048];
        while (running) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                bcRecvSocket.receive(p);
                String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                String ip = p.getAddress().getHostAddress();

                if (msg.startsWith("ALL|")) {
                    String[] f = msg.split("\\|", 8);
                    if (f.length < 8 || f[1].equals(id)) continue;
                    int s = Integer.parseInt(f[2]);
                    long t = Long.parseLong(f[3]);
                    String key = ip + "|" + f[1];
                    Integer last = bcLastSeq.get(key);
                    if (last != null && s <= last) continue;              // trùng (vd đến qua 2 card mạng)
                    String note = (last != null && s > last + 1) ? "  (!! mất " + (s - last - 1) + " thông báo)" : "";
                    bcLastSeq.put(key, s);
                    System.out.println("[" + timeFmt.format(new Date(t)) + "] [TẤT CẢ #" + s + "] " + f[4]
                            + " (" + ip + ", " + f[5] + ", phòng " + f[6] + "): " + f[7] + note);

                    if (listener != null) {
                        listener.onBroadcastMessage(t, s, f[4], ip, f[5], Integer.parseInt(f[6]), f[7], note.isEmpty() ? null : note.trim());
                    }

                } else if (msg.startsWith("DISCOVER_CHAT|")) {
                    String fromId = msg.substring("DISCOVER_CHAT|".length());
                    if (fromId.equals(id)) continue;
                    Long prev = lastDiscover.put(fromId, System.currentTimeMillis());
                    if (prev != null && System.currentTimeMillis() - prev < 500) continue;
                    byte[] data = ("CHAT_PEER|" + name + "|" + hostName + "|" + room + "|" + id)
                            .getBytes(StandardCharsets.UTF_8);
                    bcSendSocket.send(new DatagramPacket(data, data.length, p.getAddress(), p.getPort())); // unicast
                }
            } catch (Exception e) {
                if (running && !(e instanceof NumberFormatException))
                    System.out.println("Lỗi nhận broadcast: " + e.getMessage());
            }
        }
    }

    private void peerReplyLoop() {
        byte[] buf = new byte[1024];
        while (running) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                bcSendSocket.receive(p);
                String[] f = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).split("\\|");
                if (f.length == 5 && f[0].equals("CHAT_PEER") && !f[4].equals(id)) {
                    String peerId = f[4];
                    String peerIp = p.getAddress().getHostAddress();
                    String peerName = f[1];
                    String peerHost = f[2];
                    String peerRoom = f[3];
                    peers.put(peerId, new String[]{peerIp, peerName, peerHost, peerRoom});
                    if (listener != null) {
                        listener.onPeerUpdated(peerId, peerIp, peerName, peerHost, peerRoom);
                    }
                }
            } catch (Exception e) {
                if (running) System.out.println("Lỗi nhận phản hồi: " + e.getMessage());
            }
        }
    }

    private void notifySystem(String text, String level) {
        if (listener != null) {
            listener.onSystemMessage(text, level);
        }
    }

    // ======================================================================
    //                              CHƯƠNG TRÌNH
    // ======================================================================

    public void startServices() throws Exception {
        startDaemon(this::multicastReceiveLoop, "recv-multicast");
        startDaemon(this::broadcastReceiveLoop, "recv-broadcast");
        startDaemon(this::peerReplyLoop, "recv-peer");
        timer.scheduleAtFixedRate(this::retryMissing, 2, 2, TimeUnit.SECONDS);
        joinRoom(1);
    }

    public void executeCommand(String line) throws Exception {
        String[] p = line.split("\\s+", 2);
        String arg = p.length > 1 ? p[1].trim() : "";
        switch (p[0].toLowerCase()) {
            case "/all":
                if (arg.isEmpty()) notifySystem("Cách dùng: /all nội dung", "WARN");
                else sendToAll(arg);
                break;
            case "/join":
                if (arg.isEmpty()) notifySystem("Cách dùng: /join số_phòng (1..254)", "WARN");
                else {
                    try {
                        int r = Integer.parseInt(arg);
                        if (r < 1 || r > 254) notifySystem("Phòng hợp lệ: 1..254", "WARN");
                        else if (r == room) notifySystem("Bạn đang ở phòng " + r, "INFO");
                        else joinRoom(r);
                    } catch (NumberFormatException ex) {
                        notifySystem("Số phòng không hợp lệ", "WARN");
                    }
                }
                break;
            case "/find":
                new Thread(() -> { try { discover(); } catch (Exception ignored) {} }).start();
                break;
            case "/peers":
                printPeers();
                break;
            case "/room":
                notifySystem("Phòng hiện tại: " + room + " (" + GROUP_PREFIX + room + ":" + MC_PORT + ")", "INFO");
                break;
            case "/loss":
                try {
                    setLossPercent(Integer.parseInt(arg));
                } catch (Exception ex) {
                    notifySystem("Cách dùng: /loss phần_trăm (0..100)", "WARN");
                }
                break;
            case "/help":
                printHelp();
                break;
            case "/quit":
                quit();
                break;
            default:
                notifySystem("Lệnh không hợp lệ. Gõ /help để xem hướng dẫn", "WARN");
        }
    }

    public void printHelp() {
        String help = "------------------------------------------------------------------\n"
                + " Gõ nội dung + Enter  -> gửi cho PHÒNG hiện tại (multicast)\n"
                + " /all nội dung        -> gửi cho TẤT CẢ máy trong LAN (broadcast)\n"
                + " /join N              -> chuyển sang phòng N (" + GROUP_PREFIX + "N)\n"
                + " /find | /peers       -> tìm / xem các máy đang online\n"
                + " /room | /loss P | /quit\n"
                + "------------------------------------------------------------------";
        System.out.println(help);
        notifySystem(help, "INFO");
    }

    public void quit() {
        if (!running) return;
        running = false;
        try {
            sendData(room, 'L', "đã thoát");
            for (NetworkInterface ni : ifaces) mcSocket.leaveGroup(groupEP, ni);
        } catch (Exception ignored) { }
        try { mcSocket.close(); } catch (Exception ignored) { }
        try { bcRecvSocket.close(); } catch (Exception ignored) { }
        try { bcSendSocket.close(); } catch (Exception ignored) { }
        try { timer.shutdownNow(); } catch (Exception ignored) { }
        System.out.println("Đã thoát.");
    }

    // ======================================================================
    //                              CARD MẠNG
    // ======================================================================

    /** Mặc định dùng mọi card IPv4 đang bật (tránh gói đi nhầm card ảo WSL/Hyper-V/VirtualBox). */
    static List<NetworkInterface> chooseInterfaces(String arg) throws Exception {
        List<NetworkInterface> list = new ArrayList<>();
        if (arg != null && !arg.trim().isEmpty() && !arg.startsWith("-")) {
            NetworkInterface ni = NetworkInterface.getByName(arg);
            if (ni == null) ni = NetworkInterface.getByInetAddress(InetAddress.getByName(arg));
            if (ni == null) throw new IllegalArgumentException("Không tìm thấy card mạng: " + arg);
            list.add(ni);
            return list;
        }
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback() || !ni.supportsMulticast()) continue;
            if (Collections.list(ni.getInetAddresses()).stream().anyMatch(a -> a instanceof Inet4Address)) list.add(ni);
        }
        if (list.isEmpty()) list.add(null);
        return list;
    }

    static List<InetAddress> findBroadcastAddresses(List<NetworkInterface> ifaces) throws Exception {
        List<InetAddress> list = new ArrayList<>();
        for (NetworkInterface ni : ifaces) {
            if (ni == null) continue;
            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                InetAddress b = ia.getBroadcast();
                if (b != null && !list.contains(b)) list.add(b);
            }
        }
        if (list.isEmpty()) list.add(InetAddress.getByName("255.255.255.255"));
        return list;
    }

    static String describe(NetworkInterface ni) {
        if (ni == null) return "(card mặc định)";
        StringBuilder sb = new StringBuilder(ni.getDisplayName());
        for (InetAddress a : Collections.list(ni.getInetAddresses()))
            if (a instanceof Inet4Address) sb.append("  ").append(a.getHostAddress());
        return sb.toString();
    }

    private static void startDaemon(Runnable r, String n) {
        Thread t = new Thread(r, n);
        t.setDaemon(true);
        t.start();
    }

    // ======================================================================
    //                          GIAO DIỆN SWING (GUI)
    // ======================================================================

    public static class GUI extends JFrame implements ChatListener {
        private final LanChat core;
        private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss");

        // Top bar
        private JTextField txtNick;
        private JSpinner spRoom;
        private JComboBox<String> cbLoss;
        private JLabel lblStatus;

        // Chat View
        private JTextPane chatPane;
        private HTMLEditorKit htmlKit;
        private HTMLDocument htmlDoc;
        private JCheckBox chkAutoScroll;

        // Peers Table
        private DefaultTableModel peerModel;
        private final List<String> peerRowIds = new ArrayList<>();
        private JLabel lblPeerCount;

        // Bottom Bar
        private JComboBox<String> cbSendMode;
        private JTextField txtInput;
        private JButton btnSend;

        public GUI(LanChat chatCore) {
            this.core = chatCore;
            this.core.setListener(this);

            setTitle("LAN Chat - " + core.getName() + " (" + core.getId() + ")");
            setSize(940, 620);
            setMinimumSize(new Dimension(750, 480));
            setLocationRelativeTo(null);
            setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    core.quit();
                    dispose();
                }
            });

            initComponents();

            // Khởi động các dịch vụ mạng
            try {
                core.startServices();
                new Thread(() -> {
                    try { core.discover(); } catch (Exception ignored) { }
                }).start();
            } catch (Exception ex) {
                appendSystem("Không thể khởi động mạng: " + ex.getMessage(), "ERROR");
            }
        }

        private void initComponents() {
            JPanel root = new JPanel(new BorderLayout(0, 0));
            root.setBackground(new Color(0xF4F6F9));

            // 1. TOP PANEL (Controls & Status)
            JPanel topContainer = new JPanel(new BorderLayout());
            topContainer.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xCFD8DC)));

            JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
            topBar.setBackground(new Color(0xECEFF1));

            // Tên
            topBar.add(new JLabel("👤 Tên:"));
            txtNick = new JTextField(core.getName(), 9);
            txtNick.setToolTipText("Nhập tên hiển thị và nhấn Đổi");
            topBar.add(txtNick);

            JButton btnChangeNick = new JButton("Đổi");
            btnChangeNick.setFocusPainted(false);
            btnChangeNick.addActionListener(e -> {
                String newN = txtNick.getText().trim();
                if (!newN.isEmpty()) {
                    core.setName(newN);
                    setTitle("LAN Chat - " + core.getName() + " (" + core.getId() + ")");
                }
            });
            topBar.add(btnChangeNick);

            topBar.add(new JSeparator(SwingConstants.VERTICAL));

            // Phòng
            topBar.add(new JLabel("🏠 Phòng:"));
            spRoom = new JSpinner(new SpinnerNumberModel(core.getRoom(), 1, 254, 1));
            spRoom.setPreferredSize(new Dimension(50, 24));
            topBar.add(spRoom);

            JButton btnJoin = new JButton("Vào phòng");
            btnJoin.setFocusPainted(false);
            btnJoin.addActionListener(e -> {
                int r = (Integer) spRoom.getValue();
                try {
                    core.joinRoom(r);
                } catch (Exception ex) {
                    appendSystem("Lỗi vào phòng: " + ex.getMessage(), "ERROR");
                }
            });
            topBar.add(btnJoin);

            topBar.add(new JSeparator(SwingConstants.VERTICAL));

            // Giả lập mất gói
            topBar.add(new JLabel("⚠️ Mất gói:"));
            cbLoss = new JComboBox<>(new String[]{"0% (Chuẩn)", "10%", "25%", "50%"});
            cbLoss.addActionListener(e -> {
                int idx = cbLoss.getSelectedIndex();
                int pct = (idx == 1) ? 10 : (idx == 2) ? 25 : (idx == 3) ? 50 : 0;
                core.setLossPercent(pct);
            });
            topBar.add(cbLoss);

            topBar.add(new JSeparator(SwingConstants.VERTICAL));

            // Nút tìm peer
            JButton btnFind = new JButton("🔍 Tìm máy online");
            btnFind.setFocusPainted(false);
            btnFind.setToolTipText("Broadcast tìm kiếm các máy cùng mở chat trong LAN");
            btnFind.addActionListener(e -> {
                new Thread(() -> {
                    try { core.discover(); } catch (Exception ignored) { }
                }).start();
            });
            topBar.add(btnFind);

            topBar.add(new JSeparator(SwingConstants.VERTICAL));

            // Nút mở thêm cửa sổ chat mới
            JButton btnNewWin = new JButton("➕ Cửa sổ mới");
            btnNewWin.setFocusPainted(false);
            btnNewWin.setToolTipText("Mở thêm 1 cửa sổ chat người dùng khác để thử nghiệm trên cùng 1 máy tính");
            btnNewWin.addActionListener(e -> {
                try {
                    String newName = "User_" + (new Random().nextInt(900) + 100);
                    LanChat newCore = new LanChat(newName, null);
                    GUI newGui = new GUI(newCore);
                    Point p = getLocation();
                    newGui.setLocation(p.x + 35, p.y + 35);
                    newGui.setVisible(true);
                } catch (Exception ex) {
                    appendSystem("Lỗi mở thêm cửa sổ: " + ex.getMessage(), "ERROR");
                }
            });
            topBar.add(btnNewWin);

            topContainer.add(topBar, BorderLayout.NORTH);

            // Sub-status bar
            JPanel subStatusBar = new JPanel(new BorderLayout());
            subStatusBar.setBackground(new Color(0xE0E0E0));
            subStatusBar.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));

            lblStatus = new JLabel(getStatusText());
            lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            lblStatus.setForeground(new Color(0x37474F));
            subStatusBar.add(lblStatus, BorderLayout.CENTER);

            topContainer.add(subStatusBar, BorderLayout.SOUTH);
            root.add(topContainer, BorderLayout.NORTH);

            // 2. CENTER PANEL (Split: Left Chat Log, Right Peers Table)
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
            split.setResizeWeight(0.72);
            split.setDividerSize(6);

            // Left: Chat pane
            JPanel chatContainer = new JPanel(new BorderLayout());
            chatContainer.setBackground(Color.WHITE);

            JPanel chatHeader = new JPanel(new BorderLayout(5, 5));
            chatHeader.setBackground(new Color(0xFAFAFA));
            chatHeader.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

            JLabel lblChatTitle = new JLabel("💬 Nội dung tin nhắn");
            lblChatTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
            chatHeader.add(lblChatTitle, BorderLayout.WEST);

            JPanel chatControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
            chatControls.setOpaque(false);
            chkAutoScroll = new JCheckBox("Tự cuộn", true);
            chkAutoScroll.setOpaque(false);
            chatControls.add(chkAutoScroll);

            JButton btnClear = new JButton("Xóa log");
            btnClear.setFocusPainted(false);
            btnClear.addActionListener(e -> clearChat());
            chatControls.add(btnClear);

            chatHeader.add(chatControls, BorderLayout.EAST);
            chatContainer.add(chatHeader, BorderLayout.NORTH);

            htmlKit = new HTMLEditorKit();
            htmlDoc = new HTMLDocument();
            chatPane = new JTextPane();
            chatPane.setEditorKit(htmlKit);
            chatPane.setDocument(htmlDoc);
            chatPane.setEditable(false);
            chatPane.setBackground(Color.WHITE);
            chatPane.setMargin(new Insets(8, 8, 8, 8));

            JScrollPane scrollChat = new JScrollPane(chatPane);
            scrollChat.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xEEEEEE)));
            chatContainer.add(scrollChat, BorderLayout.CENTER);

            split.setLeftComponent(chatContainer);

            // Right: Peers Table
            JPanel peersContainer = new JPanel(new BorderLayout());
            peersContainer.setBackground(new Color(0xFAFAFA));
            peersContainer.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(0xCFD8DC)));

            JPanel peersHeader = new JPanel(new BorderLayout());
            peersHeader.setBackground(new Color(0xECEFF1));
            peersHeader.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
            JLabel lblPeersTitle = new JLabel("👥 Máy online (Peers)");
            lblPeersTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
            peersHeader.add(lblPeersTitle, BorderLayout.WEST);
            peersContainer.add(peersHeader, BorderLayout.NORTH);

            peerModel = new DefaultTableModel(new String[]{"IP", "Tên", "Phòng", "Máy"}, 0) {
                @Override public boolean isCellEditable(int row, int col) { return false; }
            };
            JTable tblPeers = new JTable(peerModel);
            tblPeers.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            tblPeers.setRowHeight(22);
            tblPeers.getColumnModel().getColumn(0).setPreferredWidth(100);
            tblPeers.getColumnModel().getColumn(1).setPreferredWidth(70);
            tblPeers.getColumnModel().getColumn(2).setPreferredWidth(45);
            tblPeers.getColumnModel().getColumn(3).setPreferredWidth(75);

            DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
            centerRenderer.setHorizontalAlignment(JLabel.CENTER);
            tblPeers.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);

            JScrollPane scrollPeers = new JScrollPane(tblPeers);
            scrollPeers.setBorder(BorderFactory.createEmptyBorder());
            peersContainer.add(scrollPeers, BorderLayout.CENTER);

            JPanel peersFooter = new JPanel(new BorderLayout(5, 5));
            peersFooter.setBackground(new Color(0xFAFAFA));
            peersFooter.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

            lblPeerCount = new JLabel("0 máy online");
            lblPeerCount.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            peersFooter.add(lblPeerCount, BorderLayout.CENTER);

            JButton btnScan = new JButton("Quét lại");
            btnScan.setFocusPainted(false);
            btnScan.addActionListener(e -> {
                new Thread(() -> {
                    try { core.discover(); } catch (Exception ignored) { }
                }).start();
            });
            peersFooter.add(btnScan, BorderLayout.EAST);

            peersContainer.add(peersFooter, BorderLayout.SOUTH);
            split.setRightComponent(peersContainer);

            root.add(split, BorderLayout.CENTER);

            // 3. BOTTOM PANEL (Send input & tips)
            JPanel bottomContainer = new JPanel(new BorderLayout(0, 4));
            bottomContainer.setBackground(new Color(0xECEFF1));
            bottomContainer.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

            JPanel sendRow = new JPanel(new BorderLayout(8, 0));
            sendRow.setOpaque(false);

            cbSendMode = new JComboBox<>(new String[]{
                    "💬 Chat PHÒNG (Multicast)",
                    "📢 Gửi TẤT CẢ (Broadcast /all)"
            });
            cbSendMode.setFocusable(false);
            cbSendMode.setPreferredSize(new Dimension(205, 32));
            sendRow.add(cbSendMode, BorderLayout.WEST);

            txtInput = new JTextField();
            txtInput.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            txtInput.setPreferredSize(new Dimension(100, 32));
            txtInput.addActionListener(e -> handleSendMessage());
            sendRow.add(txtInput, BorderLayout.CENTER);

            btnSend = new JButton("Gửi ↵");
            btnSend.setPreferredSize(new Dimension(85, 32));
            btnSend.setFont(new Font("Segoe UI", Font.BOLD, 12));
            btnSend.setBackground(new Color(0x1976D2));
            btnSend.setForeground(Color.BLACK);
            btnSend.setFocusPainted(false);
            btnSend.addActionListener(e -> handleSendMessage());
            sendRow.add(btnSend, BorderLayout.EAST);

            bottomContainer.add(sendRow, BorderLayout.CENTER);

            JLabel lblTip = new JLabel("<html><small style='color:#546E7A;'>"
                    + "💡 <b>Hỗ trợ gõ lệnh trực tiếp:</b> /all &lt;nội dung&gt;, /join &lt;phòng&gt;, /find (tìm peer), /loss &lt;%&gt;, /help, /clear"
                    + "</small></html>");
            bottomContainer.add(lblTip, BorderLayout.SOUTH);

            root.add(bottomContainer, BorderLayout.SOUTH);

            setContentPane(root);
        }

        private String getStatusText() {
            return "ID: " + core.getId()
                    + "  |  Máy: " + core.getHostName()
                    + "  |  Multicast: " + GROUP_PREFIX + core.getRoom() + ":" + MC_PORT
                    + "  |  Broadcast: Cổng " + BC_PORT;
        }

        private void handleSendMessage() {
            String text = txtInput.getText().trim();
            if (text.isEmpty()) return;
            txtInput.setText("");

            if (text.startsWith("/")) {
                try {
                    core.executeCommand(text);
                } catch (Exception ex) {
                    appendSystem("Lỗi thực hiện lệnh: " + ex.getMessage(), "ERROR");
                }
            } else {
                int mode = cbSendMode.getSelectedIndex();
                if (mode == 1) { // Broadcast /all
                    core.sendToAll(text);
                } else { // Multicast room
                    core.sendRoomChat(text);
                }
            }
            txtInput.requestFocusInWindow();
        }

        private void clearChat() {
            try {
                htmlDoc = new HTMLDocument();
                chatPane.setDocument(htmlDoc);
            } catch (Exception ignored) { }
        }

        private void appendHtml(String html) {
            SwingUtilities.invokeLater(() -> {
                try {
                    htmlKit.insertHTML(htmlDoc, htmlDoc.getLength(), html, 0, 0, null);
                    if (chkAutoScroll.isSelected()) {
                        chatPane.setCaretPosition(htmlDoc.getLength());
                    }
                } catch (Exception ignored) { }
            });
        }

        public void appendSystem(String text, String level) {
            String color = level.equals("ERROR") ? "#D32F2F" : level.equals("WARN") ? "#E65100" : "#455A64";
            String bg = level.equals("ERROR") ? "#FFEBEE" : level.equals("WARN") ? "#FFF3E0" : "#ECEFF1";
            String label = level.equals("ERROR") ? "LỖI" : level.equals("WARN") ? "CẢNH BÁO" : "HỆ THỐNG";

            String time = timeFmt.format(new Date());
            String html = String.format(
                    "<div style='margin-bottom:4px; font-family:sans-serif; font-size:12px;'>"
                            + "<span style='color:#757575;'>[%s]</span> "
                            + "<span style='background-color:%s; color:%s; font-weight:bold; padding:1px 4px; border:1px solid %s;'>[%s]</span> "
                            + "<span style='color:%s;'>%s</span></div>",
                    time, bg, color, color, label, color, escape(text));
            appendHtml(html);
        }

        // --- ChatListener Implementation ---

        @Override
        public void onRoomMessage(long time, char kind, String who, String text, String note, int r) {
            String t = timeFmt.format(new Date(time));
            String tag = (note == null || note.isEmpty()) ? "" : " <span style='color:#D32F2F; font-size:11px;'>(" + escape(note) + ")</span>";
            String html;

            if (kind == 'C') {
                html = String.format(
                        "<div style='margin-bottom:4px; font-family:sans-serif; font-size:13px;'>"
                                + "<span style='color:#757575; font-size:11px;'>[%s]</span> "
                                + "<span style='background-color:#E3F2FD; color:#0D47A1; font-weight:bold; padding:1px 4px; border:1px solid #90CAF9;'>[Phòng %d]</span> "
                                + "<b style='color:#1565C0;'>%s:</b> <span style='color:#000000;'>%s</span>%s</div>",
                        t, r, escape(who), escape(text), tag);
            } else {
                html = String.format(
                        "<div style='margin-bottom:4px; font-family:sans-serif; font-size:12px; color:#546E7A;'>"
                                + "<span style='color:#757575; font-size:11px;'>[%s]</span> "
                                + "<span style='background-color:#ECEFF1; color:#37474F; font-weight:bold; padding:1px 4px; border:1px solid #CFD8DC;'>[Phòng %d]</span> "
                                + "<i>* %s %s</i>%s</div>",
                        t, r, escape(who), escape(text), tag);
            }
            appendHtml(html);
        }

        @Override
        public void onBroadcastMessage(long time, int seq, String who, String ip, String host, int r, String text, String note) {
            String t = timeFmt.format(new Date(time));
            String tag = (note == null || note.isEmpty()) ? "" : " <span style='color:#D32F2F; font-size:11px;'>(" + escape(note) + ")</span>";

            String html = String.format(
                    "<div style='margin-bottom:4px; font-family:sans-serif; font-size:13px;'>"
                            + "<span style='color:#757575; font-size:11px;'>[%s]</span> "
                            + "<span style='background-color:#FBE9E7; color:#D84315; font-weight:bold; padding:1px 4px; border:1px solid #FFAB91;'>[TẤT CẢ #%d]</span> "
                            + "<b style='color:#D84315;'>%s</b> <span style='color:#616161; font-size:11px;'>(%s, %s, phòng %d):</span> "
                            + "<b style='color:#000000;'>%s</b>%s</div>",
                    t, seq, escape(who), escape(ip), escape(host), r, escape(text), tag);
            appendHtml(html);
        }

        @Override
        public void onSystemMessage(String text, String level) {
            appendSystem(text, level);
        }

        @Override
        public void onPeerUpdated(String id, String ip, String name, String host, String r) {
            SwingUtilities.invokeLater(() -> {
                int found = -1;
                for (int i = 0; i < peerRowIds.size(); i++) {
                    if (peerRowIds.get(i).equals(id)) {
                        found = i;
                        break;
                    }
                }
                if (found >= 0) {
                    peerModel.setValueAt(ip, found, 0);
                    peerModel.setValueAt(name, found, 1);
                    peerModel.setValueAt(r, found, 2);
                    peerModel.setValueAt(host, found, 3);
                } else {
                    peerRowIds.add(id);
                    peerModel.addRow(new Object[]{ip, name, r, host});
                }
                lblPeerCount.setText(peerModel.getRowCount() + " máy online");
            });
        }

        @Override
        public void onPeersCleared() {
            SwingUtilities.invokeLater(() -> {
                peerRowIds.clear();
                peerModel.setRowCount(0);
                lblPeerCount.setText("Đang tìm máy online trong LAN...");
            });
        }

        @Override
        public void onRoomChanged(int newRoom) {
            SwingUtilities.invokeLater(() -> {
                spRoom.setValue(newRoom);
                lblStatus.setText(getStatusText());
            });
        }

        static String escape(String s) {
            if (s == null) return "";
            return s.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
        }
    }

    // ======================================================================
    //                              MAIN
    // ======================================================================

    private static void runCLI(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);
        String name = "";
        while (name.isEmpty()) {
            System.out.print("Nhập tên hiển thị: ");
            if (!sc.hasNextLine()) return;
            name = sc.nextLine().trim();
        }
        String ifArg = null;
        for (String a : args) {
            if (!a.startsWith("-")) { ifArg = a; break; }
        }
        LanChat app = new LanChat(name, ifArg);
        System.out.println("Card mạng sử dụng:");
        for (NetworkInterface ni : app.ifaces) System.out.println("   - " + describe(ni));
        StringJoiner sj = new StringJoiner(", ");
        for (InetAddress b : app.broadcastAddrs) sj.add(b.getHostAddress());
        System.out.println("Broadcast tới: " + sj + " (cổng " + BC_PORT + ")  |  Multicast: " + GROUP_PREFIX + "N (cổng " + MC_PORT + ")");

        app.startServices();
        app.printHelp();
        app.discover();

        while (app.running && sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;
            try {
                if (line.startsWith("/")) app.executeCommand(line);
                else {
                    app.sendData(app.room, 'C', line);
                    app.showRoom(System.currentTimeMillis(), 'C', app.name + " (bạn)", line, null);
                }
            } catch (Exception e) {
                System.out.println("Lỗi: " + e.getMessage());
            }
        }
        app.quit();
    }

    public static void main(String[] args) throws Exception {
        boolean cliMode = false;
        String ifArg = null;
        String nameArg = null;

        for (String a : args) {
            if (a.equalsIgnoreCase("--cli") || a.equalsIgnoreCase("-c")) {
                cliMode = true;
            } else if (a.matches("\\d+\\.\\d+\\.\\d+\\.\\d+") || a.startsWith("eth") || a.startsWith("wlan")) {
                ifArg = a;
            } else if (!a.startsWith("-") && nameArg == null) {
                nameArg = a;
            }
        }

        if (cliMode || GraphicsEnvironment.isHeadless()) {
            runCLI(args);
        } else {
            // Khởi chạy GUI
            String finalIfArg = ifArg;
            String tempName = (nameArg != null && !nameArg.trim().isEmpty())
                    ? nameArg.trim()
                    : System.getProperty("user.name", "User");
            if (tempName == null || tempName.trim().isEmpty()) {
                tempName = "User_" + (new Random().nextInt(900) + 100);
            }
            final String finalName = tempName;

            SwingUtilities.invokeLater(() -> {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) { }

                try {
                    LanChat core = new LanChat(finalName, finalIfArg);
                    GUI gui = new GUI(core);
                    gui.setVisible(true);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null,
                            "Không thể khởi chạy LAN Chat:\n" + e.getMessage(),
                            "Lỗi Khởi Động",
                            JOptionPane.ERROR_MESSAGE);
                    e.printStackTrace();
                }
            });
        }
    }
}
