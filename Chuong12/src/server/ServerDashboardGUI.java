package server;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Giao diện đồ họa Quản lý Server (Dashboard GUI) theo tông màu Đỏ - Trắng
 * Cho phép bật/tắt độc lập hoặc đồng thời Server 1 (8080) và Server 2 (8081)
 * Áp dụng StyledButton để đảm bảo nút luôn hiển thị chữ rõ nét trên Windows.
 */
public class ServerDashboardGUI extends JFrame {

    private final Color PRIMARY_RED = new Color(220, 38, 38);      // #DC2626
    private final Color DARK_RED = new Color(153, 27, 27);         // #991B1B
    private final Color LIGHT_BG = new Color(248, 250, 252);       // #F8FAFC
    private final Color BORDER_COLOR = new Color(226, 232, 240);    // #E2E8F0
    private final Color TEXT_MAIN = new Color(15, 23, 42);         // #0F172A

    private HttpServerCore server1;
    private HttpServerCore server2;

    private JLabel lblStatus1;
    private JLabel lblStatus2;
    private StyledButton btnToggle1;
    private StyledButton btnToggle2;
    private JTextArea txtLogs;

    public ServerDashboardGUI() {
        setTitle("Bảng Điều Khiển HTTP Server - Đại học Sài Gòn (Chương 12)");
        setSize(960, 640);
        setMinimumSize(new Dimension(780, 520));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        initServers();
        initUI();

        // Tự động tắt server khi đóng cửa sổ
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopServer1();
                stopServer2();
            }
        });

        // Tự động bật cả hai Server khi khởi động để sẵn sàng hoạt động ngay lập tức
        SwingUtilities.invokeLater(() -> {
            if (!server1.isRunning()) toggleServer1();
            if (!server2.isRunning()) toggleServer2();
        });
    }

    private void initServers() {
        File webroot1 = new File("webroot1");
        File webroot2 = new File("webroot2");
        if (!webroot1.exists()) webroot1.mkdirs();
        if (!webroot2.exists()) webroot2.mkdirs();

        server1 = new HttpServerCore("Server 1 (IIS)", 8080, webroot1, this::appendLog);
        server2 = new HttpServerCore("Server 2 (App)", 8081, webroot2, this::appendLog);

        // Đăng ký cơ chế Kiểm duyệt Quản trị viên khi có yêu cầu nạp trang từ Web Crawler
        server1.setApprovalListener(this::promptAdminApproval);
        server2.setApprovalListener(this::promptAdminApproval);
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(LIGHT_BG);
        mainPanel.setBorder(new EmptyBorder(14, 14, 14, 14));

        // 1. Header Đỏ Trắng
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(PRIMARY_RED);
        headerPanel.setBorder(new EmptyBorder(15, 20, 15, 20));

        JLabel lblTitle = new JLabel("BẢNG ĐIỀU KHIỂN MÁY CHỦ HTTP (SERVER DASHBOARD)");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblTitle.setForeground(Color.WHITE);

        JLabel lblSub = new JLabel("Mô hình Client - Server Đa Máy Chủ | Chương 12: com.sun.net.httpserver");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSub.setForeground(new Color(254, 226, 226));

        headerPanel.add(lblTitle, BorderLayout.NORTH);
        headerPanel.add(lblSub, BorderLayout.SOUTH);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // 2. Center Panel: Controls + Logs
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setOpaque(false);

        // Vùng Server Cards
        JPanel cardsPanel = new JPanel(new GridLayout(1, 2, 14, 0));
        cardsPanel.setOpaque(false);

        // Card Server 1
        cardsPanel.add(createServerCard(
                "SERVER 1 (MÔ PHỎNG IIS)",
                "Cổng: 8080 | Webroot: webroot1/ (P1.HTM, P2.HTM)",
                lblStatus1 = new JLabel("● ĐANG DỪNG"),
                btnToggle1 = new StyledButton("Bật Server 1 (8080)", PRIMARY_RED, Color.WHITE),
                () -> toggleServer1(),
                "http://localhost:8080/P1.HTM"
        ));

        // Card Server 2
        cardsPanel.add(createServerCard(
                "SERVER 2 (MÁY CHỦ THỨ 2)",
                "Cổng: 8081 | Webroot: webroot2/ (P3.HTM, P4.HTM)",
                lblStatus2 = new JLabel("● ĐANG DỪNG"),
                btnToggle2 = new StyledButton("Bật Server 2 (8081)", DARK_RED, Color.WHITE),
                () -> toggleServer2(),
                "http://localhost:8081/P3.HTM"
        ));

        lblStatus1.setForeground(new Color(100, 116, 139));
        lblStatus2.setForeground(new Color(100, 116, 139));

        centerPanel.add(cardsPanel, BorderLayout.NORTH);

        // Vùng Log Area
        JPanel logContainer = new JPanel(new BorderLayout(6, 6));
        logContainer.setOpaque(false);
        logContainer.setBorder(new EmptyBorder(6, 0, 0, 0));

        JPanel logHeader = new JPanel(new BorderLayout());
        logHeader.setOpaque(false);
        JLabel lblLogTitle = new JLabel("NHẬT KÝ KẾT NỐI VÀ REQUEST THỜI GIAN THỰC (REALTIME LOGS):");
        lblLogTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblLogTitle.setForeground(PRIMARY_RED);

        JPanel logButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        logButtons.setOpaque(false);

        StyledButton btnStartAll = new StyledButton("Bật Cả Hai Server", new Color(22, 163, 74), Color.WHITE);
        btnStartAll.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnStartAll.addActionListener(e -> {
            if (!server1.isRunning()) toggleServer1();
            if (!server2.isRunning()) toggleServer2();
        });

        StyledButton btnStopAll = new StyledButton("Dừng Cả Hai", new Color(220, 38, 38), Color.WHITE);
        btnStopAll.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnStopAll.addActionListener(e -> {
            if (server1.isRunning()) toggleServer1();
            if (server2.isRunning()) toggleServer2();
        });

        StyledButton btnCreatePage = new StyledButton("➕ Tạo Trang Mới", new Color(13, 148, 136), Color.WHITE);
        btnCreatePage.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnCreatePage.setToolTipText("Tạo trang HTML mới lưu vào webroot1 và tự động đồng bộ sang Windows IIS");
        btnCreatePage.addActionListener(e -> showCreatePageDialog());

        StyledButton btnSyncIis = new StyledButton("🔄 Đồng Bộ Sang IIS", new Color(79, 70, 229), Color.WHITE);
        btnSyncIis.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnSyncIis.setToolTipText("Đồng bộ toàn bộ file từ webroot1 sang C:\\inetpub\\wwwroot của IIS");
        btnSyncIis.addActionListener(e -> syncAllToIis());

        StyledButton btnClearLog = new StyledButton("Xóa Log", Color.WHITE, TEXT_MAIN);
        btnClearLog.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnClearLog.addActionListener(e -> txtLogs.setText(""));

        StyledButton btnOpenCrawled = new StyledButton("📁 Web Đã Cào", Color.WHITE, new Color(37, 99, 235));
        btnOpenCrawled.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnOpenCrawled.setToolTipText("Mở thư mục chứa các trang web đã được cào về");
        btnOpenCrawled.addActionListener(e -> {
            try {
                File dir = new File("webroot1/crawled");
                if (!dir.exists()) dir.mkdirs();
                Desktop.getDesktop().open(dir);
            } catch (Exception ex) {
                appendLog("[LỖI] Không thể mở thư mục: " + ex.getMessage());
            }
        });

        logButtons.add(btnStartAll);
        logButtons.add(btnStopAll);
        logButtons.add(btnCreatePage);
        logButtons.add(btnSyncIis);
        logButtons.add(btnOpenCrawled);
        logButtons.add(btnClearLog);

        logHeader.add(lblLogTitle, BorderLayout.WEST);
        logHeader.add(logButtons, BorderLayout.EAST);

        txtLogs = new JTextArea();
        txtLogs.setEditable(false);
        txtLogs.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtLogs.setBackground(new Color(15, 23, 42)); // Nền tối hiện đại
        txtLogs.setForeground(new Color(74, 222, 128)); // Xanh terminal sáng rõ 100%
        txtLogs.setCaretColor(Color.WHITE);
        txtLogs.setMargin(new Insets(10, 12, 10, 12));

        JScrollPane scrollLog = new JScrollPane(txtLogs);
        scrollLog.setBorder(new LineBorder(BORDER_COLOR, 1));

        logContainer.add(logHeader, BorderLayout.NORTH);
        logContainer.add(scrollLog, BorderLayout.CENTER);

        centerPanel.add(logContainer, BorderLayout.CENTER);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        setContentPane(mainPanel);
    }

    private JPanel createServerCard(String title, String desc, JLabel statusLabel, StyledButton actionBtn, Runnable onAction, String sampleUrl) {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(14, 16, 14, 16)
        ));

        JPanel top = new JPanel(new GridLayout(2, 1, 2, 2));
        top.setOpaque(false);
        JLabel lblT = new JLabel(title);
        lblT.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblT.setForeground(PRIMARY_RED);
        JLabel lblD = new JLabel(desc);
        lblD.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblD.setForeground(new Color(100, 116, 139));
        top.add(lblT);
        top.add(lblD);

        JPanel mid = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 6));
        mid.setOpaque(false);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        mid.add(statusLabel);

        JPanel btm = new JPanel(new BorderLayout(6, 6));
        btm.setOpaque(false);
        actionBtn.addActionListener(e -> onAction.run());

        StyledButton btnOpenBrowser = new StyledButton("Mở Trình Duyệt Ngoài", Color.WHITE, TEXT_MAIN);
        btnOpenBrowser.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnOpenBrowser.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(new URI(sampleUrl));
            } catch (Exception ex) {
                appendLog("[LỖI] Không thể mở trình duyệt: " + ex.getMessage());
            }
        });

        btm.add(actionBtn, BorderLayout.CENTER);
        btm.add(btnOpenBrowser, BorderLayout.EAST);

        card.add(top, BorderLayout.NORTH);
        card.add(mid, BorderLayout.CENTER);
        card.add(btm, BorderLayout.SOUTH);
        return card;
    }

    private void toggleServer1() {
        if (!server1.isRunning()) {
            try {
                server1.start();
                lblStatus1.setText("● ĐANG HOẠT ĐỘNG (Port 8080)");
                lblStatus1.setForeground(new Color(22, 163, 74));
                btnToggle1.setText("Dừng Server 1");
                btnToggle1.setColors(new Color(100, 116, 139), Color.WHITE);
            } catch (Exception e) {
                appendLog("[LỖI] Khởi động Server 1 thất bại: " + e.getMessage());
            }
        } else {
            stopServer1();
        }
    }

    private void stopServer1() {
        if (server1.isRunning()) {
            server1.stop();
            lblStatus1.setText("● ĐANG DỪNG");
            lblStatus1.setForeground(new Color(100, 116, 139));
            btnToggle1.setText("Bật Server 1 (8080)");
            btnToggle1.setColors(PRIMARY_RED, Color.WHITE);
        }
    }

    private void toggleServer2() {
        if (!server2.isRunning()) {
            try {
                server2.start();
                lblStatus2.setText("● ĐANG HOẠT ĐỘNG (Port 8081)");
                lblStatus2.setForeground(new Color(22, 163, 74));
                btnToggle2.setText("Dừng Server 2");
                btnToggle2.setColors(new Color(100, 116, 139), Color.WHITE);
            } catch (Exception e) {
                appendLog("[LỖI] Khởi động Server 2 thất bại: " + e.getMessage());
            }
        } else {
            stopServer2();
        }
    }

    private void stopServer2() {
        if (server2.isRunning()) {
            server2.stop();
            lblStatus2.setText("● ĐANG DỪNG");
            lblStatus2.setForeground(new Color(100, 116, 139));
            btnToggle2.setText("Bật Server 2 (8081)");
            btnToggle2.setColors(DARK_RED, Color.WHITE);
        }
    }

    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            txtLogs.append(message + "\n");
            txtLogs.setCaretPosition(txtLogs.getDocument().getLength());
        });
    }

    /**
     * Hộp thoại kiểm duyệt Quản trị viên (Admin Approval)
     * Chặn thread xử lý của Server cho đến khi Quản trị viên đưa ra quyết định
     */
    private boolean promptAdminApproval(String srvName, String sourceUrl, String filename, int contentSize) {
        final boolean[] result = new boolean[]{false};
        try {
            SwingUtilities.invokeAndWait(() -> {
                toFront();
                requestFocus();

                String message = String.format(
                        "<html><body style='font-family: Segoe UI, sans-serif; width: 400px;'>"
                        + "<h3 style='color: #DC2626; margin: 0 0 10px 0;'>🔔 YÊU CẦU KIỂM DUYỆT TỪ WEB CRAWLER</h3>"
                        + "<p>Máy chủ <b>%s</b> vừa nhận được yêu cầu nạp trang web mới từ Client:</p>"
                        + "<table cellpadding='5' style='background: #F8FAFC; border: 1px solid #E2E8F0; width: 100%%; font-size: 12px;'>"
                        + "<tr><td><b>URL Nguồn:</b></td><td><span style='color: #2563EB;'>%s</span></td></tr>"
                        + "<tr><td><b>Tên Tệp:</b></td><td><b>%s</b></td></tr>"
                        + "<tr><td><b>Kích Thước:</b></td><td>%,d ký tự</td></tr>"
                        + "<tr><td><b>Đường Dẫn Server:</b></td><td><code>/crawled/%s</code></td></tr>"
                        + "</table>"
                        + "<p style='margin-top: 12px; font-weight: bold; color: #0F172A;'>Bạn có CHẤP NHẬN cho phép máy chủ nạp và phát hành trang này?</p>"
                        + "</body></html>",
                        srvName, sourceUrl, filename, contentSize, filename
                );

                Object[] options = {"✅ CHẤP NHẬN NẠP", "❌ TỪ CHỐI"};
                int choice = JOptionPane.showOptionDialog(
                        this,
                        message,
                        "Kiểm Duyệt Nạp Trang Web - " + srvName,
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        options,
                        options[0]
                );

                result[0] = (choice == JOptionPane.YES_OPTION);
            });
        } catch (Exception e) {
            appendLog("[LỖI] Lỗi hiển thị kiểm duyệt: " + e.getMessage());
            return true;
        }
        return result[0];
    }

    private void showCreatePageDialog() {
        JDialog dialog = new JDialog(this, "➕ Tạo Trang Web Mới & Đồng Bộ Sang IIS", true);
        dialog.setSize(600, 520);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));
        panel.setBackground(Color.WHITE);

        // Header
        JLabel lblHeader = new JLabel("Tạo Trang Web Mới (Lưu Vào webroot1 & IIS)");
        lblHeader.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblHeader.setForeground(PRIMARY_RED);

        // Fields
        JPanel fieldsPanel = new JPanel(new GridLayout(2, 2, 8, 8));
        fieldsPanel.setOpaque(false);

        JLabel lblName = new JLabel("Tên tệp (vd: P6.HTM, P7.HTM, about.html):");
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 12));
        JTextField txtName = new JTextField("P6.HTM");

        JLabel lblTitle = new JLabel("Tiêu đề trang (Title):");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        JTextField txtTitle = new JTextField("Trang Web Mới P6");

        fieldsPanel.add(lblName);
        fieldsPanel.add(txtName);
        fieldsPanel.add(lblTitle);
        fieldsPanel.add(txtTitle);

        JPanel contentPanel = new JPanel(new BorderLayout(6, 6));
        contentPanel.setOpaque(false);
        JLabel lblBody = new JLabel("Nội dung HTML (mã nguồn hoàn chỉnh):");
        lblBody.setFont(new Font("Segoe UI", Font.BOLD, 12));

        String defaultHtml = "<!DOCTYPE html>\n" +
                "<html lang=\"vi\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <title>Trang Web P6</title>\n" +
                "    <style>\n" +
                "        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f8fafc; padding: 40px; color: #0f172a; }\n" +
                "        .card { max-width: 600px; margin: auto; background: white; padding: 30px; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.08); border: 1px solid #e2e8f0; }\n" +
                "        h1 { color: #dc2626; margin-top: 0; }\n" +
                "        p { font-size: 15px; line-height: 1.6; }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"card\">\n" +
                "        <h1>Chào Mừng Đến Với Trang Web Mới!</h1>\n" +
                "        <p>Trang này được tạo trực tiếp từ <b>Server Dashboard</b> của hệ thống mạng phân tán.</p>\n" +
                "        <p>Trang đã được lưu đồng thời tại Java Server (Port 8080) và Microsoft IIS (Port 80).</p>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";

        JTextArea txtContent = new JTextArea(defaultHtml);
        txtContent.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtContent.setMargin(new Insets(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(txtContent);

        contentPanel.add(lblBody, BorderLayout.NORTH);
        contentPanel.add(scroll, BorderLayout.CENTER);

        JPanel topSection = new JPanel(new BorderLayout(10, 10));
        topSection.setOpaque(false);
        topSection.add(lblHeader, BorderLayout.NORTH);
        topSection.add(fieldsPanel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnPanel.setOpaque(false);

        StyledButton btnSave = new StyledButton("💾 Lưu & Đồng Bộ IIS", new Color(22, 163, 74), Color.WHITE);
        btnSave.addActionListener(e -> {
            String filename = txtName.getText().trim();
            if (filename.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Vui lòng nhập tên tệp!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!filename.toLowerCase().endsWith(".htm") && !filename.toLowerCase().endsWith(".html")) {
                filename += ".htm";
            }

            String content = txtContent.getText();
            try {
                // 1. Lưu vào webroot1
                File webroot1 = new File("webroot1");
                if (!webroot1.exists()) webroot1.mkdirs();
                File target = new File(webroot1, filename);
                Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);

                // 2. Lưu vào IIS nếu có
                File iisDir = HttpServerCore.getActiveIisDir();
                boolean iisSuccess = false;
                if (iisDir != null && iisDir.exists()) {
                    try {
                        File iisTarget = new File(iisDir, filename);
                        Files.writeString(iisTarget.toPath(), content, StandardCharsets.UTF_8);
                        iisSuccess = true;
                    } catch (Exception ex) {
                        appendLog("[LƯU Ý IIS] Chưa thể ghi vào " + iisDir.getAbsolutePath() + ": " + ex.getMessage());
                    }
                }

                appendLog(String.format("[TẠO TRANG] Đã tạo thành công trang '%s' tại webroot1%s",
                        filename, (iisSuccess ? " và đồng bộ sang IIS (" + iisDir.getAbsolutePath() + ")" : "")));

                String msg = "Đã tạo thành công trang: " + filename + "\n- Lưu tại: webroot1/" + filename;
                if (iisSuccess) {
                    msg += "\n- Đồng bộ sang IIS: " + iisDir.getAbsolutePath() + "\\" + filename + "\n\n👉 Bạn hãy mở IIS Manager (inetmgr), chọn Default Web Site và nhấn F5 để thấy ngay!";
                } else {
                    msg += "\n\n(Lưu ý: Nếu chưa thấy trên IIS, hãy chạy 'sync_to_iis.bat' để cấp quyền và đồng bộ toàn bộ file sang IIS)";
                }
                JOptionPane.showMessageDialog(dialog, msg, "Tạo Trang Thành Công", JOptionPane.INFORMATION_MESSAGE);
                dialog.dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Lỗi khi lưu file: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });

        StyledButton btnCancel = new StyledButton("Đóng", Color.WHITE, TEXT_MAIN);
        btnCancel.addActionListener(e -> dialog.dispose());

        btnPanel.add(btnSave);
        btnPanel.add(btnCancel);

        panel.add(topSection, BorderLayout.NORTH);
        panel.add(contentPanel, BorderLayout.CENTER);
        panel.add(btnPanel, BorderLayout.SOUTH);

        dialog.setContentPane(panel);
        dialog.setVisible(true);
    }

    private void syncAllToIis() {
        File iisDir = HttpServerCore.getActiveIisDir();
        if (iisDir == null || !iisDir.exists()) {
            JOptionPane.showMessageDialog(this,
                    "Không tìm thấy thư mục IIS trên máy này (đã kiểm tra C:\\inetpub\\wwwroot và C:\\inetpub\\site1).\nVui lòng kiểm tra lại cấu hình IIS.",
                    "Không tìm thấy IIS", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File webroot1 = new File("webroot1");
        if (!webroot1.exists()) {
            JOptionPane.showMessageDialog(this, "Thư mục webroot1 không tồn tại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        appendLog("[ĐỒNG BỘ IIS] Đang đồng bộ toàn bộ webroot1 sang " + iisDir.getAbsolutePath() + "...");

        try {
            int copied = copyDirectoryRecursive(webroot1, iisDir);
            appendLog(String.format("[ĐỒNG BỘ IIS] Đã sao chép thành công %,d tệp/thư mục sang %s", copied, iisDir.getAbsolutePath()));
            JOptionPane.showMessageDialog(this,
                    "Đồng bộ thành công " + copied + " tệp/thư mục sang IIS!\n" +
                    "Đích: " + iisDir.getAbsolutePath() + "\n\n" +
                    "👉 Hãy mở IIS Manager (inetmgr), chọn Default Web Site và bấm F5 để xem kết quả!",
                    "Đồng Bộ IIS Hoàn Tất", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            appendLog("[LỖI ĐỒNG BỘ IIS] " + ex.getMessage());
            int opt = JOptionPane.showConfirmDialog(this,
                    "Không thể ghi trực tiếp vào " + iisDir.getAbsolutePath() + " do quyền bảo mật của Windows:\n" +
                    ex.getMessage() + "\n\n" +
                    "Bạn có muốn mở file 'sync_to_iis.bat' để tự động cấp quyền và đồng bộ ngay không?",
                    "Cần Quyền Administrator", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (opt == JOptionPane.YES_OPTION) {
                try {
                    File bat = new File("sync_to_iis.bat");
                    if (bat.exists()) Desktop.getDesktop().open(bat);
                } catch (Exception openEx) {
                    appendLog("[LỖI] Không thể mở sync_to_iis.bat: " + openEx.getMessage());
                }
            }
        }
    }

    private int copyDirectoryRecursive(File source, File target) throws java.io.IOException {
        int count = 0;
        if (source.isDirectory()) {
            if (!target.exists()) target.mkdirs();
            File[] files = source.listFiles();
            if (files != null) {
                for (File file : files) {
                    count += copyDirectoryRecursive(file, new File(target, file.getName()));
                }
            }
        } else {
            Files.copy(source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            count++;
        }
        return count;
    }

    /**
     * Nút bấm tùy biến không phụ thuộc LookAndFeel của Windows
     * Luôn vẽ màu nền và màu chữ chính xác 100%, không bao giờ bị lỗi 'trắng tinh'!
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
            setBorder(new EmptyBorder(8, 16, 8, 16));
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
            ServerDashboardGUI gui = new ServerDashboardGUI();
            gui.setVisible(true);
        });
    }
}
