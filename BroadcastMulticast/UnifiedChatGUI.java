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
 * Phòng Chat Hợp Nhất (Gộp cả UDP Broadcast và Multicast vào cùng 1 đoạn chat).
 * 
 * Tính năng chính:
 * 1. Gửi và nhận 2 chiều trên cùng 1 cửa sổ chat (không chia tách Sender/Receiver).
 * 2. Tích hợp đồng thời cả Broadcast (toàn mạng) và Multicast (theo nhóm 239.1.1.1).
 * 3. Tùy chọn chế độ gửi: Broadcast, Multicast, hoặc Gửi cả hai cùng lúc.
 * 4. Tự động nhận diện card Mobile Hotspot (192.168.137.x) và tự điền IP Subnet Broadcast.
 * 5. Lọc echo loopback tránh trùng lặp tin nhắn tự gửi.
 * 6. Hỗ trợ mở nhiều cửa sổ chat để test nhiều người trên cùng 1 máy tính.
 */
public class UnifiedChatGUI extends JFrame {

    // Màu sắc chủ đạo
    private static final Color C_PRIMARY    = new Color(0x1565C0); // Xanh dương Broadcast
    private static final Color C_MULTICAST  = new Color(0x6A1B9A); // Tím Multicast
    private static final Color C_BOTH       = new Color(0x2E7D32); // Xanh lá Cả hai
    private static final Color C_MY_MSG     = new Color(0xE65100); // Cam tin của tôi
    private static final Color C_BG         = new Color(0xF5F7FA); // Nền xám dịu
    private static final Color C_PANEL      = Color.WHITE;

    // Thành phần giao diện
    private JTextField txtNick;
    private JComboBox<NetworkHelper.CardInfo> cbCards;
    private JTextField txtBcastIP;
    private JTextField txtBcastPort;
    private JTextField txtMcastIP;
    private JTextField txtMcastPort;
    private JButton btnConnect;
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
    private volatile boolean connected = false;
    private DatagramSocket bcastSocket;
    private MulticastSocket mcastSocket;
    private InetSocketAddress mcastGroupAddress;
    private NetworkHelper.CardInfo currentCard;

    private Thread bcastThread;
    private Thread mcastThread;

    // Bộ nhớ đệm tin nhắn đã gửi gần đây (để lọc loopback/echo của chính máy mình)
    private final ConcurrentHashMap<String, Long> recentSentMessages = new ConcurrentHashMap<>();

    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");

    public UnifiedChatGUI() {
        this(System.getProperty("user.name", "User"));
    }

    public UnifiedChatGUI(String initialNick) {
        super("💬 UDP Unified Chat — Gộp Broadcast & Multicast");
        initUI(initialNick);
        // Tự động kết nối khi khởi động
        SwingUtilities.invokeLater(this::startChat);
    }

    private void initUI(String initialNick) {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(860, 680);
        setMinimumSize(new Dimension(720, 520));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(C_BG);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ═══════════════════════════════════════════════════════════════════════
        // 1. THANH MENU (MENU BAR)
        // ═══════════════════════════════════════════════════════════════════════
        setJMenuBar(createMenuBar());

        // ═══════════════════════════════════════════════════════════════════════
        // 2. PHẦN CẤU HÌNH TRÊN CÙNG (TOP CONFIG PANEL)
        // ═══════════════════════════════════════════════════════════════════════
        JPanel topPanel = createConfigPanel(initialNick);
        root.add(topPanel, BorderLayout.NORTH);

        // ═══════════════════════════════════════════════════════════════════════
        // 3. KHUNG HIỂN THỊ CHAT TRUNG TÂM (CENTER CHAT PANE)
        // ═══════════════════════════════════════════════════════════════════════
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setContentType("text/html");
        htmlKit = new HTMLEditorKit();
        htmlDoc = new HTMLDocument();
        chatPane.setEditorKit(htmlKit);
        chatPane.setDocument(htmlDoc);
        chatPane.setBackground(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(chatPane);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(0xCFD8DC), 1));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        root.add(scrollPane, BorderLayout.CENTER);

        // ═══════════════════════════════════════════════════════════════════════
        // 4. PHẦN NHẬP VÀ GỬI TIN NHẮN DƯỚI CÙNG (BOTTOM INPUT PANEL)
        // ═══════════════════════════════════════════════════════════════════════
        JPanel bottomPanel = createInputPanel();
        root.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(root);

