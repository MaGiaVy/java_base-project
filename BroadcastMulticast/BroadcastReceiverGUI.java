import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * BroadcastReceiverGUI.java
 * Nhận broadcast trên cổng đã chỉ định.
 * Không cần joinGroup, mở socket trên cổng và bind đúng interface/mọi interface.
 */
public class BroadcastReceiverGUI {

    static JTextArea log;
    static volatile boolean running = false;
    static DatagramSocket socket;
    static NetworkHelper.CardInfo card;

    public static void launch(String nickname, int port) {
        launch(nickname, port, null);
    }

    public static void launch(String nickname, int port, String targetCardOrIP) {
        try {
            card = NetworkHelper.pickCard(targetCardOrIP);
        } catch (Exception ignored) {}

        JFrame frame = new JFrame("📢 Broadcast RECEIVER  [Port:" + port + "]");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(680, 480);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.setBackground(new Color(0xE8F5E9));

        // Info label
        String cardStr = card != null ? card.displayName + " (" + card.ip + ")" : "Mọi card";
        JLabel info = new JLabel(
                "<html><b>Broadcast Receiver</b> &nbsp;|&nbsp; Nick: <b>" + nickname + "</b>" +
                " &nbsp;|&nbsp; Lắng nghe cổng: <b>" + port + "</b>" +
                "<br><small>Card ưu tiên: <b>" + cardStr + "</b> (nhận mọi broadcast tới cổng này)</small></html>");
        info.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        root.add(info, BorderLayout.NORTH);

        // Log area
        log = new JTextArea();
        log.setEditable(false);
        log.setFont(new Font("Monospaced", Font.PLAIN, 13));
        log.setBackground(new Color(0xFAFAFA));
        JScrollPane scroll = new JScrollPane(log);
        root.add(scroll, BorderLayout.CENTER);

        // Bottom row
        JButton btnStart = new JButton("▶ Bắt đầu nhận / Start");
        JButton btnStop  = new JButton("⏹ Dừng / Stop");
        JButton btnClear = new JButton("🗑 Xóa log");
        btnStart.setBackground(new Color(0x2E7D32));
        btnStart.setForeground(Color.WHITE);
        btnStart.setOpaque(true); btnStart.setFocusPainted(false);
        btnStop.setBackground(new Color(0xC62828));
        btnStop.setForeground(Color.WHITE);
        btnStop.setOpaque(true); btnStop.setFocusPainted(false);
        btnStop.setEnabled(false);

        JPanel btmPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btmPanel.setBackground(new Color(0xE8F5E9));
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

    static void startReceiving(int port) {
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
                // Bind wildcard 0.0.0.0 để nhận broadcast từ bất kỳ card nào (kể cả hotspot)
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

    static void stopReceiving() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
    }

    static void appendLog(String text) {
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
