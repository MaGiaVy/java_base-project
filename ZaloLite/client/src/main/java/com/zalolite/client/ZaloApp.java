package com.zalolite.client;

import com.formdev.flatlaf.FlatLightLaf;
import com.zalolite.client.ui.LoginFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;

public class ZaloApp {
    public static final Color ZALO_BLUE = new Color(0x0068FF);

    public static void main(String[] args) {
        FlatLightLaf.setup();
        UIManager.put("Component.accentColor", ZALO_BLUE);
        UIManager.put("Button.arc", 10);
        UIManager.put("Component.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
