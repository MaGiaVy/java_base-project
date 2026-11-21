package com.iot;

import javax.swing.*;

/**
 * =========================================================================
 * FILE: App.java - ĐIỂM KHỞI CHẠY ỨNG DỤNG JAVA DESKTOP CLIENT (ENTRY POINT)
 * =========================================================================
 * - Nhiệm vụ: Là file đầu tiên được JVM gọi khi chạy RUN_JAVA_CLIENT.bat.
 * - Vai trò: Khởi tạo giao diện đồ họa Swing và mở cửa sổ MainFrame.
 * =========================================================================
 */
public class App {

    /**
     * Hàm main: Điểm bắt đầu thực thi chương trình Java
     * @param args: Các tham số dòng lệnh truyền vào (nếu có)
     */
    public static void main(String[] args) {
        // BƯỚC 1: Cài đặt giao diện (Look and Feel) theo chuẩn hệ điều hành Windows
        // Giúp các nút bấm, thanh cuộn, font chữ hiển thị tự nhiên như ứng dụng native của Windows
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Nếu hệ điều hành không hỗ trợ thì bỏ qua, dùng giao diện mặc định của Java
        }

        // BƯỚC 2: Khởi tạo và hiển thị cửa sổ giao diện chính (MainFrame)
        // Dùng SwingUtilities.invokeLater() để đưa việc tạo giao diện vào luồng EDT (Event Dispatch Thread)
        // Đảm bảo giao diện Swing hoạt động an toàn đa luồng (thread-safe), không bị lag hay đơ
        SwingUtilities.invokeLater(() -> {
            // Tạo đối tượng cửa sổ chính của chương trình
            MainFrame frame = new MainFrame();

            // Hiển thị cửa sổ lên màn hình máy tính cho người dùng thao tác
            frame.setVisible(true);
        });
    }
}
