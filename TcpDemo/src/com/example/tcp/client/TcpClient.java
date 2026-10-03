package com.example.tcp.client;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class TcpClient {
    public static void main(String[] args) {
        String serverIp = "127.0.0.1";
        int serverPort = 5000;

        try {
            // 1. Kết nối tới Server
            Socket socket = new Socket(serverIp, serverPort);
            System.out.println("Da ket noi den Server " + serverIp + ":" + serverPort);

            // 2. Tạo luồng gửi/nhận
            PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                    true);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            Scanner scanner = new Scanner(System.in, "UTF-8");

            // 3. Thread riêng để NHẬN tin nhắn từ server (chạy nền)
            // Vì server có thể gửi broadcast bất cứ lúc nào
            Thread receiveThread = new Thread(() -> {
                try {
                    String response;
                    while ((response = reader.readLine()) != null) {
                        System.out.println("Server: " + response);
                    }
                } catch (IOException e) {
                    System.out.println("Mat ket noi voi Server.");
                }
            });
            receiveThread.setDaemon(true); // Thread nền, tự tắt khi main tắt
            receiveThread.start();

            // 4. Vòng lặp GỬI tin nhắn từ bàn phím
            System.out.println("Nhap tin nhan (go 'exit' de thoat):");
            while (true) {
                String input = scanner.nextLine().trim();
                if (input.isEmpty())
                    continue;

                writer.println(input);

                if (input.equalsIgnoreCase("exit")) {
                    System.out.println("Dang ngat ket noi...");
                    break;
                }
            }

            // 5. Đóng kết nối
            socket.close();
            scanner.close();
            System.out.println("Client da dong.");

        } catch (ConnectException e) {
            System.out.println("LOI: Khong the ket noi toi Server! Hay chay Server truoc.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
