import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Client POP3 mức thấp qua Socket: USER, PASS, STAT, RETR, DELE, QUIT (mục 8 của giáo trình). */
public class Pop3SocketClient {
    private static final int TIMEOUT_MS = 15000;

    private final AccountConfig cfg;
    private final MailLog log;

    public Pop3SocketClient(AccountConfig cfg, MailLog log) {
        this.cfg = cfg;
        this.log = log;
    }

    public List<ReceivedMail> fetchLatest(int limit) throws MailException {
        log.info("POP3(Socket): kết nối tới " + cfg.pop3Host + ":" + cfg.pop3Port + " (" + cfg.pop3Security + ")");
        try (LineConnection c = new LineConnection(cfg.pop3Host, cfg.pop3Port,
                cfg.pop3Security == Security.SSL, TIMEOUT_MS)) {
            String greeting = c.readLine();
            log.info("S: " + greeting);
            requireOk(greeting);

            command(c, "USER " + cfg.username, "USER " + cfg.username);
            command(c, "PASS " + cfg.password, "PASS <password masked>");
            String stat = command(c, "STAT", "STAT");
            int count = Integer.parseInt(stat.trim().split("\\s+")[1]);

            List<ReceivedMail> mails = new ArrayList<>();
            for (int i = count; i > Math.max(0, count - limit); i--) {
                byte[] raw = retrieve(c, i);
                mails.add(MailParser.parse(String.valueOf(i), raw));
            }
            try {
                command(c, "QUIT", "QUIT");
            } catch (MailException ignored) {
                // Không quan trọng
            }
            log.info("POP3(Socket): đã tải thành công " + mails.size() + " thư");
            return mails;
        } catch (IOException e) {
            throw MailException.fromIo(e);
        }
    }

    /** Xóa thư trên server qua lệnh DELE (xác nhận cập nhật trong UPDATE state khi phát lệnh QUIT). */
    public void deleteMail(String id) throws MailException {
        log.info("POP3(Socket): xóa thư ID " + id);
        try (LineConnection c = new LineConnection(cfg.pop3Host, cfg.pop3Port,
                cfg.pop3Security == Security.SSL, TIMEOUT_MS)) {
            String greeting = c.readLine();
            requireOk(greeting);

            command(c, "USER " + cfg.username, "USER " + cfg.username);
            command(c, "PASS " + cfg.password, "PASS <password masked>");
            command(c, "DELE " + id, "DELE " + id);
            command(c, "QUIT", "QUIT"); // Chuyển sang UPDATE state để cam kết xóa thư
            log.info("POP3(Socket): đã xóa thành công thư ID " + id);
        } catch (IOException e) {
            throw MailException.fromIo(e);
        }
    }

    private String command(LineConnection c, String wire, String logText) throws IOException, MailException {
        log.info("C: " + logText);
        c.writeLine(wire);
        String response = c.readLine();
        log.info("S: " + response);
        requireOk(response);
        return response;
    }

    /** RETR n: đọc đến dòng chỉ có ".", bỏ một dấu "." ở các dòng "..xxx" (kỹ thuật dot-unstuffing). */
    private byte[] retrieve(LineConnection c, int n) throws IOException, MailException {
        command(c, "RETR " + n, "RETR " + n);
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        String line;
        while (!(line = c.readLine()).equals(".")) {
            if (line.startsWith("..")) {
                line = line.substring(1);
            }
            raw.write(line.getBytes(StandardCharsets.ISO_8859_1));
            raw.write('\r');
            raw.write('\n');
        }
        log.info("S: <thư " + n + ": " + raw.size() + " byte>");
        return raw.toByteArray();
    }

    private static void requireOk(String response) throws MailException {
        if (!response.startsWith("+OK")) {
            throw new MailException("POP3: " + response, -1, response.contains("[SYS/TEMP]"));
        }
    }
}
