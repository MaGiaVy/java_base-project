package ui;

import server.FtpServer;
import server.FtpSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Giao diện Quản trị và Giám sát FTP Server (Multi-client Monitor & Audit Log)
 * Tông màu Đỏ Ruby (#DC2626)
 */
public class FtpServerPanel extends JPanel {

    private FtpServer ftpServer;
    private Path rootDir;

    // Thành phần điều khiển
    private JTextField txtPort, txtRootDir;
    private JButton btnStart, btnStop, btnBrowse;
    private JLabel lblServerStatus;

    // Bảng Session
    private JTable sessionTable;
    private DefaultTableModel sessionModel;

    // Server Console Log
    private JTextPane logPane;
    private StyledDocument logDoc;

    public FtpServerPanel(Path initialRootDir, int defaultPort) {
        this.rootDir = initialRootDir != null ? initialRootDir.toAbsolutePath().normalize() : Paths.get("FtpRoot").toAbsolutePath().normalize();

        setLayout(new BorderLayout(8, 8));
        setBackground(RedTheme.BG_MAIN);
        setBorder(new EmptyBorder(8, 8, 8, 8));

        initUI(defaultPort);
    }

    private void initUI(int defaultPort) {
        // 1. TOP: Control Bar
        add(createControlPanel(defaultPort), BorderLayout.NORTH);

        // 2. CENTER: SplitPane (Session Monitor Table + Server Log Console)
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, createSessionMonitorPanel(), createServerLogPanel());
        splitPane.setResizeWeight(0.4);
        splitPane.setDividerSize(6);
        splitPane.setBorder(null);

        add(splitPane, BorderLayout.CENTER);
    }

    private JPanel createControlPanel(int defaultPort) {
        JPanel p = RedTheme.createCardPanel();
        p.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 6));

        txtPort = RedTheme.createTextField(4);
        txtPort.setText(String.valueOf(defaultPort));

        txtRootDir = RedTheme.createTextField(24);
        txtRootDir.setText(rootDir.toString());
        txtRootDir.setEditable(false);

        btnBrowse = RedTheme.createSecondaryButton("Duyệt...");
        btnBrowse.addActionListener(e -> chooseRootDir());

        btnStart = RedTheme.createPrimaryButton("▶ Khởi Động Server");
        btnStop = RedTheme.createSecondaryButton("■ Dừng Server");
        btnStop.setEnabled(false);

        lblServerStatus = new JLabel("● Máy chủ đang tắt");
        lblServerStatus.setFont(RedTheme.FONT_BODY_BOLD);
        lblServerStatus.setForeground(RedTheme.TEXT_MUTED);

        p.add(new JLabel("Port:"));
        p.add(txtPort);
        p.add(new JLabel("FtpRoot:"));
        p.add(txtRootDir);
        p.add(btnBrowse);
        p.add(btnStart);
        p.add(btnStop);
        p.add(Box.createHorizontalStrut(10));
        p.add(lblServerStatus);

        btnStart.addActionListener(e -> startServer());
        btnStop.addActionListener(e -> stopServer());

        return p;
    }

    private JPanel createSessionMonitorPanel() {
        JPanel p = RedTheme.createCardPanel();
        p.setLayout(new BorderLayout(4, 4));
        p.setBorder(RedTheme.createSectionBorder("Danh Sách Client Đang Kết Nối (Multi-Client Sessions)"));

        String[] cols = {"Session ID", "Địa Chỉ Client", "Tài Khoản", "Thư Mục Hiện Tại", "Trạng Thái Hoạt Động"};
        sessionModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        sessionTable = new JTable(sessionModel);
        RedTheme.styleTable(sessionTable);

        p.add(new JScrollPane(sessionTable), BorderLayout.CENTER);
        return p;
    }

    private JPanel createServerLogPanel() {
        JPanel p = RedTheme.createCardPanel();
        p.setLayout(new BorderLayout(4, 4));
        p.setBorder(RedTheme.createSectionBorder("Nhật Ký Máy Chủ (Server Audit & Event Log)"));

        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setFont(RedTheme.FONT_MONO);
        logPane.setBackground(Color.WHITE);
        logDoc = logPane.getStyledDocument();

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        toolbar.setOpaque(false);
        JButton btnClear = RedTheme.createSecondaryButton("Xóa Log");
        btnClear.addActionListener(e -> logPane.setText(""));
        toolbar.add(btnClear);

        p.add(toolbar, BorderLayout.NORTH);
        p.add(new JScrollPane(logPane), BorderLayout.CENTER);
        return p;
    }

    private void chooseRootDir() {
        JFileChooser chooser = new JFileChooser(rootDir.toFile());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            rootDir = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
            txtRootDir.setText(rootDir.toString());
        }
    }

    public synchronized boolean startServerWithFallback(int startPort) {
        if (ftpServer != null && ftpServer.isRunning()) return true;

        int port = startPort;
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                ftpServer = new FtpServer(port, rootDir, this::appendLog, this::refreshSessionsTable);
                ftpServer.start();

                final int boundPort = port;
                SwingUtilities.invokeLater(() -> {
                    txtPort.setText(String.valueOf(boundPort));
                    lblServerStatus.setText("● Đang chạy tại Port " + boundPort + " - Sẵn sàng");
                    lblServerStatus.setForeground(RedTheme.SUCCESS);
                    btnStart.setEnabled(false);
                    btnStop.setEnabled(true);
                    txtPort.setEnabled(false);
                    btnBrowse.setEnabled(false);
                });
                return true;
            } catch (Exception ex) {
                appendLog("[SERVER CẢNH BÁO] Cổng " + port + " đang bận (" + ex.getMessage() + "), đang tự chuyển sang cổng tiếp theo...");
                port++;
            }
        }
        appendLog("[SERVER LỖI] Không thể tìm thấy cổng trống từ " + startPort + " đến " + (port - 1));
        return false;
    }

    public int getActualPort() {
        return (ftpServer != null && ftpServer.isRunning()) ? ftpServer.getPort() : Integer.parseInt(txtPort.getText().trim());
    }

    public synchronized void startServer() {
        if (ftpServer != null && ftpServer.isRunning()) return;

        int port;
        try {
            port = Integer.parseInt(txtPort.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Cổng Port không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            ftpServer = new FtpServer(port, rootDir, this::appendLog, this::refreshSessionsTable);
            ftpServer.start();

            lblServerStatus.setText("● Đang chạy tại Port " + port + " - Sẵn sàng");
            lblServerStatus.setForeground(RedTheme.SUCCESS);
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            txtPort.setEnabled(false);
            btnBrowse.setEnabled(false);
        } catch (Exception ex) {
            appendLog("[SERVER LỖI] Không thể khởi động: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, "Không thể mở Server tại Port " + port + ":\n" + ex.getMessage() + "\n(Mẹo: Nếu Port 21 hoặc 2121 bị chiếm, hãy đổi sang Port khác như 2122)", "Lỗi Khởi Động", JOptionPane.ERROR_MESSAGE);
        }
    }

    public synchronized void stopServer() {
        if (ftpServer != null && ftpServer.isRunning()) {
            ftpServer.stop();
        }
        lblServerStatus.setText("● Máy chủ đã dừng");
        lblServerStatus.setForeground(RedTheme.TEXT_MUTED);
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        txtPort.setEnabled(true);
        btnBrowse.setEnabled(true);
        sessionModel.setRowCount(0);
    }

    public void refreshSessionsTable() {
        SwingUtilities.invokeLater(() -> {
            sessionModel.setRowCount(0);
            if (ftpServer != null && ftpServer.isRunning()) {
                List<FtpSession> sessions = ftpServer.getActiveSessions();
                for (FtpSession s : sessions) {
                    sessionModel.addRow(new Object[]{
                            s.getSessionId(),
                            s.getClientAddress(),
                            s.isLoggedIn() ? s.getUsername() : "<Chưa login>",
                            s.getCurrentVirtualPath(),
                            s.getCurrentStatus()
                    });
                }
            }
        });
    }

    public void appendLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            try {
                Style style = logPane.addStyle("ServerLogStyle", null);
                if (msg.contains("[CONNECT]") || msg.contains("Sẵn sàng")) {
                    StyleConstants.setForeground(style, RedTheme.SUCCESS);
                    StyleConstants.setBold(style, true);
                } else if (msg.contains("[DISCONNECT]")) {
                    StyleConstants.setForeground(style, RedTheme.TEXT_MUTED);
                    StyleConstants.setBold(style, false);
                } else if (msg.contains("LỖI") || msg.contains("Access denied")) {
                    StyleConstants.setForeground(style, RedTheme.ERROR);
                    StyleConstants.setBold(style, true);
                } else if (msg.contains(">>>")) {
                    StyleConstants.setForeground(style, new Color(30, 58, 138));
                    StyleConstants.setBold(style, false);
                } else if (msg.contains("<<<")) {
                    StyleConstants.setForeground(style, new Color(15, 118, 110));
                    StyleConstants.setBold(style, false);
                } else {
                    StyleConstants.setForeground(style, RedTheme.TEXT_PRIMARY);
                    StyleConstants.setBold(style, false);
                }

                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                logDoc.insertString(logDoc.getLength(), "[" + timestamp + "] " + msg + "\n", style);
                logPane.setCaretPosition(logDoc.getLength());
            } catch (Exception ignored) {}
        });
    }
}