        // Đóng socket sạch sẽ khi tắt cửa sổ
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopChat();
            }
        });
    }

    private JMenuBar createMenuBar() {
        JMenuBar mb = new JMenuBar();

        JMenu mFile = new JMenu("Phòng Chat");
        JMenuItem miNewWindow = new JMenuItem("➕ Mở thêm cửa sổ Chat (Test 2 người)");
        miNewWindow.addActionListener(e -> {
            UnifiedChatGUI other = new UnifiedChatGUI("User_" + (new Random().nextInt(900) + 100));
            other.setLocation(getX() + 40, getY() + 40);
            other.setVisible(true);
        });
        JMenuItem miExit = new JMenuItem("Thoát");
        miExit.addActionListener(e -> {
            stopChat();
            dispose();
        });
        mFile.add(miNewWindow);
        mFile.addSeparator();
        mFile.add(miExit);

        JMenu mLegacy = new JMenu("Công cụ Demo cũ");
        JMenuItem miMainMenu = new JMenuItem("Mở Menu Chọn Cũ (Main Menu)");
        miMainMenu.addActionListener(e -> NetworkDemo.showMainMenu(new String[0]));
        mLegacy.add(miMainMenu);
        mLegacy.addSeparator();
        JMenuItem miBcastSender = new JMenuItem("Mở Broadcast Sender riêng");
        miBcastSender.addActionListener(e -> {
            NetworkHelper.CardInfo card = (NetworkHelper.CardInfo) cbCards.getSelectedItem();
            String cardIp = card != null ? card.ip : null;
            BroadcastSenderGUI.launch(txtNick.getText().trim(), txtBcastIP.getText().trim(),
                    Integer.parseInt(txtBcastPort.getText().trim()), "Chào từ Broadcast", cardIp);
        });
        JMenuItem miBcastReceiver = new JMenuItem("Mở Broadcast Receiver riêng");
        miBcastReceiver.addActionListener(e -> {
            NetworkHelper.CardInfo card = (NetworkHelper.CardInfo) cbCards.getSelectedItem();
            String cardIp = card != null ? card.ip : null;
            BroadcastReceiverGUI.launch(txtNick.getText().trim(), Integer.parseInt(txtBcastPort.getText().trim()), cardIp);
        });
        JMenuItem miMcastSender = new JMenuItem("Mở Multicast Sender riêng");
        miMcastSender.addActionListener(e -> {
            NetworkHelper.CardInfo card = (NetworkHelper.CardInfo) cbCards.getSelectedItem();
            String cardIp = card != null ? card.ip : null;
            MulticastSenderGUI.launch(txtNick.getText().trim(), txtMcastIP.getText().trim(),
                    Integer.parseInt(txtMcastPort.getText().trim()), "Chào từ Multicast", cardIp);
        });
        JMenuItem miMcastReceiver = new JMenuItem("Mở Multicast Receiver riêng");
        miMcastReceiver.addActionListener(e -> {
            NetworkHelper.CardInfo card = (NetworkHelper.CardInfo) cbCards.getSelectedItem();
            String cardIp = card != null ? card.ip : null;
            MulticastReceiverGUI.launch(txtNick.getText().trim(), txtMcastIP.getText().trim(),
                    Integer.parseInt(txtMcastPort.getText().trim()), cardIp);
        });
        mLegacy.add(miBcastSender);
        mLegacy.add(miBcastReceiver);
        mLegacy.addSeparator();
        mLegacy.add(miMcastSender);
        mLegacy.add(miMcastReceiver);

        JMenu mHelp = new JMenu("Trợ giúp");
        JMenuItem miHelp = new JMenuItem("Nguyên lý Broadcast vs Multicast");
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
                BorderFactory.createLineBorder(new Color(0xCFD8DC), 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));

        // Hàng 1: Nickname + Card mạng + Nút Kết nối
        JPanel row1 = new JPanel(new GridBagLayout());
        row1.setBackground(C_PANEL);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 4, 3, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        row1.add(new JLabel("👤 Tên hiển thị:"), gbc);

        txtNick = new JTextField(initialNick, 10);
        txtNick.setFont(new Font("SansSerif", Font.BOLD, 12));
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.25;
        row1.add(txtNick, gbc);

        gbc.gridx = 2; gbc.gridy = 0; gbc.weightx = 0;
        row1.add(new JLabel("🌐 Card mạng:"), gbc);

        java.util.List<NetworkHelper.CardInfo> cards = NetworkHelper.getAvailableCards();
        cbCards = new JComboBox<>(cards.toArray(new NetworkHelper.CardInfo[0]));
        cbCards.setFont(new Font("SansSerif", Font.PLAIN, 12));
        gbc.gridx = 3; gbc.gridy = 0; gbc.weightx = 0.65;
        row1.add(cbCards, gbc);

        btnConnect = new JButton("🔴 Đang ngắt");
        btnConnect.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnConnect.setFocusPainted(false);
        btnConnect.setBackground(new Color(0xD32F2F));
        btnConnect.setForeground(Color.WHITE);
        btnConnect.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnConnect.addActionListener(e -> {
            if (connected) {
                stopChat();
            } else {
                startChat();
            }
        });
        gbc.gridx = 4; gbc.gridy = 0; gbc.weightx = 0;
        row1.add(btnConnect, gbc);

        // Hàng 2: Cấu hình IP/Port Broadcast & Multicast
        JPanel row2 = new JPanel(new GridBagLayout());
        row2.setBackground(C_PANEL);

        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc2.insets = new Insets(3, 4, 3, 6);
        gbc2.fill = GridBagConstraints.HORIZONTAL;

        // Broadcast settings
        gbc2.gridx = 0; gbc2.gridy = 0; gbc2.weightx = 0;
        JLabel lblBcast = new JLabel("📢 Broadcast IP/Port:");
        lblBcast.setForeground(C_PRIMARY);
        lblBcast.setFont(new Font("SansSerif", Font.BOLD, 12));
        row2.add(lblBcast, gbc2);

        String initialBcast = "255.255.255.255";
        if (!cards.isEmpty() && cards.get(0).broadcastIp != null) {
            initialBcast = cards.get(0).broadcastIp;
        }
        txtBcastIP = new JTextField(initialBcast, 12);
        gbc2.gridx = 1; gbc2.gridy = 0; gbc2.weightx = 0.35;
        row2.add(txtBcastIP, gbc2);

        txtBcastPort = new JTextField("7000", 5);
        gbc2.gridx = 2; gbc2.gridy = 0; gbc2.weightx = 0.15;
        row2.add(txtBcastPort, gbc2);

        // Multicast settings
        gbc2.gridx = 3; gbc2.gridy = 0; gbc2.weightx = 0;
        JLabel lblMcast = new JLabel("📡 Multicast Nhóm/Port:");
        lblMcast.setForeground(C_MULTICAST);
        lblMcast.setFont(new Font("SansSerif", Font.BOLD, 12));
        row2.add(lblMcast, gbc2);

        txtMcastIP = new JTextField("239.1.1.1", 10);
        gbc2.gridx = 4; gbc2.gridy = 0; gbc2.weightx = 0.35;
        row2.add(txtMcastIP, gbc2);

        txtMcastPort = new JTextField("8000", 5);
        gbc2.gridx = 5; gbc2.gridy = 0; gbc2.weightx = 0.15;
        row2.add(txtMcastPort, gbc2);

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
        lblStatus.setFont(new Font("SansSerif", Font.ITALIC, 12));
        row3.add(lblStatus, BorderLayout.WEST);

        JPanel opts = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        opts.setBackground(C_PANEL);
        chkAutoScroll = new JCheckBox("Tự cuộn xuống", true);
        chkAutoScroll.setBackground(C_PANEL);
        chkFilterSelf = new JCheckBox("Lọc tin nhắn lặp của chính mình", true);
        chkFilterSelf.setBackground(C_PANEL);
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
                "📡 Multicast (Nhóm 239.1.1.1)",
                "📢+📡 Cả hai (Broadcast & Multicast)"
        };
        cbSendMode = new JComboBox<>(modes);
        cbSendMode.setFont(new Font("SansSerif", Font.BOLD, 12));
        cbSendMode.setPreferredSize(new Dimension(240, 36));
        sendRow.add(cbSendMode, BorderLayout.WEST);

        // Ô nhập tin nhắn
        txtInput = new JTextField();
        txtInput.setFont(new Font("SansSerif", Font.PLAIN, 14));
        txtInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x90CAF9), 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        txtInput.addActionListener(e -> sendMessage());
        sendRow.add(txtInput, BorderLayout.CENTER);

        // Các nút bấm
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btnPanel.setBackground(C_BG);

        btnSend = new JButton("📤 Gửi / Send");
        btnSend.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnSend.setBackground(C_PRIMARY);
        btnSend.setForeground(Color.WHITE);
        btnSend.setFocusPainted(false);
        btnSend.setPreferredSize(new Dimension(110, 36));
        btnSend.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSend.addActionListener(e -> sendMessage());

        btnPing = new JButton("⚡ Ping");
        btnPing.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btnPing.setFocusPainted(false);
        btnPing.setPreferredSize(new Dimension(75, 36));
        btnPing.setToolTipText("Gửi tin nhắn kiểm tra nhanh");
        btnPing.addActionListener(e -> {
            txtInput.setText("👋 Xin chào từ " + txtNick.getText().trim() + "! (Ping lúc " + TIME_FMT.format(new Date()) + ")");
            sendMessage();
        });

        btnClear = new JButton("🗑 Xóa");
        btnClear.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btnClear.setFocusPainted(false);
        btnClear.setPreferredSize(new Dimension(75, 36));
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
            String mcastIP = txtMcastIP.getText().trim();

            connected = true;

            // Xử lý thông minh cổng:
            if (bcastPort == mcastPort) {
                // Nếu cùng port: 1 MulticastSocket duy nhất vừa joinGroup vừa nhận Broadcast!
                startUnifiedListener(mcastIP, bcastPort, currentCard);
            } else {
                // Nếu 2 port khác nhau: 2 listener riêng biệt hoàn toàn không lo xung đột
                startBroadcastListener(bcastPort);
                startMulticastListener(mcastIP, mcastPort, currentCard);
            }

            // Cập nhật trạng thái giao diện
            btnConnect.setText("🟢 Đang hoạt động");
            btnConnect.setBackground(new Color(0x2E7D32));
            String hsTag = currentCard.isHotspot ? " 🔥[HOTSPOT]" : "";
            lblStatus.setText("<html><b style='color:#2E7D32;'>🟢 ĐANG LẮNG NGHE:</b> " +
                    "Broadcast (port " + bcastPort + ") & Multicast (" + mcastIP + ":" + mcastPort + ") | Card: " +
                    currentCard.displayName + " (" + currentCard.ip + ")" + hsTag + "</html>");

            appendSystemMessage("✅ <b>ĐÃ KẾT NỐI PHÒNG CHAT THÀNH CÔNG!</b><br>" +
                    "• Card: <b>" + currentCard.displayName + " (" + currentCard.ip + ")" + hsTag + "</b><br>" +
                    "• Đang nhận Broadcast tại cổng: <b>" + bcastPort + "</b><br>" +
                    "• Đang tham gia nhóm Multicast: <b>" + mcastIP + ":" + mcastPort + "</b>", "green");

        } catch (Exception ex) {
            connected = false;
            btnConnect.setText("🔴 Kết nối lại");
            btnConnect.setBackground(new Color(0xD32F2F));
            lblStatus.setText("<html><b style='color:red;'>❌ LỖI:</b> " + ex.getMessage() + "</html>");
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
                if (mcastGroupAddress != null && currentCard != null && currentCard.nif != null) {
                    mcastSocket.leaveGroup(mcastGroupAddress, currentCard.nif);
                }
            } catch (Exception ignored) {}
            try { mcastSocket.close(); } catch (Exception ignored) {}
        }

        btnConnect.setText("🔴 Đã ngắt");
        btnConnect.setBackground(new Color(0xD32F2F));
        lblStatus.setText("<html><span style='color:gray;'>⚪ Đã ngắt kết nối. Nhấn nút để kết nối lại.</span></html>");
        appendSystemMessage("⏹ Đã ngắt kết nối và rời nhóm multicast.", "gray");
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
                mcastSocket = new MulticastSocket(port);
                mcastSocket.setReuseAddress(true);
                if (card != null && card.nif != null) {
                    mcastSocket.setNetworkInterface(card.nif);
                }

                InetAddress group = InetAddress.getByName(groupIP);
                mcastGroupAddress = new InetSocketAddress(group, port);
                if (card != null && card.nif != null) {
                    mcastSocket.joinGroup(mcastGroupAddress, card.nif);
                }

                byte[] buf = new byte[4096];
                while (connected && !mcastSocket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    mcastSocket.receive(packet);
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

    /**
     * Trường hợp bcastPort == mcastPort: 1 MulticastSocket duy nhất nhận CẢ Broadcast lẫn Multicast.
     */
    private void startUnifiedListener(String groupIP, int port, NetworkHelper.CardInfo card) {
        mcastThread = new Thread(() -> {
            try {
                mcastSocket = new MulticastSocket(port);
                mcastSocket.setReuseAddress(true);
                if (card != null && card.nif != null) {
                    mcastSocket.setNetworkInterface(card.nif);
                }

                InetAddress group = InetAddress.getByName(groupIP);
                mcastGroupAddress = new InetSocketAddress(group, port);
                if (card != null && card.nif != null) {
                    mcastSocket.joinGroup(mcastGroupAddress, card.nif);
                }

                byte[] buf = new byte[4096];
                while (connected && !mcastSocket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    mcastSocket.receive(packet);
                    String raw = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    String senderIP = packet.getAddress().getHostAddress();

                    // Xác định xem đây là broadcast hay multicast qua địa chỉ hoặc nội dung
                    String channel = raw.startsWith("[BCAST]") ? "BROADCAST" :
                                    (raw.startsWith("[MCAST]") ? "MULTICAST" : "BROADCAST / MULTICAST");

                    handleIncomingPacket(channel, senderIP, raw);
                }
            } catch (SocketException se) {
                // Socket đóng
            } catch (Exception e) {
                if (connected) appendSystemMessage("⚠️ Lỗi luồng Unified: " + e.getMessage(), "red");
            }
        }, "UnifiedListenerThread");
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

        // Lưu vào cache để nhận diện loopback/echo của chính máy mình
        long now = System.currentTimeMillis();
        recentSentMessages.put(fullMessage, now);
        // Dọn dẹp các tin nhắn cũ hơn 5 giây
        recentSentMessages.entrySet().removeIf(e -> now - e.getValue() > 5000);

        try {
            if (doBcast) {
                String bcastIP = txtBcastIP.getText().trim();
                int bcastPort = Integer.parseInt(txtBcastPort.getText().trim());
                sendBroadcast(fullMessage, bcastIP, bcastPort);
            }
            if (doMcast) {
                String mcastIP = txtMcastIP.getText().trim();
                int mcastPort = Integer.parseInt(txtMcastPort.getText().trim());
                sendMulticast(fullMessage, mcastIP, mcastPort);
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
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            InetAddress addr = InetAddress.getByName(ip);
            socket.send(new DatagramPacket(data, data.length, addr, port));
        }
    }

    private void sendMulticast(String payload, String groupIP, int port) throws Exception {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        try (MulticastSocket socket = new MulticastSocket()) {
            if (currentCard != null && currentCard.nif != null) {
                socket.setNetworkInterface(currentCard.nif);
            }
            socket.setTimeToLive(1); // Chỉ trong mạng nội bộ LAN / Hotspot
            InetAddress group = InetAddress.getByName(groupIP);
            socket.send(new DatagramPacket(data, data.length, group, port));
        }
    }

    private void handleIncomingPacket(String channel, String senderIP, String raw) {
        // Lọc loopback: Nếu là tin do chính mình vừa gửi và người dùng bật lọc
        if (chkFilterSelf.isSelected() && recentSentMessages.containsKey(raw)) {
            return;
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
    // RENDER TIN NHẮN ĐẸP MẮT VỚI HTML / CSS
    // ═══════════════════════════════════════════════════════════════════════════

    private void appendMyMessage(String nick, String text, String modeText) {
        String timeStr = TIME_FMT.format(new Date());
        String badgeColor = modeText.contains("CẢ HAI") ? "#2E7D32" : (modeText.contains("MULTICAST") ? "#6A1B9A" : "#1565C0");
        String html = String.format(
                "<div style='margin-bottom:8px; padding:6px 10px; background:#FFF3E0; border-left:4px solid #E65100; font-family:sans-serif;'>" +
                "<span style='color:#757575; font-size:11px;'>[%s]</span> " +
                "<span style='background:%s; color:white; font-size:10px; font-weight:bold; padding:2px 6px; border-radius:3px;'>GỬI %s</span> " +
                "<b style='color:#E65100; font-size:13px;'>👤 Bạn (%s):</b> " +
                "<span style='font-size:13px; color:#212121;'>%s</span>" +
                "</div>",
                timeStr, badgeColor, modeText, escapeHtml(nick), escapeHtml(text));

        insertHtml(html);
    }

    private void appendReceivedMessage(String channel, String nick, String senderIP, String text) {
        String timeStr = TIME_FMT.format(new Date());
        boolean isMcast = channel.contains("MULTICAST");
        String badgeBg = isMcast ? "#6A1B9A" : "#1565C0";
        String borderCol = isMcast ? "#8E24AA" : "#1E88E5";
        String cardBg = isMcast ? "#F3E5F5" : "#E3F2FD";
        String nickCol = isMcast ? "#4A148C" : "#0D47A1";
        String icon = isMcast ? "📡" : "📢";

        String html = String.format(
                "<div style='margin-bottom:8px; padding:6px 10px; background:%s; border-left:4px solid %s; font-family:sans-serif;'>" +
                "<span style='color:#757575; font-size:11px;'>[%s]</span> " +
                "<span style='background:%s; color:white; font-size:10px; font-weight:bold; padding:2px 6px; border-radius:3px;'>%s %s</span> " +
                "<b style='color:%s; font-size:13px;'>%s</b> <small style='color:#616161;'>(%s):</small> " +
                "<span style='font-size:13px; color:#212121; font-weight:500;'>%s</span>" +
                "</div>",
                cardBg, borderCol, timeStr, badgeBg, icon, channel, nickCol, escapeHtml(nick), escapeHtml(senderIP), escapeHtml(text));

        insertHtml(html);
    }

    private void appendSystemMessage(String text, String color) {
        String timeStr = TIME_FMT.format(new Date());
        String hex = switch (color) {
            case "red" -> "#C62828";
            case "green" -> "#2E7D32";
            case "blue" -> "#1565C0";
            case "orange" -> "#EF6C00";
            default -> "#616161";
        };
        String html = String.format(
                "<div style='margin-bottom:6px; padding:3px 8px; font-family:sans-serif; font-size:12px; color:%s;'>" +
                "<i>[%s] ⚙️ %s</i></div>",
                hex, timeStr, text);

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
                <h2>📡 So sánh Broadcast & Multicast trong UDP</h2>
                <hr>
                <p><b>1. BROADCAST (Phát thanh quảng bá):</b><br>
                - Gói tin gửi tới địa chỉ Subnet Broadcast (vd <code>192.168.137.255</code>) hoặc <code>255.255.255.255</code>.<br>
                - <b>Mọi máy</b> trong cùng mạng LAN/Hotspot đều nhận được gói tin nếu mở socket ở cùng Port.<br>
                - Không cần đăng ký hay gia nhập nhóm nào cả.</p>
                
                <p><b>2. MULTICAST (Phát thanh đa điểm theo nhóm):</b><br>
                - Gói tin gửi tới một địa chỉ lớp D (vd <code>239.1.1.1</code>).<br>
                - <b>Chỉ những máy nào</b> đã gọi <code>joinGroup()</code> trên card mạng đó mới nhận được gói tin.<br>
                - Tiết kiệm băng thông hơn Broadcast vì router/switch chỉ gửi cho thành viên nhóm.</p>
                
                <p><b>3. PHÒNG CHAT HỢP NHẤT NÀY:</b><br>
                - Cho phép bạn vừa gửi vừa nhận cả hai loại trên <b>cùng một giao diện</b>.<br>
                - Nhãn màu xanh <code>[📢 BROADCAST]</code>: Tin đến từ luồng phát thanh toàn mạng.<br>
                - Nhãn màu tím <code>[📡 MULTICAST]</code>: Tin đến từ luồng nhóm đăng ký.</p>
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
