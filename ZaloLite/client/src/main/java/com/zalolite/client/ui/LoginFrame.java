package com.zalolite.client.ui;

import com.zalolite.client.Connection;
import com.zalolite.client.ZaloApp;
import com.zalolite.common.Packet;
import com.zalolite.common.PacketType;
import com.zalolite.common.User;

import javax.swing.*;
import java.awt.*;

/** Màn hình đăng nhập kiểu Zalo PC. */
public class LoginFrame extends JFrame {
    private final JTextField serverField = new JTextField("localhost:9000");
    private final JTextField phoneField = new JTextField();
    private final JPasswordField passField = new JPasswordField();
    private final JButton loginBtn = new JButton("Đăng nhập");
    private final JLabel status = new JLabel(" ");

    public LoginFrame() {
        super("ZaloLite - Đăng nhập");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(400, 520);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(new Color(0xE8F3FF));
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        JLabel logo = new JLabel("Zalo");
        logo.setFont(logo.getFont().deriveFont(Font.BOLD, 40f));
        logo.setForeground(ZaloApp.ZALO_BLUE);
        logo.setAlignmentX(CENTER_ALIGNMENT);
        JLabel sub = new JLabel("Đăng nhập tài khoản ZaloLite");
        sub.setAlignmentX(CENTER_ALIGNMENT);

        phoneField.putClientProperty("JTextField.placeholderText", "Số điện thoại");
        passField.putClientProperty("JTextField.placeholderText", "Mật khẩu");
        serverField.putClientProperty("JTextField.placeholderText", "Máy chủ (host:port)");

        loginBtn.setBackground(ZaloApp.ZALO_BLUE);
        loginBtn.setForeground(Color.WHITE);
        JButton registerBtn = new JButton("Tạo tài khoản mới");
        registerBtn.setBorderPainted(false);
        registerBtn.setContentAreaFilled(false);
        registerBtn.setForeground(ZaloApp.ZALO_BLUE);
        status.setForeground(Color.RED);

        card.add(logo);
        card.add(sub);
        card.add(Box.createVerticalStrut(20));
        for (JComponent c : new JComponent[]{phoneField, passField, serverField, loginBtn}) {
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            c.setAlignmentX(CENTER_ALIGNMENT);
            card.add(c);
            card.add(Box.createVerticalStrut(10));
        }
        status.setAlignmentX(CENTER_ALIGNMENT);
        registerBtn.setAlignmentX(CENTER_ALIGNMENT);
        card.add(status);
        card.add(registerBtn);
        card.setPreferredSize(new Dimension(320, 400));
        root.add(card);
        setContentPane(root);

        loginBtn.addActionListener(e -> doLogin());
        passField.addActionListener(e -> doLogin());
        registerBtn.addActionListener(e -> new RegisterDialog(this, serverField.getText()).setVisible(true));
    }

    /** Tách "host:port"; mặc định cổng 9000. */
    static Object[] parseServer(String s) {
        s = s.trim();
        int i = s.lastIndexOf(':');
        if (i < 0) return new Object[]{s, 9000};
        return new Object[]{s.substring(0, i), Integer.parseInt(s.substring(i + 1))};
    }

    private void doLogin() {
        String phone = phoneField.getText().trim();
        String pass = new String(passField.getPassword());
        if (phone.isEmpty() || pass.isEmpty()) {
            status.setText("Nhập số điện thoại và mật khẩu");
            return;
        }
        loginBtn.setEnabled(false);
        status.setText(" ");
        String server = serverField.getText();
        new SwingWorker<Object[], Void>() {
            @Override
            protected Object[] doInBackground() throws Exception {
                Object[] hp = parseServer(server);
                Connection conn = new Connection((String) hp[0], (int) hp[1]);
                Packet res = conn.request(new Packet(PacketType.LOGIN).put("phone", phone).put("password", pass));
                if (!res.isOk()) {
                    conn.close();
                    throw new Exception(res.str("error"));
                }
                return new Object[]{conn, res.get("user", User.class)};
            }

            @Override
            protected void done() {
                loginBtn.setEnabled(true);
                try {
                    Object[] r = get();
                    new MainFrame((Connection) r[0], (User) r[1]).setVisible(true);
                    dispose();
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    status.setText(c.getMessage() == null ? "Không kết nối được máy chủ" : c.getMessage());
                }
            }
        }.execute();
    }
}
