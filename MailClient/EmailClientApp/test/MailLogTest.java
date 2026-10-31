import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MailLogTest {
    @Test
    void writesLinesToFileAndNotifiesListeners(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("logs").resolve("mail.log");
        MailLog log = new MailLog(file);
        List<String> seen = new ArrayList<>();
        log.addListener(seen::add);

        log.info("xin chào");
        log.warn("cảnh báo");

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("[INFO] xin chào"));
        assertTrue(lines.get(1).contains("[WARN] cảnh báo"));
        assertEquals(2, seen.size());
    }

    @Test
    void nullFileOnlyNotifiesListeners() {
        MailLog log = new MailLog(null);
        List<String> seen = new ArrayList<>();
        log.addListener(seen::add);
        log.error("lỗi");
        assertEquals(1, seen.size());
        assertTrue(seen.get(0).contains("[ERROR] lỗi"));
    }
}
