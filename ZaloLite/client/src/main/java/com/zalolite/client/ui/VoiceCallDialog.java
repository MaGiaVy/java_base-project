package com.zalolite.client.ui;

import com.zalolite.client.VoiceCallSession;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hộp thoại cuộc gọi thoại 1-1 không chặn cửa sổ chính (modeless), hỗ trợ 3 trạng thái:
 * cuộc gọi đi (outgoing), cuộc gọi đến (incoming), và đang đàm thoại (active với đồng hồ đếm giờ + nút tắt/bật mic).
 * Điều khiển phiên âm thanh UDP {@link VoiceCallSession} và gọi callback về {@link MainFrame} để gửi tín hiệu CALL_ACCEPT / CALL_END.
 */
public final class VoiceCallDialog extends JDialog {
    private final JLabel statusLabel;
    private final JLabel timerLabel;
    private final JButton acceptBtn;
    private final JButton muteBtn;
    private final JButton hangupBtn;
    private final AtomicBoolean endCallbackFired = new AtomicBoolean(false);

    private Runnable onEndCallback;
    private VoiceCallSession session;
    private Timer callTimer;
    private int elapsedSeconds;

    private VoiceCallDialog(Window owner, String peerName, long peerId, boolean incoming,
                            Runnable onAccept, Runnable onEnd) {
        super(owner, "Cuộc gọi thoại", ModalityType.MODELESS);
        this.onEndCallback = onEnd;

        setSize(340, 360);
        setResizable(false);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.CHAT_BG);

