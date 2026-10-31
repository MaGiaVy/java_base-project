package com.iot;

import javax.swing.*;

/**
 * Điểm khởi chạy ứng dụng Java Desktop IoT Client
 */
public class App {
    public static void main(String[] args) {
        // Áp dụng Look & Feel giao diện hệ thống
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
