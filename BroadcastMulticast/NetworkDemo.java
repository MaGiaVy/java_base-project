import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/**
 * NetworkDemo.java - Launcher tổng hợp Broadcast vs Multicast
 * Swing GUI chính, fallback console nếu headless.
 */
public class NetworkDemo {

    // ─── Màu sắc chủ đạo ─────────────────────────────────────────────────────
    static final Color C_BROADCAST = new Color(0x1565C0); // xanh đậm
    static final Color C_MULTICAST  = new Color(0x2E7D32); // xanh lá
    static final Color C_SENDER     = new Color(0xE65100); // cam
    static final Color C_RECEIVER   = new Color(0x6A1B9A); // tím
    static final Color C_BG         = new Color(0xF5F5F5);
    static final Color C_TEXT       = Color.WHITE;

    public static void main(String[] args) {
        // Kiểm tra headless
        if (GraphicsEnvironment.isHeadless()) {
            consoleMenu(args);
            return;
        }
        SwingUtilities.invokeLater(() -> showMainMenu(args));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SWING GUI
    // ═══════════════════════════════════════════════════════════════════════════

    static void showMainMenu(String[] args) {
        JFrame frame = new JFrame("📡 Broadcast & Multicast Demo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(560, 400);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(C_BG);
        root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // ── Header ─────────────────────────────────────────────────────────────
        JLabel title = new JLabel("Chọn chế độ truyền dữ liệu / Select Mode", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        root.add(title, BorderLayout.NORTH);

        // ── Hai nút chính ──────────────────────────────────────────────────────
        JPanel center = new JPanel(new GridLayout(1, 2, 20, 0));
        center.setBackground(C_BG);

        JButton btnBroadcast = bigButton("📢 BROADCAST",
                "<html><center>Gửi tới <b>mọi máy</b> trong LAN<br><small>DatagramSocket + setBroadcast(true)</small></center></html>",
                C_BROADCAST);
        JButton btnMulticast = bigButton("📡 MULTICAST",
                "<html><center>Gửi tới <b>nhóm đã đăng ký</b><br><small>MulticastSocket + joinGroup</small></center></html>",
                C_MULTICAST);

        center.add(btnBroadcast);
        center.add(btnMulticast);
        root.add(center, BorderLayout.CENTER);

        // ── Footer ─────────────────────────────────────────────────────────────
        JLabel footer = new JLabel(
                "<html><center><small>💡 Broadcast = hét cho cả phòng | Multicast = group chat</small></center></html>",
                SwingConstants.CENTER);
        footer.setForeground(Color.GRAY);
        root.add(footer, BorderLayout.SOUTH);

        frame.setContentPane(root);

        // ── Action ─────────────────────────────────────────────────────────────
        btnBroadcast.addActionListener(e -> {
            frame.dispose();
            showRoleMenu("BROADCAST", frame);
        });
        btnMulticast.addActionListener(e -> {
            frame.dispose();
            showRoleMenu("MULTICAST", frame);
        });

        frame.setVisible(true);
    }

    static void showRoleMenu(String mode, JFrame parent) {
        JFrame frame = new JFrame("📡 " + mode + " - Chọn vai trò / Choose Role");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 460);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        Color modeColor = mode.equals("BROADCAST") ? C_BROADCAST : C_MULTICAST;

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(C_BG);
        root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Header
        JLabel title = new JLabel("Chế độ: " + mode, SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        title.setForeground(modeColor);
        root.add(title, BorderLayout.NORTH);

        // Config panel
        JPanel cfg = new JPanel(new GridBagLayout());
        cfg.setBackground(C_BG);
        cfg.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(modeColor, 1), " ⚙️ Cấu hình / Config ",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 12), modeColor));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // Nickname
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        cfg.add(new JLabel("Nickname:"), gbc);
        JTextField txtNick = new JTextField(System.getProperty("user.name", "user"), 14);
        gbc.gridx = 1; gbc.weightx = 1.0;
        cfg.add(txtNick, gbc);

        // Port
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        cfg.add(new JLabel("Port:"), gbc);
        String defaultPort = mode.equals("BROADCAST") ? "7000" : "8000";
        JTextField txtPort = new JTextField(defaultPort, 6);
        gbc.gridx = 1; gbc.weightx = 1.0;
        cfg.add(txtPort, gbc);

        // Multicast IP (chỉ hiện khi Multicast)
        JLabel lblGroup = new JLabel("Nhóm multicast IP:");
        JTextField txtGroup = new JTextField("239.1.1.1", 14);
        if (mode.equals("MULTICAST")) {
            gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
            cfg.add(lblGroup, gbc);
            gbc.gridx = 1; gbc.weightx = 1.0;
            cfg.add(txtGroup, gbc);
        }

        // Broadcast IP (Sender Broadcast)
        JLabel lblBcastIP = new JLabel("Broadcast IP:");
        JTextField txtBcastIP = new JTextField("255.255.255.255", 14);
        if (mode.equals("BROADCAST")) {
            gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
            cfg.add(lblBcastIP, gbc);
            gbc.gridx = 1; gbc.weightx = 1.0;
            cfg.add(txtBcastIP, gbc);
        }

        // Message (chỉ Sender)
        JLabel lblMsg = new JLabel("Tin nhắn (Sender):");
        JTextField txtMsg = new JTextField("Xin chào từ " + System.getProperty("user.name", "user"), 20);
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        cfg.add(lblMsg, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        cfg.add(txtMsg, gbc);

        root.add(cfg, BorderLayout.CENTER);

        // Buttons
        JPanel btns = new JPanel(new GridLayout(1, 3, 14, 0));
        btns.setBackground(C_BG);

        JButton btnSender   = bigButton("📤 SENDER",   "<html><center>Gửi tin / Send</center></html>",   C_SENDER);
        JButton btnReceiver = bigButton("📥 RECEIVER", "<html><center>Nhận tin / Receive</center></html>", C_RECEIVER);
        JButton btnBack     = smallButton("← Quay lại", Color.GRAY);

        btns.add(btnSender);
        btns.add(btnReceiver);

        JPanel bottomRow = new JPanel(new BorderLayout(10, 0));
        bottomRow.setBackground(C_BG);
        bottomRow.add(btnBack, BorderLayout.WEST);
        bottomRow.add(btns, BorderLayout.CENTER);
        root.add(bottomRow, BorderLayout.SOUTH);

        frame.setContentPane(root);

        // Actions
        btnSender.addActionListener(e -> {
            String port = txtPort.getText().trim();
            String nick = txtNick.getText().trim();
            String msg  = txtMsg.getText().trim();
            if (!validatePort(frame, port)) return;
            frame.dispose();
            if (mode.equals("BROADCAST")) {
                String bip = txtBcastIP.getText().trim();
                if (bip.isEmpty()) bip = "255.255.255.255";
                BroadcastSenderGUI.launch(nick, bip, Integer.parseInt(port), msg);
            } else {
                String grp = txtGroup.getText().trim();
                if (grp.isEmpty()) grp = "239.1.1.1";
                MulticastSenderGUI.launch(nick, grp, Integer.parseInt(port), msg);
            }
        });

        btnReceiver.addActionListener(e -> {
            String port = txtPort.getText().trim();
            String nick = txtNick.getText().trim();
            if (!validatePort(frame, port)) return;
            frame.dispose();
            if (mode.equals("BROADCAST")) {
                BroadcastReceiverGUI.launch(nick, Integer.parseInt(port));
            } else {
                String grp = txtGroup.getText().trim();
                if (grp.isEmpty()) grp = "239.1.1.1";
                MulticastReceiverGUI.launch(nick, grp, Integer.parseInt(port));
            }
        });

        btnBack.addActionListener(e -> {
            frame.dispose();
            SwingUtilities.invokeLater(() -> showMainMenu(new String[0]));
        });

        frame.setVisible(true);
    }

    // ── Helper tạo nút lớn ────────────────────────────────────────────────────
    static JButton bigButton(String title, String subtitle, Color bg) {
        JButton btn = new JButton("<html><center><b>" + title + "</b><br>" + subtitle + "</center></html>");
        btn.setBackground(bg);
        btn.setForeground(C_TEXT);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);
        btn.setPreferredSize(new Dimension(220, 100));
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

        System.out.print("Port [7000/8000]: ");
        String portStr = sc.nextLine().trim();
        int port = portStr.isEmpty() ? (modeChoice.equals("1") ? 7000 : 8000) : Integer.parseInt(portStr);

        if (modeChoice.equals("1")) {
            if (roleChoice.equals("1")) {
                System.out.print("Broadcast IP [255.255.255.255]: ");
                String bip = sc.nextLine().trim();
                if (bip.isEmpty()) bip = "255.255.255.255";
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
