import java.nio.file.Paths;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Điểm khởi chạy chính của ứng dụng Mail Client Java (khớp cấu trúc thư mục mục 10 của giáo trình). */
public class EmailClientApp {
    public static void main(String[] args) {
        try {
            // Thiết lập giao diện hệ thống đồng bộ và thẩm mỹ
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        MailLog log = new MailLog(Paths.get("logs", "mail-client.log"));
        SwingUtilities.invokeLater(() -> new MainFrame(log).setVisible(true));
    }
}
