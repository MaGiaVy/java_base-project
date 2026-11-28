package com.zalolite.client.ui;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline full-color Twemoji PNG asset loader and rich-text renderer for Swing.
 */
public final class EmojiAssets {
    private EmojiAssets() {
    }

    public static final String[] PICKER_EMOJIS = {
            "😀", "😂", "🥰", "😍", "🤩", "😎",
            "😊", "😉", "😅", "🤣", "😢", "😭",
            "😮", "😱", "😡", "🤔", "😴", "🥳",
            "❤️", "🩷", "💜", "🔥", "✨", "🎉",
            "👍", "👎", "👏", "🙌", "🙏", "🤝",
            "🍓", "🍇", "☕", "🎂", "🎵", "💯"
    };

    public static final String[] REACTION_EMOJIS = {
            "👍", "❤️", "😂", "😮", "😢", "😡"
    };

    private static final String[] EXTRA_EMOJIS = {"😆"};
    private static final Set<Integer> KNOWN_CODEPOINTS = new HashSet<>();
    private static final Map<String, BufferedImage> RAW_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ImageIcon> ICON_CACHE = new ConcurrentHashMap<>();
    // shortcut: only highlights single-word @mentions without spaces; upgrade with member-name matching if multi-word mentions need full highlight
    private static final Pattern MENTION_PATTERN = Pattern.compile("@[\\p{L}\\p{N}_]+");

    static {
        registerAll(PICKER_EMOJIS);
        registerAll(REACTION_EMOJIS);
        registerAll(EXTRA_EMOJIS);
    }

    private static void registerAll(String[] emojis) {
        for (String em : emojis) {
            for (int i = 0; i < em.length(); ) {
                int cp = em.codePointAt(i);
                i += Character.charCount(cp);
                if (cp != 0xFE0F) {
                    KNOWN_CODEPOINTS.add(cp);
                    break;
                }
            }
        }
    }

    public static String toFileKey(String emoji) {
        if (emoji == null || emoji.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < emoji.length(); ) {
            int cp = emoji.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == 0xFE0F) continue;
            if (!sb.isEmpty()) sb.append('-');
            sb.append(Integer.toHexString(cp));
        }
        return sb.toString();
    }

    public static ImageIcon icon(String emoji, int sizePx) {
        if (emoji == null || emoji.isEmpty() || sizePx <= 0) return null;
        String key = toFileKey(emoji);
        if (key.isEmpty()) return null;

        String cacheKey = key + "@" + sizePx;
        ImageIcon cached = ICON_CACHE.get(cacheKey);
        if (cached != null) return cached;

        BufferedImage raw = loadRaw(key);
        if (raw == null) return null;

        BufferedImage scaled = new BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(raw, 0, 0, sizePx, sizePx, null);
        g2.dispose();

        ImageIcon icon = new ImageIcon(scaled);
        ICON_CACHE.putIfAbsent(cacheKey, icon);
        return ICON_CACHE.get(cacheKey);
    }

    private static BufferedImage loadRaw(String key) {
        BufferedImage cached = RAW_CACHE.get(key);
        if (cached != null) return cached;

        try (InputStream in = EmojiAssets.class.getResourceAsStream("/emoji/" + key + ".png")) {
            if (in == null) return null;
            BufferedImage img = ImageIO.read(in);
            if (img == null) return null;
            RAW_CACHE.putIfAbsent(key, img);
            return RAW_CACHE.get(key);
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean hasEmoji(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp != 0xFE0F && KNOWN_CODEPOINTS.contains(cp)) {
                return true;
            }
        }
        return false;
    }

    public static void insertRichText(JTextPane pane, String text, Color fg, float fontSize) {
        insertRichText(pane, text, fg, fontSize, null);
    }

    public static void insertRichText(JTextPane pane, String text, Color fg, float fontSize, Color mentionColor) {
        StyledDocument doc = pane.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
            if (text == null || text.isEmpty()) return;

            Font uiFont = Theme.uiFont(fontSize);
            SimpleAttributeSet baseAttr = new SimpleAttributeSet();
            StyleConstants.setFontFamily(baseAttr, uiFont.getFamily());
            StyleConstants.setFontSize(baseAttr, uiFont.getSize());
            if (fg != null) StyleConstants.setForeground(baseAttr, fg);

            SimpleAttributeSet mentionAttr = null;
            if (mentionColor != null) {
                mentionAttr = new SimpleAttributeSet(baseAttr);
                StyleConstants.setBold(mentionAttr, true);
                StyleConstants.setForeground(mentionAttr, mentionColor);
            }

            int iconPx = Math.max(1, Math.round(fontSize * 1.35f));
            Matcher mentionMatcher = mentionAttr != null ? MENTION_PATTERN.matcher(text) : null;
            StringBuilder plain = new StringBuilder();
            int iconSeq = 0;

            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                int charCount = Character.charCount(cp);

                if (KNOWN_CODEPOINTS.contains(cp)) {
                    String em = new String(Character.toChars(cp));
                    ImageIcon ic = icon(em, iconPx);
                    if (ic != null) {
                        flush(doc, plain, baseAttr);
                        i += charCount;
                        if (i < text.length() && text.charAt(i) == '\uFE0F') i++;
                        // Unique seq prevents DefaultStyledDocument from merging adjacent identical emojis into one IconView.
                        SimpleAttributeSet iconAttr = new SimpleAttributeSet(baseAttr);
                        StyleConstants.setIcon(iconAttr, ic);
                        iconAttr.addAttribute("emoji-seq", iconSeq++);
                        doc.insertString(doc.getLength(), em, iconAttr);
                        continue;
                    }
                }

                if (mentionMatcher != null && cp == '@' && (i == 0 || !isWordChar(text.codePointBefore(i)))) {
                    mentionMatcher.region(i, text.length());
                    if (mentionMatcher.lookingAt()) {
                        flush(doc, plain, baseAttr);
                        doc.insertString(doc.getLength(), mentionMatcher.group(), mentionAttr);
                        i = mentionMatcher.end();
                        continue;
                    }
                }

                plain.append(text, i, i + charCount);
                i += charCount;
            }
            flush(doc, plain, baseAttr);
        } catch (BadLocationException ignored) {
            // Document offsets are derived directly from doc.getLength().
        }
    }

    private static void flush(StyledDocument doc, StringBuilder plain, SimpleAttributeSet attr) throws BadLocationException {
        if (!plain.isEmpty()) {
            doc.insertString(doc.getLength(), plain.toString(), attr);
            plain.setLength(0);
        }
    }

    private static boolean isWordChar(int cp) {
        return Character.isLetterOrDigit(cp) || cp == '_';
    }
}
