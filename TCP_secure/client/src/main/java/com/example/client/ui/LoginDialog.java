package com.example.client.ui;

import com.example.client.ChatClient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Modern modal Swing dialog for TLS Chat Login.
 */
public class LoginDialog extends JDialog {

    private final JTextField hostField;
    private final JTextField portField;
    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JLabel statusLabel;
    private final JButton loginButton;
    private final JButton exitButton;

    private boolean succeeded = false;
    private ChatClient client;

    public LoginDialog(Frame parent) {
        super(parent, "Đăng nhập Phòng Chat Bảo Mật (TLS)", true);

        // Layout setup
        JPanel rootPanel = new JPanel(new BorderLayout(15, 15));
        rootPanel.setBorder(new EmptyBorder(20, 25, 20, 25));
        rootPanel.setBackground(new Color(248, 249, 250));

        // Header Banner
        JPanel headerPanel = new JPanel(new BorderLayout(5, 5));
        headerPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("SECURE TCP CHAT");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(new Color(33, 37, 41));

        JLabel subtitleLabel = new JLabel("🔒 Kênh truyền mã hóa TLS 1.3 / 1.2 & Xác thực X.509");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(new Color(40, 167, 69));

        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(subtitleLabel, BorderLayout.SOUTH);

        // Form Fields
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        hostField = new JTextField("localhost", 15);
        portField = new JTextField("5000", 6);
        usernameField = new JTextField("an", 15);
        passwordField = new JPasswordField("pass123", 15);

        // Host & Port row
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 1; gbc.weightx = 0;
        formPanel.add(createFormLabel("Server Host:"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1;
        formPanel.add(hostField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        formPanel.add(createFormLabel("Cổng (Port):"), gbc);
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1;
        formPanel.add(portField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        formPanel.add(createFormLabel("Tài khoản:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; gbc.weightx = 1;
        formPanel.add(usernameField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0;
        formPanel.add(createFormLabel("Mật khẩu:"), gbc);
        gbc.gridx = 1; gbc.gridy = 3; gbc.weightx = 1;
        formPanel.add(passwordField, gbc);

        // Status message
        statusLabel = new JLabel(" ");
        statusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        statusLabel.setForeground(new Color(220, 53, 69));
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        formPanel.add(statusLabel, gbc);

        // Button Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);

        loginButton = new JButton("Đăng nhập");
        loginButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        loginButton.setBackground(new Color(13, 110, 253));
        loginButton.setForeground(Color.WHITE);
        loginButton.setFocusPainted(false);
        loginButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        exitButton = new JButton("Thoát");
        exitButton.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        exitButton.setFocusPainted(false);
        exitButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        buttonPanel.add(exitButton);
        buttonPanel.add(loginButton);

        rootPanel.add(headerPanel, BorderLayout.NORTH);
        rootPanel.add(formPanel, BorderLayout.CENTER);
        rootPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(rootPanel);
        getRootPane().setDefaultButton(loginButton);

        // Events
        loginButton.addActionListener(e -> performLogin());
        exitButton.addActionListener(e -> cancelLogin());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancelLogin();
            }
        });

        pack();
        setResizable(false);
        setLocationRelativeTo(parent);
    }

    private JLabel createFormLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        label.setForeground(new Color(73, 80, 87));
        return label;
    }

    private void performLogin() {
        String host = hostField.getText().trim();
        String portStr = portField.getText().trim();
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (host.isEmpty()) {
            showError("Vui lòng nhập Server Host.");
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port <= 0 || port > 65535) {
                showError("Cổng không hợp lệ (1 - 65535).");
                return;
            }
        } catch (NumberFormatException e) {
            showError("Cổng phải là số nguyên.");
            return;
        }

        if (username.isEmpty()) {
            showError("Vui lòng nhập Username.");
            return;
        }
        if (password.isEmpty()) {
            showError("Vui lòng nhập Password.");
            return;
        }

        setLoading(true, "Đang kết nối TLS & xác thực...");

        // Perform connection on background thread to prevent UI freeze
        new Thread(() -> {
            ChatClient chatClient = new ChatClient();
            try {
                chatClient.connect(host, port);
                boolean ok = chatClient.login(username, password);

                SwingUtilities.invokeLater(() -> {
                    if (ok) {
                        this.client = chatClient;
                        this.succeeded = true;
                        dispose();
                    } else {
                        setLoading(false, null);
                        String err = chatClient.getLastErrorMessage();
                        showError("Đăng nhập thất bại: " + (err != null ? err : "Sai thông tin"));
                    }
                });
            } catch (Exception ex) {
                chatClient.disconnect();
                SwingUtilities.invokeLater(() -> {
                    setLoading(false, null);
                    showError("Lỗi kết nối/TLS: " + ex.getMessage());
                });
            }
        }, "LoginWorkerThread").start();
    }

    private void cancelLogin() {
        succeeded = false;
        if (client != null) {
            client.disconnect();
        }
        dispose();
    }

    private void setLoading(boolean loading, String message) {
        loginButton.setEnabled(!loading);
        exitButton.setEnabled(!loading);
        hostField.setEnabled(!loading);
        portField.setEnabled(!loading);
        usernameField.setEnabled(!loading);
        passwordField.setEnabled(!loading);

        if (loading) {
            statusLabel.setForeground(new Color(13, 110, 253));
            statusLabel.setText(message != null ? message : "Đang xử lý...");
        }
    }

    private void showError(String message) {
        statusLabel.setForeground(new Color(220, 53, 69));
        statusLabel.setText(message);
    }

    public boolean isSucceeded() {
        return succeeded;
    }

    public ChatClient getClient() {
        return client;
    }

    public String getUsername() {
        return usernameField.getText().trim();
    }

    public String getHost() {
        return hostField.getText().trim();
    }

    public int getPort() {
        try {
            return Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            return ChatClient.DEFAULT_PORT;
        }
    }
}
