import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * MulticastReceiverGUI.java
 * Nhận multicast — BẮT BUỘC phải joinGroup() trên đúng card mạng.
 * Toàn bộ chữ đen trên nền pastel có viền rõ ràng.
 */
public class MulticastReceiverGUI {

    private JTextArea log;
    private volatile boolean running = false;
    private MulticastSocket socket;
    private InetSocketAddress groupAddr;
    private NetworkHelper.CardInfo card;

    public static void launch(String nickname, String groupIP, int port) {
        launch(nickname, groupIP, port, null);
    }

    public static void launch(String nickname, String groupIP, int port, String targetCardOrIP) {
        new MulticastReceiverGUI().createAndShow(nickname, groupIP, port, targetCardOrIP);
    }

    public void createAndShow(String nickname, String groupIP, int port, String targetCardOrIP) {
        JFrame frame = new JFrame("📡 Multicast RECEIVER  [" + groupIP + ":" + port + "]");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(700, 500);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.setBackground(new Color(0xF4F6F9));

        // Info label
        JLabel info = new JLabel(
                "<html><b style='color:#000000;'>Multicast Receiver</b> &nbsp;|&nbsp; Nick: <b style='color:#000000;'>" + nickname + "</b>" +
                " &nbsp;|&nbsp; Nhóm: <b style='color:#000000;'>" + groupIP + ":" + port + "</b>" +
                "<br><small style='color:#000000;'>MulticastSocket + <b>joinGroup()</b> — chỉ nhận khi đã tham gia nhóm</small></html>");
        info.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        root.add(info, BorderLayout.NORTH);

        // Log area
        log = new JTextArea();
        log.setEditable(false);
        log.setFont(new Font("Monospaced", Font.PLAIN, 13));
        log.setForeground(Color.BLACK);
        log.setBackground(Color.WHITE);
        JScrollPane scroll = new JScrollPane(log);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(0x607D8B), 1));
        root.add(scroll, BorderLayout.CENTER);

        // Bottom row
        JButton btnJoin  = new JButton("▶ Tham gia nhóm / Join Group");
        JButton btnLeave = new JButton("⏹ Rời nhóm / Leave Group");
        JButton btnClear = new JButton("🗑 Xóa log");

        btnJoin.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnJoin.setForeground(Color.BLACK);
        btnJoin.setBackground(new Color(0xE1BEE7)); // Tím pastel
        btnJoin.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x6A1B9A), 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        btnJoin.setOpaque(true);
        btnJoin.setFocusPainted(false);
        btnJoin.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btnLeave.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnLeave.setForeground(Color.BLACK);
        btnLeave.setBackground(new Color(0xFFCDD2)); // Đỏ pastel
        btnLeave.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xC62828), 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        btnLeave.setOpaque(true);
        btnLeave.setFocusPainted(false);
        btnLeave.setEnabled(false);
        btnLeave.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btnClear.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnClear.setForeground(Color.BLACK);
        btnClear.setBackground(new Color(0xEEEEEE)); // Xám pastel
        btnClear.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x757575), 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        btnClear.setOpaque(true);
        btnClear.setFocusPainted(false);
        btnClear.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel btmPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btmPanel.setBackground(new Color(0xF4F6F9));
        btmPanel.add(btnJoin);
        btmPanel.add(btnLeave);
        btmPanel.add(btnClear);
        root.add(btmPanel, BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.setVisible(true);

        // Nút Join
        btnJoin.addActionListener(e -> {
            btnJoin.setEnabled(false);
            btnLeave.setEnabled(true);
            startReceiving(groupIP, port, targetCardOrIP);
        });

        // Nút Leave
        btnLeave.addActionListener(e -> {
            stopReceiving();
            btnLeave.setEnabled(false);
            btnJoin.setEnabled(true);
        });

        btnClear.addActionListener(e -> log.setText(""));

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent we) { stopReceiving(); }
        });
    }

    private void startReceiving(String groupIP, int port, String targetCardOrIP) {
        running = true;
        appendLog("[OK] Đang chuẩn bị tham gia nhóm " + groupIP + ":" + port + "...");

        Thread t = new Thread(() -> {
            try {
                InetAddress group = InetAddress.getByName(groupIP);
                groupAddr = new InetSocketAddress(group, port);
                card = NetworkHelper.pickCard(targetCardOrIP);

                String hsTag = card.isHotspot ? " ★ [MOBILE HOTSPOT]" : "";
                appendLog("[OK] Đang dùng card: " + card.displayName + " (" + card.ip + ")" + hsTag);

                socket = new MulticastSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(port));

                if (card != null && card.nif != null) {
                    try { socket.setNetworkInterface(card.nif); } catch (Exception ignored) {}
                }

                if (card != null && card.nif != null) {
                    socket.joinGroup(groupAddr, card.nif);
                } else {
                    socket.joinGroup(groupAddr, null);
                }

                appendLog("[OK] ✅ Đã joinGroup(" + groupIP + ") trên card: " + card.displayName);
                appendLog("[OK] Đang lắng nghe tin nhắn multicast... (chỉ nhận từ thành viên đã gửi tới nhóm này)");

                byte[] buf = new byte[4096];
                while (running) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    socket.receive(p);
                    String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                    String from = p.getAddress().getHostAddress();
                    appendLog("[NHẬN ← " + from + "] " + msg);
                }
            } catch (Exception e) {
                if (running) appendLog("[LỖI] " + e.getMessage());
            } finally {
                appendLog("[DỪNG] Đã rời nhóm / socket đóng.");
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void stopReceiving() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            try {
                if (groupAddr != null && card != null && card.nif != null) {
                    socket.leaveGroup(groupAddr, card.nif);
                }
            } catch (Exception ignored) {}
            socket.close();
        }
    }

    private void appendLog(String text) {
        SwingUtilities.invokeLater(() -> {
            log.append(text + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    // Console mode
    public static void receiveConsole(String groupIP, int port) throws Exception {
        InetAddress group = InetAddress.getByName(groupIP);
        InetSocketAddress gAddr = new InetSocketAddress(group, port);
        NetworkHelper.CardInfo c = NetworkHelper.pickCard(null);
        System.out.println("[OK] Dùng card: " + c.displayName + " (" + c.ip + ")");
        System.out.println("[OK] Tham gia nhóm " + groupIP + ":" + port + "... (Ctrl+C để dừng)");

        try (MulticastSocket s = new MulticastSocket(null)) {
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(port));
            if (c.nif != null) {
                s.setNetworkInterface(c.nif);
                s.joinGroup(gAddr, c.nif);
            } else {
                s.joinGroup(gAddr, null);
            }
            System.out.println("[OK] Đã joinGroup! Đang chờ tin nhắn...");

            byte[] buf = new byte[4096];
            while (true) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                s.receive(p);
                String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                System.out.println("[NHẬN ← " + p.getAddress().getHostAddress() + "] " + msg);
            }
        }
    }
}