        // Header banner
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.RAIL_BG);
        header.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JLabel title = new JLabel(incoming ? "📞 Cuộc gọi đến" : "📞 Cuộc gọi thoại", SwingConstants.CENTER);
        title.setFont(Theme.emojiFont(Font.BOLD, 14f));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.CENTER);
        root.add(header, BorderLayout.NORTH);

        // Center peer info
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(24, 20, 16, 20));

        Theme.AvatarBadge avatar = new Theme.AvatarBadge(
                peerName, 84, Theme.avatarColor(peerId, false), true, true);
        avatar.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel nameLabel = new JLabel(peerName == null || peerName.isBlank() ? "Người dùng" : peerName);
        nameLabel.setFont(Theme.uiFont(Font.BOLD, 18f));
        nameLabel.setForeground(Theme.TEXT_DARK);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        statusLabel = new JLabel(incoming ? "Đang gọi đến..." : "Đang đổ chuông...");
        statusLabel.setFont(Theme.uiFont(Font.PLAIN, 13f));
        statusLabel.setForeground(Theme.TEXT_MUTED);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        timerLabel = new JLabel(" ");
        timerLabel.setFont(Theme.uiFont(Font.BOLD, 16f));
        timerLabel.setForeground(Theme.ONLINE_GREEN);
        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        center.add(avatar);
        center.add(Box.createVerticalStrut(14));
        center.add(nameLabel);
        center.add(Box.createVerticalStrut(6));
        center.add(statusLabel);
        center.add(Box.createVerticalStrut(10));
        center.add(timerLabel);
        root.add(center, BorderLayout.CENTER);

        // Bottom controls
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 16));
        actions.setOpaque(false);

        // [UI -> GỬI] Bấm "Nghe máy": gọi callback để MainFrame mở VoiceCallSession UDP và gửi CALL_ACCEPT qua TLS
        acceptBtn = Theme.primaryButton("📞 Nghe máy");
        acceptBtn.setFont(Theme.emojiFont(Font.BOLD, 13f));
        acceptBtn.setBackground(Theme.ONLINE_GREEN);
        acceptBtn.setVisible(incoming);
        acceptBtn.addActionListener(e -> {
            acceptBtn.setEnabled(false);
            statusLabel.setText("Đang kết nối...");
            if (onAccept != null) onAccept.run();
        });

        // Nút bật/tắt micro cục bộ trong phiên VoiceCallSession
        muteBtn = Theme.softButton("🎤 Tắt mic");
        muteBtn.setFont(Theme.emojiFont(Font.BOLD, 12.5f));
        muteBtn.setVisible(false);
        muteBtn.addActionListener(e -> toggleMute());

        // [UI -> GỬI] Bấm "Từ chối" / "Cúp máy": đóng phiên UDP và gọi callback gửi CALL_END qua TLS
        hangupBtn = Theme.primaryButton(incoming ? "📞 Từ chối" : "📞 Cúp máy");
        hangupBtn.setFont(Theme.emojiFont(Font.BOLD, 13f));
        hangupBtn.setBackground(Theme.DANGER_RED);
        hangupBtn.addActionListener(e -> handleLocalHangup());

        actions.add(acceptBtn);
        actions.add(muteBtn);
        actions.add(hangupBtn);
        root.add(actions, BorderLayout.SOUTH);

        setContentPane(root);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleLocalHangup();
            }
        });
    }

    public static VoiceCallDialog outgoing(Window owner, String peerName, long peerId, Runnable onHangup) {
        return new VoiceCallDialog(owner, peerName, peerId, false, null, onHangup);
    }

    public static VoiceCallDialog incoming(Window owner, String callerName, long callerId,
                                           Runnable onAccept, Runnable onReject) {
        return new VoiceCallDialog(owner, callerName, callerId, true, onAccept, onReject);
    }

    public void setConnected(VoiceCallSession session, Runnable onHangup) {
        if (onHangup != null) this.onEndCallback = onHangup;
        setConnected(session);
    }

    /** [PUSH -> UI] Chuyển giao diện sang trạng thái đang đàm thoại (khi nhận CALL_ACCEPT hoặc vừa chấp nhận) và chạy đồng hồ. */
    public void setConnected(VoiceCallSession session) {
        runOnEdt(() -> {
            this.session = session;
            acceptBtn.setVisible(false);
            muteBtn.setVisible(true);
            muteBtn.setEnabled(session != null);
            hangupBtn.setText("📞 Cúp máy");
            hangupBtn.setBackground(Theme.DANGER_RED);
            statusLabel.setText("Đang trong cuộc gọi");
            statusLabel.setForeground(Theme.ONLINE_GREEN);

            elapsedSeconds = 0;
            timerLabel.setText("00:00");
            if (callTimer != null) callTimer.stop();
            callTimer = new Timer(1000, e -> {
                elapsedSeconds++;
                timerLabel.setText(String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60));
            });
            callTimer.start();
            revalidate();
            repaint();
        });
    }

    /** [PUSH -> UI] Kết thúc cuộc gọi từ phía đối phương/server (CALL_END), đóng phiên UDP và tự ẩn hộp thoại. */
    public void endCall(String statusText) {
        runOnEdt(() -> {
            endCallbackFired.set(true);
            stopTimerAndSession();
            acceptBtn.setEnabled(false);
            muteBtn.setEnabled(false);
            hangupBtn.setEnabled(false);
            if (statusText != null && !statusText.isBlank()) {
                statusLabel.setText(statusText);
                statusLabel.setForeground(Theme.DANGER_RED);
                Timer closeDelay = new Timer(900, e -> dispose());
                closeDelay.setRepeats(false);
                closeDelay.start();
            } else {
                dispose();
            }
        });
    }

    private void toggleMute() {
        if (session == null) return;
        boolean next = !session.isMuted();
        session.setMuted(next);
        muteBtn.setText(next ? "🔇 Bật mic" : "🎤 Tắt mic");
        muteBtn.setBackground(next ? Theme.ITEM_SELECTED : Theme.BUBBLE_OTHER);
    }

    private void handleLocalHangup() {
        stopTimerAndSession();
        if (endCallbackFired.compareAndSet(false, true) && onEndCallback != null) {
            onEndCallback.run();
        }
        dispose();
    }

    private void stopTimerAndSession() {
        if (callTimer != null) {
            callTimer.stop();
            callTimer = null;
        }
        if (session != null) {
            session.close();
            session = null;
        }
    }

    private static void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }
}
