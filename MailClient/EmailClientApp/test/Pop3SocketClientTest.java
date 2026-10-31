import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

public class Pop3SocketClientTest {
    private static AccountConfig config(int port) {
        AccountConfig c = new AccountConfig("u@test.com", "secret");
        c.pop3Host = "localhost";
        c.pop3Port = port;
        c.pop3Security = Security.NONE;
        return c;
    }

    @Test
    void fetchesNewestFirstAndUnstuffsDots() throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: +OK POP3 ready",
                "C: USER u@test.com", "S: +OK",
                "C: PASS secret", "S: +OK logged in",
                "C: STAT", "S: +OK 2 300",
                "C: RETR 2", "S: +OK",
                "S: From: b@x.com", "S: Subject: Second", "S: ", "S: ..dong bat dau bang cham", "S: .",
                "C: RETR 1", "S: +OK",
                "S: From: a@x.com", "S: Subject: First", "S: ", "S: noi dung 1", "S: .",
                "C: QUIT", "S: +OK bye")) {
            List<ReceivedMail> mails = new Pop3SocketClient(config(server.port()), new MailLog(null)).fetchLatest(5);
            server.assertCompleted();

            assertEquals(2, mails.size());
            assertEquals("Second", mails.get(0).subject);
            assertEquals("2", mails.get(0).id);
            assertTrue(mails.get(0).body.contains(".dong bat dau bang cham"));
            assertFalse(mails.get(0).body.contains("..dong"));
            assertEquals("First", mails.get(1).subject);
        }
    }

    @Test
    void respectsLimit() throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: +OK ready",
                "C: USER *", "S: +OK",
                "C: PASS *", "S: +OK",
                "C: STAT", "S: +OK 3 900",
                "C: RETR 3", "S: +OK",
                "S: Subject: Third", "S: ", "S: x", "S: .",
                "C: QUIT", "S: +OK bye")) {
            List<ReceivedMail> mails = new Pop3SocketClient(config(server.port()), new MailLog(null)).fetchLatest(1);
            server.assertCompleted();
            assertEquals(1, mails.size());
            assertEquals("Third", mails.get(0).subject);
        }
    }

    @Test
    void deletesMailWithDeleAndQuit() throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: +OK ready",
                "C: USER *", "S: +OK",
                "C: PASS *", "S: +OK",
                "C: DELE 2", "S: +OK message deleted",
                "C: QUIT", "S: +OK bye")) {
            new Pop3SocketClient(config(server.port()), new MailLog(null)).deleteMail("2");
            server.assertCompleted();
        }
    }

    @Test
    void wrongPasswordIsPermanentError() throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: +OK ready",
                "C: USER *", "S: +OK",
                "C: PASS *", "S: -ERR [AUTH] Username and password not accepted.")) {
            MailException e = assertThrows(MailException.class,
                    () -> new Pop3SocketClient(config(server.port()), new MailLog(null)).fetchLatest(5));
            assertFalse(e.isTransientError());
            assertTrue(e.getMessage().contains("not accepted"));
            server.assertCompleted();
        }
    }

    @Test
    void tempErrorIsTransient() throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: +OK ready",
                "C: USER *", "S: -ERR [SYS/TEMP] Server busy")) {
            MailException e = assertThrows(MailException.class,
                    () -> new Pop3SocketClient(config(server.port()), new MailLog(null)).fetchLatest(5));
            assertTrue(e.isTransientError());
            server.assertCompleted();
        }
    }
}
