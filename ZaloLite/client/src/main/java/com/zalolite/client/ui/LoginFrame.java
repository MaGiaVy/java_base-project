package com.zalolite.client.ui;

import com.zalolite.client.Connection;
import com.zalolite.client.LanDiscovery;
import com.zalolite.common.Packet;
import com.zalolite.common.PacketType;
import com.zalolite.common.User;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Màn hình đăng nhập chính của ZaloLite Client.
 * Hỗ trợ:
 *  - Nhập địa chỉ server (mặc định localhost:9000) hoặc bấm nút "LAN" để dò server qua UDP ({@link LanDiscovery}).
 *  - Đăng nhập qua TLS ({@code LOGIN}), mở {@link MainFrame} khi thành công.
 *  - Mở hộp thoại đăng ký ({@link RegisterDialog}) và quên mật khẩu OTP ({@link ForgotPasswordDialog}).
 */
public class LoginFrame extends JFrame {
    private final JTextField serverField = new JTextField("localhost:9000");
    private final JTextField phoneField = new JTextField();
    private final JPasswordField passField = new JPasswordField();
    private final JButton loginBtn = Theme.primaryButton("Đăng nhập");
    private final JButton lanBtn = Theme.softButton("🔍 LAN");
    private final JLabel status = new JLabel(" ");
    private int discoveredFilePort = -1;

    public LoginFrame() {
        super("ZaloLite Berry - Đăng nhập");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(420, 540);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(Theme.SIDEBAR_BG);

        Theme.RoundedPanel card = new Theme.RoundedPanel(22, Color.WHITE, null, Theme.BORDER_PINK);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(28, 30, 26, 30));

        JLabel badge = new JLabel("🍓", SwingConstants.CENTER);
        badge.setFont(Theme.emojiFont(36f));
        badge.setAlignmentX(CENTER_ALIGNMENT);

        JLabel logo = new JLabel("ZaloLite Berry");
        logo.setFont(Theme.uiFont(Font.BOLD, 28f));
        logo.setForeground(Theme.PRIMARY_RED);
        logo.setAlignmentX(CENTER_ALIGNMENT);

        JLabel sub = new JLabel("Đăng nhập tài khoản của bạn");
        sub.setFont(Theme.uiFont(13f));
        sub.setForeground(Theme.TEXT_MUTED);
        sub.setAlignmentX(CENTER_ALIGNMENT);

        phoneField.putClientProperty("JTextField.placeholderText", "Số điện thoại");
        passField.putClientProperty("JTextField.placeholderText", "Mật khẩu");
        serverField.putClientProperty("JTextField.placeholderText", "Máy chủ (host:port)");

        JButton registerBtn = new JButton("Tạo tài khoản mới");
        registerBtn.setFont(Theme.uiFont(Font.BOLD, 13f));
        registerBtn.setBorderPainted(false);
        registerBtn.setContentAreaFilled(false);
        registerBtn.setForeground(Theme.PRIMARY_PURPLE);
        registerBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        status.setFont(Theme.uiFont(12f));
        status.setForeground(Theme.PRIMARY_RED_DARK);

        card.add(badge);
        card.add(Box.createVerticalStrut(4));
        card.add(logo);
        card.add(Box.createVerticalStrut(4));
        card.add(sub);
        card.add(Box.createVerticalStrut(22));
        JPanel serverRow = new JPanel(new BorderLayout(6, 0));
        serverRow.setOpaque(false);
        serverRow.add(serverField, BorderLayout.CENTER);
        lanBtn.setToolTipText("Tự động tìm máy chủ ZaloLite trong mạng LAN (UDP Multicast)");
        lanBtn.setPreferredSize(new Dimension(78, 40));
        serverRow.add(lanBtn, BorderLayout.EAST);

        for (JComponent c : new JComponent[]{phoneField, passField, serverRow, loginBtn}) {
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            c.setAlignmentX(CENTER_ALIGNMENT);
            card.add(c);
            card.add(Box.createVerticalStrut(10));
        }
        JButton forgotBtn = new JButton("Quên mật khẩu?");
        forgotBtn.setFont(Theme.uiFont(12.5f));
        forgotBtn.setBorderPainted(false);
        forgotBtn.setContentAreaFilled(false);
        forgotBtn.setForeground(Theme.TEXT_MUTED);
        forgotBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel linksRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        linksRow.setOpaque(false);
        linksRow.add(registerBtn);
        linksRow.add(forgotBtn);
        linksRow.setAlignmentX(CENTER_ALIGNMENT);

