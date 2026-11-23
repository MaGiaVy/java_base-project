package com.zalolite.client;

import com.formdev.flatlaf.FlatLightLaf;
import com.zalolite.client.ui.LoginFrame;
import com.zalolite.client.ui.Theme;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;

public class ZaloApp {
    public static final Color BERRY_RED = Theme.PRIMARY_RED;

    public static void main(String[] args) {
        FlatLightLaf.setup();
        UIManager.put("Component.accentColor", Theme.PRIMARY_RED);
        UIManager.put("Component.focusColor", Theme.PRIMARY_PURPLE);
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 8);
        UIManager.put("defaultFont", Theme.uiFont(13f));
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
