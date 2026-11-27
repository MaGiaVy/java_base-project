package com.zalolite.client.ui;

import com.zalolite.client.Connection;
import com.zalolite.client.P2pTransfer;
import com.zalolite.client.VoiceCallSession;
import com.zalolite.common.Conversation;
import com.zalolite.common.Message;
import com.zalolite.common.Packet;
import com.zalolite.common.PacketType;
import com.zalolite.common.User;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cửa sổ chính ZaloLite Berry:
 * Thanh rail tím mận (58px) | Cột giữa hội thoại / bạn bè (290px) | Khung chat Berry Pop.
 */
public class MainFrame extends JFrame {
    private static final String TAB_CHATS = "CHATS";
    private static final String TAB_FRIENDS = "FRIENDS";

    private final Connection conn;
    private final User me;
    private final Map<String, File> localMediaFiles = new ConcurrentHashMap<>();
    private P2pTransfer p2p;
    private boolean soundEnabled = true;
    private VoiceCallSession activeCallSession;
    private VoiceCallDialog activeCallDialog;
    private long activeCallPeerId;

    // Left rail
    private final RailButton chatNavBtn;
    private final RailButton friendNavBtn;
    private String activeTab = TAB_CHATS;

    // Middle panel
    private final CardLayout midCards = new CardLayout();
    private final JPanel midPanel = new JPanel(midCards);
    private final JTextField convFilterField = new JTextField();
    private final JPanel convListBox = new JPanel();
    private final FriendPanel friendPanel;
    private final List<Conversation> conversations = new ArrayList<>();

    // Right panel
    private final JPanel rightPanel = new JPanel(new BorderLayout());
    private final JPanel rightContent = new JPanel(new BorderLayout());
    private final JLabel toastLabel = new JLabel(" ", SwingConstants.CENTER);
    private final Timer toastTimer;
    private ChatPanel activeChat;

    public MainFrame(Connection conn, User me) {
        super("ZaloLite Berry - " + me.name);
        this.conn = conn;
        this.me = me;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1120, 700);
        setMinimumSize(new Dimension(880, 560));
        setLocationRelativeTo(null);

        // --- 1. Left Rail (58px, #5B21B6) ---
        JPanel rail = new JPanel();
        rail.setLayout(new BoxLayout(rail, BoxLayout.Y_AXIS));
        rail.setBackground(Theme.RAIL_BG);
        rail.setPreferredSize(new Dimension(58, 0));
        rail.setBorder(BorderFactory.createEmptyBorder(14, 6, 14, 6));

