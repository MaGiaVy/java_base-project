import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.List;

/**
 * NetworkDemo.java - Launcher tổng hợp UDP Broadcast & Multicast
 * Toàn bộ chữ đen sắc nét trên nền Pastel có viền đậm rõ ràng.
 */
public class NetworkDemo {

    // Màu nền Pastel và viền đậm tương phản cao
    static final Color C_BROADCAST  = new Color(0xBBDEFB); // Xanh dương pastel
    static final Color B_BROADCAST  = new Color(0x1976D2); // Viền xanh dương đậm

    static final Color C_MULTICAST  = new Color(0xC8E6C9); // Xanh lá pastel
    static final Color B_MULTICAST  = new Color(0x388E3C); // Viền xanh lá đậm

    static final Color C_UNIFIED    = new Color(0xB2DFDB); // Xanh ngọc pastel
    static final Color B_UNIFIED    = new Color(0x00796B); // Viền xanh ngọc đậm

    static final Color C_SENDER     = new Color(0xFFE0B2); // Cam pastel
    static final Color B_SENDER     = new Color(0xF57C00); // Viền cam đậm

    static final Color C_RECEIVER   = new Color(0xE1BEE7); // Tím pastel
    static final Color B_RECEIVER   = new Color(0x7B1FA2); // Viền tím đậm

    static final Color C_BG         = new Color(0xF5F7FA);
    static final Color C_TEXT       = Color.BLACK;

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
        frame.setSize(640, 500);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(C_BG);
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JLabel title = new JLabel("Chọn chế độ truyền dữ liệu / Select Mode", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setForeground(C_TEXT);
        root.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(3, 1, 0, 10));
        center.setBackground(C_BG);

        JButton btnUnified = bigButton("💬 PHÒNG CHAT HỢP NHẤT (KHUYÊN DÙNG)",
                "Gộp Broadcast & Multicast trong 1 giao diện (Gửi & Nhận 2 chiều, chọn nhóm)",
                C_UNIFIED, B_UNIFIED);

        JButton btnBroadcast = bigButton("📢 BROADCAST RIÊNG",
                "Gửi tới mọi máy trong toàn mạng (DatagramSocket + setBroadcast)",
                C_BROADCAST, B_BROADCAST);

        JButton btnMulticast = bigButton("📡 MULTICAST RIÊNG",
                "Gửi tới nhóm đã đăng ký (MulticastSocket + joinGroup)",
                C_MULTICAST, B_MULTICAST);

        center.add(btnUnified);
        center.add(btnBroadcast);
        center.add(btnMulticast);
        root.add(center, BorderLayout.CENTER);

        btnUnified.addActionListener(e -> { frame.dispose(); UnifiedChatGUI.main(new String[0]); });

