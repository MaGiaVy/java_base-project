package com.zalolite.client.ui;

import com.zalolite.client.Connection;
import com.zalolite.client.ZaloApp;
import com.zalolite.common.Packet;
import com.zalolite.common.PacketType;
import com.zalolite.common.User;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Cửa sổ chính kiểu Zalo PC: thanh dọc xanh bên trái | danh sách hội thoại | khung chat.
 * Ngày 1: mới có khung; danh sách hội thoại và chat sẽ thêm từ Ngày 2-3.
 */
public class MainFrame extends JFrame {
    private final Connection conn;
    private final User me;

    public MainFrame(Connection conn, User me) {
        super("ZaloLite - " + me.name);
        this.conn = conn;
        this.me = me;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1000, 650);
        setLocationRelativeTo(null);

        JPanel nav = new JPanel();
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBackground(ZaloApp.ZALO_BLUE);
        nav.setPreferredSize(new Dimension(64, 0));
        JLabel avatar = new JLabel(initials(me.name), SwingConstants.CENTER);
        avatar.setOpaque(true);
        avatar.setBackground(Color.WHITE);
        avatar.setForeground(ZaloApp.ZALO_BLUE);
        avatar.setFont(avatar.getFont().deriveFont(Font.BOLD, 16f));
        avatar.setMaximumSize(new Dimension(44, 44));
        avatar.setAlignmentX(CENTER_ALIGNMENT);
        avatar.setToolTipText(me.name + " - " + me.phone);
        JButton logout = new JButton("⏻");
        logout.setToolTipText("Đăng xuất");
        logout.setAlignmentX(CENTER_ALIGNMENT);
        nav.add(Box.createVerticalStrut(16));
        nav.add(avatar);
        nav.add(Box.createVerticalGlue());
        nav.add(logout);
        nav.add(Box.createVerticalStrut(16));

        JPanel convList = new JPanel(new BorderLayout());
        convList.setPreferredSize(new Dimension(300, 0));
        convList.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0xDDDDDD)));
        convList.add(new JLabel("  Danh sách hội thoại (Ngày 2-3)"), BorderLayout.NORTH);

        JLabel welcome = new JLabel("<html><center><h2>Chào mừng đến với ZaloLite!</h2>Xin chào <b>"
                + escape(me.name) + "</b></center></html>", SwingConstants.CENTER);

        add(nav, BorderLayout.WEST);
        JPanel center = new JPanel(new BorderLayout());
        center.add(convList, BorderLayout.WEST);
        center.add(welcome, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        logout.addActionListener(e -> {
            try {
                conn.request(new Packet(PacketType.LOGOUT));
            } catch (Exception ignored) {
            }
            conn.close();
            dispose();
            new LoginFrame().setVisible(true);
        });
        conn.setOnDisconnect(() -> {
            JOptionPane.showMessageDialog(this, "Mất kết nối tới máy chủ.");
            dispose();
            new LoginFrame().setVisible(true);
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                conn.close();
            }
        });
    }

    static String initials(String name) {
        String[] parts = name.trim().split("\\s+");
        String last = parts[parts.length - 1];
        return last.isEmpty() ? "?" : last.substring(0, 1).toUpperCase();
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
