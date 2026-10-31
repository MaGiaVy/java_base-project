import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SmtpSocketClientTest {
    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static AccountConfig config(int port) {
        AccountConfig c = new AccountConfig("u@test.com", "secret");
        c.smtpHost = "localhost";
        c.smtpPort = port;
        c.smtpSecurity = Security.NONE;
        return c;
    }

    private static OutgoingMail mail() {
        return new OutgoingMail(Arrays.asList("a@test.com"), "Hello", "Body", false, Collections.<File>emptyList());
    }

    @Test
    void sendsMailFollowingEightStepFlow(@TempDir Path dir) throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: 220 test ESMTP",
                "C: EHLO *",
                "S: 250-test",
                "S: 250 AUTH LOGIN",
                "C: AUTH LOGIN",
                "S: 334 VXNlcm5hbWU6",
                "C: " + b64("u@test.com"),
                "S: 334 UGFzc3dvcmQ6",
                "C: " + b64("secret"),
                "S: 235 ok",
                "C: MAIL FROM:<u@test.com>",
                "S: 250 ok",
                "C: RCPT TO:<a@test.com>",
                "S: 250 ok",
                "C: DATA",
                "S: 354 go ahead",
                "DATA",
                "S: 250 queued",
                "C: QUIT",
                "S: 221 bye")) {
            Path logFile = dir.resolve("smtp.log");
            new SmtpSocketClient(config(server.port()), new MailLog(logFile)).send(mail());
            server.assertCompleted();

            assertTrue(server.dataLines().contains("Subject: Hello"));
            assertTrue(server.dataLines().contains("From: u@test.com"));
            String log = new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8);
            assertFalse(log.contains("secret"));
            assertFalse(log.contains(b64("secret")), "mật khẩu không bao giờ được xuất hiện trong log");
        }
    }

    @Test
    void wrongPasswordIsPermanentError() throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: 220 hi", "C: EHLO *", "S: 250 ok",
                "C: AUTH LOGIN", "S: 334 x",
                "C: *", "S: 334 y",
                "C: *", "S: 535 5.7.8 Username and Password not accepted")) {
            MailException e = assertThrows(MailException.class,
                    () -> new SmtpSocketClient(config(server.port()), new MailLog(null)).send(mail()));
            assertEquals(535, e.getReplyCode());
            assertFalse(e.isTransientError());
            server.assertCompleted();
        }
    }

    @Test
    void mailboxBusyIsTransientError() throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: 220 hi", "C: EHLO *", "S: 250 ok",
                "C: AUTH LOGIN", "S: 334 x",
                "C: *", "S: 334 y",
                "C: *", "S: 235 ok",
                "C: MAIL FROM:*", "S: 250 ok",
                "C: RCPT TO:*", "S: 450 Mailbox busy")) {
            MailException e = assertThrows(MailException.class,
                    () -> new SmtpSocketClient(config(server.port()), new MailLog(null)).send(mail()));
            assertEquals(450, e.getReplyCode());
            assertTrue(e.isTransientError());
            server.assertCompleted();
        }
    }

    @Test
    void connectionRefusedIsTransientNetworkError() {
        AccountConfig c = config(1); // Cổng 1 không mở
        MailException e = assertThrows(MailException.class,
                () -> new SmtpSocketClient(c, new MailLog(null)).send(mail()));
        assertTrue(e.isTransientError());
        assertEquals(-1, e.getReplyCode());
    }
}
