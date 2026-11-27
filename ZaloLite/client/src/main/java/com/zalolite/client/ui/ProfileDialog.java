package com.zalolite.client.ui;

import com.zalolite.client.Connection;
import com.zalolite.common.Packet;
import com.zalolite.common.PacketType;
import com.zalolite.common.User;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** Hộp thoại chỉnh sửa hồ sơ cá nhân và đổi mật khẩu theo phong cách Berry Pop. */
public class ProfileDialog extends JDialog {
    public ProfileDialog(Frame owner, Connection conn, User me, Consumer<User> onUpdated) {
        super(owner, "Hồ sơ cá nhân", true);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(Theme.SIDEBAR_BG);
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));

        // Header: AvatarBadge + tên + số điện thoại (chỉ đọc)
        Theme.RoundedPanel header = new Theme.RoundedPanel(16, Color.WHITE, null, Theme.BORDER_PINK);
        header.setLayout(new BorderLayout(12, 0));
        header.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        header.setAlignmentX(LEFT_ALIGNMENT);

        Theme.AvatarBadge avatar = new Theme.AvatarBadge(me.name, 52, Theme.avatarColor(me.id, false), true, true);
        JLabel headerName = new JLabel(me.name != null ? me.name : "");
        headerName.setFont(Theme.uiFont(Font.BOLD, 16f));
        headerName.setForeground(Theme.TEXT_DARK);
        JLabel phoneLabel = new JLabel("SĐT: " + (me.phone != null ? me.phone : ""));
        phoneLabel.setFont(Theme.uiFont(12.5f));
        phoneLabel.setForeground(Theme.TEXT_MUTED);

        JPanel headerInfo = new JPanel();
        headerInfo.setOpaque(false);
        headerInfo.setLayout(new BoxLayout(headerInfo, BoxLayout.Y_AXIS));
        headerInfo.add(headerName);
        headerInfo.add(Box.createVerticalStrut(2));
        headerInfo.add(phoneLabel);

        header.add(avatar, BorderLayout.WEST);
        header.add(headerInfo, BorderLayout.CENTER);
        root.add(header);
        root.add(Box.createVerticalStrut(12));

        // Section 1: Thông tin cá nhân
        Theme.RoundedPanel infoCard = cardPanel("Thông tin cá nhân");
        JTextField nameField = new JTextField(me.name != null ? me.name : "");
        JTextField emailField = new JTextField(me.email != null ? me.email : "");
        emailField.putClientProperty("JTextField.placeholderText", "name@example.com");
        JLabel infoStatus = statusLabel();
        JButton saveInfoBtn = Theme.primaryButton("Lưu thông tin");

        infoCard.add(fieldLabel("Tên hiển thị"));
        infoCard.add(nameField);
        infoCard.add(fieldLabel("Email"));
        infoCard.add(emailField);
        infoCard.add(infoStatus);
        infoCard.add(saveInfoBtn);
        root.add(infoCard);
        root.add(Box.createVerticalStrut(12));

        // Section 2: Đổi mật khẩu
        Theme.RoundedPanel passCard = cardPanel("Đổi mật khẩu");
        JPasswordField oldPassField = new JPasswordField();
        JPasswordField newPassField = new JPasswordField();
        JPasswordField confirmPassField = new JPasswordField();
        JLabel passStatus = statusLabel();
        JButton changePassBtn = Theme.softButton("Đổi mật khẩu");

        passCard.add(fieldLabel("Mật khẩu hiện tại"));
        passCard.add(oldPassField);
        passCard.add(fieldLabel("Mật khẩu mới (ít nhất 6 ký tự)"));
        passCard.add(newPassField);
        passCard.add(fieldLabel("Xác nhận mật khẩu mới"));
        passCard.add(confirmPassField);
        passCard.add(passStatus);
        passCard.add(changePassBtn);
        root.add(passCard);

        saveInfoBtn.addActionListener(e -> {
            String newName = nameField.getText().trim();
            String newEmail = emailField.getText().trim();
            if (newName.isEmpty()) {
                setStatus(infoStatus, "Tên hiển thị không được để trống", true);
                return;
            }
            saveInfoBtn.setEnabled(false);
            infoStatus.setText(" ");
            Theme.runAsync(() -> conn.request(new Packet(PacketType.PROFILE_UPDATE)
                    .put("name", newName)
                    .put("email", newEmail)), res -> {
                saveInfoBtn.setEnabled(true);
                if (res.isOk()) {
                    User updated = res.get("user", User.class);
                    me.name = updated != null && updated.name != null ? updated.name : newName;
                    me.email = updated != null ? updated.email : (newEmail.isEmpty() ? null : newEmail);
                    headerName.setText(me.name);
                    avatar.setNameAndColor(me.name, Theme.avatarColor(me.id, false), true, true);
                    if (onUpdated != null) onUpdated.accept(updated != null ? updated : me);
                    setStatus(infoStatus, "Đã cập nhật thông tin cá nhân.", false);
                } else {
                    setStatus(infoStatus, msgOr(res.str("error"), "Cập nhật thất bại"), true);
                }
            }, err -> {
                saveInfoBtn.setEnabled(true);
                setStatus(infoStatus, err, true);
            });
        });

        changePassBtn.addActionListener(e -> {
            String oldPass = new String(oldPassField.getPassword());
            String newPass = new String(newPassField.getPassword());
            String confirmPass = new String(confirmPassField.getPassword());
            if (oldPass.isEmpty()) {
                setStatus(passStatus, "Nhập mật khẩu hiện tại", true);
                return;
            }
            if (newPass.length() < 6) {
                setStatus(passStatus, "Mật khẩu mới phải có ít nhất 6 ký tự", true);
                return;
            }
            if (!newPass.equals(confirmPass)) {
                setStatus(passStatus, "Mật khẩu xác nhận không khớp", true);
                return;
            }
            changePassBtn.setEnabled(false);
            passStatus.setText(" ");
            Theme.runAsync(() -> conn.request(new Packet(PacketType.PASSWORD_CHANGE)
                    .put("oldPassword", oldPass)
                    .put("newPassword", newPass)), res -> {
                changePassBtn.setEnabled(true);
                if (res.isOk()) {
                    oldPassField.setText("");
                    newPassField.setText("");
                    confirmPassField.setText("");
                    setStatus(passStatus, "Đổi mật khẩu thành công.", false);
                } else {
                    setStatus(passStatus, msgOr(res.str("error"), "Đổi mật khẩu thất bại"), true);
                }
            }, err -> {
                changePassBtn.setEnabled(true);
                setStatus(passStatus, err, true);
            });
        });

        setContentPane(root);
        pack();
        setSize(420, getHeight() + 8);
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private static Theme.RoundedPanel cardPanel(String titleText) {
        Theme.RoundedPanel card = new Theme.RoundedPanel(16, Color.WHITE, null, Theme.BORDER_PINK);
        card.setLayout(new GridLayout(0, 1, 6, 5));
        card.setBorder(BorderFactory.createEmptyBorder(12, 16, 14, 16));
        card.setAlignmentX(LEFT_ALIGNMENT);
        JLabel title = new JLabel(titleText);
        title.setFont(Theme.uiFont(Font.BOLD, 14f));
        title.setForeground(Theme.PRIMARY_RED);
        card.add(title);
        return card;
    }

    private static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.uiFont(Font.BOLD, 12f));
        l.setForeground(Theme.TEXT_DARK);
        return l;
    }

    private static JLabel statusLabel() {
        JLabel l = new JLabel(" ");
        l.setFont(Theme.uiFont(12f));
        l.setForeground(Theme.PRIMARY_RED_DARK);
        return l;
    }

    private static void setStatus(JLabel label, String msg, boolean error) {
        label.setForeground(error ? Theme.PRIMARY_RED_DARK : Theme.ONLINE_GREEN);
        label.setText(msg);
    }

    private static String msgOr(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
