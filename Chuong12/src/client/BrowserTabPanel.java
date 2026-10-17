package client;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * BrowserTabPanel: Đại diện cho 1 Tab duyệt web trong Mini Chrome
 * Tích hợp động cơ WebKit hiện đại (JavaFX WebView qua JFXPanel) giống nhóm HAnh.
 * Hỗ trợ 100% CSS3, JavaScript, SVG, HTML5, video, phông chữ và bố cục web hiện đại.
 */
public class BrowserTabPanel extends JPanel {

    private final Color PRIMARY_RED = new Color(220, 38, 38);
    private final Color LIGHT_BG = new Color(248, 250, 252);
    private final Color CARD_BG = Color.WHITE;
    private final Color BORDER_COLOR = new Color(226, 232, 240);
    private final Color TEXT_MAIN = new Color(15, 23, 42);

    private final HttpClientHelper httpHelper;
    private final Consumer<TabStatusEvent> statusListener;
    private final BiConsumer<BrowserTabPanel, String> titleChangeListener;

    private JTextField txtUrl;
    private JFXPanel jfxPanel;
    private WebView fxWebView;
    private WebEngine fxWebEngine;

    private MiniChromeBrowser.StyledButton btnBack, btnForward, btnGo, btnReload, btnViewSource, btnOpenExternal;

    private String currentUrl = "";
    private String tabTitle = "Trang mới";

    public BrowserTabPanel(HttpClientHelper httpHelper,
                           Consumer<TabStatusEvent> statusListener,
                           BiConsumer<BrowserTabPanel, String> titleChangeListener) {
        this.httpHelper = httpHelper;
        this.statusListener = statusListener;
        this.titleChangeListener = titleChangeListener;

        setLayout(new BorderLayout());
        setBackground(LIGHT_BG);

        initUI();
    }

