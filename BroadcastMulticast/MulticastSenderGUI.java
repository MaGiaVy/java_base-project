import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * MulticastSenderGUI.java
 * Gửi multicast tới địa chỉ nhóm lớp D.
 * Toàn bộ chữ đen trên nền pastel có viền rõ ràng.
 */
public class MulticastSenderGUI {

    private JTextArea log;
    private MulticastSocket socket;
    private NetworkHelper.CardInfo card;
    private InetAddress groupAddr;
    private int port;
    private String nick;

    public static void launch(String nickname, String groupIP, int p, String initMsg) {
        launch(nickname, groupIP, p, initMsg, null);
    }

    public static void launch(String nickname, String groupIP, int p, String initMsg, String targetCardOrIP) {
        new MulticastSenderGUI().createAndShow(nickname, groupIP, p, initMsg, targetCardOrIP);
    }

    public void createAndShow(String nickname, String groupIP, int p, String initMsg, String targetCardOrIP) {
        this.nick = nickname;
        this.port = p;

        JFrame frame = new JFrame("📡 Multicast SENDER  [" + groupIP + ":" + p + "]");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(700, 520);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.setBackground(new Color(0xF4F6F9));

        // Info label
        JLabel info = new JLabel(
                "<html><b style='color:#000000;'>Multicast Sender</b> &nbsp;|&nbsp; Nick: <b style='color:#000000;'>" + nickname + "</b>" +
                " &nbsp;|&nbsp; Nhóm: <b style='color:#000000;'>" + groupIP + ":" + p + "</b>" +
                "<br><small style='color:#000000;'>MulticastSocket + setTimeToLive(1) — phát cho thành viên đã joinGroup</small></html>");
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

        JButton btnSend  = new JButton("📤 Gửi tới nhóm / Send to group");
        btnSend.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnSend.setForeground(Color.BLACK);
        btnSend.setBackground(new Color(0xFFE0B2)); // Cam pastel
        btnSend.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE65100), 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        btnSend.setOpaque(true);
        btnSend.setFocusPainted(false);
        btnSend.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.setBackground(new Color(0xF4F6F9));
        inputRow.add(txtMsg, BorderLayout.CENTER);
        inputRow.add(btnSend, BorderLayout.EAST);
        root.add(inputRow, BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.setVisible(true);

        // Init socket
        try {
            groupAddr = InetAddress.getByName(groupIP);
            card = NetworkHelper.pickCard(targetCardOrIP);

            socket = new MulticastSocket();
            socket.setNetworkInterface(card.nif);
            socket.setTimeToLive(1); // chỉ trong LAN/Hotspot, không qua router

            String hsTag = card.isHotspot ? " ★ [MOBILE HOTSPOT]" : "";
            appendLog("[OK] Đang dùng card: " + card.displayName + " (" + card.ip + ")" + hsTag);
            appendLog("[OK] Đã gắn card mạng cho MulticastSocket thành công.");
            appendLog("[OK] Đích đến: " + groupIP + ":" + port + " | TTL=1 (LAN only)");
        } catch (Exception e) {
            appendLog("[LỖI KHỞI TẠO] " + e.getMessage());
        }

        btnSend.addActionListener(e -> {
            String msg = txtMsg.getText().trim();
            if (msg.isEmpty()) return;
            try {
                String full = nick + ": " + msg;
                byte[] data = full.getBytes(StandardCharsets.UTF_8);
                socket.send(new DatagramPacket(data, data.length, groupAddr, port));
                appendLog("[GỬI → " + groupAddr.getHostAddress() + ":" + port + "] " + full);
            } catch (Exception ex) {
                appendLog("[LỖI GỬI] " + ex.getMessage());
            }
        });

        txtMsg.addActionListener(e -> btnSend.doClick());

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent we) {
                if (socket != null && !socket.isClosed()) socket.close();
            }
        });
    }

    private void appendLog(String text) {
        SwingUtilities.invokeLater(() -> {
            log.append(text + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    // Console mode
    public static void sendConsole(String groupIP, int port, String msg) throws Exception {
        InetAddress group = InetAddress.getByName(groupIP);
        NetworkHelper.CardInfo c = NetworkHelper.pickCard(null);
        System.out.println("[OK] Dùng card: " + c.displayName + " (" + c.ip + ")");
        try (MulticastSocket s = new MulticastSocket()) {
            s.setNetworkInterface(c.nif);
            s.setTimeToLive(1);
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            s.send(new DatagramPacket(data, data.length, group, port));
            System.out.println("[GỬI] Multicast → " + groupIP + ":" + port + " | " + msg);
        }
    }
}
