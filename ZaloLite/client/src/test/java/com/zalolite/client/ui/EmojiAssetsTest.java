package com.zalolite.client.ui;

import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

class EmojiAssetsTest {

    @Test
    void allPickerAndReactionEmojisLoadAsColorIcons() {
        assertEquals(36, EmojiAssets.PICKER_EMOJIS.length);
        assertEquals(6, EmojiAssets.REACTION_EMOJIS.length);

        for (String em : EmojiAssets.PICKER_EMOJIS) {
            assertValidIcon(em, 20);
        }
        for (String em : EmojiAssets.REACTION_EMOJIS) {
            assertValidIcon(em, 20);
        }
        assertValidIcon("😆", 20);
        assertValidIcon("❤", 20);
        assertEquals("2764", EmojiAssets.toFileKey("❤️"));
        assertEquals("2764", EmojiAssets.toFileKey("❤"));
        assertTrue(EmojiAssets.hasEmoji("Xin chào ❤️"));
        assertFalse(EmojiAssets.hasEmoji("Xin chào ZaloLite"));
    }

    @Test
    void insertRichTextReplacesEmojisWithIconAttributes() {
        JTextPane pane = new JTextPane();
        Color mentionColor = Theme.PRIMARY_PURPLE;
        EmojiAssets.insertRichText(pane, "Xin chào ❤️😂 @Lan!", Theme.TEXT_DARK, 14f, mentionColor);

        StyledDocument doc = pane.getStyledDocument();
        int iconCount = countIcons(doc.getDefaultRootElement());
        assertTrue(iconCount >= 2, "Expected at least 2 emoji icons, found " + iconCount);

        boolean foundMention = hasColoredBoldRun(doc.getDefaultRootElement(), doc, "@Lan", mentionColor);
        assertTrue(foundMention, "Expected @Lan mention to be styled bold with mentionColor");

        // Adjacent identical emojis must remain two separate icon elements.
        EmojiAssets.insertRichText(pane, "😂😂", Theme.TEXT_DARK, 14f);
        assertEquals(2, countIcons(pane.getStyledDocument().getDefaultRootElement()));
    }

    private static void assertValidIcon(String emoji, int size) {
        ImageIcon icon = EmojiAssets.icon(emoji, size);
        assertNotNull(icon, "Missing icon for " + emoji + " (" + EmojiAssets.toFileKey(emoji) + ")");
        assertEquals(size, icon.getIconWidth(), "Width mismatch for " + emoji);
        assertEquals(size, icon.getIconHeight(), "Height mismatch for " + emoji);
    }

    private static int countIcons(Element el) {
        if (el.isLeaf()) {
            return StyleConstants.getIcon(el.getAttributes()) != null ? 1 : 0;
        }
        int total = 0;
        for (int i = 0; i < el.getElementCount(); i++) {
            total += countIcons(el.getElement(i));
        }
        return total;
    }

    private static boolean hasColoredBoldRun(Element el, StyledDocument doc, String expectedText, Color expectedColor) {
        if (el.isLeaf()) {
            try {
                int len = el.getEndOffset() - el.getStartOffset();
                String run = doc.getText(el.getStartOffset(), len);
                if (expectedText.equals(run)
                        && StyleConstants.isBold(el.getAttributes())
                        && expectedColor.equals(StyleConstants.getForeground(el.getAttributes()))) {
                    return true;
                }
            } catch (Exception ignored) {
                return false;
            }
            return false;
        }
        for (int i = 0; i < el.getElementCount(); i++) {
            if (hasColoredBoldRun(el.getElement(i), doc, expectedText, expectedColor)) return true;
        }
        return false;
    }
}
