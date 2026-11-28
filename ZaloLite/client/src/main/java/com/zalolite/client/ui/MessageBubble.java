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

/**
 * Thành phần hiển thị một bong bóng tin nhắn (tin gửi đi, tin nhận về hoặc thông báo hệ thống):
 *  - Hỗ trợ nội dung văn bản, ảnh thumbnail, video, file đính kèm, tin nhắn thoại (WAV), trích dẫn trả lời, chuyển tiếp.
 *  - Tự động tải xem trước đường link (Link Preview) bất đồng bộ qua {@link LinkPreviewFetcher} (HTTP/1.1 thuần Socket).
 *  - Hiển thị nhãn phương thức truyền ({@code [P2P ⚡]} hoặc {@code [Server]}) và trạng thái giao nhận (Đã gửi / Đã nhận / Đã xem).
 *  - Menu chuột phải kích hoạt thả cảm xúc (Reaction), Trả lời (Reply), Chuyển tiếp (Forward), Thu hồi (Recall).
 */
public class MessageBubble extends JPanel {
    private static final int MAX_TEXT_WIDTH = 360;

    private final Message msg;
    private final long meId;
    private final boolean isGroup;
    private final BiConsumer<Message, String> onReact;
    private final Consumer<Message> onRecall;
    private final Consumer<Message> onReply;
    private final Consumer<Message> onForward;
    private final Consumer<Message> onPin;
    private final BiConsumer<Message, Consumer<File>> mediaResolver;
    private final AudioHelper audioHelper;

    private final JPanel bodyHolder = new JPanel(new BorderLayout());
    private final JPanel reactionsBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
    private JLabel metaLabel;
    private VideoPlayerPane activeVideoPlayer;

