package com.zalolite.client.ui;

import com.zalolite.client.Connection;
import com.zalolite.common.Conversation;
import com.zalolite.common.Message;
import com.zalolite.common.Packet;
import com.zalolite.common.PacketType;
import com.zalolite.common.User;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Khung chat chính: header, danh sách tin nhắn có phân trang & phân ngày, tìm kiếm, typing và composer. */
public class ChatPanel extends JPanel {
    private static final int PAGE_SIZE = 30;

    private final Connection conn;
    private final User me;
    private final Conversation conv;
    private final Consumer<Message> onSentCallback;
    private final Runnable onGroupLeft;
    private final Consumer<String> onGroupRenamed;

    // Header
    private final Theme.AvatarBadge headerAvatar;
    private final JLabel titleLabel = new JLabel();
    private final JLabel subtitleLabel = new JLabel();

    // Search bar
    private final JPanel searchBar = new JPanel(new BorderLayout(6, 6));
    private final JTextField searchField = new JTextField();
    private final JPanel searchResultsPanel = new JPanel();
    private final JScrollPane searchResultsScroll;

    // Message list
    private final JPanel messagesBox = new JPanel();
    private final JScrollPane scrollPane;
    private final JButton loadMoreBtn = Theme.softButton("↑ Tải thêm tin cũ");
    private final List<Message> messages = new ArrayList<>();
    private final Map<Long, MessageBubble> bubblesById = new HashMap<>();
    private final Map<Long, Long> seenByUser = new HashMap<>();
    private boolean hasMore;
    private boolean loadingHistory;

    // Typing
    private final JLabel typingLabel = new JLabel(" ");
    private final Timer typingClearTimer;
    private final Timer typingStopTimer;
    private long lastTypingSentMs;
    private boolean sentTypingTrue;

    // Composer
    private final JTextField inputField = new JTextField();
    private final JButton sendBtn = Theme.primaryButton("Gửi");

