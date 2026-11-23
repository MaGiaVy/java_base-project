package com.zalolite.client.ui;

import com.zalolite.client.Connection;
import com.zalolite.common.Conversation;
import com.zalolite.common.Member;
import com.zalolite.common.Packet;
import com.zalolite.common.PacketType;
import com.zalolite.common.User;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Hộp thoại xem & quản lý thành viên nhóm (đổi tên, thêm/xoá thành viên, rời nhóm). */
public class GroupManageDialog extends JDialog {
    private final Connection conn;
    private final User me;
    private final Conversation conv;
    private final Runnable onLeftGroup;
    private final Consumer<String> onRenamed;

    private final JLabel nameLabel = new JLabel();
    private final JPanel membersPanel = new JPanel();
    private final JLabel status = new JLabel(" ");
    private final Set<Long> memberIds = new HashSet<>();

    public GroupManageDialog(Frame owner, Connection conn, User me, Conversation conv,
                             Consumer<String> onRenamed, Runnable onLeftGroup) {
        super(owner, "Quản lý nhóm - " + conv.name, true);
        this.conn = conn;
        this.me = me;
        this.conv = conv;
        this.onRenamed = onRenamed;
        this.onLeftGroup = onLeftGroup;

        setSize(430, 500);
        setLocationRelativeTo(owner);

        boolean isOwner = me.id == conv.ownerId;

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(Theme.SIDEBAR_BG);
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        nameLabel.setText(conv.name);
        nameLabel.setFont(Theme.uiFont(Font.BOLD, 16f));
        nameLabel.setForeground(Theme.TEXT_DARK);
        header.add(nameLabel, BorderLayout.CENTER);

        if (isOwner) {
            JButton renameBtn = Theme.softButton("Đổi tên");
            renameBtn.addActionListener(e -> doRename());
            header.add(renameBtn, BorderLayout.EAST);
        }

        membersPanel.setLayout(new BoxLayout(membersPanel, BoxLayout.Y_AXIS));
        membersPanel.setBackground(Color.WHITE);
        membersPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JScrollPane scroll = new JScrollPane(membersPanel);
        scroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.BORDER_PINK), "Thành viên nhóm"));
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        bottom.setOpaque(false);
        status.setFont(Theme.uiFont(12f));
        status.setForeground(Theme.PRIMARY_RED_DARK);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        if (isOwner) {
            JButton addBtn = Theme.primaryButton("+ Thêm thành viên");
            addBtn.addActionListener(e -> doAddMember());
            btns.add(addBtn);
        }
        JButton leaveBtn = Theme.softButton("Rời nhóm");
        leaveBtn.setForeground(Theme.PRIMARY_RED_DARK);
        leaveBtn.addActionListener(e -> doLeave());
        JButton closeBtn = Theme.softButton("Đóng");
        closeBtn.addActionListener(e -> dispose());
        btns.add(leaveBtn);
        btns.add(closeBtn);

        bottom.add(status, BorderLayout.NORTH);
        bottom.add(btns, BorderLayout.SOUTH);

        root.add(header, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);

        loadMembers();
    }

    private void loadMembers() {
        membersPanel.removeAll();
        membersPanel.add(new JLabel("Đang tải thành viên..."));
        Theme.runAsync(
                () -> conn.request(new Packet(PacketType.GROUP_MEMBERS).put("convId", conv.id)),
                res -> {
                    membersPanel.removeAll();
                    memberIds.clear();
                    if (!res.isOk()) {
                        status.setText(res.str("error"));
                        return;
                    }
                    List<Member> members = res.getList("members", Member.class);
                    boolean isOwner = me.id == conv.ownerId;
                    for (Member m : members) {
                        if (m.user == null) continue;
                        memberIds.add(m.user.id);
                        membersPanel.add(buildMemberRow(m, isOwner));
                        membersPanel.add(Box.createVerticalStrut(4));
                    }
                    membersPanel.revalidate();
                    membersPanel.repaint();
                },
                err -> status.setText(err)
        );
    }

    private JPanel buildMemberRow(Member m, boolean isOwner) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(Theme.CHAT_BG);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER_PINK, 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));

        Theme.AvatarBadge av = new Theme.AvatarBadge(
                m.user.name, 32, Theme.avatarColor(m.user.id, false), true, m.user.online);
        row.add(av, BorderLayout.WEST);

        JPanel info = new JPanel(new GridLayout(0, 1));
        info.setOpaque(false);
        String label = m.user.name + (m.user.id == me.id ? " (Bạn)" : "");
        JLabel name = new JLabel(label);
        name.setFont(Theme.uiFont(Font.BOLD, 13f));
        name.setForeground(Theme.TEXT_DARK);
        info.add(name);
        if (m.user.phone != null) {
            JLabel phone = new JLabel(m.user.phone);
            phone.setFont(Theme.uiFont(11f));
            phone.setForeground(Theme.TEXT_MUTED);
            info.add(phone);
        }
        row.add(info, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.setOpaque(false);
        if ("OWNER".equalsIgnoreCase(m.role)) {
            right.add(new Theme.PillLabel("Trưởng nhóm", Theme.BUBBLE_OTHER, Theme.PRIMARY_PURPLE, 11f, false));
        } else if (isOwner && m.user.id != me.id) {
            JButton rm = Theme.softButton("Xoá");
            rm.setForeground(Theme.PRIMARY_RED_DARK);
            rm.addActionListener(e -> doRemoveMember(m.user));
            right.add(rm);
        }
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private void doRename() {
        String newName = JOptionPane.showInputDialog(this, "Nhập tên nhóm mới:", conv.name);
        if (newName == null || newName.trim().isEmpty()) return;
        String trimmed = newName.trim();
        Theme.runAsync(
                () -> conn.request(new Packet(PacketType.GROUP_RENAME)
                        .put("convId", conv.id).put("name", trimmed)),
                res -> {
                    if (!res.isOk()) {
                        status.setText(res.str("error"));
                        return;
                    }
                    conv.name = trimmed;
                    nameLabel.setText(trimmed);
                    setTitle("Quản lý nhóm - " + trimmed);
                    if (onRenamed != null) onRenamed.accept(trimmed);
                },
                err -> status.setText(err)
        );
    }

    private void doAddMember() {
        Theme.runAsync(
                () -> conn.request(new Packet(PacketType.FRIEND_LIST)),
                res -> {
                    if (!res.isOk()) {
                        status.setText(res.str("error"));
                        return;
                    }
                    List<User> candidates = res.getList("friends", User.class).stream()
                            .filter(u -> !memberIds.contains(u.id))
                            .toList();
                    if (candidates.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "Tất cả bạn bè của bạn đều đã ở trong nhóm.");
                        return;
                    }
                    User chosen = (User) JOptionPane.showInputDialog(
                            this, "Chọn bạn để thêm vào nhóm:", "Thêm thành viên",
                            JOptionPane.PLAIN_MESSAGE, null, candidates.toArray(), candidates.get(0));
                    if (chosen == null) return;
                    Theme.runAsync(
                            () -> conn.request(new Packet(PacketType.GROUP_ADD_MEMBER)
                                    .put("convId", conv.id).put("userId", chosen.id)),
                            addRes -> {
                                if (!addRes.isOk()) {
                                    status.setText(addRes.str("error"));
                                } else {
                                    status.setText(" ");
                                    loadMembers();
                                }
                            },
                            err -> status.setText(err)
                    );
                },
                err -> status.setText(err)
        );
    }

    private void doRemoveMember(User target) {
        int ok = JOptionPane.showConfirmDialog(this,
                "Xoá " + target.name + " khỏi nhóm?", "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;
        Theme.runAsync(
                () -> conn.request(new Packet(PacketType.GROUP_REMOVE_MEMBER)
                        .put("convId", conv.id).put("userId", target.id)),
                res -> {
                    if (!res.isOk()) {
                        status.setText(res.str("error"));
                    } else {
                        loadMembers();
                    }
                },
                err -> status.setText(err)
        );
    }

    private void doLeave() {
        int ok = JOptionPane.showConfirmDialog(this,
                "Bạn chắc chắn muốn rời khỏi nhóm \"" + conv.name + "\"?",
                "Rời nhóm", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;
        Theme.runAsync(
                () -> conn.request(new Packet(PacketType.GROUP_LEAVE).put("convId", conv.id)),
                res -> {
                    if (!res.isOk()) {
                        status.setText(res.str("error"));
                        return;
                    }
                    if (onLeftGroup != null) onLeftGroup.run();
                    dispose();
                },
                err -> status.setText(err)
        );
    }
}
