import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class OutgoingMailTest {
    private static final List<File> NO_FILES = Collections.emptyList();

    @Test
    void splitsRecipientsByCommaAndSemicolon() {
        assertEquals(Arrays.asList("a@x.com", "b@y.com", "c@z.com"),
                OutgoingMail.splitRecipients("a@x.com, b@y.com; c@z.com ,"));
        assertTrue(OutgoingMail.splitRecipients(null).isEmpty());
    }

    @Test
    void requiresAtLeastOneValidRecipient() {
        assertThrows(IllegalArgumentException.class,
                () -> new OutgoingMail(Collections.<String>emptyList(), "s", "b", false, NO_FILES));
        assertThrows(IllegalArgumentException.class,
                () -> new OutgoingMail(Arrays.asList("khong-hop-le"), "s", "b", false, NO_FILES));
    }

    @Test
    void rejectsHeaderInjectionInAddress() {
        assertThrows(IllegalArgumentException.class, () -> new OutgoingMail(
                Arrays.asList("a@x.com\r\nBcc: evil@x.com"), "s", "b", false, NO_FILES));
    }

    @Test
    void stripsNewlinesFromSubjectAndDefaultsNulls() {
        OutgoingMail m = new OutgoingMail(Arrays.asList("a@x.com"), "dòng 1\r\nBcc: e@x.com", null, false, null);
        assertEquals("dòng 1 Bcc: e@x.com", m.subject);
        assertEquals("", m.body);
        assertTrue(m.attachments.isEmpty());
    }

    @Test
    void supportsBothFilesAndDirectories(@TempDir Path dir) throws Exception {
        File missing = dir.resolve("none.pdf").toFile();
        assertThrows(IllegalArgumentException.class, () -> new OutgoingMail(
                Arrays.asList("a@x.com"), "s", "b", false, Arrays.asList(missing)));

        // Hỗ trợ thư mục và file thật
        File realFile = Files.write(dir.resolve("a.txt"), "x".getBytes()).toFile();
        File subDir = Files.createDirectory(dir.resolve("subfolder")).toFile();
        OutgoingMail ok = new OutgoingMail(Arrays.asList("a@x.com"), "s", "b", true, Arrays.asList(realFile, subDir));
        assertEquals(2, ok.attachments.size());
        assertTrue(ok.html);
    }
}
