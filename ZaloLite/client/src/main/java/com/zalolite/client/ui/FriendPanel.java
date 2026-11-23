package com.zalolite.client.ui;

import com.zalolite.client.Connection;
import com.zalolite.common.Conversation;
import com.zalolite.common.Packet;
import com.zalolite.common.PacketType;
import com.zalolite.common.User;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Tab Bạn bè ở cột giữa: tìm kiếm người dùng, lời mời kết bạn, danh sách bạn bè. */
public class FriendPanel extends JPanel {
    private final Connection conn;
    private final User me;
    private final Consumer<Conversation> onOpenConversation;
    private final IntConsumer onIncomingCountChanged;

    private final JTextField searchField = new JTextField();
    private final JPanel contentBox = new JPanel();
    private final JPanel searchSection = new JPanel();
    private final JPanel incomingSection = new JPanel();
    private final JPanel friendsSection = new JPanel();

    private final List<User> friends = new ArrayList<>();
    private final List<User> incoming = new ArrayList<>();
    private final Set<Long> friendIds = new HashSet<>();
    private final Set<Long> outgoingIds = new HashSet<>();

    public FriendPanel(Connection conn, User me,
                       Consumer<Conversation> onOpenConversation,
                       IntConsumer onIncomingCountChanged) {
        this.conn = conn;
        this.me = me;
        this.onOpenConversation = onOpenConversation;
        this.onIncomingCountChanged = onIncomingCountChanged;

        setLayout(new BorderLayout());
        setBackground(Theme.SIDEBAR_BG);

        // Header + ô tìm kiếm người dùng
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setBackground(Theme.SIDEBAR_BG);
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER_PINK),
                BorderFactory.createEmptyBorder(12, 12, 10, 12)
        ));

        JLabel title = new JLabel("Bạn bè");
        title.setFont(Theme.uiFont(Font.BOLD, 17f));
        title.setForeground(Theme.TEXT_DARK);
        top.add(title, BorderLayout.NORTH);

        JPanel searchRow = new JPanel(new BorderLayout(6, 0));
        searchRow.setOpaque(false);
        searchField.putClientProperty("JTextField.placeholderText", "Tìm SĐT hoặc tên để kết bạn...");
        JButton searchBtn = Theme.primaryButton("Tìm");
        searchBtn.setPreferredSize(new Dimension(56, 32));
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(searchBtn, BorderLayout.EAST);
        top.add(searchRow, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        contentBox.setLayout(new BoxLayout(contentBox, BoxLayout.Y_AXIS));
        contentBox.setBackground(Theme.SIDEBAR_BG);
        contentBox.setBorder(BorderFactory.createEmptyBorder(8, 8, 12, 8));

        initSection(searchSection);
        initSection(incomingSection);
        initSection(friendsSection);

        contentBox.add(searchSection);
        contentBox.add(incomingSection);
        contentBox.add(friendsSection);

        JScrollPane scroll = new JScrollPane(contentBox);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.SIDEBAR_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        searchBtn.addActionListener(e -> doSearch());
        searchField.addActionListener(e -> doSearch());
    }

    private void initSection(JPanel p) {
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setAlignmentX(LEFT_ALIGNMENT);
    }

    public void refresh() {
        Theme.runAsync(
                () -> conn.request(new Packet(PacketType.FRIEND_LIST)),
                res -> {
                    if (!res.isOk()) return;
                    friends.clear();
                    friends.addAll(res.getList("friends", User.class));
                    incoming.clear();
                    incoming.addAll(res.getList("incoming", User.class));
                    List<User> outgoing = res.getList("outgoing", User.class);

                    friendIds.clear();
                    for (User u : friends) friendIds.add(u.id);
                    outgoingIds.clear();
                    for (User u : outgoing) outgoingIds.add(u.id);

                    if (onIncomingCountChanged != null) {
                        onIncomingCountChanged.accept(incoming.size());
                    }
                    renderIncoming();
                    renderFriends();
                },
                null
        );
    }

    public void onPresence(long userId, boolean online) {
        boolean changed = false;
        for (User u : friends) {
            if (u.id == userId) {
                u.online = online;
                changed = true;
            }
        }
        if (changed) renderFriends();
    }

    private void doSearch() {
        String q = searchField.getText().trim();
        if (q.isEmpty()) {
            searchSection.removeAll();
            searchSection.revalidate();
            searchSection.repaint();
            return;
        }
        Theme.runAsync(
                () -> conn.request(new Packet(PacketType.USER_SEARCH).put("query", q)),
                res -> {
                    searchSection.removeAll();
                    if (!res.isOk()) {
                        searchSection.add(sectionHeader("Kết quả tìm kiếm"));
                        searchSection.add(mutedLabel(res.str("error")));
                    } else {
                        List<User> found = res.getList("users", User.class);
                        searchSection.add(sectionHeader("Kết quả tìm kiếm (" + found.size() + ")"));
                        if (found.isEmpty()) {
                            searchSection.add(mutedLabel("Không tìm thấy người dùng nào."));
                        } else {
                            for (User u : found) {
                                if (u.id == me.id) continue;
                                searchSection.add(buildSearchRow(u));
                                searchSection.add(Box.createVerticalStrut(4));
                            }
                        }
                        searchSection.add(Box.createVerticalStrut(8));
                    }
                    searchSection.revalidate();
                    searchSection.repaint();
                },
                err -> JOptionPane.showMessageDialog(this, err)
        );
    }

    private JPanel buildSearchRow(User u) {
        Theme.RoundedPanel row = new Theme.RoundedPanel(12, Color.WHITE, null, Theme.BORDER_PINK);
        row.setLayout(new BorderLayout(8, 0));
        row.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        row.setAlignmentX(LEFT_ALIGNMENT);

        row.add(new Theme.AvatarBadge(u.name, 34, Theme.avatarColor(u.id, false), true, u.online), BorderLayout.WEST);
        row.add(userInfoPanel(u), BorderLayout.CENTER);

        if (friendIds.contains(u.id)) {
            JButton chatBtn = Theme.softButton("Nhắn tin");
            chatBtn.addActionListener(e -> openDirectChat(u));
            row.add(chatBtn, BorderLayout.EAST);
        } else if (outgoingIds.contains(u.id)) {
            row.add(new Theme.PillLabel("Đã gửi", Theme.BUBBLE_OTHER, Theme.TEXT_MUTED, 11f, false), BorderLayout.EAST);
        } else {
            JButton addBtn = Theme.primaryButton("Kết bạn");
            addBtn.setFont(Theme.uiFont(Font.BOLD, 11.5f));
            addBtn.addActionListener(e -> {
                addBtn.setEnabled(false);
                Theme.runAsync(
                        () -> conn.request(new Packet(PacketType.FRIEND_REQUEST).put("targetId", u.id)),
                        res -> {
                            if (res.isOk()) {
                                outgoingIds.add(u.id);
                                addBtn.setText("Đã gửi");
                                refresh();
                            } else {
                                addBtn.setEnabled(true);
                                JOptionPane.showMessageDialog(this, res.str("error"));
                            }
                        },
                        err -> {
                            addBtn.setEnabled(true);
                            JOptionPane.showMessageDialog(this, err);
                        }
                );
            });
            row.add(addBtn, BorderLayout.EAST);
        }
        return row;
    }

    private void renderIncoming() {
        incomingSection.removeAll();
        if (!incoming.isEmpty()) {
            incomingSection.add(sectionHeader("Lời mời kết bạn (" + incoming.size() + ")"));
            for (User u : incoming) {
                Theme.RoundedPanel card = new Theme.RoundedPanel(12, Color.WHITE, null, Theme.BORDER_PINK);
                card.setLayout(new BorderLayout(6, 6));
                card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
                card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));
                card.setAlignmentX(LEFT_ALIGNMENT);

                JPanel top = new JPanel(new BorderLayout(8, 0));
                top.setOpaque(false);
                top.add(new Theme.AvatarBadge(u.name, 32, Theme.avatarColor(u.id, false), false, false), BorderLayout.WEST);
                top.add(userInfoPanel(u), BorderLayout.CENTER);

                JPanel btns = new JPanel(new GridLayout(1, 2, 6, 0));
                btns.setOpaque(false);
                JButton accept = Theme.primaryButton("Chấp nhận");
                accept.setFont(Theme.uiFont(Font.BOLD, 11.5f));
                JButton reject = Theme.softButton("Từ chối");
                reject.setFont(Theme.uiFont(Font.BOLD, 11.5f));

                accept.addActionListener(e -> handleInvite(PacketType.FRIEND_ACCEPT, u.id));
                reject.addActionListener(e -> handleInvite(PacketType.FRIEND_REJECT, u.id));
                btns.add(accept);
                btns.add(reject);

                card.add(top, BorderLayout.NORTH);
                card.add(btns, BorderLayout.SOUTH);
                incomingSection.add(card);
                incomingSection.add(Box.createVerticalStrut(6));
            }
            incomingSection.add(Box.createVerticalStrut(6));
        }
        incomingSection.revalidate();
        incomingSection.repaint();
    }

    private void handleInvite(String type, long userId) {
        Theme.runAsync(
                () -> conn.request(new Packet(type).put("userId", userId)),
                res -> {
                    if (!res.isOk()) {
                        JOptionPane.showMessageDialog(this, res.str("error"));
                    } else {
                        refresh();
                    }
                },
                err -> JOptionPane.showMessageDialog(this, err)
        );
    }

    private void renderFriends() {
        friendsSection.removeAll();
        friendsSection.add(sectionHeader("Bạn bè (" + friends.size() + ")"));
        if (friends.isEmpty()) {
            friendsSection.add(mutedLabel("Chưa có bạn bè. Hãy tìm SĐT ở trên để kết bạn!"));
        } else {
            for (User u : friends) {
                Theme.RoundedPanel row = new Theme.RoundedPanel(12, Color.WHITE, null, Theme.BORDER_PINK);
                row.setLayout(new BorderLayout(10, 0));
                row.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
                row.setAlignmentX(LEFT_ALIGNMENT);
                row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

                row.add(new Theme.AvatarBadge(u.name, 36, Theme.avatarColor(u.id, false), true, u.online), BorderLayout.WEST);
                row.add(userInfoPanel(u), BorderLayout.CENTER);

                row.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        row.setFill(Theme.ITEM_HOVER);
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        row.setFill(Color.WHITE);
                    }

                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (SwingUtilities.isLeftMouseButton(e)) {
                            openDirectChat(u);
                        }
                    }

                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (e.isPopupTrigger()) showFriendMenu(e, u);
                    }

                    @Override
                    public void mouseReleased(MouseEvent e) {
                        if (e.isPopupTrigger()) showFriendMenu(e, u);
                    }
                });
                friendsSection.add(row);
                friendsSection.add(Box.createVerticalStrut(5));
            }
        }
        friendsSection.revalidate();
        friendsSection.repaint();
    }

    private void showFriendMenu(MouseEvent e, User u) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem chat = new JMenuItem("Nhắn tin");
        chat.addActionListener(ev -> openDirectChat(u));
        JMenuItem remove = new JMenuItem("Huỷ kết bạn");
        remove.setForeground(Theme.PRIMARY_RED_DARK);
        remove.addActionListener(ev -> {
            int ok = JOptionPane.showConfirmDialog(this,
                    "Huỷ kết bạn với " + u.name + "?", "Xác nhận", JOptionPane.YES_NO_OPTION);
            if (ok != JOptionPane.YES_OPTION) return;
            Theme.runAsync(
                    () -> conn.request(new Packet(PacketType.FRIEND_REMOVE).put("userId", u.id)),
                    res -> {
                        if (res.isOk()) refresh();
                        else JOptionPane.showMessageDialog(this, res.str("error"));
                    },
                    err -> JOptionPane.showMessageDialog(this, err)
            );
        });
        menu.add(chat);
        menu.add(remove);
        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void openDirectChat(User peer) {
        Theme.runAsync(
                () -> conn.request(new Packet(PacketType.CONV_OPEN_DIRECT).put("peerId", peer.id)),
                res -> {
                    if (!res.isOk()) {
                        JOptionPane.showMessageDialog(this, res.str("error"));
                        return;
                    }
                    Conversation c = res.get("conversation", Conversation.class);
                    if (c != null && onOpenConversation != null) {
                        onOpenConversation.accept(c);
                    }
                },
                err -> JOptionPane.showMessageDialog(this, err)
        );
    }

    private JPanel userInfoPanel(User u) {
        JPanel p = new JPanel(new GridLayout(0, 1));
        p.setOpaque(false);
        JLabel name = new JLabel(u.name == null ? "" : u.name);
        name.setFont(Theme.uiFont(Font.BOLD, 13f));
        name.setForeground(Theme.TEXT_DARK);
        p.add(name);
        if (u.phone != null) {
            JLabel phone = new JLabel(u.phone);
            phone.setFont(Theme.uiFont(11f));
            phone.setForeground(Theme.TEXT_MUTED);
            p.add(phone);
        }
        return p;
    }

    private JLabel sectionHeader(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.uiFont(Font.BOLD, 12f));
        l.setForeground(Theme.TEXT_MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(4, 4, 6, 4));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JLabel mutedLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.uiFont(12f));
        l.setForeground(Theme.TEXT_MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(2, 6, 6, 6));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }
}
