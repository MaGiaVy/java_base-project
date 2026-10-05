import javax.swing.*;
import java.awt.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * MulticastSenderGUI.java
 * Gửi multicast tới địa chỉ nhóm lớp D.
 * Sender KHÔNG cần joinGroup. Dùng MulticastSocket + setTimeToLive.
 */
public class MulticastSenderGUI {

    static JTextArea log;
    static MulticastSocket socket;
    static NetworkInterface nif;
    static InetAddress groupAddr;
    static int port;
    static String nick;

    public static void launch(String nickname, String groupIP, int p, String initMsg) {
        nick = nickname;
        port = p;

        JFrame frame = new JFrame("📡 Multicast SENDER  [" + groupIP + ":" + p + "]");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(660, 520);
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.setBackground(new Color(0xFFF3E0));

        // Info label
        JLabel info = new JLabel(
                "<html><b>Multicast Sender</b> &nbsp;|&nbsp; Nick: <b>" + nickname + "</b>" +
                " &nbsp;|&nbsp; Nhóm: <b>" + groupIP + ":" + p + "</b>" +
                "<br><small>MulticastSocket + setTimeToLive(1) — chỉ gửi tới thành viên đã joinGroup</small></html>");
        info.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        root.add(info, BorderLayout.NORTH);

        // Log area
        log = new JTextArea();
        log.setEditable(false);
        log.setFont(new Font("Monospaced", Font.PLAIN, 13));
        log.setBackground(new Color(0xFAFAFA));
        root.add(new JScrollPane(log), BorderLayout.CENTER);

        // Input row
        JTextField txtMsg = new JTextField(initMsg);
        JButton btnSend  = new JButton("📤 Gửi tới nhóm / Send to group");
        btnSend.setBackground(new Color(0xE65100));
        btnSend.setForeground(Color.WHITE);
        btnSend.setOpaque(true); btnSend.setFocusPainted(false);

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.setBackground(new Color(0xFFF3E0));
        inputRow.add(txtMsg, BorderLayout.CENTER);
        inputRow.add(btnSend, BorderLayout.EAST);
        root.add(inputRow, BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.setVisible(true);

        // Init socket
        try {
            groupAddr = InetAddress.getByName(groupIP);
            nif = pickBestInterface();
            socket = new MulticastSocket();
            socket.setNetworkInterface(nif);
            socket.setTimeToLive(1); // chỉ trong LAN, không qua router
            appendLog("[OK] Socket mở. Card mạng: " + nif.getDisplayName());
            appendLog("[OK] Gửi tới nhóm: " + groupIP + ":" + p + " | TTL=1 (LAN only)");
        } catch (Exception e) {
            appendLog("[LỖI] " + e.getMessage());
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
                appendLog("[LỖI] " + ex.getMessage());
            }
        });

        txtMsg.addActionListener(e -> btnSend.doClick());

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent we) {
                if (socket != null && !socket.isClosed()) socket.close();
            }
        });
    }

    /** Tự động chọn card WiFi, bỏ card ảo */
    static NetworkInterface pickBestInterface() throws SocketException {
        String[] bad  = {"vmware","virtualbox","vethernet","hyper-v","docker","wsl","tap","tun","vpn","bluetooth","virtual"};
        String[] good = {"wi-fi","wifi","wlan","wireless","en0"};
        java.util.List<NetworkInterface> cands = new ArrayList<>();

        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback() || !ni.supportsMulticast()) continue;
            boolean has4 = false;
            for (InetAddress a : Collections.list(ni.getInetAddresses()))
                if (a instanceof Inet4Address) { has4 = true; break; }
            if (!has4) continue;
            cands.add(ni);
        }
        if (cands.isEmpty()) throw new SocketException("Không có card mạng hỗ trợ multicast.");

        for (NetworkInterface ni : cands) {
            String n = (ni.getName() + " " + ni.getDisplayName()).toLowerCase();
            if (containsAny(n, bad)) continue;
            if (containsAny(n, good)) return ni;
        }
        // fallback: bỏ card ảo nếu có
        for (NetworkInterface ni : cands) {
            String n = (ni.getName() + " " + ni.getDisplayName()).toLowerCase();
            if (!containsAny(n, bad)) return ni;
        }
        return cands.get(0);
    }

    static boolean containsAny(String s, String[] keys) {
        for (String k : keys) if (s.contains(k)) return true;
        return false;
    }

    static void appendLog(String text) {
        SwingUtilities.invokeLater(() -> {
            log.append(text + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    // Console mode
    public static void sendConsole(String groupIP, int port, String msg) throws Exception {
        InetAddress group = InetAddress.getByName(groupIP);
        try (MulticastSocket s = new MulticastSocket()) {
            s.setTimeToLive(1);
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            s.send(new DatagramPacket(data, data.length, group, port));
            System.out.println("[GỬI] Multicast → " + groupIP + ":" + port + " | " + msg);
        }
    }
}
