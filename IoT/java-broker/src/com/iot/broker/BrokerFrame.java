package com.iot.broker;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Giao dien Swing quan ly Java MQTT Broker Server (TCP 1883 & WebSocket 9001).
 */
public class BrokerFrame extends JFrame implements MqttBrokerServer.BrokerListener {

    private static final Color BG_DARK = new Color(15, 23, 42);
    private static final Color CARD_BG = new Color(30, 41, 59);
    private static final Color BORDER_COLOR = new Color(51, 65, 85);
    private static final Color TEXT_PRIMARY = new Color(248, 250, 252);
    private static final Color TEXT_MUTED = new Color(148, 163, 184);

    private final MqttBrokerServer server;
    private final Random random = new Random();

    private JLabel lblStatusBadge;
    private JLabel lblPortsValue;
    private JLabel lblClientsValue;
    private JLabel lblMessagesValue;

    private JButton btnToggleBroker;
    private JButton btnSendTest;
    private JButton btnClearLogs;
    private JCheckBox chkRequireAuth;

    private DefaultTableModel clientsModel;
    private DefaultTableModel logsModel;

    public BrokerFrame(int tcpPort, int wsPort) {
        this.server = new MqttBrokerServer(tcpPort, wsPort, this);
        initUI();
        startBroker();
    }

