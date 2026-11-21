package com.zalolite.client.ui;

import com.zalolite.client.Connection;
import com.zalolite.common.Packet;
import com.zalolite.common.PacketType;

import javax.swing.*;
import java.awt.*;

public class RegisterDialog extends JDialog {
    public RegisterDialog(Frame owner, String server) {
        super(owner, "Tạo tài khoản", true);
        JTextField phone = new JTextField();
        JTextField name = new JTextField();
        JTextField email = new JTextField();
        JPasswordField pass = new JPasswordField();
        JPasswordField pass2 = new JPasswordField();
        JLabel status = new JLabel(" ");
        status.setForeground(Color.RED);
        JButton ok = new JButton("Đăng ký");

        JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
        form.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        form.add(new JLabel("Số điện thoại"));
        form.add(phone);
        form.add(new JLabel("Tên hiển thị"));
        form.add(name);
        form.add(new JLabel("Email (để lấy lại mật khẩu)"));
        form.add(email);
        form.add(new JLabel("Mật khẩu (ít nhất 6 ký tự)"));
        form.add(pass);
        form.add(new JLabel("Nhập lại mật khẩu"));
        form.add(pass2);
        form.add(status);
        form.add(ok);
        setContentPane(form);
        pack();
        setSize(360, getHeight());
        setLocationRelativeTo(owner);

        ok.addActionListener(e -> {
            String p1 = new String(pass.getPassword());
            if (!p1.equals(new String(pass2.getPassword()))) {
                status.setText("Mật khẩu nhập lại không khớp");
                return;
            }
            ok.setEnabled(false);
            new SwingWorker<Packet, Void>() {
                @Override
                protected Packet doInBackground() throws Exception {
                    Object[] hp = LoginFrame.parseServer(server);
                    Connection conn = new Connection((String) hp[0], (int) hp[1]);
                    try {
                        return conn.request(new Packet(PacketType.REGISTER)
                                .put("phone", phone.getText().trim()).put("name", name.getText().trim())
                                .put("email", email.getText().trim()).put("password", p1));
                    } finally {
                        conn.close();
                    }
                }

                @Override
                protected void done() {
                    ok.setEnabled(true);
                    try {
                        Packet res = get();
                        if (res.isOk()) {
                            JOptionPane.showMessageDialog(RegisterDialog.this, "Đăng ký thành công, hãy đăng nhập.");
                            dispose();
                        } else {
                            status.setText(res.str("error"));
                        }
                    } catch (Exception ex) {
                        status.setText("Không kết nối được máy chủ");
                    }
                }
            }.execute();
        });
    }
}
