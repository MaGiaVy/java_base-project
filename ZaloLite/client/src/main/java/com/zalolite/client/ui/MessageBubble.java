package com.zalolite.client.ui;

import com.zalolite.client.AudioHelper;
import com.zalolite.client.LinkPreviewFetcher;
import com.zalolite.common.Message;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Một bong bóng tin nhắn (đi/đến/hệ thống) kèm media, reply, forward, link preview, reaction và menu chuột phải. */
public class MessageBubble extends JPanel {
    private static final String[] QUICK_REACTIONS = {"❤️", "😆", "😮", "😢", "👍"};
    private static final int MAX_TEXT_WIDTH = 360;

    private final Message msg;
    private final long meId;
    private final boolean isGroup;
    private final BiConsumer<Message, String> onReact;
    private final Consumer<Message> onRecall;
    private final Consumer<Message> onReply;
    private final Consumer<Message> onForward;
    private final BiConsumer<Message, Consumer<File>> mediaResolver;
    private final AudioHelper audioHelper;

    private final JPanel bodyHolder = new JPanel(new BorderLayout());
    private final JPanel reactionsBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
    private JLabel metaLabel;

    public MessageBubble(Message msg, long meId, boolean isGroup,
                         BiConsumer<Message, String> onReact, Consumer<Message> onRecall,
                         Consumer<Message> onReply, Consumer<Message> onForward,
                         BiConsumer<Message, Consumer<File>> mediaResolver, AudioHelper audioHelper) {
        this.msg = msg;
        this.meId = meId;
        this.isGroup = isGroup;
        this.onReact = onReact;
        this.onRecall = onRecall;
        this.onReply = onReply;
        this.onForward = onForward;
        this.mediaResolver = mediaResolver;
        this.audioHelper = audioHelper;

        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 12));

        if (Message.KIND_SYSTEM.equals(msg.kind)) {
            buildSystemBubble();
        } else {
            buildChatBubble();
        }
    }

    public Message getMessage() {
        return msg;
    }

    private void buildSystemBubble() {
        JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 2));
        center.setOpaque(false);
        Theme.PillLabel pill = new Theme.PillLabel(
                msg.content == null ? "" : msg.content,
                Theme.BUBBLE_OTHER, Theme.TEXT_MUTED, 11.5f, true);
        pill.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        center.add(pill);
        add(center, BorderLayout.CENTER);
    }

    private void buildChatBubble() {
        boolean mine = msg.senderId == meId;

        JPanel row = new JPanel(new FlowLayout(mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);

        if (!mine) {
            Theme.AvatarBadge av = new Theme.AvatarBadge(
                    msg.senderName, 30, Theme.avatarColor(msg.senderId, false), false, false);
            JPanel avWrap = new JPanel(new BorderLayout());
            avWrap.setOpaque(false);
            avWrap.add(av, BorderLayout.NORTH);
            row.add(avWrap);
        }

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));

        if (!mine && msg.senderName != null && !msg.senderName.isBlank()) {
            JLabel sender = new JLabel(msg.senderName);
            sender.setFont(Theme.uiFont(Font.BOLD, 11.5f));
            sender.setForeground(Theme.TEXT_MUTED);
            sender.setAlignmentX(LEFT_ALIGNMENT);
            sender.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 0));
            stack.add(sender);
        }

        bodyHolder.setOpaque(false);
        bodyHolder.setAlignmentX(mine ? RIGHT_ALIGNMENT : LEFT_ALIGNMENT);
        renderBubbleBody();
        stack.add(bodyHolder);

        reactionsBar.setOpaque(false);
        reactionsBar.setAlignmentX(mine ? RIGHT_ALIGNMENT : LEFT_ALIGNMENT);
        if (mine) {
            reactionsBar.setLayout(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        }
        renderReactions();
        stack.add(reactionsBar);

        metaLabel = new JLabel(buildMetaText());
        metaLabel.setFont(Theme.emojiFont(10.5f));
        metaLabel.setForeground(Theme.TEXT_MUTED);
        metaLabel.setAlignmentX(mine ? RIGHT_ALIGNMENT : LEFT_ALIGNMENT);
        metaLabel.setBorder(BorderFactory.createEmptyBorder(1, 4, 0, 4));
        stack.add(metaLabel);

        row.add(stack);
        add(row, BorderLayout.CENTER);
    }

    private void renderBubbleBody() {
        bodyHolder.removeAll();
        boolean mine = msg.senderId == meId;

        Theme.RoundedPanel bubble;
        if (msg.recalled) {
            bubble = new Theme.RoundedPanel(16, Color.WHITE, null, Theme.BORDER_PINK);
            JLabel lbl = new JLabel("Tin nhắn đã được thu hồi");
            lbl.setFont(Theme.uiFont(Font.ITALIC, 13f));
            lbl.setForeground(Theme.TEXT_MUTED);
            bubble.setLayout(new BorderLayout());
            bubble.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            bubble.add(lbl, BorderLayout.CENTER);
        } else {
            bubble = mine
                    ? new Theme.RoundedPanel(16, Theme.PRIMARY_RED, Theme.PRIMARY_RED_DARK, null)
                    : new Theme.RoundedPanel(16, Theme.BUBBLE_OTHER, null, null);
            bubble.setLayout(new BorderLayout());
            bubble.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

            JPanel inner = new JPanel();
            inner.setOpaque(false);
            inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

            if (msg.forwarded) {
                JLabel fwd = new JLabel("↪ Đã chuyển tiếp");
                fwd.setFont(Theme.emojiFont(Font.ITALIC, 11f));
                fwd.setForeground(mine ? new Color(0xFFE4E6) : Theme.TEXT_MUTED);
                fwd.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));
                fwd.setAlignmentX(LEFT_ALIGNMENT);
                inner.add(fwd);
            }

            if (msg.replyTo != null && (msg.replyPreview != null || msg.replySender != null)) {
                JPanel quote = new JPanel(new GridLayout(0, 1));
                quote.setBackground(mine ? new Color(0x9F1239) : new Color(0xE9D5FF));
                quote.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, mine ? Color.WHITE : Theme.PRIMARY_PURPLE),
                        BorderFactory.createEmptyBorder(4, 8, 4, 8)));
                quote.setAlignmentX(LEFT_ALIGNMENT);
                JLabel qs = new JLabel(msg.replySender != null ? msg.replySender : "Trả lời");
                qs.setFont(Theme.uiFont(Font.BOLD, 11f));
                qs.setForeground(mine ? Color.WHITE : Theme.PRIMARY_PURPLE);
                JLabel qp = new JLabel(msg.replyPreview != null ? msg.replyPreview : "");
                qp.setFont(Theme.emojiFont(11.5f));
                qp.setForeground(mine ? new Color(0xFFE4E6) : Theme.TEXT_DARK);
                quote.add(qs);
                quote.add(qp);
                inner.add(quote);
                inner.add(Box.createVerticalStrut(5));
            }

            JComponent contentComp = buildContentComponent(mine);
            contentComp.setAlignmentX(LEFT_ALIGNMENT);
            inner.add(contentComp);

            if ((msg.kind == null || Message.KIND_TEXT.equals(msg.kind)) && msg.content != null) {
                String url = LinkPreviewFetcher.extractFirstUrl(msg.content);
                if (url != null) {
                    attachLinkPreviewAsync(inner, url, mine);
                }
            }

            bubble.add(inner, BorderLayout.CENTER);
            attachContextMenu(bubble);
            attachContextMenu(contentComp);
        }

        bodyHolder.add(bubble, BorderLayout.CENTER);
        bodyHolder.revalidate();
        bodyHolder.repaint();
    }

    private JComponent buildContentComponent(boolean mine) {
        Color fg = mine ? Color.WHITE : Theme.TEXT_DARK;
        String kind = msg.kind == null ? Message.KIND_TEXT : msg.kind;
        return switch (kind) {
            case Message.KIND_IMAGE -> buildImageCard(mine, fg);
            case Message.KIND_VIDEO -> buildFileActionCard(mine, fg, "🎬", "▶ Phát video",
                    f -> MediaViewer.openWithSystemPlayer(this, f));
            case Message.KIND_FILE -> buildFileActionCard(mine, fg, "📎", "⬇ Mở / Tải về",
                    f -> MediaViewer.openWithSystemPlayer(this, f));
            case Message.KIND_AUDIO -> buildAudioCard(mine, fg);
            default -> buildTextArea(msg.content == null ? "" : msg.content, fg);
        };
    }

    private JComponent buildImageCard(boolean mine, Color fg) {
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

        JLabel imgLbl = new JLabel("🖼️ Đang tải ảnh...");
        imgLbl.setFont(Theme.emojiFont(12.5f));
        imgLbl.setForeground(fg);
        imgLbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        imgLbl.setAlignmentX(LEFT_ALIGNMENT);
        box.add(imgLbl);

        if (msg.fileName != null && !msg.fileName.isBlank()) {
            JLabel cap = new JLabel(msg.fileName + (msg.fileSize > 0 ? " (" + formatFileSize(msg.fileSize) + ")" : ""));
            cap.setFont(Theme.uiFont(11f));
            cap.setForeground(mine ? new Color(0xFFE4E6) : Theme.TEXT_MUTED);
            cap.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
            cap.setAlignmentX(LEFT_ALIGNMENT);
            box.add(cap);
        }

        if (mediaResolver != null) {
            mediaResolver.accept(msg, file -> {
                if (file == null) {
                    imgLbl.setText("🖼️ " + (msg.fileName == null ? "Hình ảnh" : msg.fileName) + " (chỉ lưu cục bộ P2P)");
                    return;
                }
                Theme.runAsync(
                        () -> MediaViewer.loadThumbnail(file, 220, 180),
                        icon -> {
                            if (icon != null) {
                                imgLbl.setText("");
                                imgLbl.setIcon(icon);
                                imgLbl.setToolTipText("Bấm để xem ảnh lớn");
                                imgLbl.addMouseListener(new MouseAdapter() {
                                    @Override
                                    public void mouseClicked(MouseEvent e) {
                                        if (SwingUtilities.isLeftMouseButton(e)) {
                                            MediaViewer.showImageDialog(MessageBubble.this, file, msg.fileName);
                                        }
                                    }
                                });
                            } else {
                                imgLbl.setText("🖼️ " + (msg.fileName == null ? "Hình ảnh" : msg.fileName));
                            }
                            revalidate();
                            repaint();
                        },
                        err -> imgLbl.setText("🖼️ Không tải được ảnh")
                );
            });
        }
        return box;
    }

    private JComponent buildFileActionCard(boolean mine, Color fg, String icon, String btnText, Consumer<File> onFile) {
        JPanel box = new JPanel(new BorderLayout(10, 4));
        box.setOpaque(false);
        String name = (msg.fileName == null || msg.fileName.isBlank()) ? "Tệp đính kèm" : msg.fileName;
        String meta = msg.fileSize > 0 ? formatFileSize(msg.fileSize) : "";
        JLabel title = new JLabel(icon + " " + name + (meta.isEmpty() ? "" : " (" + meta + ")"));
        title.setFont(Theme.emojiFont(Font.BOLD, 12.5f));
        title.setForeground(fg);

        JButton act = mine ? Theme.softButton(btnText) : Theme.primaryButton(btnText);
        act.setFont(Theme.emojiFont(Font.BOLD, 11.5f));
        act.addActionListener(e -> {
            if (mediaResolver == null) return;
            act.setEnabled(false);
            mediaResolver.accept(msg, file -> {
                act.setEnabled(true);
                if (file == null) {
                    JOptionPane.showMessageDialog(this, "Không tìm thấy hoặc không tải được tệp.");
                    return;
                }
                onFile.accept(file);
            });
        });
        box.add(title, BorderLayout.NORTH);
        box.add(act, BorderLayout.SOUTH);
        return box;
    }

    private JComponent buildAudioCard(boolean mine, Color fg) {
        JPanel box = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        box.setOpaque(false);
        JButton playBtn = mine ? Theme.softButton("▶ Nghe") : Theme.primaryButton("▶ Nghe");
        playBtn.setFont(Theme.emojiFont(Font.BOLD, 11.5f));
        JLabel lbl = new JLabel("🎤 Tin nhắn thoại" + (msg.durationSec > 0 ? " (" + msg.durationSec + "s)" : ""));
        lbl.setFont(Theme.emojiFont(12.5f));
        lbl.setForeground(fg);

        final boolean[] playing = {false};
        playBtn.addActionListener(e -> {
            if (audioHelper == null || mediaResolver == null) return;
            if (playing[0]) {
                audioHelper.stopPlayback();
                playing[0] = false;
                playBtn.setText("▶ Nghe");
                return;
            }
            playBtn.setEnabled(false);
            mediaResolver.accept(msg, file -> {
                playBtn.setEnabled(true);
                if (file == null) {
                    JOptionPane.showMessageDialog(this, "Không tìm thấy hoặc không tải được tin nhắn thoại.");
                    return;
                }
                try {
                    playing[0] = true;
                    playBtn.setText("⏹ Dừng");
                    audioHelper.play(file, () -> SwingUtilities.invokeLater(() -> {
                        playing[0] = false;
                        playBtn.setText("▶ Nghe");
                    }));
                } catch (Exception ex) {
                    playing[0] = false;
                    playBtn.setText("▶ Nghe");
                    JOptionPane.showMessageDialog(this, "Không phát được âm thanh: " + ex.getMessage());
                }
            });
        });

        box.add(playBtn);
        box.add(lbl);
        return box;
    }

    private JComponent buildTextArea(String text, Color fg) {
        JTextArea area = new JTextArea(text);
        area.setFont(Theme.emojiFont(13.5f));
        area.setForeground(fg);
        area.setOpaque(false);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(null);

        // Tính chiều rộng gọn theo nội dung, tối đa MAX_TEXT_WIDTH để bong bóng ngắn không bị phình
        FontMetrics fm = area.getFontMetrics(area.getFont());
        int longestLine = 0;
        for (String line : text.split("\n", -1)) {
            longestLine = Math.max(longestLine, fm.stringWidth(line));
        }
        int targetW = Math.max(24, Math.min(longestLine + 8, MAX_TEXT_WIDTH));
        area.setSize(new Dimension(targetW, Short.MAX_VALUE));
        Dimension pref = area.getPreferredSize();
        area.setPreferredSize(new Dimension(targetW, pref.height));
        return area;
    }

    private void attachLinkPreviewAsync(JPanel container, String url, boolean mine) {
        Theme.runAsync(
                () -> LinkPreviewFetcher.fetch(url),
                preview -> {
                    if (preview == null) return;
                    Theme.RoundedPanel card = new Theme.RoundedPanel(10,
                            mine ? new Color(0x9F1239) : Color.WHITE, null,
                            mine ? new Color(0xFDA4AF) : Theme.BORDER_PINK);
                    card.setLayout(new GridLayout(0, 1, 0, 2));
                    card.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
                    card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    card.setAlignmentX(LEFT_ALIGNMENT);
                    JLabel t = new JLabel("🔗 " + preview.title());
                    t.setFont(Theme.emojiFont(Font.BOLD, 11.5f));
                    t.setForeground(mine ? Color.WHITE : Theme.TEXT_DARK);
                    JLabel h = new JLabel(preview.host());
                    h.setFont(Theme.uiFont(10.5f));
                    h.setForeground(mine ? new Color(0xFFE4E6) : Theme.TEXT_MUTED);
                    card.add(t);
                    card.add(h);
                    card.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            if (SwingUtilities.isLeftMouseButton(e)) {
                                try {
                                    Desktop.getDesktop().browse(URI.create(preview.url()));
                                } catch (Exception ignored) {}
                            }
                        }
                    });
                    container.add(Box.createVerticalStrut(6));
                    container.add(card);
                    revalidate();
                    repaint();
                },
                null
        );
    }

    private String formatAttachmentMeta() {
        StringBuilder sb = new StringBuilder();
        if (msg.fileName != null && !msg.fileName.isBlank()) {
            sb.append(": ").append(msg.fileName);
        }
        if (msg.fileSize > 0) {
            sb.append(" (").append(formatFileSize(msg.fileSize)).append(")");
        }
        return sb.toString();
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void attachContextMenu(JComponent target) {
        target.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showContextMenu(e);
            }
        });
    }

    private void showContextMenu(MouseEvent e) {
        if (msg.recalled || Message.KIND_SYSTEM.equals(msg.kind)) return;
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(Color.WHITE);
        menu.setBorder(BorderFactory.createLineBorder(Theme.BORDER_PINK, 1));

        JPanel reactRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4));
        reactRow.setBackground(Color.WHITE);
        for (String emoji : QUICK_REACTIONS) {
            JButton b = new JButton(emoji);
            b.setFont(Theme.emojiFont(16f));
            b.setFocusPainted(false);
            b.setBorderPainted(false);
            b.setContentAreaFilled(false);
            b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            b.addActionListener(ev -> {
                menu.setVisible(false);
                if (onReact != null) onReact.accept(msg, emoji);
            });
            reactRow.add(b);
        }
        menu.add(reactRow);
        menu.addSeparator();

        JMenuItem replyItem = new JMenuItem("↩ Trả lời");
        replyItem.setFont(Theme.emojiFont(12.5f));
        replyItem.addActionListener(ev -> {
            if (onReply != null) onReply.accept(msg);
        });
        menu.add(replyItem);

        JMenuItem fwdItem = new JMenuItem("➡ Chuyển tiếp");
        fwdItem.setFont(Theme.emojiFont(12.5f));
        fwdItem.addActionListener(ev -> {
            if (onForward != null) onForward.accept(msg);
        });
        menu.add(fwdItem);

        if (msg.senderId == meId) {
            menu.addSeparator();
            JMenuItem recall = new JMenuItem("Thu hồi tin nhắn");
            recall.setFont(Theme.uiFont(12.5f));
            recall.setForeground(Theme.PRIMARY_RED_DARK);
            recall.addActionListener(ev -> {
                if (onRecall != null) onRecall.accept(msg);
            });
            menu.add(recall);
        }
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void renderReactions() {
        reactionsBar.removeAll();
        if (msg.recalled || msg.reactions == null || msg.reactions.isEmpty()) {
            reactionsBar.setVisible(false);
        } else {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String emoji : msg.reactions.values()) {
                if (emoji != null && !emoji.isBlank()) {
                    counts.merge(emoji, 1, Integer::sum);
                }
            }
            if (counts.isEmpty()) {
                reactionsBar.setVisible(false);
            } else {
                reactionsBar.setVisible(true);
                String myEmoji = msg.reactions.get(meId);
                for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                    String em = entry.getKey();
                    int c = entry.getValue();
                    boolean mine = em.equals(myEmoji);
                    Theme.PillLabel pill = new Theme.PillLabel(
                            em + " " + c,
                            mine ? Theme.ITEM_SELECTED : Color.WHITE,
                            Theme.TEXT_DARK,
                            11.5f,
                            true);
                    pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    pill.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            if (onReact != null) onReact.accept(msg, em);
                        }
                    });
                    reactionsBar.add(pill);
                }
            }
        }
        reactionsBar.revalidate();
        reactionsBar.repaint();
    }

    private String buildMetaText() {
        StringBuilder sb = new StringBuilder(Theme.formatClock(msg.createdAt));
        if ("P2P".equalsIgnoreCase(msg.transport)) {
            sb.append(" [P2P ⚡]");
        } else if ("SERVER".equalsIgnoreCase(msg.transport)) {
            sb.append(" [Server]");
        }
        if (msg.senderId == meId && !msg.recalled) {
            String state = msg.deliveryState == null ? Message.STATE_SENT : msg.deliveryState;
            if (Message.STATE_SEEN.equals(state) || msg.seenCount > 0) {
                if (isGroup && msg.seenCount > 0) {
                    sb.append(" · Đã xem ").append(msg.seenCount);
                } else {
                    sb.append(" · Đã xem ✓✓");
                }
            } else if (Message.STATE_DELIVERED.equals(state)) {
                sb.append(" · ✓✓ Đã nhận");
            } else {
                sb.append(" · ✓ Đã gửi");
            }
        }
        return sb.toString();
    }

    public void markRecalled() {
        msg.recalled = true;
        if (msg.reactions != null) msg.reactions.clear();
        renderBubbleBody();
        renderReactions();
        if (metaLabel != null) metaLabel.setText(buildMetaText());
    }

    public void applyReaction(long userId, String emoji) {
        if (msg.reactions == null) msg.reactions = new LinkedHashMap<>();
        if (emoji == null || emoji.isBlank()) {
            msg.reactions.remove(userId);
        } else {
            msg.reactions.put(userId, emoji);
        }
        renderReactions();
    }

    public void updateDelivery(String state, int seenCount) {
        if (state != null) msg.deliveryState = state;
        if (seenCount > msg.seenCount) msg.seenCount = seenCount;
        if (metaLabel != null) metaLabel.setText(buildMetaText());
    }

    @Override
    public Dimension getMaximumSize() {
        Dimension pref = getPreferredSize();
        return new Dimension(Integer.MAX_VALUE, pref.height);
    }
}
