package com.zalolite.client.ui;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Trình phát video MP4 nhúng trực tiếp trong Swing (MessageBubble & hộp thoại phóng to) dùng OpenJFX Media.
 * Khởi tạo lười (lazy): chỉ tạo {@link JFXPanel} và {@link MediaPlayer} khi người dùng bấm phát.
 */
public class VideoPlayerPane extends JPanel {
    private static final Color VIEWPORT_BG = new Color(0x111827);
    private static final Color CONTROL_BG = new Color(0x1F2937);
    private static final Color TEXT_LIGHT = new Color(0xF9FAFB);
    private static final Color TEXT_SUB = new Color(0xD1D5DB);
    private static final int INLINE_WIDTH = 300;
    private static final int INLINE_HEIGHT = 215;
    private static final int INLINE_VIDEO_HEIGHT = 168;
    private static final int SLIDER_MAX = 1000;

    private static final String CARD_PREVIEW = "preview";
    private static final String CARD_VIDEO = "video";
    private static final String CARD_ERROR = "error";

    static {
        // Giữ JavaFX toolkit sống khi đóng hộp thoại video hoặc tháo JFXPanel
        try {
            Platform.setImplicitExit(false);
        } catch (Throwable ignored) {
        }
    }

    private final File videoFile;
    private final String fileName;
    private final Runnable onDownloadClick;
    private final Runnable onOpenExternal;

    private final CardLayout viewportCards = new CardLayout();
    private final JPanel viewport = new JPanel(viewportCards);
    private final JPanel fxHost = new JPanel(new BorderLayout());
    private final JButton playPauseBtn;
    private final JSlider seekSlider;
    private final JLabel timeLabel;
    private final JButton muteBtn;
    private final JButton fullBtn;

    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final AtomicBoolean syncingSlider = new AtomicBoolean(false);

    private JFXPanel fxPanel;
    private volatile MediaPlayer mediaPlayer;
    private volatile boolean initialized;
    private volatile boolean playing;
    private volatile boolean muted;
    private volatile boolean failed;
    private volatile double totalDurationMs;

    public VideoPlayerPane(File videoFile, String fileName, long fileSize,
                           Runnable onDownloadClick, Runnable onOpenExternal) {
        this(videoFile, fileName, fileSize, onDownloadClick, onOpenExternal, false);
    }

