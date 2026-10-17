package client;

import crawler.WebCrawlerEngine;
import server.HttpServerCore;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Ứng dụng "Chrome Thu Nhỏ" (HTTP Client GUI)
 * Thiết kế giao diện Đỏ - Trắng hiện đại theo chuẩn Swing
 * 
 * Tính năng nâng cấp:
 * 1. Hệ thống Đa Tab (Multi-Tab) giống Google Chrome: Cho phép mở nhiều tab duyệt web độc lập.
 * 2. Tích hợp Công Cụ Cào Web (Web Crawler): Cào web ngoài qua Socket/SSLSocket, gửi yêu cầu
 *    kiểm duyệt đến Server và tự động mở tab duyệt ngay khi được duyệt.
 * 3. Bảo toàn nguyên vẹn Tab Chat & Giao Tiếp Server (POST /api/message & GET /api/time).
 * 4. Nút Xem Mã Nguồn HTML (View Source) và Bảng Soi Gói Tin Raw.
 */
public class MiniChromeBrowser extends JFrame {

    // Bảng màu Đỏ - Trắng chuẩn UI Styling (Crimson / Slate)
    public static final Color PRIMARY_RED = new Color(220, 38, 38);      // #DC2626
    public static final Color DARK_RED = new Color(153, 27, 27);         // #991B1B
    public static final Color LIGHT_BG = new Color(248, 250, 252);       // #F8FAFC
    public static final Color CARD_BG = Color.WHITE;
    public static final Color BORDER_COLOR = new Color(226, 232, 240);    // #E2E8F0
    public static final Color TEXT_MAIN = new Color(15, 23, 42);         // #0F172A
    public static final Color TEXT_MUTED = new Color(100, 116, 139);     // #64748B

    private final HttpClientHelper httpHelper;

    // Quản lý Đa Tab
    private JTabbedPane mainTabbedPane;
    private final List<BrowserTabPanel> browserTabs = new ArrayList<>();
    private JPanel messagingTabPanel;

    // Các thành phần Tab Chat / API
    private JComboBox<String> cbServerTarget;
    private JTextField txtMessageInput;
    private JTextArea txtChatHistory;
    private JTextArea txtRawInspect;

    // Thanh Status Bar chung
    private JLabel lblConnStatus;
    private JLabel lblStatusCodeBadge;
    private JLabel lblLatency;
    private JLabel lblContentType;
    private JLabel lblContentSize;

    public MiniChromeBrowser() {
        this.httpHelper = new HttpClientHelper();

        setTitle("Chrome Thu Nhỏ (Mini Chrome) - Lập Trình Mạng Chương 12");
        setSize(1060, 780);
        setMinimumSize(new Dimension(880, 620));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        initUI();

        // Mở Tab đầu tiên với trang P1.HTM
        SwingUtilities.invokeLater(() -> addNewBrowserTab("http://localhost:8080/P1.HTM"));
    }

