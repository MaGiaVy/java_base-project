import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BÀI THỰC HÀNH TỔNG HỢP: LẬP TRÌNH FTP CLIENT - FTP SERVER VỚI JAVA (TCP SOCKET)
 * Đáp ứng đầy đủ 10 tiêu chí đánh giá ở Slide 30 (Control Connection, Data Connection,
 * PASV/PORT, USER/PASS, PWD/CWD/CDUP, TYPE I/A, LIST, RETR, STOR, DELE, MKD, RMD, RNFR/RNTO,
 * Multi-client Threads, chống Path Traversal, giới hạn FTP Root, Timeout, kiểm tra quyền)
 * + TÍNH NĂNG MỚI:
 *   1. Tự động thêm số thứ tự (1), (2), (3)... khi trùng tên tập tin/thư mục (Upload, Download, MKD).
 *   2. Nháy đúp chuột (Double-click) vào tập tin ở bảng Local hoặc bảng FTP Server để xem trực tiếp nội dung.
 */
public class FtpClientServerApp extends JFrame {

    // Thư mục gốc cho FTP Server và FTP Client Local
    private static final Path FTP_ROOT_DIR = Paths.get("FtpRoot").toAbsolutePath().normalize();
    private static final Path CLIENT_LOCAL_DIR = Paths.get("FtpClientLocal").toAbsolutePath().normalize();

    private EmbeddedFtpServer ftpServer;
    private int activeServerPort = 2121;

    // Thành phần GUI cho Server Tab
    private JLabel lblServerStatus;
    private JTextField txtServerPort;
    private JButton btnStartServer, btnStopServer, btnOpenExtraClient;
    private JTextArea txtServerLog;
    private DefaultTableModel sessionTableModel;

