package com.iot;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Giao diện đồ họa Swing Desktop Client giám sát IoT & Điều khiển ESP32
 */
public class MainFrame extends JFrame implements MqttManager.MqttListener {

    private final String TOPIC_TELEMETRY = "esp32/sensor/dht22/data";
    private final String TOPIC_LEGACY_TELEMETRY = "sensor/data";
    private final String TOPIC_LED_CMD = "esp32/led/command";
    private final String TOPIC_STATUS = "esp32/status";

    private MqttManager mqttManager;

    // UI Elements - Settings
    private JTextField txtBrokerUrl;
    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JButton btnConnect;
    private JButton btnDisconnect;
    private JLabel lblStatusBadge;

    // UI Elements - Telemetry Cards
    private JPanel pnlTempCard;
    private JLabel lblTempValue;
    private JLabel lblTempAlert;
    private JPanel pnlHumCard;
    private JLabel lblHumValue;

    // UI Elements - LED Control
    private JPanel pnlLedVisual;
    private JLabel lblLedStatus;
    private JButton btnLedOn;
    private JButton btnLedOff;

    // UI Elements - Logs & Export
    private DefaultTableModel tableModel;
    private JTable logTable;
    private JButton btnExportCsv;
    private JButton btnClearLogs;

    public MainFrame() {
        mqttManager = new MqttManager(this);
        initUI();
    }

    private void initUI() {
        setTitle("IoT Smart Monitoring & Control System (Java Client)");
        setSize(960, 720);
        setMinimumSize(new Dimension(860, 600));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Dark theme colors
        Color bgDark = new Color(15, 23, 42);
        Color cardBg = new Color(30, 41, 59);
        Color borderColor = new Color(51, 65, 85);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout(15, 15));
        mainPanel.setBackground(bgDark);
        mainPanel.setBorder(new EmptyBorder(16, 16, 16, 16));

        // 1. TOP HEADER & CONNECTION PANEL
        JPanel topContainer = new JPanel(new BorderLayout(10, 10));
        topContainer.setBackground(bgDark);

        // Header Title
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(cardBg);
        headerPanel.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel lblTitle = new JLabel("⚡ IOT SMART MONITORING & CONTROL (JAVA CLIENT)");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(new Color(248, 250, 252));

        lblStatusBadge = new JLabel(" ● Chưa kết nối (Offline) ");
        lblStatusBadge.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblStatusBadge.setForeground(new Color(239, 68, 68));
        lblStatusBadge.setBorder(new LineBorder(new Color(239, 68, 68), 1, true));

        headerPanel.add(lblTitle, BorderLayout.WEST);
        headerPanel.add(lblStatusBadge, BorderLayout.EAST);

