package com.zalolite.client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Popup chọn nhanh 36 biểu tượng cảm xúc (emoji) đầy đủ màu sắc (Twemoji PNG) theo lưới 6x6.
 * Khi người dùng bấm vào một emoji, callback {@code onPick} chèn ký tự Unicode vào ô nhập tin nhắn.
 */
public class EmojiPicker extends JPopupMenu {
    public EmojiPicker(Consumer<String> onPick) {
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_PINK, 1),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        JPanel grid = new JPanel(new GridLayout(6, 6, 4, 4));
        grid.setBackground(Color.WHITE);
        for (String emoji : EmojiAssets.PICKER_EMOJIS) {
            ImageIcon ic = EmojiAssets.icon(emoji, 24);
            JButton btn = ic != null ? new JButton(ic) : new JButton(emoji);
            if (ic == null) btn.setFont(Theme.iconFont(18f));
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setOpaque(true);
            btn.setBackground(Color.WHITE);
            btn.setPreferredSize(new Dimension(38, 36));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    btn.setBackground(Theme.ITEM_SELECTED);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    btn.setBackground(Color.WHITE);
                }
            });
            btn.addActionListener(e -> {
                onPick.accept(emoji);
                setVisible(false);
            });
            grid.add(btn);
        }
        add(grid);
    }
}