    private VideoPlayerPane(File videoFile, String fileName, long fileSize,
                            Runnable onDownloadClick, Runnable onOpenExternal, boolean dialogMode) {
        this.videoFile = videoFile;
        this.fileName = (fileName != null && !fileName.isBlank())
                ? fileName
                : (videoFile != null ? videoFile.getName() : "Video");
        this.onDownloadClick = onDownloadClick;
        this.onOpenExternal = onOpenExternal;

        setOpaque(false);
        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        if (!dialogMode) {
            Dimension pref = new Dimension(INLINE_WIDTH, INLINE_HEIGHT);
            setPreferredSize(pref);
            setMinimumSize(pref);
            setMaximumSize(pref);
        }

        viewport.setOpaque(true);
        viewport.setBackground(VIEWPORT_BG);
        if (!dialogMode) {
            viewport.setPreferredSize(new Dimension(INLINE_WIDTH - 6, INLINE_VIDEO_HEIGHT));
        }

        fxHost.setOpaque(true);
        fxHost.setBackground(VIEWPORT_BG);

        viewport.add(buildPreviewCard(fileSize), CARD_PREVIEW);
        viewport.add(fxHost, CARD_VIDEO);
        viewport.add(buildErrorCard(), CARD_ERROR);
        viewportCards.show(viewport, CARD_PREVIEW);
        add(viewport, BorderLayout.CENTER);

        playPauseBtn = createControlButton("▶", "Phát / Tạm dừng");
        playPauseBtn.addActionListener(e -> togglePlayPause());

        seekSlider = new JSlider(0, SLIDER_MAX, 0);
        seekSlider.setOpaque(false);
        seekSlider.setFocusable(false);
        seekSlider.setEnabled(false);
        seekSlider.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        seekSlider.addChangeListener(e -> onSliderChanged());

        timeLabel = new JLabel("00:00 / 00:00");
        timeLabel.setFont(Theme.uiFont(10.5f));
        timeLabel.setForeground(TEXT_SUB);
        timeLabel.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 3));

        muteBtn = createControlButton("🔊", "Tắt / Bật tiếng");
        muteBtn.addActionListener(e -> toggleMute());

        fullBtn = createControlButton("⛶", "Xem toàn màn hình");
        fullBtn.setVisible(!dialogMode);
        fullBtn.addActionListener(e -> openFullDialog());

        JButton downloadBtn = createControlButton("⬇", "Tải video về máy");
        downloadBtn.addActionListener(e -> {
            if (this.onDownloadClick != null) this.onDownloadClick.run();
        });

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 1, 0));
        rightControls.setOpaque(false);
        rightControls.add(timeLabel);
        rightControls.add(muteBtn);
        if (!dialogMode) {
            rightControls.add(fullBtn);
        }
        rightControls.add(downloadBtn);

        JPanel controlBar = new JPanel(new BorderLayout(3, 0));
        controlBar.setOpaque(true);
        controlBar.setBackground(CONTROL_BG);
        controlBar.setBorder(BorderFactory.createEmptyBorder(4, 5, 4, 5));
        controlBar.add(playPauseBtn, BorderLayout.WEST);
        controlBar.add(seekSlider, BorderLayout.CENTER);
        controlBar.add(rightControls, BorderLayout.EAST);

        add(controlBar, BorderLayout.SOUTH);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(VIEWPORT_BG);
        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
        g2.dispose();
        super.paintComponent(g);
    }

    private JPanel buildPreviewCard(long fileSize) {
        JPanel preview = new JPanel(new BorderLayout());
        preview.setBackground(VIEWPORT_BG);
        preview.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        String meta = this.fileName + (fileSize > 0 ? " · " + formatFileSize(fileSize) : "");
        JLabel title = new JLabel("🎬 " + meta);
        title.setFont(Theme.emojiFont(11f));
        title.setForeground(TEXT_SUB);
        title.setBorder(BorderFactory.createEmptyBorder(6, 8, 2, 8));
        preview.add(title, BorderLayout.NORTH);

        JButton centerPlay = new JButton("▶ Phát");
        centerPlay.setFont(Theme.emojiFont(Font.BOLD, 13f));
        centerPlay.setBackground(Theme.PRIMARY_RED);
        centerPlay.setForeground(Color.WHITE);
        centerPlay.setFocusPainted(false);
        centerPlay.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        centerPlay.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        centerPlay.addActionListener(e -> startPlayback());

        JPanel centerWrap = new JPanel(new GridBagLayout());
        centerWrap.setOpaque(false);
        centerWrap.add(centerPlay);
        preview.add(centerWrap, BorderLayout.CENTER);

        MouseAdapter clickPlay = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) startPlayback();
            }
        };
        preview.addMouseListener(clickPlay);
        centerWrap.addMouseListener(clickPlay);
        return preview;
    }

    private JPanel buildErrorCard() {
        JPanel err = new JPanel();
        err.setBackground(VIEWPORT_BG);
        err.setLayout(new BoxLayout(err, BoxLayout.Y_AXIS));
        err.setBorder(BorderFactory.createEmptyBorder(16, 12, 16, 12));

        JLabel msg = new JLabel("<html><div style='text-align:center'>Định dạng video chưa hỗ trợ xem trực tiếp</div></html>",
                SwingConstants.CENTER);
        msg.setFont(Theme.uiFont(Font.BOLD, 12f));
        msg.setForeground(TEXT_LIGHT);
        msg.setAlignmentX(CENTER_ALIGNMENT);

        JButton openBtn = Theme.primaryButton("Mở bằng trình phát máy");
        openBtn.setFont(Theme.uiFont(Font.BOLD, 11.5f));
        openBtn.setAlignmentX(CENTER_ALIGNMENT);
        openBtn.setVisible(onOpenExternal != null);
        openBtn.addActionListener(e -> {
            if (onOpenExternal != null) onOpenExternal.run();
        });

        err.add(Box.createVerticalGlue());
        err.add(msg);
        err.add(Box.createVerticalStrut(10));
        err.add(openBtn);
        err.add(Box.createVerticalGlue());
        return err;
    }

    private JButton createControlButton(String icon, String tooltip) {
        JButton b = new JButton(icon);
        b.setFont(Theme.emojiFont(12.5f));
        b.setForeground(TEXT_LIGHT);
        b.setToolTipText(tooltip);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setMargin(new Insets(1, 3, 1, 3));
        b.setPreferredSize(new Dimension(26, 26));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void startPlayback() {
        if (disposed.get() || failed) return;
        if (videoFile == null || !videoFile.isFile()) {
            showErrorState();
            return;
        }
        if (initialized) {
            resumeOrPause(true);
            return;
        }
        initialized = true;
        playPauseBtn.setText("⏸");
        if (fxPanel == null) {
            // Khởi tạo JFXPanel trên Swing EDT sẽ kích hoạt JavaFX toolkit an toàn
            fxPanel = new JFXPanel();
            fxPanel.setBackground(VIEWPORT_BG);
            fxPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            fxHost.add(fxPanel, BorderLayout.CENTER);
            fxHost.revalidate();
        }
        viewportCards.show(viewport, CARD_VIDEO);

        final JFXPanel targetPanel = fxPanel;
        final String mediaUri = videoFile.toURI().toString();
        Platform.runLater(() -> {
            if (disposed.get()) return;
            try {
                Platform.setImplicitExit(false);
                Media media = new Media(mediaUri);
                media.setOnError(() -> SwingUtilities.invokeLater(this::showErrorState));

                MediaPlayer mp = new MediaPlayer(media);
                mp.setMute(muted);
                mp.setOnError(() -> SwingUtilities.invokeLater(this::showErrorState));
                mp.totalDurationProperty().addListener((obs, oldDur, newDur) -> updateTotalDuration(newDur));
                mp.setOnReady(() -> {
                    updateTotalDuration(mp.getTotalDuration());
                    if (!disposed.get()) {
                        mp.play();
                    }
                });
                mp.setOnPlaying(() -> {
                    playing = true;
                    SwingUtilities.invokeLater(() -> playPauseBtn.setText("⏸"));
                });
                mp.setOnPaused(() -> {
                    playing = false;
                    SwingUtilities.invokeLater(() -> playPauseBtn.setText("▶"));
                });
                mp.setOnEndOfMedia(() -> {
                    playing = false;
                    mp.pause();
                    mp.seek(Duration.ZERO);
                    SwingUtilities.invokeLater(() -> {
                        playPauseBtn.setText("▶");
                        syncingSlider.set(true);
                        try {
                            seekSlider.setValue(0);
                        } finally {
                            syncingSlider.set(false);
                        }
                        timeLabel.setText("00:00 / " + formatTime(totalDurationMs));
                    });
                });
                mp.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                    if (newTime == null || totalDurationMs <= 0 || disposed.get()) return;
                    double curMs = newTime.toMillis();
                    int pos = (int) Math.max(0, Math.min(SLIDER_MAX, Math.round((curMs / totalDurationMs) * SLIDER_MAX)));
                    SwingUtilities.invokeLater(() -> {
                        if (disposed.get() || seekSlider.getValueIsAdjusting()) return;
                        syncingSlider.set(true);
                        try {
                            seekSlider.setValue(pos);
                        } finally {
                            syncingSlider.set(false);
                        }
                        timeLabel.setText(formatTime(curMs) + " / " + formatTime(totalDurationMs));
                    });
                });

                MediaView view = new MediaView(mp);
                view.setPreserveRatio(true);
                StackPane root = new StackPane(view);
                root.setStyle("-fx-background-color: #111827;");
                view.fitWidthProperty().bind(root.widthProperty());
                view.fitHeightProperty().bind(root.heightProperty());
                root.setOnMouseClicked(ev -> SwingUtilities.invokeLater(this::togglePlayPause));

                Scene scene = new Scene(root, javafx.scene.paint.Color.web("#111827"));
                targetPanel.setScene(scene);

                if (disposed.get()) {
                    safeDisposePlayer(mp);
                    return;
                }
                mediaPlayer = mp;
            } catch (Throwable ex) {
                SwingUtilities.invokeLater(this::showErrorState);
            }
        });
    }

    private void updateTotalDuration(Duration total) {
        if (total == null || total.isUnknown() || total.isIndefinite() || disposed.get()) return;
        double totalMs = total.toMillis();
        if (totalMs <= 0) return;
        totalDurationMs = totalMs;
        SwingUtilities.invokeLater(() -> {
            if (disposed.get() || failed) return;
            seekSlider.setEnabled(true);
            double curMs = (seekSlider.getValue() / (double) SLIDER_MAX) * totalMs;
            timeLabel.setText(formatTime(curMs) + " / " + formatTime(totalMs));
        });
    }

    private void togglePlayPause() {
        if (disposed.get() || failed) return;
        if (!initialized) {
            startPlayback();
            return;
        }
        resumeOrPause(!playing);
    }

    private void resumeOrPause(boolean shouldPlay) {
        MediaPlayer mp = mediaPlayer;
        if (mp == null || disposed.get()) return;
        Platform.runLater(() -> {
            if (disposed.get()) return;
            try {
                if (shouldPlay) {
                    mp.play();
                } else {
                    mp.pause();
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private void toggleMute() {
        muted = !muted;
        muteBtn.setText(muted ? "🔇" : "🔊");
        MediaPlayer mp = mediaPlayer;
        if (mp != null && !disposed.get()) {
            boolean targetMute = muted;
            Platform.runLater(() -> {
                try {
                    mp.setMute(targetMute);
                } catch (Throwable ignored) {
                }
            });
        }
    }

    private void onSliderChanged() {
        if (syncingSlider.get() || totalDurationMs <= 0 || disposed.get()) return;
        double targetMs = (seekSlider.getValue() / (double) SLIDER_MAX) * totalDurationMs;
        timeLabel.setText(formatTime(targetMs) + " / " + formatTime(totalDurationMs));
        if (!seekSlider.getValueIsAdjusting()) {
            MediaPlayer mp = mediaPlayer;
            if (mp != null) {
                Platform.runLater(() -> {
                    try {
                        mp.seek(Duration.millis(targetMs));
                    } catch (Throwable ignored) {
                    }
                });
            }
        }
    }

    private void openFullDialog() {
        resumeOrPause(false);
        showFullPlayerDialog(this, videoFile, fileName, onDownloadClick, onOpenExternal);
    }

    private void showErrorState() {
        if (disposed.get()) return;
        failed = true;
        playing = false;
        playPauseBtn.setText("▶");
        playPauseBtn.setEnabled(false);
        seekSlider.setEnabled(false);
        MediaPlayer mp = mediaPlayer;
        mediaPlayer = null;
        if (mp != null) {
            Platform.runLater(() -> safeDisposePlayer(mp));
        }
        viewportCards.show(viewport, CARD_ERROR);
    }

    /** Giải phóng toàn bộ tài nguyên MediaPlayer (idempotent, an toàn gọi nhiều lần). */
    public void dispose() {
        if (!disposed.compareAndSet(false, true)) return;
        playing = false;
        MediaPlayer mp = mediaPlayer;
        mediaPlayer = null;
        if (mp != null) {
            Platform.runLater(() -> safeDisposePlayer(mp));
        }
    }

    private static void safeDisposePlayer(MediaPlayer mp) {
        try {
            mp.stop();
        } catch (Throwable ignored) {
        }
        try {
            mp.dispose();
        } catch (Throwable ignored) {
        }
    }

    /** Mở hộp thoại phát video kích thước lớn (800x500), tự phát và giải phóng MediaPlayer khi đóng / ESC. */
    public static void showFullPlayerDialog(Component parent, File videoFile, String title,
                                            Runnable onDownloadClick, Runnable onOpenExternal) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        String dialogTitle = (title != null && !title.isBlank())
                ? title
                : (videoFile != null ? videoFile.getName() : "Trình phát video");
        JDialog dialog = new JDialog(owner, dialogTitle, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setSize(800, 500);
        dialog.setMinimumSize(new Dimension(480, 320));
        dialog.setLocationRelativeTo(parent);
        dialog.getContentPane().setBackground(VIEWPORT_BG);
        dialog.setLayout(new BorderLayout());

        long size = (videoFile != null && videoFile.isFile()) ? videoFile.length() : 0L;
        VideoPlayerPane fullPane = new VideoPlayerPane(videoFile, dialogTitle, size,
                onDownloadClick, onOpenExternal, true);
        dialog.add(fullPane, BorderLayout.CENTER);

        dialog.getRootPane().registerKeyboardAction(
                e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                fullPane.startPlayback();
            }

            @Override
            public void windowClosing(WindowEvent e) {
                fullPane.dispose();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                fullPane.dispose();
            }
        });

        dialog.setVisible(true);
    }

    private static String formatTime(double millis) {
        long totalSec = Math.max(0L, (long) (millis / 1000.0));
        long mins = totalSec / 60;
        long secs = totalSec % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
