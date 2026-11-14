import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSpinnerUI;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UDP Multicast Chat - Giao diện Dark Theme hiện đại (Discord / Telegram style).
 * 
 * Giữ nguyên 100% giao thức mạng với MulticastChat:
 * - Multicast 239.1.1.X:8000 (MSG, NACK, RESEND, HB)
 * - Broadcast qua cổng 8001 (BC)
 * - Đầy đủ bộ đệm cache, phát hiện mất gói (gaps), NACK và gửi lại.
 * - Hoàn toàn bằng Java Swing thuần, không cần cài thêm thư viện jar bên ngoài.
 */
public class MulticastChatGUI extends JFrame {
    private static final int PORT = 8000;
    private static final int BC_PORT = 8001;
    private static final int TTL = 1;
    private static final int MAX_PACKET = 16 * 1024;
    private static final int MAX_RESEND_RANGE = 50;
    private static final int CACHE_LIMIT = 500;
    private static final int HEARTBEAT_MS = 2000;
    private static final int OFFLINE_MS = 7000;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // =====================================================================
    //  BẢNG MÀU DARK THEME HIỆN ĐẠI (DISCORD / TELEGRAM PALETTE)
    // =====================================================================
    public static final Color BG_DARKEST    = new Color(250, 250, 250);   // Nền tổng thể
    public static final Color BG_SIDEBAR    = new Color(240, 240, 240);   // Sidebar trái/phải
    public static final Color BG_CARD       = new Color(255, 255, 255);   // Khung thẻ / container
    public static final Color BG_INPUT      = new Color(255, 255, 255);   // Ô nhập liệu
    public static final Color BG_HOVER      = new Color(230, 230, 230);   // Hover
    public static final Color BORDER_COLOR  = new Color(200, 200, 200);   // Đường viền tinh tế

    public static final Color ACCENT_PRIMARY= new Color(79, 70, 229); // Indigo / Blurple sáng
    public static final Color ACCENT_HOVER  = new Color(99, 102, 241);
    public static final Color ACCENT_GREEN  = new Color(16, 185, 129); // Online / Resend thành công
    public static final Color ACCENT_AMBER  = new Color(245, 158, 11); // Broadcast
    public static final Color ACCENT_RED    = new Color(239, 68, 68); // Thoát / Lỗi / Mất gói
    public static final Color ACCENT_CYAN   = new Color(14, 165, 233);  // Thông tin / Peer
    public static final Color ACCENT_PURPLE = new Color(168, 85, 247); // Room badge

