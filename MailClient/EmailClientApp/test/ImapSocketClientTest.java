import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ImapSocketClientTest {
    private static AccountConfig config(int port) {
        AccountConfig c = new AccountConfig("u@test.com", "se\"cret");
        c.imapHost = "localhost";
        c.imapPort = port;
        c.imapSecurity = Security.NONE;
        return c;
    }

    @Test
    void fetchesMessageThroughLiteral() throws Exception {
        String raw = "From: a@x.com\r\nSubject: Hi\r\n\r\nBody\r\n";
        int n = raw.getBytes(StandardCharsets.US_ASCII).length;
        try (ScriptedServer server = new ScriptedServer(
                "S: * OK IMAP4rev1 ready",
                "C: A1 LOGIN \"u@test.com\" \"se\\\"cret\"",
                "S: A1 OK logged in",
                "C: A2 SELECT INBOX",
                "S: * 1 EXISTS",
                "S: A2 OK [READ-WRITE] done",
                "C: A3 FETCH 1 BODY.PEEK[]",
                "S: * 1 FETCH (BODY[] {" + n + "}",
                "L: " + raw,
                "S:  FLAGS (\\Seen))",
                "S: A3 OK Success",
                "C: A4 LOGOUT",
                "S: * BYE",
                "S: A4 OK done")) {
            List<ReceivedMail> mails = new ImapSocketClient(config(server.port()), new MailLog(null)).fetchLatest(10);
            server.assertCompleted();
            assertEquals(1, mails.size());
            assertEquals("Hi", mails.get(0).subject);
            assertEquals("Body", mails.get(0).body.trim());
            assertEquals("1", mails.get(0).id);
        }
    }

    @Test
    void emptyMailboxReturnsEmptyList() throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: * OK ready",
                "C: A1 LOGIN *", "S: A1 OK ok",
                "C: A2 SELECT INBOX", "S: * 0 EXISTS", "S: A2 OK done",
                "C: A3 LOGOUT", "S: * BYE", "S: A3 OK done")) {
            List<ReceivedMail> mails = new ImapSocketClient(config(server.port()), new MailLog(null)).fetchLatest(10);
            server.assertCompleted();
            assertTrue(mails.isEmpty());
        }
    }

    @Test
    void deletesMailWithStoreAndExpunge() throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: * OK ready",
                "C: A1 LOGIN *", "S: A1 OK ok",
                "C: A2 SELECT INBOX", "S: A2 OK done",
                "C: A3 STORE 1 +FLAGS (\\Deleted)", "S: A3 OK done",
                "C: A4 EXPUNGE", "S: * 1 EXPUNGE", "S: A4 OK done",
                "C: A5 LOGOUT", "S: A5 OK done")) {
            new ImapSocketClient(config(server.port()), new MailLog(null)).deleteMail("1");
            server.assertCompleted();
        }
    }

    @Test
    void invalidCredentialsArePermanent() throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: * OK ready",
                "C: A1 LOGIN *",
                "S: A1 NO [AUTHENTICATIONFAILED] Invalid credentials (Failure)")) {
            MailException e = assertThrows(MailException.class,
                    () -> new ImapSocketClient(config(server.port()), new MailLog(null)).fetchLatest(10));
            assertFalse(e.isTransientError());
            assertTrue(e.getMessage().contains("AUTHENTICATIONFAILED"));
            server.assertCompleted();
        }
    }

    @Test
    void unavailableIsTransient() throws Exception {
        try (ScriptedServer server = new ScriptedServer(
                "S: * OK ready",
                "C: A1 LOGIN *",
                "S: A1 NO [UNAVAILABLE] try later")) {
            MailException e = assertThrows(MailException.class,
                    () -> new ImapSocketClient(config(server.port()), new MailLog(null)).fetchLatest(10));
            assertTrue(e.isTransientError());
            server.assertCompleted();
        }
    }
}
