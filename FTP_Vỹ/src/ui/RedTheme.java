package ui;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Hệ thống Design Tokens và giao diện tông màu Đỏ Ruby (#DC2626)
 * Tuân thủ quy chuẩn /ui-styling: trực quan, tương phản cao, hiện đại
 */
public class RedTheme {

    // Bảng màu chính (Palette Tokens)
    public static final Color PRIMARY = new Color(220, 38, 38);       // Red-600
    public static final Color PRIMARY_HOVER = new Color(185, 28, 28); // Red-700
    public static final Color PRIMARY_ACTIVE = new Color(153, 27, 27);// Red-800
    public static final Color SURFACE_RED = new Color(254, 242, 242); // Red-50
    public static final Color BORDER_RED = new Color(254, 202, 202);  // Red-200

    public static final Color BG_MAIN = new Color(248, 250, 252);     // Slate-50
    public static final Color BG_CARD = Color.WHITE;
    public static final Color BORDER_SOFT = new Color(226, 232, 240); // Slate-200

    public static final Color TEXT_PRIMARY = Color.BLACK;            // Đen tuyền 100% rõ nét
    public static final Color TEXT_MUTED = new Color(30, 41, 59);    // Slate-800 đen đậm

    public static final Color SUCCESS = new Color(21, 128, 61);       // Green-700
    public static final Color WARNING = new Color(180, 83, 9);        // Amber-700
    public static final Color ERROR = new Color(185, 28, 28);         // Red-700

    // Typography Fonts - Tăng kích thước và in đậm rõ nét
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 16);
    public static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_BODY = new Font("Segoe UI", Font.BOLD, 12);
    public static final Font FONT_BODY_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_MONO = new Font("Consolas", Font.BOLD, 13);

    public static JButton createPrimaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BODY_BOLD);
        btn.setForeground(Color.BLACK); // CHỮ MÀU ĐEN 100% THEO YÊU CẦU
        btn.setBackground(new Color(254, 202, 202)); // Nền đỏ nhạt nổi bật
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(true);
        btn.setOpaque(true);
        btn.setBorder(new CompoundBorder(new LineBorder(PRIMARY, 2, true), new EmptyBorder(6, 12, 6, 12)));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(new Color(252, 165, 165));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(new Color(254, 202, 202));
            }
        });
        return btn;
    }

    public static JButton createSecondaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BODY_BOLD);
        btn.setForeground(Color.BLACK); // CHỮ MÀU ĐEN 100%
        btn.setBackground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(true);
        btn.setOpaque(true);
        btn.setBorder(new CompoundBorder(new LineBorder(new Color(100, 116, 139), 1, true), new EmptyBorder(6, 12, 6, 12)));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(new Color(241, 245, 249));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(Color.WHITE);
            }
        });
        return btn;
    }

    public static JTextField createTextField(int columns) {
        JTextField tf = new JTextField(columns);
        tf.setFont(FONT_BODY);
        tf.setForeground(TEXT_PRIMARY);
        tf.setBackground(Color.WHITE);
        tf.setBorder(new CompoundBorder(new LineBorder(BORDER_SOFT, 1, true), new EmptyBorder(5, 8, 5, 8)));
        return tf;
    }

    public static void styleTable(JTable table) {
        table.setFont(FONT_BODY);
        table.setForeground(TEXT_PRIMARY);
        table.setRowHeight(26);
        table.setGridColor(BORDER_SOFT);
        table.setShowGrid(true);
        table.setSelectionBackground(SURFACE_RED);
        table.setSelectionForeground(PRIMARY_ACTIVE);

        JTableHeader header = table.getTableHeader();
        header.setFont(FONT_BODY_BOLD);
        header.setBackground(new Color(241, 245, 249)); // Slate-100
        header.setForeground(TEXT_PRIMARY);
        header.setBorder(new LineBorder(BORDER_SOFT, 1));
        header.setPreferredSize(new Dimension(header.getWidth(), 30));

        // Center align cột ngày và kích thước
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i = 0; i < table.getColumnCount(); i++) {
            if (table.getColumnName(i).contains("Kích Thước") || table.getColumnName(i).contains("Ngày") || table.getColumnName(i).contains("Port")) {
                table.getColumnModel().getColumn(i).setCellRenderer(centerRenderer);
            }
        }
    }

    public static JPanel createCardPanel() {
        JPanel p = new JPanel();
        p.setBackground(BG_CARD);
        p.setBorder(new CompoundBorder(new LineBorder(BORDER_SOFT, 1, true), new EmptyBorder(8, 8, 8, 8)));
        return p;
    }

    public static Border createSectionBorder(String title) {
        return BorderFactory.createTitledBorder(
                new LineBorder(BORDER_RED, 1, true),
                " " + title + " ",
                0,
                0,
                FONT_HEADER,
                PRIMARY
        );
    }
}
