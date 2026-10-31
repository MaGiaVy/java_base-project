import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UnifiedChatGUI.java
 * Phòng Chat Hợp Nhất (Gộp cả UDP Broadcast và Multicast vào cùng 1 giao diện).
 * 
 * Tính năng chính:
 * 1. Gửi và nhận 2 chiều trên cùng 1 cửa sổ chat.
 * 2. Tích hợp đồng thời cả Broadcast (toàn mạng) và Multicast (theo nhóm có thể chọn/đổi nhóm).
 * 3. Hỗ trợ chọn danh sách nhóm Multicast (hoặc tự gõ IP) và Bật/Tắt tham gia nhóm (Join/Leave).
 * 4. Toàn bộ chữ hiển thị MÀU ĐEN (Black Text) trên nền màu sáng pastel có viền rõ nét.
 * 5. Khắc phục triệt để lỗi duplicate tin nhắn và phân biệt kênh chính xác 100%.
 */
public class UnifiedChatGUI extends JFrame {

    // Bảng màu Pastel dịu nhẹ kết hợp viền đậm giúp chữ Đen nổi bật sắc nét
    private static final Color C_BG              = new Color(0xF4F6F9); // Nền cửa sổ
    private static final Color C_PANEL           = Color.WHITE;          // Nền panel trắng
    private static final Color C_BORDER          = new Color(0x607D8B); // Viền xám đậm rõ nét
    private static final Color C_TEXT_BLACK      = Color.BLACK;         // Chữ đen chuẩn

    // Màu nền Pastel cho các nút bấm
    private static final Color C_BTN_CONNECT_ON  = new Color(0xC8E6C9); // Xanh lá pastel (Đang hoạt động)
    private static final Color C_BORDER_CONN_ON  = new Color(0x2E7D32);
    private static final Color C_BTN_CONNECT_OFF = new Color(0xFFCDD2); // Đỏ pastel (Đang ngắt)
    private static final Color C_BORDER_CONN_OFF = new Color(0xC62828);

    private static final Color C_BTN_SEND        = new Color(0xBBDEFB); // Xanh dương pastel (Gửi)
    private static final Color C_BORDER_SEND     = new Color(0x1565C0);

    private static final Color C_BTN_PING        = new Color(0xFFF9C4); // Vàng pastel (Ping)
    private static final Color C_BORDER_PING     = new Color(0xFBC02D);

    private static final Color C_BTN_CLEAR       = new Color(0xEEEEEE); // Xám sáng pastel (Xóa)
    private static final Color C_BORDER_CLEAR    = new Color(0x757575);

    private static final Color C_BTN_LEAVE       = new Color(0xFFE0B2); // Cam pastel (Rời nhóm)
    private static final Color C_BORDER_LEAVE    = new Color(0xE65100);
    private static final Color C_BTN_JOIN        = new Color(0xE1BEE7); // Tím pastel (Vào nhóm)
    private static final Color C_BORDER_JOIN     = new Color(0x6A1B9A);

    // Thành phần giao diện
    private JTextField txtNick;
    private JComboBox<NetworkHelper.CardInfo> cbCards;
    private JTextField txtBcastIP;
    private JTextField txtBcastPort;
    private JComboBox<String> cbMcastGroup;
    private JTextField txtMcastPort;
    private JButton btnConnect;
    private JButton btnMcastToggle;
    private JLabel lblStatus;

    private JTextPane chatPane;
    private HTMLEditorKit htmlKit;
    private HTMLDocument htmlDoc;

    private JComboBox<String> cbSendMode;
    private JTextField txtInput;
    private JButton btnSend;
    private JButton btnPing;
    private JButton btnClear;
    private JCheckBox chkAutoScroll;
    private JCheckBox chkFilterSelf;

    // Quản lý Socket và Đa luồng
    private final boolean isMainWindow;
    private volatile boolean connected = false;
    private volatile boolean inMcastGroup = true; // Mặc định vào sẵn nhóm 1 theo yêu cầu
    private DatagramSocket bcastSocket;
    private MulticastSocket mcastSocket;
    private InetSocketAddress currentMcastGroupAddress;
    private NetworkHelper.CardInfo currentCard;

    private Thread bcastThread;
    private Thread mcastThread;

    // Bộ nhớ đệm lọc tin nhắn echo của chính mình và chống nhận trùng lặp
    private final ConcurrentHashMap<String, Long> mySentMsgIds = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> receivedMsgIds = new ConcurrentHashMap<>();

    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");

    public UnifiedChatGUI() {
        this(System.getProperty("user.name", "User"), true);
    }

    public UnifiedChatGUI(String initialNick) {
        this(initialNick, false);
    }

    public UnifiedChatGUI(String initialNick, boolean isMain) {
        super("💬 UDP Unified Chat — Gộp Broadcast & Multicast (Chọn Nhóm)");
        this.isMainWindow = isMain;
        initUI(initialNick);
        // Tự động kết nối khi khởi động
        SwingUtilities.invokeLater(this::startChat);
    }

