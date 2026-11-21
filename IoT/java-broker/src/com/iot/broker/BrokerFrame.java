package com.iot.broker;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Giao diện Swing quản lý Java MQTT Broker Server (High-Contrast Industrial Light + Crimson).
 */
public class BrokerFrame extends JFrame implements MqttBrokerServer.BrokerListener {

    private static final Color BG_CANVAS = new Color(248, 250, 252);
    private static final Color CARD_BG = Color.WHITE;
    private static final Color BORDER_LIGHT = new Color(203, 213, 225);

    private static final Color CRIMSON_DARK = new Color(185, 28, 28);
    private static final Color CRIMSON = new Color(220, 38, 38);
    private static final Color CRIMSON_HOVER = new Color(153, 27, 27);
    private static final Color CRIMSON_BG = new Color(254, 226, 226);

    private static final Color TEXT_BLACK = new Color(15, 23, 42);
    private static final Color TEXT_MUTED = new Color(51, 65, 85);
    private static final Color CHARCOAL_HOVER = new Color(30, 41, 59);

    private static final Color EMERALD = new Color(22, 163, 74);
    private static final Color EMERALD_DARK = new Color(21, 128, 61);
    private static final Color EMERALD_BG = new Color(220, 252, 231);

    private static final Color ORANGE_ACCENT = new Color(234, 88, 12);
    private static final Color BTN_MUTED_BG = new Color(226, 232, 240);
    private static final Color BTN_MUTED_HOVER = new Color(203, 213, 225);

    private final MqttBrokerServer server;
    private final Random random = new Random();
    private Timer autoPublishTimer;

    private PillLabel lblStatusBadge;
    private JLabel lblPortsValue;
    private JLabel lblClientsValue;
    private JLabel lblMessagesValue;

    private RoundedButton btnToggleBroker;
    private RoundedButton btnSendTest;
    private RoundedButton btnAutoPublish;
    private RoundedButton btnClearLogs;
    private JCheckBox chkRequireAuth;

    private DefaultTableModel clientsModel;
    private DefaultTableModel logsModel;

    // Quản lý cơ sở dữ liệu PostgreSQL và giao diện bảng lịch sử
    private DatabaseManager dbManager;
    private PillLabel lblDbBadge;
    private DefaultTableModel dbSensorModel;
    private DefaultTableModel dbActuatorModel;
    private JLabel lblDbStats;
    private RoundedButton btnRefreshDb;
    private RoundedButton btnExportDocx;
    private RoundedButton btnConfigDb;

    public BrokerFrame(int tcpPort, int wsPort) {
        this.server = new MqttBrokerServer(tcpPort, wsPort, this);
        this.autoPublishTimer = new Timer(2000, e -> sendSampleTelemetry());
        this.autoPublishTimer.setInitialDelay(0);

        // Khởi tạo đối tượng DatabaseManager và kết nối tới PostgreSQL
        this.dbManager = new DatabaseManager();
        this.dbManager.connect("localhost", 5432, "postgres", "postgres", "M@giavy265");
        this.server.setDatabaseManager(this.dbManager);

        initUI();
        startBroker();

        // Tự động tải dữ liệu ban đầu lên bảng Database nếu đã kết nối
        refreshDatabaseTables();
    }

