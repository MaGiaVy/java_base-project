package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ỨNG DỤNG HỢP NHẤT TOÀN DIỆN (ALL-IN-ONE DASHBOARD)
 * Chứa Tab 1: FTP Client (Truyền tập tin) và Tab 2: FTP Server (Quản trị & Giám sát)
 * Thích hợp nhất khi báo cáo, trình chiếu demo trực tiếp cho Thầy Trần Minh Nhật
 */
public class FtpAllInOneApp extends JFrame {

    private final FtpServerPanel serverPanel;
    private final FtpClientPanel clientPanel;

    public FtpAllInOneApp() {
        setTitle("SGU FTP Suite - Hệ Thống FTP Client & Server Hoàn Chỉnh (RFC 959 TCP Socket)");
        setSize(1320, 840);
        setMinimumSize(new Dimension(1150, 720));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        Path rootDir = Paths.get("FtpRoot").toAbsolutePath().normalize();
        Path localDir = Paths.get("FtpClientLocal").toAbsolutePath().normalize();

        // Banner Header Tông Đỏ Ruby
        JPanel headerPanel = createHeaderBanner();
        add(headerPanel, BorderLayout.NORTH);

        // Khởi tạo các Panel
        int defaultServerPort = 2121; // Dùng 2121 để không đụng cổng 21 của IIS Windows đang chạy sẵn
        serverPanel = new FtpServerPanel(rootDir, defaultServerPort);
        clientPanel = new FtpClientPanel(localDir, defaultServerPort);

        // Tabbed Pane
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(RedTheme.FONT_HEADER);
        tabs.setBackground(Color.WHITE);

        tabs.addTab("1. Giao Diện FTP Client (Quản Lý & Truyền Tập Tin)", clientPanel);
        tabs.addTab("2. Giao Diện FTP Server (Multi-Client & Nhật Ký Hệ Thống)", serverPanel);

        add(tabs, BorderLayout.CENTER);

        // Tự động bật Server local và điền thông tin kết nối với cơ chế tự chuyển cổng nếu bận
        serverPanel.startServerWithFallback(defaultServerPort);
        int actualPort = serverPanel.getActualPort();
        clientPanel.setConnectionFields("127.0.0.1", actualPort, "student", "123456");

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                serverPanel.stopServer();
            }
        });
    }

    private JPanel createHeaderBanner() {
        JPanel banner = new JPanel(new BorderLayout());
        banner.setBackground(RedTheme.PRIMARY);
        banner.setBorder(new EmptyBorder(10, 16, 10, 16));

        JLabel lblTitle = new JLabel("TRƯỜNG ĐẠI HỌC SÀI GÒN (SGU) - ĐỒ ÁN LẬP TRÌNH MẠNG JAVA");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTitle.setForeground(Color.WHITE);

        JLabel lblSub = new JLabel("HỆ THỐNG FTP CLIENT - SERVER CHUẨN RFC 959 (KIỂM THỬ CHÉO CHỮ X) | Sinh viên: Vỹ");
        lblSub.setFont(RedTheme.FONT_BODY);
        lblSub.setForeground(new Color(254, 226, 226)); // Red-100

        JPanel textPanel = new JPanel(new GridLayout(2, 1, 2, 2));
        textPanel.setOpaque(false);
        textPanel.add(lblTitle);
        textPanel.add(lblSub);

        banner.add(textPanel, BorderLayout.WEST);
        return banner;
    }

    public static void main(String[] args) {
        System.out.println("=======================================================");
        System.out.println("  SGU FTP SUITE - GIAO DIỆN HỢP NHẤT (ALL-IN-ONE)");
        System.out.println("  Môn học: Lập trình mạng - SGU | Sinh viên: Vỹ");
        System.out.println("=======================================================");
        System.out.println("[1/2] Đang khởi tạo giao diện người dùng...");

        // Thiết lập giao diện hệ thống
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            try {
                FtpAllInOneApp app = new FtpAllInOneApp();
                app.setVisible(true);
                app.toFront();
                app.requestFocus();
                System.out.println("[2/2] Giao diện đồ họa đã mở thành công trên màn hình!");
                System.out.println("[INFO] Server đang lắng nghe tại Port 2121.");
                System.out.println("[INFO] Giữ cửa sổ console này để theo dõi tiến trình.");
            } catch (Exception e) {
                System.err.println("[LỖI KHỞI ĐỘNG GUI]: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}