    private void initUI(String initialNick) {
        setDefaultCloseOperation(isMainWindow ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);
        setSize(920, 720);
        setMinimumSize(new Dimension(780, 560));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(C_BG);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 1. THANH MENU
        setJMenuBar(createMenuBar());

        // 2. PHẦN CẤU HÌNH TRÊN CÙNG
        JPanel topPanel = createConfigPanel(initialNick);
        root.add(topPanel, BorderLayout.NORTH);

        // 3. KHUNG HIỂN THỊ CHAT TRUNG TÂM
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setContentType("text/html");
        htmlKit = new HTMLEditorKit();
        htmlDoc = new HTMLDocument();
        chatPane.setEditorKit(htmlKit);
        chatPane.setDocument(htmlDoc);
        chatPane.setBackground(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(chatPane);
        scrollPane.setBorder(BorderFactory.createLineBorder(C_BORDER, 1));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        root.add(scrollPane, BorderLayout.CENTER);

        // 4. PHẦN NHẬP VÀ GỬI TIN NHẮN DƯỚI CÙNG
        JPanel bottomPanel = createInputPanel();
        root.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(root);

        // Đóng socket sạch sẽ khi tắt cửa sổ
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopChat();
                if (isMainWindow) {
                    System.exit(0);
                }
            }
        });
    }

    private JMenuBar createMenuBar() {
        JMenuBar mb = new JMenuBar();
        mb.setBackground(Color.WHITE);
        mb.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xCFD8DC)));

        JMenu mFile = new JMenu("Phòng Chat");
        mFile.setForeground(C_TEXT_BLACK);
        mFile.setFont(new Font("SansSerif", Font.BOLD, 12));

        JMenuItem miNewWindow = new JMenuItem("➕ Mở thêm cửa sổ Chat (Test 2 người)");
        miNewWindow.setForeground(C_TEXT_BLACK);
        miNewWindow.addActionListener(e -> {
            UnifiedChatGUI other = new UnifiedChatGUI("User_" + (new Random().nextInt(900) + 100));
            other.setLocation(getX() + 40, getY() + 40);
            other.setVisible(true);
        });
        JMenuItem miExit = new JMenuItem("Thoát");
        miExit.setForeground(C_TEXT_BLACK);
        miExit.addActionListener(e -> {
            stopChat();
            dispose();
        });
        mFile.add(miNewWindow);
        mFile.addSeparator();
        mFile.add(miExit);

        JMenu mLegacy = new JMenu("Công cụ Demo cũ");
        mLegacy.setForeground(C_TEXT_BLACK);
        mLegacy.setFont(new Font("SansSerif", Font.BOLD, 12));

        JMenuItem miMainMenu = new JMenuItem("Mở Menu Chọn Cũ (Main Menu)");
        miMainMenu.setForeground(C_TEXT_BLACK);
        miMainMenu.addActionListener(e -> NetworkDemo.showMainMenu(new String[0]));
        mLegacy.add(miMainMenu);
        mLegacy.addSeparator();

        JMenuItem miBcastSender = new JMenuItem("Mở Broadcast Sender riêng");
        miBcastSender.setForeground(C_TEXT_BLACK);
        miBcastSender.addActionListener(e -> {
            NetworkHelper.CardInfo card = (NetworkHelper.CardInfo) cbCards.getSelectedItem();
            String cardIp = card != null ? card.ip : null;
            BroadcastSenderGUI.launch(txtNick.getText().trim(), txtBcastIP.getText().trim(),
                    Integer.parseInt(txtBcastPort.getText().trim()), "Chào từ Broadcast", cardIp);
        });

        JMenuItem miBcastReceiver = new JMenuItem("Mở Broadcast Receiver riêng");
        miBcastReceiver.setForeground(C_TEXT_BLACK);
        miBcastReceiver.addActionListener(e -> {
            NetworkHelper.CardInfo card = (NetworkHelper.CardInfo) cbCards.getSelectedItem();
            String cardIp = card != null ? card.ip : null;
            BroadcastReceiverGUI.launch(txtNick.getText().trim(), Integer.parseInt(txtBcastPort.getText().trim()), cardIp);
        });

        JMenuItem miMcastSender = new JMenuItem("Mở Multicast Sender riêng");
        miMcastSender.setForeground(C_TEXT_BLACK);
        miMcastSender.addActionListener(e -> {
            NetworkHelper.CardInfo card = (NetworkHelper.CardInfo) cbCards.getSelectedItem();
            String cardIp = card != null ? card.ip : null;
            MulticastSenderGUI.launch(txtNick.getText().trim(), getSelectedMcastIP(),
                    Integer.parseInt(txtMcastPort.getText().trim()), "Chào từ Multicast", cardIp);
        });

        JMenuItem miMcastReceiver = new JMenuItem("Mở Multicast Receiver riêng");
        miMcastReceiver.setForeground(C_TEXT_BLACK);
        miMcastReceiver.addActionListener(e -> {
            NetworkHelper.CardInfo card = (NetworkHelper.CardInfo) cbCards.getSelectedItem();
            String cardIp = card != null ? card.ip : null;
            MulticastReceiverGUI.launch(txtNick.getText().trim(), getSelectedMcastIP(),
                    Integer.parseInt(txtMcastPort.getText().trim()), cardIp);
        });

        mLegacy.add(miBcastSender);
        mLegacy.add(miBcastReceiver);
        mLegacy.addSeparator();
        mLegacy.add(miMcastSender);
        mLegacy.add(miMcastReceiver);

        JMenu mHelp = new JMenu("Trợ giúp");
        mHelp.setForeground(C_TEXT_BLACK);
        mHelp.setFont(new Font("SansSerif", Font.BOLD, 12));
        JMenuItem miHelp = new JMenuItem("Nguyên lý Broadcast vs Multicast");
        miHelp.setForeground(C_TEXT_BLACK);
        miHelp.addActionListener(e -> showHelpDialog());
        mHelp.add(miHelp);

        mb.add(mFile);
        mb.add(mLegacy);
        mb.add(mHelp);
        return mb;
    }

    private JPanel createConfigPanel(String initialNick) {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBackground(C_PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xB0BEC5), 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        // Hàng 1: Nickname + Card mạng + Nút Kết nối
        JPanel row1 = new JPanel(new GridBagLayout());
        row1.setBackground(C_PANEL);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        JLabel lblNick = new JLabel("👤 Tên hiển thị:");
        lblNick.setForeground(C_TEXT_BLACK);
        lblNick.setFont(new Font("SansSerif", Font.BOLD, 12));
        row1.add(lblNick, gbc);

        txtNick = new JTextField(initialNick, 10);
        txtNick.setFont(new Font("SansSerif", Font.BOLD, 12));
        txtNick.setForeground(C_TEXT_BLACK);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.25;
        row1.add(txtNick, gbc);

        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0;
        JLabel lblCard = new JLabel("🌐 Card mạng:");
        lblCard.setForeground(C_TEXT_BLACK);
        lblCard.setFont(new Font("SansSerif", Font.BOLD, 12));
        row1.add(lblCard, gbc);

        java.util.List<NetworkHelper.CardInfo> cards = NetworkHelper.getAvailableCards();
        cbCards = new JComboBox<>(cards.toArray(new NetworkHelper.CardInfo[0]));
        cbCards.setFont(new Font("SansSerif", Font.PLAIN, 12));
        cbCards.setForeground(C_TEXT_BLACK);
        gbc.gridx = 3; gbc.gridy = 0; gbc.weightx = 0.65;
        row1.add(cbCards, gbc);

        btnConnect = createStyledButton("🔴 Đang ngắt", C_BTN_CONNECT_OFF, C_BORDER_CONN_OFF);
        btnConnect.addActionListener(e -> {
            if (connected) {
                stopChat();
            } else {
                startChat();
            }
        });
        gbc.gridx = 4; gbc.gridy = 0; gbc.weightx = 0;
        row1.add(btnConnect, gbc);

        // Hàng 2: Cấu hình Broadcast IP/Port & Multicast Group/Port
        JPanel row2 = new JPanel(new GridBagLayout());
        row2.setBackground(C_PANEL);

        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc2.insets = new Insets(4, 4, 4, 6);
        gbc2.fill = GridBagConstraints.HORIZONTAL;

        // ── 2A. Broadcast settings ──
        gbc2.gridx = 0; gbc2.gridy = 0; gbc2.weightx = 0;
        JLabel lblBcast = new JLabel("📢 Broadcast IP/Port:");
        lblBcast.setForeground(C_TEXT_BLACK);
        lblBcast.setFont(new Font("SansSerif", Font.BOLD, 12));
        row2.add(lblBcast, gbc2);

        String initialBcast = "255.255.255.255";
        if (!cards.isEmpty() && cards.get(0).broadcastIp != null) {
            initialBcast = cards.get(0).broadcastIp;
        }
        txtBcastIP = new JTextField(initialBcast, 12);
        txtBcastIP.setFont(new Font("SansSerif", Font.PLAIN, 12));
        txtBcastIP.setForeground(C_TEXT_BLACK);
        gbc2.gridx = 1; gbc2.gridy = 0; gbc2.weightx = 0.28;
        row2.add(txtBcastIP, gbc2);

        txtBcastPort = new JTextField("7000", 5);
        txtBcastPort.setFont(new Font("SansSerif", Font.PLAIN, 12));
        txtBcastPort.setForeground(C_TEXT_BLACK);
        gbc2.gridx = 2; gbc2.gridy = 0; gbc2.weightx = 0.12;
        row2.add(txtBcastPort, gbc2);

        // ── 2B. Multicast settings: Danh sách nhóm + Nút Rời/Vào nhóm ──
        gbc2.gridx = 3; gbc2.gridy = 0; gbc2.weightx = 0;
        JLabel lblMcast = new JLabel("📡 Nhóm Multicast:");
        lblMcast.setForeground(C_TEXT_BLACK);
        lblMcast.setFont(new Font("SansSerif", Font.BOLD, 12));
        row2.add(lblMcast, gbc2);

        String[] presetGroups = {
                "239.1.1.1 (Nhóm 1 - Mặc định)",
                "239.1.1.2 (Nhóm 2)",
                "239.1.1.3 (Nhóm 3)",
                "239.255.0.1 (Nhóm VIP)"
        };
        cbMcastGroup = new JComboBox<>(presetGroups);
        cbMcastGroup.setEditable(true); // Cho phép tự gõ IP khác
        cbMcastGroup.setFont(new Font("SansSerif", Font.PLAIN, 12));
        cbMcastGroup.setForeground(C_TEXT_BLACK);
        cbMcastGroup.setToolTipText("Chọn nhóm có sẵn hoặc tự gõ IP Multicast (224.0.0.0 - 239.255.255.255)");
        gbc2.gridx = 4; gbc2.gridy = 0; gbc2.weightx = 0.35;
        row2.add(cbMcastGroup, gbc2);

        txtMcastPort = new JTextField("8000", 4);
        txtMcastPort.setFont(new Font("SansSerif", Font.PLAIN, 12));
        txtMcastPort.setForeground(C_TEXT_BLACK);
        gbc2.gridx = 5; gbc2.gridy = 0; gbc2.weightx = 0.10;
        row2.add(txtMcastPort, gbc2);

        // Nút Tham gia / Rời nhóm Multicast trực tiếp
        btnMcastToggle = createStyledButton("🚪 Rời nhóm", C_BTN_LEAVE, C_BORDER_LEAVE);
        btnMcastToggle.setToolTipText("Bấm để Rời nhóm hoặc Vào nhóm Multicast để demo");
        btnMcastToggle.addActionListener(e -> toggleMulticastGroup());
        gbc2.gridx = 6; gbc2.gridy = 0; gbc2.weightx = 0;
        row2.add(btnMcastToggle, gbc2);

        // Tự động chuyển nhóm khi người dùng chọn nhóm mới trong ComboBox
        cbMcastGroup.addActionListener(e -> {
            if (connected && inMcastGroup) {
                switchMulticastGroup(getSelectedMcastIP());
            }
        });

        // Khi đổi Card mạng, tự động cập nhật Broadcast IP tương ứng
        cbCards.addActionListener(e -> {
            NetworkHelper.CardInfo sel = (NetworkHelper.CardInfo) cbCards.getSelectedItem();
            if (sel != null && sel.broadcastIp != null) {
                txtBcastIP.setText(sel.broadcastIp);
            }
            if (connected) {
                appendSystemMessage("⚠️ Bạn vừa đổi card mạng. Đang tự động kết nối lại...", "blue");
                stopChat();
                startChat();
            }
        });

        // Hàng 3: Trạng thái & Tùy chọn
        JPanel row3 = new JPanel(new BorderLayout(8, 0));
        row3.setBackground(C_PANEL);

        lblStatus = new JLabel("⚪ Chưa kết nối");
        lblStatus.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblStatus.setForeground(C_TEXT_BLACK);
        row3.add(lblStatus, BorderLayout.WEST);

        JPanel opts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        opts.setBackground(C_PANEL);
        chkAutoScroll = new JCheckBox("Tự cuộn xuống", true);
        chkAutoScroll.setBackground(C_PANEL);
        chkAutoScroll.setForeground(C_TEXT_BLACK);
        chkAutoScroll.setFont(new Font("SansSerif", Font.PLAIN, 12));

        chkFilterSelf = new JCheckBox("Lọc tin nhắn lặp của chính mình", true);
        chkFilterSelf.setBackground(C_PANEL);
        chkFilterSelf.setForeground(C_TEXT_BLACK);
        chkFilterSelf.setFont(new Font("SansSerif", Font.PLAIN, 12));

        opts.add(chkFilterSelf);
        opts.add(chkAutoScroll);
        row3.add(opts, BorderLayout.EAST);

        panel.add(row1, BorderLayout.NORTH);
        panel.add(row2, BorderLayout.CENTER);
        panel.add(row3, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBackground(C_BG);

        // Hàng điều khiển gửi tin
        JPanel sendRow = new JPanel(new BorderLayout(6, 0));
        sendRow.setBackground(C_BG);

        // Chọn phương thức gửi
        String[] modes = {
                "📢 Broadcast (Toàn mạng)",
                "📡 Multicast (Nhóm đã chọn)",
                "📢+📡 Cả hai (Broadcast & Multicast)"
        };
        cbSendMode = new JComboBox<>(modes);
        cbSendMode.setFont(new Font("SansSerif", Font.BOLD, 12));
        cbSendMode.setForeground(C_TEXT_BLACK);
        cbSendMode.setBackground(Color.WHITE);
        cbSendMode.setPreferredSize(new Dimension(250, 36));
        sendRow.add(cbSendMode, BorderLayout.WEST);

        // Ô nhập tin nhắn
        txtInput = new JTextField();
        txtInput.setFont(new Font("SansSerif", Font.PLAIN, 14));
        txtInput.setForeground(C_TEXT_BLACK);
        txtInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x607D8B), 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        txtInput.addActionListener(e -> sendMessage());
        sendRow.add(txtInput, BorderLayout.CENTER);

        // Các nút bấm
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        btnPanel.setBackground(C_BG);

        btnSend = createStyledButton("📤 Gửi / Send", C_BTN_SEND, C_BORDER_SEND);
        btnSend.setPreferredSize(new Dimension(115, 36));
        btnSend.addActionListener(e -> sendMessage());

        btnPing = createStyledButton("⚡ Ping", C_BTN_PING, C_BORDER_PING);
        btnPing.setPreferredSize(new Dimension(80, 36));
        btnPing.setToolTipText("Gửi tin nhắn kiểm tra nhanh");
        btnPing.addActionListener(e -> {
            txtInput.setText("👋 Xin chào từ " + txtNick.getText().trim() + "! (Ping lúc " + TIME_FMT.format(new Date()) + ")");
            sendMessage();
        });

        btnClear = createStyledButton("🗑 Xóa", C_BTN_CLEAR, C_BORDER_CLEAR);
        btnClear.setPreferredSize(new Dimension(80, 36));
        btnClear.setToolTipText("Xóa lịch sử đoạn chat hiện tại");
        btnClear.addActionListener(e -> {
            try {
                htmlDoc = new HTMLDocument();
                chatPane.setDocument(htmlDoc);
                appendSystemMessage("Đã xóa trắng màn hình chat.", "gray");
            } catch (Exception ignored) {}
        });

        btnPanel.add(btnPing);
        btnPanel.add(btnClear);
        btnPanel.add(btnSend);

        sendRow.add(btnPanel, BorderLayout.EAST);
        panel.add(sendRow, BorderLayout.CENTER);

        return panel;
    }

    // Tiện ích tạo nút bấm chữ ĐEN trên nền Pastel có viền đậm
    private JButton createStyledButton(String text, Color bgPastel, Color borderDark) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setForeground(C_TEXT_BLACK);
        btn.setBackground(bgPastel);
        btn.setOpaque(true);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderDark, 1),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void updateButtonStyle(JButton btn, String text, Color bgPastel, Color borderDark) {
        btn.setText(text);
        btn.setBackground(bgPastel);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderDark, 1),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
    }

    private String getSelectedMcastIP() {
        Object item = cbMcastGroup.getSelectedItem();
        if (item == null) return "239.1.1.1";
        String s = item.toString().trim();
        int spaceIdx = s.indexOf(' ');
        if (spaceIdx > 0) {
            return s.substring(0, spaceIdx).trim();
        }
        return s;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // QUẢN LÝ KẾT NỐI (START / STOP LISTENER)
    // ═══════════════════════════════════════════════════════════════════════════

    public synchronized void startChat() {
        if (connected) return;

        try {
            currentCard = (NetworkHelper.CardInfo) cbCards.getSelectedItem();
            if (currentCard == null) {
                appendSystemMessage("❌ Không tìm thấy card mạng hợp lệ!", "red");
                return;
            }

            int bcastPort = Integer.parseInt(txtBcastPort.getText().trim());
            int mcastPort = Integer.parseInt(txtMcastPort.getText().trim());
            String mcastIP = getSelectedMcastIP();

            connected = true;

            // Mở Broadcast Listener
            startBroadcastListener(bcastPort);

            // Mở Multicast Listener
            startMulticastListener(mcastIP, mcastPort, currentCard);

            // Cập nhật trạng thái giao diện
            updateButtonStyle(btnConnect, "🟢 Đang hoạt động", C_BTN_CONNECT_ON, C_BORDER_CONN_ON);
            updateStatusLabel();

            String hsTag = currentCard.isHotspot ? " 🔥[HOTSPOT]" : "";
            appendSystemMessage("✅ <b>ĐÃ KẾT NỐI PHÒNG CHAT THÀNH CÔNG!</b><br>" +
                    "• Card: <b>" + currentCard.displayName + " (" + currentCard.ip + ")" + hsTag + "</b><br>" +
                    "• Đang nhận Broadcast toàn mạng tại cổng: <b>" + bcastPort + "</b><br>" +
                    "• Trạng thái Multicast: <b>" + (inMcastGroup ? "Đã tham gia nhóm " + mcastIP : "Chưa vào nhóm") + " (Port " + mcastPort + ")</b>", "green");

        } catch (Exception ex) {
            connected = false;
            updateButtonStyle(btnConnect, "🔴 Kết nối lại", C_BTN_CONNECT_OFF, C_BORDER_CONN_OFF);
            lblStatus.setText("<html><b style='color:#C62828;'>❌ LỖI:</b> " + escapeHtml(ex.getMessage()) + "</html>");
            appendSystemMessage("❌ Lỗi khi khởi động socket: " + ex.getMessage(), "red");
        }
    }

    public synchronized void stopChat() {
        if (!connected) return;
        connected = false;

        // Đóng Broadcast Socket
        if (bcastSocket != null && !bcastSocket.isClosed()) {
            try { bcastSocket.close(); } catch (Exception ignored) {}
        }

        // Rời nhóm và đóng Multicast Socket
        if (mcastSocket != null && !mcastSocket.isClosed()) {
            try {
                if (currentMcastGroupAddress != null && currentCard != null && currentCard.nif != null) {
                    mcastSocket.leaveGroup(currentMcastGroupAddress, currentCard.nif);
                }
            } catch (Exception ignored) {}
            try { mcastSocket.close(); } catch (Exception ignored) {}
        }

        updateButtonStyle(btnConnect, "🔴 Đã ngắt", C_BTN_CONNECT_OFF, C_BORDER_CONN_OFF);
        lblStatus.setText("<html><span style='color:#000000;'>⚪ Đã ngắt kết nối. Nhấn nút để kết nối lại.</span></html>");
        appendSystemMessage("⏹ Đã ngắt kết nối và rời các kênh.", "gray");
    }

    // Bật / Tắt trạng thái tham gia nhóm Multicast
    private synchronized void toggleMulticastGroup() {
        if (!connected) {
            appendSystemMessage("⚠️ Vui lòng kết nối phòng chat trước khi thao tác nhóm!", "orange");
            return;
        }

        String groupIP = getSelectedMcastIP();
        if (inMcastGroup) {
            // Rời nhóm
            leaveCurrentMulticastGroup();
        } else {
            // Vào nhóm
            joinMulticastGroup(groupIP);
        }
    }

    private synchronized void leaveCurrentMulticastGroup() {
        if (mcastSocket == null || mcastSocket.isClosed() || currentMcastGroupAddress == null) return;
        try {
            if (currentCard != null && currentCard.nif != null) {
                mcastSocket.leaveGroup(currentMcastGroupAddress, currentCard.nif);
            } else {
                mcastSocket.leaveGroup(currentMcastGroupAddress, null);
            }
            inMcastGroup = false;
            updateButtonStyle(btnMcastToggle, "✅ Vào nhóm", C_BTN_JOIN, C_BORDER_JOIN);
            updateStatusLabel();
            appendSystemMessage("🚪 <b>BẠN ĐÃ RỜI NHÓM MULTICAST!</b><br>" +
                    "• Bạn <b>SẼ KHÔNG NHẬN ĐƯỢC</b> tin Multicast gửi tới bất kỳ nhóm nào.<br>" +
                    "• Nhưng bạn <b>VẪN NHẬN ĐƯỢC</b> tin Broadcast gửi toàn mạng (Chứng minh đúng yêu cầu của thầy).", "orange");
        } catch (Exception ex) {
            appendSystemMessage("⚠️ Lỗi khi rời nhóm Multicast: " + ex.getMessage(), "red");
        }
    }

    private synchronized void joinMulticastGroup(String groupIP) {
        if (mcastSocket == null || mcastSocket.isClosed()) return;
        try {
            InetAddress group = InetAddress.getByName(groupIP);
            int mcastPort = Integer.parseInt(txtMcastPort.getText().trim());
            InetSocketAddress targetAddr = new InetSocketAddress(group, mcastPort);

            // Rời nhóm cũ trước nếu có
            if (currentMcastGroupAddress != null) {
                try {
                    if (currentCard != null && currentCard.nif != null) {
                        mcastSocket.leaveGroup(currentMcastGroupAddress, currentCard.nif);
                    }
                } catch (Exception ignored) {}
            }

            // Tham gia nhóm mới
            if (currentCard != null && currentCard.nif != null) {
                mcastSocket.joinGroup(targetAddr, currentCard.nif);
            } else {
                mcastSocket.joinGroup(targetAddr, null);
            }

            currentMcastGroupAddress = targetAddr;
            inMcastGroup = true;
            updateButtonStyle(btnMcastToggle, "🚪 Rời nhóm", C_BTN_LEAVE, C_BORDER_LEAVE);
            updateStatusLabel();
            appendSystemMessage("✅ <b>BẠN ĐÃ THAM GIA NHÓM MULTICAST: " + groupIP + "</b><br>" +
                    "• Bạn sẽ nhận được tất cả các tin nhắn gửi tới nhóm này.", "green");
        } catch (Exception ex) {
            appendSystemMessage("⚠️ Không thể tham gia nhóm Multicast " + groupIP + ": " + ex.getMessage(), "red");
        }
    }

    private synchronized void switchMulticastGroup(String newGroupIP) {
        if (!connected || !inMcastGroup) return;
        if (currentMcastGroupAddress != null && currentMcastGroupAddress.getAddress().getHostAddress().equals(newGroupIP)) {
            return; // Đang ở đúng nhóm đó rồi
        }
        appendSystemMessage("🔄 Đang chuyển sang nhóm: <b>" + newGroupIP + "</b>...", "blue");
        joinMulticastGroup(newGroupIP);
    }

    private void updateStatusLabel() {
        if (!connected) return;
        int bcastPort = Integer.parseInt(txtBcastPort.getText().trim());
        int mcastPort = Integer.parseInt(txtMcastPort.getText().trim());
        String mcastIP = getSelectedMcastIP();
        String mcastStatusText = inMcastGroup ?
                "<b style='color:#6A1B9A;'>📡 Multicast (" + mcastIP + ":" + mcastPort + ") [ĐANG THAM GIA]</b>" :
                "<b style='color:#E65100;'>📡 Multicast [ĐÃ RỜI NHÓM]</b>";

        lblStatus.setText("<html><b style='color:#2E7D32;'>🟢 ĐANG HOẠT ĐỘNG:</b> " +
                "📢 Broadcast (Port " + bcastPort + ") & " + mcastStatusText +
                " | Card: <b>" + currentCard.displayName + " (" + currentCard.ip + ")</b></html>");
    }

    // ───────────────────────────────────────────────────────────────────────────
    // CÁC LUỒNG LẮNG NGHE (LISTENER THREADS)
    // ───────────────────────────────────────────────────────────────────────────

    private void startBroadcastListener(int port) {
        bcastThread = new Thread(() -> {
            try {
                bcastSocket = new DatagramSocket(null);
                bcastSocket.setReuseAddress(true);
                bcastSocket.bind(new InetSocketAddress(port));
                bcastSocket.setBroadcast(true);

                byte[] buf = new byte[4096];
                while (connected && !bcastSocket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    bcastSocket.receive(packet);
                    String raw = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    String senderIP = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                    handleIncomingPacket("BROADCAST", senderIP, raw);
                }
            } catch (SocketException se) {
                // Socket đã được đóng có chủ đích khi stopChat()
            } catch (Exception e) {
                if (connected) appendSystemMessage("⚠️ Lỗi luồng Broadcast: " + e.getMessage(), "red");
            }
        }, "BcastListenerThread");
        bcastThread.setDaemon(true);
        bcastThread.start();
    }

    private void startMulticastListener(String groupIP, int port, NetworkHelper.CardInfo card) {
        mcastThread = new Thread(() -> {
            try {
                mcastSocket = new MulticastSocket(null);
                mcastSocket.setReuseAddress(true);
                mcastSocket.bind(new InetSocketAddress(port));

                if (card != null && card.nif != null) {
                    try { mcastSocket.setNetworkInterface(card.nif); } catch (Exception ignored) {}
                }

                InetAddress group = InetAddress.getByName(groupIP);
                currentMcastGroupAddress = new InetSocketAddress(group, port);

                if (inMcastGroup) {
                    if (card != null && card.nif != null) {
                        try {
                            mcastSocket.joinGroup(currentMcastGroupAddress, card.nif);
                        } catch (Exception ex) {
                            mcastSocket.joinGroup(currentMcastGroupAddress, null);
                        }
                    } else {
                        mcastSocket.joinGroup(currentMcastGroupAddress, null);
                    }
                }

                byte[] buf = new byte[4096];
                while (connected && !mcastSocket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    mcastSocket.receive(packet);

                    // Nếu người dùng đã chọn RỜI NHÓM mà gói tin vẫn lọt vào socket thì bỏ qua
                    if (!inMcastGroup) continue;

                    String raw = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    String senderIP = packet.getAddress().getHostAddress();
                    handleIncomingPacket("MULTICAST", senderIP, raw);
                }
            } catch (SocketException se) {
                // Socket đóng có chủ đích
            } catch (Exception e) {
                if (connected) appendSystemMessage("⚠️ Lỗi luồng Multicast: " + e.getMessage(), "red");
            }
        }, "McastListenerThread");
        mcastThread.setDaemon(true);
        mcastThread.start();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GỬI VÀ NHẬN TIN NHẮN (MESSAGING LOGIC)
    // ═══════════════════════════════════════════════════════════════════════════

    private void sendMessage() {
        String text = txtInput.getText().trim();
        if (text.isEmpty()) return;

        if (!connected) {
            appendSystemMessage("⚠️ Chưa kết nối mạng! Vui lòng kiểm tra và bấm nút kết nối.", "orange");
            return;
        }

        String nick = txtNick.getText().trim();
        if (nick.isEmpty()) nick = "User";

        int modeIndex = cbSendMode.getSelectedIndex();
        boolean doBcast = (modeIndex == 0 || modeIndex == 2);
        boolean doMcast = (modeIndex == 1 || modeIndex == 2);

        String fullMessage = nick + ": " + text;

        // Sinh msgId duy nhất để lọc chính xác tin nhắn echo của máy mình và chống lặp
        String msgId = UUID.randomUUID().toString().substring(0, 8);
        long now = System.currentTimeMillis();
        mySentMsgIds.put(msgId, now);
        mySentMsgIds.entrySet().removeIf(e -> now - e.getValue() > 15000);

        try {
            if (doBcast) {
                String bcastIP = txtBcastIP.getText().trim();
                int bcastPort = Integer.parseInt(txtBcastPort.getText().trim());
                String wirePayload = "#MSG#" + msgId + "#[BCAST]#" + fullMessage;
                sendBroadcast(wirePayload, bcastIP, bcastPort);
            }
            if (doMcast) {
                String mcastIP = getSelectedMcastIP();
                int mcastPort = Integer.parseInt(txtMcastPort.getText().trim());
                String wirePayload = "#MSG#" + msgId + "#[MCAST]#" + fullMessage;
                sendMulticast(wirePayload, mcastIP, mcastPort);
            }

            // Vẽ tin nhắn của chính mình lên giao diện
            String modeText = modeIndex == 0 ? "BROADCAST" : (modeIndex == 1 ? "MULTICAST" : "CẢ HAI (BCAST + MCAST)");
            appendMyMessage(nick, text, modeText);

            txtInput.setText("");
            txtInput.requestFocus();

        } catch (Exception ex) {
            appendSystemMessage("❌ Lỗi khi gửi tin: " + ex.getMessage(), "red");
        }
    }

    private void sendBroadcast(String payload, String ip, int port) throws Exception {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        DatagramSocket socket;
        if (currentCard != null && currentCard.ip != null) {
            try {
                socket = new DatagramSocket(new InetSocketAddress(InetAddress.getByName(currentCard.ip), 0));
            } catch (Exception ex) {
                socket = new DatagramSocket();
            }
        } else {
            socket = new DatagramSocket();
        }

        try (DatagramSocket s = socket) {
            s.setBroadcast(true);

            // Ưu tiên dùng Subnet Broadcast của card (vd 192.168.137.255) để Windows định tuyến chính xác 100%
            String targetIP = "255.255.255.255";
            if (ip != null && !ip.trim().isEmpty() && !ip.trim().equals("255.255.255.255")) {
                targetIP = ip.trim();
            } else if (currentCard != null && currentCard.broadcastIp != null) {
                targetIP = currentCard.broadcastIp;
            } else if (ip != null && !ip.trim().isEmpty()) {
                targetIP = ip.trim();
            }

            InetAddress addr = InetAddress.getByName(targetIP);
            s.send(new DatagramPacket(data, data.length, addr, port));
        }
    }

    private void sendMulticast(String payload, String groupIP, int port) throws Exception {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        try (MulticastSocket socket = new MulticastSocket()) {
            if (currentCard != null && currentCard.nif != null) {
                try { socket.setNetworkInterface(currentCard.nif); } catch (Exception ignored) {}
            }
            socket.setTimeToLive(32);
            InetAddress group = InetAddress.getByName(groupIP);
            socket.send(new DatagramPacket(data, data.length, group, port));
        }
    }

    private void handleIncomingPacket(String defaultChannel, String senderIP, String raw) {
        String channel = defaultChannel;

        // 1. Kiểm tra format có gắn msgId
        if (raw.startsWith("#MSG#")) {
            int secondHash = raw.indexOf('#', 5);
            if (secondHash > 5) {
                String incomingId = raw.substring(5, secondHash);

                // Nếu chính máy này gửi ra gói tin này thì bỏ qua (lọc loopback)
                if (chkFilterSelf.isSelected() && mySentMsgIds.containsKey(incomingId)) {
                    return;
                }

                // Chống nhận trùng lặp: Nếu đã xử lý msgId này trong vòng 10 giây qua thì bỏ qua
                long now = System.currentTimeMillis();
                receivedMsgIds.entrySet().removeIf(e -> now - e.getValue() > 10000);
                if (receivedMsgIds.putIfAbsent(incomingId, now) != null) {
                    return; // Gói tin bản sao bị drop ngay lập tức!
                }

                raw = raw.substring(secondHash + 1); // Bỏ qua phần header ID
            }
        }

        // 2. Nhận diện kênh truyền chính xác qua tiền tố payload
        if (raw.startsWith("[BCAST]#")) {
            channel = "BROADCAST";
            raw = raw.substring(8);
        } else if (raw.startsWith("[MCAST]#")) {
            channel = "MULTICAST";
            raw = raw.substring(8);
        }

        // Tách Nickname và Nội dung
        String senderNick = "Khách";
        String content = raw;
        int colonIdx = raw.indexOf(":");
        if (colonIdx > 0) {
            senderNick = raw.substring(0, colonIdx).trim();
            content = raw.substring(colonIdx + 1).trim();
        }

        appendReceivedMessage(channel, senderNick, senderIP, content);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // RENDER TIN NHẮN ĐẸP MẮT VỚI CHỮ ĐEN & NỀN PASTEL
    // ═══════════════════════════════════════════════════════════════════════════

    private void appendMyMessage(String nick, String text, String modeText) {
        String timeStr = TIME_FMT.format(new Date());
        String badgeBg = modeText.contains("CẢ HAI") ? "#C8E6C9" : (modeText.contains("MULTICAST") ? "#E1BEE7" : "#BBDEFB");
        String borderCol = modeText.contains("CẢ HAI") ? "#2E7D32" : (modeText.contains("MULTICAST") ? "#6A1B9A" : "#1565C0");

        String html = String.format(
                "<div style='margin-bottom:8px; padding:6px 10px; background:#FFF8E1; border:1px solid #FFE082; border-left:5px solid #FF8F00; font-family:sans-serif;'>" +
                "<span style='color:#000000; font-size:11px; font-weight:bold;'>[%s]</span> " +
                "<span style='background:%s; color:#000000; font-size:11px; font-weight:bold; padding:2px 6px; border:1px solid %s; border-radius:3px;'>GỬI %s</span> " +
                "<b style='color:#000000; font-size:13px;'>👤 Bạn (%s):</b> " +
                "<span style='font-size:13px; color:#000000;'>%s</span>" +
                "</div>",
                timeStr, badgeBg, borderCol, modeText, escapeHtml(nick), escapeHtml(text));

        insertHtml(html);
    }

    private void appendReceivedMessage(String channel, String nick, String senderIP, String text) {
        String timeStr = TIME_FMT.format(new Date());
        boolean isMcast = channel.contains("MULTICAST");

        String cardBg   = isMcast ? "#F3E5F5" : "#E3F2FD";
        String borderCol = isMcast ? "#AB47BC" : "#42A5F5";
        String badgeBg  = isMcast ? "#E1BEE7" : "#BBDEFB";
        String badgeBorder = isMcast ? "#6A1B9A" : "#1565C0";
        String icon     = isMcast ? "📡" : "📢";

        String html = String.format(
                "<div style='margin-bottom:8px; padding:6px 10px; background:%s; border:1px solid %s; border-left:5px solid %s; font-family:sans-serif;'>" +
                "<span style='color:#000000; font-size:11px; font-weight:bold;'>[%s]</span> " +
                "<span style='background:%s; color:#000000; font-size:11px; font-weight:bold; padding:2px 6px; border:1px solid %badgeBorder; border-radius:3px;'>%s %s</span> " +
                "<b style='color:#000000; font-size:13px;'>%s</b> <span style='color:#000000; font-size:11px;'>(%s):</span> " +
                "<span style='font-size:13px; color:#000000; font-weight:500;'>%s</span>" +
                "</div>",
                cardBg, borderCol, badgeBorder, timeStr, badgeBg, icon, channel, escapeHtml(nick), escapeHtml(senderIP), escapeHtml(text))
                .replace("%badgeBorder", badgeBorder);

        insertHtml(html);
    }

    private void appendSystemMessage(String text, String colorType) {
        String timeStr = TIME_FMT.format(new Date());
        String bg = "#ECEFF1";
        String border = "#607D8B";

        if ("green".equals(colorType)) {
            bg = "#E8F5E9"; border = "#4CAF50";
        } else if ("red".equals(colorType)) {
            bg = "#FFEBEE"; border = "#E53935";
        } else if ("orange".equals(colorType)) {
            bg = "#FFF3E0"; border = "#FB8C00";
        } else if ("blue".equals(colorType)) {
            bg = "#E3F2FD"; border = "#1E88E5";
        }

        String html = String.format(
                "<div style='margin-bottom:6px; padding:4px 10px; background:%s; border:1px solid %s; border-left:4px solid %s; font-family:sans-serif; font-size:12px; color:#000000;'>" +
                "<b>[%s] ⚙️ HỆ THỐNG:</b> %s</div>",
                bg, border, border, timeStr, text);

        insertHtml(html);
    }

    private void insertHtml(String html) {
        SwingUtilities.invokeLater(() -> {
            try {
                javax.swing.text.Element body = htmlDoc.getDefaultRootElement().getElement(0);
                if (body != null) {
                    htmlDoc.insertBeforeEnd(body, html);
                } else {
                    htmlKit.insertHTML(htmlDoc, htmlDoc.getLength(), html, 0, 0, javax.swing.text.html.HTML.Tag.BODY);
                }
                if (chkAutoScroll.isSelected()) {
                    chatPane.setCaretPosition(htmlDoc.getLength());
                }
            } catch (Exception ignored) {}
        });
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void showHelpDialog() {
        String help = """
                <html>
                <body style='color:#000000; font-family:sans-serif;'>
                <h2>📡 So sánh Broadcast & Multicast trong UDP</h2>
                <hr>
                <p><b>1. BROADCAST (Quảng bá toàn mạng):</b><br>
                - Gói tin gửi tới địa chỉ Subnet Broadcast (vd <code>192.168.137.255</code>) hoặc <code>255.255.255.255</code>.<br>
                - <b>Tất cả mọi máy</b> trong cùng mạng LAN/Hotspot đều nhận được tin nhắn nếu mở cổng tương ứng.<br>
                - Không cần đăng ký hay gia nhập bất kỳ nhóm nào cả.</p>
                
                <p><b>2. MULTICAST (Đa điểm theo nhóm):</b><br>
                - Gói tin gửi tới một địa chỉ lớp D (vd <code>239.1.1.1</code>, <code>239.1.1.2</code>...).<br>
                - <b>Chỉ những máy nào</b> đã bấm <b>'Tham gia nhóm' (joinGroup)</b> vào đúng IP nhóm đó mới nhận được tin nhắn.<br>
                - Nếu bạn bấm <b>'Rời nhóm'</b> hoặc chọn nhóm khác, bạn sẽ không nhận được tin của nhóm cũ nữa.</p>
                
                <p><b>3. CÁCH DEMO CHO THẦY XEM:</b><br>
                - <b>Bước 1:</b> Trên Máy B, bấm nút <b>'🚪 Rời nhóm'</b>.<br>
                - <b>Bước 2:</b> Trên Máy A, gửi tin <b>Multicast</b> &rarr; Máy B <b>không nhận được</b>.<br>
                - <b>Bước 3:</b> Trên Máy A, gửi tin <b>Broadcast</b> &rarr; Máy B <b>nhận được bình thường</b>.<br>
                - <b>Bước 4:</b> Trên Máy B, bấm <b>'✅ Vào nhóm'</b> &rarr; Máy A gửi Multicast lại &rarr; Máy B <b>nhận được ngay</b>.</p>
                </body>
                </html>
                """;
        JOptionPane.showMessageDialog(this, new JLabel(help), "Kiến thức Mạng UDP", JOptionPane.INFORMATION_MESSAGE);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HÀM MAIN
    // ═══════════════════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            UnifiedChatGUI app = new UnifiedChatGUI();
            app.setVisible(true);
        });
    }
}
