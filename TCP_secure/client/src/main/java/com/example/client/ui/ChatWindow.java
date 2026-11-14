package com.example.client.ui;

import com.example.client.ChatClient;
import com.example.model.ChatMessage;
import com.example.model.MessageType;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modern Swing Chat Window with TLS Security Status, Real-time messaging,
 * and background network thread safety (Slide 33 & 34).
 */
public class ChatWindow extends JFrame {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern USER_JOIN_PATTERN = Pattern.compile("^(?:SYSTEM:\\s*)?(\\w+)\\s+đã tham gia", Pattern.CASE_INSENSITIVE);

    private final ChatClient client;
    private final DefaultListModel<String> onlineListModel = new DefaultListModel<>();

    private JTextPane messagePane;
    private StyledDocument doc;
    private JTextField inputField;
    private JButton sendButton;
    private JLabel statusBadge;
    private JLabel onlineCountLabel;
    private JList<String> userList;

    // Styles for message styling
    private Style styleTimestamp;
    private Style styleOwnUser;
    private Style stylePeerUser;
    private Style styleMessageBody;
    private Style styleSystem;
    private Style styleUserLeft;

    public ChatWindow(ChatClient client) {
        super("Secure TCP Chat - " + client.getUsername() + " (ID: #" + client.getClientId() + ")");
        this.client = client;

        initUI();
        initStyles();
        setupNetworking();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(880, 620);
        setMinimumSize(new Dimension(680, 480));
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(245, 247, 250));

        // 1. TOP HEADER - SECURITY STATUS & USER INFO
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // 2. CENTER - MESSAGES & ONLINE USERS
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setResizeWeight(0.8);
        splitPane.setDividerSize(6);
        splitPane.setBorder(null);

        // Message display area
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatPanel.setBorder(new EmptyBorder(10, 10, 10, 5));
        chatPanel.setOpaque(false);

        messagePane = new JTextPane();
        messagePane.setEditable(false);
        messagePane.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        messagePane.setBackground(Color.WHITE);
        messagePane.setMargin(new Insets(10, 10, 10, 10));

        DefaultCaret caret = (DefaultCaret) messagePane.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JScrollPane messageScrollPane = new JScrollPane(messagePane);
        messageScrollPane.setBorder(new LineBorder(new Color(222, 226, 230), 1, true));
        messageScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        chatPanel.add(messageScrollPane, BorderLayout.CENTER);

        // Online users sidebar
        JPanel userPanel = createOnlineUsersPanel();

