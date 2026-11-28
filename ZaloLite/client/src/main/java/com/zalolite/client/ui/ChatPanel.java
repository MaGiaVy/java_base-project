package com.zalolite.client.ui;

import com.zalolite.client.AudioHelper;
import com.zalolite.client.Connection;
import com.zalolite.client.FileTransfer;
import com.zalolite.client.P2pTransfer;
import com.zalolite.common.Conversation;
import com.zalolite.common.Message;
import com.zalolite.common.Packet;
import com.zalolite.common.PacketType;
import com.zalolite.common.User;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Khung trò chuyện chính cho một cuộc hội thoại (chat 1-1, nhóm chat, hoặc Cloud của tôi):
 *  - Header: avatar + trạng thái online, nút gọi thoại UDP 1-1, thanh tìm kiếm tin nhắn ({@code MSG_SEARCH}), quản lý nhóm.
 *  - Danh sách tin nhắn: phân trang lịch sử ({@code MSG_HISTORY}), chia mốc ngày, bong bóng {@link MessageBubble}.
 *  - Khung soạn tin (Composer): gửi text ({@code MSG_SEND}), emoji, gửi ảnh/file/video/voice ưu tiên P2P trực tiếp
 *    ({@link P2pTransfer}) rồi tự fallback sang FileServer TCP :9001 ({@link FileTransfer}), kéo-thả file (Drag & Drop),
 *    dán ảnh clipboard ({@code Ctrl+V}), trả lời (Reply), chuyển tiếp (Forward), thả cảm xúc (Reaction), thu hồi (Recall),
 *    báo đang gõ ({@code TYPING}) và đánh dấu đã xem ({@code MSG_SEEN}).
 */
public class ChatPanel extends JPanel {
    private static final int PAGE_SIZE = 30;

    private final Connection conn;
    private final User me;
    private final Conversation conv;
    private final Map<String, File> localMediaFiles;
    private final Supplier<List<Conversation>> conversationsSupplier;
    private final Runnable onStartCall;
    private final AudioHelper audioHelper = new AudioHelper();
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
    private final JButton voiceBtn;
    private final JProgressBar transferBar = new JProgressBar(0, 100);
    private final JPanel replyBar = new JPanel(new BorderLayout(8, 0));
    private final JLabel replyBarLabel = new JLabel();
    private Message replyTarget;

    public ChatPanel(Connection conn, User me, Conversation conv,
                     Map<String, File> localMediaFiles,
                     Supplier<List<Conversation>> conversationsSupplier,
                     Runnable onStartCall,
                     Consumer<Message> onSentCallback,
                     Consumer<String> onGroupRenamed,
                     Runnable onGroupLeft) {
        this.conn = conn;
        this.me = me;
        this.conv = conv;
        this.localMediaFiles = localMediaFiles;
        this.conversationsSupplier = conversationsSupplier;
        this.onStartCall = onStartCall;
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
        if (!conv.isCloud) {
            Theme.PillLabel p2pPill = new Theme.PillLabel("⚡ P2P", Theme.BUBBLE_OTHER, Theme.PRIMARY_PURPLE, 11.5f, true);
            p2pPill.setToolTipText("File/ảnh/thoại ưu tiên P2P trực tiếp, tự chuyển về Server nếu ngoại tuyến");
            headerActions.add(p2pPill);
        }

        // [UI -> GỬI] Nút gọi thoại 1-1 (chỉ hiện với chat trực tiếp): gọi callback MainFrame.startOutgoingCall()
        if (!conv.isGroup && !conv.isCloud && onStartCall != null) {
            JButton callBtn = iconButton("📞", "Gọi thoại 1-1 realtime (UDP)");
            callBtn.addActionListener(e -> onStartCall.run());
            headerActions.add(callBtn);
        }

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

        JPanel topBottom = new JPanel(new BorderLayout());
        topBottom.setOpaque(false);
        typingLabel.setFont(Theme.uiFont(Font.ITALIC, 11.5f));
        typingLabel.setForeground(Theme.TEXT_MUTED);
        typingLabel.setBorder(BorderFactory.createEmptyBorder(4, 16, 2, 16));
        topBottom.add(typingLabel, BorderLayout.NORTH);

        transferBar.setStringPainted(true);
        transferBar.setForeground(Theme.PRIMARY_RED);
        transferBar.setBackground(Theme.BUBBLE_OTHER);
        transferBar.setFont(Theme.uiFont(Font.BOLD, 11f));
        transferBar.setPreferredSize(new Dimension(0, 18));
        transferBar.setVisible(false);

        replyBar.setBackground(Theme.BUBBLE_OTHER);
        replyBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, Theme.PRIMARY_PURPLE),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        replyBarLabel.setFont(Theme.emojiFont(12f));
        replyBarLabel.setForeground(Theme.TEXT_DARK);
        JButton cancelReplyBtn = iconButton("✕", "Huỷ trả lời");
        cancelReplyBtn.setPreferredSize(new Dimension(24, 24));
        cancelReplyBtn.addActionListener(e -> clearReply());
        replyBar.add(replyBarLabel, BorderLayout.CENTER);
        replyBar.add(cancelReplyBtn, BorderLayout.EAST);
        replyBar.setVisible(false);