        // Settings Form
        JPanel settingsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        settingsPanel.setBackground(cardBg);
        settingsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(borderColor), "🌐 Cấu Hình Mosquitto Broker", 0, 0,
                new Font("Segoe UI", Font.BOLD, 12), new Color(148, 163, 184)));

        txtBrokerUrl = new JTextField("tcp://localhost:1883", 16);
        txtUsername = new JTextField("user1", 8);
        txtPassword = new JPasswordField("123", 8);

        btnConnect = new JButton("Kết Nối");
        btnConnect.setBackground(new Color(59, 130, 246));
        btnConnect.setForeground(Color.WHITE);
        btnConnect.setFocusPainted(false);

        btnDisconnect = new JButton("Ngắt Kết Nối");
        btnDisconnect.setBackground(new Color(239, 68, 68));
        btnDisconnect.setForeground(Color.WHITE);
        btnDisconnect.setEnabled(false);
        btnDisconnect.setFocusPainted(false);

        JButton btnLocal = new JButton("Local");
        JButton btnPublic = new JButton("Public EMQX");

        settingsPanel.add(new JLabel("URL:"));
        settingsPanel.add(txtBrokerUrl);
        settingsPanel.add(new JLabel("User:"));
        settingsPanel.add(txtUsername);
        settingsPanel.add(new JLabel("Pass:"));
        settingsPanel.add(txtPassword);
        settingsPanel.add(btnConnect);
        settingsPanel.add(btnDisconnect);
        settingsPanel.add(btnLocal);
        settingsPanel.add(btnPublic);

        topContainer.add(headerPanel, BorderLayout.NORTH);
        topContainer.add(settingsPanel, BorderLayout.SOUTH);

        // 2. CENTER PANEL (CARDS + CONTROL)
        JPanel centerGrid = new JPanel(new GridLayout(1, 3, 15, 15));
        centerGrid.setBackground(bgDark);

        // Temp Card
        pnlTempCard = createCardPanel("🌡️ NHIỆT ĐỘ (TEMPERATURE)", borderColor);
        lblTempValue = new JLabel("--.- °C", SwingConstants.CENTER);
        lblTempValue.setFont(new Font("Segoe UI", Font.BOLD, 42));
        lblTempValue.setForeground(new Color(249, 115, 22));

        lblTempAlert = new JLabel("Trạng thái: Bình thường", SwingConstants.CENTER);
        lblTempAlert.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTempAlert.setForeground(new Color(148, 163, 184));

        pnlTempCard.add(lblTempValue, BorderLayout.CENTER);
        pnlTempCard.add(lblTempAlert, BorderLayout.SOUTH);

        // Hum Card
        pnlHumCard = createCardPanel("💧 ĐỘ ẨM (HUMIDITY)", borderColor);
        lblHumValue = new JLabel("--.- %", SwingConstants.CENTER);
        lblHumValue.setFont(new Font("Segoe UI", Font.BOLD, 42));
        lblHumValue.setForeground(new Color(6, 182, 212));

        JLabel lblHumStandard = new JLabel("Dải chuẩn phòng: 40 - 70%", SwingConstants.CENTER);
        lblHumStandard.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblHumStandard.setForeground(new Color(148, 163, 184));

        pnlHumCard.add(lblHumValue, BorderLayout.CENTER);
        pnlHumCard.add(lblHumStandard, BorderLayout.SOUTH);

        // LED Control Card
        JPanel pnlControlCard = createCardPanel("💡 ĐIỀU KHIỂN ĐÈN LED (ESP32)", borderColor);
        JPanel ctrlCenter = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        ctrlCenter.setBackground(cardBg);

        pnlLedVisual = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (btnLedOn.isEnabled() && lblLedStatus.getText().contains("BẬT")) {
                    g2.setColor(new Color(239, 68, 68));
                    g2.fillOval(5, 5, 40, 40);
                } else {
                    g2.setColor(new Color(71, 85, 105));
                    g2.fillOval(5, 5, 40, 40);
                }
            }
        };
        pnlLedVisual.setPreferredSize(new Dimension(50, 50));
        pnlLedVisual.setBackground(cardBg);

        lblLedStatus = new JLabel("Đèn: TẮT (OFF)");
        lblLedStatus.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblLedStatus.setForeground(new Color(148, 163, 184));

        btnLedOn = new JButton("BẬT ĐÈN (ON)");
        btnLedOn.setBackground(new Color(22, 163, 74));
        btnLedOn.setForeground(Color.WHITE);
        btnLedOn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnLedOn.setFocusPainted(false);

        btnLedOff = new JButton("TẮT ĐÈN (OFF)");
        btnLedOff.setBackground(new Color(71, 85, 105));
        btnLedOff.setForeground(Color.WHITE);
        btnLedOff.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnLedOff.setFocusPainted(false);

        ctrlCenter.add(pnlLedVisual);
        ctrlCenter.add(lblLedStatus);

        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        btnPanel.setBackground(cardBg);
        btnPanel.add(btnLedOn);
        btnPanel.add(btnLedOff);

        pnlControlCard.add(ctrlCenter, BorderLayout.CENTER);
        pnlControlCard.add(btnPanel, BorderLayout.SOUTH);

        centerGrid.add(pnlTempCard);
        centerGrid.add(pnlHumCard);
        centerGrid.add(pnlControlCard);

        // 3. BOTTOM PANEL: TABLE LOGS & EXPORT CSV
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 8));
        bottomPanel.setBackground(cardBg);
        bottomPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(borderColor), "📜 Nhật Ký Dữ Liệu Thời Gian Thực", 0, 0,
                new Font("Segoe UI", Font.BOLD, 13), new Color(148, 163, 184)));

        tableModel = new DefaultTableModel(new String[]{"Thời Gian", "Topic", "Nội Dung Payload"}, 0);
        logTable = new JTable(tableModel);
        logTable.setBackground(new Color(15, 23, 42));
        logTable.setForeground(new Color(241, 245, 249));
        logTable.setRowHeight(24);
        logTable.setFont(new Font("Consolas", Font.PLAIN, 12));
        logTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));

        JScrollPane scrollPane = new JScrollPane(logTable);
        scrollPane.setPreferredSize(new Dimension(800, 180));

        JPanel bottomActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        bottomActions.setBackground(cardBg);

        btnExportCsv = new JButton("📥 Xuất Báo Cáo CSV (Export CSV)");
        btnExportCsv.setBackground(new Color(13, 148, 136));
        btnExportCsv.setForeground(Color.WHITE);
        btnExportCsv.setFocusPainted(false);

        btnClearLogs = new JButton("Xóa Nhật Ký");
        btnClearLogs.setBackground(new Color(71, 85, 105));
        btnClearLogs.setForeground(Color.WHITE);
        btnClearLogs.setFocusPainted(false);

        bottomActions.add(btnExportCsv);
        bottomActions.add(btnClearLogs);

        bottomPanel.add(scrollPane, BorderLayout.CENTER);
        bottomPanel.add(bottomActions, BorderLayout.SOUTH);

        // Assemble Main Frame
        mainPanel.add(topContainer, BorderLayout.NORTH);
        mainPanel.add(centerGrid, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // 4. ACTION LISTENERS
        btnConnect.addActionListener(e -> connectBroker());
        btnDisconnect.addActionListener(e -> disconnectBroker());

        btnLedOn.addActionListener(e -> sendLedCommand("ON"));
        btnLedOff.addActionListener(e -> sendLedCommand("OFF"));

        btnLocal.addActionListener(e -> {
            txtBrokerUrl.setText("tcp://localhost:1883");
            txtUsername.setText("user1");
            txtPassword.setText("123");
        });

        btnPublic.addActionListener(e -> {
            txtBrokerUrl.setText("tcp://broker.emqx.io:1883");
            txtUsername.setText("");
            txtPassword.setText("");
        });

        btnExportCsv.addActionListener(e -> exportToCsv());
        btnClearLogs.addActionListener(e -> tableModel.setRowCount(0));
    }

    private JPanel createCardPanel(String title, Color borderColor) {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBackground(new Color(30, 41, 59));
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(borderColor, 1, true),
                new EmptyBorder(12, 14, 12, 14)
        ));
        JLabel lblCardTitle = new JLabel(title);
        lblCardTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblCardTitle.setForeground(new Color(148, 163, 184));
        card.add(lblCardTitle, BorderLayout.NORTH);
        return card;
    }

    private void connectBroker() {
        String url = txtBrokerUrl.getText().trim();
        String user = txtUsername.getText().trim();
        String pass = new String(txtPassword.getPassword()).trim();
        String clientId = "JavaSwingClient_" + Long.toHexString(System.currentTimeMillis());

        lblStatusBadge.setText(" Đang kết nối... ");
        lblStatusBadge.setForeground(new Color(251, 146, 60));

        new Thread(() -> {
            try {
                mqttManager.connect(url, clientId, user, pass);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblStatusBadge.setText(" ● Lỗi: " + ex.getMessage());
                    lblStatusBadge.setForeground(new Color(239, 68, 68));
                    JOptionPane.showMessageDialog(this, "Không thể kết nối Broker: " + ex.getMessage(),
                            "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE);
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
            boolean isOn = "ON".equalsIgnoreCase(state) || "1".equals(state);
            lblLedStatus.setText(isOn ? "Đèn: BẬT (ON)" : "Đèn: TẮT (OFF)");
            lblLedStatus.setForeground(isOn ? new Color(74, 222, 128) : new Color(148, 163, 184));
            pnlLedVisual.repaint();
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

            Pattern pTemp = Pattern.compile("\"temperature\"\\s*:\\s*([+-]?[0-9]*\\.?[0-9]+)");
            Matcher mTemp = pTemp.matcher(payload);
            if (mTemp.find()) {
                temp = Double.parseDouble(mTemp.group(1));
            }

            Pattern pHum = Pattern.compile("\"humidity\"\\s*:\\s*([+-]?[0-9]*\\.?[0-9]+)");
            Matcher mHum = pHum.matcher(payload);
            if (mHum.find()) {
                hum = Double.parseDouble(mHum.group(1));
            }

            final double finalTemp = temp;
            final double finalHum = hum;

            SwingUtilities.invokeLater(() -> {
                if (!Double.isNaN(finalTemp)) {
                    lblTempValue.setText(String.format("%.1f °C", finalTemp));
                    if (finalTemp > 35.0) {
                        pnlTempCard.setBackground(new Color(153, 27, 27));
                        lblTempAlert.setText("⚠ CẢNH BÁO: NHIỆT ĐỘ QUÁ CAO (> 35°C)!");
                        lblTempAlert.setForeground(new Color(254, 202, 202));
                    } else {
                        pnlTempCard.setBackground(new Color(30, 41, 59));
                        lblTempAlert.setText("Trạng thái: An toàn (Bình thường)");
                        lblTempAlert.setForeground(new Color(148, 163, 184));
                    }
                }
                if (!Double.isNaN(finalHum)) {
                    lblHumValue.setText(String.format("%.1f %%", finalHum));
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
            lblStatusBadge.setText(" ● Đã kết nối Broker ");
            lblStatusBadge.setForeground(new Color(74, 222, 128));
            lblStatusBadge.setBorder(new LineBorder(new Color(74, 222, 128), 1, true));
            btnConnect.setEnabled(false);
            btnDisconnect.setEnabled(true);
            addLogRow("[HỆ THỐNG]", "Đã kết nối thành công tới " + brokerUrl);
        });

        // Tự động đăng ký lại topic mỗi khi kết nối hoặc tự động kết nối lại (Auto-reconnect)
        try {
            mqttManager.subscribe(TOPIC_TELEMETRY, 0);
            mqttManager.subscribe(TOPIC_LED_CMD, 0);
            mqttManager.subscribe(TOPIC_STATUS, 0);
        } catch (Exception ex) {
            System.err.println("Lỗi đăng ký topic khi kết nối: " + ex.getMessage());
        }
    }

    @Override
    public void onConnectionLost(String reason) {
        SwingUtilities.invokeLater(() -> {
            lblStatusBadge.setText(" ● Ngắt kết nối ");
            lblStatusBadge.setForeground(new Color(239, 68, 68));
            lblStatusBadge.setBorder(new LineBorder(new Color(239, 68, 68), 1, true));
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
}