    private void initUI() {
        setTitle("IoT Smart Monitoring — Java MQTT Broker Server (TCP "
                + server.getTcpPort() + " & WebSocket " + server.getWsPort() + ")");
        setSize(1020, 740);
        setMinimumSize(new Dimension(650, 600));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopAutoPublish();
                if (server != null) {
                    server.stop();
                }
                if (dbManager != null) {
                    dbManager.disconnect();
                }
                dispose();
                System.exit(0);
            }
        });

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBackground(BG_CANVAS);
        mainPanel.setBorder(new EmptyBorder(12, 14, 14, 14));

        // 1. TOP CRIMSON HEADER + 2-ROW CONTROL CARD
        JPanel topContainer = new JPanel(new BorderLayout(10, 10));
        topContainer.setBackground(BG_CANVAS);

        JPanel headerPanel = new JPanel(new BorderLayout(10, 0));
        headerPanel.setBackground(CRIMSON_DARK);
        headerPanel.setBorder(new EmptyBorder(11, 16, 11, 16));

        JPanel titleStack = new JPanel();
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        titleStack.setOpaque(false);

        JLabel lblTitle = new JLabel("JAVA MQTT BROKER SERVER");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTitle.setForeground(Color.WHITE);

        JLabel lblSubtitle = new JLabel("Pure JDK • TCP " + server.getTcpPort() + " & WebSocket " + server.getWsPort());
        lblSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSubtitle.setForeground(new Color(254, 226, 226));

        titleStack.add(lblTitle);
        titleStack.add(lblSubtitle);

        lblStatusBadge = new PillLabel(" ● ĐANG KHỞI ĐỘNG ", new Color(254, 243, 199), new Color(180, 83, 9), new Color(252, 211, 77));
        lblStatusBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));

        lblDbBadge = new PillLabel(" ● POSTGRESQL ", EMERALD_BG, EMERALD_DARK, new Color(134, 239, 172));
        lblDbBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        updateDbBadge();

        JPanel badgeWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        badgeWrap.setOpaque(false);
        badgeWrap.add(lblStatusBadge);
        badgeWrap.add(lblDbBadge);

        headerPanel.add(titleStack, BorderLayout.CENTER);
        headerPanel.add(badgeWrap, BorderLayout.EAST);

        // Control Bar Card (2 clean rows so half-screen never clips controls)
        CardPanel controlCard = new CardPanel(CARD_BG, BORDER_LIGHT, null);
        controlCard.setLayout(new BoxLayout(controlCard, BoxLayout.Y_AXIS));
        controlCard.setBorder(new EmptyBorder(8, 12, 8, 12));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        btnToggleBroker = new RoundedButton("Dừng Broker", CRIMSON, CRIMSON_HOVER, Color.WHITE, null);
        btnSendTest = new RoundedButton("Bắn 1 Mẫu Test", TEXT_BLACK, CHARCOAL_HOVER, Color.WHITE, null);
        btnAutoPublish = new RoundedButton("Tự Phát Số (2s): TẮT", BTN_MUTED_BG, BTN_MUTED_HOVER, TEXT_BLACK, BORDER_LIGHT);
        btnClearLogs = new RoundedButton("Xóa Nhật Ký", BTN_MUTED_BG, BTN_MUTED_HOVER, TEXT_BLACK, BORDER_LIGHT);

        btnRow.add(btnToggleBroker);
        btnRow.add(btnSendTest);
        btnRow.add(btnAutoPublish);
        btnRow.add(btnClearLogs);

        JPanel optRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        optRow.setOpaque(false);
        optRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        chkRequireAuth = new JCheckBox("Bắt buộc mật khẩu (user1 / 123)", false);
        chkRequireAuth.setOpaque(false);
        chkRequireAuth.setForeground(TEXT_BLACK);
        chkRequireAuth.setFont(new Font("Segoe UI", Font.BOLD, 12));
        chkRequireAuth.setFocusPainted(false);

        JLabel lblHint = new JLabel("• Local Client: localhost:" + server.getTcpPort() + " | Web: ws://localhost:" + server.getWsPort());
        lblHint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblHint.setForeground(TEXT_MUTED);

        optRow.add(chkRequireAuth);
        optRow.add(lblHint);

        controlCard.add(btnRow);
        controlCard.add(optRow);

        btnToggleBroker.addActionListener(e -> toggleBroker());
        btnSendTest.addActionListener(e -> sendSampleTelemetry());
        btnAutoPublish.addActionListener(e -> toggleAutoPublish());
        btnClearLogs.addActionListener(e -> logsModel.setRowCount(0));
        chkRequireAuth.addActionListener(e -> {
            boolean req = chkRequireAuth.isSelected();
            server.setRequireAuth(req);
            appendLogRow("--", "CONFIG", "JavaBroker", "-",
                    req ? "Đã bật xác thực user1 / 123" : "Cho phép kết nối ẩn danh (allow_anonymous)");
        });

        topContainer.add(headerPanel, BorderLayout.NORTH);
        topContainer.add(controlCard, BorderLayout.SOUTH);

        // 2. SUMMARY METRIC CARDS
        JPanel cardsGrid = new JPanel(new GridLayout(1, 3, 14, 14));
        cardsGrid.setBackground(BG_CANVAS);

        lblPortsValue = new JLabel("TCP " + server.getTcpPort() + " • WS " + server.getWsPort(), SwingConstants.CENTER);
        lblPortsValue.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblPortsValue.setForeground(TEXT_BLACK);

        lblClientsValue = new JLabel("0", SwingConstants.CENTER);
        lblClientsValue.setFont(new Font("Segoe UI", Font.BOLD, 34));
        lblClientsValue.setForeground(EMERALD_DARK);

        lblMessagesValue = new JLabel("0", SwingConstants.CENTER);
        lblMessagesValue.setFont(new Font("Segoe UI", Font.BOLD, 34));
        lblMessagesValue.setForeground(CRIMSON_DARK);

        cardsGrid.add(createMetricCard("CỔNG LẮNG NGHE", lblPortsValue, "TCP (Java/ESP32) & WebSocket (Web)", CRIMSON));
        cardsGrid.add(createMetricCard("CLIENT TRỰC TUYẾN", lblClientsValue, "Thiết bị & Ứng dụng đang Online", EMERALD));
        cardsGrid.add(createMetricCard("BẢN TIN ĐÃ XỬ LÝ", lblMessagesValue, "Tổng gói PUBLISH đã điều phối", ORANGE_ACCENT));

        // 3. CENTER SPLIT PANEL: CLIENTS TABLE + LOGS TABLE
        clientsModel = new DefaultTableModel(
                new Object[]{"Client ID", "Giao Thức", "Địa Chỉ IP", "Tài Khoản", "Topics Đăng Ký"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable tblClients = createStyledTable(clientsModel, false);
        tblClients.getColumnModel().getColumn(0).setPreferredWidth(180);
        tblClients.getColumnModel().getColumn(1).setPreferredWidth(90);
        tblClients.getColumnModel().getColumn(2).setPreferredWidth(150);
        tblClients.getColumnModel().getColumn(3).setPreferredWidth(90);
        tblClients.getColumnModel().getColumn(4).setPreferredWidth(410);

        JPanel clientsCard = wrapTableCard("DANH SÁCH CLIENT ĐANG KẾT NỐI", tblClients);

        logsModel = new DefaultTableModel(
                new Object[]{"Thời Gian", "Loại Gói Tin", "Client ID", "Topic", "Nội Dung Payload"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable tblLogs = createStyledTable(logsModel, true);
        tblLogs.getColumnModel().getColumn(0).setPreferredWidth(85);
        tblLogs.getColumnModel().getColumn(1).setPreferredWidth(105);
        tblLogs.getColumnModel().getColumn(2).setPreferredWidth(160);
        tblLogs.getColumnModel().getColumn(3).setPreferredWidth(190);
        tblLogs.getColumnModel().getColumn(4).setPreferredWidth(390);

        JPanel logsCard = wrapTableCard("NHẬT KÝ ĐIỀU PHỐI BẢN TIN MQTT (REALTIME BROKER LOGS)", tblLogs);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, clientsCard, logsCard);
        splitPane.setDividerLocation(175);
        splitPane.setResizeWeight(0.3);
        splitPane.setBackground(BG_CANVAS);
        splitPane.setBorder(null);

        // Bổ sung JTabbedPane chứa cả luồng Điều phối thời gian thực và Lịch sử Database PostgreSQL
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabbedPane.setBackground(BG_CANVAS);
        tabbedPane.addTab("  ⚡ Điều Phối Bản Tin Thời Gian Thực  ", splitPane);
        tabbedPane.addTab("  🗄️ Lịch Sử Cơ Sở Dữ Liệu PostgreSQL & Báo Cáo  ", createDatabaseTab());

        JPanel bodyPanel = new JPanel(new BorderLayout(12, 12));
        bodyPanel.setBackground(BG_CANVAS);
        bodyPanel.add(cardsGrid, BorderLayout.NORTH);
        bodyPanel.add(tabbedPane, BorderLayout.CENTER);

        mainPanel.add(topContainer, BorderLayout.NORTH);
        mainPanel.add(bodyPanel, BorderLayout.CENTER);
        setContentPane(mainPanel);
    }

    private JPanel createMetricCard(String title, JLabel valueLabel, String subtitle, Color accent) {
        CardPanel card = new CardPanel(CARD_BG, BORDER_LIGHT, accent);
        card.setLayout(new BorderLayout(6, 6));
        card.setBorder(new EmptyBorder(12, 14, 12, 14));

        JLabel lblHead = new JLabel(title, SwingConstants.CENTER);
        lblHead.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblHead.setForeground(TEXT_MUTED);

        JLabel lblSub = new JLabel(subtitle, SwingConstants.CENTER);
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblSub.setForeground(TEXT_MUTED);

        card.add(lblHead, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        card.add(lblSub, BorderLayout.SOUTH);
        return card;
    }

    private JPanel wrapTableCard(String title, JTable table) {
        CardPanel panel = new CardPanel(CARD_BG, BORDER_LIGHT, null);
        panel.setLayout(new BorderLayout(6, 6));
        panel.setBorder(new EmptyBorder(10, 12, 12, 12));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTitle.setForeground(TEXT_MUTED);

        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_LIGHT, 1));

        panel.add(lblTitle, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JTable createStyledTable(DefaultTableModel model, boolean colorizePacketType) {
        JTable table = new JTable(model);
        table.setBackground(Color.WHITE);
        table.setForeground(TEXT_BLACK);
        table.setGridColor(new Color(226, 232, 240));
        table.setRowHeight(25);
        table.setFont(new Font("Consolas", Font.PLAIN, 12));
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setSelectionBackground(CRIMSON_BG);
        table.setSelectionForeground(TEXT_BLACK);

        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object val, boolean sel, boolean focus, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(tbl, val, sel, focus, row, col);
                lbl.setOpaque(true);
                lbl.setBackground(TEXT_BLACK);
                lbl.setForeground(Color.WHITE);
                lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
                lbl.setBorder(new EmptyBorder(6, 10, 6, 10));
                return lbl;
            }
        });

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object val, boolean sel, boolean focus, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(tbl, val, sel, focus, row, col);
                lbl.setOpaque(true);
                lbl.setBorder(new EmptyBorder(2, 10, 2, 10));
                lbl.setBackground(sel ? CRIMSON_BG : ((row % 2 == 0) ? Color.WHITE : BG_CANVAS));
                lbl.setForeground(TEXT_BLACK);
                lbl.setFont(new Font("Consolas", Font.PLAIN, 12));

                if (colorizePacketType && col == 1 && val != null) {
                    String type = val.toString().toUpperCase(Locale.ROOT);
                    lbl.setFont(new Font("Consolas", Font.BOLD, 12));
                    if ("CONNECT".equals(type) || "BROKER".equals(type)) {
                        lbl.setForeground(EMERALD_DARK);
                    } else if ("PUBLISH".equals(type)) {
                        lbl.setForeground(new Color(29, 78, 216));
                    } else if ("SUBSCRIBE".equals(type) || "UNSUBSCRIBE".equals(type)) {
                        lbl.setForeground(new Color(126, 34, 206));
                    } else if ("DISCONNECT".equals(type) || "LWT".equals(type) || "ERROR".equals(type) || "AUTH_FAIL".equals(type)) {
                        lbl.setForeground(CRIMSON_DARK);
                    }
                }
                return lbl;
            }
        });
        return table;
    }

    private void startBroker() {
        try {
            server.start();
            updateRunningState(true);
        } catch (IOException e) {
            updateRunningState(false);
            appendLogRow("--", "ERROR", "JavaBroker", "-",
                    "Không mở được cổng " + server.getTcpPort() + "/" + server.getWsPort() + ": " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Không mở được cổng " + server.getTcpPort() + " / " + server.getWsPort()
                            + ".\nHãy kiểm tra xem Mosquitto hoặc ứng dụng khác có đang chiếm cổng không.\nChi tiết: "
                            + e.getMessage(),
                    "Lỗi Khởi Động Broker", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void toggleBroker() {
        if (server.isRunning()) {
            stopAutoPublish();
            server.stop();
            updateRunningState(false);
        } else {
            startBroker();
        }
    }

    private void toggleAutoPublish() {
        if (!server.isRunning()) {
            JOptionPane.showMessageDialog(this, "Broker đang dừng! Hãy khởi động Broker trước.",
                    "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (autoPublishTimer.isRunning()) {
            stopAutoPublish();
        } else {
            btnAutoPublish.setText("ĐANG PHÁT (2s)");
            btnAutoPublish.setPalette(EMERALD, EMERALD_DARK, Color.WHITE, null);
            autoPublishTimer.start();
        }
    }

    private void stopAutoPublish() {
        if (autoPublishTimer != null && autoPublishTimer.isRunning()) {
            autoPublishTimer.stop();
        }
        if (btnAutoPublish != null) {
            btnAutoPublish.setText("Tự Phát Số (2s): TẮT");
            btnAutoPublish.setPalette(BTN_MUTED_BG, BTN_MUTED_HOVER, TEXT_BLACK, BORDER_LIGHT);
        }
    }

    private void sendSampleTelemetry() {
        if (!server.isRunning()) {
            stopAutoPublish();
            JOptionPane.showMessageDialog(this, "Broker đang tắt! Hãy bật Broker trước.",
                    "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        double temp = 27.0 + random.nextDouble() * 5.0;
        double hum = 58.0 + random.nextDouble() * 10.0;
        String json = String.format(Locale.US, "{\"temperature\": %.1f, \"humidity\": %.1f}", temp, hum);
        server.publishServerMessage("esp32/sensor/dht22/data", json, 0, false);
        server.publishServerMessage("sensor/data", json, 0, false);
    }

    private void updateRunningState(boolean running) {
        if (running) {
            lblStatusBadge.setText(" ● ĐANG CHẠY ");
            lblStatusBadge.setPillColors(EMERALD_BG, EMERALD_DARK, new Color(134, 239, 172));
            btnToggleBroker.setText("Dừng Broker");
            btnToggleBroker.setPalette(CRIMSON, CRIMSON_HOVER, Color.WHITE, null);
            btnSendTest.setEnabled(true);
            btnAutoPublish.setEnabled(true);
        } else {
            lblStatusBadge.setText(" ● ĐÃ DỪNG ");
            lblStatusBadge.setPillColors(CRIMSON_BG, CRIMSON_DARK, new Color(252, 165, 165));
            btnToggleBroker.setText("Khởi Động Broker");
            btnToggleBroker.setPalette(EMERALD, EMERALD_DARK, Color.WHITE, null);
            btnSendTest.setEnabled(false);
            btnAutoPublish.setEnabled(false);
        }
    }

    private void appendLogRow(String time, String type, String clientId, String topic, String detail) {
        logsModel.insertRow(0, new Object[]{time, type, clientId, topic, detail});
        while (logsModel.getRowCount() > 500) {
            logsModel.removeRow(logsModel.getRowCount() - 1);
        }
    }

    /**
     * Tạo giao diện Tab 2: Lịch sử Cơ sở Dữ liệu PostgreSQL và Xuất Báo cáo Word (.docx)
     */
    private JPanel createDatabaseTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(BG_CANVAS);
        panel.setBorder(new EmptyBorder(8, 0, 0, 0));

        // Thanh công cụ thao tác trên cơ sở dữ liệu
        JPanel bar = new JPanel(new BorderLayout(8, 8));
        bar.setOpaque(false);

        JPanel leftBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        leftBtns.setOpaque(false);

        btnRefreshDb = new RoundedButton("🔄 Làm Mới Dữ Liệu", TEXT_BLACK, CHARCOAL_HOVER, Color.WHITE, null);
        btnExportDocx = new RoundedButton("📄 Xuất Báo Cáo Word (.docx)", new Color(29, 78, 216), new Color(30, 64, 175), Color.WHITE, null);
        btnConfigDb = new RoundedButton("⚙️ Cấu Hình PostgreSQL", BTN_MUTED_BG, BTN_MUTED_HOVER, TEXT_BLACK, BORDER_LIGHT);

        leftBtns.add(btnRefreshDb);
        leftBtns.add(btnExportDocx);
        leftBtns.add(btnConfigDb);

        lblDbStats = new JLabel("Chưa tải dữ liệu | Nhấn 'Làm Mới' để xem bản ghi từ PostgreSQL");
        lblDbStats.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblDbStats.setForeground(TEXT_MUTED);
        lblDbStats.setBorder(new EmptyBorder(0, 8, 0, 8));

        bar.add(leftBtns, BorderLayout.WEST);
        bar.add(lblDbStats, BorderLayout.EAST);

        // Bảng 1: Lịch sử cảm biến DHT22
        dbSensorModel = new DefaultTableModel(
                new Object[]{"ID", "Thời Gian Ghi", "Thiết Bị", "Nhiệt Độ (°C)", "Độ Ẩm (%)", "Chỉ Số Nhiệt (°C)", "MQTT Topic"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        JTable tblSensor = createStyledTable(dbSensorModel, false);
        tblSensor.getColumnModel().getColumn(0).setPreferredWidth(60);
        tblSensor.getColumnModel().getColumn(1).setPreferredWidth(160);
        tblSensor.getColumnModel().getColumn(2).setPreferredWidth(120);
        tblSensor.getColumnModel().getColumn(3).setPreferredWidth(100);
        tblSensor.getColumnModel().getColumn(4).setPreferredWidth(100);
        tblSensor.getColumnModel().getColumn(5).setPreferredWidth(110);
        tblSensor.getColumnModel().getColumn(6).setPreferredWidth(200);

        JPanel sensorCard = wrapTableCard("BẢNG 1: LỊCH SỬ DỮ LIỆU CẢM BIẾN (sensor_data) TỪ ESP32", tblSensor);

        // Bảng 2: Lịch sử điều khiển đèn LED
        dbActuatorModel = new DefaultTableModel(
                new Object[]{"ID", "Thời Gian Ghi", "Thiết Bị", "Lệnh Điều Khiển", "Nguồn Kích Hoạt", "MQTT Topic"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        JTable tblActuator = createStyledTable(dbActuatorModel, false);
        tblActuator.getColumnModel().getColumn(0).setPreferredWidth(60);
        tblActuator.getColumnModel().getColumn(1).setPreferredWidth(160);
        tblActuator.getColumnModel().getColumn(2).setPreferredWidth(120);
        tblActuator.getColumnModel().getColumn(3).setPreferredWidth(120);
        tblActuator.getColumnModel().getColumn(4).setPreferredWidth(140);
        tblActuator.getColumnModel().getColumn(5).setPreferredWidth(200);

        JPanel actuatorCard = wrapTableCard("BẢNG 2: LỊCH SỬ ĐIỀU KHIỂN & TRẠNG THÁI THIẾT BỊ (actuator_logs)", tblActuator);

        JSplitPane dbSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sensorCard, actuatorCard);
        dbSplit.setDividerLocation(240);
        dbSplit.setResizeWeight(0.6);
        dbSplit.setBackground(BG_CANVAS);
        dbSplit.setBorder(null);

        panel.add(bar, BorderLayout.NORTH);
        panel.add(dbSplit, BorderLayout.CENTER);

        // Đăng ký sự kiện các nút
        btnRefreshDb.addActionListener(e -> refreshDatabaseTables());
        btnExportDocx.addActionListener(e -> exportDocxReport());
        btnConfigDb.addActionListener(e -> showDbConfigDialog());

        return panel;
    }

    /**
     * Cập nhật màu sắc và trạng thái huy hiệu PostgreSQL trên thanh tiêu đề
     */
    private void updateDbBadge() {
        if (lblDbBadge == null) return;
        if (dbManager != null && dbManager.isConnected()) {
            lblDbBadge.setText(" ● POSTGRESQL: ĐÃ KẾT NỐI (5432) ");
            lblDbBadge.setPillColors(EMERALD_BG, EMERALD_DARK, new Color(134, 239, 172));
        } else {
            lblDbBadge.setText(" ● POSTGRESQL: CHƯA KẾT NỐI ");
            lblDbBadge.setPillColors(CRIMSON_BG, CRIMSON_DARK, new Color(252, 165, 165));
        }
    }

    /**
     * Tải dữ liệu mới nhất từ PostgreSQL lên 2 bảng giao diện
     */
    private void refreshDatabaseTables() {
        if (dbManager == null || !dbManager.isConnected()) {
            updateDbBadge();
            if (lblDbStats != null) {
                lblDbStats.setText("PostgreSQL chưa kết nối! Nhấn 'Cấu Hình PostgreSQL' để kết nối.");
            }
            return;
        }

        SwingUtilities.invokeLater(() -> {
            updateDbBadge();

            // 1. Tải dữ liệu cảm biến
            List<DatabaseManager.SensorRecord> sensors = dbManager.getRecentSensorData(100);
            if (dbSensorModel != null) {
                dbSensorModel.setRowCount(0);
                for (DatabaseManager.SensorRecord r : sensors) {
                    dbSensorModel.addRow(new Object[]{
                            r.id,
                            r.recordedAt,
                            r.deviceId,
                            String.format(Locale.US, "%.1f", r.temperature),
                            String.format(Locale.US, "%.1f", r.humidity),
                            String.format(Locale.US, "%.1f", r.heatIndex),
                            r.topic
                    });
                }
            }

            // 2. Tải dữ liệu điều khiển đèn LED
            List<DatabaseManager.ActuatorRecord> actuators = dbManager.getRecentActuatorLogs(50);
            if (dbActuatorModel != null) {
                dbActuatorModel.setRowCount(0);
                for (DatabaseManager.ActuatorRecord r : actuators) {
                    dbActuatorModel.addRow(new Object[]{
                            r.id,
                            r.recordedAt,
                            r.deviceId,
                            r.action,
                            r.triggeredBy,
                            r.topic
                    });
                }
            }

            if (lblDbStats != null) {
                lblDbStats.setText(String.format("Đã tải: %d mẫu cảm biến, %d nhật ký đèn LED | PostgreSQL: localhost:5432/postgres",
                        sensors.size(), actuators.size()));
            }
        });
    }

    /**
     * Xuất báo cáo dữ liệu lịch sử ra file Microsoft Word (.docx)
     */
    private void exportDocxReport() {
        if (dbManager == null || !dbManager.isConnected()) {
            JOptionPane.showMessageDialog(this,
                    "Chưa kết nối cơ sở dữ liệu PostgreSQL! Hãy kiểm tra kết nối trước.",
                    "Chưa Có Dữ Liệu", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<DatabaseManager.SensorRecord> records = dbManager.getRecentSensorData(200);
        if (records.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Hiện tại chưa có bản ghi nào trong bảng sensor_data để xuất báo cáo!",
                    "Bảng Trống", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Lưu Báo Cáo Dữ Liệu IoT (.docx)");
        fileChooser.setSelectedFile(new File("BaoCao_GiamSat_IoT_DHT22.docx"));
        fileChooser.setFileFilter(new FileNameExtensionFilter("Tài liệu Microsoft Word (*.docx)", "docx"));

        int userSelection = fileChooser.showSaveDialog(this);
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToSave = fileChooser.getSelectedFile();
            if (!fileToSave.getName().toLowerCase().endsWith(".docx")) {
                fileToSave = new File(fileToSave.getAbsolutePath() + ".docx");
            }

            boolean ok = dbManager.exportToDocx(fileToSave, records);
            if (ok) {
                int choice = JOptionPane.showConfirmDialog(this,
                        "Xuất báo cáo Word (.docx) thành công!\nĐường dẫn: " + fileToSave.getAbsolutePath()
                                + "\n\nBạn có muốn mở file báo cáo ngay bây giờ không?",
                        "Xuất Báo Cáo Thành Công", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);

                if (choice == JOptionPane.YES_OPTION) {
                    try {
                        if (Desktop.isDesktopSupported()) {
                            Desktop.getDesktop().open(fileToSave);
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(this, "Không mở được ứng dụng Word: " + ex.getMessage());
                    }
                }
            } else {
                JOptionPane.showMessageDialog(this,
                        "Xuất file thất bại! Lỗi: " + dbManager.getLastError(),
                        "Lỗi Xuất File", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Hộp thoại cấu hình nhanh thông số kết nối PostgreSQL
     */
    private void showDbConfigDialog() {
        JTextField txtHost = new JTextField("localhost", 15);
        JTextField txtPort = new JTextField("5432", 6);
        JTextField txtDb = new JTextField("postgres", 12);
        JTextField txtUser = new JTextField("postgres", 12);
        JPasswordField txtPass = new JPasswordField("M@giavy265", 15);

        JPanel form = new JPanel(new GridLayout(5, 2, 8, 8));
        form.add(new JLabel("Máy chủ (Host):"));
        form.add(txtHost);
        form.add(new JLabel("Cổng (Port):"));
        form.add(txtPort);
        form.add(new JLabel("Tên Database:"));
        form.add(txtDb);
        form.add(new JLabel("Tài khoản (User):"));
        form.add(txtUser);
        form.add(new JLabel("Mật khẩu (Password):"));
        form.add(txtPass);

        int result = JOptionPane.showConfirmDialog(this, form,
                "Cấu Hình Kết Nối PostgreSQL", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String host = txtHost.getText().trim();
            int port = 5432;
            try {
                port = Integer.parseInt(txtPort.getText().trim());
            } catch (NumberFormatException ignored) {}
            String db = txtDb.getText().trim();
            String user = txtUser.getText().trim();
            String pass = new String(txtPass.getPassword());

            boolean connected = dbManager.connect(host, port, db, user, pass);
            updateDbBadge();
            if (connected) {
                JOptionPane.showMessageDialog(this,
                        "Kết nối thành công tới PostgreSQL: " + host + ":" + port + "/" + db,
                        "Kết Nối Thành Công", JOptionPane.INFORMATION_MESSAGE);
                refreshDatabaseTables();
            } else {
                JOptionPane.showMessageDialog(this,
                        "Kết nối thất bại!\nChi tiết: " + dbManager.getLastError(),
                        "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    @Override
    public void onLog(String time, String type, String clientId, String topic, String detail) {
        SwingUtilities.invokeLater(() -> appendLogRow(time, type, clientId, topic, detail));
    }

    @Override
    public void onClientsChanged(List<MqttBrokerServer.ClientInfo> clients) {
        SwingUtilities.invokeLater(() -> {
            clientsModel.setRowCount(0);
            for (MqttBrokerServer.ClientInfo c : clients) {
                String topics = c.subscriptions.isEmpty()
                        ? "(chưa subscribe)"
                        : String.join(", ", c.subscriptions.keySet());
                clientsModel.addRow(new Object[]{
                        c.clientId,
                        c.transport,
                        c.remoteAddress,
                        c.username.isEmpty() ? "(anonymous)" : c.username,
                        topics
                });
            }
        });
    }

    @Override
    public void onStatsChanged(int connectedClients, long totalMessages) {
        SwingUtilities.invokeLater(() -> {
            lblClientsValue.setText(String.valueOf(connectedClients));
            lblMessagesValue.setText(String.valueOf(totalMessages));
        });
    }

    // --- Custom Swing Components (Windows L&F Independent) ---

    private static class RoundedButton extends JButton {
        private Color baseBg;
        private Color hoverBg;
        private Color fgColor;
        private Color borderCol;
        private boolean hovered = false;

        RoundedButton(String text, Color bg, Color hover, Color fg, Color border) {
            super(text);
            this.baseBg = bg;
            this.hoverBg = hover;
            this.fgColor = fg;
            this.borderCol = border;
            setFont(new Font("Segoe UI", Font.BOLD, 12));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(7, 13, 7, 13));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }
            });
        }

        void setPalette(Color bg, Color hover, Color fg, Color border) {
            this.baseBg = bg;
            this.hoverBg = hover;
            this.fgColor = fg;
            this.borderCol = border;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            Color fill;
            Color textCol;
            if (!isEnabled()) {
                fill = new Color(226, 232, 240);
                textCol = new Color(100, 116, 139);
            } else if (getModel().isPressed()) {
                fill = hoverBg.darker();
                textCol = fgColor;
            } else if (hovered) {
                fill = hoverBg;
                textCol = fgColor;
            } else {
                fill = baseBg;
                textCol = fgColor;
            }

            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 10, 10));
            if (borderCol != null && isEnabled()) {
                g2.setColor(borderCol);
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 2, h - 2, 10, 10));
            }
            g2.dispose();

            setForeground(textCol);
            super.paintComponent(g);
        }
    }

    private static class CardPanel extends JPanel {
        private final Color cardBg;
        private final Color borderCol;
        private final Color topAccent;

        CardPanel(Color bg, Color border, Color topAccent) {
            this.cardBg = bg;
            this.borderCol = border;
            this.topAccent = topAccent;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            Shape cardShape = new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 14, 14);
            g2.setColor(cardBg);
            g2.fill(cardShape);

            if (topAccent != null) {
                Shape oldClip = g2.getClip();
                g2.setClip(cardShape);
                g2.setColor(topAccent);
                g2.fillRect(0, 0, w, 4);
                g2.setClip(oldClip);
            }

            g2.setColor(borderCol);
            g2.draw(cardShape);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class PillLabel extends JLabel {
        private Color bg;
        private Color border;

        PillLabel(String text, Color bg, Color fg, Color border) {
            super(text, SwingConstants.CENTER);
            this.bg = bg;
            this.border = border;
            setForeground(fg);
            setOpaque(false);
            setBorder(new EmptyBorder(4, 10, 4, 10));
        }

        void setPillColors(Color bg, Color fg, Color border) {
            this.bg = bg;
            this.border = border;
            setForeground(fg);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Double(0, 0, w - 1, h - 1, h - 2, h - 2));
            if (border != null) {
                g2.setColor(border);
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 2, h - 2, h - 2, h - 2));
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static void main(String[] args) throws Exception {
        if (Arrays.asList(args).contains("--console")) {
            MqttBrokerServer.main(args);
            return;
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        int tcpPort = 1883;
        int wsPort = 9001;
        for (int i = 0; i < args.length; i++) {
            if ("--tcp".equals(args[i]) && i + 1 < args.length) {
                tcpPort = Integer.parseInt(args[++i]);
            } else if ("--ws".equals(args[i]) && i + 1 < args.length) {
                wsPort = Integer.parseInt(args[++i]);
            }
        }
        final int tcp = tcpPort;
        final int ws = wsPort;
        SwingUtilities.invokeLater(() -> new BrokerFrame(tcp, ws).setVisible(true));
    }
}