        JPanel barsStack = new JPanel();
        barsStack.setOpaque(false);
        barsStack.setLayout(new BoxLayout(barsStack, BoxLayout.Y_AXIS));
        barsStack.add(transferBar);
        barsStack.add(replyBar);
        topBottom.add(barsStack, BorderLayout.SOUTH);
        bottom.add(topBottom, BorderLayout.NORTH);

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

        JButton imgBtn = iconButton("🖼️", "Gửi hình ảnh");
        imgBtn.addActionListener(e -> chooseAndSendImage());
        leftTools.add(imgBtn);

        JButton fileBtn = iconButton("📎", "Gửi tệp, video hoặc âm thanh (.wav)");
        fileBtn.addActionListener(e -> chooseAndSendFile());
        leftTools.add(fileBtn);

        voiceBtn = iconButton("🎤", "Bấm để ghi âm tin nhắn thoại (tối đa 60s)");
        voiceBtn.addActionListener(e -> toggleVoiceRecord());
        leftTools.add(voiceBtn);
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

        // [UI -> GỬI] Gắn sự kiện nút Gửi / phím Enter -> sendMessage(); gõ chữ -> phát gói TYPING
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

        // Kích hoạt kéo-thả file (Drag & Drop) và dán ảnh từ clipboard (Ctrl+V), sau đó tải 30 tin gần nhất
        installDragAndDrop();
        installClipboardImagePaste();
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
        conv.memberCount = updated.memberCount;
        titleLabel.setText(conv.name);
        headerAvatar.setNameAndColor(
                conv.name,
                Theme.avatarColor(conv.isGroup ? conv.id : conv.peerId, conv.isGroup),
                !conv.isGroup,
                conv.peerOnline);
        refreshSubtitle();
    }

    /** Dọn mic/loa và timer khi đổi hội thoại hoặc đóng cửa sổ. */
    public void cleanup() {
        typingClearTimer.stop();
        typingStopTimer.stop();
        audioHelper.stopPlayback();
        if (audioHelper.isRecording()) {
            try { audioHelper.stopRecording(); } catch (Exception ignored) {}
        }
    }

    private void refreshSubtitle() {
        if (conv.isCloud) {
            subtitleLabel.setText("☁️ Lưu trữ tệp và ghi chú cá nhân");
            subtitleLabel.setForeground(Theme.PRIMARY_PURPLE);
        } else if (conv.isGroup) {
            subtitleLabel.setText(conv.memberCount > 0 ? conv.memberCount + " thành viên" : "Nhóm chat Berry Pop");
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

    /** [UI -> GỬI] Tìm kiếm tin nhắn trong hội thoại hiện tại bằng gói MSG_SEARCH qua TLS (:9000). */
    private void runSearch() {
        String q = searchField.getText().trim();
        if (q.isEmpty()) return;
        Theme.runAsync(
                // [GỬI] Gửi từ khóa tìm kiếm lên server
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

    /** [UI -> GỬI] Tải trang lịch sử tin nhắn mới nhất (MSG_HISTORY, tối đa 30 tin) khi mở hội thoại. */
    private void loadInitialHistory() {
        if (loadingHistory) return;
        loadingHistory = true;
        Theme.runAsync(
                // [GỬI] Yêu cầu 30 tin nhắn gần nhất qua TLS (:9000)
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

    /** [UI -> GỬI] Bấm "Tải thêm tin cũ": gửi MSG_HISTORY kèm beforeId để nạp thêm 30 tin cũ hơn. */
    private void loadOlderHistory() {
        if (loadingHistory || messages.isEmpty()) return;
        loadingHistory = true;
        loadMoreBtn.setEnabled(false);
        long oldestId = messages.get(0).id;
        int oldMax = scrollPane.getVerticalScrollBar().getMaximum();
        int oldVal = scrollPane.getVerticalScrollBar().getValue();

        Theme.runAsync(
                // [GỬI] Yêu cầu phân trang lịch sử trước oldestId qua TLS (:9000)
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
            MessageBubble bubble = new MessageBubble(m, me.id, conv.isGroup, this::sendReact, this::sendRecall,
                    this::startReply, this::forwardMessage, this::resolveMediaFile, audioHelper);
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
        MessageBubble bubble = new MessageBubble(m, me.id, conv.isGroup, this::sendReact, this::sendRecall,
                this::startReply, this::forwardMessage, this::resolveMediaFile, audioHelper);
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

    /** [UI -> GỬI] Bấm nút "Gửi" hoặc nhấn Enter: gửi tin nhắn văn bản (MSG_SEND, có thể kèm replyTo) qua TLS (:9000). */
    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        Long replyId = replyTarget == null ? null : replyTarget.id;
        clearReply();
        inputField.setText("");
        typingStopTimer.stop();
        sendTypingState(false);
        sendBtn.setEnabled(false);

        Theme.runAsync(
                () -> {
                    // [GỬI] Đóng gói MSG_SEND (kind=TEXT) gửi lên server qua kênh TLS (:9000)
                    Packet req = new Packet(PacketType.MSG_SEND)
                            .put("convId", conv.id)
                            .put("kind", Message.KIND_TEXT)
                            .put("content", text);
                    if (replyId != null) req.put("replyTo", replyId);
                    return conn.request(req);
                },
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

    // --- Gửi ảnh, file, video, tin nhắn thoại (P2P ưu tiên -> Server fallback) ---

    private void chooseAndSendImage() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Chọn hình ảnh");
        fc.setFileFilter(new FileNameExtensionFilter("Hình ảnh (PNG, JPG, GIF)", "png", "jpg", "jpeg", "gif"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            sendMediaFile(fc.getSelectedFile(), Message.KIND_IMAGE, 0);
        }
    }

    private void chooseAndSendFile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Chọn tệp / video / âm thanh");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            String lower = f.getName().toLowerCase();
            if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")) {
                sendMediaFile(f, Message.KIND_IMAGE, 0);
            } else if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv")
                    || lower.endsWith(".avi") || lower.endsWith(".webm")) {
                sendMediaFile(f, Message.KIND_VIDEO, 0);
            } else if (lower.endsWith(".wav")) {
                int dur = 0;
                try { dur = AudioHelper.wavDurationSec(f); } catch (Exception ignored) {}
                sendMediaFile(f, Message.KIND_AUDIO, dur);
            } else {
                sendMediaFile(f, Message.KIND_FILE, 0);
            }
        }
    }

    private void toggleVoiceRecord() {
        if (audioHelper.isRecording()) {
            finishVoiceRecord();
            return;
        }
        try {
            audioHelper.startRecording(() -> SwingUtilities.invokeLater(this::finishVoiceRecord));
            voiceBtn.setText("⏹");
            voiceBtn.setForeground(Theme.PRIMARY_RED);
            voiceBtn.setToolTipText("Đang ghi âm... Bấm lại để gửi");
        } catch (Exception ex) {
            // Dự phòng nếu máy demo không có micro -> cho chọn file .wav có sẵn
            int pick = JOptionPane.showConfirmDialog(this,
                    "Không mở được micro (" + ex.getMessage() + ").\nBạn muốn chọn file âm thanh (.wav) có sẵn để gửi không?",
                    "Tin nhắn thoại", JOptionPane.YES_NO_OPTION);
            if (pick == JOptionPane.YES_OPTION) {
                JFileChooser fc = new JFileChooser();
                fc.setFileFilter(new FileNameExtensionFilter("Âm thanh WAV (*.wav)", "wav"));
                if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    File f = fc.getSelectedFile();
                    int dur = 0;
                    try { dur = AudioHelper.wavDurationSec(f); } catch (Exception ignored) {}
                    sendMediaFile(f, Message.KIND_AUDIO, dur);
                }
            }
        }
    }

    private void finishVoiceRecord() {
        voiceBtn.setText("🎤");
        voiceBtn.setForeground(Theme.TEXT_DARK);
        voiceBtn.setToolTipText("Bấm để ghi âm tin nhắn thoại (tối đa 60s)");
        if (!audioHelper.isRecording()) return;
        try {
            AudioHelper.RecordedAudio rec = audioHelper.stopRecording();
            if (rec != null && rec.file().exists() && rec.file().length() > 44) {
                sendMediaFile(rec.file(), Message.KIND_AUDIO, rec.durationSec());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi lưu âm thanh: " + ex.getMessage());
        }
    }

    private void showProgress(String label, long done, long total) {
        SwingUtilities.invokeLater(() -> {
            int pct = total > 0 ? (int) Math.min(100, (done * 100) / total) : 0;
            transferBar.setValue(pct);
            transferBar.setString(label + " " + pct + "%");
            transferBar.setVisible(true);
        });
    }

    private void hideProgress() {
        SwingUtilities.invokeLater(() -> transferBar.setVisible(false));
    }

    /**
     * [UI -> GỬI] Quy trình gửi file/ảnh/video/voice lai (Hybrid P2P -> Server fallback):
     *  1) Nếu là chat 1-1 (không phải nhóm/Cloud): hỏi server {@code P2P_LOOKUP} để lấy IP + cổng P2P đối phương,
     *     thử truyền thẳng qua {@link P2pTransfer#sendDirect} (timeout 1.5s).
     *  2) Nếu đối phương offline hoặc tường lửa chặn: tự động fallback tải lên FileServer TCP :9001 qua {@link FileTransfer#upload}.
     *  3) Cuối cùng gửi gói metadata {@code MSG_SEND} qua TLS (:9000) kèm nhãn {@code transport} ("P2P" hoặc "SERVER").
     */
    private void sendMediaFile(File file, String kind, int durationSec) {
        if (file == null || !file.isFile()) return;
        if (file.length() > FileTransfer.MAX_FILE_SIZE) {
            JOptionPane.showMessageDialog(this, "Tệp vượt quá giới hạn 100MB.");
            return;
        }
        showProgress("Đang chuẩn bị " + file.getName(), 0, file.length());
        Theme.runAsync(() -> {
            String transport = "SERVER";
            String filePath = null;

            // 1. [GỬI] Ưu tiên P2P trực tiếp cho chat 1-1 (bỏ qua Cloud của tôi)
            if (!conv.isGroup && !conv.isCloud && conv.peerId > 0) {
                try {
                    // Hỏi server IP + cổng P2P đang lắng nghe của đối phương
                    Packet lk = conn.request(new Packet(PacketType.P2P_LOOKUP).put("userId", conv.peerId));
                    if (lk.isOk() && lk.bool("online")) {
                        String ip = lk.str("ip");
                        int port = (int) lk.num("port");
                        if (ip != null && port > 0) {
                            // [CỔNG/GIAO THỨC] Truyền file TCP trực tiếp tới ip:port P2P của đối phương
                            P2pTransfer.sendDirect(ip, port, conv.id, me.id, kind, file, durationSec,
                                    (d, t) -> showProgress("⚡ P2P: " + file.getName(), d, t));
                            transport = "P2P";
                            filePath = "p2p://" + conv.id + "/" + P2pTransfer.sanitizeFileName(file.getName());
                        }
                    }
                } catch (Exception ignored) {
                    // Tự động rơi về Server nếu P2P timeout / Firewall chặn
                }
            }

            // 2. [GỬI] Fallback tải file lên FileServer TCP cổng 9001
            if (filePath == null) {
                transport = "SERVER";
                filePath = FileTransfer.upload(conn.getHost(), conn.getFilePort(), file,
                        (d, t) -> showProgress("Đang gửi (Server): " + file.getName(), d, t));
            }
            if (localMediaFiles != null) {
                localMediaFiles.put(filePath, file);
            }

            // 3. [GỬI] Gửi gói tin metadata MSG_SEND qua kênh TLS (:9000) để lưu lịch sử và thông báo người nhận
            Packet req = new Packet(PacketType.MSG_SEND)
                    .put("convId", conv.id)
                    .put("kind", kind)
                    .put("content", file.getName())
                    .put("filePath", filePath)
                    .put("fileName", file.getName())
                    .put("fileSize", file.length())
                    .put("durationSec", durationSec)
                    .put("transport", transport);
            return conn.request(req);
        }, res -> {
            hideProgress();
            if (!res.isOk()) {
                JOptionPane.showMessageDialog(this, res.str("error"));
                return;
            }
            Message sent = res.get("message", Message.class);
            if (sent != null) {
                appendMessageToView(sent);
                if (onSentCallback != null) onSentCallback.accept(sent);
            }
        }, err -> {
            hideProgress();
            JOptionPane.showMessageDialog(this, "Không gửi được tệp: " + err);
        });
    }

    /**
     * [NHẬN] Tìm file đính kèm theo thứ tự: cache RAM/P2P cục bộ -> cache đĩa tạm -> tải từ FileServer TCP :9001
     * ({@link FileTransfer#download}) rồi trả về {@link MessageBubble} để hiển thị ảnh / mở file / phát âm thanh.
     */
    private void resolveMediaFile(Message m, Consumer<File> onReady) {
        if (m == null || m.filePath == null || m.filePath.isBlank()) {
            onReady.accept(null);
            return;
        }
        File local = localMediaFiles == null ? null : localMediaFiles.get(m.filePath);
        if (local != null && local.isFile()) {
            onReady.accept(local);
            return;
        }
        if (m.filePath.startsWith("p2p://")) {
            // Không hiện JOptionPane để tránh bão popup khi mở lại lịch sử nhiều ảnh P2P cũ
            onReady.accept(null);
            return;
        }
        File cached = FileTransfer.cacheFile(m.filePath, m.fileName == null ? "file" : m.fileName);
        if (cached.isFile() && (m.fileSize <= 0 || cached.length() == m.fileSize)) {
            if (localMediaFiles != null) localMediaFiles.put(m.filePath, cached);
            onReady.accept(cached);
            return;
        }
        showProgress("Đang tải " + (m.fileName == null ? "tệp" : m.fileName), 0, Math.max(1, m.fileSize));
        Theme.runAsync(
                () -> FileTransfer.download(conn.getHost(), conn.getFilePort(), m.filePath, cached,
                        (d, t) -> showProgress("Đang tải: " + (m.fileName == null ? "tệp" : m.fileName), d, t)),
                file -> {
                    hideProgress();
                    if (localMediaFiles != null) localMediaFiles.put(m.filePath, file);
                    onReady.accept(file);
                },
                err -> {
                    hideProgress();
                    onReady.accept(null);
                }
        );
    }

    private void startReply(Message m) {
        replyTarget = m;
        String who = m.senderName != null ? m.senderName : "Tin nhắn";
        String body = m.content != null ? m.content : (m.fileName != null ? m.fileName : "");
        if (body.length() > 60) body = body.substring(0, 60) + "…";
        replyBarLabel.setText("↩ Đang trả lời " + who + ": " + body);
        replyBar.setVisible(true);
        inputField.requestFocusInWindow();
        revalidate();
    }

    private void clearReply() {
        replyTarget = null;
        replyBar.setVisible(false);
        revalidate();
    }

    /** [UI -> GỬI] Chuyển tiếp tin nhắn sang hội thoại khác (gửi MSG_SEND với cờ forwarded=true; nếu là file P2P thì upload lên FileServer trước). */
    private void forwardMessage(Message m) {
        if (conversationsSupplier == null) return;
        List<Conversation> list = conversationsSupplier.get();
        if (list == null || list.isEmpty()) return;
        String[] names = list.stream().map(c -> c.name + (c.isGroup ? " (Nhóm)" : "")).toArray(String[]::new);
        Object pick = JOptionPane.showInputDialog(this, "Chọn hội thoại để chuyển tiếp:",
                "Chuyển tiếp tin nhắn", JOptionPane.PLAIN_MESSAGE, null, names, names[0]);
        if (pick == null) return;
        int idx = java.util.Arrays.asList(names).indexOf(pick);
        if (idx < 0) return;
        Conversation dest = list.get(idx);
        Theme.runAsync(
                () -> {
                    String fp = m.filePath;
                    String tr = m.transport;
                    // Nếu tin gốc là P2P, tải lên FileServer để hội thoại đích nhận được đầy đủ
                    if (fp != null && fp.startsWith("p2p://")) {
                        File local = localMediaFiles == null ? null : localMediaFiles.get(fp);
                        if (local == null || !local.isFile()) {
                            throw new Exception("Tệp P2P không còn trên máy để chuyển tiếp");
                        }
                        fp = FileTransfer.upload(conn.getHost(), conn.getFilePort(), local, null);
                        tr = "SERVER";
                        localMediaFiles.put(fp, local);
                    }
                    return conn.request(new Packet(PacketType.MSG_SEND)
                            .put("convId", dest.id)
                            .put("kind", m.kind == null ? Message.KIND_TEXT : m.kind)
                            .put("content", m.content)
                            .put("filePath", fp)
                            .put("fileName", m.fileName)
                            .put("fileSize", m.fileSize)
                            .put("durationSec", m.durationSec)
                            .put("transport", tr)
                            .put("forwarded", true));
                },
                res -> {
                    if (!res.isOk()) {
                        JOptionPane.showMessageDialog(this, res.str("error"));
                    } else if (dest.id == conv.id) {
                        Message sent = res.get("message", Message.class);
                        if (sent != null) appendMessageToView(sent);
                    } else {
                        JOptionPane.showMessageDialog(this, "Đã chuyển tiếp tới " + dest.name);
                    }
                },
                err -> JOptionPane.showMessageDialog(this, err)
        );
    }

    /** [UI -> GỬI] Cài đặt kéo-thả file (Drag & Drop) vào khung chat để tự phân loại ảnh/video/wav/file và gửi đi. */
    private void installDragAndDrop() {
        TransferHandler handler = new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    List<File> files = (List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    for (File f : files) {
                        if (f.isFile()) sendAutoTypedFile(f);
                    }
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };
        setTransferHandler(handler);
        messagesBox.setTransferHandler(handler);
    }

    private void sendAutoTypedFile(File f) {
        String lower = f.getName().toLowerCase();
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")) {
            sendMediaFile(f, Message.KIND_IMAGE, 0);
        } else if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv")
                || lower.endsWith(".avi") || lower.endsWith(".webm")) {
            sendMediaFile(f, Message.KIND_VIDEO, 0);
        } else if (lower.endsWith(".wav")) {
            int dur = 0;
            try { dur = AudioHelper.wavDurationSec(f); } catch (Exception ignored) {}
            sendMediaFile(f, Message.KIND_AUDIO, dur);
        } else {
            sendMediaFile(f, Message.KIND_FILE, 0);
        }
    }

    /** [UI -> GỬI] Bắt tổ hợp phím Ctrl+V trên ô nhập tin: nếu clipboard chứa ảnh chụp màn hình thì lưu tạm .png và gửi đi. */
    private void installClipboardImagePaste() {
        KeyStroke pasteKey = KeyStroke.getKeyStroke(KeyEvent.VK_V, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        Action defaultPaste = inputField.getActionMap().get("paste-from-clipboard");
        inputField.getInputMap(JComponent.WHEN_FOCUSED).put(pasteKey, "zalolite-paste");
        inputField.getActionMap().put("zalolite-paste", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
                    Transferable tr = cb.getContents(null);
                    if (tr != null && tr.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                        Image img = (Image) tr.getTransferData(DataFlavor.imageFlavor);
                        BufferedImage bi = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g = bi.createGraphics();
                        g.drawImage(img, 0, 0, null);
                        g.dispose();
                        File tmp = File.createTempFile("clipboard_", ".png");
                        ImageIO.write(bi, "png", tmp);
                        sendMediaFile(tmp, Message.KIND_IMAGE, 0);
                        return;
                    }
                } catch (Exception ignored) {
                }
                if (defaultPaste != null) defaultPaste.actionPerformed(e);
                else inputField.paste();
            }
        });
    }

    /** [UI -> GỬI] Thả biểu tượng cảm xúc lên tin nhắn (MSG_REACT) qua TLS (:9000). */
    private void sendReact(Message m, String emoji) {
        Theme.runAsync(
                // [GỬI] Gửi gói MSG_REACT kèm msgId và emoji
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

    /** [UI -> GỬI] Thu hồi tin nhắn đã gửi (MSG_RECALL) qua TLS (:9000). */
    private void sendRecall(Message m) {
        int ok = JOptionPane.showConfirmDialog(this,
                "Thu hồi tin nhắn này cho mọi người?", "Thu hồi tin nhắn", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;
        Theme.runAsync(
                // [GỬI] Gửi gói MSG_RECALL yêu cầu server thu hồi tin nhắn
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

    /** [GỬI] Báo đã xem tới tin nhắn {@code lastReadMsgId} (gói fire-and-forget MSG_SEEN qua TLS :9000). */
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

    /** [UI -> GỬI] Phát trạng thái đang gõ phím (gói TYPING qua TLS :9000), có debounce 2 giây. */
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

    // --- [PUSH -> UI] Các hàm nhận sự kiện đẩy từ MainFrame.onPush() (đã chạy trên EDT) ---

    /** [PUSH -> UI] Thêm tin nhắn mới (MSG_NEW) vào cuối khung chat và tự động gửi MSG_SEEN nếu là tin của người khác. */
    public void onNewMessage(Message m) {
        appendMessageToView(m);
        if (m.senderId != me.id) {
            sendSeen(m.id);
        }
    }

    /** [PUSH -> UI] Đánh dấu bong bóng đã thu hồi khi nhận MSG_RECALLED. */
    public void onMessageRecalled(long msgId) {
        MessageBubble b = bubblesById.get(msgId);
        if (b != null) b.markRecalled();
    }

    /** [PUSH -> UI] Cập nhật biểu tượng cảm xúc trên bong bóng khi nhận MSG_REACTED. */
    public void onMessageReacted(long msgId, long userId, String emoji) {
        MessageBubble b = bubblesById.get(msgId);
        if (b != null) b.applyReaction(userId, emoji);
    }

    /** [PUSH -> UI] Hiển thị hoặc ẩn dòng "... đang soạn tin..." khi nhận gói TYPING. */
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

    /** [PUSH -> UI] Cập nhật nhãn "Đã xem" cho các tin của mình khi đối phương/thành viên nhóm gửi SEEN_UPDATE. */
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

    /** [PUSH -> UI] Đổi trạng thái tin nhắn sang "✓✓ Đã nhận" khi nhận MSG_DELIVERED. */
    public void onDelivered(long msgId) {
        MessageBubble b = bubblesById.get(msgId);
        if (b != null && !Message.STATE_SEEN.equals(b.getMessage().deliveryState)) {
            b.updateDelivery(Message.STATE_DELIVERED, b.getMessage().seenCount);
        }
    }

    /** [PUSH -> UI] Cập nhật chấm xanh online và dòng trạng thái trên header khi nhận PRESENCE. */
    public void onPresence(long userId, boolean online) {
        if (!conv.isGroup && conv.peerId == userId) {
            conv.peerOnline = online;
            headerAvatar.setOnline(online);
            refreshSubtitle();
        }
    }
}