        // Hiển thị card mạng hotspot được phát hiện
        List<NetworkHelper.CardInfo> cards = NetworkHelper.getAvailableCards();
        String detectedNotice = "<html><center><small><b style='color:#000000;'>💡 Broadcast = loa phóng thanh toàn mạng | Multicast = group chat theo nhóm</b><br>";
        if (!cards.isEmpty() && cards.get(0).isHotspot) {
            detectedNotice += "<b style='color:#BF360C;'>🔥 Đã tự nhận diện Mobile Hotspot: " + cards.get(0).ip + " (" + cards.get(0).displayName + ")</b>";
        } else if (!cards.isEmpty()) {
            detectedNotice += "<span style='color:#000000;'>Card mặc định: " + cards.get(0).ip + " (" + cards.get(0).displayName + ")</span>";
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
        frame.setSize(680, 540);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        Color modeBorder = mode.equals("BROADCAST") ? B_BROADCAST : B_MULTICAST;

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(C_BG);
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        // Header
        JLabel title = new JLabel("Chế độ: " + mode, SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setForeground(C_TEXT);
        root.add(title, BorderLayout.NORTH);

        // Config panel
        JPanel cfg = new JPanel(new GridBagLayout());
        cfg.setBackground(Color.WHITE);
        cfg.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(modeBorder, 2), " ⚙️ Cấu hình mạng & tài khoản ",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 12), C_TEXT));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // 1. Nickname
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        JLabel lblNick = new JLabel("Nickname:");
        lblNick.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblNick.setForeground(C_TEXT);
        cfg.add(lblNick, gbc);

        JTextField txtNick = new JTextField(System.getProperty("user.name", "user"), 14);
        txtNick.setFont(new Font("SansSerif", Font.BOLD, 12));
        txtNick.setForeground(C_TEXT);
        gbc.gridx = 1; gbc.weightx = 1.0;
        cfg.add(txtNick, gbc);

        // 2. Card mạng
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        JLabel lblCard = new JLabel("Card mạng:");
        lblCard.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblCard.setForeground(C_TEXT);
        cfg.add(lblCard, gbc);

        List<NetworkHelper.CardInfo> cards = NetworkHelper.getAvailableCards();
        JComboBox<NetworkHelper.CardInfo> cbCards = new JComboBox<>(cards.toArray(new NetworkHelper.CardInfo[0]));
        cbCards.setFont(new Font("SansSerif", Font.PLAIN, 12));
        cbCards.setForeground(C_TEXT);
        gbc.gridx = 1; gbc.weightx = 1.0;
        cfg.add(cbCards, gbc);

        // 3. Port
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        JLabel lblPort = new JLabel("Port UDP:");
        lblPort.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblPort.setForeground(C_TEXT);
        cfg.add(lblPort, gbc);

        String defaultPort = mode.equals("BROADCAST") ? "7000" : "8000";
        JTextField txtPort = new JTextField(defaultPort, 6);
        txtPort.setFont(new Font("SansSerif", Font.PLAIN, 12));
        txtPort.setForeground(C_TEXT);
        gbc.gridx = 1; gbc.weightx = 1.0;
        cfg.add(txtPort, gbc);

        // 4. IP đích
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        JLabel lblDest = new JLabel(mode.equals("MULTICAST") ? "Nhóm Multicast IP:" : "Broadcast IP:");
        lblDest.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblDest.setForeground(C_TEXT);
        cfg.add(lblDest, gbc);

        String initialDest = "239.1.1.1";
        if (mode.equals("BROADCAST")) {
            if (!cards.isEmpty() && cards.get(0).broadcastIp != null) {
                initialDest = cards.get(0).broadcastIp;
            } else {
                initialDest = "255.255.255.255";
            }
        }
        JTextField txtDest = new JTextField(initialDest, 14);
        txtDest.setFont(new Font("SansSerif", Font.PLAIN, 12));
        txtDest.setForeground(C_TEXT);
        gbc.gridx = 1; gbc.weightx = 1.0;
        cfg.add(txtDest, gbc);

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
        JLabel lblMsg = new JLabel("Tin nhắn (Sender):");
        lblMsg.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblMsg.setForeground(C_TEXT);
        cfg.add(lblMsg, gbc);

        JTextField txtMsg = new JTextField("Xin chào từ " + System.getProperty("user.name", "user"), 20);
        txtMsg.setFont(new Font("SansSerif", Font.PLAIN, 12));
        txtMsg.setForeground(C_TEXT);
        gbc.gridx = 1; gbc.weightx = 1.0;
        cfg.add(txtMsg, gbc);

        root.add(cfg, BorderLayout.CENTER);

        // Buttons bottom
        JButton btnSender   = bigButton("📤 SENDER",   "Gửi tin nhắn / Send",   C_SENDER, B_SENDER);
        JButton btnReceiver = bigButton("📥 RECEIVER", "Nhận tin nhắn / Receive", C_RECEIVER, B_RECEIVER);
        JButton btnBack     = smallButton("← Quay lại", new Color(0xEEEEEE), new Color(0x757575));

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

        // Action: SENDER
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

        // Action: RECEIVER
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

    static JButton bigButton(String title, String subtitle, Color bg, Color border) {
        JButton btn = new JButton("<html><center><b style='color:#000000; font-size:13px;'>" + title + "</b><br><span style='color:#000000; font-size:11px;'>" + subtitle + "</span></center></html>");
        btn.setBackground(bg);
        btn.setForeground(C_TEXT);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 2),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);
        btn.setPreferredSize(new Dimension(230, 95));
        return btn;
    }

    static JButton smallButton(String text, Color bg, Color border) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(C_TEXT);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 1),
                BorderFactory.createEmptyBorder(5, 12, 5, 12)));
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
