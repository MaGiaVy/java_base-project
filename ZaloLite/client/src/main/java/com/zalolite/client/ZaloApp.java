package com.zalolite.client;

import com.formdev.flatlaf.FlatLightLaf;
import com.zalolite.client.ui.LoginFrame;
import com.zalolite.client.ui.Theme;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;

/**
 * Điểm khởi chạy (entry point) của ứng dụng desktop ZaloLite Client.
 * Thiết lập giao diện FlatLaf (bo góc, màu chủ đạo, thanh cuộn, font chữ)
 * rồi mở màn hình đăng nhập {@link LoginFrame} trên luồng Swing EDT.
 */
public class ZaloApp {
    public static final Color BERRY_RED = Theme.PRIMARY_RED;

    public static void main(String[] args) {
        // Cài đặt giao diện FlatLightLaf hiện đại và các thuộc tính bo góc/màu sắc chung
        FlatLightLaf.setup();
        UIManager.put("Component.accentColor", Theme.PRIMARY_RED);
        UIManager.put("Component.focusColor", Theme.PRIMARY_PURPLE);
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 8);
        UIManager.put("defaultFont", Theme.uiFont(13f));
        // Khởi tạo cửa sổ LoginFrame trên luồng sự kiện Swing (EDT)
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
