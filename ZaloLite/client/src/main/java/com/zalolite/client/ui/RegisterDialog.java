package com.zalolite.client.ui;

import com.zalolite.client.Connection;
import com.zalolite.common.Packet;
import com.zalolite.common.PacketType;

import javax.swing.*;
import java.awt.*;

/**
 * Hộp thoại đăng ký tài khoản mới.
 * Mở kết nối TLS tạm tới server (:9000), gửi gói {@code REGISTER} và đóng kết nối sau khi nhận phản hồi.
 */
public class RegisterDialog extends JDialog {
    public RegisterDialog(Frame owner, String server) {
        super(owner, "ZaloLite Berry - Tạo tài khoản", true);
        JTextField phone = new JTextField();
        JTextField name = new JTextField();
        JTextField email = new JTextField();
        JPasswordField pass = new JPasswordField();
        JPasswordField pass2 = new JPasswordField();
        JLabel status = new JLabel(" ");
        status.setFont(Theme.uiFont(12f));
        status.setForeground(Theme.PRIMARY_RED_DARK);
        JButton ok = Theme.primaryButton("Đăng ký");

        JPanel form = new JPanel(new GridLayout(0, 1, 6, 6));
        form.setBackground(Theme.SIDEBAR_BG);
        form.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JLabel title = new JLabel("🍓 Tạo tài khoản ZaloLite Berry");
        title.setFont(Theme.emojiFont(Font.BOLD, 16f));
        title.setForeground(Theme.PRIMARY_RED);
        form.add(title);

        form.add(fieldLabel("Số điện thoại"));
        form.add(phone);
        form.add(fieldLabel("Tên hiển thị"));
        form.add(name);
        form.add(fieldLabel("Email (để lấy lại mật khẩu)"));
        form.add(email);
        form.add(fieldLabel("Mật khẩu (ít nhất 6 ký tự)"));
        form.add(pass);
        form.add(fieldLabel("Nhập lại mật khẩu"));
        form.add(pass2);
        form.add(status);
        form.add(ok);
        setContentPane(form);
        pack();
        setSize(380, getHeight() + 12);
        setLocationRelativeTo(owner);

        // [UI -> GỬI] Bấm nút "Đăng ký": mở kết nối TLS tạm (:9000) và gửi gói REGISTER chứa SĐT, tên, email, mật khẩu
        ok.addActionListener(e -> {
            String p1 = new String(pass.getPassword());
            if (!p1.equals(new String(pass2.getPassword()))) {
                status.setText("Mật khẩu nhập lại không khớp");
                return;
            }
            ok.setEnabled(false);
            status.setText(" ");
            Theme.runAsync(() -> {
                Object[] hp = LoginFrame.parseServer(server);
                // [CỔNG/GIAO THỨC] Tạo SSLSocket kết nối tạm tới cổng điều khiển server (mặc định :9000)
                Connection conn = new Connection((String) hp[0], (int) hp[1]);
                try {
                    // [GỬI] Gửi yêu cầu REGISTER và chờ server xác nhận
                    return conn.request(new Packet(PacketType.REGISTER)
                            .put("phone", phone.getText().trim())
                            .put("name", name.getText().trim())
                            .put("email", email.getText().trim())
                            .put("password", p1));
                } finally {
                    conn.close();
                }
            }, res -> {
                ok.setEnabled(true);
                if (res.isOk()) {
                    JOptionPane.showMessageDialog(RegisterDialog.this, "Đăng ký thành công, hãy đăng nhập.");
                    dispose();
                } else {
                    status.setText(res.str("error"));
                }
            }, err -> {
                ok.setEnabled(true);
                status.setText(err);
            });
        });
    }

    private static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.uiFont(Font.BOLD, 12f));
        l.setForeground(Theme.TEXT_DARK);
        return l;
    }
}
