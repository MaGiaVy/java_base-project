package ui;

import client.FtpClient;
import common.FtpFileEntry;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Giao diện FTP Client hoàn chỉnh với bố cục Dual-Pane (Local - Remote)
 * Áp dụng Design Tokens tông màu Đỏ Ruby (#DC2626)
 */
public class FtpClientPanel extends JPanel {

    private final FtpClient ftpClient;
    private Path localCurrentDir;

    // Thành phần Quick Connect
    private JTextField txtHost, txtPort, txtUser;
    private JPasswordField txtPass;
    private JButton btnConnect, btnDisconnect;
    private JLabel lblStatus;

    // Dual-Pane File Tables
    private JLabel lblLocalPath, lblRemotePath;
    private JTable localTable, remoteTable;
    private DefaultTableModel localModel, remoteModel;

    // Toolbar buttons
    private JButton btnUpload, btnDownload, btnRefresh, btnNewFolder, btnNewFile, btnRename, btnDelete, btnPreview;

    // Progress Bar
    private JProgressBar progressBar;
    private JLabel lblProgressInfo;

    // Console Log
    private JTextPane logPane;
    private StyledDocument logDoc;

    public FtpClientPanel(Path initialLocalDir, int defaultPort) {
        this.localCurrentDir = initialLocalDir != null ? initialLocalDir.toAbsolutePath().normalize() : Paths.get("FtpClientLocal").toAbsolutePath().normalize();
        this.ftpClient = new FtpClient(this::appendLog);

        setLayout(new BorderLayout(8, 8));
        setBackground(RedTheme.BG_MAIN);
        setBorder(new EmptyBorder(8, 8, 8, 8));

        initUI(defaultPort);
        refreshLocalFiles();
    }

    private void initUI(int defaultPort) {
        // 1. TOP: Quick Connect Bar
        add(createQuickConnectPanel(defaultPort), BorderLayout.NORTH);

        // 2. CENTER: SplitPane (Toolbar + Dual Table + Progress + Console)
        JPanel centerPanel = new JPanel(new BorderLayout(6, 6));
        centerPanel.setOpaque(false);

        // Action Toolbar
        centerPanel.add(createActionToolbar(), BorderLayout.NORTH);

        // Dual Pane File Explorer
        JSplitPane fileSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createLocalPane(), createRemotePane());
        fileSplitPane.setResizeWeight(0.5);
        fileSplitPane.setDividerSize(6);
        fileSplitPane.setBorder(null);

        // Bottom area (Progress + Log Console)
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.setOpaque(false);
        bottomPanel.add(createProgressPanel(), BorderLayout.NORTH);
        bottomPanel.add(createLogConsolePanel(), BorderLayout.CENTER);

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, fileSplitPane, bottomPanel);
        mainSplitPane.setResizeWeight(0.65);
        mainSplitPane.setDividerSize(6);
        mainSplitPane.setBorder(null);

        centerPanel.add(mainSplitPane, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);
    }

    private JPanel createQuickConnectPanel(int defaultPort) {
        JPanel p = RedTheme.createCardPanel();
        p.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 6));

        txtHost = RedTheme.createTextField(10);
        txtHost.setText("127.0.0.1");

        txtPort = RedTheme.createTextField(4);
        txtPort.setText(String.valueOf(defaultPort));

        txtUser = RedTheme.createTextField(8);
        txtUser.setText("student");

        txtPass = new JPasswordField(8);
        txtPass.setFont(RedTheme.FONT_BODY);
        txtPass.setText("123456");

        btnConnect = RedTheme.createPrimaryButton("Kết Nối");
        btnDisconnect = RedTheme.createSecondaryButton("Ngắt Kết Nối");
        btnDisconnect.setEnabled(false);

        lblStatus = new JLabel("● Chưa kết nối");
        lblStatus.setFont(RedTheme.FONT_BODY_BOLD);
        lblStatus.setForeground(RedTheme.TEXT_MUTED);

        p.add(new JLabel("Host:"));
        p.add(txtHost);
        p.add(new JLabel("Port:"));
        p.add(txtPort);
        p.add(new JLabel("User:"));
        p.add(txtUser);
        p.add(new JLabel("Pass:"));
        p.add(txtPass);
        p.add(btnConnect);
        p.add(btnDisconnect);
        p.add(Box.createHorizontalStrut(10));
        p.add(lblStatus);

        btnConnect.addActionListener(e -> doConnect());
        btnDisconnect.addActionListener(e -> doDisconnect());

        return p;
    }

    private JPanel createActionToolbar() {
        JPanel tb = RedTheme.createCardPanel();
        tb.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 4));

        btnUpload = RedTheme.createPrimaryButton("↑ Tải Lên (STOR)");
        btnDownload = RedTheme.createPrimaryButton("↓ Tải Xuống (RETR)");
        btnRefresh = RedTheme.createSecondaryButton("↻ Làm Mới");
        btnNewFolder = RedTheme.createSecondaryButton("+ Thư Mục Mới");
        btnNewFile = RedTheme.createSecondaryButton("📄 Tạo File Mới");
        btnRename = RedTheme.createSecondaryButton("✎ Đổi Tên");
        btnDelete = RedTheme.createSecondaryButton("✕ Xóa");
        btnPreview = RedTheme.createSecondaryButton("👁 Xem & Sửa");

        tb.add(btnUpload);
        tb.add(btnDownload);
        tb.add(Box.createHorizontalStrut(8));
        tb.add(btnRefresh);
        tb.add(btnNewFolder);
        tb.add(btnNewFile);
        tb.add(btnRename);
        tb.add(btnDelete);
        tb.add(btnPreview);

        btnUpload.addActionListener(e -> doUploadSelected());
        btnDownload.addActionListener(e -> doDownloadSelected());
        btnRefresh.addActionListener(e -> {
            refreshLocalFiles();
            refreshRemoteFiles();
        });
        btnNewFolder.addActionListener(e -> doNewFolder());
        btnNewFile.addActionListener(e -> doNewFile());
        btnRename.addActionListener(e -> doRename());
        btnDelete.addActionListener(e -> doDelete());
        btnPreview.addActionListener(e -> doPreview());

        return tb;
    }

    private JPanel createLocalPane() {
        JPanel p = RedTheme.createCardPanel();
        p.setLayout(new BorderLayout(4, 4));
        p.setBorder(RedTheme.createSectionBorder("Máy Cục Bộ (Local Files)"));

        JPanel nav = new JPanel(new BorderLayout(6, 2));
        nav.setOpaque(false);
        JButton btnUp = RedTheme.createSecondaryButton("▲ Lên (..)");
        btnUp.addActionListener(e -> doLocalUp());
        lblLocalPath = new JLabel(localCurrentDir.toString());
        lblLocalPath.setFont(RedTheme.FONT_BODY);
        lblLocalPath.setForeground(RedTheme.TEXT_MUTED);

        nav.add(btnUp, BorderLayout.WEST);
        nav.add(lblLocalPath, BorderLayout.CENTER);
        p.add(nav, BorderLayout.NORTH);

        String[] cols = {"Loại", "Tên Tập Tin", "Kích Thước", "Ngày Cập Nhật"};
        localModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        localTable = new JTable(localModel);
        RedTheme.styleTable(localTable);

        // Menu chuột phải cho bảng Local
        JPopupMenu localPopup = new JPopupMenu();
        JMenuItem itemLocalView = new JMenuItem("👁 Xem & Chỉnh Sửa File");
        JMenuItem itemLocalUpload = new JMenuItem("↑ Tải Lên Server (STOR)");
        JMenuItem itemLocalRename = new JMenuItem("✎ Đổi Tên");
        JMenuItem itemLocalDelete = new JMenuItem("✕ Xóa Tập Tin");
        localPopup.add(itemLocalView);
        localPopup.add(itemLocalUpload);
        localPopup.addSeparator();
        localPopup.add(itemLocalRename);
        localPopup.add(itemLocalDelete);

        itemLocalView.addActionListener(e -> {
            int row = localTable.getSelectedRow();
            if (row != -1) {
                String type = (String) localModel.getValueAt(row, 0);
                String name = (String) localModel.getValueAt(row, 1);
                if (!"[DIR]".equals(type)) doPreviewLocal(name);
            }
        });
        itemLocalUpload.addActionListener(e -> doUploadSelected());
        itemLocalRename.addActionListener(e -> doLocalRename());
        itemLocalDelete.addActionListener(e -> doLocalDelete());

        localTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = localTable.rowAtPoint(e.getPoint());
                if (row != -1 && SwingUtilities.isRightMouseButton(e)) {
                    localTable.setRowSelectionInterval(row, row);
                    localPopup.show(localTable, e.getX(), e.getY());
                } else if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    if (row != -1) {
                        String type = (String) localModel.getValueAt(row, 0);
                        String name = (String) localModel.getValueAt(row, 1);
                        if ("[DIR]".equals(type)) {
                            localCurrentDir = localCurrentDir.resolve(name).normalize();
                            refreshLocalFiles();
                        } else {
                            doPreviewLocal(name);
                        }
                    }
                }
            }
        });

        p.add(new JScrollPane(localTable), BorderLayout.CENTER);
        return p;
    }

    private JPanel createRemotePane() {
        JPanel p = RedTheme.createCardPanel();
        p.setLayout(new BorderLayout(4, 4));
        p.setBorder(RedTheme.createSectionBorder("Máy Chủ Từ Xa (Remote FTP Server)"));

        JPanel nav = new JPanel(new BorderLayout(6, 2));
        nav.setOpaque(false);
        JButton btnUp = RedTheme.createSecondaryButton("▲ Thư Mục Cha (CDUP)");
        btnUp.addActionListener(e -> doRemoteCdup());
        lblRemotePath = new JLabel("/");
        lblRemotePath.setFont(RedTheme.FONT_BODY);
        lblRemotePath.setForeground(RedTheme.TEXT_MUTED);

        nav.add(btnUp, BorderLayout.WEST);
        nav.add(lblRemotePath, BorderLayout.CENTER);
        p.add(nav, BorderLayout.NORTH);

        String[] cols = {"Loại", "Tên Tập Tin", "Kích Thước", "Quyền", "Ngày Cập Nhật"};
        remoteModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        remoteTable = new JTable(remoteModel);
        RedTheme.styleTable(remoteTable);

        // Menu chuột phải cho bảng Remote
        JPopupMenu remotePopup = new JPopupMenu();
        JMenuItem itemRemoteView = new JMenuItem("👁 Xem & Chỉnh Sửa File");
        JMenuItem itemRemoteDownload = new JMenuItem("↓ Tải Xuống Máy (RETR)");
        JMenuItem itemRemoteRename = new JMenuItem("✎ Đổi Tên (RNFR/RNTO)");
        JMenuItem itemRemoteDelete = new JMenuItem("✕ Xóa Mục Này (DELE)");
        remotePopup.add(itemRemoteView);
        remotePopup.add(itemRemoteDownload);
        remotePopup.addSeparator();
        remotePopup.add(itemRemoteRename);
        remotePopup.add(itemRemoteDelete);

        itemRemoteView.addActionListener(e -> {
            int row = remoteTable.getSelectedRow();
            if (row != -1) {
                String type = (String) remoteModel.getValueAt(row, 0);
                String name = (String) remoteModel.getValueAt(row, 1);
                if (!"[DIR]".equals(type)) doPreviewRemote(name);
            }
        });
        itemRemoteDownload.addActionListener(e -> doDownloadSelected());
        itemRemoteRename.addActionListener(e -> doRename());
        itemRemoteDelete.addActionListener(e -> doDelete());

        remoteTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = remoteTable.rowAtPoint(e.getPoint());
                if (row != -1 && SwingUtilities.isRightMouseButton(e)) {
                    remoteTable.setRowSelectionInterval(row, row);
                    remotePopup.show(remoteTable, e.getX(), e.getY());
                } else if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    if (row != -1) {
                        String type = (String) remoteModel.getValueAt(row, 0);
                        String name = (String) remoteModel.getValueAt(row, 1);
                        if ("[DIR]".equals(type)) {
                            doRemoteCwd(name);
                        } else {
                            doPreviewRemote(name);
                        }
                    }
                }
            }
        });

        p.add(new JScrollPane(remoteTable), BorderLayout.CENTER);
        return p;
    }

    private JPanel createProgressPanel() {
        JPanel p = RedTheme.createCardPanel();
        p.setLayout(new BorderLayout(6, 4));
        p.setPreferredSize(new Dimension(p.getWidth(), 38));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setForeground(RedTheme.PRIMARY);
        progressBar.setBackground(new Color(241, 245, 249));

        lblProgressInfo = new JLabel("Sẵn sàng truyền dữ liệu");
        lblProgressInfo.setFont(RedTheme.FONT_BODY);
        lblProgressInfo.setForeground(RedTheme.TEXT_MUTED);

        p.add(lblProgressInfo, BorderLayout.WEST);
        p.add(progressBar, BorderLayout.CENTER);
        return p;
    }

    private JPanel createLogConsolePanel() {
        JPanel p = RedTheme.createCardPanel();
        p.setLayout(new BorderLayout(4, 4));
        p.setPreferredSize(new Dimension(p.getWidth(), 160));
        p.setBorder(RedTheme.createSectionBorder("Cửa Sổ Lệnh & Nhật Ký Truyền Thông (FTP Protocol Log)"));

        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setFont(RedTheme.FONT_MONO);
        logPane.setBackground(Color.WHITE);
        logDoc = logPane.getStyledDocument();

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        toolbar.setOpaque(false);
        JButton btnClear = RedTheme.createSecondaryButton("Xóa Log");
        btnClear.addActionListener(e -> logPane.setText(""));
        toolbar.add(btnClear);

        p.add(toolbar, BorderLayout.NORTH);
        p.add(new JScrollPane(logPane), BorderLayout.CENTER);
        return p;
    }

    // ==========================================
    // CÁC HÀM XỬ LÝ SỰ KIỆN MẠNG & THAO TÁC FILE
    // ==========================================

    public void setConnectionFields(String host, int port, String user, String pass) {
        txtHost.setText(host);
        txtPort.setText(String.valueOf(port));
        txtUser.setText(user);
        txtPass.setText(pass);
    }

    public void triggerConnect() {
        doConnect();
    }

    private void doConnect() {
        String host = txtHost.getText().trim();
        int port;
        try {
            port = Integer.parseInt(txtPort.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Cổng không hợp lệ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String user = txtUser.getText().trim();
        String pass = new String(txtPass.getPassword());

        btnConnect.setEnabled(false);
        lblStatus.setText("● Đang kết nối...");
        lblStatus.setForeground(RedTheme.WARNING);

        new Thread(() -> {
            try {
                boolean ok = ftpClient.connect(host, port);
                if (ok) {
                    boolean logged = ftpClient.login(user, pass);
                    SwingUtilities.invokeLater(() -> {
                        if (logged) {
                            lblStatus.setText("● Đã đăng nhập: " + user + "@" + host + ":" + port);
                            lblStatus.setForeground(RedTheme.SUCCESS);
                            btnDisconnect.setEnabled(true);
                            refreshRemoteFiles();
                        } else {
                            lblStatus.setText("● Sai tài khoản / mật khẩu");
                            lblStatus.setForeground(RedTheme.ERROR);
                            btnConnect.setEnabled(true);
                        }
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        lblStatus.setText("● Kết nối thất bại");
                        lblStatus.setForeground(RedTheme.ERROR);
                        btnConnect.setEnabled(true);
                    });
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("● Lỗi: " + ex.getMessage());
                    lblStatus.setForeground(RedTheme.ERROR);
                    btnConnect.setEnabled(true);
                    JOptionPane.showMessageDialog(this, "Không thể kết nối đến FTP Server:\n" + ex.getMessage(), "Lỗi Kết Nối", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void doDisconnect() {
        new Thread(() -> {
            ftpClient.disconnect();
            SwingUtilities.invokeLater(() -> {
                lblStatus.setText("● Đã ngắt kết nối");
                lblStatus.setForeground(RedTheme.TEXT_MUTED);
                btnConnect.setEnabled(true);
                btnDisconnect.setEnabled(false);
                remoteModel.setRowCount(0);
                lblRemotePath.setText("/");
            });
        }).start();
    }

    public void refreshLocalFiles() {
        localModel.setRowCount(0);
        lblLocalPath.setText(localCurrentDir.toString());

        if (!Files.exists(localCurrentDir)) {
            try { Files.createDirectories(localCurrentDir); } catch (IOException ignored) {}
        }

        File folder = localCurrentDir.toFile();
        File[] files = folder.listFiles();
        if (files != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            for (File f : files) {
                String type = f.isDirectory() ? "[DIR]" : "[FILE]";
                String size = f.isDirectory() ? "<DIR>" : String.format("%.1f KB", f.length() / 1024.0);
                String date = sdf.format(new Date(f.lastModified()));
                localModel.addRow(new Object[]{type, f.getName(), size, date});
            }
        }
    }

    public void refreshRemoteFiles() {
        if (!ftpClient.isConnected()) {
            SwingUtilities.invokeLater(() -> {
                lblStatus.setText("● Chưa kết nối. Vui lòng bấm 'Kết Nối'!");
                lblStatus.setForeground(RedTheme.WARNING);
                btnConnect.setEnabled(true);
                btnDisconnect.setEnabled(false);
            });
            return;
        }

        new Thread(() -> {
            try {
                String curDir = ftpClient.pwd();
                List<FtpFileEntry> entries = ftpClient.listFiles("");
                SwingUtilities.invokeLater(() -> {
                    lblRemotePath.setText(curDir);
                    remoteModel.setRowCount(0);
                    for (FtpFileEntry fe : entries) {
                        String type = fe.isDirectory() ? "[DIR]" : "[FILE]";
                        remoteModel.addRow(new Object[]{type, fe.getName(), fe.getFormattedSize(), fe.getPermissions(), fe.getFormattedDate()});
                    }
                });
            } catch (Exception e) {
                appendLog("[CLIENT LỖI] Làm mới file thất bại: " + e.getMessage());
            }
        }).start();
    }

    private void doLocalUp() {
        Path parent = localCurrentDir.getParent();
        if (parent != null) {
            localCurrentDir = parent;
            refreshLocalFiles();
        }
    }

    private void doRemoteCwd(String folderName) {
        new Thread(() -> {
            try {
                if (ftpClient.cwd(folderName)) {
                    refreshRemoteFiles();
                }
            } catch (Exception e) {
                appendLog("[CLIENT LỖI] CWD thất bại: " + e.getMessage());
            }
        }).start();
    }

    private void doRemoteCdup() {
        new Thread(() -> {
            try {
                if (ftpClient.cdup()) {
                    refreshRemoteFiles();
                }
            } catch (Exception e) {
                appendLog("[CLIENT LỖI] CDUP thất bại: " + e.getMessage());
            }
        }).start();
    }

    private void doUploadSelected() {
        if (!ftpClient.isConnected()) {
            JOptionPane.showMessageDialog(this, "Chưa kết nối tới FTP Server! Vui lòng bấm 'Kết Nối' trước.", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File localFile = null;
        String name = null;

        int row = localTable.getSelectedRow();
        if (row != -1) {
            String type = (String) localModel.getValueAt(row, 0);
            name = (String) localModel.getValueAt(row, 1);
            if ("[DIR]".equals(type)) {
                // Nếu chọn thư mục thì mở JFileChooser để chọn file bất kỳ
                JFileChooser chooser = new JFileChooser(localCurrentDir.toFile());
                chooser.setDialogTitle("Chọn tập tin bất kỳ trên máy tính để tải lên Server");
                int ret = chooser.showOpenDialog(this);
                if (ret == JFileChooser.APPROVE_OPTION) {
                    localFile = chooser.getSelectedFile();
                    name = localFile.getName();
                } else {
                    return;
                }
            } else {
                localFile = localCurrentDir.resolve(name).toFile();
            }
        } else {
            // Nếu chưa chọn dòng nào, mở JFileChooser để người dùng chọn file bất kỳ từ máy tính (Desktop, Ổ D, Downloads...)
            JFileChooser chooser = new JFileChooser(localCurrentDir.toFile());
            chooser.setDialogTitle("Chọn tập tin bất kỳ trên máy tính để tải lên Server");
            int ret = chooser.showOpenDialog(this);
            if (ret == JFileChooser.APPROVE_OPTION) {
                localFile = chooser.getSelectedFile();
                name = localFile.getName();
            } else {
                return;
            }
        }

        final File fileToUpload = localFile;
        // Kiểm tra và tự động tạo tên bản sao nếu file đã tồn tại trên Server (ví dụ: file(1).txt)
        final String remoteFileName = getUniqueRemoteFileName(name);
        if (!remoteFileName.equals(name)) {
            appendLog("[CLIENT] Phát hiện tập tin trùng tên trên Server! Tự động lưu thành bản sao: " + remoteFileName);
        }

        progressBar.setValue(0);
        lblProgressInfo.setText("Đang tải lên: " + remoteFileName + "...");

        new Thread(() -> {
            try {
                boolean ok = ftpClient.uploadFile(fileToUpload, remoteFileName, (transferred, total, speed) -> {
                    SwingUtilities.invokeLater(() -> {
                        int percent = (total > 0) ? (int)((transferred * 100) / total) : 50;
                        progressBar.setValue(percent);
                        lblProgressInfo.setText(String.format("Tải lên: %s [%d%%] - %.1f KB/s", remoteFileName, percent, speed));
                    });
                });
                SwingUtilities.invokeLater(() -> {
                    if (ok) {
                        progressBar.setValue(100);
                        lblProgressInfo.setText("Đã tải lên thành công: " + remoteFileName);
                        refreshRemoteFiles();
                    } else {
                        lblProgressInfo.setText("Tải lên thất bại!");
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblProgressInfo.setText("Lỗi: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Lỗi khi Upload file: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void doDownloadSelected() {
        int row = remoteTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 tập tin ở bảng bên phải (Remote) để tải về!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String type = (String) remoteModel.getValueAt(row, 0);
        String name = (String) remoteModel.getValueAt(row, 1);
        if ("[DIR]".equals(type)) {
            JOptionPane.showMessageDialog(this, "Không hỗ trợ tải về cả thư mục, vui lòng chọn file!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Kiểm tra và tự động tạo tên bản sao nếu file đã tồn tại ở máy cục bộ (ví dụ: file(1).txt)
        File destFile = getUniqueLocalFile(localCurrentDir, name);
        final String finalLocalName = destFile.getName();
        if (!finalLocalName.equals(name)) {
            appendLog("[CLIENT] Phát hiện tập tin trùng tên ở máy cục bộ! Tự động lưu thành bản sao: " + finalLocalName);
        }

        progressBar.setValue(0);
        lblProgressInfo.setText("Đang tải xuống: " + finalLocalName + "...");

        new Thread(() -> {
            try {
                boolean ok = ftpClient.downloadFile(name, destFile, (transferred, total, speed) -> {
                    SwingUtilities.invokeLater(() -> {
                        lblProgressInfo.setText(String.format("Tải xuống: %s (%.1f KB) - %.1f KB/s", finalLocalName, transferred / 1024.0, speed));
                    });
                });
                SwingUtilities.invokeLater(() -> {
                    if (ok) {
                        progressBar.setValue(100);
                        lblProgressInfo.setText("Đã tải xuống thành công: " + finalLocalName);
                        refreshLocalFiles();
                    } else {
                        lblProgressInfo.setText("Tải xuống thất bại!");
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblProgressInfo.setText("Lỗi: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, "Lỗi khi Download: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private static final Pattern COPY_SUFFIX_PATTERN = Pattern.compile("^(.*?)\\((\\d+)\\)$");

    private String getUniqueRemoteFileName(String originalName) {
        if (!isRemoteFileExists(originalName)) {
            return originalName;
        }
        String baseName = originalName;
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = originalName.substring(0, dotIndex);
            extension = originalName.substring(dotIndex);
        }

        int counter = 1;
        Matcher m = COPY_SUFFIX_PATTERN.matcher(baseName);
        if (m.matches()) {
            baseName = m.group(1);
            counter = Integer.parseInt(m.group(2)) + 1;
        }

        while (true) {
            String candidate = baseName + "(" + counter + ")" + extension;
            if (!isRemoteFileExists(candidate)) {
                return candidate;
            }
            counter++;
        }
    }

    private boolean isRemoteFileExists(String filename) {
        if (filename == null) return false;
        for (int i = 0; i < remoteModel.getRowCount(); i++) {
            String name = (String) remoteModel.getValueAt(i, 1);
            if (filename.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private File getUniqueLocalFile(Path dir, String originalName) {
        File initial = dir.resolve(originalName).toFile();
        if (!initial.exists()) {
            return initial;
        }
        String baseName = originalName;
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = originalName.substring(0, dotIndex);
            extension = originalName.substring(dotIndex);
        }

        int counter = 1;
        Matcher m = COPY_SUFFIX_PATTERN.matcher(baseName);
        if (m.matches()) {
            baseName = m.group(1);
            counter = Integer.parseInt(m.group(2)) + 1;
        }

        while (true) {
            File candidate = dir.resolve(baseName + "(" + counter + ")" + extension).toFile();
            if (!candidate.exists()) {
                return candidate;
            }
            counter++;
        }
    }

    private void doNewFolder() {
        String name = JOptionPane.showInputDialog(this, "Nhập tên thư mục mới cần tạo trên Server:", "Tạo Thư Mục", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            new Thread(() -> {
                try {
                    if (ftpClient.makeDirectory(name.trim())) {
                        refreshRemoteFiles();
                    } else {
                        JOptionPane.showMessageDialog(this, "Không thể tạo thư mục!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }).start();
        }
    }

    private void doRename() {
        int row = remoteTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 mục trên Server cần đổi tên!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String oldName = (String) remoteModel.getValueAt(row, 1);
        String newName = JOptionPane.showInputDialog(this, "Nhập tên mới cho '" + oldName + "':", "Đổi Tên", JOptionPane.PLAIN_MESSAGE);
        if (newName != null && !newName.trim().isEmpty()) {
            new Thread(() -> {
                try {
                    if (ftpClient.rename(oldName, newName.trim())) {
                        refreshRemoteFiles();
                    } else {
                        JOptionPane.showMessageDialog(this, "Đổi tên thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }).start();
        }
    }

    private void doDelete() {
        int row = remoteTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 mục trên Server cần xóa!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String type = (String) remoteModel.getValueAt(row, 0);
        String name = (String) remoteModel.getValueAt(row, 1);

        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc chắn muốn xóa '" + name + "' trên Server không?", "Xác Nhận Xóa", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            new Thread(() -> {
                try {
                    boolean ok = "[DIR]".equals(type) ? ftpClient.removeDirectory(name) : ftpClient.deleteFile(name);
                    if (ok) {
                        refreshRemoteFiles();
                    } else {
                        JOptionPane.showMessageDialog(this, "Xóa thất bại! (Kiểm tra quyền hoặc thư mục không rỗng)", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Lỗi: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }).start();
        }
    }

    private void doLocalRename() {
        int row = localTable.getSelectedRow();
        if (row == -1) return;
        String oldName = (String) localModel.getValueAt(row, 1);
        String newName = JOptionPane.showInputDialog(this, "Nhập tên mới cho '" + oldName + "':", "Đổi Tên File Cục Bộ", JOptionPane.PLAIN_MESSAGE);
        if (newName != null && !newName.trim().isEmpty()) {
            File oldFile = localCurrentDir.resolve(oldName).toFile();
            File newFile = localCurrentDir.resolve(newName.trim()).toFile();
            if (oldFile.renameTo(newFile)) {
                refreshLocalFiles();
            } else {
                JOptionPane.showMessageDialog(this, "Không thể đổi tên file cục bộ!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void doLocalDelete() {
        int row = localTable.getSelectedRow();
        if (row == -1) return;
        String name = (String) localModel.getValueAt(row, 1);
        int confirm = JOptionPane.showConfirmDialog(this, "Bạn có chắc chắn muốn xóa file cục bộ '" + name + "' không?", "Xác Nhận Xóa", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            File target = localCurrentDir.resolve(name).toFile();
            try {
                if (target.isDirectory()) {
                    target.delete();
                } else {
                    Files.deleteIfExists(target.toPath());
                }
                refreshLocalFiles();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Không thể xóa: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void doNewFile() {
        int remoteRow = remoteTable.getSelectedRow();
        String[] options = {"Tạo trên Server (Máy Chủ)", "Tạo trên Local (Máy Cục Bộ)"};
        int choice = JOptionPane.showOptionDialog(this,
                "Bạn muốn tạo tập tin văn bản mới ở đâu?",
                "Tạo File Mới",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                (remoteRow != -1 || ftpClient.isConnected()) ? options[0] : options[1]);

        if (choice == JOptionPane.CLOSED_OPTION) return;

        boolean onServer = (choice == 0);
        if (onServer && !ftpClient.isConnected()) {
            JOptionPane.showMessageDialog(this, "Chưa kết nối tới FTP Server! Vui lòng bấm 'Kết Nối' trước.", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String filename = JOptionPane.showInputDialog(this, "Nhập tên file mới (ví dụ: ghichu.txt, data.txt):", "Tạo Tập Tin Mới", JOptionPane.PLAIN_MESSAGE);
        if (filename == null || filename.trim().isEmpty()) return;
        filename = filename.trim();
        if (!filename.contains(".")) {
            filename += ".txt";
        }

        if (onServer) {
            File tempFile = new File(System.getProperty("java.io.tmpdir"), "ftp_new_" + System.currentTimeMillis() + "_" + filename);
            try {
                Files.writeString(tempFile.toPath(), "", StandardCharsets.UTF_8);
                showTextEditorDialog(filename + " [Tập Tin Mới Trên Server]", filename, "", tempFile, true);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi tạo file tạm: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            File localFile = localCurrentDir.resolve(filename).toFile();
            try {
                if (!localFile.exists()) {
                    Files.writeString(localFile.toPath(), "", StandardCharsets.UTF_8);
                    refreshLocalFiles();
                }
                String content = Files.exists(localFile.toPath()) ? Files.readString(localFile.toPath(), StandardCharsets.UTF_8) : "";
                showTextEditorDialog(filename + " [Tập Tin Cục Bộ]", filename, content, localFile, false);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi tạo file cục bộ: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void doPreview() {
        int remoteRow = remoteTable.getSelectedRow();
        int localRow = localTable.getSelectedRow();

        if (remoteRow != -1) {
            String type = (String) remoteModel.getValueAt(remoteRow, 0);
            String name = (String) remoteModel.getValueAt(remoteRow, 1);
            if ("[DIR]".equals(type)) {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 tập tin (không chọn thư mục) để xem và sửa nội dung!", "Thông báo", JOptionPane.WARNING_MESSAGE);
                return;
            }
            doPreviewRemote(name);
        } else if (localRow != -1) {
            String type = (String) localModel.getValueAt(localRow, 0);
            String name = (String) localModel.getValueAt(localRow, 1);
            if ("[DIR]".equals(type)) {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 tập tin (không chọn thư mục) để xem và sửa nội dung!", "Thông báo", JOptionPane.WARNING_MESSAGE);
                return;
            }
            doPreviewLocal(name);
        } else {
            JOptionPane.showMessageDialog(this, "Vui lòng bấm chọn 1 file văn bản ở bảng bên trái hoặc bên phải rồi bấm 'Xem & Sửa'!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void doPreviewRemote(String name) {
        File tempFile = new File(System.getProperty("java.io.tmpdir"), "ftp_preview_" + name);
        new Thread(() -> {
            try {
                if (ftpClient.downloadFile(name, tempFile, null)) {
                    String content = Files.readString(tempFile.toPath(), StandardCharsets.UTF_8);
                    showTextEditorDialog(name + " [Máy Chủ Server]", name, content, tempFile, true);
                } else {
                    SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(this, "Không thể tải file từ Server để xem!", "Lỗi", JOptionPane.ERROR_MESSAGE)
                    );
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this, "Không thể đọc file từ Server: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE)
                );
            }
        }).start();
    }

    private void doPreviewLocal(String name) {
        File localFile = localCurrentDir.resolve(name).toFile();
        try {
            String content = Files.readString(localFile.toPath(), StandardCharsets.UTF_8);
            showTextEditorDialog(name + " [Máy Cục Bộ]", name, content, localFile, false);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không thể đọc file cục bộ: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showTextEditorDialog(String title, String filename, String content, File fileToOpen, boolean isRemote) {
        SwingUtilities.invokeLater(() -> {
            JDialog dlg = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Trình Xem & Chỉnh Sửa Tập Tin: " + title, true);
            dlg.setSize(750, 540);
            dlg.setLocationRelativeTo(this);
            dlg.setLayout(new BorderLayout(8, 8));

            // Header panel
            JPanel headerPanel = new JPanel(new BorderLayout(8, 4));
            headerPanel.setBorder(new EmptyBorder(8, 12, 4, 12));
            headerPanel.setOpaque(false);

            JLabel lblTitle = new JLabel("📄 " + filename + (isRemote ? "  (Đang mở từ FTP Server)" : "  (Đang mở từ máy cục bộ)"));
            lblTitle.setFont(RedTheme.FONT_TITLE);
            lblTitle.setForeground(RedTheme.PRIMARY);

            JLabel lblHint = new JLabel(isRemote ? "💡 Bạn có thể chỉnh sửa nội dung văn bản dưới đây, sau đó bấm 'Lưu & Tải Lên Server (STOR)' để cập nhật lại lên máy chủ."
                                                  : "💡 Bạn có thể chỉnh sửa nội dung và bấm 'Lưu File Cục Bộ'.");
            lblHint.setFont(RedTheme.FONT_BODY);
            lblHint.setForeground(RedTheme.TEXT_MUTED);

            headerPanel.add(lblTitle, BorderLayout.NORTH);
            headerPanel.add(lblHint, BorderLayout.SOUTH);
            dlg.add(headerPanel, BorderLayout.NORTH);

            // Editor text area
            JTextArea ta = new JTextArea(content != null ? content : "");
            ta.setEditable(true); // Cho phép chỉnh sửa thoải mái!
            ta.setFont(new Font("Consolas", Font.PLAIN, 14));
            ta.setForeground(Color.BLACK);
            ta.setBackground(new Color(255, 255, 255));
            ta.setCaretPosition(0);
            ta.setMargin(new Insets(8, 8, 8, 8));

            JScrollPane scrollPane = new JScrollPane(ta);
            scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)));
            dlg.add(scrollPane, BorderLayout.CENTER);

            // Bottom Bar
            JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
            bottomBar.setBorder(new EmptyBorder(4, 12, 8, 12));

            JButton btnSave;
            if (isRemote) {
                btnSave = RedTheme.createPrimaryButton("💾 Lưu & Tải Lên Server (STOR)");
                btnSave.addActionListener(e -> {
                    btnSave.setEnabled(false);
                    btnSave.setText("Đang lưu lên Server...");
                    new Thread(() -> {
                        try {
                            // Ghi vào file tạm
                            Files.writeString(fileToOpen.toPath(), ta.getText(), StandardCharsets.UTF_8);
                            // Gọi lệnh STOR tải lên Server
                            if (ftpClient.uploadFile(fileToOpen, filename, null)) {
                                SwingUtilities.invokeLater(() -> {
                                    JOptionPane.showMessageDialog(dlg, "Đã lưu và cập nhật tập tin '" + filename + "' lên Server thành công!\n(Dữ liệu được cập nhật bằng lệnh STOR)", "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                                    btnSave.setEnabled(true);
                                    btnSave.setText("💾 Lưu & Tải Lên Server (STOR)");
                                    refreshRemoteFiles();
                                });
                            } else {
                                SwingUtilities.invokeLater(() -> {
                                    JOptionPane.showMessageDialog(dlg, "Không thể lưu lên Server (Kiểm tra quyền ghi/upload của tài khoản)!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                                    btnSave.setEnabled(true);
                                    btnSave.setText("💾 Lưu & Tải Lên Server (STOR)");
                                });
                            }
                        } catch (Exception ex) {
                            SwingUtilities.invokeLater(() -> {
                                JOptionPane.showMessageDialog(dlg, "Lỗi khi lưu lên Server: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                                btnSave.setEnabled(true);
                                btnSave.setText("💾 Lưu & Tải Lên Server (STOR)");
                            });
                        }
                    }).start();
                });
            } else {
                btnSave = RedTheme.createPrimaryButton("💾 Lưu File Cục Bộ");
                btnSave.addActionListener(e -> {
                    try {
                        Files.writeString(fileToOpen.toPath(), ta.getText(), StandardCharsets.UTF_8);
                        JOptionPane.showMessageDialog(dlg, "Đã lưu nội dung vào file cục bộ thành công!", "Thành Công", JOptionPane.INFORMATION_MESSAGE);
                        refreshLocalFiles();
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(dlg, "Lỗi khi lưu file: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    }
                });
            }

            JButton btnNotepad = RedTheme.createSecondaryButton("Mở Bằng Notepad Ngoài");
            btnNotepad.addActionListener(e -> {
                try {
                    Files.writeString(fileToOpen.toPath(), ta.getText(), StandardCharsets.UTF_8);
                    if (Desktop.isDesktopSupported() && fileToOpen.exists()) {
                        Desktop.getDesktop().open(fileToOpen);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dlg, "Không thể mở bằng ứng dụng hệ thống: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            });

            JButton btnClose = RedTheme.createSecondaryButton("Đóng");
            btnClose.addActionListener(e -> dlg.dispose());

            bottomBar.add(btnSave);
            bottomBar.add(btnNotepad);
            bottomBar.add(btnClose);

            dlg.add(bottomBar, BorderLayout.SOUTH);
            dlg.setVisible(true);
        });
    }

    private void appendLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            try {
                Style style = logPane.addStyle("LogStyle", null);
                if (msg.startsWith(">>>")) {
                    StyleConstants.setForeground(style, new Color(30, 58, 138)); // Blue command
                    StyleConstants.setBold(style, true);
                } else if (msg.contains("220") || msg.contains("230") || msg.contains("226") || msg.contains("250") || msg.contains("200")) {
                    StyleConstants.setForeground(style, RedTheme.SUCCESS); // Green
                    StyleConstants.setBold(style, false);
                } else if (msg.contains("331") || msg.contains("150") || msg.contains("227")) {
                    StyleConstants.setForeground(style, RedTheme.WARNING); // Amber
                    StyleConstants.setBold(style, false);
                } else if (msg.contains("530") || msg.contains("550") || msg.contains("425") || msg.contains("LỖI")) {
                    StyleConstants.setForeground(style, RedTheme.ERROR); // Red error
                    StyleConstants.setBold(style, true);
                } else {
                    StyleConstants.setForeground(style, RedTheme.TEXT_PRIMARY);
                    StyleConstants.setBold(style, false);
                }

                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                logDoc.insertString(logDoc.getLength(), "[" + timestamp + "] " + msg + "\n", style);
                logPane.setCaretPosition(logDoc.getLength());
            } catch (Exception ignored) {}
        });
    }
}
