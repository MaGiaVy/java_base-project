package com.zalolite.client.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Hệ thống màu sắc, font (hỗ trợ emoji) và component bo tròn phong cách Berry Pop.
 */
public final class Theme {
    private Theme() {
    }

    // Bảng màu Berry Pop
    public static final Color RAIL_BG = new Color(0x5B21B6);
    public static final Color RAIL_HOVER = new Color(0x6D28D9);
    public static final Color RAIL_ACTIVE = new Color(0x7C3AED);
    public static final Color PRIMARY_RED = new Color(0xE11D48);
    public static final Color PRIMARY_RED_DARK = new Color(0xBE123C);
    public static final Color PRIMARY_PURPLE = new Color(0x9333EA);
    public static final Color BUBBLE_OTHER = new Color(0xF3E8FF);
    public static final Color TEXT_DARK = new Color(0x2E1065);
    public static final Color TEXT_MUTED = new Color(0x7E22CE);
    public static final Color SIDEBAR_BG = new Color(0xFFF5F8);
    public static final Color CHAT_BG = new Color(0xFFF9FB);
    public static final Color ITEM_SELECTED = new Color(0xFCE7F3);
    public static final Color ITEM_HOVER = new Color(0xFDF2F8);
    public static final Color BORDER_PINK = new Color(0xF5D0E6);
    public static final Color ONLINE_GREEN = new Color(0x10B981);

    private static final Color[] AVATAR_PALETTE = {
            new Color(0xE11D48), new Color(0x9333EA), new Color(0xDB2777),
            new Color(0x7C3AED), new Color(0xC026D3), new Color(0xEC4899)
    };

    private static final String UI_FONT_FAMILY;
    private static final String EMOJI_FONT_FAMILY;

    static {
        Set<String> fonts = Arrays.stream(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()
        ).collect(Collectors.toSet());
        UI_FONT_FAMILY = fonts.contains("Segoe UI") ? "Segoe UI" : Font.SANS_SERIF;
        if (fonts.contains("Segoe UI Emoji")) {
            EMOJI_FONT_FAMILY = "Segoe UI Emoji";
        } else if (fonts.contains("Apple Color Emoji")) {
            EMOJI_FONT_FAMILY = "Apple Color Emoji";
        } else if (fonts.contains("Noto Color Emoji")) {
            EMOJI_FONT_FAMILY = "Noto Color Emoji";
        } else {
            EMOJI_FONT_FAMILY = UI_FONT_FAMILY;
        }
    }

    private static final DateTimeFormatter FMT_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FMT_DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter FMT_FULL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static Font uiFont(float size) {
        return new Font(UI_FONT_FAMILY, Font.PLAIN, Math.round(size));
    }

    public static Font uiFont(int style, float size) {
        return new Font(UI_FONT_FAMILY, style, Math.round(size));
    }

    public static Font emojiFont(float size) {
        return new Font(EMOJI_FONT_FAMILY, Font.PLAIN, Math.round(size));
    }

    public static Font emojiFont(int style, float size) {
        return new Font(EMOJI_FONT_FAMILY, style, Math.round(size));
    }

    public static Color avatarColor(long id, boolean isGroup) {
        if (isGroup) return PRIMARY_PURPLE;
        int idx = (int) (Math.abs(id) % AVATAR_PALETTE.length);
        return AVATAR_PALETTE[idx];
    }

