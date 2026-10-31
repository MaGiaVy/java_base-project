import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class MailLog {
    public interface Listener {
        void onLine(String line);
    }

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Path file;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    /** file = null thì chỉ thông báo cho listener, không ghi ra đĩa (tiện cho unit test). */
    public MailLog(Path file) {
        this.file = file;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void info(String message) {
        write("INFO", message);
    }

    public void warn(String message) {
        write("WARN", message);
    }

    public void error(String message) {
        write("ERROR", message);
    }

    private synchronized void write(String level, String message) {
        String line = LocalDateTime.now().format(FMT) + " [" + level + "] " + message;
        if (file != null) {
            try {
                Path dir = file.toAbsolutePath().getParent();
                if (dir != null) {
                    Files.createDirectories(dir);
                }
                Files.write(file, (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.err.println("Không ghi được log: " + e.getMessage());
            }
        }
        for (Listener listener : listeners) {
            listener.onLine(line);
        }
    }
}
