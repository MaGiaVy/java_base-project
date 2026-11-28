package com.zalolite.client.ui;

import com.zalolite.client.Connection;
import com.zalolite.common.Packet;
import com.zalolite.common.PacketType;

import javax.swing.*;
import java.awt.*;

/**
 * Hộp thoại quên mật khẩu và đặt lại mật khẩu qua mã OTP 6 chữ số.
 * Quy trình 2 bước qua kết nối TLS tạm (:9000):
 *  - Bước 1: gửi {@code OTP_REQUEST} (SĐT) -> server gửi OTP về email (hoặc in console nếu chưa cấu hình SMTP).
 *  - Bước 2: gửi {@code OTP_RESET_PASSWORD} (SĐT + mã OTP + mật khẩu mới).
 */
public class ForgotPasswordDialog extends JDialog {
    public ForgotPasswordDialog(Frame owner, String serverAddress) {
        super(owner, "Quên mật khẩu — OTP", true);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(Theme.SIDEBAR_BG);
        root.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));

        JLabel title = new JLabel("🍓 Khôi phục mật khẩu");
        title.setFont(Theme.emojiFont(Font.BOLD, 16f));
        title.setForeground(Theme.PRIMARY_RED);
        title.setAlignmentX(LEFT_ALIGNMENT);
        root.add(title);
        root.add(Box.createVerticalStrut(12));

        // Bước 1: Yêu cầu mã OTP
        Theme.RoundedPanel step1 = cardPanel("Bước 1: Nhận mã OTP");
        JTextField phoneField = new JTextField();
        phoneField.putClientProperty("JTextField.placeholderText", "Nhập số điện thoại đã đăng ký");
        JButton sendOtpBtn = Theme.primaryButton("Gửi mã OTP");
        JLabel otpStatus = statusLabel();

        step1.add(fieldLabel("Số điện thoại"));
        step1.add(phoneField);
        step1.add(sendOtpBtn);
        step1.add(otpStatus);
        root.add(step1);
        root.add(Box.createVerticalStrut(12));

        // Bước 2: Nhập OTP & mật khẩu mới (khóa cho đến khi bước 1 thành công)
        Theme.RoundedPanel step2 = cardPanel("Bước 2: Đặt lại mật khẩu");
        JTextField otpField = new JTextField();
        otpField.putClientProperty("JTextField.placeholderText", "Nhập mã OTP 6 chữ số");
        JPasswordField newPassField = new JPasswordField();
        JPasswordField confirmPassField = new JPasswordField();
        JLabel resetStatus = statusLabel();
        JButton resetBtn = Theme.primaryButton("Đặt lại mật khẩu");

        setStep2Enabled(false, otpField, newPassField, confirmPassField, resetBtn);

        step2.add(fieldLabel("Mã OTP (6 chữ số)"));
        step2.add(otpField);
        step2.add(fieldLabel("Mật khẩu mới (ít nhất 6 ký tự)"));
        step2.add(newPassField);
        step2.add(fieldLabel("Xác nhận mật khẩu mới"));
        step2.add(confirmPassField);
        step2.add(resetStatus);
        step2.add(resetBtn);
        root.add(step2);

        // [UI -> GỬI] Bấm nút "Gửi mã OTP": mở kết nối TLS tạm và gửi gói OTP_REQUEST
        sendOtpBtn.addActionListener(e -> {
            String phone = phoneField.getText().trim();
            if (phone.isEmpty()) {
                setStatus(otpStatus, "Nhập số điện thoại", true);
                return;
            }
            sendOtpBtn.setEnabled(false);
            otpStatus.setText(" ");
            // [GỬI] Yêu cầu server sinh mã OTP và gửi về email đã đăng ký
            Theme.runAsync(() -> sendOnce(serverAddress, new Packet(PacketType.OTP_REQUEST)
                    .put("phone", phone)), res -> {
                sendOtpBtn.setEnabled(true);
                if (res.isOk()) {
                    if (res.bool("consoleMode")) {
                        setStatus(otpStatus, "Kiểm tra console server để lấy mã OTP", false);
                    } else {
                        String masked = res.str("maskedEmail");
                        if (masked == null || masked.isBlank()) masked = res.str("email");
                        String msg = masked != null && !masked.isBlank()
                                ? "Đã gửi mã OTP tới " + masked
                                : "Đã gửi mã OTP tới email đăng ký";
                        setStatus(otpStatus, msg, false);
                    }
                    setStep2Enabled(true, otpField, newPassField, confirmPassField, resetBtn);
                    otpField.requestFocusInWindow();
                } else {
                    setStatus(otpStatus, msgOr(res.str("error"), "Không thể gửi mã OTP"), true);
                }
            }, err -> {
                sendOtpBtn.setEnabled(true);
                setStatus(otpStatus, err, true);
            });
        });

        // [UI -> GỬI] Bấm nút "Đặt lại mật khẩu": gửi gói OTP_RESET_PASSWORD kèm SĐT, mã OTP và mật khẩu mới
        resetBtn.addActionListener(e -> {
            String phone = phoneField.getText().trim();
            String code = otpField.getText().trim();
            String newPass = new String(newPassField.getPassword());
            String confirmPass = new String(confirmPassField.getPassword());
            if (phone.isEmpty()) {
                setStatus(resetStatus, "Nhập số điện thoại", true);
                return;
            }
            if (code.isEmpty()) {
                setStatus(resetStatus, "Nhập mã OTP", true);
                return;
            }
            if (newPass.length() < 6) {
                setStatus(resetStatus, "Mật khẩu mới phải có ít nhất 6 ký tự", true);
                return;
            }
            if (!newPass.equals(confirmPass)) {
                setStatus(resetStatus, "Mật khẩu xác nhận không khớp", true);
                return;
            }
            resetBtn.setEnabled(false);
            resetStatus.setText(" ");
            // [GỬI] Gửi mã OTP + mật khẩu mới qua TLS (:9000) để server xác thực và cập nhật mật khẩu
            Theme.runAsync(() -> sendOnce(serverAddress, new Packet(PacketType.OTP_RESET_PASSWORD)
                    .put("phone", phone)
                    .put("code", code)
                    .put("newPassword", newPass)), res -> {
                resetBtn.setEnabled(true);
                if (res.isOk()) {
                    JOptionPane.showMessageDialog(ForgotPasswordDialog.this,
                            "Đặt lại mật khẩu thành công. Hãy đăng nhập bằng mật khẩu mới.");
                    dispose();
                } else {
                    setStatus(resetStatus, msgOr(res.str("error"), "Đặt lại mật khẩu thất bại"), true);
                }
            }, err -> {
                resetBtn.setEnabled(true);
                setStatus(resetStatus, err, true);
            });
        });

        setContentPane(root);
        pack();
        setSize(420, getHeight() + 8);
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** [CỔNG/GIAO THỨC] Mở kết nối TLS tạm (:9000), gửi 1 gói tin yêu cầu rồi đóng ngay sau khi nhận phản hồi. */
    private static Packet sendOnce(String serverAddress, Packet packet) throws Exception {
        Object[] hp = LoginFrame.parseServer(serverAddress);
        Connection conn = new Connection((String) hp[0], (int) hp[1]);
        try {
            return conn.request(packet);
        } finally {
            conn.close();
        }
    }

    private static void setStep2Enabled(boolean enabled, JComponent... components) {
        for (JComponent c : components) c.setEnabled(enabled);
    }

    private static Theme.RoundedPanel cardPanel(String titleText) {
        Theme.RoundedPanel card = new Theme.RoundedPanel(16, Color.WHITE, null, Theme.BORDER_PINK);
        card.setLayout(new GridLayout(0, 1, 6, 5));
        card.setBorder(BorderFactory.createEmptyBorder(12, 16, 14, 16));
        card.setAlignmentX(LEFT_ALIGNMENT);
        JLabel title = new JLabel(titleText);
        title.setFont(Theme.uiFont(Font.BOLD, 13.5f));
        title.setForeground(Theme.PRIMARY_PURPLE);
        card.add(title);
        return card;
    }

    private static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.uiFont(Font.BOLD, 12f));
        l.setForeground(Theme.TEXT_DARK);
        return l;
    }

    private static JLabel statusLabel() {
        JLabel l = new JLabel(" ");
        l.setFont(Theme.uiFont(12f));
        l.setForeground(Theme.PRIMARY_RED_DARK);
        return l;
    }

    private static void setStatus(JLabel label, String msg, boolean error) {
        label.setForeground(error ? Theme.PRIMARY_RED_DARK : Theme.ONLINE_GREEN);
        label.setText(msg);
    }

    private static String msgOr(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
