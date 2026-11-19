package com.iot;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Giao diện Swing Desktop Client giám sát IoT & Điều khiển ESP32 (High-Contrast Industrial Light + Crimson)
 */
public class MainFrame extends JFrame implements MqttManager.MqttListener {

    private final String TOPIC_TELEMETRY = "esp32/sensor/dht22/data";
    private final String TOPIC_LEGACY_TELEMETRY = "sensor/data";
    private final String TOPIC_LED_CMD = "esp32/led/command";
    private final String TOPIC_STATUS = "esp32/status";

    // Bảng màu shadcn/ui Light + Đỏ Thẫm + Đen Sắc Nét (tương phản cao >= 7:1 trên Windows)
    private static final Color BG_CANVAS = new Color(248, 250, 252);
    private static final Color CARD_BG = Color.WHITE;
    private static final Color ALERT_BG = new Color(254, 242, 242);
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
    private static final Color TEAL = new Color(8, 145, 178);
    private static final Color TEAL_DARK = new Color(15, 118, 110);
    private static final Color BTN_MUTED_BG = new Color(226, 232, 240);
    private static final Color BTN_MUTED_HOVER = new Color(203, 213, 225);

    private MqttManager mqttManager;

    // UI Elements - Settings
    private JTextField txtBrokerUrl;
    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private RoundedButton btnConnect;
    private RoundedButton btnDisconnect;
    private RoundedButton btnPublic;
    private RoundedButton btnLocal;
    private PillLabel lblStatusBadge;
    private JLabel lblModeHint;

    // UI Elements - Telemetry Cards
    private CardPanel pnlTempCard;
    private GaugePanel tempGauge;
    private PillLabel lblTempAlert;
    private CardPanel pnlHumCard;
    private GaugePanel humGauge;
    private PillLabel lblHumStandard;

    // UI Elements - LED Control
    private LedBulbPanel pnlLedVisual;
    private JLabel lblLedStatus;
    private RoundedButton btnLedOn;
    private RoundedButton btnLedOff;
    private JCheckBox chkAutoLed;
    private boolean ledStateOn = false;
    private boolean autoLedTriggered = false;

    // UI Elements - Logs & Export
    private DefaultTableModel tableModel;
    private JTable logTable;
    private RoundedButton btnExportCsv;
    private RoundedButton btnClearLogs;

    public MainFrame() {
        mqttManager = new MqttManager(this);
        initUI();
        autoConnectOnStartup();
    }

