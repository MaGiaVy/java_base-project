import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * BroadcastReceiverGUI.java
 * Nhận broadcast trên cổng đã chỉ định.
 * Toàn bộ chữ đen trên nền pastel có viền rõ nét.
 */
public class BroadcastReceiverGUI {

    private JTextArea log;
    private volatile boolean running = false;
    private DatagramSocket socket;
    private NetworkHelper.CardInfo card;

    public static void launch(String nickname, int port) {
        launch(nickname, port, null);
    }

    public static void launch(String nickname, int port, String targetCardOrIP) {
        new BroadcastReceiverGUI().createAndShow(nickname, port, targetCardOrIP);
    }

    public void createAndShow(String nickname, int port, String targetCardOrIP) {
        try {
            this.card = NetworkHelper.pickCard(targetCardOrIP);
        } catch (Exception ignored) {}

        JFrame frame = new JFrame("📢 Broadcast RECEIVER  [Port:" + port + "]");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(700, 500);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.setBackground(new Color(0xF4F6F9));

        // Info label
        String cardStr = card != null ? card.displayName + " (" + card.ip + ")" : "Mọi card";
        JLabel info = new JLabel(
                "<html><b style='color:#000000;'>Broadcast Receiver</b> &nbsp;|&nbsp; Nick: <b style='color:#000000;'>" + nickname + "</b>" +
                " &nbsp;|&nbsp; Lắng nghe cổng: <b style='color:#000000;'>" + port + "</b>" +
                "<br><small style='color:#000000;'>Card ưu tiên: <b>" + cardStr + "</b> (nhận mọi broadcast tới cổng này)</small></html>");
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
        JButton btnStart = new JButton("▶ Bắt đầu nhận / Start");
        btnStart.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnStart.setForeground(Color.BLACK);
        btnStart.setBackground(new Color(0xC8E6C9)); // Xanh lá pastel
        btnStart.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x2E7D32), 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        btnStart.setOpaque(true);
        btnStart.setFocusPainted(false);
        btnStart.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JButton btnStop  = new JButton("⏹ Dừng / Stop");
        btnStop.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnStop.setForeground(Color.BLACK);
        btnStop.setBackground(new Color(0xFFCDD2)); // Đỏ pastel
        btnStop.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xC62828), 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        btnStop.setOpaque(true);
        btnStop.setFocusPainted(false);
        btnStop.setEnabled(false);
        btnStop.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JButton btnClear = new JButton("🗑 Xóa log");
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
        btmPanel.add(btnStart);
        btmPanel.add(btnStop);
        btmPanel.add(btnClear);
        root.add(btmPanel, BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.setVisible(true);

        // Start/Stop actions
        btnStart.addActionListener(e -> {
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            startReceiving(port);
        });

        btnStop.addActionListener(e -> {
            stopReceiving();
            btnStop.setEnabled(false);
            btnStart.setEnabled(true);
        });

        btnClear.addActionListener(e -> log.setText(""));

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent we) { stopReceiving(); }
        });
    }

    private void startReceiving(int port) {
        running = true;
        appendLog("[OK] Bắt đầu lắng nghe Broadcast tại cổng " + port + "...");
        if (card != null) {
            appendLog("[OK] Card mạng ưu tiên: " + card.displayName + " (" + card.ip + ")"
                    + (card.isHotspot ? " ★ [MOBILE HOTSPOT]" : ""));
        }

        Thread t = new Thread(() -> {
            try {
                socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(port));
                socket.setBroadcast(true);

                appendLog("[OK] Socket đã bind cổng " + port + " (sẵn sàng nhận gói broadcast).");

                byte[] buf = new byte[4096];
                while (running) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    socket.receive(p);
                    String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                    String from = p.getAddress().getHostAddress() + ":" + p.getPort();
                    appendLog("[NHẬN ← " + from + "] " + msg);
                }
            } catch (Exception e) {
                if (running) appendLog("[LỖI] " + e.getMessage());
            } finally {
                appendLog("[DỪNG] Không còn nhận broadcast.");
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void stopReceiving() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
    }

    private void appendLog(String text) {
        SwingUtilities.invokeLater(() -> {
            log.append(text + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    // Console mode
    public static void receiveConsole(int port) throws Exception {
        System.out.println("[OK] Lắng nghe Broadcast tại cổng " + port + "... (Ctrl+C để dừng)");
        try (DatagramSocket socket = new DatagramSocket(null)) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(port));
            socket.setBroadcast(true);
            byte[] buf = new byte[4096];
            while (true) {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8);
                System.out.println("[NHẬN ← " + p.getAddress().getHostAddress()
                        + ":" + p.getPort() + "] " + msg);
            }
        }
    }
}