    public static final Color TEXT_MAIN     = new Color(20, 20, 20); // Chữ đen
    public static final Color TEXT_SECONDARY= new Color(80, 80, 80); // Chữ xám
    public static final Color TEXT_MUTED    = new Color(130, 130, 130); // Chữ xám nhạt

    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 15);
    public static final Font FONT_MAIN  = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font FONT_BOLD  = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_MONO  = new Font("Consolas", Font.PLAIN, 12);

    private static final Map<String, Room> ROOMS = new LinkedHashMap<>();
    static {
        ROOMS.put("1", new Room("1", "Java", "239.1.1.1"));
        ROOMS.put("2", new Room("2", "Network", "239.1.1.2"));
        ROOMS.put("3", new Room("3", "IT1", "239.1.1.3"));
    }

    // ---------------- Trạng thái mạng ----------------
    private final String nodeId = UUID.randomUUID().toString();
    private final Object roomLock = new Object();
    private final String displayName;
    private final NetworkInterface networkInterface;
    private final String localIp;
    private final MulticastSocket socket;
    private final DatagramSocket bcSocket;
    private final AtomicLong bcSeq = new AtomicLong(0);
    private final Set<String> bcSeen = ConcurrentHashMap.newKeySet();
    private final List<InetAddress> bcTargets = new ArrayList<>();
    private volatile Room currentRoom;
    private volatile boolean running = true;

    private final AtomicInteger simulateDropEvery = new AtomicInteger(0);
    private final Set<String> simulatedDrops = ConcurrentHashMap.newKeySet();
    private final Map<String, AtomicLong> seqByRoom = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentHashMap<Long, ChatMessage>> sentCacheByRoom = new ConcurrentHashMap<>();
    private final Map<String, SenderTracker> trackerBySender = new ConcurrentHashMap<>();

    private final AtomicLong sSent = new AtomicLong(), sRecv = new AtomicLong(), sLost = new AtomicLong(),
            sNack = new AtomicLong(), sResent = new AtomicLong(), sRecovered = new AtomicLong(),
            sBcSent = new AtomicLong(), sBcRecv = new AtomicLong();

    // ---------------- Thành phần giao diện ----------------
    private final JTextPane chatPane = new JTextPane();
    private final JTextArea logArea = new JTextArea();
    private final JTextField input = new ModernTextField("Nhập tin nhắn (hoặc gõ /help để xem lệnh)...");
    private final JLabel headerUserLabel = new JLabel();
    private final JLabel headerIpLabel = new JLabel();
    private final JLabel headerRoomLabel = new JLabel();
    private final JLabel headerLossLabel = new JLabel();

    private final DefaultListModel<Room> roomModel = new DefaultListModel<>();
    private final JList<Room> roomList = new JList<>(roomModel);
    private final DefaultListModel<PeerItem> peerModel = new DefaultListModel<>();
    private final JList<PeerItem> peerList = new JList<>(peerModel);

    private final JSpinner lossSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
    private final JLabel[] statValues = new JLabel[8];
    private final JComboBox<String> sendMode = new JComboBox<>(new String[]{
            "[Phòng] Multicast", "[Toàn mạng] Broadcast"});

    private boolean roomListUpdating = false;
    private javax.swing.Timer uiTimer;
    private java.util.Timer heartbeatTimer;

    // =====================================================================
    //  KHỞI ĐỘNG & DIALOG ĐĂNG NHẬP
    // =====================================================================
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        UIManager.put("ComboBox.background", BG_INPUT);
        UIManager.put("ComboBox.foreground", TEXT_MAIN);
        UIManager.put("ComboBox.selectionBackground", ACCENT_PRIMARY);
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
        UIManager.put("TextField.background", BG_INPUT);
        UIManager.put("TextField.foreground", TEXT_MAIN);
        UIManager.put("TextField.caretForeground", TEXT_MAIN);
        UIManager.put("Label.foreground", TEXT_MAIN);
        UIManager.put("Panel.background", BG_DARKEST);
        SwingUtilities.invokeLater(MulticastChatGUI::showLogin);
    }

    /** Hộp thoại đăng nhập mang phong cách Modern Dark */
    private static void showLogin() {
        JDialog dialog = new JDialog((Frame) null, "UDP Multicast Chat - Đăng nhập", true);
        dialog.getContentPane().setBackground(BG_DARKEST);
        dialog.setLayout(new BorderLayout());

        // Header Banner
        JPanel banner = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, ACCENT_PRIMARY, getWidth(), getHeight(), new Color(130, 80, 223));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        banner.setPreferredSize(new Dimension(460, 75));
        banner.setLayout(new BoxLayout(banner, BoxLayout.Y_AXIS));
        banner.setBorder(new EmptyBorder(12, 20, 12, 20));

        JLabel titleLbl = new JLabel("UDP MULTICAST CHAT");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLbl.setForeground(TEXT_MAIN);
        JLabel subLbl = new JLabel("Ứng dụng chat nhóm P2P thời gian thực trong mạng LAN");
        subLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subLbl.setForeground(TEXT_SECONDARY);
        banner.add(titleLbl);
        banner.add(Box.createVerticalStrut(4));
        banner.add(subLbl);

        // Body Form
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(new EmptyBorder(18, 20, 10, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        JTextField nameField = new ModernTextField("Nhập tên của bạn...");
        nameField.setText(System.getProperty("user.name", "User"));

        List<String> ips = new ArrayList<>();
        JComboBox<String> ifaceBox = new JComboBox<>();
        styleComboBox(ifaceBox);
        ifaceBox.addItem("Tự động chọn card mạng phù hợp");
        ips.add("");
        try {
            NetworkInterface auto = chooseInterface(null);
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || !ni.supportsMulticast()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        boolean rec = auto != null && auto.getName().equals(ni.getName());
                        ifaceBox.addItem(a.getHostAddress() + " - " + ni.getDisplayName() + (rec ? " (Tự động)" : ""));
                        ips.add(a.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) { }

        JComboBox<Room> roomBox = new JComboBox<>(ROOMS.values().toArray(new Room[0]));
        styleComboBox(roomBox);

        c.gridx = 0; c.gridy = 0; c.weightx = 0.25;
        form.add(createFormLabel("Tên hiển thị:"), c);
        c.gridx = 1; c.weightx = 0.75;
        form.add(nameField, c);

        c.gridx = 0; c.gridy = 1; c.weightx = 0.25;
        form.add(createFormLabel("Card mạng:"), c);
        c.gridx = 1; c.weightx = 0.75;
        form.add(ifaceBox, c);

        c.gridx = 0; c.gridy = 2; c.weightx = 0.25;
        form.add(createFormLabel("Phòng ban đầu:"), c);
        c.gridx = 1; c.weightx = 0.75;
        form.add(roomBox, c);

        // Buttons
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 15));
        bottom.setOpaque(false);
        ModernButton cancelBtn = new ModernButton("Hủy", BG_CARD, BG_HOVER, TEXT_MAIN);
        ModernButton loginBtn = new ModernButton("Vào Chat", ACCENT_PRIMARY, ACCENT_HOVER, Color.WHITE);
        bottom.add(cancelBtn);
        bottom.add(loginBtn);

        cancelBtn.addActionListener(e -> { dialog.dispose(); System.exit(0); });

        final boolean[] success = {false};
        loginBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Vui lòng nhập tên hiển thị.", "Thông báo", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (name.length() > 24) name = name.substring(0, 24);
            String ip = ips.get(Math.max(0, ifaceBox.getSelectedIndex()));
            try {
                dialog.dispose();
                MulticastChatGUI f = new MulticastChatGUI(name, ip.isEmpty() ? null : ip, (Room) roomBox.getSelectedItem());
                f.setVisible(true);
                success[0] = true;
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null, "Không khởi động được socket:\n" + ex.getMessage(),
                        "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });

        dialog.add(banner, BorderLayout.NORTH);
        dialog.add(form, BorderLayout.CENTER);
        dialog.add(bottom, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(null);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setVisible(true);

        if (!success[0]) System.exit(0);
    }

    private static JLabel createFormLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_BOLD);
        l.setForeground(TEXT_MAIN);
        return l;
    }

    // =====================================================================
    //  KHỞI TẠO MẠNG VÀ CỬA SỔ CHÍNH
    // =====================================================================
    private MulticastChatGUI(String name, String ip, Room firstRoom) throws Exception {
        super("UDP Multicast Chat - " + name);
        this.displayName = name;
        this.networkInterface = chooseInterface(ip);
        if (networkInterface == null) {
            throw new IllegalStateException("Không tìm thấy card mạng IPv4 đang hoạt động và hỗ trợ multicast.");
        }
        this.localIp = ip != null ? ip : firstIpv4(networkInterface);

        socket = new MulticastSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(PORT));
        socket.setNetworkInterface(networkInterface);
        socket.setTimeToLive(TTL);
        socket.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, true);

        bcSocket = new DatagramSocket(null);
        bcSocket.setReuseAddress(true);
        bcSocket.setBroadcast(true);
        bcSocket.bind(new InetSocketAddress(BC_PORT));
        collectBroadcastTargets();

        buildUi();
        switchRoom(firstRoom);

        Thread t = new Thread(this::receiveLoop, "multicast-receiver");
        t.setDaemon(true);
        t.start();
        Thread tb = new Thread(this::broadcastReceiveLoop, "broadcast-receiver");
        tb.setDaemon(true);
        tb.start();

        heartbeatTimer = new java.util.Timer("heartbeat", true);
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            public void run() { sendHeartbeat(); }
        }, 300, HEARTBEAT_MS);

        uiTimer = new javax.swing.Timer(1000, e -> { refreshPeers(); refreshStats(); });
        uiTimer.start();

        system("Đã kết nối: card " + networkInterface.getDisplayName() + " (" + localIp + ")");
        StringBuilder bt = new StringBuilder();
        for (InetAddress a : bcTargets) bt.append(a.getHostAddress()).append(' ');
        system("Broadcast qua cổng " + BC_PORT + " tới: " + bt.toString().trim());
        system("Gõ /help để xem hướng dẫn lệnh. Chọn 'Gửi tới' để chuyển giữa Multicast và Broadcast.");
    }

    // =====================================================================
    //  THIẾT KẾ GIAO DIỆN (UI BUILDER)
    // =====================================================================
    private void buildUi() {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) { exitApp(); }
        });
        setSize(1100, 700);
        setMinimumSize(new Dimension(880, 560));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(BG_DARKEST);
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 1. THANH HEADER TRÊN CÙNG (STATUS BAR HIỆN ĐẠI)
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBackground(BG_SIDEBAR);
        header.setBorder(new EmptyBorder(8, 14, 8, 14));
        header.putClientProperty("border-radius", 8);

        JPanel headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        headerLeft.setOpaque(false);
        JLabel logo = new JLabel("UDP CHAT");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 15));
        logo.setForeground(ACCENT_PRIMARY);
        headerLeft.add(logo);

        headerUserLabel.setFont(FONT_BOLD);
        headerUserLabel.setForeground(TEXT_MAIN);
        headerLeft.add(createPill(displayName, BG_CARD, ACCENT_CYAN));

        headerIpLabel.setFont(FONT_SMALL);
        headerLeft.add(createPill(localIp, BG_CARD, TEXT_SECONDARY));

        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        headerRight.setOpaque(false);
        headerRoomLabel.setFont(FONT_BOLD);
        headerRight.add(headerRoomLabel);
        headerRight.add(headerLossLabel);

        header.add(headerLeft, BorderLayout.WEST);
        header.add(headerRight, BorderLayout.EAST);

        // 2. KHUNG BÊN TRÁI: PHÒNG CHAT + DANH SÁCH THÀNH VIÊN
        JPanel leftPanel = new JPanel(new BorderLayout(0, 10));
        leftPanel.setOpaque(false);
        leftPanel.setPreferredSize(new Dimension(240, 0));

        // Danh sách phòng
        for (Room r : ROOMS.values()) roomModel.addElement(r);
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomList.setBackground(BG_SIDEBAR);
        roomList.setCellRenderer(new RoomCellRenderer());
        roomList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || roomListUpdating) return;
            Room r = roomList.getSelectedValue();
            if (r != null) joinRoom(r);
        });

        JScrollPane roomScroll = createModernScrollPane(roomList);
        JPanel roomBoxPanel = createTitledCard("KÊNH PHÒNG (MULTICAST)", roomScroll);
        roomBoxPanel.setPreferredSize(new Dimension(240, 160));

        // Danh sách thành viên online
        peerList.setBackground(BG_SIDEBAR);
        peerList.setCellRenderer(new PeerCellRenderer());
        JScrollPane peerScroll = createModernScrollPane(peerList);
        JPanel peerBoxPanel = createTitledCard("THÀNH VIÊN ONLINE", peerScroll);

        leftPanel.add(roomBoxPanel, BorderLayout.NORTH);
        leftPanel.add(peerBoxPanel, BorderLayout.CENTER);

        // 3. KHUNG CHÍNH Ở GIỮA: NỘI DUNG CHAT + Ô NHẬP TIN NHẮN
        JPanel centerPanel = new JPanel(new BorderLayout(0, 8));
        centerPanel.setOpaque(false);

        chatPane.setEditable(false);
        chatPane.setBackground(BG_DARKEST);
        chatPane.setFont(FONT_MAIN);
        chatPane.setMargin(new Insets(10, 12, 10, 12));
        JScrollPane chatScroll = createModernScrollPane(chatPane);
        chatScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));

        // Thanh công cụ gửi tin
        JPanel bottomBar = new JPanel(new BorderLayout(8, 0));
        bottomBar.setOpaque(false);
        bottomBar.setBorder(new EmptyBorder(2, 0, 0, 0));

        styleComboBox(sendMode);
        sendMode.setPreferredSize(new Dimension(175, 40));

        ModernButton sendBtn = new ModernButton("Gửi", ACCENT_PRIMARY, ACCENT_HOVER, Color.WHITE);
        sendBtn.setPreferredSize(new Dimension(85, 40));
        ModernButton leaveBtn = new ModernButton("Thoát", BG_CARD, ACCENT_RED, TEXT_MAIN);
        leaveBtn.setPreferredSize(new Dimension(75, 40));

        input.setPreferredSize(new Dimension(200, 40));
        input.addActionListener(e -> onSubmit());
        sendBtn.addActionListener(e -> onSubmit());
        leaveBtn.addActionListener(e -> exitApp());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        actions.add(sendBtn);
        actions.add(leaveBtn);

        bottomBar.add(sendMode, BorderLayout.WEST);
        bottomBar.add(input, BorderLayout.CENTER);
        bottomBar.add(actions, BorderLayout.EAST);

        centerPanel.add(chatScroll, BorderLayout.CENTER);
        centerPanel.add(bottomBar, BorderLayout.SOUTH);

        // 4. KHUNG BÊN PHẢI: GIẢ LẬP MẤT GÓI + THỐNG KÊ + NHẬT KÝ GIAO THỨC
        JPanel rightPanel = new JPanel(new BorderLayout(0, 10));
        rightPanel.setOpaque(false);
        rightPanel.setPreferredSize(new Dimension(320, 0));

        // Card Giả lập mất gói
        JPanel lossInner = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        lossInner.setOpaque(false);
        JLabel lossTip = new JLabel("Bỏ gói có seq chia hết cho N:");
        lossTip.setFont(FONT_SMALL);
        lossTip.setForeground(TEXT_SECONDARY);
        styleSpinner(lossSpinner);
        lossSpinner.setPreferredSize(new Dimension(55, 30));
        ModernButton applyLoss = new ModernButton("Áp dụng", ACCENT_PRIMARY, ACCENT_HOVER, Color.WHITE);
        applyLoss.setFont(FONT_SMALL);
        applyLoss.setPreferredSize(new Dimension(75, 30));
        applyLoss.addActionListener(e -> setLoss((Integer) lossSpinner.getValue()));
        lossInner.add(lossTip);
        lossInner.add(lossSpinner);
        lossInner.add(applyLoss);

        JPanel lossCard = createTitledCard("GIẢ LẬP MẤT GÓI (DEMO NACK)", lossInner);

        // Card Thống kê
        JPanel statGrid = new JPanel(new GridLayout(4, 2, 8, 6));
        statGrid.setOpaque(false);
        statGrid.setBorder(new EmptyBorder(6, 8, 8, 8));

        String[] statNames = {
                "Đã gửi", "Đã nhận",
                "Mô phỏng mất", "NACK đã gửi",
                "Đã gửi lại", "Đã khôi phục",
                "Broadcast gửi", "Broadcast nhận"
        };
        Color[] statColors = {
                ACCENT_CYAN, ACCENT_GREEN,
                ACCENT_RED, ACCENT_AMBER,
                ACCENT_PURPLE, ACCENT_GREEN,
                ACCENT_AMBER, ACCENT_AMBER
        };

        for (int i = 0; i < 8; i++) {
            JPanel item = new JPanel(new BorderLayout());
            item.setBackground(BG_CARD);
            item.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_COLOR, 1),
                    new EmptyBorder(4, 8, 4, 8)
            ));
            JLabel nameLbl = new JLabel(statNames[i]);
            nameLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            nameLbl.setForeground(TEXT_SECONDARY);

            statValues[i] = new JLabel("0");
            statValues[i].setFont(new Font("Segoe UI", Font.BOLD, 13));
            statValues[i].setForeground(statColors[i]);

            item.add(nameLbl, BorderLayout.WEST);
            item.add(statValues[i], BorderLayout.EAST);
            statGrid.add(item);
        }
        JPanel statCard = createTitledCard("THỐNG KÊ THỜI GIAN THỰC", statGrid);

        // Card Nhật ký mạng
        logArea.setEditable(false);
        logArea.setBackground(BG_DARKEST);
        logArea.setForeground(TEXT_MAIN);
        logArea.setFont(FONT_MONO);
        logArea.setMargin(new Insets(6, 6, 6, 6));
        JScrollPane logScroll = createModernScrollPane(logArea);
        JPanel logCard = createTitledCard("NHẬT KÝ GIAO THỨC", logScroll);

        JPanel rightTop = new JPanel();
        rightTop.setLayout(new BoxLayout(rightTop, BoxLayout.Y_AXIS));
        rightTop.setOpaque(false);
        rightTop.add(lossCard);
        rightTop.add(Box.createVerticalStrut(8));
        rightTop.add(statCard);

        rightPanel.add(rightTop, BorderLayout.NORTH);
        rightPanel.add(logCard, BorderLayout.CENTER);

        // Ghép vào khung root
        root.add(header, BorderLayout.NORTH);
        root.add(leftPanel, BorderLayout.WEST);
        root.add(centerPanel, BorderLayout.CENTER);
        root.add(rightPanel, BorderLayout.EAST);

        setContentPane(root);
        SwingUtilities.invokeLater(input::requestFocusInWindow);
    }

    private void updateStatus() {
        Room r = currentRoom;
        if (r != null) {
            headerRoomLabel.setText(r.name + " (" + r.group + ":" + PORT + ")");
            headerRoomLabel.setForeground(ACCENT_PURPLE);
        } else {
            headerRoomLabel.setText("Chưa vào phòng");
            headerRoomLabel.setForeground(TEXT_MUTED);
        }

        int drop = simulateDropEvery.get();
        if (drop == 0) {
            headerLossLabel.setText("Loss: Tắt");
            headerLossLabel.setForeground(ACCENT_GREEN);
        } else {
            headerLossLabel.setText("Loss: N=" + drop);
            headerLossLabel.setForeground(ACCENT_RED);
        }

        roomListUpdating = true;
        if (r != null) roomList.setSelectedValue(r, true);
        roomListUpdating = false;
    }

    // =====================================================================
    //  HIỂN THỊ TIN NHẮN CHAT VỚI FORMATTING HIỆN ĐẠI
    // =====================================================================
    private void append(String prefix, Color prefixColor, String sender, Color senderColor,
                        String tag, Color tagColor, String body, Color bodyColor) {
        ui(() -> {
            try {
                StyledDocument doc = chatPane.getStyledDocument();

                if (prefix != null && !prefix.isEmpty()) {
                    SimpleAttributeSet aPre = new SimpleAttributeSet();
                    StyleConstants.setForeground(aPre, prefixColor);
                    StyleConstants.setFontFamily(aPre, "Segoe UI");
                    StyleConstants.setFontSize(aPre, 12);
                    doc.insertString(doc.getLength(), prefix + " ", aPre);
                }

                if (sender != null && !sender.isEmpty()) {
                    SimpleAttributeSet aSend = new SimpleAttributeSet();
                    StyleConstants.setForeground(aSend, senderColor);
                    StyleConstants.setFontFamily(aSend, "Segoe UI");
                    StyleConstants.setBold(aSend, true);
                    StyleConstants.setFontSize(aSend, 14);
                    doc.insertString(doc.getLength(), sender + " ", aSend);
                }

                if (tag != null && !tag.isEmpty()) {
                    SimpleAttributeSet aTag = new SimpleAttributeSet();
                    StyleConstants.setForeground(aTag, tagColor);
                    StyleConstants.setFontFamily(aTag, "Segoe UI");
                    StyleConstants.setFontSize(aTag, 12);
                    doc.insertString(doc.getLength(), tag + " ", aTag);
                }

                SimpleAttributeSet aBody = new SimpleAttributeSet();
                StyleConstants.setForeground(aBody, bodyColor);
                StyleConstants.setFontFamily(aBody, "Segoe UI");
                StyleConstants.setFontSize(aBody, 14);
                doc.insertString(doc.getLength(), "\n" + body + "\n\n", aBody);

                chatPane.setCaretPosition(doc.getLength());
            } catch (Exception ignored) { }
        });
    }

    private void chatLine(String time, String who, long seq, String text, boolean self, boolean resent) {
        String seqTag = String.format("#%04d%s", seq, resent ? " [GỬI LẠI]" : "");
        Color whoCol = self ? ACCENT_PRIMARY : ACCENT_CYAN;
        Color tagCol = resent ? ACCENT_GREEN : TEXT_MUTED;
        String prefix = "[" + time + "]";
        append(prefix, TEXT_MUTED, who, whoCol, seqTag, tagCol, "  " + text, TEXT_MAIN);
    }

    private void system(String text) {
        ui(() -> {
            try {
                StyledDocument doc = chatPane.getStyledDocument();
                SimpleAttributeSet a = new SimpleAttributeSet();
                StyleConstants.setForeground(a, TEXT_MAIN);
                StyleConstants.setFontFamily(a, "Segoe UI");
                StyleConstants.setItalic(a, true);
                StyleConstants.setFontSize(a, 12);
                doc.insertString(doc.getLength(), "  [*] " + text + "\n", a);
                chatPane.setCaretPosition(doc.getLength());
            } catch (Exception ignored) { }
        });
    }

    private void warn(String text) {
        ui(() -> {
            try {
                StyledDocument doc = chatPane.getStyledDocument();
                SimpleAttributeSet a = new SimpleAttributeSet();
                StyleConstants.setForeground(a, ACCENT_RED);
                StyleConstants.setFontFamily(a, "Segoe UI");
                StyleConstants.setBold(a, true);
                StyleConstants.setFontSize(a, 12);
                doc.insertString(doc.getLength(), "  [!] " + text + "\n", a);
                chatPane.setCaretPosition(doc.getLength());
            } catch (Exception ignored) { }
        });
    }

    private void log(String type, String text) {
        String line = String.format("%s %-7s %s%n", now(), type, text);
        ui(() -> {
            logArea.append(line);
            if (logArea.getDocument().getLength() > 20000) logArea.replaceRange("", 0, 5000);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void refreshStats() {
        long[] v = {sSent.get(), sRecv.get(), sLost.get(), sNack.get(), sResent.get(), sRecovered.get(),
                sBcSent.get(), sBcRecv.get()};
        for (int i = 0; i < 8; i++) statValues[i].setText(Long.toString(v[i]));
    }

    private void refreshPeers() {
        long now = System.currentTimeMillis();
        List<PeerItem> rows = new ArrayList<>();
        rows.add(new PeerItem(displayName, true, true, 0, 0, 0));
        for (SenderTracker t : trackerBySender.values()) {
            synchronized (t) {
                if (t.name == null) continue;
                boolean online = now - t.lastHeard < OFFLINE_MS;
                rows.add(new PeerItem(t.name, online, false, Math.max(0, t.maxSeen), t.count, t.gaps));
            }
        }
        peerModel.clear();
        for (PeerItem p : rows) peerModel.addElement(p);
    }

    // =====================================================================
    //  HÀNH ĐỘNG NGƯỜI DÙNG & LỆNH ĐIỀU KHIỂN
    // =====================================================================
    private void onSubmit() {
        String text = input.getText().trim();
        if (text.isEmpty()) return;
        input.setText("");
        if (text.startsWith("/")) { command(text); return; }
        try {
            if (sendMode.getSelectedIndex() == 1) sendBroadcast(text);
            else sendChat(text);
        } catch (Exception e) {
            warn("Không gửi được: " + e.getMessage());
        }
    }

    private void command(String line) {
        String[] p = line.split("\\s+", 2);
        String arg = p.length > 1 ? p[1].trim() : "";
        switch (p[0].toLowerCase()) {
            case "/join" -> {
                Room r = resolveRoom(arg);
                if (r == null) warn("Không tìm thấy phòng '" + arg + "'. Gõ /rooms để xem danh sách.");
                else joinRoom(r);
            }
            case "/loss" -> {
                try {
                    int n = Math.max(0, Integer.parseInt(arg));
                    lossSpinner.setValue(Math.min(100, n));
                    setLoss(n);
                } catch (NumberFormatException e) { warn("Cú pháp: /loss <số>, ví dụ /loss 3 hoặc /loss 0"); }
            }
            case "/bc" -> {
                if (arg.isEmpty()) warn("Cú pháp: /bc <nội dung> - gửi broadcast tới toàn bộ máy trong LAN");
                else {
                    try { sendBroadcast(arg); } catch (Exception e) { warn("Không gửi được broadcast: " + e.getMessage()); }
                }
            }
            case "/rooms" -> {
                for (Room r : ROOMS.values()) system(r.id + ". " + r.name + " -> " + r.group + ":" + PORT);
            }
            case "/status" -> system("Tên: " + displayName + " | Card: " + networkInterface.getDisplayName()
                    + " (" + localIp + ") | Phòng: " + (currentRoom == null ? "-" : currentRoom.group)
                    + " | Loss=" + simulateDropEvery.get());
            case "/exit" -> exitApp();
            default -> {
                system("Danh sách lệnh hỗ trợ:");
                system("  /rooms          Xem danh sách các phòng multicast");
                system("  /join <1|2|3>   Chuyển nhanh sang phòng khác (ví dụ: /join 2)");
                system("  /bc <nội dung>  Gửi BROADCAST toàn mạng LAN (không phân biệt phòng)");
                system("  /loss <số>      Mô phỏng mất gói có seq chia hết cho số N (demo NACK)");
                system("  /status         Xem trạng thái kết nối mạng hiện tại");
                system("  /exit           Rời nhóm và đóng chương trình");
            }
        }
    }

    private void joinRoom(Room r) {
        if (currentRoom != null && currentRoom.id.equals(r.id)) return;
        try {
            switchRoom(r);
        } catch (Exception e) {
            warn("Không chuyển được phòng: " + e.getMessage());
            updateStatus();
        }
    }

    private void setLoss(int n) {
        simulateDropEvery.set(n);
        simulatedDrops.clear();
        if (n == 0) system("Đã tắt giả lập mất gói.");
        else system("Đã bật giả lập: bỏ qua lần nhận ĐẦU TIÊN của mỗi gói có seq chia hết cho " + n + ".");
        updateStatus();
    }

    private void exitApp() {
        running = false;
        if (uiTimer != null) uiTimer.stop();
        if (heartbeatTimer != null) heartbeatTimer.cancel();
        try {
            synchronized (roomLock) {
                if (currentRoom != null) {
                    socket.leaveGroup(new InetSocketAddress(InetAddress.getByName(currentRoom.group), PORT),
                            networkInterface);
                }
            }
        } catch (Exception ignored) { }
        socket.close();
        bcSocket.close();
        dispose();
        System.exit(0);
    }

    // =====================================================================
    //  LÕI MẠNG MULTICAST & BROADCAST (CHUẨN GIAO THỨC)
    // =====================================================================
    private void switchRoom(Room newRoom) throws Exception {
        synchronized (roomLock) {
            if (currentRoom != null && currentRoom.id.equals(newRoom.id)) return;
            if (currentRoom != null) {
                socket.leaveGroup(new InetSocketAddress(InetAddress.getByName(currentRoom.group), PORT), networkInterface);
                system("Đã rời phòng " + currentRoom.name + " (" + currentRoom.group + ")");
                log("LEAVE", currentRoom.group);
            }
            socket.joinGroup(new InetSocketAddress(InetAddress.getByName(newRoom.group), PORT), networkInterface);
            currentRoom = newRoom;
            trackerBySender.clear();
            simulatedDrops.clear();
        }
        system("=== ĐÃ THAM GIA PHÒNG " + newRoom.name + " (" + newRoom.group + ":" + PORT + ") ===");
        log("JOIN", newRoom.group + ":" + PORT);
        ui(() -> { updateStatus(); refreshPeers(); });
        sendHeartbeat();
    }

    private void sendChat(String text) throws Exception {
        Room room = currentRoom;
        if (room == null) { warn("Chưa tham gia phòng."); return; }
        long seq = seqByRoom.computeIfAbsent(room.id, k -> new AtomicLong(0)).incrementAndGet();
        long epoch = System.currentTimeMillis();
        ChatMessage msg = new ChatMessage(room.id, nodeId, seq, epoch, displayName, text);
        ConcurrentHashMap<Long, ChatMessage> cache = sentCacheByRoom.computeIfAbsent(room.id, k -> new ConcurrentHashMap<>());
        cache.put(seq, msg);
        if (cache.size() > CACHE_LIMIT) {
            long cutoff = seq - CACHE_LIMIT;
            cache.keySet().removeIf(k -> k < cutoff);
        }
        sendRaw(msg.toWire("MSG"), room);
        sSent.incrementAndGet();
        chatLine(formatTime(epoch), "Bạn", seq, text, true, false);
        log("MSG", String.format("gửi #%04d", seq));
    }

    private void sendHeartbeat() {
        Room room = currentRoom;
        if (room == null || !running) return;
        try {
            AtomicLong a = seqByRoom.get(room.id);
            long last = a == null ? 0 : a.get();
            sendRaw(String.join("|", "HB", room.id, nodeId, encode(displayName), Long.toString(last)), room);
        } catch (Exception ignored) { }
    }

    private void receiveLoop() {
        byte[] buffer = new byte[MAX_PACKET];
        while (running && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String wire = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                handleIncoming(wire);
            } catch (Exception e) {
                if (running && !socket.isClosed()) warn("Lỗi nhận: " + e.getMessage());
            }
        }
    }

    private void handleIncoming(String wire) {
        try {
            String[] p = wire.split("\\|", -1);
            if (p.length == 0) return;
            switch (p[0]) {
                case "NACK" -> handleNack(p);
                case "HB" -> handleHeartbeat(p);
                case "MSG", "RESEND" -> handleMessage(p);
                default -> { }
            }
        } catch (Exception ignored) { }
    }

    private void handleHeartbeat(String[] p) {
        if (p.length != 5) return;
        Room room = currentRoom;
        if (room == null || !room.id.equals(p[1])) return;
        String senderId = p[2];
        if (nodeId.equals(senderId)) return;
        String name = decode(p[3]);
        long last = Long.parseLong(p[4]);

        SenderTracker t = trackerBySender.computeIfAbsent(senderId, k -> new SenderTracker());
        long from = -1, to = -1;
        boolean isNew;
        synchronized (t) {
            isNew = t.name == null;
            t.name = name;
            t.lastHeard = System.currentTimeMillis();
            if (t.maxSeen < 0) {
                t.maxSeen = last;
            } else if (last > t.maxSeen && System.currentTimeMillis() - t.lastNack > 1500) {
                from = t.maxSeen + 1;
                to = Math.min(last, from + MAX_RESEND_RANGE - 1);
                t.lastNack = System.currentTimeMillis();
                t.gaps += (to - from + 1);
            }
        }
        if (isNew) system(name + " đã tham gia phòng.");
        if (from >= 0) {
            warn(String.format("Heartbeat phát hiện thiếu gói %s #%04d..#%04d", name, from, to));
            log("GAP", String.format("%s #%04d..#%04d (qua HB)", name, from, to));
            sendNack(room, senderId, from, to);
        }
    }

    private void handleMessage(String[] p) {
        if (p.length != 7) return;
        Room room = currentRoom;
        if (room == null || !room.id.equals(p[1])) return;
        String senderId = p[2];
        long seq = Long.parseLong(p[3]);
        long epoch = Long.parseLong(p[4]);
        String senderName = decode(p[5]);
        String text = decode(p[6]);
        if (nodeId.equals(senderId)) return;

        boolean isResend = "RESEND".equals(p[0]);
        if (!isResend && shouldSimulateDrop(senderId, seq)) {
            sLost.incrementAndGet();
            warn(String.format("[MÔ PHỎNG] Bỏ qua gói %s #%04d lần đầu.", senderName, seq));
            log("DROP", String.format("%s #%04d", senderName, seq));
            return;
        }

        SenderTracker t = trackerBySender.computeIfAbsent(senderId, k -> new SenderTracker());
        long missingFrom = -1, missingTo = -1;
        boolean late;
        synchronized (t) {
            t.name = senderName;
            t.lastHeard = System.currentTimeMillis();
            if (t.seen.contains(seq)) return;
            if (t.maxSeen < 0) {
                t.maxSeen = seq;
                late = false;
            } else {
                long oldMax = t.maxSeen;
                if (seq > oldMax + 1) {
                    missingFrom = oldMax + 1;
                    missingTo = Math.min(seq - 1, missingFrom + MAX_RESEND_RANGE - 1);
                    t.gaps += (missingTo - missingFrom + 1);
                    t.lastNack = System.currentTimeMillis();
                }
                late = seq <= oldMax;
                if (seq > t.maxSeen) t.maxSeen = seq;
            }
            t.seen.add(seq);
            t.count++;
            if (t.seen.size() >= 300) {
                long keepFrom = Math.max(0, t.maxSeen - 200);
                t.seen.removeIf(v -> v < keepFrom);
            }
        }

        boolean recovered = late || isResend;
        sRecv.incrementAndGet();
        if (recovered) sRecovered.incrementAndGet();
        chatLine(formatTime(epoch), senderName, seq, text, false, recovered);
        log(isResend ? "RESEND" : "MSG", String.format("nhận %s #%04d%s", senderName, seq, recovered ? " (khôi phục)" : ""));

        if (missingFrom >= 0) {
            warn(String.format("Phát hiện thiếu gói %s #%04d..#%04d -> gửi NACK", senderName, missingFrom, missingTo));
            log("GAP", String.format("%s #%04d..#%04d", senderName, missingFrom, missingTo));
            sendNack(room, senderId, missingFrom, missingTo);
        }
    }

    private boolean shouldSimulateDrop(String senderId, long seq) {
        int n = simulateDropEvery.get();
        if (n <= 0 || seq % n != 0) return false;
        return simulatedDrops.add(senderId + ":" + seq);
    }

    private void sendNack(Room room, String targetSenderId, long from, long to) {
        try {
            sendRaw(String.join("|", "NACK", room.id, nodeId, targetSenderId, Long.toString(from), Long.toString(to)), room);
            sNack.incrementAndGet();
            log("NACK", String.format("yêu cầu #%04d..#%04d", from, to));
        } catch (Exception e) {
            warn("Không gửi được NACK: " + e.getMessage());
        }
    }

    private void handleNack(String[] p) throws Exception {
        if (p.length != 6) return;
        String roomId = p[1], requesterId = p[2], target = p[3];
        long from = Long.parseLong(p[4]), to = Long.parseLong(p[5]);
        if (!nodeId.equals(target) || nodeId.equals(requesterId)) return;
        Room room = currentRoom;
        if (room == null || !room.id.equals(roomId)) return;
        ConcurrentHashMap<Long, ChatMessage> cache = sentCacheByRoom.get(roomId);
        if (cache == null) return;
        long safeTo = Math.min(to, from + MAX_RESEND_RANGE - 1);
        for (long seq = from; seq <= safeTo; seq++) {
            ChatMessage msg = cache.get(seq);
            if (msg == null) continue;
            sendRaw(msg.toWire("RESEND"), room);
            sResent.incrementAndGet();
            system(String.format("Đã gửi lại gói #%04d theo yêu cầu NACK.", seq));
            log("RESEND", String.format("phát lại #%04d", seq));
            try { Thread.sleep(8); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
    }

    // =====================================================================
    //  BROADCAST VÀ RAW TRANSMISSION
    // =====================================================================
    private void collectBroadcastTargets() {
        try {
            for (InterfaceAddress ia : networkInterface.getInterfaceAddresses()) {
                InetAddress b = ia.getBroadcast();
                if (b != null && ia.getAddress() instanceof Inet4Address && !bcTargets.contains(b)) bcTargets.add(b);
            }
            bcTargets.add(InetAddress.getByName("255.255.255.255"));
        } catch (Exception ignored) { }
    }

    private void sendBroadcast(String text) throws Exception {
        long seq = bcSeq.incrementAndGet();
        long epoch = System.currentTimeMillis();
        byte[] data = String.join("|", "BC", nodeId, Long.toString(seq), Long.toString(epoch),
                encode(displayName), encode(text)).getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_PACKET) throw new IllegalArgumentException("Tin nhắn quá dài.");
        int ok = 0;
        Exception last = null;
        for (InetAddress target : bcTargets) {
            try {
                synchronized (bcSocket) { bcSocket.send(new DatagramPacket(data, data.length, target, BC_PORT)); }
                ok++;
            } catch (Exception e) { last = e; }
        }
        if (ok == 0) throw last != null ? last : new IllegalStateException("Không có địa chỉ broadcast.");
        sBcSent.incrementAndGet();
        append("[" + formatTime(epoch) + "]", TEXT_MUTED, "Bạn (Broadcast)", ACCENT_AMBER,
                String.format("#%04d", seq), TEXT_MUTED, "  " + text, new Color(254, 215, 170));
        log("BC", String.format("gửi #%04d tới %d địa chỉ", seq, ok));
    }

    private void broadcastReceiveLoop() {
        byte[] buffer = new byte[MAX_PACKET];
        while (running && !bcSocket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                bcSocket.receive(packet);
                String wire = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                String[] p = wire.split("\\|", -1);
                if (p.length != 6 || !"BC".equals(p[0])) continue;
                String senderId = p[1];
                if (nodeId.equals(senderId)) continue;
                long seq = Long.parseLong(p[2]);
                long epoch = Long.parseLong(p[3]);
                if (!bcSeen.add(senderId + ":" + seq)) continue;
                if (bcSeen.size() > 2000) bcSeen.clear();
                String name = decode(p[4]);
                String text = decode(p[5]);
                sBcRecv.incrementAndGet();
                append("[" + formatTime(epoch) + "]", TEXT_MUTED, name + " (Broadcast)", ACCENT_AMBER,
                        String.format("#%04d", seq), TEXT_MUTED, "  " + text, new Color(254, 215, 170));
                log("BC", String.format("nhận %s #%04d từ %s", name, seq, packet.getAddress().getHostAddress()));
            } catch (Exception e) {
                if (running && !bcSocket.isClosed()) warn("Lỗi nhận broadcast: " + e.getMessage());
            }
        }
    }

    private void sendRaw(String wire, Room room) throws Exception {
        byte[] data = wire.getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_PACKET) throw new IllegalArgumentException("Tin nhắn quá dài.");
        DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(room.group), PORT);
        synchronized (socket) { socket.send(packet); }
    }

    // =====================================================================
    //  TIỆN ÍCH HỆ THỐNG & ĐỊNH DẠNG
    // =====================================================================
    private static void ui(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) r.run(); else SwingUtilities.invokeLater(r);
    }

    private static String now() { return formatTime(System.currentTimeMillis()); }

    private static String formatTime(long ms) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).format(TIME_FMT);
    }

    private static String encode(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String s) {
        return new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8);
    }

    private Room resolveRoom(String token) {
        if (token == null || token.isBlank()) return null;
        Room byId = ROOMS.get(token.trim());
        if (byId != null) return byId;
        for (Room r : ROOMS.values()) if (r.name.equalsIgnoreCase(token.trim())) return r;
        return null;
    }

    private static String firstIpv4(NetworkInterface ni) {
        for (InetAddress a : Collections.list(ni.getInetAddresses())) {
            if (a instanceof Inet4Address) return a.getHostAddress();
        }
        return "?";
    }

    static NetworkInterface chooseInterface(String localIp) throws Exception {
        if (localIp != null && !localIp.isBlank()) {
            NetworkInterface ni = NetworkInterface.getByInetAddress(InetAddress.getByName(localIp));
            if (ni == null) throw new IllegalArgumentException("Không tìm thấy card mạng của IP " + localIp);
            if (!ni.isUp() || !ni.supportsMulticast())
                throw new IllegalArgumentException("Card mạng của IP " + localIp + " không sẵn sàng cho multicast.");
            return ni;
        }
        Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
        if (all == null) return null;
        NetworkInterface best = null;
        int bestScore = Integer.MIN_VALUE;
        for (NetworkInterface ni : Collections.list(all)) {
            if (!ni.isUp() || ni.isLoopback() || ni.isVirtual() || !ni.supportsMulticast()) continue;
            boolean hasIpv4 = false, siteLocal = false;
            for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                if (a instanceof Inet4Address && !a.isLoopbackAddress() && !a.isLinkLocalAddress()) {
                    hasIpv4 = true;
                    if (a.isSiteLocalAddress()) siteLocal = true;
                }
            }
            if (!hasIpv4) continue;
            String t = (ni.getName() + " " + ni.getDisplayName()).toLowerCase();
            int score = 0;
            if (siteLocal) score += 10;
            if (t.contains("wi-fi") || t.contains("wifi") || t.contains("wlan") || t.contains("wireless")) score += 20;
            if (t.contains("ethernet") || t.startsWith("eth") || t.startsWith("en")) score += 15;
            if (t.contains("virtual") || t.contains("vmware") || t.contains("vbox") || t.contains("virtualbox")
                    || t.contains("hyper-v") || t.contains("vethernet") || t.contains("docker") || t.contains("wsl")
                    || t.contains("vpn") || t.contains("tap") || t.contains("tun") || t.contains("radmin")
                    || t.contains("hamachi") || t.contains("zerotier") || t.contains("tailscale")
                    || t.contains("bluetooth") || t.contains("pseudo")) score -= 100;
            if (score > bestScore) { bestScore = score; best = ni; }
        }
        return best;
    }

    // =====================================================================
    //  CÁC COMPONENT CUSTOM GIAO DIỆN HIỆN ĐẠI (SWING NGUYÊN BẢN)
    // =====================================================================
    private static JScrollPane createModernScrollPane(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBackground(BG_SIDEBAR);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(BG_DARKEST);
        sp.getVerticalScrollBar().setUI(new ModernScrollBarUI());
        sp.getHorizontalScrollBar().setUI(new ModernScrollBarUI());
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        sp.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 8));
        return sp;
    }

    private static JPanel createTitledCard(String title, Component center) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(BG_SIDEBAR);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(8, 8, 8, 8)
        ));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        titleLbl.setForeground(TEXT_MUTED);
        card.add(titleLbl, BorderLayout.NORTH);
        card.add(center, BorderLayout.CENTER);
        return card;
    }

    private static JPanel createPill(String text, Color bg, Color fg) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 3)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                super.paintComponent(g);
            }
        };
        p.setOpaque(false);
        JLabel l = new JLabel(text);
        l.setFont(FONT_SMALL);
        l.setForeground(fg);
        p.add(l);
        return p;
    }

    private static void styleComboBox(JComboBox<?> box) {
        box.setBackground(BG_INPUT);
        box.setForeground(TEXT_MAIN);
        box.setFont(FONT_BOLD);
        box.setFocusable(false);
        box.setOpaque(true);
        box.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        box.setUI(new BasicComboBoxUI() {
            @Override
            public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(BG_INPUT);
                g2.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
                g2.dispose();
            }

            @Override
            public void paint(Graphics g, JComponent c) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(BG_INPUT);
                g2.fillRect(0, 0, c.getWidth(), c.getHeight());
                g2.dispose();
                super.paint(g, c);
            }

            @Override
            protected JButton createArrowButton() {
                JButton btn = new JButton() {
                    @Override
                    public void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(BG_INPUT);
                        g2.fillRect(0, 0, getWidth(), getHeight());
                        g2.setColor(TEXT_MAIN);
                        int midX = getWidth() / 2;
                        int midY = getHeight() / 2;
                        Polygon p = new Polygon();
                        p.addPoint(midX - 5, midY - 2);
                        p.addPoint(midX + 5, midY - 2);
                        p.addPoint(midX, midY + 4);
                        g2.fill(p);
                        g2.dispose();
                    }
                };
                btn.setBorder(BorderFactory.createEmptyBorder());
                btn.setFocusable(false);
                btn.setContentAreaFilled(false);
                btn.setOpaque(false);
                return btn;
            }
        });
        box.setRenderer(new ListCellRenderer<Object>() {
            private final JLabel label = new JLabel();
            {
                label.setOpaque(true);
                label.setFont(FONT_BOLD);
            }
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                label.setText(value == null ? "" : value.toString());
                if (isSelected) {
                    label.setBackground(ACCENT_PRIMARY);
                    label.setForeground(Color.WHITE);
                } else {
                    label.setBackground(index == -1 ? BG_INPUT : BG_CARD);
                    label.setForeground(TEXT_MAIN);
                }
                label.setBorder(new EmptyBorder(6, 12, 6, 12));
                return label;
            }
        });
    }

    private static void styleSpinner(JSpinner spinner) {
        spinner.setBackground(BG_INPUT);
        spinner.setForeground(TEXT_MAIN);
        spinner.setFont(FONT_BOLD);
        spinner.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor de) {
            de.getTextField().setBackground(BG_INPUT);
            de.getTextField().setForeground(TEXT_MAIN);
            de.getTextField().setCaretColor(TEXT_MAIN);
            de.getTextField().setFont(FONT_BOLD);
        }
        spinner.setUI(new BasicSpinnerUI());
    }

    /** Nút bấm phẳng hiện đại với bo góc và hiệu ứng Hover */
    static class ModernButton extends JButton {
        private final Color baseBg;
        private final Color hoverBg;
        private boolean hovering = false;

        public ModernButton(String text, Color baseBg, Color hoverBg, Color fg) {
            super(text);
            this.baseBg = baseBg;
            this.hoverBg = hoverBg;
            setForeground(fg);
            setFont(FONT_BOLD);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(6, 14, 6, 14));

            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hovering = true; repaint(); }
                public void mouseExited(MouseEvent e) { hovering = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hovering ? hoverBg : baseBg);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
            if (baseBg == BG_CARD) {
                g2.setColor(BORDER_COLOR);
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 8, 8));
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Ô nhập văn bản bo góc với padding đẹp và tương phản cao */
    static class ModernTextField extends JTextField {
        private final String placeholder;

        public ModernTextField(String placeholder) {
            this.placeholder = placeholder;
            setBackground(BG_INPUT);
            setForeground(TEXT_MAIN);
            setCaretColor(TEXT_MAIN);
            setFont(FONT_MAIN);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_COLOR, 1),
                    new EmptyBorder(6, 12, 6, 12)
            ));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(BG_INPUT);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
            if (placeholder != null && getText().isEmpty()) {
                Graphics2D g2Text = (Graphics2D) g.create();
                g2Text.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2Text.setColor(TEXT_MUTED);
                g2Text.setFont(FONT_MAIN);
                FontMetrics fm = g2Text.getFontMetrics();
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2Text.drawString(placeholder, 12, y);
                g2Text.dispose();
            }
        }
    }

    /** Thanh cuộn mỏng bo tròn phong cách hiện đại */
    static class ModernScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            this.thumbColor = new Color(55, 60, 78);
            this.trackColor = BG_SIDEBAR;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) { return createZeroButton(); }
        @Override
        protected JButton createIncreaseButton(int orientation) { return createZeroButton(); }

        private JButton createZeroButton() {
            JButton btn = new JButton();
            btn.setPreferredSize(new Dimension(0, 0));
            btn.setMinimumSize(new Dimension(0, 0));
            btn.setMaximumSize(new Dimension(0, 0));
            return btn;
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isThumbRollover() ? ACCENT_PRIMARY : thumbColor);
            g2.fillRoundRect(thumbBounds.x + 1, thumbBounds.y + 1, thumbBounds.width - 2, thumbBounds.height - 2, 6, 6);
            g2.dispose();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            g.setColor(trackColor);
            g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
        }
    }

    /** Renderer hiển thị danh sách phòng chat */
    static class RoomCellRenderer extends JPanel implements ListCellRenderer<Room> {
        private final JLabel nameLbl = new JLabel();
        private final JLabel ipLbl = new JLabel();

        public RoomCellRenderer() {
            setLayout(new BorderLayout(6, 2));
            setBorder(new EmptyBorder(8, 10, 8, 10));
            setOpaque(false);

            nameLbl.setFont(FONT_BOLD);
            ipLbl.setFont(FONT_SMALL);
            add(nameLbl, BorderLayout.WEST);
            add(ipLbl, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Room> list, Room value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            nameLbl.setText("# " + value.name);
            ipLbl.setText(value.group);

            if (isSelected) {
                setBackground(ACCENT_PRIMARY);
                nameLbl.setForeground(Color.WHITE);
                ipLbl.setForeground(new Color(220, 225, 255));
                setOpaque(true);
            } else {
                setBackground(BG_SIDEBAR);
                nameLbl.setForeground(TEXT_MAIN);
                ipLbl.setForeground(TEXT_MUTED);
                setOpaque(false);
            }
            return this;
        }
    }

    /** Renderer danh sách thành viên online với avatar chữ cái */
    static class PeerCellRenderer extends JPanel implements ListCellRenderer<PeerItem> {
        private final JLabel avatarLbl = new JLabel();
        private final JLabel nameLbl = new JLabel();
        private final JLabel statusLbl = new JLabel();

        public PeerCellRenderer() {
            setLayout(new BorderLayout(8, 0));
            setBorder(new EmptyBorder(6, 8, 6, 8));
            setOpaque(false);

            avatarLbl.setPreferredSize(new Dimension(28, 28));
            avatarLbl.setHorizontalAlignment(SwingConstants.CENTER);
            avatarLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
            avatarLbl.setForeground(Color.WHITE);

            nameLbl.setFont(FONT_MAIN);
            nameLbl.setForeground(TEXT_MAIN);

            statusLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            statusLbl.setForeground(TEXT_MUTED);

            JPanel center = new JPanel(new GridLayout(2, 1, 0, 1));
            center.setOpaque(false);
            center.add(nameLbl);
            center.add(statusLbl);

            add(avatarLbl, BorderLayout.WEST);
            add(center, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends PeerItem> list, PeerItem val, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            String initial = val.name.isEmpty() ? "?" : val.name.substring(0, 1).toUpperCase();
            avatarLbl.setText(initial);

            Color avatarBg = val.isSelf ? ACCENT_PRIMARY : new Color(Math.abs(val.name.hashCode() % 160 + 50),
                    Math.abs((val.name.hashCode() / 3) % 160 + 50),
                    Math.abs((val.name.hashCode() / 7) % 160 + 50));

            avatarLbl.setIcon(new Icon() {
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(avatarBg);
                    g2.fillOval(x, y, 26, 26);
                    g2.setColor(val.online ? ACCENT_GREEN : TEXT_MUTED);
                    g2.fillOval(x + 18, y + 18, 8, 8);
                }
                public int getIconWidth() { return 26; }
                public int getIconHeight() { return 26; }
            });

            nameLbl.setText(val.name + (val.isSelf ? " (Bạn)" : ""));
            nameLbl.setForeground(val.online ? TEXT_MAIN : TEXT_MUTED);

            if (val.isSelf) {
                statusLbl.setText("Đang hoạt động");
            } else {
                statusLbl.setText((val.online ? "Seq #" + val.maxSeen + " | Nhận " + val.count : "Offline")
                        + (val.gaps > 0 ? " | Thiếu " + val.gaps : ""));
            }

            setBackground(isSelected ? BG_HOVER : BG_SIDEBAR);
            setOpaque(isSelected);
            return this;
        }
    }

    private record Room(String id, String name, String group) {
        @Override public String toString() { return id + ". " + name + "  (" + group + ")"; }
    }

    private record PeerItem(String name, boolean online, boolean isSelf, long maxSeen, long count, long gaps) {}

    private record ChatMessage(String roomId, String senderId, long seq, long epoch, String senderName, String text) {
        String toWire(String type) {
            return String.join("|", type, roomId, senderId, Long.toString(seq), Long.toString(epoch),
                    encode(senderName), encode(text));
        }
    }

    private static class SenderTracker {
        long maxSeen = -1;
        String name;
        long count, gaps, lastHeard, lastNack;
        final Set<Long> seen = ConcurrentHashMap.newKeySet();
    }
}