        status.setAlignmentX(CENTER_ALIGNMENT);
        card.add(status);
        card.add(Box.createVerticalStrut(4));
        card.add(linksRow);
        card.setPreferredSize(new Dimension(340, 448));

        root.add(card);
        setContentPane(root);

        // [UI -> GỬI] Gắn sự kiện nút Đăng nhập, phím Enter ở ô mật khẩu, nút dò server LAN và các hộp thoại phụ
        loginBtn.addActionListener(e -> doLogin());
        passField.addActionListener(e -> doLogin());
        lanBtn.addActionListener(e -> discoverLan());
        registerBtn.addActionListener(e -> new RegisterDialog(this, serverField.getText()).setVisible(true));
        forgotBtn.addActionListener(e -> new ForgotPasswordDialog(this, serverField.getText()).setVisible(true));
    }

    /** [UI -> GỬI] Bấm nút "LAN": phát gói UDP thăm dò cổng 9009 qua {@link LanDiscovery} để tự điền host:port. */
    private void discoverLan() {
        lanBtn.setEnabled(false);
        status.setForeground(Theme.TEXT_MUTED);
        status.setText("Đang tìm máy chủ trong LAN...");
        Theme.runAsync(
                // [CỔNG/GIAO THỨC] Dò server qua UDP Multicast/Broadcast cổng 9009 trong 1.5 giây
                () -> LanDiscovery.discover(1500),
                found -> {
                    lanBtn.setEnabled(true);
                    if (found.isEmpty()) {
                        status.setForeground(Theme.PRIMARY_RED_DARK);
                        status.setText("Không thấy máy chủ nào trong LAN");
                        return;
                    }
                    LanDiscovery.DiscoveredServer chosen = found.get(0);
                    if (found.size() > 1) {
                        Object pick = JOptionPane.showInputDialog(this,
                                "Chọn máy chủ tìm thấy:", "Tìm máy chủ LAN",
                                JOptionPane.PLAIN_MESSAGE, null, found.toArray(), found.get(0));
                        if (pick instanceof LanDiscovery.DiscoveredServer ds) chosen = ds;
                    }
                    serverField.setText(chosen.host() + ":" + chosen.tcpPort());
                    discoveredFilePort = chosen.filePort();
                    status.setForeground(Theme.ONLINE_GREEN);
                    status.setText("Đã tìm thấy: " + chosen.host() + ":" + chosen.tcpPort());
                },
                err -> {
                    lanBtn.setEnabled(true);
                    status.setForeground(Theme.PRIMARY_RED_DARK);
                    status.setText(err);
                }
        );
    }

    /** Tách "host:port"; mặc định cổng 9000. */
    static Object[] parseServer(String s) {
        s = s.trim();
        int i = s.lastIndexOf(':');
        if (i < 0) return new Object[]{s, 9000};
        return new Object[]{s.substring(0, i), Integer.parseInt(s.substring(i + 1))};
    }

    /** [UI -> GỬI] Xử lý đăng nhập: mở kết nối TLS (:9000), gửi gói LOGIN và mở {@link MainFrame} khi hợp lệ. */
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
        int fPort = discoveredFilePort;
        Theme.runAsync(() -> {
            Object[] hp = parseServer(server);
            int tcpPort = (int) hp[1];
            // [CỔNG/GIAO THỨC] Mở SSLSocket tới cổng điều khiển (mặc định :9000) và ghi nhớ cổng FileServer (:9001)
            Connection conn = new Connection((String) hp[0], tcpPort, fPort > 0 ? fPort : tcpPort + 1);
            try {
                // [GỬI] Gửi gói LOGIN (phone + password) qua kênh TLS và chờ phản hồi
                Packet res = conn.request(new Packet(PacketType.LOGIN).put("phone", phone).put("password", pass));
                if (!res.isOk()) {
                    conn.close();
                    throw new Exception(res.str("error"));
                }
                return new Object[]{conn, res.get("user", User.class)};
            } catch (Exception ex) {
                conn.close();
                throw ex;
            }
        }, r -> {
            loginBtn.setEnabled(true);
            new MainFrame((Connection) r[0], (User) r[1]).setVisible(true);
            dispose();
        }, err -> {
            loginBtn.setEnabled(true);
            status.setText(err);
        });
    }
}