    public FtpClientServerApp() {
        setTitle("Lập Trình Mạng Java - Hệ Thống FTP Server & FTP Client (Tự Động Đánh Số File Trùng & Xem File Khi Đúp Chuột)");
        setSize(1280, 820);
        setMinimumSize(new Dimension(1100, 720));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initSampleDirectoriesAndFiles();

        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.setFont(new Font("SansSerif", Font.BOLD, 13));

        // Tạo Panel Server trước để lấy khung Log
        JPanel serverPanel = createServerDashboardPanel();

        // Tự động khởi động FTP Server (thử Port 21 trước, nếu cần quyền root thì dùng 2121)
        startFtpServerAuto();

        // Tạo Panel Client chính
        FtpClientPanel clientPanel1 = new FtpClientPanel("Client #1", activeServerPort);

        mainTabs.addTab("1. Giao Diện FTP Client (Quản Lý, Xem Trực Tiếp & Truyền Tập Tin)", clientPanel1);
        mainTabs.addTab("2. Giao Diện FTP Server (Multi-Client & Nhật Ký Hệ Thống)", serverPanel);

        add(mainTabs, BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (ftpServer != null) {
                    ftpServer.stop();
                }
            }
        });
    }

    /**
     * Hàm tiện ích: Tự động tạo đường dẫn không bị trùng tên bằng cách thêm (1), (2), (3)...
     * Ví dụ: "report.pdf" -> "report (1).pdf" -> "report (2).pdf"
     *        "backup"     -> "backup (1)"     -> "backup (2)"
     */
    public static Path resolveUniquePath(Path directory, String originalName, boolean isDirectory) {
        Path candidate = directory.resolve(originalName).normalize();
        if (!Files.exists(candidate)) {
            return candidate;
        }

        String baseName = originalName;
        String extension = "";

        if (!isDirectory) {
            int dotIndex = originalName.lastIndexOf('.');
            if (dotIndex > 0 && dotIndex < originalName.length() - 1) {
                baseName = originalName.substring(0, dotIndex);
                extension = originalName.substring(dotIndex); // bao gồm dấu chấm "."
            }
        }

        // Nếu tên gốc đã có dạng "ten (k)" thì tách phần gốc để đánh số tiếp tự nhiên
        if (baseName.matches(".*\\s\\(\\d+\\)$")) {
            int openParen = baseName.lastIndexOf(" (");
            if (openParen > 0) {
                baseName = baseName.substring(0, openParen);
            }
        }

        int counter = 1;
        while (true) {
            String numberedName = baseName + " (" + counter + ")" + extension;
            Path nextCandidate = directory.resolve(numberedName).normalize();
            if (!Files.exists(nextCandidate)) {
                return nextCandidate;
            }
            counter++;
        }
    }

    /**
     * Hàm tiện ích: Sinh tên file không trùng dựa trên danh sách tên đã có (dùng khi kiểm tra trên Remote Table)
     */
    public static String resolveUniqueNameInSet(String originalName, Set<String> existingNames, boolean isDirectory) {
        if (!existingNames.contains(originalName)) {
            return originalName;
        }

        String baseName = originalName;
        String extension = "";

        if (!isDirectory) {
            int dotIndex = originalName.lastIndexOf('.');
            if (dotIndex > 0 && dotIndex < originalName.length() - 1) {
                baseName = originalName.substring(0, dotIndex);
                extension = originalName.substring(dotIndex);
            }
        }

        if (baseName.matches(".*\\s\\(\\d+\\)$")) {
            int openParen = baseName.lastIndexOf(" (");
            if (openParen > 0) {
                baseName = baseName.substring(0, openParen);
            }
        }

        int counter = 1;
        while (true) {
            String numberedName = baseName + " (" + counter + ")" + extension;
            if (!existingNames.contains(numberedName)) {
                return numberedName;
            }
            counter++;
        }
    }

    /**
     * Khởi tạo cấu trúc thư mục FtpRoot/ (documents, images, upload) và file mẫu giống Slide 18, 29, 30
     */
    private void initSampleDirectoriesAndFiles() {
        try {
            Files.createDirectories(FTP_ROOT_DIR.resolve("documents"));
            Files.createDirectories(FTP_ROOT_DIR.resolve("images"));
            Files.createDirectories(FTP_ROOT_DIR.resolve("upload"));
            Files.createDirectories(FTP_ROOT_DIR.resolve("data"));

            createSampleFileIfNotExists(FTP_ROOT_DIR.resolve("file1.txt"),
                    "Xin chào! Đây là nội dung tập tin mẫu file1.txt trên FTP Server.\r\n" +
                    "Môn học: Lập trình mạng với Java - Trường Đại học Sài Gòn (SGU).\r\n" +
                    "Bạn có thể nháy đúp chuột (Double-Click) vào bất kỳ file nào để xem trực tiếp nội dung!");
            createSampleFileIfNotExists(FTP_ROOT_DIR.resolve("report.pdf"),
                    "%PDF-1.4\n%Mẫu tập tin nhị phân report.pdf phục vụ kiểm thử lệnh RETR (Download) với chế độ Binary (TYPE I).\n" +
                    "Nội dung minh họa: Báo cáo kết quả thực hành giao thức FTP qua TCP Socket.");
            createSampleFileIfNotExists(FTP_ROOT_DIR.resolve("sensor.txt"),
                    "Time: 10:25:00 | Temp: 28.5C | Humidity: 70%\r\n" +
                    "Time: 10:26:00 | Temp: 28.7C | Humidity: 69%\r\n" +
                    "Time: 10:27:00 | Temp: 29.0C | Humidity: 68%");
            createSampleFileIfNotExists(FTP_ROOT_DIR.resolve("data/readme_data.txt"),
                    "Tập tin nằm trong thư mục /data dùng để kiểm thử lệnh CWD /data và CDUP.\r\n" +
                    "Nháy đúp chuột vào file này để xem nội dung trực tiếp từ FTP Server.");

            // Thư mục máy Client Local
            Files.createDirectories(CLIENT_LOCAL_DIR);
            createSampleFileIfNotExists(CLIENT_LOCAL_DIR.resolve("data.txt"),
                    "Dữ liệu mẫu từ máy Client dùng để kiểm thử lệnh STOR (Upload file lên FTP Server).\r\n" +
                    "Sinh viên thực hành: Lập trình FTP với Java.\r\n" +
                    "Nếu bạn bấm Upload nhiều lần, hệ thống sẽ tự động lưu thành data (1).txt, data (2).txt...");
            createSampleFileIfNotExists(CLIENT_LOCAL_DIR.resolve("upload_demo.txt"),
                    "Tập tin kiểm thử Upload (STOR) từ Client -> Server qua Data Connection.\r\n" +
                    "Nháy đúp chuột vào file ở bảng Local hoặc bảng Server để mở trình xem trực tiếp.");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void createSampleFileIfNotExists(Path path, String content) throws IOException {
        if (!Files.exists(path)) {
            Files.writeString(path, content, StandardCharsets.UTF_8);
        }
    }

    private void startFtpServerAuto() {
        int[] candidatePorts = {21, 2121};
        for (int port : candidatePorts) {
            try {
                ftpServer = new EmbeddedFtpServer(port, FTP_ROOT_DIR, this::appendServerLog, this::refreshSessionTable);
                ftpServer.start();
                activeServerPort = port;
                txtServerPort.setText(String.valueOf(port));
                lblServerStatus.setText("ĐANG CHẠY (Port " + port + " | FtpRoot: " + FTP_ROOT_DIR.getFileName() + "/)");
                lblServerStatus.setForeground(new Color(0, 140, 60));
                btnStartServer.setEnabled(false);
                btnStopServer.setEnabled(true);
                if (port == 2121) {
                    appendServerLog("[INFO] Cổng 21 yêu cầu quyền Root/Administrator trên HĐH này. Server đã tự động lắng nghe tại Port 2121.");
                }
                return;
            } catch (IOException ignored) {
            }
        }
        lblServerStatus.setText("LỖI KHỞI ĐỘNG SERVER");
        lblServerStatus.setForeground(Color.RED);
    }

    private JPanel createServerDashboardPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Thanh điều khiển Server trên cùng
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        topBar.setBorder(new TitledBorder("Điều Khiển FTP Server (TcpListener / ServerSocket)"));

        topBar.add(new JLabel("Control Port:"));
        txtServerPort = new JTextField("2121", 6);
        topBar.add(txtServerPort);

        btnStartServer = new JButton("Khởi động Server");
        btnStopServer = new JButton("Dừng Server");
        btnOpenExtraClient = new JButton("Mở thêm cửa sổ FTP Client thứ 2 (Test Multi-Client)");
        btnOpenExtraClient.setBackground(new Color(0, 102, 204));
        btnOpenExtraClient.setForeground(Color.WHITE);

        lblServerStatus = new JLabel("Đang khởi tạo...");
        lblServerStatus.setFont(new Font("SansSerif", Font.BOLD, 13));

        topBar.add(btnStartServer);
        topBar.add(btnStopServer);
        topBar.add(new JLabel(" | Trạng thái: "));
        topBar.add(lblServerStatus);
        topBar.add(Box.createHorizontalStrut(15));
        topBar.add(btnOpenExtraClient);

        btnStartServer.addActionListener(e -> {
            try {
                int p = Integer.parseInt(txtServerPort.getText().trim());
                ftpServer = new EmbeddedFtpServer(p, FTP_ROOT_DIR, this::appendServerLog, this::refreshSessionTable);
                ftpServer.start();
                activeServerPort = p;
                lblServerStatus.setText("ĐANG CHẠY (Port " + p + ")");
                lblServerStatus.setForeground(new Color(0, 140, 60));
                btnStartServer.setEnabled(false);
                btnStopServer.setEnabled(true);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Không thể mở Port: " + ex.getMessage() + "\nHãy thử Port 2121!",
                        "Lỗi mở cổng", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnStopServer.addActionListener(e -> {
            if (ftpServer != null) {
                ftpServer.stop();
                lblServerStatus.setText("ĐÃ DỪNG");
                lblServerStatus.setForeground(Color.RED);
                btnStartServer.setEnabled(true);
                btnStopServer.setEnabled(false);
            }
        });

        btnOpenExtraClient.addActionListener(e -> openAdditionalClientWindow());

        // Bảng danh sách phiên Client đang kết nối (Multi-client Sessions - Slide 28)
        String[] cols = {"Session ID", "Địa chỉ Client", "Username", "Đã Login", "Quyền hạn", "Thư mục hiện tại (PWD)", "Chế độ TYPE"};
        sessionTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable tblSessions = new JTable(sessionTableModel);
        tblSessions.setRowHeight(24);
        JScrollPane sessionScroll = new JScrollPane(tblSessions);
        sessionScroll.setBorder(new TitledBorder("Danh Sách Phiên Client Đang Kết Nối Đồng Thời (Multi-Client FtpSession - Slide 28)"));
        sessionScroll.setPreferredSize(new Dimension(100, 170));

        // Màn hình Log Server chuẩn Slide 30 mục 8
        txtServerLog = new JTextArea();
        txtServerLog.setEditable(false);
        txtServerLog.setBackground(new Color(20, 24, 33));
        txtServerLog.setForeground(new Color(0, 230, 118));
        txtServerLog.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane logScroll = new JScrollPane(txtServerLog);
        logScroll.setBorder(new TitledBorder("Log Hoạt Động Của FTP Server (Theo chuẩn Slide 30 - Mục 8)"));

        // Thông tin tài khoản tích hợp sẵn
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        infoPanel.add(new JLabel("<html><b>Tài khoản thử nghiệm trên Server:</b> "
                + "<font color='#0056b3'>[1] User: <b>student</b> / Pass: <b>123456</b> (Full quyền: Read, Write, Delete, Rename)</font> &nbsp;|&nbsp; "
                + "<font color='#b35900'>[2] User: <b>guest</b> / Pass: <b>123456</b> (Chỉ đọc - Dùng để test lỗi <b>550 Permission denied</b>)</font></html>"));

        JSplitPane centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sessionScroll, logScroll);
        centerSplit.setResizeWeight(0.3);

        panel.add(topBar, BorderLayout.NORTH);
        panel.add(centerSplit, BorderLayout.CENTER);
        panel.add(infoPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void openAdditionalClientWindow() {
        JFrame extraFrame = new JFrame("Cửa Sổ FTP Client Độc Lập (Kiểm Thử Nhiều Client Đồng Thời - Slide 28 & 30)");
        extraFrame.setSize(1150, 750);
        extraFrame.setLocationRelativeTo(this);
        FtpClientPanel extraPanel = new FtpClientPanel("Client #" + (int) (Math.random() * 90 + 10), activeServerPort);
        extraFrame.add(extraPanel);
        extraFrame.setVisible(true);
    }

    private void appendServerLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            txtServerLog.append("[" + time + "] " + msg + "\n");
            txtServerLog.setCaretPosition(txtServerLog.getDocument().getLength());
        });
    }

    private void refreshSessionTable(Collection<FtpSession> sessions) {
        SwingUtilities.invokeLater(() -> {
            sessionTableModel.setRowCount(0);
            for (FtpSession s : sessions) {
                sessionTableModel.addRow(new Object[]{
                        "Session #" + s.sessionId,
                        s.clientAddress,
                        s.username != null ? s.username : "(chưa gửi USER)",
                        s.loggedIn ? "230 Logged in" : "Chưa đăng nhập",
                        s.canWrite ? "Read / Write / Delete" : "Read-Only (Chỉ đọc)",
                        s.currentVirtualDir,
                        s.transferType.equals("I") ? "TYPE I (Binary)" : "TYPE A (ASCII)"
                });
            }
        });
    }

    // =========================================================================================
    // HỘP THOẠI XEM TRỰC TIẾP TẬP TIN KHI NHÁY ĐÚP CHUỘT (DIRECT FILE PREVIEW DIALOG)
    // =========================================================================================
    private static void showFilePreviewDialog(Component parent, String titlePrefix, String fileName,
                                              Path filePath, boolean allowEditAndSave, Runnable onSavedCallback) {
        try {
            byte[] rawBytes = Files.readAllBytes(filePath);
            long fileSize = rawBytes.length;

            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent),
                    titlePrefix + " — " + fileName + " (" + fileSize + " bytes)", Dialog.ModalityType.MODELESS);
            dialog.setSize(820, 600);
            dialog.setLocationRelativeTo(parent);
            dialog.setLayout(new BorderLayout(8, 8));

            // Header thông tin tập tin
            JPanel headerPanel = new JPanel(new BorderLayout(6, 4));
            headerPanel.setBorder(new EmptyBorder(10, 12, 4, 12));
            String modifiedTime = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                    .format(new Date(Files.getLastModifiedTime(filePath).toMillis()));
            JLabel lblInfo = new JLabel(String.format(
                    "<html><b>Tên tập tin:</b> <font color='#0056b3'>%s</font> &nbsp;|&nbsp; " +
                    "<b>Kích thước:</b> %,d bytes &nbsp;|&nbsp; <b>Cập nhật:</b> %s<br>" +
                    "<b>Đường dẫn:</b> <code>%s</code></html>",
                    fileName, fileSize, modifiedTime, filePath.toAbsolutePath()));
            headerPanel.add(lblInfo, BorderLayout.CENTER);
            dialog.add(headerPanel, BorderLayout.NORTH);

            String lowerName = fileName.toLowerCase();
            boolean isImage = lowerName.endsWith(".png") || lowerName.endsWith(".jpg")
                    || lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif") || lowerName.endsWith(".bmp");

            JTabbedPane previewTabs = new JTabbedPane();
            JTextArea txtEditor = new JTextArea();

            // 1. Nếu là file ảnh -> Hiển thị tab Xem Hình Ảnh trước
            if (isImage) {
                ImageIcon icon = new ImageIcon(rawBytes);
                JLabel imgLabel = new JLabel(icon);
                imgLabel.setHorizontalAlignment(SwingConstants.CENTER);
                previewTabs.addTab("🖼 Xem Hình Ảnh Trực Tiếp", new JScrollPane(imgLabel));
            }

            // 2. Tab Nội dung Văn bản (UTF-8)
            String textContent = new String(rawBytes, StandardCharsets.UTF_8);
            txtEditor.setText(textContent);
            txtEditor.setCaretPosition(0);
            txtEditor.setFont(new Font("Monospaced", Font.PLAIN, 13));
            txtEditor.setEditable(allowEditAndSave && !isImage);
            if (!txtEditor.isEditable()) {
                txtEditor.setBackground(new Color(248, 249, 250));
            }

            JScrollPane textScroll = new JScrollPane(txtEditor);
            previewTabs.addTab(allowEditAndSave && !isImage
                    ? "📄 Nội Dung Văn Bản (UTF-8 - Có thể sửa & lưu)"
                    : "📄 Nội Dung Văn Bản (UTF-8)", textScroll);

            // 3. Tab Hex / Binary Inspector (Rất hữu ích khi xem file nhị phân như PDF, EXE, ZIP ở Slide 19)
            JTextArea txtHex = new JTextArea(buildHexDump(rawBytes, 4096));
            txtHex.setEditable(false);
            txtHex.setCaretPosition(0);
            txtHex.setFont(new Font("Monospaced", Font.PLAIN, 12));
            txtHex.setBackground(new Color(22, 27, 34));
            txtHex.setForeground(new Color(121, 192, 255));
            previewTabs.addTab("🔢 Xem Mã Nhị Phân / Hex (Binary Inspector)", new JScrollPane(txtHex));

            JPanel centerWrapper = new JPanel(new BorderLayout());
            centerWrapper.setBorder(new EmptyBorder(0, 12, 0, 12));
            centerWrapper.add(previewTabs, BorderLayout.CENTER);
            dialog.add(centerWrapper, BorderLayout.CENTER);

            // Thanh công cụ bên dưới
            JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
            if (allowEditAndSave && !isImage) {
                JButton btnSave = new JButton("💾 Lưu Thay Đổi Vào File");
                btnSave.setBackground(new Color(40, 167, 69));
                btnSave.setForeground(Color.WHITE);
                btnSave.addActionListener(e -> {
                    try {
                        Files.writeString(filePath, txtEditor.getText(), StandardCharsets.UTF_8);
                        if (onSavedCallback != null) onSavedCallback.run();
                        JOptionPane.showMessageDialog(dialog, "Đã lưu nội dung tập tin thành công!",
                                "Lưu thành công", JOptionPane.INFORMATION_MESSAGE);
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(dialog, "Lỗi khi lưu file: " + ex.getMessage(),
                                "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                });
                footerPanel.add(btnSave);
            }

            JButton btnOpenOs = new JButton("🖥 Mở Bằng Ứng Dụng Mặc Định Của HĐH");
            btnOpenOs.addActionListener(e -> {
                try {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(filePath.toFile());
                    } else {
                        JOptionPane.showMessageDialog(dialog, "Hệ điều hành không hỗ trợ Desktop.open()!");
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog, "Không thể mở bằng ứng dụng ngoài: " + ex.getMessage(),
                            "Thông báo", JOptionPane.WARNING_MESSAGE);
                }
            });

            JButton btnClose = new JButton("Đóng cửa sổ");
            btnClose.addActionListener(e -> dialog.dispose());

            footerPanel.add(btnOpenOs);
            footerPanel.add(btnClose);
            dialog.add(footerPanel, BorderLayout.SOUTH);

            dialog.setVisible(true);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parent, "Không thể đọc tập tin để xem trực tiếp: " + ex.getMessage(),
                    "Lỗi đọc file", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String buildHexDump(byte[] data, int maxBytes) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(data.length, maxBytes);
        sb.append(String.format("Hiển thị %d / %d bytes đầu tiên ở định dạng Hexadecimal:\n", limit, data.length));
        sb.append("Offset    00 01 02 03 04 05 06 07  08 09 0A 0B 0C 0D 0E 0F   | ASCII\n");
        sb.append("------------------------------------------------------------------------------\n");
        for (int i = 0; i < limit; i += 16) {
            sb.append(String.format("%08X  ", i));
            StringBuilder ascii = new StringBuilder();
            for (int j = 0; j < 16; j++) {
                if (i + j < limit) {
                    int b = data[i + j] & 0xFF;
                    sb.append(String.format("%02X ", b));
                    ascii.append((b >= 32 && b <= 126) ? (char) b : '.');
                } else {
                    sb.append("   ");
                }
                if (j == 7) sb.append(" ");
            }
            sb.append("  | ").append(ascii).append("\n");
        }
        if (data.length > maxBytes) {
            sb.append("\n... (Còn ").append(data.length - maxBytes).append(" bytes nữa) ...");
        }
        return sb.toString();
    }

    // =========================================================================================
    // PHẦN 1: GIAO DIỆN & LOGIC FTP CLIENT (SLIDE 14 -> 25 & 30)
    // =========================================================================================
    private class FtpClientPanel extends JPanel {
        private final String clientName;
        private RawFtpClient ftpClient;

        private JTextField txtHost, txtPort, txtUser, txtPass, txtRemotePwd, txtCustomCmd;
        private JComboBox<String> cbMode, cbType;
        private JButton btnConnect, btnLogin, btnQuit;
        private JButton btnPwd, btnCdup, btnList, btnMkd, btnRmd, btnRename, btnDele;
        private JButton btnUpload, btnDownload, btnRunTestSuite;
        private JLabel lblLastReplyBadge;

        private DefaultTableModel localTableModel, remoteTableModel;
        private JTable tblLocal, tblRemote;
        private JTextPane txtProtocolLog;
        private Path currentLocalDir = CLIENT_LOCAL_DIR;
        private JTextField txtLocalPath;

        public FtpClientPanel(String clientName, int defaultPort) {
            this.clientName = clientName;
            setLayout(new BorderLayout(8, 8));
            setBorder(new EmptyBorder(8, 8, 8, 8));

            add(createConnectionBar(defaultPort), BorderLayout.NORTH);
            add(createMainSplitWorkspace(), BorderLayout.CENTER);
            add(createBottomRawCommandBar(), BorderLayout.SOUTH);

            refreshLocalFileTable();
            updateActionButtonsState(false, false);
        }

        private JPanel createConnectionBar(int defaultPort) {
            JPanel wrapper = new JPanel(new BorderLayout(5, 5));
            JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
            bar.setBorder(new TitledBorder("Thiết Lập Phiên Kết Nối FTP (" + clientName + " - Control Connection TCP)"));

            bar.add(new JLabel("Host:"));
            txtHost = new JTextField("127.0.0.1", 9);
            bar.add(txtHost);

            bar.add(new JLabel("Port:"));
            txtPort = new JTextField(String.valueOf(defaultPort), 5);
            bar.add(txtPort);

            bar.add(new JLabel("Username:"));
            txtUser = new JTextField("student", 8);
            bar.add(txtUser);

            bar.add(new JLabel("Password:"));
            txtPass = new JPasswordField("123456", 7);
            bar.add(txtPass);

            bar.add(new JLabel("Data Mode:"));
            cbMode = new JComboBox<>(new String[]{"PASV (Passive Mode)", "PORT (Active Mode)"});
            bar.add(cbMode);

            bar.add(new JLabel("Type:"));
            cbType = new JComboBox<>(new String[]{"TYPE I (Binary)", "TYPE A (ASCII)"});
            bar.add(cbType);

            btnConnect = new JButton("1. Connect");
            btnLogin = new JButton("2. Login (USER/PASS)");
            btnQuit = new JButton("5. QUIT");

            btnConnect.setBackground(new Color(0, 123, 255));
            btnConnect.setForeground(Color.WHITE);
            btnLogin.setBackground(new Color(40, 167, 69));
            btnLogin.setForeground(Color.WHITE);
            btnQuit.setBackground(new Color(220, 53, 69));
            btnQuit.setForeground(Color.WHITE);

            bar.add(btnConnect);
            bar.add(btnLogin);
            bar.add(btnQuit);

            // Dòng trạng thái Reply Code & nút kịch bản kiểm thử tự động
            JPanel statusRow = new JPanel(new BorderLayout(10, 4));
            statusRow.setBorder(new EmptyBorder(2, 6, 2, 6));

            lblLastReplyBadge = new JLabel(" Trạng thái: Chưa kết nối (Mẹo: Nháy đúp chuột vào File bất kỳ để xem trực tiếp!) ");
            lblLastReplyBadge.setOpaque(true);
            lblLastReplyBadge.setBackground(new Color(230, 230, 230));
            lblLastReplyBadge.setFont(new Font("SansSerif", Font.BOLD, 12));

            btnRunTestSuite = new JButton("Chạy Tự Động 8 Kịch Bản Kiểm Thử Bắt Buộc (Slide 30 - Mục 6)");
            btnRunTestSuite.setBackground(new Color(111, 66, 193));
            btnRunTestSuite.setForeground(Color.WHITE);

            statusRow.add(lblLastReplyBadge, BorderLayout.CENTER);
            statusRow.add(btnRunTestSuite, BorderLayout.EAST);

            wrapper.add(bar, BorderLayout.NORTH);
            wrapper.add(statusRow, BorderLayout.SOUTH);

            // Sự kiện các nút Kết nối / Đăng nhập / Thoát
            btnConnect.addActionListener(e -> doConnect());
            btnLogin.addActionListener(e -> doLogin());
            btnQuit.addActionListener(e -> doQuit());
            cbType.addActionListener(e -> {
                if (ftpClient != null && ftpClient.isLoggedIn()) {
                    sendTypeCommand();
                }
            });
            btnRunTestSuite.addActionListener(e -> runMandatoryTestScenarios());

            return wrapper;
        }

        private JSplitPane createMainSplitWorkspace() {
            // 1. Khung bên trái: Máy Client Local
            JPanel localPanel = new JPanel(new BorderLayout(5, 5));
            localPanel.setBorder(new TitledBorder("Tập Tin Trên Máy Client (Local — Nháy đúp chuột vào File để xem trực tiếp / Thư mục để mở)"));

            JPanel localTop = new JPanel(new BorderLayout(5, 5));
            txtLocalPath = new JTextField(currentLocalDir.toString());
            txtLocalPath.setEditable(false);
            JButton btnLocalUp = new JButton("⬆ Lên");
            JButton btnChooseLocalFolder = new JButton("Chọn thư mục...");
            JButton btnRefreshLocal = new JButton("Làm mới");
            JPanel localBtnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            localBtnGroup.add(btnLocalUp);
            localBtnGroup.add(btnChooseLocalFolder);
            localBtnGroup.add(btnRefreshLocal);

            localTop.add(new JLabel("Thư mục Local: "), BorderLayout.WEST);
            localTop.add(txtLocalPath, BorderLayout.CENTER);
            localTop.add(localBtnGroup, BorderLayout.EAST);

            localTableModel = new DefaultTableModel(new String[]{"Tên tập tin", "Loại", "Kích thước (Bytes)"}, 0) {
                @Override
                public boolean isCellEditable(int row, int col) {
                    return false;
                }
            };
            tblLocal = new JTable(localTableModel);
            tblLocal.setRowHeight(24);

            // Tô màu thư mục ở bảng Local
            tblLocal.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value,
                                                               boolean isSelected, boolean hasFocus, int row, int column) {
                    Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    String type = String.valueOf(table.getValueAt(row, 1));
                    if (!isSelected) {
                        if ("DIRECTORY".equals(type)) {
                            c.setForeground(new Color(0, 86, 179));
                            c.setFont(c.getFont().deriveFont(Font.BOLD));
                        } else {
                            c.setForeground(Color.BLACK);
                            c.setFont(c.getFont().deriveFont(Font.PLAIN));
                        }
                    }
                    return c;
                }
            });

            // TÍNH NĂNG MỚI: Nháy đúp chuột vào bảng Local để mở thư mục hoặc XEM TRỰC TIẾP FILE
            tblLocal.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        int row = tblLocal.getSelectedRow();
                        if (row >= 0) {
                            String name = String.valueOf(localTableModel.getValueAt(row, 0));
                            String type = String.valueOf(localTableModel.getValueAt(row, 1));
                            Path target = currentLocalDir.resolve(name).normalize();
                            if ("DIRECTORY".equals(type)) {
                                if (Files.isDirectory(target)) {
                                    currentLocalDir = target;
                                    txtLocalPath.setText(currentLocalDir.toString());
                                    refreshLocalFileTable();
                                }
                            } else if ("FILE".equals(type) && Files.exists(target)) {
                                appendClientLog("[XEM FILE LOCAL] Mở trình xem trực tiếp cho file: " + target.getFileName());
                                showFilePreviewDialog(FtpClientPanel.this,
                                        "Xem File Trên Máy Client (Local)",
                                        name, target, true, FtpClientPanel.this::refreshLocalFileTable);
                            }
                        }
                    }
                }
            });

            btnUpload = new JButton("⬆ UPLOAD LÊN SERVER (Lệnh STOR — Tự động thêm số nếu trùng tên)");
            btnUpload.setFont(new Font("SansSerif", Font.BOLD, 12));
            btnUpload.setBackground(new Color(0, 123, 255));
            btnUpload.setForeground(Color.WHITE);

            localPanel.add(localTop, BorderLayout.NORTH);
            localPanel.add(new JScrollPane(tblLocal), BorderLayout.CENTER);
            localPanel.add(btnUpload, BorderLayout.SOUTH);

            btnLocalUp.addActionListener(e -> {
                if (currentLocalDir.getParent() != null) {
                    currentLocalDir = currentLocalDir.getParent();
                    txtLocalPath.setText(currentLocalDir.toString());
                    refreshLocalFileTable();
                }
            });
            btnRefreshLocal.addActionListener(e -> refreshLocalFileTable());
            btnChooseLocalFolder.addActionListener(e -> {
                JFileChooser chooser = new JFileChooser(currentLocalDir.toFile());
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    currentLocalDir = chooser.getSelectedFile().toPath();
                    txtLocalPath.setText(currentLocalDir.toString());
                    refreshLocalFileTable();
                }
            });
            btnUpload.addActionListener(e -> doUploadSelectedFile());

            // 2. Khung bên phải: Máy chủ FTP Server (Remote)
            JPanel remotePanel = new JPanel(new BorderLayout(5, 5));
            remotePanel.setBorder(new TitledBorder("Tập Tin Trên FTP Server (Remote — Nháy đúp chuột vào File để xem trực tiếp / Thư mục để CWD)"));

            txtRemotePwd = new JTextField("/");
            txtRemotePwd.setEditable(false);
            txtRemotePwd.setFont(new Font("Monospaced", Font.BOLD, 13));

            JPanel navToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            btnPwd = new JButton("PWD");
            btnCdup = new JButton("⬆ CDUP (Thư mục cha)");
            btnList = new JButton("🔄 LIST (Làm mới)");
            btnMkd = new JButton("➕ MKD (Tạo folder)");
            btnRmd = new JButton("🗑 RMD (Xóa folder)");
            btnRename = new JButton("✏ RNFR/RNTO (Đổi tên)");
            btnDele = new JButton("❌ DELE (Xóa file)");

            navToolbar.add(btnPwd);
            navToolbar.add(btnCdup);
            navToolbar.add(btnList);
            navToolbar.add(btnMkd);
            navToolbar.add(btnRmd);
            navToolbar.add(btnRename);
            navToolbar.add(btnDele);

            JPanel remoteHeader = new JPanel(new BorderLayout(4, 4));
            JPanel pwdRow = new JPanel(new BorderLayout(4, 4));
            pwdRow.add(new JLabel("Thư mục Server (PWD): "), BorderLayout.WEST);
            pwdRow.add(txtRemotePwd, BorderLayout.CENTER);
            remoteHeader.add(pwdRow, BorderLayout.NORTH);
            remoteHeader.add(navToolbar, BorderLayout.SOUTH);

            remoteTableModel = new DefaultTableModel(
                    new String[]{"Quyền (Permissions)", "Loại", "Tên File / Thư Mục", "Kích thước (Bytes)", "Ngày sửa đổi"}, 0) {
                @Override
                public boolean isCellEditable(int row, int col) {
                    return false;
                }
            };
            tblRemote = new JTable(remoteTableModel);
            tblRemote.setRowHeight(24);

            // Tô màu dòng thư mục trên bảng Remote
            tblRemote.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value,
                                                               boolean isSelected, boolean hasFocus, int row, int column) {
                    Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                    String type = String.valueOf(table.getValueAt(row, 1));
                    if (!isSelected) {
                        if ("DIRECTORY".equals(type)) {
                            c.setForeground(new Color(0, 86, 179));
                            c.setFont(c.getFont().deriveFont(Font.BOLD));
                        } else {
                            c.setForeground(Color.BLACK);
                            c.setFont(c.getFont().deriveFont(Font.PLAIN));
                        }
                    }
                    return c;
                }
            });

            // TÍNH NĂNG MỚI: Double-click vào bảng Remote:
            // - Nếu là DIRECTORY: Gửi lệnh CWD để chuyển thư mục.
            // - Nếu là FILE: Tải ngầm qua Data Connection (RETR) và mở cửa sổ XEM TRỰC TIẾP!
            tblRemote.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        int row = tblRemote.getSelectedRow();
                        if (row >= 0) {
                            String type = String.valueOf(remoteTableModel.getValueAt(row, 1));
                            String name = String.valueOf(remoteTableModel.getValueAt(row, 2));
                            if ("DIRECTORY".equals(type)) {
                                doChangeWorkingDirectory(name);
                            } else if ("FILE".equals(type)) {
                                doPreviewRemoteFile(name);
                            }
                        }
                    }
                }
            });

            btnDownload = new JButton("⬇ DOWNLOAD VỀ MÁY CLIENT (Lệnh RETR — Tự động thêm số nếu trùng tên)");
            btnDownload.setFont(new Font("SansSerif", Font.BOLD, 12));
            btnDownload.setBackground(new Color(40, 167, 69));
            btnDownload.setForeground(Color.WHITE);

            remotePanel.add(remoteHeader, BorderLayout.NORTH);
            remotePanel.add(new JScrollPane(tblRemote), BorderLayout.CENTER);
            remotePanel.add(btnDownload, BorderLayout.SOUTH);

            // Gắn sự kiện cho các nút điều hướng và quản lý file
            btnPwd.addActionListener(e -> doPwd());
            btnCdup.addActionListener(e -> doCdup());
            btnList.addActionListener(e -> doListDirectory());
            btnMkd.addActionListener(e -> doMakeDirectory());
            btnRmd.addActionListener(e -> doRemoveDirectory());
            btnRename.addActionListener(e -> doRenameFileOrDir());
            btnDele.addActionListener(e -> doDeleteFile());
            btnDownload.addActionListener(e -> doDownloadSelectedFile());

            JSplitPane filesSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, localPanel, remotePanel);
            filesSplit.setResizeWeight(0.42);

            // 3. Khung bên dưới: Nhật ký giao thức Control Connection & Data Connection
            txtProtocolLog = new JTextPane();
            txtProtocolLog.setEditable(false);
            txtProtocolLog.setBackground(new Color(25, 25, 35));
            txtProtocolLog.setForeground(new Color(235, 235, 245));
            txtProtocolLog.setFont(new Font("Monospaced", Font.PLAIN, 13));
            JScrollPane logScroll = new JScrollPane(txtProtocolLog);
            logScroll.setBorder(new TitledBorder("Bộ Phân Tích Giao Thức FTP Thời Gian Thực (Control Connection vs Data Connection - Slide 5, 12, 13)"));
            logScroll.setPreferredSize(new Dimension(100, 220));

            JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, filesSplit, logScroll);
            verticalSplit.setResizeWeight(0.65);

            return verticalSplit;
        }

        private JPanel createBottomRawCommandBar() {
            JPanel panel = new JPanel(new BorderLayout(8, 4));
            panel.setBorder(new TitledBorder("Gửi Lệnh FTP Thô Qua Control Connection (Kết thúc bằng \\r\\n CRLF - Slide 10)"));

            txtCustomCmd = new JTextField("PWD");
            txtCustomCmd.setFont(new Font("Monospaced", Font.PLAIN, 13));
            JButton btnSendRaw = new JButton("Gửi Lệnh FTP");
            JButton btnTestTraversal = new JButton("Test Bảo Mật: Gửi 'RETR ../../secret.txt' (Chống Path Traversal - Slide 29)");
            btnTestTraversal.setForeground(new Color(180, 0, 0));

            JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
            rightBtns.add(btnSendRaw);
            rightBtns.add(btnTestTraversal);

            panel.add(new JLabel("Nhập FTP Command (VD: PWD, CWD /data, TYPE I, PASV, DELE abc.txt): "), BorderLayout.WEST);
            panel.add(txtCustomCmd, BorderLayout.CENTER);
            panel.add(rightBtns, BorderLayout.EAST);

            btnSendRaw.addActionListener(e -> doSendCustomRawCommand(txtCustomCmd.getText().trim()));
            txtCustomCmd.addActionListener(e -> doSendCustomRawCommand(txtCustomCmd.getText().trim()));
            btnTestTraversal.addActionListener(e -> doSendCustomRawCommand("RETR ../../secret.txt"));

            return panel;
        }

        private void refreshLocalFileTable() {
            localTableModel.setRowCount(0);
            try {
                File[] files = currentLocalDir.toFile().listFiles();
                if (files != null) {
                    Arrays.sort(files, Comparator.comparing(File::isFile).thenComparing(File::getName));
                    for (File f : files) {
                        localTableModel.addRow(new Object[]{
                                f.getName(),
                                f.isDirectory() ? "DIRECTORY" : "FILE",
                                f.isDirectory() ? "-" : f.length()
                        });
                    }
                }
            } catch (Exception ex) {
                appendClientLog("[ERROR] Không thể đọc thư mục Local: " + ex.getMessage());
            }
        }

        private Set<String> getCurrentRemoteNames() {
            Set<String> names = new HashSet<>();
            for (int i = 0; i < remoteTableModel.getRowCount(); i++) {
                names.add(String.valueOf(remoteTableModel.getValueAt(i, 2)));
            }
            return names;
        }

        private void updateActionButtonsState(boolean connected, boolean loggedIn) {
            btnConnect.setEnabled(!connected);
            btnLogin.setEnabled(connected && !loggedIn);
            btnQuit.setEnabled(connected);

            btnPwd.setEnabled(loggedIn);
            btnCdup.setEnabled(loggedIn);
            btnList.setEnabled(loggedIn);
            btnMkd.setEnabled(loggedIn);
            btnRmd.setEnabled(loggedIn);
            btnRename.setEnabled(loggedIn);
            btnDele.setEnabled(loggedIn);
            btnUpload.setEnabled(loggedIn);
            btnDownload.setEnabled(loggedIn);
        }

        private void updateReplyBadge(FtpReply reply) {
            if (reply == null) return;
            SwingUtilities.invokeLater(() -> {
                int group = reply.code / 100;
                String groupDesc;
                Color bg;
                Color fg = Color.WHITE;
                switch (group) {
                    case 1 -> {
                        groupDesc = "1xx (Đang xử lý - Chờ phản hồi tiếp theo)";
                        bg = new Color(23, 162, 184);
                    }
                    case 2 -> {
                        groupDesc = "2xx (Thành công)";
                        bg = new Color(40, 167, 69);
                    }
                    case 3 -> {
                        groupDesc = "3xx (Cần thêm thông tin - VD: PASS hoặc RNTO)";
                        bg = new Color(255, 193, 7);
                        fg = Color.BLACK;
                    }
                    case 4 -> {
                        groupDesc = "4xx (Lỗi tạm thời)";
                        bg = new Color(253, 126, 20);
                    }
                    default -> {
                        groupDesc = "5xx (Lỗi vĩnh viễn / Từ chối truy cập)";
                        bg = new Color(220, 53, 69);
                    }
                }
                lblLastReplyBadge.setBackground(bg);
                lblLastReplyBadge.setForeground(fg);
                lblLastReplyBadge.setText(" Phản hồi gần nhất: [" + reply.code + " " + reply.message + "]  —  Nhóm mã: " + groupDesc + " ");
            });
        }

        private void appendClientLog(String text) {
            SwingUtilities.invokeLater(() -> {
                txtProtocolLog.setText(txtProtocolLog.getText() + text + "\n");
                txtProtocolLog.setCaretPosition(txtProtocolLog.getDocument().getLength());
            });
        }

        // --- CÁC THAO TÁC CỦA FTP CLIENT ---
        private void doConnect() {
            String host = txtHost.getText().trim();
            int port = Integer.parseInt(txtPort.getText().trim());
            new Thread(() -> {
                try {
                    ftpClient = new RawFtpClient(this::appendClientLog, this::updateReplyBadge);
                    FtpReply greeting = ftpClient.connect(host, port);
                    SwingUtilities.invokeLater(() -> {
                        if (greeting.code == 220) {
                            updateActionButtonsState(true, false);
                        }
                    });
                } catch (Exception ex) {
                    appendClientLog("[LỖI KẾT NỐI] " + ex.getMessage());
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this,
                            "Không thể kết nối FTP Server tại " + host + ":" + port + "\nChi tiết: " + ex.getMessage(),
                            "Connection Error", JOptionPane.ERROR_MESSAGE));
                }
            }).start();
        }

        private void doLogin() {
            if (ftpClient == null) return;
            String user = txtUser.getText().trim();
            String pass = txtPass.getText().trim();
            new Thread(() -> {
                try {
                    boolean ok = ftpClient.login(user, pass);
                    if (ok) {
                        sendTypeCommand();
                        String pwd = ftpClient.printWorkingDirectory();
                        List<RemoteFileEntry> entries = ftpClient.listFiles(isPassiveSelected());
                        SwingUtilities.invokeLater(() -> {
                            txtRemotePwd.setText(pwd);
                            populateRemoteTable(entries);
                            updateActionButtonsState(true, true);
                        });
                    }
                } catch (Exception ex) {
                    appendClientLog("[LỖI ĐĂNG NHẬP] " + ex.getMessage());
                }
            }).start();
        }

        private void sendTypeCommand() {
            try {
                String typeCode = cbType.getSelectedIndex() == 0 ? "I" : "A";
                ftpClient.setType(typeCode);
            } catch (Exception ex) {
                appendClientLog("[LỖI TYPE] " + ex.getMessage());
            }
        }

        private boolean isPassiveSelected() {
            return cbMode.getSelectedIndex() == 0;
        }

        private void doPwd() {
            new Thread(() -> {
                try {
                    String pwd = ftpClient.printWorkingDirectory();
                    SwingUtilities.invokeLater(() -> txtRemotePwd.setText(pwd));
                } catch (Exception ex) {
                    appendClientLog("[LỖI PWD] " + ex.getMessage());
                }
            }).start();
        }

        private void doChangeWorkingDirectory(String targetDir) {
            new Thread(() -> {
                try {
                    FtpReply rep = ftpClient.changeWorkingDirectory(targetDir);
                    if (rep.isSuccess()) {
                        String pwd = ftpClient.printWorkingDirectory();
                        List<RemoteFileEntry> entries = ftpClient.listFiles(isPassiveSelected());
                        SwingUtilities.invokeLater(() -> {
                            txtRemotePwd.setText(pwd);
                            populateRemoteTable(entries);
                        });
                    }
                } catch (Exception ex) {
                    appendClientLog("[LỖI CWD] " + ex.getMessage());
                }
            }).start();
        }

        /**
         * TÍNH NĂNG MỚI: Xem trực tiếp nội dung file trên FTP Server khi nháy đúp chuột
         * Hệ thống tải file qua Data Connection (lệnh RETR) vào một file tạm rồi mở hộp thoại Preview
         */
        private void doPreviewRemoteFile(String remoteFileName) {
            if (ftpClient == null || !ftpClient.isLoggedIn()) return;
            new Thread(() -> {
                try {
                    String ext = "";
                    int dot = remoteFileName.lastIndexOf('.');
                    if (dot >= 0) ext = remoteFileName.substring(dot);

                    Path tempPreviewFile = Files.createTempFile("ftp_preview_", "_" + remoteFileName.replaceAll("[^a-zA-Z0-9._-]", "_") + ext);
                    tempPreviewFile.toFile().deleteOnExit();

                    appendClientLog("[XEM FILE TRỰC TIẾP] Đang tải nội dung '" + remoteFileName + "' qua Data Connection để xem...");
                    sendTypeCommand();
                    boolean ok = ftpClient.downloadFile(remoteFileName, tempPreviewFile, isPassiveSelected());
                    if (ok) {
                        SwingUtilities.invokeLater(() -> showFilePreviewDialog(
                                FtpClientPanel.this,
                                "Xem Trực Tiếp File Trên FTP Server (" + txtRemotePwd.getText() + ")",
                                remoteFileName,
                                tempPreviewFile,
                                false,
                                null
                        ));
                    }
                } catch (Exception ex) {
                    appendClientLog("[LỖI XEM FILE SERVER] " + ex.getMessage());
                }
            }).start();
        }

        private void doCdup() {
            new Thread(() -> {
                try {
                    FtpReply rep = ftpClient.changeToParentDirectory();
                    if (rep.isSuccess()) {
                        String pwd = ftpClient.printWorkingDirectory();
                        List<RemoteFileEntry> entries = ftpClient.listFiles(isPassiveSelected());
                        SwingUtilities.invokeLater(() -> {
                            txtRemotePwd.setText(pwd);
                            populateRemoteTable(entries);
                        });
                    }
                } catch (Exception ex) {
                    appendClientLog("[LỖI CDUP] " + ex.getMessage());
                }
            }).start();
        }

        private void doListDirectory() {
            new Thread(() -> {
                try {
                    String pwd = ftpClient.printWorkingDirectory();
                    List<RemoteFileEntry> entries = ftpClient.listFiles(isPassiveSelected());
                    SwingUtilities.invokeLater(() -> {
                        txtRemotePwd.setText(pwd);
                        populateRemoteTable(entries);
                    });
                } catch (Exception ex) {
                    appendClientLog("[LỖI LIST] " + ex.getMessage());
                }
            }).start();
        }

        /**
         * Upload file từ Client lên Server (Lệnh STOR)
         * Nếu trên Server đã có file cùng tên, tự động thêm số (1), (2)...
         */
        private void doUploadSelectedFile() {
            int row = tblLocal.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 tập tin ở bảng Máy Client (bên trái) để Upload!");
                return;
            }
            String fileName = String.valueOf(localTableModel.getValueAt(row, 0));
            String type = String.valueOf(localTableModel.getValueAt(row, 1));
            if ("DIRECTORY".equals(type)) {
                JOptionPane.showMessageDialog(this, "Lệnh STOR dùng để truyền tập tin (File). Vui lòng chọn một File!");
                return;
            }
            Path localFile = currentLocalDir.resolve(fileName);

            // Kiểm tra trùng tên trên bảng Remote hiện tại để sinh tên có số (1), (2)...
            Set<String> existingRemoteNames = getCurrentRemoteNames();
            String targetRemoteName = resolveUniqueNameInSet(fileName, existingRemoteNames, false);
            if (!targetRemoteName.equals(fileName)) {
                appendClientLog("[AUTO-RENAME UPLOAD] File '" + fileName + "' đã tồn tại trên Server -> Tự động đổi tên thành: '" + targetRemoteName + "'");
            }

            new Thread(() -> {
                try {
                    sendTypeCommand();
                    boolean ok = ftpClient.uploadFile(localFile, targetRemoteName, isPassiveSelected());
                    if (ok) {
                        List<RemoteFileEntry> entries = ftpClient.listFiles(isPassiveSelected());
                        SwingUtilities.invokeLater(() -> populateRemoteTable(entries));
                    }
                } catch (Exception ex) {
                    appendClientLog("[LỖI STOR] " + ex.getMessage());
                }
            }).start();
        }

        /**
         * Download file từ Server về Client (Lệnh RETR)
         * Nếu trong thư mục Local đã có file cùng tên, tự động lưu thành tên có đánh số (1), (2)...
         */
        private void doDownloadSelectedFile() {
            int row = tblRemote.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 tập tin ở bảng FTP Server (bên phải) để Download!");
                return;
            }
            String type = String.valueOf(remoteTableModel.getValueAt(row, 1));
            String remoteFileName = String.valueOf(remoteTableModel.getValueAt(row, 2));
            if ("DIRECTORY".equals(type)) {
                JOptionPane.showMessageDialog(this, "Không thể dùng RETR cho thư mục. Hãy nháy đúp vào thư mục để mở, hoặc chọn 1 File!");
                return;
            }

            // Tự động tạo đường dẫn Local không trùng tên: ví dụ report (1).pdf, report (2).pdf
            Path savePath = resolveUniquePath(currentLocalDir, remoteFileName, false);
            String savedFileName = savePath.getFileName().toString();
            if (!savedFileName.equals(remoteFileName)) {
                appendClientLog("[AUTO-RENAME DOWNLOAD] File '" + remoteFileName + "' đã tồn tại trên máy Client -> Tự động lưu thành: '" + savedFileName + "'");
            }

            new Thread(() -> {
                try {
                    sendTypeCommand();
                    boolean ok = ftpClient.downloadFile(remoteFileName, savePath, isPassiveSelected());
                    if (ok) {
                        SwingUtilities.invokeLater(this::refreshLocalFileTable);
                    }
                } catch (Exception ex) {
                    appendClientLog("[LỖI RETR] " + ex.getMessage());
                }
            }).start();
        }

        private void doMakeDirectory() {
            String folderName = JOptionPane.showInputDialog(this,
                    "Nhập tên thư mục mới cần tạo (Lệnh MKD - Tự động thêm số nếu trùng):", "backup");
            if (folderName == null || folderName.isBlank()) return;

            Set<String> existingRemoteNames = getCurrentRemoteNames();
            String uniqueFolder = resolveUniqueNameInSet(folderName.trim(), existingRemoteNames, true);
            if (!uniqueFolder.equals(folderName.trim())) {
                appendClientLog("[AUTO-RENAME MKD] Thư mục '" + folderName.trim() + "' đã tồn tại -> Tự động tạo: '" + uniqueFolder + "'");
            }

            new Thread(() -> {
                try {
                    FtpReply rep = ftpClient.makeDirectory(uniqueFolder);
                    if (rep.isSuccess()) {
                        List<RemoteFileEntry> entries = ftpClient.listFiles(isPassiveSelected());
                        SwingUtilities.invokeLater(() -> populateRemoteTable(entries));
                    }
                } catch (Exception ex) {
                    appendClientLog("[LỖI MKD] " + ex.getMessage());
                }
            }).start();
        }

        private void doRemoveDirectory() {
            int row = tblRemote.getSelectedRow();
            String defaultName = (row >= 0) ? String.valueOf(remoteTableModel.getValueAt(row, 2)) : "backup";
            String folderName = JOptionPane.showInputDialog(this, "Nhập tên thư mục cần xóa (Lệnh RMD):", defaultName);
            if (folderName == null || folderName.isBlank()) return;
            new Thread(() -> {
                try {
                    FtpReply rep = ftpClient.removeDirectory(folderName.trim());
                    if (rep.isSuccess()) {
                        List<RemoteFileEntry> entries = ftpClient.listFiles(isPassiveSelected());
                        SwingUtilities.invokeLater(() -> populateRemoteTable(entries));
                    }
                } catch (Exception ex) {
                    appendClientLog("[LỖI RMD] " + ex.getMessage());
                }
            }).start();
        }

        private void doDeleteFile() {
            int row = tblRemote.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(this, "Hãy chọn một file trên bảng FTP Server để xóa (Lệnh DELE)!");
                return;
            }
            String name = String.valueOf(remoteTableModel.getValueAt(row, 2));
            int confirm = JOptionPane.showConfirmDialog(this, "Gửi lệnh 'DELE " + name + "' để xóa tập tin này?",
                    "Xác nhận xóa (DELE)", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;

            new Thread(() -> {
                try {
                    FtpReply rep = ftpClient.deleteFile(name);
                    if (rep.isSuccess()) {
                        List<RemoteFileEntry> entries = ftpClient.listFiles(isPassiveSelected());
                        SwingUtilities.invokeLater(() -> populateRemoteTable(entries));
                    }
                } catch (Exception ex) {
                    appendClientLog("[LỖI DELE] " + ex.getMessage());
                }
            }).start();
        }

        private void doRenameFileOrDir() {
            int row = tblRemote.getSelectedRow();
            String oldDefault = (row >= 0) ? String.valueOf(remoteTableModel.getValueAt(row, 2)) : "file1.txt";
            String oldName = JOptionPane.showInputDialog(this, "Bước 1 (RNFR) - Tên cũ trên Server:", oldDefault);
            if (oldName == null || oldName.isBlank()) return;
            String newName = JOptionPane.showInputDialog(this, "Bước 2 (RNTO) - Nhập tên mới (Tự động thêm số nếu trùng):", "renamed_" + oldName.trim());
            if (newName == null || newName.isBlank()) return;

            new Thread(() -> {
                try {
                    boolean ok = ftpClient.rename(oldName.trim(), newName.trim());
                    if (ok) {
                        List<RemoteFileEntry> entries = ftpClient.listFiles(isPassiveSelected());
                        SwingUtilities.invokeLater(() -> populateRemoteTable(entries));
                    }
                } catch (Exception ex) {
                    appendClientLog("[LỖI RNFR/RNTO] " + ex.getMessage());
                }
            }).start();
        }

        private void doQuit() {
            if (ftpClient == null) return;
            new Thread(() -> {
                try {
                    ftpClient.quit();
                } catch (Exception ignored) {
                } finally {
                    SwingUtilities.invokeLater(() -> {
                        remoteTableModel.setRowCount(0);
                        txtRemotePwd.setText("/");
                        updateActionButtonsState(false, false);
                    });
                }
            }).start();
        }

        private void doSendCustomRawCommand(String cmd) {
            if (cmd.isEmpty()) return;
            if (ftpClient == null || !ftpClient.isConnected()) {
                JOptionPane.showMessageDialog(this, "Vui lòng nhấn '1. Connect' trước khi gửi lệnh FTP!");
                return;
            }
            new Thread(() -> {
                try {
                    ftpClient.sendCommandAndReadReply(cmd);
                } catch (Exception ex) {
                    appendClientLog("[LỖI GỬI LỆNH] " + ex.getMessage());
                }
            }).start();
        }

        private void populateRemoteTable(List<RemoteFileEntry> entries) {
            remoteTableModel.setRowCount(0);
            for (RemoteFileEntry e : entries) {
                remoteTableModel.addRow(new Object[]{
                        e.permissions,
                        e.isDirectory ? "DIRECTORY" : "FILE",
                        e.name,
                        e.isDirectory ? "-" : e.size,
                        e.dateStr
                });
            }
        }

        /**
         * Tự động chạy 8 kịch bản kiểm thử bắt buộc ở Slide 30 (Mục 6)
         */
        private void runMandatoryTestScenarios() {
            int port = Integer.parseInt(txtPort.getText().trim());
            String host = txtHost.getText().trim();
            new Thread(() -> {
                appendClientLog("\n================================================================================");
                appendClientLog("   BẮT ĐẦU CHẠY BỘ KỊCH BẢN KIỂM THỬ BẮT BUỘC (SLIDE 30 - MỤC 6)");
                appendClientLog("================================================================================");
                try {
                    // Kịch bản 1: Login sai
                    appendClientLog("\n--- [KỊCH BẢN 1] Kiểm thử Login SAI mật khẩu (Mong đợi: 530 Login incorrect) ---");
                    RawFtpClient testClient = new RawFtpClient(this::appendClientLog, this::updateReplyBadge);
                    testClient.connect(host, port);
                    testClient.login("student", "wrong_pass_999");
                    testClient.quit();

                    // Kịch bản 2: Sai quyền (Login bằng guest Read-Only và thử tạo/xóa file -> 550 Permission denied)
                    appendClientLog("\n--- [KỊCH BẢN 2] Kiểm thử SAI QUYỀN với user 'guest' (Mong đợi: 550 Permission denied) ---");
                    testClient = new RawFtpClient(this::appendClientLog, this::updateReplyBadge);
                    testClient.connect(host, port);
                    testClient.login("guest", "123456");
                    testClient.sendCommandAndReadReply("MKD ReadOnlyFolderTest");
                    testClient.quit();

                    // Kịch bản 3: Login đúng + PWD + LIST + STOR + RETR + File không tồn tại + Chống Path Traversal + QUIT
                    appendClientLog("\n--- [KỊCH BẢN 3 -> 8] Kiểm thử Login ĐÚNG, PWD, LIST, STOR, RETR, File không tồn tại & QUIT ---");
                    ftpClient = new RawFtpClient(this::appendClientLog, this::updateReplyBadge);
                    ftpClient.connect(host, port);
                    ftpClient.login("student", "123456");

                    String pwd = ftpClient.printWorkingDirectory();
                    ftpClient.setType("I");

                    // LIST
                    ftpClient.listFiles(true);

                    // STOR (Upload data.txt - nếu chạy nhiều lần Server sẽ tự động đánh số (1), (2)...)
                    Path sampleUpload = CLIENT_LOCAL_DIR.resolve("data.txt");
                    ftpClient.uploadFile(sampleUpload, "data_uploaded_test.txt", true);

                    // RETR (Download report.pdf - tự động đánh số nếu trùng ở Local)
                    Path sampleDownload = resolveUniquePath(CLIENT_LOCAL_DIR, "downloaded_report.pdf", false);
                    ftpClient.downloadFile("report.pdf", sampleDownload, true);

                    // RETR file không tồn tại (Mong đợi: 550 File unavailable)
                    appendClientLog("\n--- [KỊCH BẢN 6] Tải file KHÔNG TỒN TẠI 'RETR khong_ton_tai.txt' (Mong đợi: 550 File unavailable) ---");
                    ftpClient.sendCommandAndReadReply("RETR khong_ton_tai.txt");

                    // Kiểm thử chống Path Traversal (Slide 29 & 30)
                    appendClientLog("\n--- [KỊCH BẢN BẢO MẬT] Chống Path Traversal 'RETR ../../secret.txt' (Mong đợi: 550 Access denied) ---");
                    ftpClient.sendCommandAndReadReply("RETR ../../secret.txt");

                    List<RemoteFileEntry> updatedList = ftpClient.listFiles(true);
                    SwingUtilities.invokeLater(() -> {
                        txtRemotePwd.setText(pwd);
                        populateRemoteTable(updatedList);
                        refreshLocalFileTable();
                        updateActionButtonsState(true, true);
                    });

                    appendClientLog("\n[HOÀN TẤT] Đã thực thi thành công toàn bộ kịch bản kiểm thử bắt buộc! Phiên làm việc hiện đang mở sẵn.");
                } catch (Exception ex) {
                    appendClientLog("[LỖI KỊCH BẢN TEST] " + ex.getMessage());
                }
            }).start();
        }
    }

    // =========================================================================================
    // PHẦN 2: LỚP RAW FTP CLIENT SỬ DỤNG TCP SOCKET THUẦN (SLIDE 10 -> 25)
    // =========================================================================================
    public interface LogConsumer {
        void log(String text);
    }

    public interface ReplyConsumer {
        void onReply(FtpReply reply);
    }

    public static class FtpReply {
        public final int code;
        public final String message;
        public final String rawLine;

        public FtpReply(int code, String message, String rawLine) {
            this.code = code;
            this.message = message;
            this.rawLine = rawLine;
        }

        public boolean isSuccess() {
            return code >= 200 && code < 300;
        }
    }

    public static class RemoteFileEntry {
        public final String permissions;
        public final boolean isDirectory;
        public final long size;
        public final String dateStr;
        public final String name;

        public RemoteFileEntry(String permissions, boolean isDirectory, long size, String dateStr, String name) {
            this.permissions = permissions;
            this.isDirectory = isDirectory;
            this.size = size;
            this.dateStr = dateStr;
            this.name = name;
        }
    }

    public static class RawFtpClient {
        private Socket controlSocket;
        private BufferedReader controlReader;
        private BufferedWriter controlWriter;
        private boolean loggedIn = false;
        private final LogConsumer logger;
        private final ReplyConsumer replyListener;

        public RawFtpClient(LogConsumer logger, ReplyConsumer replyListener) {
            this.logger = logger;
            this.replyListener = replyListener;
        }

        public boolean isConnected() {
            return controlSocket != null && controlSocket.isConnected() && !controlSocket.isClosed();
        }

        public boolean isLoggedIn() {
            return isConnected() && loggedIn;
        }

        /**
         * Kết nối đến FTP Server qua Control Connection (Slide 15)
         */
        public FtpReply connect(String host, int port) throws IOException {
            controlSocket = new Socket();
            controlSocket.connect(new InetSocketAddress(host, port), 10000);
            controlSocket.setSoTimeout(30000);

            controlReader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));
            controlWriter = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream(), StandardCharsets.UTF_8));

            logger.log("[CONTROL] Đã mở kết nối TCP đến " + host + ":" + port);
            return readReply(); // Đọc phản hồi 220 Service Ready đầu tiên (Slide 15 - mục 4)
        }

        /**
         * Gửi một FTP Command kết thúc bằng \r\n (CRLF) như Slide 6 & Slide 10
         */
        public void sendCommand(String command) throws IOException {
            String displayCmd = command.toUpperCase().startsWith("PASS ") ? "PASS ******" : command;
            logger.log("C -> S (Control): " + displayCmd + "\\r\\n");
            controlWriter.write(command + "\r\n");
            controlWriter.flush();
        }

        /**
         * Đọc FTP Reply và tách 3 chữ số đầu tiên (Slide 12 & Slide 16)
         */
        public FtpReply readReply() throws IOException {
            String line = controlReader.readLine();
            if (line == null || line.length() < 3) {
                throw new IOException("Server đã đóng kết nối Control Connection!");
            }
            logger.log("S -> C (Reply)  : " + line);
            int code = Integer.parseInt(line.substring(0, 3));
            String msg = line.length() > 4 ? line.substring(4) : "";
            FtpReply reply = new FtpReply(code, msg, line);
            if (replyListener != null) {
                replyListener.onReply(reply);
            }
            return reply;
        }

        public FtpReply sendCommandAndReadReply(String command) throws IOException {
            sendCommand(command);
            return readReply();
        }

        /**
         * Quy trình đăng nhập bằng lệnh USER và PASS (Slide 17)
         */
        public boolean login(String username, String password) throws IOException {
            FtpReply userReply = sendCommandAndReadReply("USER " + username);
            if (userReply.code == 230) {
                loggedIn = true;
                return true;
            }
            if (userReply.code != 331) {
                loggedIn = false;
                return false;
            }
            FtpReply passReply = sendCommandAndReadReply("PASS " + password);
            loggedIn = (passReply.code == 230);
            return loggedIn;
        }

        /**
         * Các lệnh điều hướng thư mục: PWD, CWD, CDUP (Slide 18)
         */
        public String printWorkingDirectory() throws IOException {
            FtpReply reply = sendCommandAndReadReply("PWD");
            if (reply.code == 257) {
                int firstQuote = reply.message.indexOf('"');
                int secondQuote = reply.message.indexOf('"', firstQuote + 1);
                if (firstQuote >= 0 && secondQuote > firstQuote) {
                    return reply.message.substring(firstQuote + 1, secondQuote);
                }
            }
            return "/";
        }

        public FtpReply changeWorkingDirectory(String path) throws IOException {
            return sendCommandAndReadReply("CWD " + path);
        }

        public FtpReply changeToParentDirectory() throws IOException {
            return sendCommandAndReadReply("CDUP");
        }

        public FtpReply setType(String typeCode) throws IOException {
            return sendCommandAndReadReply("TYPE " + typeCode);
        }

        /**
         * Thiết lập Passive Mode (PASV) và phân tích 6 số (h1,h2,h3,h4,p1,p2) với công thức Port = p1 * 256 + p2 (Slide 20 & 21)
         */
        public InetSocketAddress enterPassiveMode() throws IOException {
            FtpReply reply = sendCommandAndReadReply("PASV");
            if (reply.code != 227) {
                throw new IOException("Không thể vào chế độ Passive Mode: " + reply.rawLine);
            }
            int start = reply.rawLine.indexOf('(');
            int end = reply.rawLine.indexOf(')');
            String inside = reply.rawLine.substring(start + 1, end);
            String[] parts = inside.split(",");
            String ip = String.format("%s.%s.%s.%s", parts[0].trim(), parts[1].trim(), parts[2].trim(), parts[3].trim());
            int p1 = Integer.parseInt(parts[4].trim());
            int p2 = Integer.parseInt(parts[5].trim());
            int dataPort = p1 * 256 + p2;
            logger.log(String.format("   [PASV PARSER] IP = %s | p1=%d, p2=%d => Data Port = %d * 256 + %d = %d",
                    ip, p1, p2, p1, p2, dataPort));
            return new InetSocketAddress(ip, dataPort);
        }

        /**
         * Hỗ trợ cả Active Mode (lệnh PORT h1,h2,h3,h4,p1,p2) như Slide 8
         */
        private ServerSocket openActiveModePortSocket() throws IOException {
            ServerSocket activeListener = new ServerSocket(0);
            activeListener.setSoTimeout(15000);
            int localPort = activeListener.getLocalPort();
            int p1 = localPort / 256;
            int p2 = localPort % 256;
            String hostIp = controlSocket.getLocalAddress().getHostAddress().replace('.', ',');
            if (hostIp.contains(":")) hostIp = "127,0,0,1";
            FtpReply portRep = sendCommandAndReadReply("PORT " + hostIp + "," + p1 + "," + p2);
            if (!portRep.isSuccess()) {
                activeListener.close();
                throw new IOException("Lệnh PORT thất bại: " + portRep.rawLine);
            }
            return activeListener;
        }

        /**
         * Lệnh LIST lấy danh sách file và thư mục qua Data Connection (Slide 22)
         */
        public List<RemoteFileEntry> listFiles(boolean usePassive) throws IOException {
            List<RemoteFileEntry> result = new ArrayList<>();
            if (usePassive) {
                InetSocketAddress dataAddr = enterPassiveMode();
                try (Socket dataSocket = new Socket()) {
                    dataSocket.connect(dataAddr, 10000);
                    logger.log("   [DATA CONN] Đã kết nối Data Connection (Passive) tới " + dataAddr);
                    FtpReply rep150 = sendCommandAndReadReply("LIST");
                    if (rep150.code != 150 && rep150.code != 125) {
                        return result;
                    }
                    readListingStream(dataSocket.getInputStream(), result);
                }
                readReply(); // Đọc 226 Transfer complete
            } else {
                try (ServerSocket activeServer = openActiveModePortSocket()) {
                    FtpReply rep150 = sendCommandAndReadReply("LIST");
                    if (rep150.code != 150 && rep150.code != 125) {
                        return result;
                    }
                    try (Socket dataSocket = activeServer.accept()) {
                        logger.log("   [DATA CONN] Server đã kết nối ngược vào Active Data Port của Client");
                        readListingStream(dataSocket.getInputStream(), result);
                    }
                }
                readReply(); // 226 Transfer complete
            }
            return result;
        }

        private void readListingStream(InputStream in, List<RemoteFileEntry> result) throws IOException {
            BufferedReader dataReader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = dataReader.readLine()) != null) {
                logger.log("   [DATA LIST] " + line);
                // Định dạng dòng LIST chuẩn: drwxr-xr-x 1 user group 0 Aug 15 10:20 images
                String[] tokens = line.trim().split("\\s+", 9);
                if (tokens.length >= 9) {
                    String perms = tokens[0];
                    boolean isDir = perms.startsWith("d");
                    long size = 0;
                    try {
                        size = Long.parseLong(tokens[4]);
                    } catch (NumberFormatException ignored) {
                    }
                    String dateStr = tokens[5] + " " + tokens[6] + " " + tokens[7];
                    String name = tokens[8];
                    result.add(new RemoteFileEntry(perms, isDir, size, dateStr, name));
                }
            }
        }

        /**
         * Lệnh RETR (Download file từ FTP Server về Client) qua Data Connection (Slide 23)
         */
        public boolean downloadFile(String remoteFile, Path localTarget, boolean usePassive) throws IOException {
            if (usePassive) {
                InetSocketAddress dataAddr = enterPassiveMode();
                try (Socket dataSocket = new Socket()) {
                    dataSocket.connect(dataAddr, 10000);
                    FtpReply rep150 = sendCommandAndReadReply("RETR " + remoteFile);
                    if (rep150.code != 150 && rep150.code != 125) {
                        return false;
                    }
                    try (InputStream dataIn = dataSocket.getInputStream();
                         OutputStream fileOut = Files.newOutputStream(localTarget, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                        long bytes = dataIn.transferTo(fileOut);
                        logger.log("   [DATA RETR] Đã nhận " + bytes + " bytes và lưu vào: " + localTarget.getFileName());
                    }
                }
                FtpReply rep226 = readReply();
                return rep226.code == 226;
            } else {
                try (ServerSocket activeServer = openActiveModePortSocket()) {
                    FtpReply rep150 = sendCommandAndReadReply("RETR " + remoteFile);
                    if (rep150.code != 150 && rep150.code != 125) {
                        return false;
                    }
                    try (Socket dataSocket = activeServer.accept();
                         InputStream dataIn = dataSocket.getInputStream();
                         OutputStream fileOut = Files.newOutputStream(localTarget, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                        long bytes = dataIn.transferTo(fileOut);
                        logger.log("   [DATA RETR] Đã nhận " + bytes + " bytes (Active Mode) vào: " + localTarget.getFileName());
                    }
                }
                FtpReply rep226 = readReply();
                return rep226.code == 226;
            }
        }

        /**
         * Lệnh STOR (Upload file từ Client lên FTP Server) qua Data Connection (Slide 24)
         */
        public boolean uploadFile(Path localFile, String remoteFileName, boolean usePassive) throws IOException {
            if (!Files.exists(localFile)) {
                throw new FileNotFoundException("File local không tồn tại: " + localFile);
            }
            if (usePassive) {
                InetSocketAddress dataAddr = enterPassiveMode();
                try (Socket dataSocket = new Socket()) {
                    dataSocket.connect(dataAddr, 10000);
                    FtpReply rep150 = sendCommandAndReadReply("STOR " + remoteFileName);
                    if (rep150.code != 150 && rep150.code != 125) {
                        return false;
                    }
                    try (InputStream fileIn = Files.newInputStream(localFile);
                         OutputStream dataOut = dataSocket.getOutputStream()) {
                        long bytes = fileIn.transferTo(dataOut);
                        dataOut.flush();
                        logger.log("   [DATA STOR] Đã truyền " + bytes + " bytes của file '" + localFile.getFileName() + "' lên Server.");
                    }
                } // Đóng Data Connection để báo EOF cho Server (Slide 24 - mục 9)
                FtpReply rep226 = readReply();
                return rep226.code == 226;
            } else {
                try (ServerSocket activeServer = openActiveModePortSocket()) {
                    FtpReply rep150 = sendCommandAndReadReply("STOR " + remoteFileName);
                    if (rep150.code != 150 && rep150.code != 125) {
                        return false;
                    }
                    try (Socket dataSocket = activeServer.accept();
                         InputStream fileIn = Files.newInputStream(localFile);
                         OutputStream dataOut = dataSocket.getOutputStream()) {
                        long bytes = fileIn.transferTo(dataOut);
                        dataOut.flush();
                        logger.log("   [DATA STOR] Đã truyền " + bytes + " bytes (Active Mode) lên Server.");
                    }
                }
                FtpReply rep226 = readReply();
                return rep226.code == 226;
            }
        }

        /**
         * Các thao tác quản lý file: DELE, MKD, RMD, RNFR + RNTO (Slide 25)
         */
        public FtpReply deleteFile(String remoteFile) throws IOException {
            return sendCommandAndReadReply("DELE " + remoteFile);
        }

        public FtpReply makeDirectory(String folderName) throws IOException {
            return sendCommandAndReadReply("MKD " + folderName);
        }

        public FtpReply removeDirectory(String folderName) throws IOException {
            return sendCommandAndReadReply("RMD " + folderName);
        }

        public boolean rename(String oldName, String newName) throws IOException {
            FtpReply step1 = sendCommandAndReadReply("RNFR " + oldName);
            if (step1.code != 350) {
                return false;
            }
            FtpReply step2 = sendCommandAndReadReply("RNTO " + newName);
            return step2.code == 250;
        }

        public void quit() throws IOException {
            if (isConnected()) {
                try {
                    sendCommandAndReadReply("QUIT");
                } finally {
                    controlSocket.close();
                    loggedIn = false;
                }
            }
        }
    }

    // =========================================================================================
    // PHẦN 3: MULTI-CLIENT FTP SERVER BẰNG JAVA SOCKET (SLIDE 26 -> 30)
    // =========================================================================================
    public static class FtpSession {
        public final int sessionId;
        public final String clientAddress;
        public String username = null;
        public boolean loggedIn = false;
        public boolean canWrite = false;
        public String currentVirtualDir = "/";
        public String transferType = "I";
        public ServerSocket passiveListener = null;
        public InetSocketAddress activeClientDataAddress = null;
        public Path pendingRenameSource = null;

        public FtpSession(int sessionId, String clientAddress) {
            this.sessionId = sessionId;
            this.clientAddress = clientAddress;
        }

        public void closePassiveListener() {
            if (passiveListener != null && !passiveListener.isClosed()) {
                try {
                    passiveListener.close();
                } catch (IOException ignored) {
                }
            }
            passiveListener = null;
        }
    }

    public static class EmbeddedFtpServer {
        private static final int MAX_CONCURRENT_CLIENTS = 20;
        private static final int CONTROL_TIMEOUT_MS = 120_000; // Timeout chống treo kết nối (Slide 29)

        private final int port;
        private final Path rootDir;
        private final LogConsumer serverLogger;
        private final java.util.function.Consumer<Collection<FtpSession>> sessionUpdater;

        private ServerSocket serverSocket;
        private volatile boolean running = false;
        private final ExecutorService clientPool = Executors.newCachedThreadPool();
        private final AtomicInteger sessionCounter = new AtomicInteger(1);
        private final Map<Integer, FtpSession> activeSessions = new ConcurrentHashMap<>();

        // Cơ sở dữ liệu người dùng mẫu: student (full quyền) & guest (chỉ đọc)
        private final Map<String, String> userPasswords = Map.of(
                "student", "123456",
                "admin", "admin123",
                "guest", "123456"
        );

        public EmbeddedFtpServer(int port, Path rootDir, LogConsumer serverLogger,
                                 java.util.function.Consumer<Collection<FtpSession>> sessionUpdater) {
            this.port = port;
            this.rootDir = rootDir.toAbsolutePath().normalize();
            this.serverLogger = serverLogger;
            this.sessionUpdater = sessionUpdater;
        }

        public void start() throws IOException {
            serverSocket = new ServerSocket(port);
            running = true;
            serverLogger.log("Server started on port " + port + " | FTP Root: " + rootDir);

            Thread acceptThread = new Thread(() -> {
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        if (activeSessions.size() >= MAX_CONCURRENT_CLIENTS) {
                            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                                pw.print("421 Service not available, too many connections\r\n");
                            }
                            clientSocket.close();
                            continue;
                        }
                        clientPool.submit(() -> handleClientSession(clientSocket));
                    } catch (IOException ex) {
                        if (running) {
                            serverLogger.log("Accept error: " + ex.getMessage());
                        }
                    }
                }
            }, "FTP-Server-Acceptor");
            acceptThread.setDaemon(true);
            acceptThread.start();
        }

        public void stop() {
            running = false;
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException ignored) {
            }
            serverLogger.log("FTP Server stopped.");
        }

        private void handleClientSession(Socket client) {
            int sid = sessionCounter.getAndIncrement();
            String clientIp = client.getInetAddress().getHostAddress() + ":" + client.getPort();
            FtpSession session = new FtpSession(sid, clientIp);
            activeSessions.put(sid, session);
            sessionUpdater.accept(activeSessions.values());

            serverLogger.log("Client " + clientIp + " connected (Session #" + sid + ")");

            try (client;
                 BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {

                client.setSoTimeout(CONTROL_TIMEOUT_MS);
                sendReply(writer, session, "220 Service ready");

                String line;
                while (running && (line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    String logLine = line.toUpperCase().startsWith("PASS ") ? "PASS ******" : line;
                    serverLogger.log("C->S [#" + sid + "]: " + logLine);

                    String[] parts = line.split("\\s+", 2);
                    String command = parts[0].toUpperCase();
                    String param = parts.length > 1 ? parts[1].trim() : "";

                    // Kiểm tra đăng nhập trước khi thực thi các lệnh cần xác thực (Slide 27 - mục 6 & Slide 29 - mục 11)
                    if (!session.loggedIn && !command.equals("USER") && !command.equals("PASS")
                            && !command.equals("QUIT") && !command.equals("NOOP") && !command.equals("SYST")) {
                        sendReply(writer, session, "530 Not logged in");
                        continue;
                    }

                    boolean keepAlive = processFtpCommand(command, param, writer, session, client.getLocalAddress());
                    sessionUpdater.accept(activeSessions.values());
                    if (!keepAlive) {
                        break;
                    }
                }
            } catch (SocketTimeoutException te) {
                serverLogger.log("Session #" + sid + " timeout: 421 Service not available, closing control connection.");
            } catch (IOException ex) {
                serverLogger.log("Session #" + sid + " I/O notice: " + ex.getMessage());
            } finally {
                session.closePassiveListener();
                activeSessions.remove(sid);
                sessionUpdater.accept(activeSessions.values());
                serverLogger.log("Client " + clientIp + " disconnected (Session #" + sid + ")");
            }
        }

        private void sendReply(BufferedWriter writer, FtpSession session, String reply) throws IOException {
            serverLogger.log("S->C [#" + session.sessionId + "]: " + reply);
            writer.write(reply + "\r\n");
            writer.flush();
        }

        /**
         * Phân nhánh xử lý đầy đủ các lệnh FTP trên Server (Slide 27 & Slide 30)
         */
        private boolean processFtpCommand(String command, String param, BufferedWriter writer,
                                          FtpSession session, InetAddress serverLocalAddr) throws IOException {
            switch (command) {
                case "USER" -> {
                    session.username = param;
                    session.loggedIn = false;
                    if (param.isEmpty()) {
                        sendReply(writer, session, "501 Syntax error in parameters");
                    } else {
                        sendReply(writer, session, "331 Password required");
                    }
                }
                case "PASS" -> {
                    if (session.username == null) {
                        sendReply(writer, session, "503 Login with USER first");
                    } else if (userPasswords.containsKey(session.username)
                            && userPasswords.get(session.username).equals(param)) {
                        session.loggedIn = true;
                        session.canWrite = !"guest".equalsIgnoreCase(session.username);
                        sendReply(writer, session, "230 Login successful");
                    } else {
                        session.loggedIn = false;
                        sendReply(writer, session, "530 Login incorrect");
                    }
                }
                case "PWD", "XPWD" -> sendReply(writer, session, "257 \"" + session.currentVirtualDir + "\" is current directory");

                case "CWD" -> {
                    Path target = resolveSafePath(session, param);
                    if (target == null || !Files.exists(target) || !Files.isDirectory(target)) {
                        sendReply(writer, session, "550 Directory unavailable");
                    } else {
                        session.currentVirtualDir = toVirtualPath(target);
                        sendReply(writer, session, "250 Directory changed to " + session.currentVirtualDir);
                    }
                }
                case "CDUP" -> {
                    Path current = resolveSafePath(session, ".");
                    if (current != null && !current.equals(rootDir) && current.getParent() != null
                            && current.getParent().startsWith(rootDir)) {
                        session.currentVirtualDir = toVirtualPath(current.getParent());
                    } else {
                        session.currentVirtualDir = "/";
                    }
                    sendReply(writer, session, "200 Command okay");
                }
                case "TYPE" -> {
                    if (param.equalsIgnoreCase("I") || param.equalsIgnoreCase("A")) {
                        session.transferType = param.toUpperCase();
                        sendReply(writer, session, "200 Type set to " + session.transferType);
                    } else {
                        sendReply(writer, session, "501 Syntax error in parameters");
                    }
                }
                case "PASV" -> {
                    session.closePassiveListener();
                    session.passiveListener = new ServerSocket(0);
                    session.passiveListener.setSoTimeout(20000);
                    int dataPort = session.passiveListener.getLocalPort();
                    int p1 = dataPort / 256;
                    int p2 = dataPort % 256;
                    String ip = serverLocalAddr.getHostAddress();
                    if (ip.contains(":") || ip.equals("0.0.0.0")) {
                        ip = "127.0.0.1";
                    }
                    String formattedIp = ip.replace('.', ',');
                    sendReply(writer, session, String.format("227 Entering Passive Mode (%s,%d,%d)", formattedIp, p1, p2));
                }
                case "PORT" -> {
                    session.closePassiveListener();
                    String[] nums = param.split(",");
                    if (nums.length != 6) {
                        sendReply(writer, session, "501 Syntax error in parameters");
                    } else {
                        String ip = nums[0] + "." + nums[1] + "." + nums[2] + "." + nums[3];
                        int p1 = Integer.parseInt(nums[4].trim());
                        int p2 = Integer.parseInt(nums[5].trim());
                        int portNum = p1 * 256 + p2;
                        session.activeClientDataAddress = new InetSocketAddress(ip, portNum);
                        sendReply(writer, session, "200 PORT command successful");
                    }
                }
                case "LIST" -> handleListCommand(param, writer, session);
                case "RETR" -> handleRetrCommand(param, writer, session);
                case "STOR" -> handleStorCommand(param, writer, session);
                case "DELE" -> {
                    if (!session.canWrite) {
                        sendReply(writer, session, "550 Permission denied");
                        break;
                    }
                    Path file = resolveSafePath(session, param);
                    if (file == null || !Files.exists(file) || Files.isDirectory(file)) {
                        sendReply(writer, session, "550 File unavailable");
                    } else {
                        Files.delete(file);
                        sendReply(writer, session, "250 File deleted");
                    }
                }
                case "MKD", "XMKD" -> {
                    if (!session.canWrite) {
                        sendReply(writer, session, "550 Permission denied");
                        break;
                    }
                    Path requestedDir = resolveSafePath(session, param);
                    if (requestedDir == null) {
                        sendReply(writer, session, "550 Cannot create directory");
                    } else {
                        // Tự động đánh số (1), (2)... nếu thư mục đã tồn tại trên Server
                        Path parent = requestedDir.getParent() != null ? requestedDir.getParent() : rootDir;
                        Path finalDir = resolveUniquePath(parent, requestedDir.getFileName().toString(), true);
                        Files.createDirectories(finalDir);
                        sendReply(writer, session, "257 \"" + finalDir.getFileName().toString() + "\" created");
                    }
                }
                case "RMD", "XRMD" -> {
                    if (!session.canWrite) {
                        sendReply(writer, session, "550 Permission denied");
                        break;
                    }
                    Path dir = resolveSafePath(session, param);
                    if (dir == null || !Files.exists(dir) || !Files.isDirectory(dir) || dir.equals(rootDir)) {
                        sendReply(writer, session, "550 Remove directory failed");
                    } else {
                        try {
                            Files.delete(dir);
                            sendReply(writer, session, "250 Directory removed");
                        } catch (DirectoryNotEmptyException ex) {
                            sendReply(writer, session, "550 Remove directory failed (Directory not empty)");
                        }
                    }
                }
                case "RNFR" -> {
                    if (!session.canWrite) {
                        sendReply(writer, session, "550 Permission denied");
                        break;
                    }
                    Path source = resolveSafePath(session, param);
                    if (source == null || !Files.exists(source) || source.equals(rootDir)) {
                        sendReply(writer, session, "550 File unavailable");
                    } else {
                        session.pendingRenameSource = source;
                        sendReply(writer, session, "350 Ready for RNTO");
                    }
                }
                case "RNTO" -> {
                    if (!session.canWrite) {
                        sendReply(writer, session, "550 Permission denied");
                        break;
                    }
                    if (session.pendingRenameSource == null) {
                        sendReply(writer, session, "503 Bad sequence of commands (Send RNFR first)");
                        break;
                    }
                    Path dest = resolveSafePath(session, param);
                    if (dest == null) {
                        sendReply(writer, session, "550 Access denied");
                    } else {
                        // Nếu tên mới bị trùng với một file khác thì tự động thêm số (1), (2)...
                        boolean isDir = Files.isDirectory(session.pendingRenameSource);
                        if (Files.exists(dest) && !dest.equals(session.pendingRenameSource)) {
                            Path parent = dest.getParent() != null ? dest.getParent() : rootDir;
                            dest = resolveUniquePath(parent, dest.getFileName().toString(), isDir);
                        }
                        Files.move(session.pendingRenameSource, dest, StandardCopyOption.REPLACE_EXISTING);
                        session.pendingRenameSource = null;
                        sendReply(writer, session, "250 Rename successful (Saved as " + dest.getFileName() + ")");
                    }
                }
                case "SYST" -> sendReply(writer, session, "215 UNIX Type: L8 (Java FTP Server)");
                case "NOOP" -> sendReply(writer, session, "200 Command okay");
                case "QUIT" -> {
                    sendReply(writer, session, "221 Goodbye");
                    return false;
                }
                default -> sendReply(writer, session, "502 Command not implemented");
            }
            return true;
        }

        private Socket openDataSocket(FtpSession session) throws IOException {
            if (session.passiveListener != null) {
                Socket s = session.passiveListener.accept();
                session.closePassiveListener();
                return s;
            } else if (session.activeClientDataAddress != null) {
                Socket s = new Socket();
                s.connect(session.activeClientDataAddress, 10000);
                session.activeClientDataAddress = null;
                return s;
            }
            return null;
        }

        private void handleListCommand(String param, BufferedWriter writer, FtpSession session) throws IOException {
            Path dir = param.isEmpty() ? resolveSafePath(session, ".") : resolveSafePath(session, param);
            if (dir == null || !Files.exists(dir) || !Files.isDirectory(dir)) {
                sendReply(writer, session, "550 Directory unavailable");
                return;
            }
            sendReply(writer, session, "150 Opening data connection");
            try (Socket dataSocket = openDataSocket(session)) {
                if (dataSocket == null) {
                    sendReply(writer, session, "425 Can't open data connection");
                    return;
                }
                BufferedWriter dataOut = new BufferedWriter(new OutputStreamWriter(dataSocket.getOutputStream(), StandardCharsets.UTF_8));
                File[] files = dir.toFile().listFiles();
                SimpleDateFormat df = new SimpleDateFormat("MMM dd HH:mm", Locale.ENGLISH);
                if (files != null) {
                    Arrays.sort(files, Comparator.comparing(File::isFile).thenComparing(File::getName));
                    for (File f : files) {
                        String perms = f.isDirectory() ? "drwxr-xr-x" : "-rw-r--r--";
                        long size = f.isDirectory() ? 0 : f.length();
                        String dateStr = df.format(new Date(f.lastModified()));
                        String line = String.format("%s 1 user group %8d %s %s\r\n", perms, size, dateStr, f.getName());
                        dataOut.write(line);
                    }
                }
                dataOut.flush();
            } catch (IOException ex) {
                sendReply(writer, session, "426 Connection closed; transfer aborted");
                return;
            }
            sendReply(writer, session, "226 Transfer complete");
        }

        private void handleRetrCommand(String param, BufferedWriter writer, FtpSession session) throws IOException {
            Path file = resolveSafePath(session, param);
            if (file == null) {
                sendReply(writer, session, "550 Access denied (Path Traversal blocked)");
                return;
            }
            if (!Files.exists(file) || Files.isDirectory(file)) {
                sendReply(writer, session, "550 File unavailable");
                return;
            }
            sendReply(writer, session, "150 Opening data connection");
            try (Socket dataSocket = openDataSocket(session)) {
                if (dataSocket == null) {
                    sendReply(writer, session, "425 Can't open data connection");
                    return;
                }
                try (InputStream fileIn = Files.newInputStream(file);
                     OutputStream dataOut = dataSocket.getOutputStream()) {
                    fileIn.transferTo(dataOut);
                    dataOut.flush();
                }
            } catch (IOException ex) {
                sendReply(writer, session, "426 Connection closed; transfer aborted");
                return;
            }
            sendReply(writer, session, "226 Transfer complete");
        }

        private void handleStorCommand(String param, BufferedWriter writer, FtpSession session) throws IOException {
            if (!session.canWrite) {
                sendReply(writer, session, "550 Permission denied");
                return;
            }
            Path requestedFile = resolveSafePath(session, param);
            if (requestedFile == null || Files.isDirectory(requestedFile)) {
                sendReply(writer, session, "550 Access denied");
                return;
            }

            // TÍNH NĂNG MỚI: Nếu file đích trên Server đã tồn tại, tự động thêm số (1), (2)...
            Path parentDir = requestedFile.getParent() != null ? requestedFile.getParent() : rootDir;
            Path targetFile = resolveUniquePath(parentDir, requestedFile.getFileName().toString(), false);
            if (!targetFile.equals(requestedFile)) {
                serverLogger.log("[AUTO-NUMBER STOR] File '" + requestedFile.getFileName()
                        + "' đã tồn tại -> Server tự động lưu thành '" + targetFile.getFileName() + "'");
            }

            sendReply(writer, session, "150 Opening data connection (Saving as " + targetFile.getFileName() + ")");
            try (Socket dataSocket = openDataSocket(session)) {
                if (dataSocket == null) {
                    sendReply(writer, session, "425 Can't open data connection");
                    return;
                }
                try (InputStream dataIn = dataSocket.getInputStream();
                     OutputStream fileOut = Files.newOutputStream(targetFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    dataIn.transferTo(fileOut);
                    fileOut.flush();
                }
            } catch (IOException ex) {
                sendReply(writer, session, "426 Connection closed; transfer aborted");
                return;
            }
            sendReply(writer, session, "226 Transfer complete (" + targetFile.getFileName() + ")");
        }

        /**
         * Chuẩn hóa đường dẫn & Chống tấn công Path Traversal (Slide 29 - Mục 2 & 3)
         * Không bao giờ cho phép truy cập ra ngoài thư mục FtpRoot/
         */
        private Path resolveSafePath(FtpSession session, String rawInputPath) {
            if (rawInputPath == null || rawInputPath.isEmpty()) {
                return null;
            }
            try {
                // Chặn đường dẫn kiểu ổ đĩa Windows (C:\...) hoặc ký tự nguy hiểm
                if (rawInputPath.contains(":")) {
                    return null;
                }
                String normalizedInput = rawInputPath.replace('\\', '/');
                Path baseDir;
                if (normalizedInput.startsWith("/")) {
                    baseDir = rootDir;
                    normalizedInput = normalizedInput.substring(1);
                } else {
                    String relCurrent = session.currentVirtualDir.startsWith("/")
                            ? session.currentVirtualDir.substring(1)
                            : session.currentVirtualDir;
                    baseDir = rootDir.resolve(relCurrent).normalize();
                }
                Path resolved = baseDir.resolve(normalizedInput).normalize();
                if (!resolved.startsWith(rootDir)) {
                    serverLogger.log("[SECURITY ALERT] Chặn hành vi Path Traversal thoát khỏi FTP Root: " + rawInputPath);
                    return null;
                }
                return resolved;
            } catch (InvalidPathException ex) {
                return null;
            }
        }

        private String toVirtualPath(Path realPath) {
            Path rel = rootDir.relativize(realPath.normalize());
            String s = rel.toString().replace('\\', '/');
            return s.isEmpty() ? "/" : "/" + s;
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> {
            FtpClientServerApp app = new FtpClientServerApp();
            app.setVisible(true);
        });
    }
}
