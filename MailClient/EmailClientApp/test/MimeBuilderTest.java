import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MimeBuilderTest {
    private static OutgoingMail mail(String subject, String body, boolean html, File... files) {
        return new OutgoingMail(Arrays.asList("to@test.com"), subject, body, html, Arrays.asList(files));
    }

    private static ReceivedMail roundTrip(String built) throws Exception {
        return MailParser.parse("1", built.getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void wrapBase64SplitsAt76CharsWithCrlf() {
        byte[] data = new byte[200];
        String wrapped = MimeBuilder.wrapBase64(data);
        String[] lines = wrapped.split("\r\n");
        assertTrue(wrapped.endsWith("\r\n"));
        for (String line : lines) {
            assertTrue(line.length() <= 76);
        }
        StringBuilder joined = new StringBuilder();
        for (String line : lines) {
            joined.append(line);
        }
        assertArrayEquals(data, Base64.getDecoder().decode(joined.toString()));
        assertEquals(76, lines[0].length());
    }

    @Test
    void dotStuffDoublesLeadingDots() {
        assertEquals("..a\r\nb\r\n..\r\n...c\r\n", MimeBuilder.dotStuff(".a\r\nb\r\n.\r\n..c\r\n"));
        assertEquals("abc\r\n", MimeBuilder.dotStuff("abc\r\n"));
    }

    @Test
    void vietnameseSubjectAndBodySurviveAsAscii() throws Exception {
        String built = MimeBuilder.build("me@test.com", mail("Báo cáo doanh thu tháng 8", "Xin chào!", false));
        assertTrue(built.chars().allMatch(ch -> ch < 128), "toàn bộ thư phải là 7-bit ASCII");
        assertTrue(built.contains("\r\nMIME-Version: 1.0\r\n"));
        ReceivedMail parsed = roundTrip(built);
        assertEquals("Báo cáo doanh thu tháng 8", parsed.subject);
        assertEquals("Xin chào!", parsed.body);
        assertFalse(parsed.html);
    }

    @Test
    void htmlBodyIsFlaggedAsHtml() throws Exception {
        String built = MimeBuilder.build("me@test.com", mail("H", "<h2>Chào</h2>", true));
        assertTrue(built.contains("Content-Type: text/html; charset=utf-8"));
        ReceivedMail parsed = roundTrip(built);
        assertTrue(parsed.html);
        assertEquals("<h2>Chào</h2>", parsed.body);
    }

    @Test
    void attachmentsBecomeMultipartMixed(@TempDir Path dir) throws Exception {
        File a = Files.write(dir.resolve("report.txt"), "hello".getBytes()).toFile();
        File b = Files.write(dir.resolve("data.bin"), new byte[]{1, 2, 3}).toFile();
        String built = MimeBuilder.build("me@test.com", mail("F", "Nội dung", false, a, b));
        assertTrue(built.contains("Content-Type: multipart/mixed; boundary=\""));
        ReceivedMail parsed = roundTrip(built);
        assertEquals("Nội dung", parsed.body);
        assertEquals(Arrays.asList("report.txt", "data.bin"), parsed.getAttachmentNames());
    }

    @Test
    void folderAttachmentCreatesNestedMime(@TempDir Path dir) throws Exception {
        Path folder = Files.createDirectory(dir.resolve("docs"));
        Files.write(folder.resolve("readme.txt"), "Huong dan".getBytes());
        Path sub = Files.createDirectory(folder.resolve("nested"));
        Files.write(sub.resolve("data.csv"), "1,2,3".getBytes());

        String built = MimeBuilder.build("me@test.com", mail("Folder Test", "Body", false, folder.toFile()));
        assertTrue(built.contains("----=_Part_Folder_"), "chứa nested boundary cho folder");
        ReceivedMail parsed = roundTrip(built);
        List<String> names = parsed.getAttachmentNames();
        assertTrue(names.stream().anyMatch(n -> n.contains("readme.txt")));
        assertTrue(names.stream().anyMatch(n -> n.contains("data.csv")));
    }
}