    private void initUI() {
        setTitle("IoT Smart Monitoring & Control System (Java Client)");
        setSize(1020, 750);
        setMinimumSize(new Dimension(650, 620));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBackground(BG_CANVAS);
        mainPanel.setBorder(new EmptyBorder(12, 14, 14, 14));

        // 1. TOP CRIMSON BRAND HEADER + RESPONSIVE 2-ROW SETTINGS CARD
        JPanel topContainer = new JPanel(new BorderLayout(10, 10));
        topContainer.setBackground(BG_CANVAS);

        JPanel headerPanel = new JPanel(new BorderLayout(10, 0));
        headerPanel.setBackground(CRIMSON_DARK);
        headerPanel.setBorder(new EmptyBorder(11, 16, 11, 16));

        JPanel titleStack = new JPanel();
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        titleStack.setOpaque(false);

        JLabel lblTitle = new JLabel("SGU IOT • TRẠM GIÁM SÁT & ĐIỀU KHIỂN (JAVA CLIENT)");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTitle.setForeground(Color.WHITE);

        JLabel lblSubtitle = new JLabel("Giao thức MQTT • Đồng bộ ESP32 Wokwi & Java Broker");
        lblSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSubtitle.setForeground(new Color(254, 226, 226));

        titleStack.add(lblTitle);
        titleStack.add(lblSubtitle);

        lblStatusBadge = new PillLabel(" ● NGOẠI TUYẾN ", CRIMSON_BG, CRIMSON_DARK, new Color(252, 165, 165));
        lblStatusBadge.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JPanel badgeWrap = new JPanel(new GridBagLayout());
        badgeWrap.setOpaque(false);
        badgeWrap.add(lblStatusBadge);

        headerPanel.add(titleStack, BorderLayout.CENTER);
        headerPanel.add(badgeWrap, BorderLayout.EAST);

        // Settings Card (2-row responsive)
        CardPanel settingsCard = new CardPanel(CARD_BG, BORDER_LIGHT, null);
        settingsCard.setLayout(new BoxLayout(settingsCard, BoxLayout.Y_AXIS));
        settingsCard.setBorder(new EmptyBorder(10, 14, 10, 14));

        // Row 1: Profile Segmented Buttons + Connect/Disconnect + Hint
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        row1.setOpaque(false);
        row1.setAlignmentX(Component.LEFT_ALIGNMENT);

        btnPublic = new RoundedButton("Public EMQX (Wokwi)", CRIMSON, CRIMSON_HOVER, Color.WHITE, null);
        btnLocal = new RoundedButton("Local (Java Broker)", BTN_MUTED_BG, BTN_MUTED_HOVER, TEXT_BLACK, BORDER_LIGHT);
        btnConnect = new RoundedButton("Kết Nối", TEXT_BLACK, CHARCOAL_HOVER, Color.WHITE, null);
        btnDisconnect = new RoundedButton("Ngắt Kết Nối", CRIMSON, CRIMSON_HOVER, Color.WHITE, null);
        btnDisconnect.setEnabled(false);

        lblModeHint = new JLabel("  • Chế độ: Đang dò kết nối...");
        lblModeHint.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblModeHint.setForeground(TEXT_MUTED);

        row1.add(btnPublic);
        row1.add(btnLocal);
        row1.add(btnConnect);
        row1.add(btnDisconnect);
        row1.add(lblModeHint);

        // Row 2: Connection Inputs (GridBagLayout scales cleanly down to 650px width)
        JPanel row2 = new JPanel(new GridBagLayout());
        row2.setOpaque(false);
        row2.setAlignmentX(Component.LEFT_ALIGNMENT);
        row2.setBorder(new EmptyBorder(4, 2, 0, 2));

        txtBrokerUrl = createStyledField("tcp://broker.emqx.io:1883");
        txtUsername = createStyledField("");
        txtPassword = new JPasswordField("");
        styleField(txtPassword);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 0, 2, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        row2.add(createFieldLabel("Broker URL:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        row2.add(txtBrokerUrl, gbc);

        gbc.gridx = 2; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        row2.add(createFieldLabel("Tài khoản:"), gbc);

        gbc.gridx = 3; gbc.weightx = 0.25; gbc.fill = GridBagConstraints.HORIZONTAL;
        row2.add(txtUsername, gbc);

        gbc.gridx = 4; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        row2.add(createFieldLabel("Mật khẩu:"), gbc);

        gbc.gridx = 5; gbc.weightx = 0.25; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 0, 2, 0);
        row2.add(txtPassword, gbc);

        settingsCard.add(row1);
        settingsCard.add(row2);

        topContainer.add(headerPanel, BorderLayout.NORTH);
        topContainer.add(settingsCard, BorderLayout.SOUTH);

        // 2. CENTER PANEL (3 CUSTOM-PAINTED DASHBOARD CARDS)
        JPanel centerGrid = new JPanel(new GridLayout(1, 3, 14, 14));
        centerGrid.setBackground(BG_CANVAS);

        // Card 1: Temperature Gauge
        pnlTempCard = new CardPanel(CARD_BG, BORDER_LIGHT, ORANGE_ACCENT);
        pnlTempCard.setLayout(new BorderLayout(6, 6));
        pnlTempCard.setBorder(new EmptyBorder(14, 14, 12, 14));

        JLabel lblTempTitle = createCardHeader("NHIỆT ĐỘ (TEMPERATURE)", "DHT22");
        tempGauge = new GaugePanel("°C", ORANGE_ACCENT, CRIMSON, 35.0, true);
        lblTempAlert = new PillLabel(" AN TOÀN (<= 35°C) ", EMERALD_BG, EMERALD_DARK, new Color(134, 239, 172));
        lblTempAlert.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JPanel tempFooter = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        tempFooter.setOpaque(false);
        tempFooter.add(lblTempAlert);

        pnlTempCard.add(lblTempTitle, BorderLayout.NORTH);
        pnlTempCard.add(tempGauge, BorderLayout.CENTER);
        pnlTempCard.add(tempFooter, BorderLayout.SOUTH);

        // Card 2: Humidity Gauge
        pnlHumCard = new CardPanel(CARD_BG, BORDER_LIGHT, TEAL);
        pnlHumCard.setLayout(new BorderLayout(6, 6));
        pnlHumCard.setBorder(new EmptyBorder(14, 14, 12, 14));

        JLabel lblHumTitle = createCardHeader("ĐỘ ẨM (HUMIDITY)", "DHT22");
        humGauge = new GaugePanel("%", TEAL, TEAL, -1, false);
        lblHumStandard = new PillLabel(" Dải chuẩn phòng: 40% - 70% ", new Color(224, 242, 254), new Color(3, 105, 161), new Color(125, 211, 252));
        lblHumStandard.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JPanel humFooter = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        humFooter.setOpaque(false);
        humFooter.add(lblHumStandard);

        pnlHumCard.add(lblHumTitle, BorderLayout.NORTH);
        pnlHumCard.add(humGauge, BorderLayout.CENTER);
        pnlHumCard.add(humFooter, BorderLayout.SOUTH);

        // Card 3: LED Control Card
        CardPanel pnlControlCard = new CardPanel(CARD_BG, BORDER_LIGHT, CRIMSON);
        pnlControlCard.setLayout(new BorderLayout(6, 6));
        pnlControlCard.setBorder(new EmptyBorder(14, 14, 12, 14));

        JLabel lblCtrlTitle = createCardHeader("ĐIỀU KHIỂN ĐÈN LED", "GPIO 4");

        JPanel ctrlCenter = new JPanel();
        ctrlCenter.setLayout(new BoxLayout(ctrlCenter, BoxLayout.Y_AXIS));
        ctrlCenter.setOpaque(false);

        pnlLedVisual = new LedBulbPanel();
        pnlLedVisual.setAlignmentX(Component.CENTER_ALIGNMENT);

        lblLedStatus = new JLabel("ĐÈN: TẮT (OFF)", SwingConstants.CENTER);
        lblLedStatus.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblLedStatus.setForeground(TEXT_BLACK);
        lblLedStatus.setAlignmentX(Component.CENTER_ALIGNMENT);

        chkAutoLed = new JCheckBox("Tự bật đèn khi > 35°C", true);
        chkAutoLed.setFont(new Font("Segoe UI", Font.BOLD, 12));
        chkAutoLed.setForeground(TEXT_BLACK);
        chkAutoLed.setOpaque(false);
        chkAutoLed.setFocusPainted(false);
        chkAutoLed.setAlignmentX(Component.CENTER_ALIGNMENT);

        ctrlCenter.add(Box.createVerticalGlue());
        ctrlCenter.add(pnlLedVisual);
        ctrlCenter.add(Box.createVerticalStrut(6));
        ctrlCenter.add(lblLedStatus);
        ctrlCenter.add(Box.createVerticalStrut(6));
        ctrlCenter.add(chkAutoLed);
        ctrlCenter.add(Box.createVerticalGlue());

        btnLedOn = new RoundedButton("BẬT ĐÈN (ON)", EMERALD, EMERALD_DARK, Color.WHITE, null);
        btnLedOff = new RoundedButton("TẮT ĐÈN (OFF)", TEXT_BLACK, CHARCOAL_HOVER, Color.WHITE, null);

        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(btnLedOn);
        btnPanel.add(btnLedOff);

        pnlControlCard.add(lblCtrlTitle, BorderLayout.NORTH);
        pnlControlCard.add(ctrlCenter, BorderLayout.CENTER);
        pnlControlCard.add(btnPanel, BorderLayout.SOUTH);

        centerGrid.add(pnlTempCard);
        centerGrid.add(pnlHumCard);
        centerGrid.add(pnlControlCard);

        // 3. BOTTOM PANEL: LOG TABLE & ACTIONS
        CardPanel bottomCard = new CardPanel(CARD_BG, BORDER_LIGHT, null);
        bottomCard.setLayout(new BorderLayout(8, 8));
        bottomCard.setBorder(new EmptyBorder(12, 14, 10, 14));

        JLabel lblLogTitle = new JLabel("NHẬT KÝ DỮ LIỆU THỜI GIAN THỰC (REALTIME MQTT LOGS)");
        lblLogTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblLogTitle.setForeground(TEXT_MUTED);

        tableModel = new DefaultTableModel(new String[]{"Thời Gian", "Topic", "Nội Dung Payload"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        logTable = new JTable(tableModel);
        styleTable(logTable);
        logTable.getColumnModel().getColumn(0).setPreferredWidth(115);
        logTable.getColumnModel().getColumn(1).setPreferredWidth(210);
        logTable.getColumnModel().getColumn(2).setPreferredWidth(520);

        JScrollPane scrollPane = new JScrollPane(logTable);
        scrollPane.setPreferredSize(new Dimension(600, 165));
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_LIGHT, 1));

        JPanel bottomActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bottomActions.setOpaque(false);

        btnExportCsv = new RoundedButton("Xuất Báo Cáo CSV", TEAL_DARK, new Color(17, 94, 89), Color.WHITE, null);
        btnClearLogs = new RoundedButton("Xóa Nhật Ký", BTN_MUTED_BG, BTN_MUTED_HOVER, TEXT_BLACK, BORDER_LIGHT);

        bottomActions.add(btnExportCsv);
        bottomActions.add(btnClearLogs);

        bottomCard.add(lblLogTitle, BorderLayout.NORTH);
        bottomCard.add(scrollPane, BorderLayout.CENTER);
        bottomCard.add(bottomActions, BorderLayout.SOUTH);

        mainPanel.add(topContainer, BorderLayout.NORTH);
        mainPanel.add(centerGrid, BorderLayout.CENTER);
        mainPanel.add(bottomCard, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // 4. ACTION LISTENERS
        btnConnect.addActionListener(e -> connectBroker());
        btnDisconnect.addActionListener(e -> disconnectBroker());

        btnLedOn.addActionListener(e -> sendLedCommand("ON"));
        btnLedOff.addActionListener(e -> sendLedCommand("OFF"));

        btnLocal.addActionListener(e -> switchProfile("tcp://localhost:1883", "user1", "123"));
        btnPublic.addActionListener(e -> switchProfile("tcp://broker.emqx.io:1883", "", ""));

        btnExportCsv.addActionListener(e -> exportToCsv());
        btnClearLogs.addActionListener(e -> tableModel.setRowCount(0));
    }

    private JLabel createFieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(TEXT_BLACK);
        return lbl;
    }

    private JTextField createStyledField(String value) {
        JTextField tf = new JTextField(value);
        styleField(tf);
        return tf;
    }

    private void styleField(JTextField field) {
        field.setFont(new Font("Consolas", Font.PLAIN, 13));
        field.setForeground(TEXT_BLACK);
        field.setBackground(Color.WHITE);
        field.setCaretColor(TEXT_BLACK);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_LIGHT, 1),
                new EmptyBorder(5, 8, 5, 8)
        ));
    }

    private JLabel createCardHeader(String title, String tag) {
        JLabel lbl = new JLabel(title + "  [" + tag + "]");
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lbl.setForeground(TEXT_MUTED);
        return lbl;
    }

    private void styleTable(JTable table) {
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
                if (sel) {
                    lbl.setBackground(CRIMSON_BG);
                    lbl.setForeground(TEXT_BLACK);
                } else {
                    lbl.setBackground((row % 2 == 0) ? Color.WHITE : BG_CANVAS);
                    lbl.setForeground(TEXT_BLACK);
                }
                return lbl;
            }
        });
    }

    private void updateProfileButtons(String url) {
        boolean isLocal = url != null && (url.contains("localhost") || url.contains("127.0.0."));
        if (isLocal) {
            btnLocal.setPalette(CRIMSON, CRIMSON_HOVER, Color.WHITE, null);
            btnPublic.setPalette(BTN_MUTED_BG, BTN_MUTED_HOVER, TEXT_BLACK, BORDER_LIGHT);
            lblModeHint.setText("  • Chế độ: Local (nối tới Java Broker 1883)");
            lblModeHint.setForeground(TEXT_BLACK);
        } else {
            btnPublic.setPalette(CRIMSON, CRIMSON_HOVER, Color.WHITE, null);
            btnLocal.setPalette(BTN_MUTED_BG, BTN_MUTED_HOVER, TEXT_BLACK, BORDER_LIGHT);
            lblModeHint.setText("  • Chế độ: Public EMQX (nhận trực tiếp từ Wokwi VS Code)");
            lblModeHint.setForeground(CRIMSON_DARK);
        }
    }

    private void autoConnectOnStartup() {
        new Thread(() -> {
            boolean localRunning = false;
            try (Socket probe = new Socket()) {
                probe.connect(new InetSocketAddress("127.0.0.2", 1883), 120);
                localRunning = true;
            } catch (Exception ignored) {
            }
            final boolean useLocal = localRunning;
            SwingUtilities.invokeLater(() -> {
                if (useLocal) {
                    switchProfile("tcp://localhost:1883", "user1", "123");
                } else {
                    switchProfile("tcp://broker.emqx.io:1883", "", "");
                }
            });
        }).start();
    }

    private void switchProfile(String url, String username, String password) {
        txtBrokerUrl.setText(url);
        txtUsername.setText(username);
        txtPassword.setText(password);
        updateProfileButtons(url);
        if (mqttManager.isConnected()) {
            disconnectBroker();
        }
        connectBroker();
    }

    private void connectBroker() {
        String url = txtBrokerUrl.getText().trim();
        String user = txtUsername.getText().trim();
        String pass = new String(txtPassword.getPassword()).trim();
        String clientId = "JavaSwingClient_" + Long.toHexString(System.currentTimeMillis());

        updateProfileButtons(url);
        btnConnect.setEnabled(false);
        lblStatusBadge.setText(" ● ĐANG KẾT NỐI... ");
        lblStatusBadge.setPillColors(new Color(254, 243, 199), new Color(180, 83, 9), new Color(252, 211, 77));

        new Thread(() -> {
            try {
                mqttManager.connect(url, clientId, user, pass);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    btnConnect.setEnabled(true);
                    btnDisconnect.setEnabled(false);
                    lblStatusBadge.setText(" ● LỖI KẾT NỐI ");
                    lblStatusBadge.setPillColors(CRIMSON_BG, CRIMSON_DARK, new Color(252, 165, 165));
                    addLogRow("[HỆ THỐNG]", "Không kết nối được tới " + url + ": " + ex.getMessage());
                });
            }
        }).start();
    }

    private void disconnectBroker() {
        mqttManager.disconnect();
        onConnectionLost("Người dùng ngắt kết nối");
    }

    private void sendLedCommand(String cmd) {
        if (!mqttManager.isConnected()) {
            JOptionPane.showMessageDialog(this, "Vui lòng kết nối Broker trước khi gửi lệnh!",
                    "Chưa kết nối", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            mqttManager.publish(TOPIC_LED_CMD, cmd, 0);
            updateLedVisual(cmd);
            addLogRow(TOPIC_LED_CMD, cmd);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi gửi lệnh: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateLedVisual(String state) {
        SwingUtilities.invokeLater(() -> {
            ledStateOn = "ON".equalsIgnoreCase(state) || "1".equals(state);
            lblLedStatus.setText(ledStateOn ? "ĐÈN: BẬT (ON)" : "ĐÈN: TẮT (OFF)");
            lblLedStatus.setForeground(ledStateOn ? CRIMSON_DARK : TEXT_BLACK);
            pnlLedVisual.setLedOn(ledStateOn);
        });
    }

    private void addLogRow(String topic, String payload) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
            tableModel.insertRow(0, new Object[]{time, topic, payload});
            if (tableModel.getRowCount() > 100) {
                tableModel.setRowCount(100);
            }
        });
    }

    private void parseTelemetry(String payload) {
        try {
            double temp = Double.NaN;
            double hum = Double.NaN;

            Pattern pTemp = Pattern.compile("\"(?:temperature|temp)\"\\s*:\\s*([+-]?[0-9]*\\.?[0-9]+)");
            Matcher mTemp = pTemp.matcher(payload);
            if (mTemp.find()) {
                temp = Double.parseDouble(mTemp.group(1));
            }

            Pattern pHum = Pattern.compile("\"(?:humidity|hum)\"\\s*:\\s*([+-]?[0-9]*\\.?[0-9]+)");
            Matcher mHum = pHum.matcher(payload);
            if (mHum.find()) {
                hum = Double.parseDouble(mHum.group(1));
            }

            final double finalTemp = temp;
            final double finalHum = hum;

            SwingUtilities.invokeLater(() -> {
                if (!Double.isNaN(finalTemp)) {
                    boolean isHot = finalTemp > 35.0;
                    tempGauge.setValue(finalTemp, isHot);
                    if (isHot) {
                        pnlTempCard.setCardStyle(ALERT_BG, CRIMSON, CRIMSON);
                        lblTempAlert.setText(" CẢNH BÁO QUÁ NHIỆT (> 35°C) ");
                        lblTempAlert.setPillColors(CRIMSON_BG, CRIMSON_HOVER, CRIMSON);
                        if (chkAutoLed.isSelected() && !autoLedTriggered) {
                            autoLedTriggered = true;
                            if (!ledStateOn && mqttManager.isConnected()) {
                                sendLedCommand("ON");
                            }
                        }
                    } else {
                        pnlTempCard.setCardStyle(CARD_BG, BORDER_LIGHT, ORANGE_ACCENT);
                        lblTempAlert.setText(" AN TOÀN (<= 35°C) ");
                        lblTempAlert.setPillColors(EMERALD_BG, EMERALD_DARK, new Color(134, 239, 172));
                        if (chkAutoLed.isSelected() && autoLedTriggered) {
                            autoLedTriggered = false;
                            if (ledStateOn && mqttManager.isConnected()) {
                                sendLedCommand("OFF");
                            }
                        }
                    }
                }
                if (!Double.isNaN(finalHum)) {
                    humGauge.setValue(finalHum, false);
                }
            });
        } catch (Exception ex) {
            System.err.println("Lỗi parse telemetry: " + ex.getMessage());
        }
    }

    private void exportToCsv() {
        if (tableModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Không có dữ liệu nhật ký để xuất!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File("iot_telemetry_report.csv"));
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.println("ThoiGian,Topic,Payload");
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    String time = tableModel.getValueAt(i, 0).toString();
                    String topic = tableModel.getValueAt(i, 1).toString();
                    String payload = tableModel.getValueAt(i, 2).toString().replace("\"", "\"\"");
                    writer.println(String.format("\"%s\",\"%s\",\"%s\"", time, topic, payload));
                }
                JOptionPane.showMessageDialog(this, "Xuất file CSV thành công:\n" + file.getAbsolutePath(),
                        "Thành Công", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi khi lưu file CSV: " + ex.getMessage(),
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --- MqttListener Implementation ---
    @Override
    public void onConnected(String brokerUrl) {
        SwingUtilities.invokeLater(() -> {
            lblStatusBadge.setText(" ● ĐÃ KẾT NỐI ");
            lblStatusBadge.setPillColors(EMERALD_BG, EMERALD_DARK, new Color(134, 239, 172));
            btnConnect.setEnabled(false);
            btnDisconnect.setEnabled(true);
            addLogRow("[HỆ THỐNG]", "Đã kết nối thành công tới " + brokerUrl);
        });

        try {
            mqttManager.subscribe(TOPIC_TELEMETRY, 0);
            mqttManager.subscribe(TOPIC_LED_CMD, 0);
            mqttManager.subscribe(TOPIC_STATUS, 0);
            if (brokerUrl != null && !brokerUrl.contains("emqx.io")) {
                mqttManager.subscribe(TOPIC_LEGACY_TELEMETRY, 0);
            }
        } catch (Exception ex) {
            System.err.println("Lỗi đăng ký topic khi kết nối: " + ex.getMessage());
        }
    }

    @Override
    public void onConnectionLost(String reason) {
        SwingUtilities.invokeLater(() -> {
            lblStatusBadge.setText(" ● NGẮT KẾT NỐI ");
            lblStatusBadge.setPillColors(CRIMSON_BG, CRIMSON_DARK, new Color(252, 165, 165));
            btnConnect.setEnabled(true);
            btnDisconnect.setEnabled(false);
            addLogRow("[HỆ THỐNG]", "Mất kết nối: " + reason);
        });
    }

    @Override
    public void onMessage(String topic, String payload) {
        addLogRow(topic, payload);
        if (TOPIC_TELEMETRY.equals(topic) || TOPIC_LEGACY_TELEMETRY.equals(topic)) {
            parseTelemetry(payload);
        } else if (TOPIC_LED_CMD.equals(topic)) {
            updateLedVisual(payload);
        }
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
        private Color cardBg;
        private Color borderCol;
        private Color topAccent;

        CardPanel(Color bg, Color border, Color topAccent) {
            this.cardBg = bg;
            this.borderCol = border;
            this.topAccent = topAccent;
            setOpaque(false);
        }

        void setCardStyle(Color bg, Color border, Color accent) {
            this.cardBg = bg;
            this.borderCol = border;
            this.topAccent = accent;
            repaint();
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
            g2.setStroke(new BasicStroke(topAccent == CRIMSON && cardBg == ALERT_BG ? 2f : 1f));
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

    private static class GaugePanel extends JPanel {
        private final String unit;
        private final Color normalColor;
        private final Color alertColor;
        private final double threshold;
        private final boolean showThreshold;

        private double value = Double.NaN;
        private boolean alert = false;

        GaugePanel(String unit, Color normalColor, Color alertColor, double threshold, boolean showThreshold) {
            this.unit = unit;
            this.normalColor = normalColor;
            this.alertColor = alertColor;
            this.threshold = threshold;
            this.showThreshold = showThreshold;
            setOpaque(false);
            setPreferredSize(new Dimension(180, 150));
        }

        void setValue(double value, boolean alert) {
            this.value = value;
            this.alert = alert;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int diameter = Math.max(84, Math.min(w - 28, h - 10));
            int x = (w - diameter) / 2;
            int y = (h - diameter) / 2 + 8;

            // Track 240 deg
            g2.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(226, 232, 240));
            g2.draw(new Arc2D.Double(x, y, diameter, diameter, 210, -240, Arc2D.OPEN));

            // Threshold tick (35°C)
            double cx = x + diameter / 2.0;
            double cy = y + diameter / 2.0;
            double radius = diameter / 2.0;
            if (showThreshold && threshold > 0) {
                double thAngle = Math.toRadians(210.0 - (threshold / 100.0) * 240.0);
                int x1 = (int) Math.round(cx + (radius - 10) * Math.cos(thAngle));
                int y1 = (int) Math.round(cy - (radius - 10) * Math.sin(thAngle));
                int x2 = (int) Math.round(cx + (radius + 10) * Math.cos(thAngle));
                int y2 = (int) Math.round(cy - (radius + 10) * Math.sin(thAngle));
                g2.setStroke(new BasicStroke(2.5f));
                g2.setColor(CRIMSON);
                g2.drawLine(x1, y1, x2, y2);
            }

            // Active Arc + Endpoint Dot
            Color activeCol = alert ? alertColor : normalColor;
            if (!Double.isNaN(value)) {
                double pct = Math.max(0.0, Math.min(1.0, value / 100.0));
                double sweep = -240.0 * pct;
                g2.setStroke(new BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(activeCol);
                g2.draw(new Arc2D.Double(x, y, diameter, diameter, 210, sweep, Arc2D.OPEN));

                double endAngle = Math.toRadians(210.0 + sweep);
                int px = (int) Math.round(cx + radius * Math.cos(endAngle));
                int py = (int) Math.round(cy - radius * Math.sin(endAngle));
                g2.setColor(Color.WHITE);
                g2.fillOval(px - 6, py - 6, 12, 12);
                g2.setColor(activeCol);
                g2.setStroke(new BasicStroke(3f));
                g2.drawOval(px - 6, py - 6, 12, 12);
            }

            // Center Readout
            String text = Double.isNaN(value)
                    ? ("--.- " + unit)
                    : String.format(Locale.US, "%.1f %s", value, unit);
            g2.setFont(new Font("Segoe UI", Font.BOLD, Math.max(20, diameter / 5)));
            g2.setColor(alert ? CRIMSON_HOVER : TEXT_BLACK);
            FontMetrics fm = g2.getFontMetrics();
            int tx = (int) Math.round(cx - fm.stringWidth(text) / 2.0);
            int ty = (int) Math.round(cy + fm.getAscent() / 3.0);
            g2.drawString(text, tx, ty);

            g2.dispose();
        }
    }

    private static class LedBulbPanel extends JPanel {
        private boolean on = false;

        LedBulbPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(96, 96));
            setMaximumSize(new Dimension(96, 96));
        }

        void setLedOn(boolean on) {
            this.on = on;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int cx = getWidth() / 2;
            int cy = getHeight() / 2;

            if (on) {
                // Radial Crimson Halo
                RadialGradientPaint halo = new RadialGradientPaint(
                        cx, cy, 44f,
                        new float[]{0.0f, 0.55f, 1.0f},
                        new Color[]{new Color(239, 68, 68, 190), new Color(220, 38, 38, 85), new Color(220, 38, 38, 0)}
                );
                g2.setPaint(halo);
                g2.fillOval(cx - 44, cy - 44, 88, 88);
            }

            // Metallic Collar Ring
            g2.setColor(new Color(203, 213, 225));
            g2.fillOval(cx - 28, cy - 28, 56, 56);
            g2.setColor(new Color(148, 163, 184));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(cx - 28, cy - 28, 56, 56);

            // Inner LED Dome
            if (on) {
                RadialGradientPaint dome = new RadialGradientPaint(
                        cx - 6, cy - 6, 22f,
                        new float[]{0.0f, 0.5f, 1.0f},
                        new Color[]{new Color(254, 202, 202), new Color(239, 68, 68), new Color(185, 28, 28)}
                );
                g2.setPaint(dome);
            } else {
                g2.setColor(new Color(100, 116, 139));
            }
            g2.fillOval(cx - 21, cy - 21, 42, 42);

            // Specular highlight
            g2.setColor(new Color(255, 255, 255, on ? 160 : 75));
            g2.fillOval(cx - 12, cy - 13, 12, 8);

            g2.dispose();
        }
    }
}
