import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * ============================================================================
 * TCP CHAT CLIENT (Hỗ trợ cả Giao diện đồ họa Swing và Dòng lệnh Console)
 * ============================================================================
 * - Kết nối tới TCP Chat Server (Port 5000).
 * - Nhận tin nhắn liên tục trên luồng nền (Background Thread).
 * - Gửi tin nhắn tức thì lên Server để Broadcast.
 * ============================================================================
 */
public class TcpChatClient {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5000;

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;

        // Nếu truyền tham số --console hoặc môi trường không có màn hình -> chạy Console
        boolean forceConsole = false;
        for (String arg : args) {
            if ("--console".equalsIgnoreCase(arg) || "-c".equalsIgnoreCase(arg)) {
                forceConsole = true;
                break;
            }
        }

        if (forceConsole || GraphicsEnvironment.isHeadless()) {
            runConsoleClient(host, port);
        } else {
            SwingUtilities.invokeLater(() -> new ChatClientGui(host, port).setVisible(true));
        }
    }

    // ========================================================================
    // CHẾ ĐỘ 1: CONSOLE DÒNG LỆNH (Áp dụng lý thuyết Chương 11)
    // ========================================================================
    public static void runConsoleClient(String host, int port) {
        System.out.println("=========================================");
        System.out.println("   📡 TCP CHAT CLIENT (CONSOLE MODE)");
        System.out.println("=========================================");
        System.out.println("Đang kết nối tới " + host + ":" + port + "...");

        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("✅ Đã kết nối thành công! Gõ tin nhắn và Enter để gửi. Gõ /quit để thoát.\n");

            // Luồng nhận tin nền (Daemon Thread)
            Thread receiveThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                } catch (IOException e) {
                    System.out.println("\n[!] Mất kết nối tới Server.");
                }
            });
            receiveThread.setDaemon(true);
            receiveThread.start();

            // Luồng chính: Nhập từ bàn phím và gửi lên Server
            while (scanner.hasNextLine()) {
                String msg = scanner.nextLine().trim();
                if (msg.isEmpty()) continue;

                writer.write(msg);
                writer.newLine();
                writer.flush();

                if (msg.equalsIgnoreCase("/quit") || msg.equalsIgnoreCase("exit")) {
                    break;
                }
            }
            System.out.println("Đã ngắt kết nối.");

        } catch (IOException e) {
            System.err.println("❌ Không thể kết nối tới Server: " + e.getMessage());
            System.err.println("👉 Hãy chắc chắn rằng ChatAndWebServer đã được khởi động!");
        }
    }

    // ========================================================================
    // CHẾ ĐỘ 2: GIAO DIỆN ĐỒ HỌA SWING (Dễ mở nhiều client test)
    // ========================================================================
    static class ChatClientGui extends JFrame {
        private final String host;
        private final int port;
        private Socket socket;
        private BufferedReader reader;
        private BufferedWriter writer;

        private JTextArea chatArea;
        private JTextField inputField;
        private JButton sendButton;
        private JLabel statusLabel;

        public ChatClientGui(String host, int port) {
            this.host = host;
            this.port = port;

            setTitle("💬 TCP Chat Client - [" + host + ":" + port + "]");
            setSize(520, 560);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

            initUi();
            connectToServer();
        }

        private void initUi() {
            JPanel root = new JPanel(new BorderLayout(8, 8));
            root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            // Header bar
            JPanel header = new JPanel(new BorderLayout());
            header.setBackground(new Color(0xC62828));
            header.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

            JLabel titleLabel = new JLabel("Phòng Chat TCP (Port " + port + ")");
            titleLabel.setForeground(Color.WHITE);
            titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
            header.add(titleLabel, BorderLayout.WEST);

            statusLabel = new JLabel("Đang kết nối...");
            statusLabel.setForeground(new Color(0xFFCDD2));
            statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
            header.add(statusLabel, BorderLayout.EAST);
            root.add(header, BorderLayout.NORTH);

            // Chat display area
            chatArea = new JTextArea();
            chatArea.setEditable(false);
            chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            chatArea.setLineWrap(true);
            chatArea.setWrapStyleWord(true);
            chatArea.setBackground(new Color(0xFAFAFA));
            JScrollPane scrollPane = new JScrollPane(chatArea);
            scrollPane.setBorder(BorderFactory.createLineBorder(new Color(0xE0E0E0)));
            root.add(scrollPane, BorderLayout.CENTER);

            // Input bar
            JPanel inputPanel = new JPanel(new BorderLayout(8, 0));
            inputField = new JTextField();
            inputField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            inputField.setEnabled(false);

            sendButton = new JButton("Gửi / Send");
            sendButton.setBackground(new Color(0xC62828));
            sendButton.setForeground(Color.WHITE);
            sendButton.setFocusPainted(false);
            sendButton.setEnabled(false);
            sendButton.setFont(new Font("SansSerif", Font.BOLD, 12));

            inputPanel.add(inputField, BorderLayout.CENTER);
            inputPanel.add(sendButton, BorderLayout.EAST);
            root.add(inputPanel, BorderLayout.SOUTH);

            setContentPane(root);

            // Action gửi tin
            ActionListener sendAction = e -> sendMessage();
            sendButton.addActionListener(sendAction);
            inputField.addActionListener(sendAction);

            // Đóng cửa sổ ngắt kết nối an toàn
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    disconnect();
                }
            });
        }

        private void connectToServer() {
            new Thread(() -> {
                try {
                    socket = new Socket(host, port);
                    reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("🟢 Đã kết nối");
                        statusLabel.setForeground(Color.WHITE);
                        inputField.setEnabled(true);
                        sendButton.setEnabled(true);
                        inputField.requestFocusInWindow();
                    });

                    // Vòng nhận tin từ Server
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String msg = line;
                        SwingUtilities.invokeLater(() -> {
                            chatArea.append(msg + "\n");
                            chatArea.setCaretPosition(chatArea.getDocument().getLength());
                        });
                    }
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("🔴 Mất kết nối");
                        chatArea.append("[!] Không thể kết nối tới Server: " + e.getMessage() + "\n");
                        inputField.setEnabled(false);
                        sendButton.setEnabled(false);
                    });
                }
            }).start();
        }

        private void sendMessage() {
            String text = inputField.getText().trim();
            if (text.isEmpty() || writer == null) return;

            try {
                writer.write(text);
                writer.newLine();
                writer.flush();
                inputField.setText("");

                if (text.equalsIgnoreCase("/quit") || text.equalsIgnoreCase("exit")) {
                    disconnect();
                    dispose();
                }
            } catch (IOException e) {
                chatArea.append("[!] Lỗi gửi tin nhắn: " + e.getMessage() + "\n");
            }
        }

        private void disconnect() {
            try {
                if (writer != null) {
                    writer.write("/quit\n");
                    writer.flush();
                }
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ignored) {}
        }
    }
}
