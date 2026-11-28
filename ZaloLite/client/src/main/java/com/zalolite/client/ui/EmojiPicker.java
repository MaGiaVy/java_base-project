package com.zalolite.client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Popup chọn nhanh 36 biểu tượng cảm xúc (emoji) theo lưới 6x6.
 * Khi người dùng bấm vào một emoji, callback {@code onPick} chèn emoji đó vào ô nhập tin nhắn ở {@link ChatPanel}.
 */
public class EmojiPicker extends JPopupMenu {
    private static final String[] EMOJIS = {
            "😀", "😂", "🥰", "😍", "🤩", "😎",
            "😊", "😉", "😅", "🤣", "😢", "😭",
            "😮", "😱", "😡", "🤔", "😴", "🥳",
            "❤️", "🩷", "💜", "🔥", "✨", "🎉",
            "👍", "👎", "👏", "🙌", "🙏", "🤝",
            "🍓", "🍇", "☕", "🎂", "🎵", "💯"
    };

    public EmojiPicker(Consumer<String> onPick) {
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_PINK, 1),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));

        JPanel grid = new JPanel(new GridLayout(6, 6, 2, 2));
        grid.setBackground(Color.WHITE);
        for (String emoji : EMOJIS) {
            JButton btn = new JButton(emoji);
            btn.setFont(Theme.emojiFont(18f));
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setOpaque(true);
            btn.setBackground(Color.WHITE);
            btn.setPreferredSize(new Dimension(38, 34));
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
