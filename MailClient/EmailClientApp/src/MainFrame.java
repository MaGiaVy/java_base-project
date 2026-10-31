import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

/**
 * Giao diện Mail Client phong cách Classic Green Vintage (cổ điển, hoài niệm, phối màu xanh lá cây).
 * Hỗ trợ 2 Engine: Socket thuần và JavaMail API; gửi HTML + đa file + folder MIME Nested;
 * nhận mail POP3/IMAP, hiển thị HTML trực quan, xóa thư và tải file đính kèm về ổ đĩa.
 */
public class MainFrame extends JFrame {
    private static final int FETCH_LIMIT = 20;

    // Bảng màu Classic Green Vintage (Retro Desktop Computing)
    private static final Color COLOR_PRIMARY_DARK = new Color(35, 73, 53);      // #234935 - Xanh rêu đậm cổ điển
    private static final Color COLOR_PRIMARY_MEDIUM = new Color(45, 90, 67);    // #2D5A43 - Xanh lá cây vintage
    private static final Color COLOR_ACCENT_HOVER = new Color(61, 120, 88);     // #3D7858 - Hover xanh lá
    private static final Color COLOR_BG_CREAM = new Color(244, 246, 240);       // #F4F6F0 - Kem ngà dịu mắt
    private static final Color COLOR_PANEL_BG = new Color(235, 240, 230);       // #EBF0E6 - Nền bảng phụ
    private static final Color COLOR_BORDER = new Color(180, 196, 177);         // #B4C4B1 - Viền rêu cổ điển
    private static final Color COLOR_TERMINAL_BG = new Color(20, 28, 22);       // #141C16 - Màn hình terminal đen rêu
    private static final Color COLOR_TERMINAL_TEXT = new Color(118, 210, 117);   // #76D275 - Chữ xanh phosphor retro
    private static final Color COLOR_DELETE_BTN = new Color(160, 64, 53);       // #A04035 - Nút xóa đỏ gạch cổ điển

    private final MailLog log;
    private AccountConfig config;

    // Top Panel: Cấu hình tài khoản
    private final JTextField emailField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(14);
    private final JComboBox<Engine> engineBox = new JComboBox<>(Engine.values());
    private final JButton configServerBtn = new JButton("⚙ Cấu hình Server...");

    // Tab 1: Soạn thư
    private final JTextField toField = new JTextField();
    private final JTextField subjectField = new JTextField();
    private final JTextArea bodyArea = new JTextArea(10, 50);
    private final JCheckBox htmlBox = new JCheckBox("Nội dung HTML");
    private final DefaultListModel<File> attachModel = new DefaultListModel<>();
    private final JList<File> attachList = new JList<>(attachModel);
    private final JButton sendButton = new JButton("🚀 GỬI EMAIL");

