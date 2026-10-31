import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class MailParserTest {
    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void parsesPlainTextWithEncodedHeaders() throws Exception {
        String raw = "From: =?UTF-8?B?VsO9?= <vy@test.com>\r\n"
                + "Subject: =?UTF-8?B?Q2jDoG8=?=\r\n"
                + "Date: Sun, 18 Oct 2026 10:00:00 +0700\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n\r\n"
                + "Xin chao\r\n";
        ReceivedMail m = MailParser.parse("1", bytes(raw));
        assertEquals("1", m.id);
        assertEquals("Chào", m.subject);
        assertTrue(m.from.contains("Vý"));
        assertTrue(m.from.contains("vy@test.com"));
        assertEquals("Xin chao", m.body.trim());
        assertFalse(m.html);
        assertFalse(m.date.isEmpty());
        assertEquals(Collections.emptyList(), m.getAttachmentNames());
    }

    @Test
    void htmlOnlyMailIsFlaggedAsHtml() throws Exception {
        String raw = "From: a@x.com\r\nSubject: H\r\nContent-Type: text/html; charset=utf-8\r\n\r\n<p>Hi</p>\r\n";
        ReceivedMail m = MailParser.parse("2", bytes(raw));
        assertTrue(m.html);
        assertEquals("<p>Hi</p>", m.body.trim());
    }

    @Test
    void listsAttachmentNamesAndExtractsBinaryData() throws Exception {
        String raw = "From: a@x.com\r\nSubject: F\r\n"
                + "Content-Type: multipart/mixed; boundary=\"B\"\r\n\r\n"
                + "--B\r\nContent-Type: text/plain; charset=utf-8\r\n\r\nNoi dung\r\n"
                + "--B\r\nContent-Type: application/pdf; name=\"report.pdf\"\r\n"
                + "Content-Disposition: attachment; filename=\"report.pdf\"\r\n"
                + "Content-Transfer-Encoding: base64\r\n\r\nSGVsbG8=\r\n"
                + "--B--\r\n";
        ReceivedMail m = MailParser.parse("3", bytes(raw));
        assertEquals("Noi dung", m.body.trim());
        assertEquals(Collections.singletonList("report.pdf"), m.getAttachmentNames());
        assertEquals(1, m.attachments.size());
        assertEquals("Hello", new String(m.attachments.get(0).getData(), StandardCharsets.UTF_8));
    }
}
