import java.awt.Color;
import java.awt.Font;

/**
 * Constants.java - Hằng số giao diện (Colors, Fonts, Spacing)
 * Không hardcode giá trị trong code, tất cả tập trung ở đây.
 */
public class Constants {

    // ===== COLORS - Material Design =====
    public static final Color PRIMARY = new Color(0x2196F3); // Blue
    public static final Color PRIMARY_DARK = new Color(0x1976D2); // Darker Blue
    public static final Color ACCENT = new Color(0xFF5722); // Orange
    public static final Color SUCCESS = new Color(0x4CAF50); // Green
    public static final Color SUCCESS_DARK = new Color(0x388E3C); // Darker Green
    public static final Color DANGER = new Color(0xF44336); // Red
    public static final Color DANGER_DARK = new Color(0xD32F2F); // Darker Red
    public static final Color WARNING = new Color(0xFFC107); // Yellow
    public static final Color BACKGROUND = new Color(0xF5F5F5); // Light Gray
    public static final Color TEXT_DARK = new Color(0x212121); // Dark Text
    public static final Color BORDER = new Color(0xBDBDBD); // Gray Border
    public static final Color TABLE_ALT_ROW = new Color(0xF5F5F5); // Alternate row
    public static final Color TABLE_SELECTED = new Color(0xBBDEFB); // Selected row
    public static final Color WHITE = Color.WHITE;
    public static final Color GRAY_600 = new Color(0x757575); // Medium Gray

    // ===== FONTS - Segoe UI =====
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 18);
    public static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 12);
    public static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_BUTTON = new Font("Segoe UI", Font.BOLD, 11);
    public static final Font FONT_STATS_LABEL = new Font("Segoe UI", Font.BOLD, 13);

    // ===== SPACING =====
    public static final int MARGIN = 15;
    public static final int PADDING = 10;
    public static final int GAP = 8;
    public static final int ROW_HEIGHT = 28;
}
