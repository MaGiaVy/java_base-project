package com.example.client;

import com.example.model.ChatMessage;
import com.example.model.MessageType;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ChatClientIntegrationTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== RUNNING ChatClientIntegrationTest ===");

        char[] keystorePass = "LabOnly_ChangeMe".toCharArray();
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream("server/certificates/server.p12")) {
            keyStore.load(in, keystorePass);
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, keystorePass);

        SSLContext serverContext = SSLContext.getInstance("TLS");
        serverContext.init(kmf.getKeyManagers(), null, null);

        SSLServerSocketFactory ssf = serverContext.getServerSocketFactory();
        SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(0);
        int port = serverSocket.getLocalPort();
        System.out.println("[SERVER] Mock TLS Server listening on port: " + port);

        CountDownLatch clientLoggedIn = new CountDownLatch(1);
        CountDownLatch clientReceivedMessage = new CountDownLatch(1);
        AtomicReference<String> receivedContent = new AtomicReference<>();
        AtomicBoolean serverError = new AtomicBoolean(false);

        // Server thread
        Thread serverThread = new Thread(() -> {
            try (SSLSocket socket = (SSLSocket) serverSocket.accept();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

                socket.startHandshake();
                System.out.println("[SERVER] Client connected and TLS handshake completed.");

                // 1. Read LOGIN
                String loginLine = reader.readLine();
                ChatMessage loginMsg = ChatMessage.fromJson(loginLine);
                if (MessageType.LOGIN.getValue().equalsIgnoreCase(loginMsg.getType())
                        && "an".equals(loginMsg.getUsername())) {
                    System.out.println("[SERVER] Received valid LOGIN from user: " + loginMsg.getUsername());
                    // Send LOGIN_RESULT
                    ChatMessage resultMsg = ChatMessage.createLoginResult(true, 101, "Login successful");
                    writer.write(resultMsg.toJson());
                    writer.flush();
                } else {
                    serverError.set(true);
                    return;
                }

                // 2. Read MESSAGE from client
                String msgLine = reader.readLine();
                ChatMessage chatMsg = ChatMessage.fromJson(msgLine);
                System.out.println("[SERVER] Received message from client: " + chatMsg.getContent());

                // 3. Send a system/peer message back to client
                ChatMessage replyMsg = ChatMessage.createMessage("ServerBot", "Echo: " + chatMsg.getContent());
                writer.write(replyMsg.toJson());
                writer.flush();

                // 4. Wait for client disconnect
                String disconnectLine = reader.readLine();
                if (disconnectLine != null) {
                    ChatMessage disMsg = ChatMessage.fromJson(disconnectLine);
                    System.out.println("[SERVER] Received disconnect type: " + disMsg.getType());
                }

            } catch (Exception e) {
                System.err.println("[SERVER] Error: " + e.getMessage());
            } finally {
                try {
                    serverSocket.close();
                } catch (Exception ignored) {}
            }
        }, "MockServerThread");
        serverThread.start();

        // Client execution
        ChatClient client = new ChatClient(new File("client/certificates/server.crt"));
        client.connect("localhost", port);
        System.out.println("[CLIENT] Connected over TLS to localhost:" + port);

        boolean loggedIn = client.login("an", "pass123");
        if (!loggedIn) {
            System.err.println("[CLIENT FAIL] Login failed: " + client.getLastErrorMessage());
            System.exit(1);
        }
        System.out.println("[CLIENT] Logged in successfully! Client ID: " + client.getClientId());
        clientLoggedIn.countDown();

        client.startReceiveLoop(msg -> {
            System.out.println("[CLIENT] Received incoming message: " + msg.getContent());
            receivedContent.set(msg.getContent());
            clientReceivedMessage.countDown();
        }, () -> {
            System.out.println("[CLIENT] OnDisconnected callback fired.");
        });

        // Send a test message
        client.sendMessage("Hello from integration test!");

        boolean receivedOk = clientReceivedMessage.await(5, TimeUnit.SECONDS);
        if (!receivedOk) {
            System.err.println("[CLIENT FAIL] Timed out waiting for message echo from server!");
            System.exit(1);
        }

        if (!"Echo: Hello from integration test!".equals(receivedContent.get())) {
            System.err.println("[CLIENT FAIL] Unexpected echo content: " + receivedContent.get());
            System.exit(1);
        }

        System.out.println("[CLIENT] Disconnecting...");
        client.disconnect();
        serverThread.join(3000);

        System.out.println("=== CHAT CLIENT INTEGRATION TEST PASSED! ===");
    }
}
