import javax.swing.*;
import java.awt.*;

/**
 * LoginFrame.java - Form đăng nhập
 * Username: admin | Password: 123
 * Đăng nhập thành công → mở MainFrame
 */
public class LoginFrame extends JFrame {

    private JTextField txtUsername;
    private JPasswordField txtPassword;

    public LoginFrame() {
        initUI();
    }

    private void initUI() {
        setTitle("Đăng Nhập");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 280);
        setLocationRelativeTo(null);
        setResizable(false);
        getContentPane().setBackground(Constants.BACKGROUND);
        setLayout(new BorderLayout(0, 0));

        // --- Title Panel ---
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 10));
        titlePanel.setBackground(Constants.PRIMARY);
        JLabel lblTitle = new JLabel("ĐĂNG NHẬP HỆ THỐNG");
        lblTitle.setFont(Constants.FONT_TITLE);
        lblTitle.setForeground(Color.WHITE);
        titlePanel.add(lblTitle);
        add(titlePanel, BorderLayout.NORTH);

        // --- Form Panel ---
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(Constants.BACKGROUND);
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 40, 10, 40));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Tên đăng nhập
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        formPanel.add(UIHelper.createLabel("Tên đăng nhập:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        txtUsername = UIHelper.createTextField(15);
        formPanel.add(txtUsername, gbc);

        // Mật khẩu
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        formPanel.add(UIHelper.createLabel("Mật khẩu:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        txtPassword = new JPasswordField(15);
        txtPassword.setFont(Constants.FONT_BODY);
        txtPassword.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Constants.BORDER),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        formPanel.add(txtPassword, gbc);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btnPanel.setOpaque(false);

        JButton btnLogin = UIHelper.createStyledButton("Đăng nhập", Constants.SUCCESS);
        JButton btnExit = UIHelper.createStyledButton("Thoát", Constants.DANGER);

        btnLogin.addActionListener(e -> dangNhap());
        btnExit.addActionListener(e -> System.exit(0));

        // Enter key để đăng nhập
        txtPassword.addActionListener(e -> dangNhap());
        txtUsername.addActionListener(e -> txtPassword.requestFocus());

        btnPanel.add(btnLogin);
        btnPanel.add(btnExit);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(15, 8, 8, 8);
        formPanel.add(btnPanel, gbc);

        add(formPanel, BorderLayout.CENTER);
    }

    private void dangNhap() {
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();

        if (username.equals("admin") && password.equals("123")) {
            JOptionPane.showMessageDialog(this,
                    "Đăng nhập thành công!",
                    "Thành công", JOptionPane.INFORMATION_MESSAGE);

            // Đóng form đăng nhập, mở MainFrame
            dispose();
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        } else {
            JOptionPane.showMessageDialog(this,
                    "Sai tên đăng nhập hoặc mật khẩu!",
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            txtPassword.setText("");
            txtPassword.requestFocus();
        }
    }

    // ============================================================
    // MAIN - Điểm khởi chạy của ứng dụng
    // ============================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            } catch (Exception e) {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ex) {
                    // Dùng Look & Feel mặc định
                }
            }

            LoginFrame login = new LoginFrame();
            login.setVisible(true);
        });
    }
}