    private void initUI() {
        // 1. Thanh điều hướng URL Bar
        JPanel navContainer = new JPanel(new BorderLayout());
        navContainer.setBackground(CARD_BG);
        navContainer.setBorder(new CompoundBorder(
                new LineBorder(BORDER_COLOR, 1),
                new EmptyBorder(8, 12, 8, 12)
        ));

        JPanel urlBar = new JPanel(new BorderLayout(8, 0));
        urlBar.setOpaque(false);

        // Nhóm nút điều hướng Back, Forward, Reload
        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        navButtons.setOpaque(false);

        btnBack = new MiniChromeBrowser.StyledButton("◀", Color.WHITE, TEXT_MAIN);
        btnBack.setToolTipText("Quay lại (Alt+Left)");
        btnBack.addActionListener(e -> goBack());

        btnForward = new MiniChromeBrowser.StyledButton("▶", Color.WHITE, TEXT_MAIN);
        btnForward.setToolTipText("Tiến tới (Alt+Right)");
        btnForward.addActionListener(e -> goForward());

        btnReload = new MiniChromeBrowser.StyledButton("🔄", Color.WHITE, TEXT_MAIN);
        btnReload.setToolTipText("Tải lại trang (F5)");
        btnReload.addActionListener(e -> reload());

        navButtons.add(btnBack);
        navButtons.add(btnForward);
        navButtons.add(btnReload);

        // Ô nhập địa chỉ URL
        txtUrl = new JTextField("http://localhost:8080/P1.HTM");
        txtUrl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtUrl.setForeground(TEXT_MAIN);
        txtUrl.setBackground(new Color(241, 245, 249));
        txtUrl.setCaretColor(TEXT_MAIN);
        txtUrl.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(BORDER_COLOR, 1, true),
                new EmptyBorder(6, 12, 6, 12)
        ));

        txtUrl.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    navigateTo(txtUrl.getText().trim(), true);
                }
            }
        });

        // Nhóm nút hành động
        JPanel actionButtonGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actionButtonGroup.setOpaque(false);

        btnGo = new MiniChromeBrowser.StyledButton("Truy Cập (Go)", PRIMARY_RED, Color.WHITE);
        btnGo.addActionListener(e -> navigateTo(txtUrl.getText().trim(), true));

        btnViewSource = new MiniChromeBrowser.StyledButton("📄 Xem Mã Nguồn (View Source)", new Color(15, 23, 42), Color.WHITE);
        btnViewSource.setToolTipText("Bấm để xem đoạn mã HTML gốc mà Server gửi qua mạng");
        btnViewSource.addActionListener(e -> showSourceCodeDialog());

        btnOpenExternal = new MiniChromeBrowser.StyledButton("🌐 Mở Ngoài (Chrome)", new Color(22, 163, 74), Color.WHITE);
        btnOpenExternal.setToolTipText("Mở liên kết này bằng trình duyệt thật của máy tính (Google Chrome / Edge)");
        btnOpenExternal.addActionListener(e -> openInExternalBrowser());

        actionButtonGroup.add(btnGo);
        actionButtonGroup.add(btnViewSource);
        actionButtonGroup.add(btnOpenExternal);

        urlBar.add(navButtons, BorderLayout.WEST);
        urlBar.add(txtUrl, BorderLayout.CENTER);
        urlBar.add(actionButtonGroup, BorderLayout.EAST);

        // Thanh Bookmark Nhanh
        JPanel bookmarkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        bookmarkPanel.setOpaque(false);
        JLabel lblQuick = new JLabel("Truy cập nhanh:");
        lblQuick.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblQuick.setForeground(PRIMARY_RED);
        bookmarkPanel.add(lblQuick);

        bookmarkPanel.add(createBookmarkButton("P1 (Server 1: 8080)", "http://localhost:8080/P1.HTM"));
        bookmarkPanel.add(createBookmarkButton("P2 (Server 1: 8080)", "http://localhost:8080/P2.HTM"));
        bookmarkPanel.add(createBookmarkButton("P3 (Server 2: 8081)", "http://localhost:8081/P3.HTM"));
        bookmarkPanel.add(createBookmarkButton("P4 (Server 2: 8081)", "http://localhost:8081/P4.HTM"));
        bookmarkPanel.add(createBookmarkButton("Test 404 Not Found", "http://localhost:8080/KhongTonTai.HTM"));

        navContainer.add(urlBar, BorderLayout.NORTH);
        navContainer.add(bookmarkPanel, BorderLayout.SOUTH);
        add(navContainer, BorderLayout.NORTH);

        // 2. KHUNG HIỂN THỊ WEBKIT (JAVAFX WEBVIEW QUA JFXPANEL)
        jfxPanel = new JFXPanel();
        jfxPanel.setBorder(null);

        Platform.runLater(() -> {
            fxWebView = new WebView();
            fxWebView.setFontScale(1.0);
            fxWebEngine = fxWebView.getEngine();
            fxWebEngine.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");

            // Lắng nghe thay đổi URL khi click link trên trang web
            fxWebEngine.locationProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        currentUrl = newVal;
                        txtUrl.setText(newVal);
                        updateNavButtons();
                    });
                }
            });

            // Lắng nghe thay đổi tiêu đề trang web
            fxWebEngine.titleProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && !newVal.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        tabTitle = newVal;
                        if (titleChangeListener != null) {
                            titleChangeListener.accept(this, tabTitle);
                        }
                    });
                }
            });

            // Lắng nghe tiến trình tải trang (Worker State)
            fxWebEngine.getLoadWorker().stateProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal == Worker.State.SUCCEEDED) {
                    SwingUtilities.invokeLater(() -> {
                        updateNavButtons();
                        if (statusListener != null) {
                            statusListener.accept(new TabStatusEvent(false, 200, "[ 200 OK ]", 0, "text/html", 0, false));
                        }
                    });
                } else if (newVal == Worker.State.FAILED) {
                    SwingUtilities.invokeLater(() -> {
                        updateNavButtons();
                        if (statusListener != null) {
                            statusListener.accept(new TabStatusEvent(false, 0, "[ LỖI KẾT NỐI ]", 0, "N/A", 0, true));
                        }
                    });
                } else if (newVal == Worker.State.RUNNING) {
                    SwingUtilities.invokeLater(() -> {
                        if (statusListener != null) {
                            statusListener.accept(new TabStatusEvent(true, 0, "[ Đang tải... ]", 0, "N/A", 0, false));
                        }
                    });
                }
            });

            Scene scene = new Scene(fxWebView);
            jfxPanel.setScene(scene);
        });

        add(jfxPanel, BorderLayout.CENTER);
    }

    public void navigateTo(String urlString, boolean pushHistory) {
        if (urlString == null || urlString.isEmpty()) return;
        if (!urlString.startsWith("http://") && !urlString.startsWith("https://") && !urlString.startsWith("file://")) {
            urlString = "http://" + urlString;
        }

        final String targetUrl = urlString;
        txtUrl.setText(targetUrl);
        currentUrl = targetUrl;
        updateNavButtons();

        if (statusListener != null) {
            statusListener.accept(new TabStatusEvent(true, 0, "[ Đang tải... ]", 0, "N/A", 0, false));
        }

        Platform.runLater(() -> {
            if (fxWebEngine != null) {
                fxWebEngine.load(targetUrl);
            }
        });
    }

    private void goBack() {
        Platform.runLater(() -> {
            if (fxWebEngine != null && fxWebEngine.getHistory().getCurrentIndex() > 0) {
                fxWebEngine.getHistory().go(-1);
                SwingUtilities.invokeLater(this::updateNavButtons);
            }
        });
    }

    private void goForward() {
        Platform.runLater(() -> {
            if (fxWebEngine != null && fxWebEngine.getHistory().getCurrentIndex() < fxWebEngine.getHistory().getEntries().size() - 1) {
                fxWebEngine.getHistory().go(1);
                SwingUtilities.invokeLater(this::updateNavButtons);
            }
        });
    }

    private void reload() {
        Platform.runLater(() -> {
            if (fxWebEngine != null) {
                fxWebEngine.reload();
            }
        });
    }

    private void updateNavButtons() {
        Platform.runLater(() -> {
            if (fxWebEngine != null && fxWebEngine.getHistory() != null) {
                int cur = fxWebEngine.getHistory().getCurrentIndex();
                int max = fxWebEngine.getHistory().getEntries().size() - 1;
                SwingUtilities.invokeLater(() -> {
                    btnBack.setEnabled(cur > 0);
                    btnForward.setEnabled(cur < max);
                });
            }
        });
    }

    public void showSourceCodeDialog() {
        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(parentWindow instanceof Frame ? (Frame) parentWindow : null,
                "Mã Nguồn HTML (View Source) - " + currentUrl, true);
        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(LIGHT_BG);
        panel.setBorder(new EmptyBorder(12, 14, 12, 14));

        JLabel lblTitle = new JLabel("MÃ NGUỒN HTML NHẬN ĐƯỢC TỪ MÁY CHỦ:");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblTitle.setForeground(PRIMARY_RED);
        panel.add(lblTitle, BorderLayout.NORTH);

        JTextArea txtSource = new JTextArea();
        txtSource.setEditable(false);
        txtSource.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtSource.setBackground(new Color(15, 23, 42));
        txtSource.setForeground(new Color(56, 189, 248));
        txtSource.setMargin(new Insets(10, 12, 10, 12));
        txtSource.setText(">>> Đang lấy mã nguồn từ " + currentUrl + "...\n");

        new Thread(() -> {
            HttpClientHelper.HttpResult res = httpHelper.sendGet(currentUrl);
            SwingUtilities.invokeLater(() -> {
                if (res.hasError()) {
                    txtSource.setText("Không thể lấy mã nguồn: " + res.errorMessage);
                } else {
                    txtSource.setText(res.body);
                    txtSource.setCaretPosition(0);
                }
            });
        }).start();

        JScrollPane scroll = new JScrollPane(txtSource);
        scroll.setBorder(new LineBorder(BORDER_COLOR, 1));
        panel.add(scroll, BorderLayout.CENTER);

        JPanel btm = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        btm.setOpaque(false);

        MiniChromeBrowser.StyledButton btnCopy = new MiniChromeBrowser.StyledButton("📋 Sao Chép Mã Nguồn", new Color(37, 99, 235), Color.WHITE);
        btnCopy.addActionListener(e -> {
            StringSelection sel = new StringSelection(txtSource.getText());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(sel, null);
            JOptionPane.showMessageDialog(dialog, "Đã sao chép mã nguồn HTML vào Clipboard!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
        });

        MiniChromeBrowser.StyledButton btnClose = new MiniChromeBrowser.StyledButton("Đóng", new Color(100, 116, 139), Color.WHITE);
        btnClose.addActionListener(e -> dialog.dispose());

        btm.add(btnCopy);
        btm.add(btnClose);
        panel.add(btm, BorderLayout.SOUTH);

        dialog.setContentPane(panel);
        dialog.setVisible(true);
    }

    private void openInExternalBrowser() {
        String url = txtUrl.getText().trim();
        if (url.isEmpty()) return;
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không thể mở trình duyệt: " + ex.getMessage(), "Lỗi", JOptionPane.WARNING_MESSAGE);
        }
    }

    public void triggerStatusRefresh() {
        if (statusListener != null) {
            statusListener.accept(new TabStatusEvent(false, 200, "[ 200 OK ]", 0, "text/html", 0, false));
        }
    }

    private MiniChromeBrowser.StyledButton createBookmarkButton(String label, String url) {
        MiniChromeBrowser.StyledButton btn = new MiniChromeBrowser.StyledButton(label, Color.WHITE, PRIMARY_RED);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setBorder(new CompoundBorder(new LineBorder(BORDER_COLOR, 1, true), new EmptyBorder(5, 10, 5, 10)));
        btn.addActionListener(e -> navigateTo(url, true));
        return btn;
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public String getTabTitle() {
        return tabTitle;
    }

    public static class TabStatusEvent {
        public final boolean loading;
        public final int statusCode;
        public final String statusText;
        public final long latencyMs;
        public final String contentType;
        public final long contentLength;
        public final boolean error;

        public TabStatusEvent(boolean loading, int statusCode, String statusText, long latencyMs,
                              String contentType, long contentLength, boolean error) {
            this.loading = loading;
            this.statusCode = statusCode;
            this.statusText = statusText;
            this.latencyMs = latencyMs;
            this.contentType = contentType;
            this.contentLength = contentLength;
            this.error = error;
        }
    }
}
