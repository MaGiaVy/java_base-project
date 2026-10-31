package ui;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Ứng dụng chạy ĐỘC LẬP FTP Client
 * Sử dụng cho Kịch bản 2: Kết nối vào Windows IIS FTP Server (Port 21) để nghiệm thu
 */
public class FtpClientApp extends JFrame {

    public FtpClientApp() {
        setTitle("SGU FTP Client (Standalone) - Trình Truyền Tập Tin Đa Năng");
        setSize(1100, 750);
        setMinimumSize(new Dimension(950, 600));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        Path localDir = Paths.get("FtpClientLocal").toAbsolutePath().normalize();
        int defaultPort = 21; // Mặc định kết nối vào IIS Port 21

        FtpClientPanel clientPanel = new FtpClientPanel(localDir, defaultPort);
        add(clientPanel, BorderLayout.CENTER);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            FtpClientApp app = new FtpClientApp();
            app.setVisible(true);
        });
    }
}
