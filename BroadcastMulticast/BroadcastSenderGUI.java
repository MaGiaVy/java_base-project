import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * BroadcastSenderGUI.java
 * Gửi broadcast tới mạng LAN / Hotspot.
 * Toàn bộ chữ đen trên nền pastel có viền rõ ràng.
 */
public class BroadcastSenderGUI {

    private JTextArea log;
    private DatagramSocket socket;
    private String broadcastIP;
    private int port;
    private String nick;
    private NetworkHelper.CardInfo card;

    public static void launch(String nickname, String bcastIP, int p, String initMsg) {
        launch(nickname, bcastIP, p, initMsg, null);
    }

    public static void launch(String nickname, String bcastIP, int p, String initMsg, String targetCardOrIP) {
        new BroadcastSenderGUI().createAndShow(nickname, bcastIP, p, initMsg, targetCardOrIP);
    }

    public void createAndShow(String nickname, String bcastIP, int p, String initMsg, String targetCardOrIP) {
        this.nick = nickname;
        this.port = p;
        this.broadcastIP = bcastIP;

        try {
            this.card = NetworkHelper.pickCard(targetCardOrIP);
            if ((broadcastIP == null || broadcastIP.equals("255.255.255.255")) && card != null && card.broadcastIp != null) {
                this.broadcastIP = card.broadcastIp;
            }
        } catch (Exception ignored) {}

        JFrame frame = new JFrame("📢 Broadcast SENDER  [" + broadcastIP + ":" + p + "]");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(700, 520);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.setBackground(new Color(0xF4F6F9));

        // Info label
        String cardStr = card != null ? card.displayName + " (" + card.ip + ")" : "Mặc định";
        JLabel info = new JLabel(
                "<html><b style='color:#000000;'>Broadcast Sender</b> &nbsp;|&nbsp; Nick: <b style='color:#000000;'>" + nickname + "</b>" +
                " &nbsp;|&nbsp; Gửi tới: <b style='color:#000000;'>" + broadcastIP + ":" + p + "</b>" +
                "<br><small style='color:#000000;'>Card mạng: <b>" + cardStr + "</b> | DatagramSocket.setBroadcast(true)</small></html>");
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

        // Input row
        JTextField txtMsg = new JTextField(initMsg);
        txtMsg.setFont(new Font("SansSerif", Font.PLAIN, 13));
        txtMsg.setForeground(Color.BLACK);

        JButton btnSend = new JButton("📤 Gửi / Send");
        btnSend.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnSend.setForeground(Color.BLACK);
        btnSend.setBackground(new Color(0xFFE0B2)); // Cam pastel
        btnSend.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE65100), 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        btnSend.setOpaque(true);
        btnSend.setFocusPainted(false);
        btnSend.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JButton btnSendAll = new JButton("📡 Gửi tất cả card");
        btnSendAll.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnSendAll.setForeground(Color.BLACK);
        btnSendAll.setBackground(new Color(0xBBDEFB)); // Xanh dương pastel
        btnSendAll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x1565C0), 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        btnSendAll.setOpaque(true);
        btnSendAll.setFocusPainted(false);
        btnSendAll.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.setBackground(new Color(0xF4F6F9));
        inputRow.add(txtMsg, BorderLayout.CENTER);
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btnRow.setBackground(new Color(0xF4F6F9));
        btnRow.add(btnSendAll);
        btnRow.add(btnSend);
        inputRow.add(btnRow, BorderLayout.EAST);
        root.add(inputRow, BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.setVisible(true);

        // Init socket
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            if (card != null) {
                appendLog("[OK] Đang ưu tiên card: " + card.displayName + " (" + card.ip + ")"
                        + (card.isHotspot ? " ★ [MOBILE HOTSPOT]" : ""));
            }
            appendLog("[OK] Socket mở sẵn sàng. Đích gửi mặc định: " + broadcastIP + ":" + port);
        } catch (Exception e) {
            appendLog("[LỖI KHỞI TẠO] " + e.getMessage());
        }

        // Send to 1 IP
        btnSend.addActionListener(e -> {
            String msg = txtMsg.getText().trim();
            if (msg.isEmpty()) return;
            try {
                sendTo(broadcastIP, port, nick + ": " + msg);
                appendLog("[GỬI → " + broadcastIP + ":" + port + "] " + nick + ": " + msg);
            } catch (Exception ex) {
                appendLog("[LỖI GỬI] " + ex.getMessage());
            }
        });

        // Send to all network interfaces
        btnSendAll.addActionListener(e -> {
            String msg = txtMsg.getText().trim();
            if (msg.isEmpty()) return;
            try {
                int sent = sendToAllInterfaces(port, nick + ": " + msg);
                if (sent == 0) appendLog("[CẢNH BÁO] Không tìm thấy card nào có địa chỉ broadcast.");
            } catch (Exception ex) {
                appendLog("[LỖI GỬI TẤT CẢ] " + ex.getMessage());
            }
        });

        // Enter key
        txtMsg.addActionListener(e -> btnSend.doClick());

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent we) {
                if (socket != null && !socket.isClosed()) socket.close();
            }
        });
    }

    private void sendTo(String ip, int p, String msg) throws Exception {
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        InetAddress addr = InetAddress.getByName(ip);
        socket.send(new DatagramPacket(data, data.length, addr, p));
    }

    private int sendToAllInterfaces(int p, String msg) throws Exception {
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        int sent = 0;
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback()) continue;
            for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                InetAddress bc = ia.getBroadcast();
                if (bc == null) continue;
                socket.send(new DatagramPacket(data, data.length, bc, p));
                appendLog("[GỬI → " + bc.getHostAddress() + ":" + p
                        + " (" + ni.getDisplayName() + ")] " + msg);
                sent++;
            }
        }
        return sent;
    }

    private void appendLog(String text) {
        SwingUtilities.invokeLater(() -> {
            log.append(text + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    // Console mode
    public static void sendConsole(String bcastIP, int port, String msg) throws Exception {
        try (DatagramSocket s = new DatagramSocket()) {
            s.setBroadcast(true);
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            s.send(new DatagramPacket(data, data.length, InetAddress.getByName(bcastIP), port));
            System.out.println("[GỬI] Broadcast → " + bcastIP + ":" + port + " | " + msg);
        }
    }
}
