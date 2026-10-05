import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * MulticastReceiverGUI.java
 * Nhận multicast — BẮT BUỘC phải joinGroup(). Dùng MulticastSocket.
 * Receiver tự động rời nhóm (leaveGroup) khi đóng cửa sổ.
 */
public class MulticastReceiverGUI {

    static JTextArea log;
    static volatile boolean running = false;
    static MulticastSocket socket;
    static InetSocketAddress groupAddr;
    static NetworkInterface nif;

    public static void launch(String nickname, String groupIP, int port) {
        JFrame frame = new JFrame("📡 Multicast RECEIVER  [" + groupIP + ":" + port + "]");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(660, 480);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.setBackground(new Color(0xF3E5F5));

        // Info label
        JLabel info = new JLabel(
                "<html><b>Multicast Receiver</b> &nbsp;|&nbsp; Nick: <b>" + nickname + "</b>" +
                " &nbsp;|&nbsp; Nhóm: <b>" + groupIP + ":" + port + "</b>" +
                "<br><small>MulticastSocket + <b>joinGroup()</b> — chỉ nhận khi đã tham gia nhóm</small></html>");
        info.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        root.add(info, BorderLayout.NORTH);

        // Log area
        log = new JTextArea();
        log.setEditable(false);
        log.setFont(new Font("Monospaced", Font.PLAIN, 13));
        log.setBackground(new Color(0xFAFAFA));
        root.add(new JScrollPane(log), BorderLayout.CENTER);

        // Bottom row
        JButton btnJoin  = new JButton("▶ Tham gia nhóm / Join Group");
        JButton btnLeave = new JButton("⏹ Rời nhóm / Leave Group");
        JButton btnClear = new JButton("🗑 Xóa log");

        btnJoin.setBackground(new Color(0x6A1B9A));
        btnJoin.setForeground(Color.WHITE);
        btnJoin.setOpaque(true); btnJoin.setFocusPainted(false);

        btnLeave.setBackground(new Color(0xC62828));
        btnLeave.setForeground(Color.WHITE);
        btnLeave.setOpaque(true); btnLeave.setFocusPainted(false);
        btnLeave.setEnabled(false);

        JPanel btmPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btmPanel.setBackground(new Color(0xF3E5F5));
        btmPanel.add(btnJoin);
        btmPanel.add(btnLeave);
        btmPanel.add(btnClear);
        root.add(btmPanel, BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.setVisible(true);

        // Join
        btnJoin.addActionListener(e -> {
            btnJoin.setEnabled(false);
            btnLeave.setEnabled(true);
            startReceiving(groupIP, port);
        });

        // Leave
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

    static void startReceiving(String groupIP, int port) {
        running = true;
        appendLog("[OK] Đang tham gia nhóm " + groupIP + ":" + port + "...");

        Thread t = new Thread(() -> {
            try {
                InetAddress group = InetAddress.getByName(groupIP);
                groupAddr = new InetSocketAddress(group, port);
                nif = MulticastSenderGUI.pickBestInterface();

                socket = new MulticastSocket(port);
                socket.setNetworkInterface(nif);
                socket.joinGroup(groupAddr, nif);  // ← BẮT BUỘC phải joinGroup

                appendLog("[OK] ✅ Đã joinGroup " + groupIP + " | Card: " + nif.getDisplayName());
                appendLog("[OK] Đang chờ tin nhắn từ nhóm... (chỉ nhận từ thành viên đã gửi multicast)");

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
                appendLog("[DỪNG] Đã rời nhóm / leaveGroup.");
            }
        });
        t.setDaemon(true);
        t.start();
    }

    static void stopReceiving() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            try {
                if (groupAddr != null && nif != null)
                    socket.leaveGroup(groupAddr, nif);  // ← leaveGroup trước khi đóng
            } catch (Exception ignored) {}
            socket.close();
        }
    }

    static void appendLog(String text) {
        SwingUtilities.invokeLater(() -> {
            log.append(text + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    // Console mode
    public static void receiveConsole(String groupIP, int port) throws Exception {
        InetAddress group = InetAddress.getByName(groupIP);
        InetSocketAddress gAddr = new InetSocketAddress(group, port);
        System.out.println("[OK] Tham gia nhóm " + groupIP + ":" + port + "... (Ctrl+C để dừng)");
        try (MulticastSocket s = new MulticastSocket(port)) {
            // Tự chọn interface tốt nhất
            try {
                NetworkInterface ni = MulticastSenderGUI.pickBestInterface();
                s.joinGroup(gAddr, ni);
                System.out.println("[OK] Card: " + ni.getDisplayName());
            } catch (Exception e) {
                s.joinGroup(gAddr, null);
            }
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