        splitPane.setLeftComponent(chatPanel);
        splitPane.setRightComponent(userPanel);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // 3. BOTTOM - INPUT PANEL
        JPanel bottomPanel = createInputPanel();
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // Safe window closing
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeApplication();
            }
        });
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(15, 0));
        header.setBackground(new Color(24, 32, 48));
        header.setBorder(new EmptyBorder(12, 18, 12, 18));

        // Left: Security status & TLS indicator
        JPanel leftPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        leftPanel.setOpaque(false);

        JLabel appTitle = new JLabel("SECURE TCP CHAT");
        appTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        appTitle.setForeground(Color.WHITE);

        String tlsInfo = String.format("🔒 %s Encrypted - Kênh truyền an toàn (%s)",
                client.getTlsProtocol(), client.getCipherSuite());
        JLabel securityInfo = new JLabel(tlsInfo);
        securityInfo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        securityInfo.setForeground(new Color(75, 209, 116)); // bright soft green

        leftPanel.add(appTitle);
        leftPanel.add(securityInfo);

        // Right: User Profile Badge
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightPanel.setOpaque(false);

        JLabel userBadge = new JLabel("👤 " + client.getUsername() + "  (ID: #" + client.getClientId() + ")");
        userBadge.setFont(new Font("Segoe UI", Font.BOLD, 13));
        userBadge.setForeground(new Color(240, 240, 240));

        statusBadge = new JLabel("🟢 Trực tuyến");
        statusBadge.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusBadge.setForeground(new Color(130, 224, 170));

        JButton disconnectBtn = new JButton("Đăng xuất");
        disconnectBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        disconnectBtn.setBackground(new Color(220, 53, 69));
        disconnectBtn.setForeground(Color.WHITE);
        disconnectBtn.setFocusPainted(false);
        disconnectBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        disconnectBtn.addActionListener(e -> closeApplication());

        rightPanel.add(userBadge);
        rightPanel.add(statusBadge);
        rightPanel.add(disconnectBtn);

        header.add(leftPanel, BorderLayout.WEST);
        header.add(rightPanel, BorderLayout.EAST);
        return header;
    }

    private JPanel createOnlineUsersPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setPreferredSize(new Dimension(190, 0));
        panel.setBorder(new EmptyBorder(10, 5, 10, 10));
        panel.setOpaque(false);

        onlineCountLabel = new JLabel("👥 Người dùng online (1)");
        onlineCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        onlineCountLabel.setForeground(new Color(52, 58, 64));

        onlineListModel.addElement("🟢 " + client.getUsername() + " (Bạn)");

        userList = new JList<>(onlineListModel);
        userList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setBackground(Color.WHITE);
        userList.setBorder(new EmptyBorder(5, 5, 5, 5));

        JScrollPane listScrollPane = new JScrollPane(userList);
        listScrollPane.setBorder(new LineBorder(new Color(222, 226, 230), 1, true));

        panel.add(onlineCountLabel, BorderLayout.NORTH);
        panel.add(listScrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createInputPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setBorder(new EmptyBorder(0, 10, 12, 10));
        panel.setOpaque(false);

        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.setBorder(new CompoundBorder(
                new LineBorder(new Color(206, 212, 218), 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));

        sendButton = new JButton("Gửi tin");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        sendButton.setBackground(new Color(13, 110, 253));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFocusPainted(false);
        sendButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        sendButton.setPreferredSize(new Dimension(100, 40));

        panel.add(inputField, BorderLayout.CENTER);
        panel.add(sendButton, BorderLayout.EAST);

        // Actions
        sendButton.addActionListener(e -> sendCurrentMessage());
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendCurrentMessage();
                }
            }
        });

        return panel;
    }

    private void initStyles() {
        doc = messagePane.getStyledDocument();

        styleTimestamp = doc.addStyle("timestamp", null);
        StyleConstants.setForeground(styleTimestamp, new Color(108, 117, 125));
        StyleConstants.setFontSize(styleTimestamp, 11);
        StyleConstants.setItalic(styleTimestamp, true);

        styleOwnUser = doc.addStyle("ownUser", null);
        StyleConstants.setForeground(styleOwnUser, new Color(13, 110, 253));
        StyleConstants.setBold(styleOwnUser, true);

        stylePeerUser = doc.addStyle("peerUser", null);
        StyleConstants.setForeground(stylePeerUser, new Color(25, 135, 84));
        StyleConstants.setBold(stylePeerUser, true);

        styleMessageBody = doc.addStyle("messageBody", null);
        StyleConstants.setForeground(styleMessageBody, new Color(33, 37, 41));
        StyleConstants.setFontSize(styleMessageBody, 13);

        styleSystem = doc.addStyle("system", null);
        StyleConstants.setForeground(styleSystem, new Color(111, 66, 193));
        StyleConstants.setBold(styleSystem, true);

        styleUserLeft = doc.addStyle("userLeft", null);
        StyleConstants.setForeground(styleUserLeft, new Color(220, 53, 69));
        StyleConstants.setItalic(styleUserLeft, true);

        // Initial system welcome message
        appendLine("[BẢO MẬT] Kênh truyền TLS 1.3 / 1.2 đã thiết lập thành công.", styleSystem);
        appendLine("[HỆ THỐNG] Chào mừng " + client.getUsername() + " tham gia phòng chat an toàn!", styleSystem);
    }

    /**
     * Starts listening for network messages on background thread.
     * All UI mutations are explicitly dispatched to Event Dispatch Thread via invokeLater (Slide 33 & 34).
     */
    private void setupNetworking() {
        client.startReceiveLoop(
                msg -> SwingUtilities.invokeLater(() -> handleIncomingMessage(msg)),
                () -> SwingUtilities.invokeLater(this::handleDisconnected)
        );
    }

    private void handleIncomingMessage(ChatMessage msg) {
        String type = msg.getType();
        String timeStr = "[" + LocalTime.now().format(TIME_FORMAT) + "] ";

        if (MessageType.MESSAGE.getValue().equalsIgnoreCase(type)) {
            String sender = msg.getUsername();
            String content = msg.getContent() != null ? msg.getContent() : "";
            boolean isOwn = sender != null && sender.equalsIgnoreCase(client.getUsername());

            appendStyledText(timeStr, styleTimestamp);
            appendStyledText("[" + sender + "]: ", isOwn ? styleOwnUser : stylePeerUser);
            appendStyledText(content + "\n", styleMessageBody);

            addUserToOnlineList(sender);

        } else if (MessageType.SYSTEM.getValue().equalsIgnoreCase(type)) {
            String content = msg.getContent() != null ? msg.getContent() : "";
            appendStyledText(timeStr, styleTimestamp);
            appendStyledText("[HỆ THỐNG] " + content + "\n", styleSystem);

            // Check if user joined
            Matcher matcher = USER_JOIN_PATTERN.matcher(content);
            if (matcher.find()) {
                addUserToOnlineList(matcher.group(1));
            }

        } else if (MessageType.USER_LEFT.getValue().equalsIgnoreCase(type)) {
            String content = msg.getContent() != null ? msg.getContent() : "";
            String user = msg.getUsername();
            appendStyledText(timeStr, styleTimestamp);
            appendStyledText("[THOÁT] " + content + "\n", styleUserLeft);

            if (user != null) {
                removeUserFromOnlineList(user);
            }
        }
    }

    private void handleDisconnected() {
        statusBadge.setText("🔴 Đã ngắt kết nối");
        statusBadge.setForeground(new Color(220, 53, 69));
        inputField.setEnabled(false);
        sendButton.setEnabled(false);

        appendLine("\n[CẢNH BÁO] Kết nối tới máy chủ đã bị ngắt.", styleUserLeft);
        JOptionPane.showMessageDialog(this,
                "Kết nối tới máy chủ đã bị ngắt.\nVui lòng khởi động lại ứng dụng để kết nối lại.",
                "Mất kết nối",
                JOptionPane.WARNING_MESSAGE);
    }

    private void sendCurrentMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        try {
            client.sendMessage(text);
            inputField.setText("");
            inputField.requestFocusInWindow();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Không thể gửi tin nhắn: " + ex.getMessage(),
                    "Lỗi gửi tin",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addUserToOnlineList(String username) {
        if (username == null || username.trim().isEmpty()) return;
        String name = username.trim();
        if (name.equalsIgnoreCase(client.getUsername())) return;

        for (int i = 0; i < onlineListModel.size(); i++) {
            String entry = onlineListModel.get(i);
            if (entry.contains(name)) {
                return; // already listed
            }
        }
        onlineListModel.addElement("🟢 " + name);
        updateOnlineCount();
    }

    private void removeUserFromOnlineList(String username) {
        if (username == null || username.trim().isEmpty()) return;
        String name = username.trim();
        for (int i = 0; i < onlineListModel.size(); i++) {
            String entry = onlineListModel.get(i);
            if (entry.contains(name) && !entry.contains("(Bạn)")) {
                onlineListModel.remove(i);
                break;
            }
        }
        updateOnlineCount();
    }

    private void updateOnlineCount() {
        onlineCountLabel.setText("👥 Người dùng online (" + onlineListModel.size() + ")");
    }

    private void appendStyledText(String text, Style style) {
        try {
            doc.insertString(doc.getLength(), text, style);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void appendLine(String text, Style style) {
        String timeStr = "[" + LocalTime.now().format(TIME_FORMAT) + "] ";
        appendStyledText(timeStr, styleTimestamp);
        appendStyledText(text + "\n", style);
    }

    private void closeApplication() {
        if (client != null) {
            client.disconnect();
        }
        dispose();
        System.exit(0);
    }
}
