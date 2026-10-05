import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * BroadcastSenderGUI.java
 * Gửi broadcast tới mạng LAN / Hotspot.
 * Hỗ trợ tự tính và điền sẵn địa chỉ directed broadcast (vd 192.168.137.255 cho Mobile Hotspot).
 */
public class BroadcastSenderGUI {

    static JTextArea log;
    static DatagramSocket socket;
    static String broadcastIP;
    static int port;
    static String nick;
    static NetworkHelper.CardInfo card;

    public static void launch(String nickname, String bcastIP, int p, String initMsg) {
        launch(nickname, bcastIP, p, initMsg, null);
    }

    public static void launch(String nickname, String bcastIP, int p, String initMsg, String targetCardOrIP) {
        nick = nickname;
        port = p;
        broadcastIP = bcastIP;

        try {
            card = NetworkHelper.pickCard(targetCardOrIP);
            // Nếu người dùng để 255.255.255.255 mà card có subnet broadcast riêng (vd 192.168.137.255), gợi ý dùng subnet broadcast
            if ((broadcastIP == null || broadcastIP.equals("255.255.255.255")) && card != null && card.broadcastIp != null) {
                broadcastIP = card.broadcastIp;
            }
        } catch (Exception ignored) {}

        JFrame frame = new JFrame("📢 Broadcast SENDER  [" + broadcastIP + ":" + p + "]");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(680, 500);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.setBackground(new Color(0xE3F2FD));

        // Info label
        String cardStr = card != null ? card.displayName + " (" + card.ip + ")" : "Mặc định";
        JLabel info = new JLabel(
                "<html><b>Broadcast Sender</b> &nbsp;|&nbsp; Nick: <b>" + nickname + "</b>" +
                " &nbsp;|&nbsp; Gửi tới: <b>" + broadcastIP + ":" + p + "</b>" +
                "<br><small>Card mạng: <b>" + cardStr + "</b> | DatagramSocket.setBroadcast(true)</small></html>");
        info.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        root.add(info, BorderLayout.NORTH);

        // Log area
        log = new JTextArea();
        log.setEditable(false);
        log.setFont(new Font("Monospaced", Font.PLAIN, 13));
        log.setBackground(new Color(0xFAFAFA));
        JScrollPane scroll = new JScrollPane(log);
        root.add(scroll, BorderLayout.CENTER);

        // Input row
        JTextField txtMsg = new JTextField(initMsg);
        JButton btnSend = new JButton("📤 Gửi / Send");
        JButton btnSendAll = new JButton("📡 Gửi tất cả card");
        btnSend.setBackground(new Color(0xE65100));
        btnSend.setForeground(Color.WHITE);
        btnSend.setOpaque(true); btnSend.setFocusPainted(false);
        btnSendAll.setBackground(new Color(0x1565C0));
        btnSendAll.setForeground(Color.WHITE);
        btnSendAll.setOpaque(true); btnSendAll.setFocusPainted(false);

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.setBackground(new Color(0xE3F2FD));
        inputRow.add(txtMsg, BorderLayout.CENTER);
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btnRow.setBackground(new Color(0xE3F2FD));
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

    static void sendTo(String ip, int p, String msg) throws Exception {
        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
        InetAddress addr = InetAddress.getByName(ip);
        socket.send(new DatagramPacket(data, data.length, addr, p));
    }

    static int sendToAllInterfaces(int p, String msg) throws Exception {
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

    static void appendLog(String text) {
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