    public ChatPanel(Connection conn, User me, Conversation conv,
                     Consumer<Message> onSentCallback,
                     Consumer<String> onGroupRenamed,
                     Runnable onGroupLeft) {
        this.conn = conn;
        this.me = me;
        this.conv = conv;
        this.onSentCallback = onSentCallback;
        this.onGroupRenamed = onGroupRenamed;
        this.onGroupLeft = onGroupLeft;

        setLayout(new BorderLayout());
        setBackground(Theme.CHAT_BG);

        // --- Header ---
        JPanel topWrap = new JPanel(new BorderLayout());
        topWrap.setBackground(Color.WHITE);

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_PINK),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)
        ));

        headerAvatar = new Theme.AvatarBadge(
                conv.name, 40, Theme.avatarColor(conv.isGroup ? conv.id : conv.peerId, conv.isGroup),
                !conv.isGroup, conv.peerOnline);
        header.add(headerAvatar, BorderLayout.WEST);

        JPanel titleBox = new JPanel(new GridLayout(0, 1));
        titleBox.setOpaque(false);
        titleLabel.setText(conv.name);
        titleLabel.setFont(Theme.uiFont(Font.BOLD, 15f));
        titleLabel.setForeground(Theme.TEXT_DARK);
        subtitleLabel.setFont(Theme.uiFont(12f));
        refreshSubtitle();
        titleBox.add(titleLabel);
        titleBox.add(subtitleLabel);
        header.add(titleBox, BorderLayout.CENTER);

        JPanel headerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        headerActions.setOpaque(false);
        Theme.PillLabel p2pPill = new Theme.PillLabel("⚡ P2P", Theme.BUBBLE_OTHER, Theme.PRIMARY_PURPLE, 11.5f, true);
        p2pPill.setToolTipText("Sẵn sàng truyền tải P2P (Đợt 3)");
        headerActions.add(p2pPill);

        JButton searchToggle = iconButton("🔍", "Tìm kiếm trong cuộc trò chuyện");
        searchToggle.addActionListener(e -> toggleSearchBar());
        headerActions.add(searchToggle);

        if (conv.isGroup) {
            JButton groupBtn = iconButton("👥", "Thành viên & cài đặt nhóm");
            groupBtn.addActionListener(e -> openGroupManage());
            headerActions.add(groupBtn);
        }
        header.add(headerActions, BorderLayout.EAST);
        topWrap.add(header, BorderLayout.NORTH);

        // --- Search bar (ẩn mặc định) ---
        searchBar.setBackground(Theme.SIDEBAR_BG);
        searchBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_PINK),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));
        searchField.putClientProperty("JTextField.placeholderText", "Tìm tin nhắn trong hội thoại...");
        JButton doSearchBtn = Theme.primaryButton("Tìm");
        JButton closeSearchBtn = Theme.softButton("Đóng");
        JPanel searchInputRow = new JPanel(new BorderLayout(6, 0));
        searchInputRow.setOpaque(false);
        searchInputRow.add(searchField, BorderLayout.CENTER);
        JPanel searchBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        searchBtns.setOpaque(false);
        searchBtns.add(doSearchBtn);
        searchBtns.add(closeSearchBtn);
        searchInputRow.add(searchBtns, BorderLayout.EAST);

        searchResultsPanel.setLayout(new BoxLayout(searchResultsPanel, BoxLayout.Y_AXIS));
        searchResultsPanel.setBackground(Color.WHITE);
        searchResultsScroll = new JScrollPane(searchResultsPanel);
        searchResultsScroll.setPreferredSize(new Dimension(0, 130));
        searchResultsScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER_PINK));
        searchResultsScroll.setVisible(false);

        searchBar.add(searchInputRow, BorderLayout.NORTH);
        searchBar.add(searchResultsScroll, BorderLayout.CENTER);
        searchBar.setVisible(false);

        doSearchBtn.addActionListener(e -> runSearch());
        searchField.addActionListener(e -> runSearch());
        closeSearchBtn.addActionListener(e -> {
            searchBar.setVisible(false);
            searchResultsScroll.setVisible(false);
            searchField.setText("");
        });
        topWrap.add(searchBar, BorderLayout.SOUTH);
        add(topWrap, BorderLayout.NORTH);

        // --- Message list ---
        messagesBox.setLayout(new BoxLayout(messagesBox, BoxLayout.Y_AXIS));
        messagesBox.setBackground(Theme.CHAT_BG);
        messagesBox.setBorder(BorderFactory.createEmptyBorder(10, 4, 10, 4));

        loadMoreBtn.setAlignmentX(CENTER_ALIGNMENT);
        loadMoreBtn.addActionListener(e -> loadOlderHistory());

        scrollPane = new JScrollPane(messagesBox);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(Theme.CHAT_BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        add(scrollPane, BorderLayout.CENTER);

        // --- Bottom: typing + composer ---
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBackground(Color.WHITE);
        bottom.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER_PINK));

        typingLabel.setFont(Theme.uiFont(Font.ITALIC, 11.5f));
        typingLabel.setForeground(Theme.TEXT_MUTED);
        typingLabel.setBorder(BorderFactory.createEmptyBorder(4, 16, 2, 16));
        bottom.add(typingLabel, BorderLayout.NORTH);

        JPanel composer = new JPanel(new BorderLayout(8, 0));
        composer.setBackground(Color.WHITE);
        composer.setBorder(BorderFactory.createEmptyBorder(6, 12, 10, 12));

        JPanel leftTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftTools.setOpaque(false);
        JButton emojiBtn = iconButton("😊", "Chèn biểu tượng cảm xúc");
        emojiBtn.addActionListener(e -> {
            EmojiPicker picker = new EmojiPicker(this::insertEmoji);
            picker.show(emojiBtn, 0, -235);
        });
        leftTools.add(emojiBtn);
        // Hook giữ chỗ cho Đợt 3 (ảnh, tệp, ghi âm)
        leftTools.add(placeholderMediaButton("🖼️", "Gửi ảnh — Sẽ mở khoá ở Đợt 3"));
        leftTools.add(placeholderMediaButton("📎", "Gửi tệp — Sẽ mở khoá ở Đợt 3"));
        leftTools.add(placeholderMediaButton("🎤", "Gửi tin nhắn thoại — Sẽ mở khoá ở Đợt 3"));
        composer.add(leftTools, BorderLayout.WEST);

        inputField.setFont(Theme.emojiFont(13.5f));
        inputField.putClientProperty("JTextField.placeholderText", "Nhập tin nhắn gửi " + conv.name + "...");
        inputField.setPreferredSize(new Dimension(0, 38));
        composer.add(inputField, BorderLayout.CENTER);

        sendBtn.setPreferredSize(new Dimension(78, 38));
        composer.add(sendBtn, BorderLayout.EAST);

        bottom.add(composer, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        // Timers typing
        typingClearTimer = new Timer(3500, e -> typingLabel.setText(" "));
        typingClearTimer.setRepeats(false);

        typingStopTimer = new Timer(2500, e -> sendTypingState(false));
        typingStopTimer.setRepeats(false);

        // Events
        sendBtn.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());
        inputField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { onInputChanged(); }
            @Override
            public void removeUpdate(DocumentEvent e) { onInputChanged(); }
            @Override
            public void changedUpdate(DocumentEvent e) { onInputChanged(); }
        });

        loadInitialHistory();
    }

    public long getConvId() {
        return conv.id;
    }

    public Conversation getConversation() {
        return conv;
    }

    public void updateConversation(Conversation updated) {
        if (updated == null) return;
        conv.name = updated.name;
        conv.peerOnline = updated.peerOnline;
        conv.ownerId = updated.ownerId;
        titleLabel.setText(conv.name);
        headerAvatar.setNameAndColor(
                conv.name,
                Theme.avatarColor(conv.isGroup ? conv.id : conv.peerId, conv.isGroup),
                !conv.isGroup,
                conv.peerOnline);
        refreshSubtitle();
    }

    private void refreshSubtitle() {
        if (conv.isGroup) {
            subtitleLabel.setText("Nhóm chat Berry Pop");
            subtitleLabel.setForeground(Theme.TEXT_MUTED);
        } else if (conv.peerOnline) {
            subtitleLabel.setText("● Đang hoạt động");
            subtitleLabel.setForeground(Theme.ONLINE_GREEN);
        } else {
            subtitleLabel.setText("Ngoại tuyến");
            subtitleLabel.setForeground(Theme.TEXT_MUTED);
        }
    }

    private JButton iconButton(String emoji, String tooltip) {
        JButton b = new JButton(emoji);
        b.setFont(Theme.emojiFont(16f));
        b.setToolTipText(tooltip);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(34, 34));
        return b;
    }

    private JButton placeholderMediaButton(String emoji, String tooltip) {
        JButton b = iconButton(emoji, tooltip);
        b.setForeground(Theme.TEXT_MUTED);
        b.addActionListener(e -> JOptionPane.showMessageDialog(
                this, tooltip, "ZaloLite Berry", JOptionPane.INFORMATION_MESSAGE));
        return b;
    }

    private void insertEmoji(String emoji) {
        int pos = inputField.getCaretPosition();
        String cur = inputField.getText();
        String next = cur.substring(0, pos) + emoji + cur.substring(pos);
        inputField.setText(next);
        inputField.setCaretPosition(pos + emoji.length());
        inputField.requestFocusInWindow();
    }

    private void toggleSearchBar() {
        boolean show = !searchBar.isVisible();
        searchBar.setVisible(show);
        if (show) {
            searchField.requestFocusInWindow();
        } else {
            searchResultsScroll.setVisible(false);
        }
        revalidate();
    }

    private void runSearch() {
        String q = searchField.getText().trim();
        if (q.isEmpty()) return;
        Theme.runAsync(
                () -> conn.request(new Packet(PacketType.MSG_SEARCH)
                        .put("convId", conv.id).put("query", q)),
                res -> {
                    searchResultsPanel.removeAll();
                    if (!res.isOk()) {
                        searchResultsPanel.add(new JLabel("Lỗi tìm kiếm: " + res.str("error")));
                    } else {
                        List<Message> found = res.getList("messages", Message.class);
                        if (found.isEmpty()) {
                            JLabel empty = new JLabel("  Không tìm thấy tin nhắn nào khớp \"" + q + "\"");
                            empty.setForeground(Theme.TEXT_MUTED);
                            searchResultsPanel.add(empty);
                        } else {
                            for (Message m : found) {
                                JButton item = new JButton(
                                        (m.senderName == null ? "" : m.senderName + ": ")
                                                + (m.content == null ? "" : m.content)
                                                + "  (" + Theme.formatShortTime(m.createdAt) + ")");
                                item.setFont(Theme.emojiFont(12f));
                                item.setHorizontalAlignment(SwingConstants.LEFT);
                                item.setBorderPainted(false);
                                item.setContentAreaFilled(false);
                                item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                                item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
                                item.addActionListener(ev -> scrollToMessage(m.id));
                                searchResultsPanel.add(item);
                            }
                        }
                    }
                    searchResultsScroll.setVisible(true);
                    searchResultsPanel.revalidate();
                    searchResultsPanel.repaint();
                    revalidate();
                },
                err -> JOptionPane.showMessageDialog(this, err)
        );
    }

    private void scrollToMessage(long msgId) {
        MessageBubble b = bubblesById.get(msgId);
        if (b != null) {
            b.scrollRectToVisible(b.getBounds());
        } else {
            JOptionPane.showMessageDialog(this, "Tin nhắn này nằm ở trang cũ hơn — bấm \"Tải thêm tin cũ\" để xem.");
        }
    }

    private void openGroupManage() {
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        new GroupManageDialog(owner, conn, me, conv, newName -> {
            conv.name = newName;
            titleLabel.setText(newName);
            if (onGroupRenamed != null) onGroupRenamed.accept(newName);
        }, () -> {
            if (onGroupLeft != null) onGroupLeft.run();
        }).setVisible(true);
    }

    // --- Lịch sử & render tin nhắn ---

    private void loadInitialHistory() {
        if (loadingHistory) return;
        loadingHistory = true;
        Theme.runAsync(
                () -> conn.request(new Packet(PacketType.MSG_HISTORY)
                        .put("convId", conv.id).put("limit", PAGE_SIZE)),
                res -> {
                    loadingHistory = false;
                    if (!res.isOk()) return;
                    List<Message> list = res.getList("messages", Message.class);
                    hasMore = res.bool("hasMore");
                    messages.clear();
                    messages.addAll(list);
                    rebuildMessageList();
                    scrollToBottom();
                    markNewestSeen();
                },
                err -> loadingHistory = false
        );
    }

    private void loadOlderHistory() {
        if (loadingHistory || messages.isEmpty()) return;
        loadingHistory = true;
        loadMoreBtn.setEnabled(false);
        long oldestId = messages.get(0).id;
        int oldMax = scrollPane.getVerticalScrollBar().getMaximum();
        int oldVal = scrollPane.getVerticalScrollBar().getValue();

        Theme.runAsync(
                () -> conn.request(new Packet(PacketType.MSG_HISTORY)
                        .put("convId", conv.id)
                        .put("beforeId", oldestId)
                        .put("limit", PAGE_SIZE)),
                res -> {
                    loadingHistory = false;
                    loadMoreBtn.setEnabled(true);
                    if (!res.isOk()) return;
                    List<Message> older = res.getList("messages", Message.class);
                    hasMore = res.bool("hasMore");
                    if (!older.isEmpty()) {
                        Set<Long> existing = new HashSet<>(bubblesById.keySet());
                        List<Message> toPrepend = new ArrayList<>();
                        for (Message m : older) {
                            if (!existing.contains(m.id)) toPrepend.add(m);
                        }
                        messages.addAll(0, toPrepend);
                        rebuildMessageList();
                        // Giữ nguyên vị trí cuộn để người dùng không bị giật lên đầu
                        SwingUtilities.invokeLater(() -> {
                            int newMax = scrollPane.getVerticalScrollBar().getMaximum();
                            scrollPane.getVerticalScrollBar().setValue(oldVal + Math.max(0, newMax - oldMax));
                        });
                    } else {
                        rebuildMessageList();
                    }
                },
                err -> {
                    loadingHistory = false;
                    loadMoreBtn.setEnabled(true);
                }
        );
    }

    private void rebuildMessageList() {
        messagesBox.removeAll();
        bubblesById.clear();

        if (hasMore) {
            JPanel wrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 6));
            wrap.setOpaque(false);
            wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            wrap.add(loadMoreBtn);
            messagesBox.add(wrap);
        }

        LocalDate prevDay = null;
        for (Message m : messages) {
            LocalDate d = Theme.dayOf(m.createdAt);
            if (prevDay == null || !d.equals(prevDay)) {
                messagesBox.add(buildDateDivider(m.createdAt));
                prevDay = d;
            }
            MessageBubble bubble = new MessageBubble(m, me.id, conv.isGroup, this::sendReact, this::sendRecall);
            bubblesById.put(m.id, bubble);
            messagesBox.add(bubble);
        }

        messagesBox.revalidate();
        messagesBox.repaint();
    }

    private JPanel buildDateDivider(long epochMillis) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        Theme.PillLabel pill = new Theme.PillLabel(
                Theme.formatDateDivider(epochMillis),
                Theme.ITEM_SELECTED, Theme.TEXT_MUTED, 11f, false);
        pill.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 12));
        p.add(pill);
        return p;
    }

    private void appendMessageToView(Message m) {
        if (m == null || bubblesById.containsKey(m.id)) return;
        LocalDate prevDay = messages.isEmpty() ? null : Theme.dayOf(messages.get(messages.size() - 1).createdAt);
        LocalDate newDay = Theme.dayOf(m.createdAt);
        if (prevDay == null || !newDay.equals(prevDay)) {
            messagesBox.add(buildDateDivider(m.createdAt));
        }
        messages.add(m);
        MessageBubble bubble = new MessageBubble(m, me.id, conv.isGroup, this::sendReact, this::sendRecall);
        bubblesById.put(m.id, bubble);
        messagesBox.add(bubble);
        messagesBox.revalidate();
        messagesBox.repaint();
        scrollToBottom();
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    // --- Gửi tin, cảm xúc, thu hồi, đã xem, đang gõ ---

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        inputField.setText("");
        typingStopTimer.stop();
        sendTypingState(false);
        sendBtn.setEnabled(false);

        Theme.runAsync(
                () -> conn.request(new Packet(PacketType.MSG_SEND)
                        .put("convId", conv.id)
                        .put("kind", Message.KIND_TEXT)
                        .put("content", text)),
                res -> {
                    sendBtn.setEnabled(true);
                    if (!res.isOk()) {
                        JOptionPane.showMessageDialog(this, res.str("error"));
                        return;
                    }
                    Message sent = res.get("message", Message.class);
                    if (sent != null) {
                        appendMessageToView(sent);
                        if (onSentCallback != null) onSentCallback.accept(sent);
                    }
                },
                err -> {
                    sendBtn.setEnabled(true);
                    JOptionPane.showMessageDialog(this, err);
                }
        );
    }

    private void sendReact(Message m, String emoji) {
        Theme.runAsync(
                () -> conn.request(new Packet(PacketType.MSG_REACT)
                        .put("msgId", m.id).put("emoji", emoji)),
                res -> {
                    if (res.isOk()) {
                        // Cập nhật ngay ở local nếu server không gửi ngược lại chính mình
                        onMessageReacted(m.id, me.id, emoji);
                    }
                },
                null
        );
    }

    private void sendRecall(Message m) {
        int ok = JOptionPane.showConfirmDialog(this,
                "Thu hồi tin nhắn này cho mọi người?", "Thu hồi tin nhắn", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;
        Theme.runAsync(
                () -> conn.request(new Packet(PacketType.MSG_RECALL).put("msgId", m.id)),
                res -> {
                    if (res.isOk()) {
                        onMessageRecalled(m.id);
                    } else {
                        JOptionPane.showMessageDialog(this, res.str("error"));
                    }
                },
                err -> JOptionPane.showMessageDialog(this, err)
        );
    }

    private void markNewestSeen() {
        if (messages.isEmpty()) return;
        long newestId = messages.get(messages.size() - 1).id;
        sendSeen(newestId);
    }

    private void sendSeen(long lastReadMsgId) {
        Theme.runAsync(() -> {
            conn.send(new Packet(PacketType.MSG_SEEN)
                    .put("convId", conv.id)
                    .put("lastReadMsgId", lastReadMsgId));
            return null;
        }, null, null);
    }

    private void onInputChanged() {
        boolean hasText = !inputField.getText().trim().isEmpty();
        if (!hasText) {
            typingStopTimer.stop();
            sendTypingState(false);
            return;
        }
        long now = System.currentTimeMillis();
        if (!sentTypingTrue || now - lastTypingSentMs > 2000) {
            sendTypingState(true);
            lastTypingSentMs = now;
        }
        typingStopTimer.restart();
    }

    private void sendTypingState(boolean typing) {
        if (!typing && !sentTypingTrue) return;
        sentTypingTrue = typing;
        Theme.runAsync(() -> {
            conn.send(new Packet(PacketType.TYPING)
                    .put("convId", conv.id)
                    .put("typing", typing));
            return null;
        }, null, null);
    }

    // --- Sự kiện đẩy từ Server (đã chạy trên EDT) ---

    public void onNewMessage(Message m) {
        appendMessageToView(m);
        if (m.senderId != me.id) {
            sendSeen(m.id);
        }
    }

    public void onMessageRecalled(long msgId) {
        MessageBubble b = bubblesById.get(msgId);
        if (b != null) b.markRecalled();
    }

    public void onMessageReacted(long msgId, long userId, String emoji) {
        MessageBubble b = bubblesById.get(msgId);
        if (b != null) b.applyReaction(userId, emoji);
    }

    public void onTyping(long userId, String userName, boolean typing) {
        if (userId == me.id) return;
        if (typing) {
            String who = (userName == null || userName.isBlank()) ? "Ai đó" : userName;
            typingLabel.setText(who + " đang soạn tin...");
            typingClearTimer.restart();
        } else {
            typingClearTimer.stop();
            typingLabel.setText(" ");
        }
    }

    public void onSeenUpdate(long userId, long lastReadMsgId) {
        if (userId == me.id) return;
        seenByUser.merge(userId, lastReadMsgId, Math::max);
        for (Message m : messages) {
            if (m.senderId == me.id && m.id <= lastReadMsgId) {
                int count = 0;
                for (long readId : seenByUser.values()) {
                    if (readId >= m.id) count++;
                }
                MessageBubble b = bubblesById.get(m.id);
                if (b != null) b.updateDelivery(Message.STATE_SEEN, count);
            }
        }
    }

    public void onDelivered(long msgId) {
        MessageBubble b = bubblesById.get(msgId);
        if (b != null && !Message.STATE_SEEN.equals(b.getMessage().deliveryState)) {
            b.updateDelivery(Message.STATE_DELIVERED, b.getMessage().seenCount);
        }
    }

    public void onPresence(long userId, boolean online) {
        if (!conv.isGroup && conv.peerId == userId) {
            conv.peerOnline = online;
            headerAvatar.setOnline(online);
            refreshSubtitle();
        }
    }
}