    // Tab 2: Hộp thư đến
    private final JComboBox<ReceiveProtocol> protocolBox = new JComboBox<>(ReceiveProtocol.values());
    private final JButton refreshButton = new JButton("📥 Tải hộp thư");
    private final JButton deleteButton = new JButton("🗑 Xóa thư đã chọn");
    private final DefaultTableModel inboxModel = new DefaultTableModel(
            new Object[]{"#", "Từ (Sender)", "Tiêu đề (Subject)", "Thời gian (Date)"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable inboxTable = new JTable(inboxModel);

    // Vùng chi tiết thư đã nhận
    private final JLabel detailFromLabel = new JLabel("Từ: -");
    private final JLabel detailSubjectLabel = new JLabel("Tiêu đề: -");
    private final JLabel detailDateLabel = new JLabel("Ngày: -");
    private final DefaultListModel<AttachmentItem> receivedAttachModel = new DefaultListModel<>();
    private final JList<AttachmentItem> receivedAttachList = new JList<>(receivedAttachModel);
    private final JButton saveAttachBtn = new JButton("💾 Lưu đính kèm về máy...");
    private final JEditorPane htmlViewer = new JEditorPane();
    private final JTextArea rawViewer = new JTextArea();
    private final JTabbedPane viewModeTab = new JTabbedPane();
    private List<ReceivedMail> currentInboxList = new ArrayList<>();

    // Bottom Panel: Log console
    private final JTextArea logArea = new JTextArea(6, 80);

    public MainFrame(MailLog log) {
        super("📬 Retro Mail Client - Lập Trình Mạng (Saigon University)");
        this.log = log;
        this.config = AccountConfig.gmail("user@gmail.com", "app_password");

        initClassicLook();
        setLayout(new BorderLayout(8, 8));
        getContentPane().setBackground(COLOR_BG_CREAM);

        add(buildHeaderPanel(), BorderLayout.NORTH);

        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.setFont(mainTabs.getFont().deriveFont(Font.BOLD, 13f));
        mainTabs.addTab("✉ Soạn Thư", buildComposePanel());
        mainTabs.addTab("📥 Hộp Thư Đến", buildInboxPanel());
        add(mainTabs, BorderLayout.CENTER);

        add(buildLogPanel(), BorderLayout.SOUTH);

        log.addListener(line -> SwingUtilities.invokeLater(() -> {
            logArea.append(line + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }));

        pack();
        setMinimumSize(new Dimension(880, 680));
        setLocationRelativeTo(null);
    }

    private void initClassicLook() {
        Font defaultFont = new Font("Segoe UI", Font.PLAIN, 12);
        setFont(defaultFont);
    }

    private JPanel buildHeaderPanel() {
        JPanel outer = new JPanel(new BorderLayout(4, 4));
        outer.setBackground(COLOR_PRIMARY_DARK);
        outer.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JLabel titleLabel = new JLabel("📬 SGU MAIL CLIENT (CLASSIC RETRO EDITION)");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLabel.setForeground(Color.WHITE);
        outer.add(titleLabel, BorderLayout.NORTH);

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        panel.setOpaque(false);

        JLabel emailLbl = new JLabel("Email / Username:");
        emailLbl.setForeground(new Color(240, 253, 244));
        panel.add(emailLbl);
        panel.add(emailField);

        JLabel passLbl = new JLabel("Mật khẩu / App Password:");
        passLbl.setForeground(new Color(240, 253, 244));
        panel.add(passLbl);
        panel.add(passwordField);

        JLabel engineLbl = new JLabel("Cách làm (Engine):");
        engineLbl.setForeground(new Color(240, 253, 244));
        panel.add(engineLbl);
        panel.add(engineBox);

        styleButton(configServerBtn, COLOR_PANEL_BG, new Color(35, 73, 53));
        configServerBtn.addActionListener(e -> onOpenServerConfig());
        panel.add(configServerBtn);

        outer.add(panel, BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildComposePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(COLOR_BG_CREAM);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel header = new JPanel(new GridLayout(2, 2, 6, 6));
        header.setOpaque(false);
        header.add(new JLabel("Người nhận (To - phân cách bởi , hoặc ;):"));
        header.add(toField);
        header.add(new JLabel("Tiêu đề (Subject):"));
        header.add(subjectField);
        panel.add(header, BorderLayout.NORTH);

        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JScrollPane bodyScroll = new JScrollPane(bodyArea);
        bodyScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(COLOR_BORDER), "Nội dung email"));
        panel.add(bodyScroll, BorderLayout.CENTER);

        // Khu vực đính kèm file & thư mục
        JPanel south = new JPanel(new BorderLayout(6, 6));
        south.setOpaque(false);

        attachList.setVisibleRowCount(3);
        JScrollPane attachScroll = new JScrollPane(attachList);
        attachScroll.setPreferredSize(new Dimension(200, 75));
        attachScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(COLOR_BORDER), "Danh sách đính kèm (File & Thư mục)"));
        south.add(attachScroll, BorderLayout.CENTER);

        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        buttonBar.setOpaque(false);

        JButton addFileBtn = new JButton("+ Thêm File...");
        JButton addFolderBtn = new JButton("📁 + Thêm Thư Mục...");
        JButton removeBtn = new JButton("✕ Xóa Đính Kèm");

        styleButton(addFileBtn, COLOR_PANEL_BG, COLOR_PRIMARY_DARK);
        styleButton(addFolderBtn, COLOR_PANEL_BG, COLOR_PRIMARY_DARK);
        styleButton(removeBtn, COLOR_PANEL_BG, COLOR_PRIMARY_DARK);

        addFileBtn.addActionListener(e -> onAddFiles());
        addFolderBtn.addActionListener(e -> onAddFolder());
        removeBtn.addActionListener(e -> {
            for (File f : attachList.getSelectedValuesList()) {
                attachModel.removeElement(f);
            }
        });

        htmlBox.setOpaque(false);
        htmlBox.setFont(htmlBox.getFont().deriveFont(Font.BOLD));

        styleButton(sendButton, COLOR_PRIMARY_MEDIUM, Color.WHITE);
        sendButton.setFont(sendButton.getFont().deriveFont(Font.BOLD, 13f));
        sendButton.addActionListener(e -> onSend());

        buttonBar.add(addFileBtn);
        buttonBar.add(addFolderBtn);
        buttonBar.add(removeBtn);
        buttonBar.add(htmlBox);
        buttonBar.add(sendButton);

        south.add(buttonBar, BorderLayout.SOUTH);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildInboxPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(COLOR_BG_CREAM);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolBar.setOpaque(false);
        toolBar.add(new JLabel("Giao thức nhận:"));
        toolBar.add(protocolBox);

        styleButton(refreshButton, COLOR_PRIMARY_MEDIUM, Color.WHITE);
        refreshButton.setFont(refreshButton.getFont().deriveFont(Font.BOLD));
        refreshButton.addActionListener(e -> onRefresh());
        toolBar.add(refreshButton);

        styleButton(deleteButton, COLOR_DELETE_BTN, Color.WHITE);
        deleteButton.addActionListener(e -> onDeleteSelectedMail());
        toolBar.add(deleteButton);

        panel.add(toolBar, BorderLayout.NORTH);

        // Bảng danh sách email
        inboxTable.setRowHeight(22);
        JTableHeader tableHeader = inboxTable.getTableHeader();
        tableHeader.setBackground(COLOR_PRIMARY_MEDIUM);
        tableHeader.setForeground(Color.WHITE);
        tableHeader.setFont(tableHeader.getFont().deriveFont(Font.BOLD));
        inboxTable.setSelectionBackground(new Color(208, 228, 215));

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        inboxTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        inboxTable.getColumnModel().getColumn(0).setMaxWidth(60);
        inboxTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);

        inboxTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSelectedMail();
            }
        });

        JScrollPane tableScroll = new JScrollPane(inboxTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(COLOR_BORDER));

        // Khung chi tiết thư
        JPanel detailPanel = buildDetailPanel();

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailPanel);
        split.setResizeWeight(0.42);
        split.setDividerSize(6);
        panel.add(split, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildDetailPanel() {
        JPanel p = new JPanel(new BorderLayout(6, 6));
        p.setBackground(COLOR_BG_CREAM);
        p.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(COLOR_BORDER), "Nội dung thư chi tiết"));

        JPanel meta = new JPanel(new GridLayout(3, 1, 2, 2));
        meta.setOpaque(false);
        meta.add(detailFromLabel);
        meta.add(detailSubjectLabel);
        meta.add(detailDateLabel);

        // Thanh file đính kèm đã nhận
        JPanel attachBar = new JPanel(new BorderLayout(4, 4));
        attachBar.setOpaque(false);
        receivedAttachList.setVisibleRowCount(2);
        JScrollPane attachListScroll = new JScrollPane(receivedAttachList);
        attachListScroll.setPreferredSize(new Dimension(160, 48));

        styleButton(saveAttachBtn, COLOR_PANEL_BG, COLOR_PRIMARY_DARK);
        saveAttachBtn.addActionListener(e -> onSaveAttachment());

        attachBar.add(new JLabel("Tệp đính kèm:"), BorderLayout.WEST);
        attachBar.add(attachListScroll, BorderLayout.CENTER);
        attachBar.add(saveAttachBtn, BorderLayout.EAST);

        JPanel topContainer = new JPanel(new BorderLayout(4, 4));
        topContainer.setOpaque(false);
        topContainer.add(meta, BorderLayout.NORTH);
        topContainer.add(attachBar, BorderLayout.SOUTH);
        p.add(topContainer, BorderLayout.NORTH);

        // Bộ chuyển đổi xem HTML trực quan và mã nguồn thô
        htmlViewer.setEditable(false);
        htmlViewer.setContentType("text/html");
        rawViewer.setEditable(false);
        rawViewer.setFont(new Font("Consolas", Font.PLAIN, 12));

        viewModeTab.addTab("🌐 Xem HTML Trực Quan", new JScrollPane(htmlViewer));
        viewModeTab.addTab("📄 Xem Mã Nguồn / Text Thô", new JScrollPane(rawViewer));
        p.add(viewModeTab, BorderLayout.CENTER);

        return p;
    }

    private JScrollPane buildLogPanel() {
        logArea.setEditable(false);
        logArea.setBackground(COLOR_TERMINAL_BG);
        logArea.setForeground(COLOR_TERMINAL_TEXT);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setCaretColor(COLOR_TERMINAL_TEXT);

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(COLOR_PRIMARY_MEDIUM),
                "Console Giao Thức Mạng (Client C: / Server S:)",
                0, 0, new Font("Segoe UI", Font.BOLD, 11), Color.WHITE));
        scroll.setBackground(COLOR_PRIMARY_DARK);
        return scroll;
    }

    private void styleButton(JButton btn, Color bg, Color fg) {
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BORDER),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
    }

    private void onOpenServerConfig() {
        ServerConfigDialog dialog = new ServerConfigDialog(this, config);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            dialog.applyTo(config);
            JOptionPane.showMessageDialog(this, "Đã cập nhật cấu hình máy chủ thành công!",
                    "Cấu hình", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private MailService currentService() {
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword()).replace(" ", "");
        if (email.isEmpty() || password.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập Email và Mật khẩu (App Password) trước!");
        }
        config = AccountConfig.custom(email, password,
                config.smtpHost, config.smtpPort, config.smtpSecurity,
                config.pop3Host, config.pop3Port, config.pop3Security,
                config.imapHost, config.imapPort, config.imapSecurity);
        Engine engine = (Engine) engineBox.getSelectedItem();
        return MailServiceFactory.create(engine, config, log);
    }

    private void onAddFiles() {
        JFileChooser chooser = new JFileChooser(new File("attachments"));
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            for (File f : chooser.getSelectedFiles()) {
                attachModel.addElement(f);
            }
        }
    }

    private void onAddFolder() {
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File folder = chooser.getSelectedFile();
            if (folder != null && folder.isDirectory()) {
                attachModel.addElement(folder);
            }
        }
    }

    private void onSend() {
        final MailService service;
        final OutgoingMail mail;
        try {
            service = currentService();
            mail = new OutgoingMail(OutgoingMail.splitRecipients(toField.getText()),
                    subjectField.getText(), bodyArea.getText(), htmlBox.isSelected(),
                    Collections.list(attachModel.elements()));
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
            return;
        }

        sendButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                service.send(mail);
                return null;
            }

            @Override
            protected void done() {
                sendButton.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Gửi email thành công qua " + engineBox.getSelectedItem() + "!",
                            "Thành công", JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException | ExecutionException ex) {
                    showError(ex);
                }
            }
        }.execute();
    }

    private void onRefresh() {
        final MailService service;
        try {
            service = currentService();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
            return;
        }
        final ReceiveProtocol protocol = (ReceiveProtocol) protocolBox.getSelectedItem();
        refreshButton.setEnabled(false);
        new SwingWorker<List<ReceivedMail>, Void>() {
            @Override
            protected List<ReceivedMail> doInBackground() throws Exception {
                return service.fetchInbox(protocol, FETCH_LIMIT);
            }

            @Override
            protected void done() {
                refreshButton.setEnabled(true);
                try {
                    currentInboxList = get();
                    inboxModel.setRowCount(0);
                    for (ReceivedMail m : currentInboxList) {
                        inboxModel.addRow(new Object[]{m.id, m.from, m.subject, m.date});
                    }
                    clearDetail();
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Đã tải thành công " + currentInboxList.size() + " thư mới nhất!",
                            "Hộp thư đến", JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException | ExecutionException ex) {
                    showError(ex);
                }
            }
        }.execute();
    }

    private void onDeleteSelectedMail() {
        int row = inboxTable.getSelectedRow();
        if (row < 0 || row >= currentInboxList.size()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một thư trong danh sách để xóa!",
                    "Chưa chọn thư", JOptionPane.WARNING_MESSAGE);
            return;
        }
        ReceivedMail target = currentInboxList.get(row);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc muốn xóa thư số " + target.id + " (" + target.subject + ") trên server không?",
                "Xác nhận xóa thư", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        final MailService service;
        try {
            service = currentService();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        final ReceiveProtocol protocol = (ReceiveProtocol) protocolBox.getSelectedItem();
        deleteButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                service.deleteMail(protocol, target.id);
                return null;
            }

            @Override
            protected void done() {
                deleteButton.setEnabled(true);
                try {
                    get();
                    inboxModel.removeRow(row);
                    currentInboxList.remove(row);
                    clearDetail();
                    JOptionPane.showMessageDialog(MainFrame.this, "Đã xóa thư thành công trên server!",
                            "Xóa thư", JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException | ExecutionException ex) {
                    showError(ex);
                }
            }
        }.execute();
    }

    private void onSaveAttachment() {
        AttachmentItem item = receivedAttachList.getSelectedValue();
        if (item == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một tệp trong danh sách đính kèm để lưu!",
                    "Chưa chọn tệp", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(item.getName()));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File target = chooser.getSelectedFile();
            try {
                item.saveTo(target);
                JOptionPane.showMessageDialog(this, "Đã lưu tệp đính kèm thành công:\n" + target.getAbsolutePath(),
                        "Lưu thành công", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                showError(e);
            }
        }
    }

    private void showSelectedMail() {
        int row = inboxTable.getSelectedRow();
        if (row < 0 || row >= currentInboxList.size()) {
            return;
        }
        ReceivedMail m = currentInboxList.get(row);
        detailFromLabel.setText("Từ: " + m.from);
        detailSubjectLabel.setText("Tiêu đề: " + m.subject);
        detailDateLabel.setText("Ngày: " + m.date);

        receivedAttachModel.clear();
        for (AttachmentItem a : m.attachments) {
            receivedAttachModel.addElement(a);
        }

        if (m.html) {
            htmlViewer.setText(m.body);
            rawViewer.setText(m.body);
            viewModeTab.setSelectedIndex(0);
        } else {
            htmlViewer.setText("<html><body style='font-family:sans-serif;'><pre>" + escapeHtml(m.body) + "</pre></body></html>");
            rawViewer.setText(m.body);
            viewModeTab.setSelectedIndex(1);
        }
        htmlViewer.setCaretPosition(0);
        rawViewer.setCaretPosition(0);
    }

    private void clearDetail() {
        detailFromLabel.setText("Từ: -");
        detailSubjectLabel.setText("Tiêu đề: -");
        detailDateLabel.setText("Ngày: -");
        receivedAttachModel.clear();
        htmlViewer.setText("");
        rawViewer.setText("");
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void showError(Throwable t) {
        Throwable cause = (t instanceof ExecutionException && t.getCause() != null) ? t.getCause() : t;
        String message = String.valueOf(cause.getMessage());
        log.error(message);
        JOptionPane.showMessageDialog(this, message, "Lỗi Giao Dịch", JOptionPane.ERROR_MESSAGE);
    }
}