    public static String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }
        String first = parts[0].substring(0, 1).toUpperCase();
        String last = parts[parts.length - 1].substring(0, 1).toUpperCase();
        return first + last;
    }

    public static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static LocalDate dayOf(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static String formatClock(long epochMillis) {
        if (epochMillis <= 0) return "";
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(FMT_TIME);
    }

    public static String formatShortTime(long epochMillis) {
        if (epochMillis <= 0) return "";
        var zdt = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault());
        LocalDate d = zdt.toLocalDate();
        LocalDate today = LocalDate.now();
        if (d.equals(today)) return zdt.format(FMT_TIME);
        if (d.equals(today.minusDays(1))) return "Hôm qua";
        return zdt.format(FMT_DAY_MONTH);
    }

    public static String formatDateDivider(long epochMillis) {
        LocalDate d = dayOf(epochMillis);
        LocalDate today = LocalDate.now();
        if (d.equals(today)) return "Hôm nay";
        if (d.equals(today.minusDays(1))) return "Hôm qua";
        return d.format(FMT_FULL_DATE);
    }

    public static JButton primaryButton(String text) {
        JButton b = new JButton(text);
        b.setFont(uiFont(Font.BOLD, 13f));
        b.setBackground(PRIMARY_RED);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    public static JButton softButton(String text) {
        JButton b = new JButton(text);
        b.setFont(uiFont(Font.BOLD, 12.5f));
        b.setBackground(BUBBLE_OTHER);
        b.setForeground(TEXT_DARK);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** Chạy tác vụ mạng ngoài EDT bằng SwingWorker và trả kết quả về EDT. */
    public static <T> void runAsync(Callable<T> task, Consumer<T> onOk, Consumer<String> onErr) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                try {
                    T res = get();
                    if (onOk != null) onOk.accept(res);
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    String msg = c.getMessage() != null ? c.getMessage() : "Lỗi kết nối máy chủ";
                    if (onErr != null) onErr.accept(msg);
                }
            }
        }.execute();
    }

    /** Panel nền bo tròn, hỗ trợ màu đơn hoặc gradient nhẹ và viền mềm. */
    public static class RoundedPanel extends JPanel {
        private final int arc;
        private Color bgStart;
        private Color bgEnd;
        private Color borderColor;

        public RoundedPanel(int arc, Color bg) {
            this(arc, bg, null, null);
        }

        public RoundedPanel(int arc, Color bgStart, Color bgEnd, Color borderColor) {
            this.arc = arc;
            this.bgStart = bgStart;
            this.bgEnd = bgEnd;
            this.borderColor = borderColor;
            setOpaque(false);
        }

        public void setFill(Color bg) {
            this.bgStart = bg;
            this.bgEnd = null;
            repaint();
        }

        public void setBorderColor(Color borderColor) {
            this.borderColor = borderColor;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            Shape shape = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, arc, arc);
            if (bgEnd != null) {
                g2.setPaint(new GradientPaint(0, 0, bgStart, w, h, bgEnd));
            } else {
                g2.setColor(bgStart);
            }
            g2.fill(shape);
            if (borderColor != null) {
                g2.setColor(borderColor);
                g2.draw(shape);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Avatar tròn có chữ viết tắt và chấm xanh online tuỳ chọn. */
    public static class AvatarBadge extends JComponent {
        private final int size;
        private String initials;
        private Color fill;
        private boolean online;
        private boolean showOnlineDot;

        public AvatarBadge(String name, int size, Color fill, boolean showOnlineDot, boolean online) {
            this.size = size;
            this.initials = initials(name);
            this.fill = fill;
            this.showOnlineDot = showOnlineDot;
            this.online = online;
            Dimension d = new Dimension(size, size);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
            setOpaque(false);
        }

        public void setOnline(boolean online) {
            this.online = online;
            repaint();
        }

        public void setNameAndColor(String name, Color fill, boolean showOnlineDot, boolean online) {
            this.initials = initials(name);
            this.fill = fill;
            this.showOnlineDot = showOnlineDot;
            this.online = online;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fill(new Ellipse2D.Float(1, 1, size - 2, size - 2));

            g2.setColor(Color.WHITE);
            g2.setFont(uiFont(Font.BOLD, Math.max(11f, size * 0.36f)));
            FontMetrics fm = g2.getFontMetrics();
            int tx = (size - fm.stringWidth(initials)) / 2;
            int ty = (size - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(initials, tx, ty);

            if (showOnlineDot && online) {
                int dot = Math.max(9, size / 4);
                int dx = size - dot - 1;
                int dy = size - dot - 1;
                g2.setColor(Color.WHITE);
                g2.fillOval(dx - 2, dy - 2, dot + 4, dot + 4);
                g2.setColor(ONLINE_GREEN);
                g2.fillOval(dx, dy, dot, dot);
            }
            g2.dispose();
        }
    }

    /** Nhãn dạng viên thuốc bo tròn (TLS, P2P, unread badge, reaction...). */
    public static class PillLabel extends JLabel {
        private Color bg;

        public PillLabel(String text, Color bg, Color fg, float fontSize, boolean emoji) {
            super(text, SwingConstants.CENTER);
            this.bg = bg;
            setForeground(fg);
            setFont(emoji ? emojiFont(Font.BOLD, fontSize) : uiFont(Font.BOLD, fontSize));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        }

        public void setPillBg(Color bg) {
            this.bg = bg;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