    public MessageBubble(Message msg, long meId, boolean isGroup,
                         BiConsumer<Message, String> onReact, Consumer<Message> onRecall,
                         Consumer<Message> onReply, Consumer<Message> onForward,
                         Consumer<Message> onPin,
                         BiConsumer<Message, Consumer<File>> mediaResolver, AudioHelper audioHelper) {
        this.msg = msg;
        this.meId = meId;
        this.isGroup = isGroup;
        this.onReact = onReact;
        this.onRecall = onRecall;
        this.onReply = onReply;
        this.onForward = onForward;
        this.onPin = onPin;
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
                    ? new Theme.RoundedPanel(14, Theme.PRIMARY_RED, Theme.PRIMARY_RED_DARK, null)
                    : new Theme.RoundedPanel(14, Color.WHITE, null, Theme.BORDER_PINK);
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
            case Message.KIND_VIDEO -> buildInlineVideoCard(mine, fg);
            case Message.KIND_FILE -> buildFileWithDownloadCard(mine, fg);
            case Message.KIND_AUDIO -> buildAudioCard(mine, fg);
            default -> buildTextArea(msg.content == null ? "" : msg.content, mine, fg);
        };
    }

    /** Phát video MP4 trực tiếp ngay trên bong bóng (JavaFX MediaPlayer) kèm nút Tải về máy riêng. */
    private JComponent buildInlineVideoCard(boolean mine, Color fg) {
        JPanel box = new JPanel(new BorderLayout(0, 4));
        box.setOpaque(false);
        JLabel status = new JLabel("🎬 Đang chuẩn bị trình phát video...");
        status.setFont(Theme.emojiFont(12f));
        status.setForeground(fg);
        box.add(status, BorderLayout.CENTER);

        if (mediaResolver != null) {
            mediaResolver.accept(msg, file -> {
                box.removeAll();
                if (file == null) {
                    JLabel miss = new JLabel("🎬 " + (msg.fileName == null ? "Video" : msg.fileName) + " (chỉ lưu cục bộ P2P)");
                    miss.setFont(Theme.emojiFont(12f));
                    miss.setForeground(fg);
                    box.add(miss, BorderLayout.CENTER);
                } else {
                    if (activeVideoPlayer != null) activeVideoPlayer.dispose();
                    activeVideoPlayer = new VideoPlayerPane(
                            file,
                            msg.fileName != null ? msg.fileName : file.getName(),
                            msg.fileSize > 0 ? msg.fileSize : file.length(),
                            () -> MediaViewer.saveFileAs(MessageBubble.this, file, msg.fileName),
                            () -> MediaViewer.openWithSystemPlayer(MessageBubble.this, file)
                    );
                    box.add(activeVideoPlayer, BorderLayout.CENTER);
                }
                box.revalidate();
                box.repaint();
                MessageBubble.this.revalidate();
            });
        }
        return box;
    }

    private JComponent buildFileWithDownloadCard(boolean mine, Color fg) {
        JPanel box = new JPanel(new BorderLayout(8, 6));
        box.setOpaque(false);
        String name = (msg.fileName == null || msg.fileName.isBlank()) ? "Tệp đính kèm" : msg.fileName;
        String meta = msg.fileSize > 0 ? formatFileSize(msg.fileSize) : "";
        JLabel title = new JLabel("📎 " + name + (meta.isEmpty() ? "" : " (" + meta + ")"));
        title.setFont(Theme.emojiFont(Font.BOLD, 12.5f));
        title.setForeground(fg);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btns.setOpaque(false);
        JButton openBtn = mine ? Theme.softButton("📂 Mở") : Theme.primaryButton("📂 Mở");
        openBtn.setFont(Theme.emojiFont(Font.BOLD, 11.5f));
        JButton dlBtn = Theme.softButton("⬇ Tải về");
        dlBtn.setFont(Theme.emojiFont(Font.BOLD, 11.5f));

        openBtn.addActionListener(e -> {
            if (mediaResolver == null) return;
            openBtn.setEnabled(false);
            mediaResolver.accept(msg, file -> {
                openBtn.setEnabled(true);
                if (file == null) JOptionPane.showMessageDialog(this, "Không tìm thấy tệp.");
                else MediaViewer.openWithSystemPlayer(this, file);
            });
        });
        dlBtn.addActionListener(e -> {
            if (mediaResolver == null) return;
            dlBtn.setEnabled(false);
            mediaResolver.accept(msg, file -> {
                dlBtn.setEnabled(true);
                if (file == null) JOptionPane.showMessageDialog(this, "Không tải được tệp.");
                else MediaViewer.saveFileAs(this, file, msg.fileName);
            });
        });
        btns.add(openBtn);
        btns.add(dlBtn);
        box.add(title, BorderLayout.NORTH);
        box.add(btns, BorderLayout.SOUTH);
        return box;
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
                if (file == null) {
                    playBtn.setEnabled(true);
                    JOptionPane.showMessageDialog(this, "Không tìm thấy hoặc không tải được tin nhắn thoại.");
                    return;
                }
                Theme.runAsync(() -> {
                    audioHelper.play(file, () -> SwingUtilities.invokeLater(() -> {
                        playing[0] = false;
                        playBtn.setText("▶ Nghe");
                    }));
                    return null;
                }, ok -> {
                    playing[0] = true;
                    playBtn.setText("⏹ Dừng");
                    playBtn.setEnabled(true);
                }, err -> {
                    playing[0] = false;
                    playBtn.setText("▶ Nghe");
                    playBtn.setEnabled(true);
                    JOptionPane.showMessageDialog(this, "Không phát được âm thanh: " + err);
                });
            });
        });

        box.add(playBtn);
        box.add(lbl);
        return box;
    }

    private JComponent buildTextArea(String text, boolean mine, Color fg) {
        JTextPane pane = new JTextPane();
        pane.setOpaque(false);
        pane.setEditable(false);
        pane.setBorder(null);
        Color mentionColor = mine ? new Color(0xFEF08A) : Theme.PRIMARY_PURPLE;
        EmojiAssets.insertRichText(pane, text, fg, 13.5f, mentionColor);

        // Tính chiều rộng gọn theo nội dung, tối đa MAX_TEXT_WIDTH để bong bóng ngắn không bị phình
        FontMetrics fm = pane.getFontMetrics(Theme.uiFont(13.5f));
        int longestLine = 0;
        for (String line : text.split("\n", -1)) {
            longestLine = Math.max(longestLine, fm.stringWidth(line) + 12);
        }
        int targetW = Math.max(36, Math.min(longestLine + 10, MAX_TEXT_WIDTH));
        pane.setSize(new Dimension(targetW, Short.MAX_VALUE));
        Dimension pref = pane.getPreferredSize();
        pane.setPreferredSize(new Dimension(Math.min(targetW, MAX_TEXT_WIDTH), Math.max(20, pref.height)));
        return pane;
    }

    /**
     * [UI -> GỬI] Tự động gọi {@link LinkPreviewFetcher#fetch(String)} (HTTP/1.1 thuần Socket cổng 80/443)
     * ở luồng nền để lấy tiêu đề trang web và gắn thẻ xem trước vào dưới bong bóng tin nhắn.
     */
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

    /** [UI -> GỬI] Mở menu chuột phải trên bong bóng tin nhắn (thả Reaction, Trả lời, Chuyển tiếp, Thu hồi). */
    private void showContextMenu(MouseEvent e) {
        if (msg.recalled || Message.KIND_SYSTEM.equals(msg.kind)) return;
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(Color.WHITE);
        menu.setBorder(BorderFactory.createLineBorder(Theme.BORDER_PINK, 1));

        JPanel reactRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4));
        reactRow.setBackground(Color.WHITE);
        for (String emoji : EmojiAssets.REACTION_EMOJIS) {
            ImageIcon ic = EmojiAssets.icon(emoji, 22);
            JButton b = ic != null ? new JButton(ic) : new JButton(emoji);
            if (ic == null) b.setFont(Theme.iconFont(16f));
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

        JMenuItem pinItem = new JMenuItem("📌 Ghim tin nhắn");
        pinItem.setFont(Theme.emojiFont(12.5f));
        pinItem.addActionListener(ev -> {
            if (onPin != null) onPin.accept(msg);
        });
        menu.add(pinItem);

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

    /** Thanh cảm xúc góc dưới bong bóng giống Zalo: huy hiệu đếm emoji màu + nút bung dải 6 emoji nổi. */
    private void renderReactions() {
        reactionsBar.removeAll();
        if (msg.recalled || Message.KIND_SYSTEM.equals(msg.kind)) {
            reactionsBar.setVisible(false);
        } else {
            reactionsBar.setVisible(true);
            Map<String, Integer> counts = new LinkedHashMap<>();
            if (msg.reactions != null) {
                for (String emoji : msg.reactions.values()) {
                    if (emoji != null && !emoji.isBlank()) {
                        counts.merge(emoji, 1, Integer::sum);
                    }
                }
            }
            String myEmoji = msg.reactions == null ? null : msg.reactions.get(meId);
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                String em = entry.getKey();
                int c = entry.getValue();
                boolean isMine = em.equals(myEmoji);
                Theme.RoundedPanel pill = new Theme.RoundedPanel(12,
                        isMine ? Theme.ITEM_SELECTED : Color.WHITE, null, Theme.BORDER_PINK);
                pill.setLayout(new FlowLayout(FlowLayout.CENTER, 3, 1));
                pill.setBorder(BorderFactory.createEmptyBorder(1, 5, 1, 5));
                pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                ImageIcon ic = EmojiAssets.icon(em, 14);
                JLabel lbl = ic != null ? new JLabel(String.valueOf(c), ic, SwingConstants.LEFT) : new JLabel(em + " " + c);
                lbl.setFont(Theme.uiFont(Font.BOLD, 11f));
                lbl.setForeground(Theme.TEXT_DARK);
                pill.add(lbl);
                pill.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (onReact != null) onReact.accept(msg, em);
                    }
                });
                reactionsBar.add(pill);
            }

            // Nút tròn thả cảm xúc góc dưới phải (giống biểu tượng 👍 nhỏ dưới góc bong bóng Zalo)
            Theme.RoundedPanel trigger = new Theme.RoundedPanel(12, Color.WHITE, null, Theme.BORDER_PINK);
            trigger.setLayout(new FlowLayout(FlowLayout.CENTER, 2, 1));
            trigger.setBorder(BorderFactory.createEmptyBorder(1, 5, 1, 5));
            trigger.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            trigger.setToolTipText("Thả biểu tượng cảm xúc");
            ImageIcon thumb = EmojiAssets.icon("👍", 13);
            JLabel plusLbl = thumb != null ? new JLabel("+", thumb, SwingConstants.LEFT) : new JLabel("+");
            plusLbl.setFont(Theme.uiFont(Font.BOLD, 10.5f));
            plusLbl.setForeground(Theme.TEXT_MUTED);
            trigger.add(plusLbl);
            trigger.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    showFloatingReactionBar(trigger);
                }
            });
            reactionsBar.add(trigger);
        }
        reactionsBar.revalidate();
        reactionsBar.repaint();
    }

    /** Bung thanh 6 emoji màu nổi (👍 ❤️ 😂 😮 😢 😡) giống Zalo khi bấm nút góc dưới bong bóng. */
    private void showFloatingReactionBar(Component anchor) {
        JPopupMenu pop = new JPopupMenu();
        pop.setBackground(Color.WHITE);
        pop.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_PINK, 1),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        row.setBackground(Color.WHITE);
        for (String em : EmojiAssets.REACTION_EMOJIS) {
            ImageIcon ic = EmojiAssets.icon(em, 24);
            JButton btn = ic != null ? new JButton(ic) : new JButton(em);
            if (ic == null) btn.setFont(Theme.iconFont(18f));
            btn.setFocusPainted(false);
            btn.setBorderPainted(false);
            btn.setContentAreaFilled(false);
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btn.setPreferredSize(new Dimension(34, 34));
            btn.addActionListener(ev -> {
                pop.setVisible(false);
                if (onReact != null) onReact.accept(msg, em);
            });
            row.add(btn);
        }
        pop.add(row);
        pop.show(anchor, -120, -48);
    }

    /** Giải phóng JavaFX MediaPlayer (nếu là bong bóng video) khi đổi phòng chat hoặc thoát app. */
    public void dispose() {
        if (activeVideoPlayer != null) {
            activeVideoPlayer.dispose();
            activeVideoPlayer = null;
        }
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

    /** [PUSH -> UI] Đổi nội dung bong bóng sang "Tin nhắn đã được thu hồi" khi nhận gói MSG_RECALLED. */
    public void markRecalled() {
        dispose();
        msg.recalled = true;
        if (msg.reactions != null) msg.reactions.clear();
        renderBubbleBody();
        renderReactions();
        if (metaLabel != null) metaLabel.setText(buildMetaText());
    }

    /** [PUSH -> UI] Cập nhật thanh reaction dưới bong bóng khi nhận gói MSG_REACTED từ server. */
    public void applyReaction(long userId, String emoji) {
        if (msg.reactions == null) msg.reactions = new LinkedHashMap<>();
        if (emoji == null || emoji.isBlank()) {
            msg.reactions.remove(userId);
        } else {
            msg.reactions.put(userId, emoji);
        }
        renderReactions();
    }

    /** [PUSH -> UI] Cập nhật trạng thái Đã nhận / Đã xem khi nhận gói SEEN_UPDATE hoặc DELIVERY_UPDATE. */
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
