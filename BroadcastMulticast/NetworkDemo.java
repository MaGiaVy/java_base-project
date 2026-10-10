import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.List;

/**
 * NetworkDemo.java - Launcher tổng hợp UDP Broadcast & Multicast
 * Tự động nhận diện và ưu tiên card Mobile Hotspot (192.168.137.x / Local Area Connection*).
 */
public class NetworkDemo {

    static final Color C_BROADCAST = new Color(0x1565C0);
    static final Color C_MULTICAST  = new Color(0x2E7D32);
    static final Color C_SENDER     = new Color(0xE65100);
    static final Color C_RECEIVER   = new Color(0x6A1B9A);
    static final Color C_BG         = new Color(0xF5F5F5);
    static final Color C_TEXT       = Color.WHITE;

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            consoleMenu(args);
            return;
        }
        UnifiedChatGUI.main(args);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MAIN MENU: Chọn Broadcast hay Multicast
    // ═══════════════════════════════════════════════════════════════════════════

    static void showMainMenu(String[] args) {
        JFrame frame = new JFrame("📡 Broadcast & Multicast Demo");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(620, 480);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(C_BG);
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JLabel title = new JLabel("Chọn chế độ truyền dữ liệu / Select Mode", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        root.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(3, 1, 0, 10));
        center.setBackground(C_BG);

        JButton btnUnified = bigButton("💬 PHÒNG CHAT HỢP NHẤT (KHUYÊN DÙNG)",
                "<html><center>Gộp <b>Broadcast & Multicast</b> trong 1 đoạn chat duy nhất (Gửi & Nhận 2 chiều)</center></html>",
                new Color(0x00796B));

        JButton btnBroadcast = bigButton("📢 BROADCAST RIÊNG",
                "<html><center>Gửi tới <b>mọi máy</b> trong mạng<br><small>DatagramSocket + setBroadcast(true)</small></center></html>",
                C_BROADCAST);
        JButton btnMulticast = bigButton("📡 MULTICAST RIÊNG",
                "<html><center>Gửi tới <b>nhóm đã đăng ký</b><br><small>MulticastSocket + joinGroup</small></center></html>",
                C_MULTICAST);

        center.add(btnUnified);
        center.add(btnBroadcast);
        center.add(btnMulticast);
        root.add(center, BorderLayout.CENTER);

        btnUnified.addActionListener(e -> { frame.dispose(); UnifiedChatGUI.main(new String[0]); });

        // Hiển thị card mạng hotspot được phát hiện
        List<NetworkHelper.CardInfo> cards = NetworkHelper.getAvailableCards();
        String detectedNotice = "<html><center><small>💡 Broadcast = loa phóng thanh | Multicast = group chat<br>";
        if (!cards.isEmpty() && cards.get(0).isHotspot) {
            detectedNotice += "<b style='color:#D84315'>🔥 Đã tự nhận diện Mobile Hotspot: " + cards.get(0).ip + " (" + cards.get(0).displayName + ")</b>";
        } else if (!cards.isEmpty()) {
            detectedNotice += "<span style='color:gray'>Card mặc định: " + cards.get(0).ip + " (" + cards.get(0).displayName + ")</span>";
        }
        detectedNotice += "</small></center></html>";

        JLabel footer = new JLabel(detectedNotice, SwingConstants.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        frame.setContentPane(root);

        btnBroadcast.addActionListener(e -> { frame.dispose(); showRoleMenu("BROADCAST"); });
        btnMulticast.addActionListener(e -> { frame.dispose(); showRoleMenu("MULTICAST"); });

        frame.setVisible(true);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ROLE MENU: Cấu hình card mạng, vai trò Sender / Receiver
    // ═══════════════════════════════════════════════════════════════════════════

    static void showRoleMenu(String mode) {
        JFrame frame = new JFrame("📡 " + mode + " - Chọn vai trò / Choose Role");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(680, 530);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        Color modeColor = mode.equals("BROADCAST") ? C_BROADCAST : C_MULTICAST;

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(C_BG);
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        // Header
        JLabel title = new JLabel("Chế độ: " + mode, SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 17));
        title.setForeground(modeColor);
        root.add(title, BorderLayout.NORTH);

        // Config panel
        JPanel cfg = new JPanel(new GridBagLayout());
        cfg.setBackground(C_BG);
        cfg.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(modeColor, 1), " ⚙️ Cấu hình mạng & tài khoản ",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 12), modeColor));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // 1. Nickname
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        cfg.add(new JLabel("Nickname:"), gbc);
        JTextField txtNick = new JTextField(System.getProperty("user.name", "user"), 14);
        gbc.gridx = 1; gbc.weightx = 1.0;
        cfg.add(txtNick, gbc);

        // 2. Card mạng (Ưu tiên Hotspot tự chọn mục đầu tiên)
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        cfg.add(new JLabel("Card mạng:"), gbc);

        List<NetworkHelper.CardInfo> cards = NetworkHelper.getAvailableCards();
        JComboBox<NetworkHelper.CardInfo> cbCards = new JComboBox<>(cards.toArray(new NetworkHelper.CardInfo[0]));
        cbCards.setFont(new Font("SansSerif", Font.PLAIN, 12));
        gbc.gridx = 1; gbc.weightx = 1.0;
        cfg.add(cbCards, gbc);

        // 3. Port
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        cfg.add(new JLabel("Port UDP:"), gbc);
        String defaultPort = mode.equals("BROADCAST") ? "7000" : "8000";
        JTextField txtPort = new JTextField(defaultPort, 6);
        gbc.gridx = 1; gbc.weightx = 1.0;
        cfg.add(txtPort, gbc);

        // 4. IP đích (Multicast Group IP hoặc Broadcast Subnet IP)
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        JLabel lblDest = new JLabel(mode.equals("MULTICAST") ? "Nhóm Multicast IP:" : "Broadcast IP:");
        cfg.add(lblDest, gbc);

        // Broadcast: lấy subnet broadcast của card đã chọn (vd 192.168.137.255)
        String initialDest = "239.1.1.1";
        if (mode.equals("BROADCAST")) {
            if (!cards.isEmpty() && cards.get(0).broadcastIp != null) {
                initialDest = cards.get(0).broadcastIp;
            } else {
                initialDest = "255.255.255.255";
            }
        }
        JTextField txtDest = new JTextField(initialDest, 14);
        gbc.gridx = 1; gbc.weightx = 1.0;
        cfg.add(txtDest, gbc);

        // Khi người dùng đổi card mạng trong combobox, tự cập nhật broadcast IP
        cbCards.addActionListener(e -> {
            if (mode.equals("BROADCAST")) {
                NetworkHelper.CardInfo sel = (NetworkHelper.CardInfo) cbCards.getSelectedItem();
                if (sel != null && sel.broadcastIp != null) {
                    txtDest.setText(sel.broadcastIp);
                }
            }
        });

        // 5. Tin nhắn ban đầu
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        cfg.add(new JLabel("Tin nhắn (Sender):"), gbc);
        JTextField txtMsg = new JTextField("Xin chào từ " + System.getProperty("user.name", "user"), 20);
        gbc.gridx = 1; gbc.weightx = 1.0;
        cfg.add(txtMsg, gbc);

        root.add(cfg, BorderLayout.CENTER);

        // Buttons bottom
        JButton btnSender   = bigButton("📤 SENDER",   "<html><center>Gửi tin / Send</center></html>",   C_SENDER);
        JButton btnReceiver = bigButton("📥 RECEIVER", "<html><center>Nhận tin / Receive</center></html>", C_RECEIVER);
        JButton btnBack     = smallButton("← Quay lại", Color.GRAY);

        JPanel btns = new JPanel(new GridLayout(1, 2, 14, 0));
        btns.setBackground(C_BG);
        btns.add(btnSender);
        btns.add(btnReceiver);

        JPanel bottomRow = new JPanel(new BorderLayout(10, 0));
        bottomRow.setBackground(C_BG);
        bottomRow.add(btnBack, BorderLayout.WEST);
        bottomRow.add(btns, BorderLayout.CENTER);
        root.add(bottomRow, BorderLayout.SOUTH);

        frame.setContentPane(root);

        // ── Action: SENDER ──────────────────────────────────────────────────
        btnSender.addActionListener(e -> {
            String port = txtPort.getText().trim();
            String nick = txtNick.getText().trim();
            String msg  = txtMsg.getText().trim();
            String dest = txtDest.getText().trim();
            NetworkHelper.CardInfo selCard = (NetworkHelper.CardInfo) cbCards.getSelectedItem();
            String cardIP = selCard != null ? selCard.ip : null;

            if (!validatePort(frame, port)) return;
            frame.dispose();

            if (mode.equals("BROADCAST")) {
                if (dest.isEmpty()) dest = "255.255.255.255";
                BroadcastSenderGUI.launch(nick, dest, Integer.parseInt(port), msg, cardIP);
            } else {
                if (dest.isEmpty()) dest = "239.1.1.1";
                MulticastSenderGUI.launch(nick, dest, Integer.parseInt(port), msg, cardIP);
            }
        });

        // ── Action: RECEIVER ────────────────────────────────────────────────
        btnReceiver.addActionListener(e -> {
            String port = txtPort.getText().trim();
            String nick = txtNick.getText().trim();
            String dest = txtDest.getText().trim();
            NetworkHelper.CardInfo selCard = (NetworkHelper.CardInfo) cbCards.getSelectedItem();
            String cardIP = selCard != null ? selCard.ip : null;

            if (!validatePort(frame, port)) return;
            frame.dispose();

            if (mode.equals("BROADCAST")) {
                BroadcastReceiverGUI.launch(nick, Integer.parseInt(port), cardIP);
            } else {
                if (dest.isEmpty()) dest = "239.1.1.1";
                MulticastReceiverGUI.launch(nick, dest, Integer.parseInt(port), cardIP);
            }
        });

        btnBack.addActionListener(e -> {
            frame.dispose();
            SwingUtilities.invokeLater(() -> showMainMenu(new String[0]));
        });

        frame.setVisible(true);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    static JButton bigButton(String title, String subtitle, Color bg) {
        JButton btn = new JButton("<html><center><b>" + title + "</b><br>" + subtitle + "</center></html>");
        btn.setBackground(bg);
        btn.setForeground(C_TEXT);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);
        btn.setPreferredSize(new Dimension(230, 95));
        return btn;
    }

    static JButton smallButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(C_TEXT);
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    static boolean validatePort(JFrame parent, String port) {
        try {
            int p = Integer.parseInt(port);
            if (p < 1 || p > 65535) throw new NumberFormatException();
            return true;
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(parent,
                    "Port không hợp lệ! Nhập số từ 1 đến 65535.",
                    "Lỗi / Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONSOLE FALLBACK
    // ═══════════════════════════════════════════════════════════════════════════

    static void consoleMenu(String[] args) {
        java.util.Scanner sc = new java.util.Scanner(System.in);
        System.out.println("===========================================");
        System.out.println("  📡 Broadcast & Multicast Demo (Console)  ");
        System.out.println("===========================================");
        System.out.println("1. Broadcast");
        System.out.println("2. Multicast");
        System.out.print("Chọn (1/2): ");
        String modeChoice = sc.nextLine().trim();

        System.out.println("\n1. Sender (gửi)");
        System.out.println("2. Receiver (nhận)");
        System.out.print("Chọn vai trò (1/2): ");
        String roleChoice = sc.nextLine().trim();

        List<NetworkHelper.CardInfo> cards = NetworkHelper.getAvailableCards();
        System.out.println("\nDanh sách card mạng khả dụng:");
        for (int i = 0; i < cards.size(); i++) {
            System.out.println("  " + (i + 1) + ". " + cards.get(i).toString());
        }
        System.out.print("Chọn card [1 = mặc định/hotspot]: ");
        String cardChoice = sc.nextLine().trim();
        int cardIdx = 0;
        try {
            int c = Integer.parseInt(cardChoice);
            if (c >= 1 && c <= cards.size()) cardIdx = c - 1;
        } catch (Exception ignored) {}
        NetworkHelper.CardInfo chosenCard = cards.isEmpty() ? null : cards.get(cardIdx);

        System.out.print("Port [7000/8000]: ");
        String portStr = sc.nextLine().trim();
        int port = portStr.isEmpty() ? (modeChoice.equals("1") ? 7000 : 8000) : Integer.parseInt(portStr);

        if (modeChoice.equals("1")) {
            if (roleChoice.equals("1")) {
                String defaultBip = chosenCard != null && chosenCard.broadcastIp != null ? chosenCard.broadcastIp : "255.255.255.255";
                System.out.print("Broadcast IP [" + defaultBip + "]: ");
                String bip = sc.nextLine().trim();
                if (bip.isEmpty()) bip = defaultBip;
                System.out.print("Tin nhắn: ");
                String msg = sc.nextLine().trim();
                try { BroadcastSenderGUI.sendConsole(bip, port, msg); }
                catch (Exception e) { e.printStackTrace(); }
            } else {
                try { BroadcastReceiverGUI.receiveConsole(port); }
                catch (Exception e) { e.printStackTrace(); }
            }
        } else {
            System.out.print("Nhóm Multicast IP [239.1.1.1]: ");
            String grp = sc.nextLine().trim();
            if (grp.isEmpty()) grp = "239.1.1.1";
            if (roleChoice.equals("1")) {
                System.out.print("Tin nhắn: ");
                String msg = sc.nextLine().trim();
                try { MulticastSenderGUI.sendConsole(grp, port, msg); }
                catch (Exception e) { e.printStackTrace(); }
            } else {
                try { MulticastReceiverGUI.receiveConsole(grp, port); }
                catch (Exception e) { e.printStackTrace(); }
            }
        }
    }
}
