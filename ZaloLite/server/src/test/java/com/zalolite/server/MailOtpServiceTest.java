package com.zalolite.server;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class MailOtpServiceTest {

    @Test
    void consoleFallbackModeWhenCredentialsBlank() throws Exception {
        MailOtpService svc = MailOtpService.consoleOnly();
        assertTrue(svc.isConsoleMode());
        assertTrue(svc.sendOtp("user@example.com", "123456"));
    }

    @Test
    void esmtp8StepFlowAgainstLocalMockServer() throws Exception {
        List<String> receivedCommands = new CopyOnWriteArrayList<>();
        StringBuilder receivedData = new StringBuilder();

        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            Thread t = new Thread(() -> {
                try (Socket s = server.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                     BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))) {
                    out.write("220 mock.smtp.local ESMTP ready\r\n");
                    out.flush();

                    String line;
                    while ((line = in.readLine()) != null) {
                        receivedCommands.add(line);
                        if (line.startsWith("EHLO ")) {
                            out.write("250-mock.smtp.local\r\n250 AUTH LOGIN\r\n");
                        } else if (line.equals("AUTH LOGIN")) {
                            out.write("334 VXNlcm5hbWU6\r\n");
                            out.flush();
                            receivedCommands.add(in.readLine());
                            out.write("334 UGFzc3dvcmQ6\r\n");
                            out.flush();
                            receivedCommands.add(in.readLine());
                            out.write("235 Authentication successful\r\n");
                        } else if (line.startsWith("MAIL FROM:")) {
                            out.write("250 OK\r\n");
                        } else if (line.startsWith("RCPT TO:")) {
                            out.write("250 OK\r\n");
                        } else if (line.equals("DATA")) {
                            out.write("354 End data with <CR><LF>.<CR><LF>\r\n");
                            out.flush();
                            String dataLine;
                            while ((dataLine = in.readLine()) != null && !dataLine.equals(".")) {
                                receivedData.append(dataLine).append('\n');
                            }
                            out.write("250 Queued\r\n");
                        } else if (line.equals("QUIT")) {
                            out.write("221 Bye\r\n");
                            out.flush();
                            break;
                        }
                        out.flush();
                    }
                } catch (Exception ignored) {
                }
            });
            t.start();

            MailOtpService svc = new MailOtpService("127.0.0.1", port, "sender@test.local", "app-pass-123", false);
            assertFalse(svc.isConsoleMode());
            assertTrue(svc.sendOtp("alice@test.local", "654321"));
            t.join(3000);

            String expectedUserB64 = Base64.getEncoder().encodeToString("sender@test.local".getBytes(StandardCharsets.UTF_8));
            String expectedPassB64 = Base64.getEncoder().encodeToString("app-pass-123".getBytes(StandardCharsets.UTF_8));

            assertEquals(List.of(
                    "EHLO zalolite.local",
                    "AUTH LOGIN",
                    expectedUserB64,
                    expectedPassB64,
                    "MAIL FROM:<sender@test.local>",
                    "RCPT TO:<alice@test.local>",
                    "DATA",
                    "QUIT"
            ), new ArrayList<>(receivedCommands));

            String body = receivedData.toString();
            assertTrue(body.contains("To: <alice@test.local>"));
            assertTrue(body.contains("654321"));
        }
    }
}