        Theme.AvatarBadge myAvatar = new Theme.AvatarBadge(
                me.name, 40, Theme.PRIMARY_RED, true, true);
        myAvatar.setAlignmentX(CENTER_ALIGNMENT);
        myAvatar.setToolTipText(me.name + " (" + me.phone + ") — Bấm để sửa hồ sơ / đổi mật khẩu");
        myAvatar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        myAvatar.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                new ProfileDialog(MainFrame.this, conn, me, updated -> {
                    me.name = updated.name;
                    me.email = updated.email;
                    setTitle("ZaloLite Berry - " + me.name);
                    myAvatar.setNameAndColor(me.name, Theme.PRIMARY_RED, true, true);
                    myAvatar.setToolTipText(me.name + " (" + me.phone + ") — Bấm để sửa hồ sơ / đổi mật khẩu");
                }).setVisible(true);
            }
        });

        chatNavBtn = new RailButton("💬", "Tin nhắn");
        friendNavBtn = new RailButton("👥", "Bạn bè");
        RailButton newGroupBtn = new RailButton("✨", "Tạo nhóm mới");
        RailButton soundBtn = new RailButton("🔔", "Bật/tắt âm báo tin nhắn mới");
        RailButton logoutBtn = new RailButton("⏻", "Đăng xuất");

        chatNavBtn.setSelected(true);
        chatNavBtn.addActionListener(e -> switchTab(TAB_CHATS));
        friendNavBtn.addActionListener(e -> switchTab(TAB_FRIENDS));
        newGroupBtn.addActionListener(e -> openGroupCreate());
        soundBtn.addActionListener(e -> {
            soundEnabled = !soundEnabled;
            soundBtn.setText(soundEnabled ? "🔔" : "🔕");
            showToast(soundEnabled ? "🔔 Đã bật chuông báo tin nhắn" : "🔕 Đã tắt chuông báo tin nhắn");
        });
        logoutBtn.addActionListener(e -> doLogout());

        Theme.PillLabel tlsPill = new Theme.PillLabel("TLS", Theme.ONLINE_GREEN, Color.WHITE, 9.5f, false);
        tlsPill.setToolTipText("Kết nối được mã hoá TLS");
        tlsPill.setAlignmentX(CENTER_ALIGNMENT);
        tlsPill.setMaximumSize(new Dimension(44, 20));

        rail.add(myAvatar);
        rail.add(Box.createVerticalStrut(18));
        rail.add(chatNavBtn);
        rail.add(Box.createVerticalStrut(8));
        rail.add(friendNavBtn);
        rail.add(Box.createVerticalStrut(8));
        rail.add(newGroupBtn);
        rail.add(Box.createVerticalGlue());
        rail.add(soundBtn);
        rail.add(Box.createVerticalStrut(8));
        rail.add(tlsPill);
        rail.add(Box.createVerticalStrut(10));
        rail.add(logoutBtn);

        // --- 2. Middle Panel (290px, #FFF5F8) ---
        midPanel.setPreferredSize(new Dimension(290, 0));
        midPanel.setBackground(Theme.SIDEBAR_BG);
        midPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER_PINK));

        JPanel convTab = buildConversationsTab();
        friendPanel = new FriendPanel(conn, me, this::openConversation, friendNavBtn::setBadgeCount);
        midPanel.add(convTab, TAB_CHATS);
        midPanel.add(friendPanel, TAB_FRIENDS);

        // --- 3. Right Panel (#FFF9FB) ---
        rightPanel.setBackground(Theme.CHAT_BG);
        toastLabel.setOpaque(true);
        toastLabel.setBackground(Theme.ITEM_SELECTED);
        toastLabel.setForeground(Theme.TEXT_DARK);
        toastLabel.setFont(Theme.emojiFont(Font.BOLD, 12.5f));
        toastLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_PINK),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        toastLabel.setVisible(false);
        toastTimer = new Timer(4000, e -> toastLabel.setVisible(false));
        toastTimer.setRepeats(false);

        rightContent.setBackground(Theme.CHAT_BG);
        showWelcomeScreen();

        rightPanel.add(toastLabel, BorderLayout.NORTH);
        rightPanel.add(rightContent, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(rail, BorderLayout.WEST);
        JPanel body = new JPanel(new BorderLayout());
        body.add(midPanel, BorderLayout.WEST);
        body.add(rightPanel, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        // Listeners mạng & vòng đời cửa sổ
        conn.addListener(this::onPush);
        conn.setOnDisconnect(() -> {
            JOptionPane.showMessageDialog(this, "Mất kết nối tới máy chủ.");
            dispose();
            new LoginFrame().setVisible(true);
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                endActiveCall(false, null);
                if (p2p != null) p2p.stop();
                conn.close();
            }
        });

        startP2pListener();
        loadConversations();
        friendPanel.refresh();
    }

    private void startP2pListener() {
        try {
            p2p = new P2pTransfer(this::onP2pFileReceived);
            p2p.start();
            int port = p2p.getListenPort();
            System.out.println("[INFO] P2P đang nghe :" + port);
            Theme.runAsync(() -> conn.request(new Packet(PacketType.P2P_REGISTER).put("port", port)), null, null);
        } catch (Exception e) {
            System.err.println("[WARN] Không mở được cổng P2P: " + e.getMessage());
        }
    }

    private void onP2pFileReceived(P2pTransfer.ReceivedP2pFile f) {
        String key = "p2p://" + f.convId() + "/" + f.fileName();
        localMediaFiles.put(key, f.savedFile());
        SwingUtilities.invokeLater(() -> showToast("⚡ Đã nhận qua P2P: " + f.fileName()));
    }

    private JPanel buildConversationsTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.SIDEBAR_BG);

        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.setBackground(Theme.SIDEBAR_BG);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_PINK),
                BorderFactory.createEmptyBorder(12, 12, 10, 12)
        ));

        JLabel title = new JLabel("Tin nhắn");
        title.setFont(Theme.uiFont(Font.BOLD, 17f));
        title.setForeground(Theme.TEXT_DARK);
        header.add(title, BorderLayout.NORTH);

        convFilterField.putClientProperty("JTextField.placeholderText", "Tìm cuộc trò chuyện...");
        convFilterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { renderConversations(); }
            @Override
            public void removeUpdate(DocumentEvent e) { renderConversations(); }
            @Override
            public void changedUpdate(DocumentEvent e) { renderConversations(); }
        });
        header.add(convFilterField, BorderLayout.SOUTH);

        convListBox.setLayout(new BoxLayout(convListBox, BoxLayout.Y_AXIS));
        convListBox.setBackground(Theme.SIDEBAR_BG);
        convListBox.setBorder(BorderFactory.createEmptyBorder(6, 6, 8, 6));

        JScrollPane scroll = new JScrollPane(convListBox);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.SIDEBAR_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private void switchTab(String tab) {
        activeTab = tab;
        chatNavBtn.setSelected(TAB_CHATS.equals(tab));
        friendNavBtn.setSelected(TAB_FRIENDS.equals(tab));
        midCards.show(midPanel, tab);
        if (TAB_FRIENDS.equals(tab)) {
            friendPanel.refresh();
        } else {
            loadConversations();
        }
    }

    private void showWelcomeScreen() {
        activeChat = null;
        rightContent.removeAll();
        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setBackground(Theme.CHAT_BG);

        Theme.RoundedPanel card = new Theme.RoundedPanel(22, Color.WHITE, null, Theme.BORDER_PINK);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(30, 38, 30, 38));

        JLabel icon = new JLabel("🍓", SwingConstants.CENTER);
        icon.setFont(Theme.emojiFont(48f));
        icon.setAlignmentX(CENTER_ALIGNMENT);

        JLabel title = new JLabel("Chào mừng đến với ZaloLite Berry!");
        title.setFont(Theme.uiFont(Font.BOLD, 20f));
        title.setForeground(Theme.PRIMARY_RED);
        title.setAlignmentX(CENTER_ALIGNMENT);

        JLabel sub = new JLabel("Xin chào " + me.name + " — chọn một hội thoại hoặc tìm bạn bè để bắt đầu.");
        sub.setFont(Theme.uiFont(13f));
        sub.setForeground(Theme.TEXT_MUTED);
        sub.setAlignmentX(CENTER_ALIGNMENT);

        card.add(icon);
        card.add(Box.createVerticalStrut(8));
        card.add(title);
        card.add(Box.createVerticalStrut(6));
        card.add(sub);
        wrap.add(card);

        rightContent.add(wrap, BorderLayout.CENTER);
        rightContent.revalidate();
        rightContent.repaint();
    }

    private void showToast(String message) {
        toastLabel.setText(message);
        toastLabel.setVisible(true);
        toastTimer.restart();
    }

    private void loadConversations() {
        Theme.runAsync(
                () -> conn.request(new Packet(PacketType.CONV_LIST)),
                res -> {
                    if (!res.isOk()) return;
                    conversations.clear();
                    conversations.addAll(res.getList("conversations", Conversation.class));
                    if (activeChat != null) {
                        for (Conversation c : conversations) {
                            if (c.id == activeChat.getConvId()) {
                                c.unreadCount = 0;
                                activeChat.updateConversation(c);
                            }
                        }
                    }
                    renderConversations();
                },
                null
        );
    }

    private void renderConversations() {
        conversations.sort(Comparator
                .comparing((Conversation c) -> !c.pinned)
                .thenComparing((Conversation c) -> -c.lastTime));

        String q = convFilterField.getText().trim().toLowerCase();
        convListBox.removeAll();
        int shown = 0;

        for (Conversation c : conversations) {
            if (!q.isEmpty()) {
                String n = c.name == null ? "" : c.name.toLowerCase();
                String lm = c.lastMessage == null ? "" : c.lastMessage.toLowerCase();
                if (!n.contains(q) && !lm.contains(q)) continue;
            }
            convListBox.add(buildConversationRow(c));
            convListBox.add(Box.createVerticalStrut(4));
            shown++;
        }

        if (shown == 0) {
            JLabel empty = new JLabel(q.isEmpty()
                    ? "Chưa có hội thoại nào."
                    : "Không tìm thấy hội thoại phù hợp.");
            empty.setFont(Theme.uiFont(12.5f));
            empty.setForeground(Theme.TEXT_MUTED);
            empty.setBorder(BorderFactory.createEmptyBorder(16, 10, 10, 10));
            convListBox.add(empty);
        }

        convListBox.revalidate();
        convListBox.repaint();
    }

    private JPanel buildConversationRow(Conversation c) {
        boolean selected = activeChat != null && activeChat.getConvId() == c.id;
        Color baseBg = selected ? Theme.ITEM_SELECTED : Theme.SIDEBAR_BG;

        Theme.RoundedPanel row = new Theme.RoundedPanel(12, baseBg);
        row.setLayout(new BorderLayout(10, 0));
        row.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        Theme.AvatarBadge av = new Theme.AvatarBadge(
                c.name, 42, Theme.avatarColor(c.isGroup ? c.id : c.peerId, c.isGroup),
                !c.isGroup, c.peerOnline);
        row.add(av, BorderLayout.WEST);

        JPanel center = new JPanel(new GridLayout(2, 1, 0, 2));
        center.setOpaque(false);

        JPanel topLine = new JPanel(new BorderLayout(4, 0));
        topLine.setOpaque(false);
        String nameText = (c.pinned ? "📌 " : "") + (c.name == null ? "" : c.name);
        JLabel nameLbl = new JLabel(nameText);
        nameLbl.setFont(Theme.emojiFont(Font.BOLD, 13f));
        nameLbl.setForeground(Theme.TEXT_DARK);
        JLabel timeLbl = new JLabel(Theme.formatShortTime(c.lastTime));
        timeLbl.setFont(Theme.uiFont(10.5f));
        timeLbl.setForeground(Theme.TEXT_MUTED);
        topLine.add(nameLbl, BorderLayout.CENTER);
        topLine.add(timeLbl, BorderLayout.EAST);

        JPanel bottomLine = new JPanel(new BorderLayout(6, 0));
        bottomLine.setOpaque(false);
        String preview = (c.lastMessage == null || c.lastMessage.isBlank()) ? "Bắt đầu trò chuyện" : c.lastMessage;
        JLabel previewLbl = new JLabel(preview);
        previewLbl.setFont(Theme.emojiFont(c.unreadCount > 0 ? Font.BOLD : Font.PLAIN, 12f));
        previewLbl.setForeground(c.unreadCount > 0 ? Theme.TEXT_DARK : Theme.TEXT_MUTED);
        bottomLine.add(previewLbl, BorderLayout.CENTER);

        if (c.unreadCount > 0) {
            String badgeTxt = c.unreadCount > 99 ? "99+" : String.valueOf(c.unreadCount);
            Theme.PillLabel badge = new Theme.PillLabel(badgeTxt, Theme.PRIMARY_RED, Color.WHITE, 10.5f, false);
            badge.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
            bottomLine.add(badge, BorderLayout.EAST);
        }

        center.add(topLine);
        center.add(bottomLine);
        row.add(center, BorderLayout.CENTER);

        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!selected) row.setFill(Theme.ITEM_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!selected) row.setFill(Theme.SIDEBAR_BG);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    openConversation(c);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showConvMenu(e, c);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showConvMenu(e, c);
            }
        });
        return row;
    }

    private void showConvMenu(MouseEvent e, Conversation c) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem pin = new JMenuItem(c.pinned ? "Bỏ ghim hội thoại" : "📌 Ghim hội thoại");
        pin.setFont(Theme.emojiFont(12.5f));
        pin.addActionListener(ev -> togglePin(c));
        menu.add(pin);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void togglePin(Conversation c) {
        boolean next = !c.pinned;
        Theme.runAsync(
                () -> conn.request(new Packet(PacketType.CONV_PIN)
                        .put("convId", c.id).put("pinned", next)),
                res -> {
                    if (res.isOk()) {
                        c.pinned = next;
                        renderConversations();
                    }
                },
                null
        );
    }

    private void openConversation(Conversation conv) {
        if (conv == null) return;
        // Đảm bảo hội thoại có trong danh sách local
        Conversation target = null;
        for (Conversation c : conversations) {
            if (c.id == conv.id) {
                target = c;
                break;
            }
        }
        if (target == null) {
            conversations.add(0, conv);
            target = conv;
        }
        target.unreadCount = 0;

        if (!TAB_CHATS.equals(activeTab)) {
            activeTab = TAB_CHATS;
            chatNavBtn.setSelected(true);
            friendNavBtn.setSelected(false);
            midCards.show(midPanel, TAB_CHATS);
        }

        Conversation finalTarget = target;
        activeChat = new ChatPanel(conn, me, finalTarget, localMediaFiles,
                () -> new ArrayList<>(conversations),
                () -> startOutgoingCall(finalTarget.peerId, finalTarget.name),
                sent -> {
                    finalTarget.lastMessage = previewOf(sent);
                    finalTarget.lastTime = sent.createdAt;
                    renderConversations();
                },
                newName -> {
                    finalTarget.name = newName;
                    renderConversations();
                },
                () -> {
                    conversations.removeIf(x -> x.id == finalTarget.id);
                    showWelcomeScreen();
                    renderConversations();
                });

        rightContent.removeAll();
        rightContent.add(activeChat, BorderLayout.CENTER);
        rightContent.revalidate();
        rightContent.repaint();
        renderConversations();
    }

    private void openGroupCreate() {
        new GroupCreateDialog(this, conn, conv -> {
            loadConversations();
            openConversation(conv);
        }).setVisible(true);
    }

    private void doLogout() {
        endActiveCall(true, null);
        if (p2p != null) p2p.stop();
        Theme.runAsync(() -> {
            try {
                conn.request(new Packet(PacketType.LOGOUT));
            } catch (Exception ignored) {
            }
            return null;
        }, r -> {
            conn.close();
            dispose();
            new LoginFrame().setVisible(true);
        }, err -> {
            conn.close();
            dispose();
            new LoginFrame().setVisible(true);
        });
    }

    private static String previewOf(Message m) {
        if (m.recalled) return "Tin nhắn đã được thu hồi";
        String kind = m.kind == null ? Message.KIND_TEXT : m.kind;
        return switch (kind) {
            case Message.KIND_IMAGE -> "🖼️ [Hình ảnh]";
            case Message.KIND_FILE -> "📎 " + (m.fileName != null ? m.fileName : "[Tệp]");
            case Message.KIND_VIDEO -> "🎬 [Video]";
            case Message.KIND_AUDIO -> "🎤 [Tin nhắn thoại]";
            default -> m.content == null ? "" : m.content;
        };
    }

    // --- Xử lý toàn bộ sự kiện đẩy từ Server (đã chạy trên EDT) ---

    private void onPush(Packet p) {
        if (p == null || p.type == null) return;
        switch (p.type) {
            case PacketType.MSG_NEW -> handleMsgNew(p);
            case PacketType.PRESENCE -> handlePresence(p);
            case PacketType.TYPING -> {
                if (activeChat != null && activeChat.getConvId() == p.num("convId")) {
                    activeChat.onTyping(p.num("userId"), p.str("userName"), p.bool("typing"));
                }
            }
            case PacketType.SEEN_UPDATE -> {
                if (activeChat != null && activeChat.getConvId() == p.num("convId")) {
                    activeChat.onSeenUpdate(p.num("userId"), p.num("lastReadMsgId"));
                }
            }
            case PacketType.MSG_DELIVERED -> {
                if (activeChat != null && activeChat.getConvId() == p.num("convId")) {
                    activeChat.onDelivered(p.num("msgId"));
                }
            }
            case PacketType.MSG_RECALLED -> {
                long convId = p.num("convId");
                long msgId = p.num("msgId");
                if (activeChat != null && activeChat.getConvId() == convId) {
                    activeChat.onMessageRecalled(msgId);
                }
                loadConversations();
            }
            case PacketType.MSG_REACTED -> {
                long convId = p.num("convId");
                if (activeChat != null && activeChat.getConvId() == convId) {
                    activeChat.onMessageReacted(p.num("msgId"), p.num("userId"), p.str("emoji"));
                }
            }
            case PacketType.FRIEND_EVENT -> handleFriendEvent(p);
            case PacketType.CONV_UPDATE -> handleConvUpdate(p);
            case PacketType.CALL_OFFER -> handleCallOffer(p);
            case PacketType.CALL_ANSWER -> handleCallAnswer(p);
            case PacketType.CALL_REJECT -> endActiveCall(false, "Đối phương đã từ chối cuộc gọi");
            case PacketType.CALL_END -> endActiveCall(false, "Cuộc gọi đã kết thúc");
            default -> {
            }
        }
    }

    private void startOutgoingCall(long peerId, String peerName) {
        if (peerId <= 0 || activeCallDialog != null) return;
        try {
            activeCallSession = new VoiceCallSession();
            activeCallPeerId = peerId;
            int udpPort = activeCallSession.getLocalPort();
            activeCallDialog = VoiceCallDialog.outgoing(this, peerName, peerId, () -> endActiveCall(true, null));
            activeCallDialog.setVisible(true);
            Theme.runAsync(
                    () -> conn.request(new Packet(PacketType.CALL_OFFER).put("targetId", peerId).put("udpPort", udpPort)),
                    res -> {
                        if (!res.isOk()) {
                            endActiveCall(false, res.str("error"));
                        }
                    },
                    err -> endActiveCall(false, err)
            );
        } catch (Exception ex) {
            endActiveCall(false, "Không mở được cổng UDP: " + ex.getMessage());
        }
    }

    private void handleCallOffer(Packet p) {
        long callerId = p.num("callerId");
        String callerName = p.str("callerName");
        String callerIp = p.str("callerIp");
        int callerUdpPort = (int) p.num("udpPort");
        if (activeCallDialog != null) {
            Theme.runAsync(() -> conn.request(new Packet(PacketType.CALL_REJECT)
                    .put("callerId", callerId).put("reason", "Người dùng đang bận")), null, null);
            return;
        }
        if (soundEnabled) Toolkit.getDefaultToolkit().beep();
        activeCallPeerId = callerId;
        activeCallDialog = VoiceCallDialog.incoming(this, callerName, callerId,
                () -> {
                    try {
                        activeCallSession = new VoiceCallSession();
                        int myPort = activeCallSession.getLocalPort();
                        activeCallSession.startAudio(callerIp, callerUdpPort);
                        if (activeCallDialog != null) activeCallDialog.setConnected(activeCallSession);
                        Theme.runAsync(() -> conn.request(new Packet(PacketType.CALL_ANSWER)
                                .put("callerId", callerId).put("udpPort", myPort)), null, null);
                    } catch (Exception ex) {
                        endActiveCall(true, "Lỗi âm thanh: " + ex.getMessage());
                    }
                },
                () -> {
                    Theme.runAsync(() -> conn.request(new Packet(PacketType.CALL_REJECT)
                            .put("callerId", callerId).put("reason", "Từ chối")), null, null);
                    endActiveCall(false, null);
                });
        activeCallDialog.setVisible(true);
    }

    private void handleCallAnswer(Packet p) {
        if (activeCallSession == null || activeCallDialog == null) return;
        String calleeIp = p.str("calleeIp");
        int calleePort = (int) p.num("udpPort");
        try {
            activeCallSession.startAudio(calleeIp, calleePort);
            activeCallDialog.setConnected(activeCallSession);
        } catch (Exception ex) {
            endActiveCall(true, "Lỗi kết nối âm thanh: " + ex.getMessage());
        }
    }

    private void endActiveCall(boolean notifyPeer, String toastMsg) {
        long peer = activeCallPeerId;
        VoiceCallSession sess = activeCallSession;
        VoiceCallDialog dlg = activeCallDialog;
        activeCallPeerId = 0;
        activeCallSession = null;
        activeCallDialog = null;
        if (notifyPeer && peer > 0) {
            Theme.runAsync(() -> conn.request(new Packet(PacketType.CALL_END).put("peerId", peer)), null, null);
        }
        if (sess != null) sess.close();
        if (dlg != null) dlg.endCall(toastMsg);
        if (toastMsg != null && !toastMsg.isBlank()) showToast("📞 " + toastMsg);
    }

    private void handleMsgNew(Packet p) {
        Message m = p.get("message", Message.class);
        if (m == null) return;
        boolean isActive = activeChat != null && activeChat.getConvId() == m.convId;
        if (soundEnabled && m.senderId != me.id && (!isActive || !isActive())) {
            Toolkit.getDefaultToolkit().beep();
        }
        if (isActive) {
            activeChat.onNewMessage(m);
        }

        Conversation found = null;
        for (Conversation c : conversations) {
            if (c.id == m.convId) {
                found = c;
                break;
            }
        }
        if (found != null) {
            found.lastMessage = previewOf(m);
            found.lastTime = m.createdAt;
            if (!isActive && m.senderId != me.id) {
                found.unreadCount++;
            } else if (isActive) {
                found.unreadCount = 0;
            }
            renderConversations();
        } else {
            loadConversations();
        }
    }

    private void handlePresence(Packet p) {
        long userId = p.num("userId");
        boolean online = p.bool("online");
        boolean convChanged = false;
        for (Conversation c : conversations) {
            if (!c.isGroup && c.peerId == userId) {
                c.peerOnline = online;
                convChanged = true;
            }
        }
        if (convChanged) renderConversations();
        friendPanel.onPresence(userId, online);
        if (activeChat != null) activeChat.onPresence(userId, online);
    }

    private void handleFriendEvent(Packet p) {
        friendPanel.refresh();
        String action = p.str("action");
        User u = p.get("user", User.class);
        String who = u != null && u.name != null ? u.name : "Một người dùng";
        if ("REQUEST".equalsIgnoreCase(action)) {
            showToast("💌 " + who + " đã gửi lời mời kết bạn cho bạn");
        } else if ("ACCEPT".equalsIgnoreCase(action) || "ACCEPTED".equalsIgnoreCase(action)) {
            showToast("🎉 " + who + " đã chấp nhận lời mời kết bạn");
        } else if ("REMOVE".equalsIgnoreCase(action) || "REMOVED".equalsIgnoreCase(action)) {
            showToast("ℹ️ Đã cập nhật danh sách bạn bè");
        }
    }

    private void handleConvUpdate(Packet p) {
        boolean removed = p.bool("removed");
        long convId = p.num("convId");
        Conversation updated = p.get("conversation", Conversation.class);
        if (removed) {
            conversations.removeIf(c -> c.id == convId);
            if (activeChat != null && activeChat.getConvId() == convId) {
                showWelcomeScreen();
                showToast("ℹ️ Bạn không còn trong cuộc trò chuyện này");
            }
            renderConversations();
            return;
        }
        if (updated != null && activeChat != null && activeChat.getConvId() == updated.id) {
            activeChat.updateConversation(updated);
        }
        loadConversations();
    }

    /** Nút điều hướng trên thanh rail tím mận, có hỗ trợ chấm badge đỏ dâu. */
    private static class RailButton extends JButton {
        private boolean selected;
        private boolean hovered;
        private int badgeCount;

        RailButton(String emoji, String tooltip) {
            super(emoji);
            setToolTipText(tooltip);
            setFont(Theme.emojiFont(20f));
            setForeground(Color.WHITE);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setAlignmentX(CENTER_ALIGNMENT);
            Dimension d = new Dimension(44, 44);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }
            });
        }

        @Override
        public void setSelected(boolean selected) {
            this.selected = selected;
            repaint();
        }

        void setBadgeCount(int count) {
            this.badgeCount = count;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (selected) {
                g2.setColor(Theme.RAIL_ACTIVE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            } else if (hovered) {
                g2.setColor(Theme.RAIL_HOVER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            }
            g2.dispose();
            super.paintComponent(g);

            if (badgeCount > 0) {
                Graphics2D g3 = (Graphics2D) g.create();
                g3.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                String txt = badgeCount > 9 ? "9+" : String.valueOf(badgeCount);
                g3.setFont(Theme.uiFont(Font.BOLD, 9.5f));
                FontMetrics fm = g3.getFontMetrics();
                int bw = Math.max(16, fm.stringWidth(txt) + 8);
                int bh = 16;
                int bx = getWidth() - bw - 1;
                int by = 1;
                g3.setColor(Theme.PRIMARY_RED);
                g3.fillRoundRect(bx, by, bw, bh, bh, bh);
                g3.setColor(Color.WHITE);
                g3.drawString(txt, bx + (bw - fm.stringWidth(txt)) / 2, by + fm.getAscent() + 1);
                g3.dispose();
            }
        }
    }
}
