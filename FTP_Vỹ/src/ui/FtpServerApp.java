package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Ứng dụng chạy ĐỘC LẬP FTP Server
 * Sử dụng cho Kịch bản 3: Để FileZilla Client từ máy khác hoặc cùng máy kết nối vào nghiệm thu
 */
public class FtpServerApp extends JFrame {

    private final FtpServerPanel serverPanel;

    public FtpServerApp() {
        setTitle("SGU FTP Server (Standalone) - Cổng 2121 / 21");
        setSize(950, 680);
        setMinimumSize(new Dimension(800, 500));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        Path rootDir = Paths.get("FtpRoot").toAbsolutePath().normalize();
        int port = 2121; // Mặc định 2121 để tránh đụng IIS port 21

        serverPanel = new FtpServerPanel(rootDir, port);
        add(serverPanel, BorderLayout.CENTER);

        serverPanel.startServerWithFallback(port);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                serverPanel.stopServer();
            }
        });
    }

    public static void main(String[] args) {
        System.out.println("=======================================================");
        System.out.println("  SGU FTP SERVER - STANDALONE (CỔNG 2121 / 21)");
        System.out.println("  Sẵn sàng để FileZilla kết nối nghiệm thu (Kịch bản 3)");
        System.out.println("=======================================================");
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            try {
                FtpServerApp app = new FtpServerApp();
                app.setVisible(true);
                app.toFront();
                app.requestFocus();
                System.out.println("[GUI] Cửa sổ FTP Server đã mở thành công!");
            } catch (Exception e) {
                System.err.println("[LỖI] " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}