    private void initUI() {
        JPanel rootPanel = new JPanel(new BorderLayout());
        rootPanel.setBackground(LIGHT_BG);

        // 1. Header Banner phong cách Đỏ - Trắng tinh tế + Nút Cào Web & Nút Thêm Tab
        JPanel headerPanel = new JPanel(new BorderLayout(10, 5));
        headerPanel.setBackground(PRIMARY_RED);
        headerPanel.setBorder(new EmptyBorder(12, 20, 12, 20));

        JPanel headerLeft = new JPanel(new GridLayout(2, 1, 2, 2));
        headerLeft.setOpaque(false);

        JLabel lblTitle = new JLabel("TRÌNH DUYỆT CHROME THU NHỎ (MINI CHROME)");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(Color.WHITE);

        JLabel lblSub = new JLabel("HTTP Client GUI • Đa Tab Chrome • Tích Hợp Cào Web (Crawler) & Kiểm Duyệt Quản Trị • API Slide 17-18");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(new Color(254, 226, 226));

        headerLeft.add(lblTitle);
        headerLeft.add(lblSub);

        // Các nút hành động nhanh trên Header
        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        headerRight.setOpaque(false);

        StyledButton btnNewTabHeader = new StyledButton("➕ Tab Mới", Color.WHITE, TEXT_MAIN);
        btnNewTabHeader.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnNewTabHeader.setToolTipText("Mở thêm một tab duyệt web mới");
        btnNewTabHeader.addActionListener(e -> addNewBrowserTab("http://localhost:8080/P1.HTM"));

        StyledButton btnCrawler = new StyledButton("🕷️ Cào Web (Web Crawler)", new Color(254, 242, 242), PRIMARY_RED);
        btnCrawler.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnCrawler.setToolTipText("Cào trang web ngoài Internet và nạp vào máy chủ Server");
        btnCrawler.addActionListener(e -> showCrawlerDialog());

        headerRight.add(btnNewTabHeader);
        headerRight.add(btnCrawler);

        headerPanel.add(headerLeft, BorderLayout.WEST);
        headerPanel.add(headerRight, BorderLayout.EAST);
        rootPanel.add(headerPanel, BorderLayout.NORTH);

        // 2. Tabbed Pane chính: Chứa các Browser Tabs + Tab Chat & API + Tab Thêm Mới (+)
        mainTabbedPane = new JTabbedPane();
        mainTabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));
        mainTabbedPane.setBackground(Color.WHITE);

        // Lắng nghe sự kiện chuyển Tab để cập nhật Status Bar
        mainTabbedPane.addChangeListener(e -> {
            int selectedIdx = mainTabbedPane.getSelectedIndex();
            if (selectedIdx >= 0 && selectedIdx < browserTabs.size()) {
                BrowserTabPanel activeTab = browserTabs.get(selectedIdx);
                activeTab.triggerStatusRefresh();
            }
        });

        // Tab Chat & Gọi API cố định
        messagingTabPanel = createMessagingTab();

        rootPanel.add(mainTabbedPane, BorderLayout.CENTER);

        // 3. Status Bar ở chân trang
        rootPanel.add(createStatusBar(), BorderLayout.SOUTH);

        setContentPane(rootPanel);
    }

    /**
     * Mở một Tab duyệt web mới
     */
    public BrowserTabPanel addNewBrowserTab(String initialUrl) {
        BrowserTabPanel newTab = new BrowserTabPanel(httpHelper, this::updateStatusBarFromTab, this::updateTabHeaderTitle);
        browserTabs.add(newTab);

        int insertIndex = browserTabs.size() - 1;
        mainTabbedPane.insertTab("Tab Mới", null, newTab, "Duyệt web", insertIndex);

        // Gắn Custom Header cho Tab (chứa tiêu đề và nút đóng x)
        mainTabbedPane.setTabComponentAt(insertIndex, createTabHeaderComponent("Tab Mới", newTab));

        // Đảm bảo Tab Chat luôn nằm sau cùng
        ensureMessagingTabAppended();

        // Kích hoạt tab vừa thêm
        mainTabbedPane.setSelectedComponent(newTab);

        if (initialUrl != null && !initialUrl.isEmpty()) {
            newTab.navigateTo(initialUrl, true);
        }

        return newTab;
    }

    private void ensureMessagingTabAppended() {
        int chatIdx = mainTabbedPane.indexOfComponent(messagingTabPanel);
        if (chatIdx != -1) {
            mainTabbedPane.remove(chatIdx);
        }
        mainTabbedPane.addTab("💬 Giao Tiếp Server & Chat API", messagingTabPanel);
    }

    private Component createTabHeaderComponent(String title, BrowserTabPanel tab) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        p.setOpaque(false);

        JLabel lbl = new JLabel(title);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(TEXT_MAIN);

        JButton btnClose = new JButton("×");
        btnClose.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnClose.setForeground(new Color(148, 163, 184));
        btnClose.setContentAreaFilled(false);
        btnClose.setBorderPainted(false);
        btnClose.setFocusPainted(false);
        btnClose.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnClose.setMargin(new Insets(0, 3, 0, 3));
        btnClose.setToolTipText("Đóng tab này");

        btnClose.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) { btnClose.setForeground(PRIMARY_RED); }
            public void mouseExited(java.awt.event.MouseEvent e) { btnClose.setForeground(new Color(148, 163, 184)); }
        });

        btnClose.addActionListener(e -> closeTab(tab));

        p.add(lbl);
        p.add(btnClose);
        return p;
    }

    private void updateTabHeaderTitle(BrowserTabPanel tab, String title) {
        int idx = mainTabbedPane.indexOfComponent(tab);
        if (idx != -1) {
            String shortTitle = title;
            if (shortTitle.length() > 16) {
                shortTitle = shortTitle.substring(0, 14) + "...";
            }
            mainTabbedPane.setTitleAt(idx, shortTitle);
            Component header = mainTabbedPane.getTabComponentAt(idx);
            if (header instanceof JPanel) {
                Component[] comps = ((JPanel) header).getComponents();
                if (comps.length > 0 && comps[0] instanceof JLabel) {
                    ((JLabel) comps[0]).setText(shortTitle);
                }
            }
        }
    }

    private void closeTab(BrowserTabPanel tab) {
        if (browserTabs.size() <= 1) {
            JOptionPane.showMessageDialog(this, "Không thể đóng tab cuối cùng!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int idx = mainTabbedPane.indexOfComponent(tab);
        if (idx != -1) {
            mainTabbedPane.remove(idx);
            browserTabs.remove(tab);
        }
    }

    private void updateStatusBarFromTab(BrowserTabPanel.TabStatusEvent e) {
        if (e.loading) {
            lblStatusCodeBadge.setText("[ Đang tải... ]");
            lblStatusCodeBadge.setBackground(new Color(234, 179, 8));
            lblStatusCodeBadge.setForeground(Color.WHITE);
            lblConnStatus.setText("● ĐANG KẾT NỐI...");
            lblConnStatus.setForeground(new Color(234, 179, 8));
        } else if (e.error) {
            lblStatusCodeBadge.setText(e.statusText);
            lblStatusCodeBadge.setBackground(PRIMARY_RED);
            lblStatusCodeBadge.setForeground(Color.WHITE);
            lblConnStatus.setText("● MẤT KẾT NỐI");
            lblConnStatus.setForeground(PRIMARY_RED);
            lblLatency.setText("Thời gian: " + e.latencyMs + " ms");
            lblContentType.setText("Loại: N/A");
            lblContentSize.setText("Kích thước: 0 B");
        } else {
            lblConnStatus.setText("● ĐÃ KẾT NỐI");
            lblConnStatus.setForeground(new Color(22, 163, 74));

            lblStatusCodeBadge.setForeground(Color.WHITE);
            if (e.statusCode >= 200 && e.statusCode < 300) {
                lblStatusCodeBadge.setText("[ " + e.statusCode + " OK ]");
                lblStatusCodeBadge.setBackground(new Color(22, 163, 74));
            } else if (e.statusCode == 404) {
                lblStatusCodeBadge.setText("[ 404 NOT FOUND ]");
                lblStatusCodeBadge.setBackground(PRIMARY_RED);
            } else {
                lblStatusCodeBadge.setText("[ HTTP " + e.statusCode + " ]");
                lblStatusCodeBadge.setBackground(new Color(217, 119, 6));
            }

            lblLatency.setText(String.format("Thời gian: %d ms", e.latencyMs));
            lblContentType.setText(String.format("Loại: %s", e.contentType));
            lblContentSize.setText(String.format("Kích thước: %,d bytes", e.contentLength));
        }
    }

    /**
     * Hộp thoại Cào Web (Web Crawler Dialog)
     */
    private void showCrawlerDialog() {
        JDialog dialog = new JDialog(this, "Công Cụ Cào Web (Web Crawler Engine) - Chương 12", true);
        dialog.setSize(680, 560);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(LIGHT_BG);
        panel.setBorder(new EmptyBorder(14, 16, 14, 16));

        // Header Dialog
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PRIMARY_RED);
        header.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel lblDlgTitle = new JLabel("CÀO WEB VÀ NẠP VÀO MÁY CHỦ HTTP (CRAWLER ENGINE)");
        lblDlgTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblDlgTitle.setForeground(Color.WHITE);

        JLabel lblDlgSub = new JLabel("Socket & SSLSocket thuần • Gỡ mã chunk hex • Kiểm duyệt Quản trị viên");
        lblDlgSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblDlgSub.setForeground(new Color(254, 226, 226));

        header.add(lblDlgTitle, BorderLayout.NORTH);
        header.add(lblDlgSub, BorderLayout.SOUTH);
        panel.add(header, BorderLayout.NORTH);

        // Form Inputs
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // URL Field
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.25;
        JLabel lblUrl = new JLabel("Địa chỉ URL cần cào:");
        lblUrl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        form.add(lblUrl, gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.75;
        JTextField txtCrawlUrl = new JTextField("https://tuoitre.vn");
        txtCrawlUrl.setFont(new Font("Consolas", Font.BOLD, 13));
        txtCrawlUrl.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BORDER_COLOR, 1), new EmptyBorder(6, 8, 6, 8)));
        form.add(txtCrawlUrl, gbc);

        // Server đích
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.25;
        JLabel lblTarget = new JLabel("Server đích nạp vào:");
        lblTarget.setFont(new Font("Segoe UI", Font.BOLD, 12));
        form.add(lblTarget, gbc);

        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 0.75;
        JComboBox<String> cbTarget = new JComboBox<>(new String[]{
                "Server 1 (Cổng 8080 - Mô phỏng IIS)",
                "Server 2 (Cổng 8081 - Server App)"
        });
        cbTarget.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        cbTarget.setBackground(Color.WHITE);
        form.add(cbTarget, gbc);

        // Tên file lưu
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.25;
        JLabel lblFilename = new JLabel("Tên file lưu trữ:");
        lblFilename.setFont(new Font("Segoe UI", Font.BOLD, 12));
        form.add(lblFilename, gbc);

        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 0.75;
        JTextField txtFilename = new JTextField("crawled_tuoitre.htm");
        txtFilename.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtFilename.setBorder(BorderFactory.createCompoundBorder(new LineBorder(BORDER_COLOR, 1), new EmptyBorder(6, 8, 6, 8)));
        form.add(txtFilename, gbc);

        // Tự động gợi ý tên file theo URL
        txtCrawlUrl.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String u = txtCrawlUrl.getText().trim();
                String d = WebCrawlerEngine.extractCleanDomain(u);
                txtFilename.setText("crawled_" + d + ".htm");
            }
        });

        // Checkbox Đồng bộ IIS
        gbc.gridx = 1; gbc.gridy = 3; gbc.weightx = 0.75;
        JCheckBox chkSyncIis = new JCheckBox("Đồng bộ trang sang Windows IIS (C:\\inetpub\\wwwroot)");
        chkSyncIis.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chkSyncIis.setSelected(true);
        chkSyncIis.setOpaque(false);
        form.add(chkSyncIis, gbc);

        // Log Console
        JTextArea txtCrawlLog = new JTextArea();
        txtCrawlLog.setEditable(false);
        txtCrawlLog.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtCrawlLog.setBackground(new Color(15, 23, 42));
        txtCrawlLog.setForeground(new Color(74, 222, 128)); // Terminal Green
        txtCrawlLog.setMargin(new Insets(8, 10, 8, 10));
        txtCrawlLog.setText(">>> Sẵn sàng cào web. Nhập URL và bấm 'Bắt Đầu Cào'...\n");

        JScrollPane scrollLog = new JScrollPane(txtCrawlLog);
        scrollLog.setBorder(new LineBorder(BORDER_COLOR, 1));
        scrollLog.setPreferredSize(new Dimension(600, 160));

        JPanel centerContainer = new JPanel(new BorderLayout(8, 8));
        centerContainer.setOpaque(false);
        centerContainer.add(form, BorderLayout.NORTH);
        centerContainer.add(scrollLog, BorderLayout.CENTER);
        panel.add(centerContainer, BorderLayout.CENTER);

        // Buttons
        JPanel btm = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        btm.setOpaque(false);

        StyledButton btnStartCrawl = new StyledButton("Bắt Đầu Cào & Nạp Server", PRIMARY_RED, Color.WHITE);
        StyledButton btnClose = new StyledButton("Đóng", Color.WHITE, TEXT_MAIN);

        btnStartCrawl.addActionListener(e -> {
            String crawlUrl = txtCrawlUrl.getText().trim();
            if (crawlUrl.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Vui lòng nhập địa chỉ URL!", "Lỗi", JOptionPane.WARNING_MESSAGE);
                return;
            }

            int targetPort = (cbTarget.getSelectedIndex() == 0) ? 8080 : 8081;
            String saveName = txtFilename.getText().trim();
            boolean syncIis = chkSyncIis.isSelected();

            btnStartCrawl.setEnabled(false);
            txtCrawlLog.append("\n==================================================\n");
            txtCrawlLog.append(">>> [BƯỚC 1/3] Đang kiểm tra Server và kết nối Socket tới " + crawlUrl + "...\n");

            new Thread(() -> {
                try {
                    // Kiểm tra máy chủ Server đích đã được bật chưa!
                    HttpClientHelper.HttpResult checkServer = httpHelper.sendGet("http://localhost:" + targetPort + "/api/time");
                    if (checkServer.hasError()) {
                        SwingUtilities.invokeLater(() -> {
                            btnStartCrawl.setEnabled(true);
                            JOptionPane.showMessageDialog(dialog,
                                    "Máy chủ Server " + (targetPort == 8080 ? "1 (Cổng 8080)" : "2 (Cổng 8081)") + " chưa được bật!\n\n"
                                    + "Vui lòng mở Bảng Điều Khiển Server (Server Dashboard) và bấm nút 'Bật Server' trước khi cào dữ liệu.",
                                    "Máy Chủ Chưa Bật",
                                    JOptionPane.WARNING_MESSAGE);
                            txtCrawlLog.append(">>> [CẢNH BÁO] Máy chủ Server cổng " + targetPort + " chưa được bật! Hãy bật Server trên Server Dashboard.\n");
                            txtCrawlLog.setCaretPosition(txtCrawlLog.getDocument().getLength());
                        });
                        return;
                    }
                    // 1. Cào HTML qua Socket thuần
                    WebCrawlerEngine.CrawlResult res = WebCrawlerEngine.crawl(crawlUrl);
                    SwingUtilities.invokeLater(() -> {
                        txtCrawlLog.append(String.format(">>> [BƯỚC 2/3] Tải xong! Status %d (%d ms). Gỡ chunk hex & chèn <base href> thành công.\n",
                                res.statusCode, res.durationMs));
                        txtCrawlLog.append(">>> [BƯỚC 3/3] Gửi yêu cầu nạp POST /api/ingest tới Server cổng " + targetPort + "...\n");
                        txtCrawlLog.append(">>> [*] ĐANG CHỜ QUẢN TRỊ VIÊN DUYỆT TRÊN SERVER DASHBOARD GUI...\n");
                        txtCrawlLog.setCaretPosition(txtCrawlLog.getDocument().getLength());
                    });

                    // 2. Gửi request nạp trang tới Server /api/ingest
                    String serverApiUrl = "http://localhost:" + targetPort + "/api/ingest";
                    String jsonPayload = String.format(
                            "{\"url\":\"%s\",\"filename\":\"%s\",\"syncIis\":%b,\"html\":\"%s\"}",
                            HttpServerCore.escapeJson(res.finalUrl),
                            HttpServerCore.escapeJson(saveName),
                            syncIis,
                            HttpServerCore.escapeJson(res.htmlContent)
                    );

                    // Sử dụng timeout 60 giây để chờ Quản trị viên bấm Duyệt
                    HttpClientHelper.HttpResult ingestRes = httpHelper.sendPostJson(serverApiUrl, jsonPayload, 60);

                    SwingUtilities.invokeLater(() -> {
                        btnStartCrawl.setEnabled(true);
                        if (ingestRes.statusCode == 201) {
                            String accessUrl = "http://localhost:" + targetPort + "/crawled/" + saveName;
                            txtCrawlLog.append(">>> [THÀNH CÔNG] Quản trị viên đã PHÊ DUYỆT nạp trang!\n");
                            txtCrawlLog.append(">>> Server đang phát tại: " + accessUrl + "\n");
                            txtCrawlLog.setCaretPosition(txtCrawlLog.getDocument().getLength());

                            // Mở tab mới duyệt trang vừa cào
                            addNewBrowserTab(accessUrl);
                            dialog.dispose();
                        } else if (ingestRes.statusCode == 403) {
                            txtCrawlLog.append(">>> [TỪ CHỐI] Quản trị viên trên Server Dashboard đã BẤM TỪ CHỐI nạp trang!\n");
                            txtCrawlLog.setCaretPosition(txtCrawlLog.getDocument().getLength());
                        } else {
                            txtCrawlLog.append(">>> [LỖI] " + ingestRes.errorMessage + "\n");
                            txtCrawlLog.setCaretPosition(txtCrawlLog.getDocument().getLength());
                        }
                    });

                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        btnStartCrawl.setEnabled(true);
                        txtCrawlLog.append(">>> [LỖI CÀO WEB] " + ex.getMessage() + "\n");
                        txtCrawlLog.setCaretPosition(txtCrawlLog.getDocument().getLength());
                    });
                }
            }).start();
        });

        btnClose.addActionListener(e -> dialog.dispose());

        btm.add(btnStartCrawl);
        btm.add(btnClose);
        panel.add(btm, BorderLayout.SOUTH);

        dialog.setContentPane(panel);
        dialog.setVisible(true);
    }

    // =========================================================================
    // TAB: GIAO TIẾP SERVER (CHAT VÀ GỌI API)
    // =========================================================================
    private JPanel createMessagingTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(LIGHT_BG);
        panel.setBorder(new EmptyBorder(12, 14, 12, 14));

        // Phía trên: Chọn Server mục tiêu & Gọi API thời gian
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        topBar.setBackground(CARD_BG);
        topBar.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 1),
                new EmptyBorder(6, 12, 6, 12)
        ));

        JLabel lblChoose = new JLabel("Máy chủ mục tiêu:");
        lblChoose.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblChoose.setForeground(PRIMARY_RED);

        cbServerTarget = new JComboBox<>(new String[]{
                "Server 1 (IIS) - http://localhost:8080",
                "Server 2 (App) - http://localhost:8081"
        });
        cbServerTarget.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        cbServerTarget.setBackground(Color.WHITE);
        cbServerTarget.setForeground(TEXT_MAIN);

        StyledButton btnGetTime = new StyledButton("Hỏi Giờ Server (GET /api/time)", DARK_RED, Color.WHITE);
        btnGetTime.addActionListener(e -> requestServerTime());

        topBar.add(lblChoose);
        topBar.add(cbServerTarget);
        topBar.add(btnGetTime);
        panel.add(topBar, BorderLayout.NORTH);

        // Ở giữa: SplitPane gồm Khung Chat và Khung Soi Gói Tin (Inspector)
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.65);
        splitPane.setBorder(null);

        // Khung Chat
        JPanel chatPanel = new JPanel(new BorderLayout(5, 5));
        chatPanel.setOpaque(false);
        JLabel lblChatTitle = new JLabel("LỊCH SỬ TRAO ĐỔI TIN NHẮN GIỮA CLIENT VÀ SERVER (Slide 17 & 18):");
        lblChatTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblChatTitle.setForeground(PRIMARY_RED);

        txtChatHistory = new JTextArea();
        txtChatHistory.setEditable(false);
        txtChatHistory.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtChatHistory.setForeground(TEXT_MAIN);
        txtChatHistory.setBackground(Color.WHITE);
        txtChatHistory.setMargin(new Insets(10, 12, 10, 12));
        txtChatHistory.setText(">>> Chào mừng đến với khung giao tiếp HTTP Client - Server!\n" +
                ">>> Hãy nhập tin nhắn bên dưới và bấm 'Gửi' để thực hiện POST /api/message.\n\n");

        JScrollPane scrollChat = new JScrollPane(txtChatHistory);
        scrollChat.setBorder(new LineBorder(BORDER_COLOR, 1));
        chatPanel.add(lblChatTitle, BorderLayout.NORTH);
        chatPanel.add(scrollChat, BorderLayout.CENTER);

        // Khung Raw Inspector
        JPanel inspectPanel = new JPanel(new BorderLayout(5, 5));
        inspectPanel.setOpaque(false);
        JLabel lblInspectTitle = new JLabel("CHI TIẾT GÓI TIN HTTP RAW (HEADERS & PAYLOAD):");
        lblInspectTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblInspectTitle.setForeground(TEXT_MAIN);

        txtRawInspect = new JTextArea();
        txtRawInspect.setEditable(false);
        txtRawInspect.setFont(new Font("Consolas", Font.PLAIN, 11));
        txtRawInspect.setBackground(new Color(15, 23, 42));
        txtRawInspect.setForeground(new Color(56, 189, 248));
        txtRawInspect.setMargin(new Insets(8, 10, 8, 10));

        JScrollPane scrollInspect = new JScrollPane(txtRawInspect);
        scrollInspect.setBorder(new LineBorder(BORDER_COLOR, 1));
        inspectPanel.add(lblInspectTitle, BorderLayout.NORTH);
        inspectPanel.add(scrollInspect, BorderLayout.CENTER);

        splitPane.setTopComponent(chatPanel);
        splitPane.setBottomComponent(inspectPanel);
        panel.add(splitPane, BorderLayout.CENTER);

        // Phía dưới: Ô nhập tin nhắn & nút Gửi
        JPanel inputPanel = new JPanel(new BorderLayout(8, 0));
        inputPanel.setBackground(CARD_BG);
        inputPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(PRIMARY_RED, 2, true),
                new EmptyBorder(6, 10, 6, 10)
        ));

        txtMessageInput = new JTextField("Xin chào Server! Tôi là Client Chrome thu nhỏ.");
        txtMessageInput.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtMessageInput.setForeground(TEXT_MAIN);
        txtMessageInput.setBackground(Color.WHITE);
        txtMessageInput.setCaretColor(TEXT_MAIN);
        txtMessageInput.setBorder(null);
        txtMessageInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendMessageToServer();
                }
            }
        });

        StyledButton btnSend = new StyledButton("Gửi Tin Nhắn (POST /api/message)", PRIMARY_RED, Color.WHITE);
        btnSend.addActionListener(e -> sendMessageToServer());

        inputPanel.add(txtMessageInput, BorderLayout.CENTER);
        inputPanel.add(btnSend, BorderLayout.EAST);
        panel.add(inputPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void requestServerTime() {
        String base = getSelectedServerBaseUrl();
        String targetUrl = base + "/api/time";

        new Thread(() -> {
            HttpClientHelper.HttpResult res = httpHelper.sendGet(targetUrl);
            SwingUtilities.invokeLater(() -> {
                String timeNow = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                if (res.hasError()) {
                    txtChatHistory.append(String.format("[%s] [LỖI] Không thể kết nối tới %s: %s\n", timeNow, targetUrl, res.errorMessage));
                } else {
                    txtChatHistory.append(String.format("[%s] [GET %s] (Status %d OK) -> Server trả về: %s\n",
                            timeNow, targetUrl, res.statusCode, res.body));
                    txtRawInspect.setText("=== HTTP GET /api/time RESPONSE ===\n" + res.rawHeaders + "\nBODY:\n" + res.body);
                }
                txtChatHistory.setCaretPosition(txtChatHistory.getDocument().getLength());
            });
        }).start();
    }

    private void sendMessageToServer() {
        String msg = txtMessageInput.getText().trim();
        if (msg.isEmpty()) return;

        String base = getSelectedServerBaseUrl();
        String targetUrl = base + "/api/message";
        String jsonPayload = String.format("{\"text\":\"%s\"}", HttpServerCore.escapeJson(msg));

        String sendTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        txtChatHistory.append(String.format("[%s] [CLIENT GỬI POST] -> %s: \"%s\"\n", sendTime, targetUrl, msg));
        txtMessageInput.setText("");

        new Thread(() -> {
            HttpClientHelper.HttpResult res = httpHelper.sendPostJson(targetUrl, jsonPayload);
            SwingUtilities.invokeLater(() -> {
                String recvTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                if (res.hasError()) {
                    txtChatHistory.append(String.format("[%s] [SERVER PHẢN HỒI LỖI] -> %s\n\n", recvTime, res.errorMessage));
                } else {
                    txtChatHistory.append(String.format("[%s] [SERVER PHẢN HỒI %d] -> Body: %s (Độ trễ: %d ms)\n\n",
                            recvTime, res.statusCode, res.body, res.latencyMs));
                    txtRawInspect.setText("=== HTTP POST /api/message RESPONSE (Status " + res.statusCode + ") ===\n"
                            + res.rawHeaders + "\nBODY:\n" + res.body);
                }
                txtChatHistory.setCaretPosition(txtChatHistory.getDocument().getLength());
            });
        }).start();
    }

    private String getSelectedServerBaseUrl() {
        int idx = cbServerTarget.getSelectedIndex();
        return (idx == 0) ? "http://localhost:8080" : "http://localhost:8081";
    }

    // =========================================================================
    // 3. STATUS BAR DƯỚI CÙNG
    // =========================================================================
    private JPanel createStatusBar() {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBackground(Color.WHITE);
        statusPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 1),
                new EmptyBorder(6, 14, 6, 14)
        ));

        lblConnStatus = new JLabel("● SẴN SÀNG");
        lblConnStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblConnStatus.setForeground(new Color(22, 163, 74));

        lblStatusCodeBadge = new JLabel("[ HTTP 200 OK ]");
        lblStatusCodeBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblStatusCodeBadge.setOpaque(true);
        lblStatusCodeBadge.setBackground(new Color(22, 163, 74));
        lblStatusCodeBadge.setForeground(Color.WHITE);
        lblStatusCodeBadge.setBorder(new EmptyBorder(3, 8, 3, 8));

        JPanel detailsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        detailsPanel.setOpaque(false);

        lblLatency = new JLabel("Thời gian: 0 ms");
        lblContentType = new JLabel("Loại: text/html");
        lblContentSize = new JLabel("Kích thước: 0 B");

        Font detailFont = new Font("Segoe UI", Font.PLAIN, 12);
        lblLatency.setFont(detailFont);
        lblLatency.setForeground(TEXT_MAIN);
        lblContentType.setFont(detailFont);
        lblContentType.setForeground(TEXT_MAIN);
        lblContentSize.setFont(detailFont);
        lblContentSize.setForeground(TEXT_MAIN);

        detailsPanel.add(lblLatency);
        detailsPanel.add(new JSeparator(SwingConstants.VERTICAL));
        detailsPanel.add(lblContentType);
        detailsPanel.add(new JSeparator(SwingConstants.VERTICAL));
        detailsPanel.add(lblContentSize);

        JPanel leftGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftGroup.setOpaque(false);
        leftGroup.add(lblConnStatus);
        leftGroup.add(lblStatusCodeBadge);

        statusPanel.add(leftGroup, BorderLayout.WEST);
        statusPanel.add(detailsPanel, BorderLayout.EAST);
        return statusPanel;
    }

    /**
     * Nút bấm tùy biến không phụ thuộc LookAndFeel của Windows
     */
    public static class StyledButton extends JButton {
        private Color bgColor;
        private Color fgColor;
        private Color hoverColor;

        public StyledButton(String text, Color bg, Color fg) {
            super(text);
            this.bgColor = bg;
            this.fgColor = fg;
            this.hoverColor = bg.equals(Color.WHITE) ? new Color(241, 245, 249) : bg.brighter();
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setForeground(fg);
            setFont(new Font("Segoe UI", Font.BOLD, 12));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(7, 14, 7, 14));
        }

        public void setColors(Color bg, Color fg) {
            this.bgColor = bg;
            this.fgColor = fg;
            this.hoverColor = bg.equals(Color.WHITE) ? new Color(241, 245, 249) : bg.brighter();
            setForeground(fg);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (!isEnabled()) {
                g2.setColor(new Color(226, 232, 240));
            } else if (getModel().isPressed()) {
                g2.setColor(bgColor.equals(Color.WHITE) ? new Color(226, 232, 240) : bgColor.darker());
            } else if (getModel().isRollover()) {
                g2.setColor(hoverColor);
            } else {
                g2.setColor(bgColor);
            }

            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);

            if (bgColor.equals(Color.WHITE)) {
                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            MiniChromeBrowser browser = new MiniChromeBrowser();
            browser.setVisible(true);
        });
    }
}