    private void initUI() {
        setTitle("IoT Smart Monitoring — Java MQTT Broker Server (TCP "
                + server.getTcpPort() + " & WebSocket " + server.getWsPort() + ")");
        setSize(1020, 740);
        setMinimumSize(new Dimension(880, 620));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                server.stop();
                dispose();
                System.exit(0);
            }
        });

        JPanel mainPanel = new JPanel(new BorderLayout(14, 14));
        mainPanel.setBackground(BG_DARK);
        mainPanel.setBorder(new EmptyBorder(16, 16, 16, 16));

        // 1. HEADER + CONTROLS
        JPanel topContainer = new JPanel(new BorderLayout(10, 10));
        topContainer.setBackground(BG_DARK);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(CARD_BG);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(12, 16, 12, 16)));

        JLabel lblTitle = new JLabel("JAVA MQTT BROKER SERVER (PURE JDK — TCP & WEBSOCKET)");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 17));
        lblTitle.setForeground(TEXT_PRIMARY);

        lblStatusBadge = new JLabel(" ● DANG KHOI DONG... ");
        lblStatusBadge.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblStatusBadge.setForeground(new Color(250, 204, 21));

        headerPanel.add(lblTitle, BorderLayout.WEST);
        headerPanel.add(lblStatusBadge, BorderLayout.EAST);

        JPanel controlBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        controlBar.setBackground(CARD_BG);
        controlBar.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(4, 10, 4, 10)));

        btnToggleBroker = createStyledButton("Tat Broker", new Color(239, 68, 68));
        btnSendTest = createStyledButton("Ban Test Telemetry", new Color(59, 130, 246));
        btnClearLogs = createStyledButton("Xoa Nhat Ky", new Color(71, 85, 105));

        chkRequireAuth = new JCheckBox("Yeu cau mat khau (user1 / 123)", false);
        chkRequireAuth.setBackground(CARD_BG);
        chkRequireAuth.setForeground(TEXT_MUTED);
        chkRequireAuth.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chkRequireAuth.setFocusPainted(false);

        btnToggleBroker.addActionListener(e -> toggleBroker());
        btnSendTest.addActionListener(e -> sendSampleTelemetry());
        btnClearLogs.addActionListener(e -> logsModel.setRowCount(0));
        chkRequireAuth.addActionListener(e -> {
            boolean req = chkRequireAuth.isSelected();
            server.setRequireAuth(req);
            appendLogRow("--", "CONFIG", "JavaBroker", "-",
                    req ? "Da bat xac thuc user1/123" : "Cho phep ket noi an danh (allow_anonymous)");
        });

        controlBar.add(btnToggleBroker);
        controlBar.add(btnSendTest);
        controlBar.add(btnClearLogs);
        controlBar.add(chkRequireAuth);

        topContainer.add(headerPanel, BorderLayout.NORTH);
        topContainer.add(controlBar, BorderLayout.SOUTH);

        // 2. SUMMARY METRIC CARDS
        JPanel cardsGrid = new JPanel(new GridLayout(1, 3, 14, 14));
        cardsGrid.setBackground(BG_DARK);

        lblPortsValue = new JLabel("TCP: " + server.getTcpPort() + " | WS: " + server.getWsPort(), SwingConstants.CENTER);
        lblPortsValue.setFont(new Font("Segoe UI", Font.BOLD, 24));
        lblPortsValue.setForeground(new Color(56, 189, 248));

        lblClientsValue = new JLabel("0", SwingConstants.CENTER);
        lblClientsValue.setFont(new Font("Segoe UI", Font.BOLD, 34));
        lblClientsValue.setForeground(new Color(34, 197, 94));

        lblMessagesValue = new JLabel("0", SwingConstants.CENTER);
        lblMessagesValue.setFont(new Font("Segoe UI", Font.BOLD, 34));
        lblMessagesValue.setForeground(new Color(249, 115, 22));

        cardsGrid.add(createMetricCard("CONG LANG NGHE", lblPortsValue, "TCP (Java/ESP32) & WebSocket (Web)"));
        cardsGrid.add(createMetricCard("CLIENT DANG KET NOI", lblClientsValue, "Thiet bi & Ung dung dang Online"));
        cardsGrid.add(createMetricCard("BAN TIN DA XU LY", lblMessagesValue, "Tong goi PUBLISH da dieu phoi"));

        // 3. CENTER SPLIT PANEL: CLIENTS TABLE + LOGS TABLE
        clientsModel = new DefaultTableModel(
                new Object[]{"Client ID", "Giao Thuc", "Dia Chi IP", "Tai Khoan", "Topics Dang Ky"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable tblClients = createStyledTable(clientsModel);
        tblClients.getColumnModel().getColumn(0).setPreferredWidth(180);
        tblClients.getColumnModel().getColumn(1).setPreferredWidth(90);
        tblClients.getColumnModel().getColumn(2).setPreferredWidth(150);
        tblClients.getColumnModel().getColumn(3).setPreferredWidth(90);
        tblClients.getColumnModel().getColumn(4).setPreferredWidth(420);

        JPanel clientsCard = wrapTableCard("DANH SACH CLIENT DANG KET NOI", tblClients);

        logsModel = new DefaultTableModel(
                new Object[]{"Thoi Gian", "Loai Goi Tin", "Client ID", "Topic", "Noi Dung Payload"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable tblLogs = createStyledTable(logsModel);
        tblLogs.getColumnModel().getColumn(0).setPreferredWidth(85);
        tblLogs.getColumnModel().getColumn(1).setPreferredWidth(100);
        tblLogs.getColumnModel().getColumn(2).setPreferredWidth(160);
        tblLogs.getColumnModel().getColumn(3).setPreferredWidth(190);
        tblLogs.getColumnModel().getColumn(4).setPreferredWidth(400);

        JPanel logsCard = wrapTableCard("NHAT KY DIEU PHOI BAN TIN MQTT (REALTIME BROKER LOGS)", tblLogs);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, clientsCard, logsCard);
        splitPane.setDividerLocation(185);
        splitPane.setResizeWeight(0.3);
        splitPane.setBackground(BG_DARK);
        splitPane.setBorder(null);

        JPanel bodyPanel = new JPanel(new BorderLayout(14, 14));
        bodyPanel.setBackground(BG_DARK);
        bodyPanel.add(cardsGrid, BorderLayout.NORTH);
        bodyPanel.add(splitPane, BorderLayout.CENTER);

        mainPanel.add(topContainer, BorderLayout.NORTH);
        mainPanel.add(bodyPanel, BorderLayout.CENTER);
        setContentPane(mainPanel);
    }

    private JPanel createMetricCard(String title, JLabel valueLabel, String subtitle) {
        JPanel card = new JPanel(new BorderLayout(6, 6));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(12, 14, 12, 14)));

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
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBackground(CARD_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(8, 10, 10, 10)));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTitle.setForeground(TEXT_MUTED);

        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(BG_DARK);
        scroll.setBorder(new LineBorder(BORDER_COLOR, 1));

        panel.add(lblTitle, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JTable createStyledTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setBackground(BG_DARK);
        table.setForeground(TEXT_PRIMARY);
        table.setGridColor(BORDER_COLOR);
        table.setRowHeight(24);
        table.setFont(new Font("Consolas", Font.PLAIN, 12));
        table.getTableHeader().setBackground(CARD_BG);
        table.getTableHeader().setForeground(TEXT_PRIMARY);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        return table;
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(7, 14, 7, 14));
        return btn;
    }

    private void startBroker() {
        try {
            server.start();
            updateRunningState(true);
        } catch (IOException e) {
            updateRunningState(false);
            appendLogRow("--", "ERROR", "JavaBroker", "-",
                    "Khong mo duoc cong " + server.getTcpPort() + "/" + server.getWsPort() + ": " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Khong mo duoc cong " + server.getTcpPort() + " / " + server.getWsPort()
                            + ".\nHay kiem tra xem Mosquitto hoac ung dung khac co dang chiem cong khong.\nChi tiet: "
                            + e.getMessage(),
                    "Loi Khoi Dong Broker", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void toggleBroker() {
        if (server.isRunning()) {
            server.stop();
            updateRunningState(false);
        } else {
            startBroker();
        }
    }

    private void sendSampleTelemetry() {
        if (!server.isRunning()) {
            JOptionPane.showMessageDialog(this, "Broker dang tat! Hay bat Broker truoc.",
                    "Thong bao", JOptionPane.WARNING_MESSAGE);
            return;
        }
        double temp = 27.0 + random.nextDouble() * 6.0;
        double hum = 58.0 + random.nextDouble() * 10.0;
        String json = String.format(Locale.US, "{\"temperature\": %.1f, \"humidity\": %.1f}", temp, hum);
        server.publishServerMessage("esp32/sensor/dht22/data", json, 0, false);
        server.publishServerMessage("sensor/data", json, 0, false);
    }

    private void updateRunningState(boolean running) {
        if (running) {
            lblStatusBadge.setText(" ● DANG CHAY — TCP:" + server.getTcpPort() + " | WS:" + server.getWsPort() + " ");
            lblStatusBadge.setForeground(new Color(74, 222, 128));
            btnToggleBroker.setText("Tat Broker");
            btnToggleBroker.setBackground(new Color(239, 68, 68));
            btnSendTest.setEnabled(true);
        } else {
            lblStatusBadge.setText(" ● DA DUNG (Offline) ");
            lblStatusBadge.setForeground(new Color(248, 113, 113));
            btnToggleBroker.setText("Bat Broker");
            btnToggleBroker.setBackground(new Color(34, 197, 94));
            btnSendTest.setEnabled(false);
        }
    }

    private void appendLogRow(String time, String type, String clientId, String topic, String detail) {
        logsModel.insertRow(0, new Object[]{time, type, clientId, topic, detail});
        while (logsModel.getRowCount() > 500) {
            logsModel.removeRow(logsModel.getRowCount() - 1);
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
                        ? "(chua subscribe)"
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

    public static void main(String[] args) throws Exception {
        if (Arrays.asList(args).contains("--console")) {
            MqttBrokerServer.main(args);
            return;
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
