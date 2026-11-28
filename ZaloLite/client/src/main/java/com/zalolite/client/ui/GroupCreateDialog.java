package com.zalolite.client.ui;

import com.zalolite.client.Connection;
import com.zalolite.common.Conversation;
import com.zalolite.common.Packet;
import com.zalolite.common.PacketType;
import com.zalolite.common.User;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Hộp thoại tạo nhóm chat mới (nhập tên nhóm và chọn tối thiểu 2 bạn bè).
 * Gọi {@code FRIEND_LIST} để nạp danh sách bạn bè và gửi {@code GROUP_CREATE} qua TLS (:9000).
 */
public class GroupCreateDialog extends JDialog {
    private final Connection conn;
    private final Consumer<Conversation> onCreated;
    private final JTextField nameField = new JTextField();
    private final JPanel listPanel = new JPanel();
    private final Map<Long, JCheckBox> checks = new LinkedHashMap<>();
    private final JLabel status = new JLabel(" ");
    private final JButton createBtn = Theme.primaryButton("Tạo nhóm");

    public GroupCreateDialog(Frame owner, Connection conn, Consumer<Conversation> onCreated) {
        super(owner, "Tạo nhóm mới", true);
        this.conn = conn;
        this.onCreated = onCreated;

        setSize(400, 480);
        setLocationRelativeTo(owner);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(Theme.SIDEBAR_BG);
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        JLabel title = new JLabel("✨ Tạo nhóm Berry Pop");
        title.setFont(Theme.emojiFont(Font.BOLD, 16f));
        title.setForeground(Theme.PRIMARY_PURPLE);
        nameField.putClientProperty("JTextField.placeholderText", "Nhập tên nhóm...");
        nameField.setPreferredSize(new Dimension(0, 36));
        top.add(title, BorderLayout.NORTH);
        top.add(nameField, BorderLayout.SOUTH);

        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(Color.WHITE);
        listPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Theme.BORDER_PINK), "Chọn bạn bè (ít nhất 2 người)"));
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel bottom = new JPanel(new BorderLayout(6, 6));
        bottom.setOpaque(false);
        status.setFont(Theme.uiFont(12f));
        status.setForeground(Theme.PRIMARY_RED_DARK);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton cancelBtn = Theme.softButton("Huỷ");
        cancelBtn.addActionListener(e -> dispose());
        createBtn.addActionListener(e -> doCreate());
        actions.add(cancelBtn);
        actions.add(createBtn);
        bottom.add(status, BorderLayout.NORTH);
        bottom.add(actions, BorderLayout.SOUTH);

        root.add(top, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);

        loadFriends();
    }

    /** [UI -> GỬI] Lấy danh sách bạn bè từ server (FRIEND_LIST) để người dùng tick chọn vào nhóm. */
    private void loadFriends() {
        listPanel.removeAll();
        listPanel.add(new JLabel("Đang tải danh sách bạn bè..."));
        Theme.runAsync(
                // [GỬI] Gửi gói FRIEND_LIST qua kênh TLS (:9000)
                () -> conn.request(new Packet(PacketType.FRIEND_LIST)),
                res -> {
                    listPanel.removeAll();
                    checks.clear();
                    if (!res.isOk()) {
                        status.setText(res.str("error"));
                        return;
                    }
                    List<User> friends = res.getList("friends", User.class);
                    if (friends.isEmpty()) {
                        JLabel empty = new JLabel("Bạn chưa có bạn bè nào để thêm vào nhóm.");
                        empty.setForeground(Theme.TEXT_MUTED);
                        listPanel.add(empty);
                    } else {
                        for (User u : friends) {
                            JCheckBox cb = new JCheckBox(u.name + " (" + u.phone + ")");
                            cb.setFont(Theme.uiFont(13f));
                            cb.setForeground(Theme.TEXT_DARK);
                            cb.setOpaque(false);
                            cb.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
                            checks.put(u.id, cb);
                            listPanel.add(cb);
                        }
                    }
                    listPanel.revalidate();
                    listPanel.repaint();
                },
                err -> status.setText(err)
        );
    }

    /** [UI -> GỬI] Bấm nút "Tạo nhóm": kiểm tra hợp lệ rồi gửi gói GROUP_CREATE kèm tên nhóm và danh sách memberIds. */
    private void doCreate() {
        String groupName = nameField.getText().trim();
        if (groupName.isEmpty()) {
            status.setText("Vui lòng nhập tên nhóm");
            return;
        }
        List<Long> memberIds = new ArrayList<>();
        for (Map.Entry<Long, JCheckBox> e : checks.entrySet()) {
            if (e.getValue().isSelected()) memberIds.add(e.getKey());
        }
        if (memberIds.size() < 2) {
            status.setText("Chọn ít nhất 2 bạn bè để tạo nhóm");
            return;
        }
        createBtn.setEnabled(false);
        status.setText(" ");
        Theme.runAsync(
                // [GỬI] Gửi yêu cầu GROUP_CREATE lên server qua TLS (:9000)
                () -> conn.request(new Packet(PacketType.GROUP_CREATE)
                        .put("name", groupName)
                        .put("memberIds", memberIds)),
                res -> {
                    createBtn.setEnabled(true);
                    if (!res.isOk()) {
                        status.setText(res.str("error"));
                        return;
                    }
                    Conversation conv = res.get("conversation", Conversation.class);
                    if (onCreated != null && conv != null) onCreated.accept(conv);
                    dispose();
                },
                err -> {
                    createBtn.setEnabled(true);
                    status.setText(err);
                }
        );
    }
}
