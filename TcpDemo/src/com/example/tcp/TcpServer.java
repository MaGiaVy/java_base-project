package com.example.tcp;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class TcpServer {
    // Biến đếm số thứ tự client (AtomicInteger an toàn khi nhiều thread cùng truy
    // cập)
    private static AtomicInteger clientCounter = new AtomicInteger(0);
    // Biến đếm số client ĐANG kết nối
    private static AtomicInteger activeClients = new AtomicInteger(0);
    // Danh sách tất cả PrintWriter của các client đang kết nối (dùng cho Chat
    // broadcast)
    // CopyOnWriteArrayList an toàn khi nhiều thread cùng đọc/ghi
    private static CopyOnWriteArrayList<PrintWriter> allWriters = new CopyOnWriteArrayList<>();

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        int port = 5000;
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            System.out.println("=== TCP SERVER DA LUONG ===");
            System.out.println("Server dang lang nghe tai port " + port + "...");
            System.out.println("Cho client ket noi...\n");

            // Vòng lặp vô hạn: Server KHÔNG BAO GIỜ dừng, luôn chờ client mới
            while (true) {
                Socket clientSocket = serverSocket.accept();

                // Tăng số thứ tự client
                int clientId = clientCounter.incrementAndGet();
                // Tăng số client đang kết nối
                activeClients.incrementAndGet();

                // Ghi thời gian kết nối
                String connectTime = LocalDateTime.now().format(TIME_FMT);
                System.out.println("[" + connectTime + "] Client #" + clientId
                        + " da ket noi tu " + clientSocket.getInetAddress()
                        + " | Dang ket noi: " + activeClients.get() + " client");

                // Tạo thread riêng cho client này
                new Thread(new ClientHandler(clientSocket, clientId)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ======================================================================
    // Lớp xử lý từng Client (mỗi client = 1 thread riêng)
    // ======================================================================
    static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private int clientId;

        public ClientHandler(Socket clientSocket, int clientId) {
            this.clientSocket = clientSocket;
            this.clientId = clientId;
        }

        @Override
        public void run() {
            PrintWriter writer = null;
            try {
                // Tạo luồng ĐỌC dữ liệu từ client
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                // Tạo luồng GHI dữ liệu về cho client
                writer = new PrintWriter(
                        new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8),
                        true // AutoFlush = true -> gửi ngay lập tức
                );

                // Thêm writer vào danh sách broadcast (cho chức năng Chat)
                allWriters.add(writer);

                // Gửi lời chào cho client vừa kết nối
                writer.println("Chao mung Client #" + clientId + "! Go 'exit' de thoat.");

                // Vòng lặp nhận nhiều thông điệp từ client
                String message;
                while ((message = reader.readLine()) != null) {
                    // Nếu client gõ "exit" thì thoát vòng lặp
                    if (message.equalsIgnoreCase("exit")) {
                        writer.println("Tam biet Client #" + clientId + "!");
                        break;
                    }

                    System.out.println("[Client #" + clientId + "] Nhan duoc: " + message);

                    // Chức năng mới: Client gõ "time" thì server trả về thời gian thực
                    if (message.equalsIgnoreCase("time")) {
                        String currentTime = LocalDateTime.now().format(TIME_FMT);
                        writer.println("[SERVER TIME] " + currentTime);
                        continue; // Không cần chuyển HOA hay broadcast, quay lại chờ tin nhắn tiếp
                    }

                    // Chức năng 4: Chuyển thành chữ HOA
                    String upperMessage = message.toUpperCase();
                    writer.println("[CHU HOA] " + upperMessage);

                    // Chức năng 5: Broadcast (gửi cho TẤT CẢ client đang kết nối)
                    String broadcastMsg = "[Chat - Client #" + clientId + "] " + message;
                    for (PrintWriter pw : allWriters) {
                        if (pw != writer) { // Không gửi lại cho chính mình
                            pw.println(broadcastMsg);
                        }
                    }
                }

            } catch (IOException e) {
                System.out.println("[Client #" + clientId + "] Mat ket noi bat thuong.");
            } finally {
                // Giải phóng tài nguyên khi client thoát
                if (writer != null) {
                    allWriters.remove(writer);
                }
                try {
                    clientSocket.close();
                } catch (IOException e) {
                }

                // Giảm số client đang kết nối
                int remaining = activeClients.decrementAndGet();

                // Ghi thời gian ngắt kết nối
                String disconnectTime = LocalDateTime.now().format(TIME_FMT);
                System.out.println("[" + disconnectTime + "] Client #" + clientId
                        + " da ngat ket noi | Con lai: " + remaining + " client");
            }
        }
    }
}
