import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;

/**
 * UIHelper.java - Helper methods tạo styled components
 * Tái sử dụng cho buttons, tables, panels, text fields, labels.
 */
public class UIHelper {

    /**
     * Tạo nút bấm có màu nền tùy chỉnh, rounded corners, hover effect.
     * Dùng custom paintComponent để hoạt động đúng trên MỌI Look & Feel.
     */
    public static JButton createStyledButton(String text, Color bgColor) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Đổi màu nền theo trạng thái (nhấn / hover / bình thường)
                if (getModel().isPressed()) {
                    g2.setColor(bgColor.darker().darker());
                } else if (getModel().isRollover()) {
                    g2.setColor(bgColor.darker());
                } else {
                    g2.setColor(bgColor);
                }

                // Vẽ hình chữ nhật bo góc
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();

                // Vẽ text lên trên nền
                super.paintComponent(g);
            }
        };
        btn.setForeground(Color.WHITE);
        btn.setFont(Constants.FONT_BUTTON);
        btn.setContentAreaFilled(false); // Không vẽ nền mặc định
        btn.setBorderPainted(false); // Không vẽ viền mặc định
        btn.setFocusPainted(false); // Không vẽ viền focus
        btn.setOpaque(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        return btn;
    }

    /**
     * Tạo JTable với header xanh, alternate row colors, row height 28px.
     */
    public static void styleTable(JTable table) {
        table.setRowHeight(Constants.ROW_HEIGHT);
        table.setFont(Constants.FONT_BODY);
        table.setSelectionBackground(Constants.TABLE_SELECTED);
        table.setSelectionForeground(Constants.TEXT_DARK);
        table.setGridColor(Constants.BORDER);
        table.setShowGrid(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoCreateRowSorter(true); // Click header để sắp xếp

        // Custom header renderer: nền xanh, chữ trắng, font bold
        table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                label.setBackground(Constants.PRIMARY);
                label.setForeground(Color.WHITE);
                label.setFont(Constants.FONT_HEADER);
                label.setHorizontalAlignment(JLabel.CENTER);
                label.setOpaque(true);
                label.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, Constants.PRIMARY_DARK));
                return label;
            }
        });

        // Alternate row colors: trắng / xám nhạt
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : Constants.TABLE_ALT_ROW);
                }
                return c;
            }
        });
    }

    /**
     * Tạo JPanel với TitledBorder + nền trắng.
     */
    public static JPanel createTitledPanel(String title) {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Constants.BORDER), title,
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                Constants.FONT_HEADER, Constants.TEXT_DARK));
        panel.setBackground(Color.WHITE);
        return panel;
    }

    /**
     * Tạo JTextField với border styling.
     */
    public static JTextField createTextField(int columns) {
        JTextField field = new JTextField(columns);
        field.setFont(Constants.FONT_BODY);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Constants.BORDER),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        return field;
    }

    /**
     * Tạo JLabel với font và màu chuẩn.
     */
    public static JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Constants.FONT_BODY);
        label.setForeground(Constants.TEXT_DARK);
        return label;
    }
}
