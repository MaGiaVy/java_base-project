import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SocketMailServiceTest {
    @Test
    void sendGoesThroughSmtpClient() throws Exception {
        AccountConfig cfg = new AccountConfig("u@test.com", "secret");
        cfg.smtpHost = "localhost";
        cfg.smtpSecurity = Security.NONE;
        try (ScriptedServer server = new ScriptedServer(
                "S: 220 hi", "C: EHLO *", "S: 250 ok",
                "C: AUTH LOGIN", "S: 334 x", "C: *", "S: 334 y", "C: *", "S: 235 ok",
                "C: MAIL FROM:*", "S: 250 ok", "C: RCPT TO:*", "S: 250 ok",
                "C: DATA", "S: 354 go", "DATA", "S: 250 queued",
                "C: QUIT", "S: 221 bye")) {
            cfg.smtpPort = server.port();
            MailService service = new SocketMailService(cfg, new MailLog(null), new Retrier(1, 1, ms -> { }, new MailLog(null)));
            service.send(new OutgoingMail(Arrays.asList("a@test.com"), "S", "B", false, Collections.<File>emptyList()));
            server.assertCompleted();
        }
    }

    @Test
    void fetchInboxDispatchesByProtocol() throws Exception {
        AccountConfig cfg = new AccountConfig("u@test.com", "secret");
        cfg.pop3Host = "localhost";
        cfg.pop3Security = Security.NONE;
        try (ScriptedServer server = new ScriptedServer(
                "S: +OK ready", "C: USER *", "S: +OK", "C: PASS *", "S: +OK",
                "C: STAT", "S: +OK 1 10",
                "C: RETR 1", "S: +OK", "S: Subject: P", "S: ", "S: x", "S: .",
                "C: QUIT", "S: +OK bye")) {
            cfg.pop3Port = server.port();
            MailService service = new SocketMailService(cfg, new MailLog(null), new Retrier(1, 1, ms -> { }, new MailLog(null)));
            List<ReceivedMail> mails = service.fetchInbox(ReceiveProtocol.POP3, 5);
            server.assertCompleted();
            assertEquals("P", mails.get(0).subject);
        }
    }
}
