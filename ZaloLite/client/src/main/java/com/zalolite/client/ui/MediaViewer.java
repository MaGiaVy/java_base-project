package com.zalolite.client.ui;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Helpers for image thumbnails, full-screen/modal image preview, and opening media with OS apps.
 */
public final class MediaViewer {
    private MediaViewer() {
    }

    public static ImageIcon loadThumbnail(File imageFile, int maxW, int maxH) {
        if (imageFile == null || !imageFile.isFile() || maxW <= 0 || maxH <= 0) {
            return null;
        }
        try {
            BufferedImage img = ImageIO.read(imageFile);
            if (img == null) return null;
            int w = img.getWidth();
            int h = img.getHeight();
            double scale = Math.min(1.0, Math.min((double) maxW / w, (double) maxH / h));
            int targetW = Math.max(1, (int) Math.round(w * scale));
            int targetH = Math.max(1, (int) Math.round(h * scale));
            if (targetW == w && targetH == h) {
                return new ImageIcon(img);
            }
            return new ImageIcon(img.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH));
        } catch (IOException e) {
            return null;
        }
    }

    public static void showImageDialog(Component parent, File imageFile, String title) {
        if (imageFile == null || !imageFile.isFile()) {
            JOptionPane.showMessageDialog(parent, "Không tìm thấy tệp hình ảnh.", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int maxW = (int) (screen.width * 0.85);
        int maxH = (int) (screen.height * 0.85);
        // Đọc & thu phóng ảnh ở luồng nền để không làm đơ giao diện (EDT)
        Theme.runAsync(
                () -> loadThumbnail(imageFile, maxW, maxH),
                icon -> {
                    if (icon == null) {
                        JOptionPane.showMessageDialog(parent, "Không thể đọc tệp hình ảnh.", "Lỗi", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    Window owner = parent != null ? SwingUtilities.getWindowAncestor(parent) : null;
                    JDialog dialog = new JDialog(owner, title != null && !title.isBlank() ? title : imageFile.getName(),
                            Dialog.ModalityType.APPLICATION_MODAL);
                    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

                    JPanel content = new JPanel(new BorderLayout());
                    content.setBackground(Theme.CHAT_BG);
                    content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

                    JLabel label = new JLabel(icon, SwingConstants.CENTER);
                    label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    label.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            dialog.dispose();
                        }
                    });
                    content.add(label, BorderLayout.CENTER);

                    dialog.getRootPane().registerKeyboardAction(
                            e -> dialog.dispose(),
                            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                            JComponent.WHEN_IN_FOCUSED_WINDOW
                    );
                    dialog.setContentPane(content);
                    dialog.pack();
                    dialog.setLocationRelativeTo(parent);
                    dialog.setVisible(true);
                },
                err -> JOptionPane.showMessageDialog(parent, "Không thể đọc tệp hình ảnh.", "Lỗi", JOptionPane.ERROR_MESSAGE)
        );
    }

    public static void openWithSystemPlayer(Component parent, File file) {
        if (file == null || !file.exists()) {
            JOptionPane.showMessageDialog(parent, "Không tìm thấy tệp để mở.", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            JOptionPane.showMessageDialog(parent, "Hệ thống không hỗ trợ mở tệp trực tiếp.", "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().open(file);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parent, "Không thể mở tệp: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}
