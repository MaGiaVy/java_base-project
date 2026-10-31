import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Client IMAP mức thấp qua Socket: LOGIN, SELECT, FETCH BODY.PEEK[], STORE, EXPUNGE, LOGOUT, literal {N}. */
public class ImapSocketClient {
    private static final int TIMEOUT_MS = 15000;
    private static final Pattern LITERAL = Pattern.compile("\\{(\\d+)\\}$");
    private static final Pattern EXISTS = Pattern.compile("^\\* (\\d+) EXISTS$");

    private static final class Response {
        final List<String> lines = new ArrayList<>();
        final List<byte[]> literals = new ArrayList<>();
        String status = "";
    }

    private final AccountConfig cfg;
    private final MailLog log;
    private int tagCounter = 0;

    public ImapSocketClient(AccountConfig cfg, MailLog log) {
        this.cfg = cfg;
        this.log = log;
    }

    public List<ReceivedMail> fetchLatest(int limit) throws MailException {
        log.info("IMAP(Socket): kết nối tới " + cfg.imapHost + ":" + cfg.imapPort + " (" + cfg.imapSecurity + ")");
        tagCounter = 0;
        try (LineConnection c = new LineConnection(cfg.imapHost, cfg.imapPort,
                cfg.imapSecurity == Security.SSL, TIMEOUT_MS)) {
            String greeting = c.readLine();
            log.info("S: " + greeting);
            if (!greeting.startsWith("* OK")) {
                throw new MailException("IMAP: " + greeting, -1, false);
            }

            run(c, "LOGIN " + quote(cfg.username) + " " + quote(cfg.password),
                    "LOGIN " + quote(cfg.username) + " <password masked>");
            Response select = run(c, "SELECT INBOX", "SELECT INBOX");
            int count = parseExists(select.lines);

            List<ReceivedMail> mails = new ArrayList<>();
            for (int i = count; i > Math.max(0, count - limit); i--) {
                Response fetch = run(c, "FETCH " + i + " BODY.PEEK[]", "FETCH " + i + " BODY.PEEK[]");
                if (fetch.literals.isEmpty()) {
                    throw new MailException("IMAP: server không trả nội dung thư " + i, -1, false);
                }
                mails.add(MailParser.parse(String.valueOf(i), fetch.literals.get(0)));
            }
            try {
                run(c, "LOGOUT", "LOGOUT");
            } catch (MailException ignored) {
                // Không quan trọng
            }
            log.info("IMAP(Socket): đã tải thành công " + mails.size() + " thư");
            return mails;
        } catch (IOException e) {
            throw MailException.fromIo(e);
        }
    }

    /** Xóa thư trên server IMAP: Gắn cờ \Deleted và phát lệnh EXPUNGE để dọn sạch. */
    public void deleteMail(String id) throws MailException {
        log.info("IMAP(Socket): xóa thư ID " + id);
        tagCounter = 0;
        try (LineConnection c = new LineConnection(cfg.imapHost, cfg.imapPort,
                cfg.imapSecurity == Security.SSL, TIMEOUT_MS)) {
            String greeting = c.readLine();
            if (!greeting.startsWith("* OK")) {
                throw new MailException("IMAP: " + greeting, -1, false);
            }

            run(c, "LOGIN " + quote(cfg.username) + " " + quote(cfg.password),
                    "LOGIN " + quote(cfg.username) + " <password masked>");
            run(c, "SELECT INBOX", "SELECT INBOX");
            run(c, "STORE " + id + " +FLAGS (\\Deleted)", "STORE " + id + " +FLAGS (\\Deleted)");
            run(c, "EXPUNGE", "EXPUNGE");
            try {
                run(c, "LOGOUT", "LOGOUT");
            } catch (MailException ignored) {
            }
            log.info("IMAP(Socket): đã xóa vĩnh viễn thư ID " + id);
        } catch (IOException e) {
            throw MailException.fromIo(e);
        }
    }

    /** Gửi một câu lệnh có gắn tag, đọc phản hồi đến dòng 'tag OK/NO/BAD', gom literal nhị phân {N}. */
    private Response run(LineConnection c, String command, String logText) throws IOException, MailException {
        String tag = "A" + (++tagCounter);
        log.info("C: " + tag + " " + logText);
        c.writeLine(tag + " " + command);

        Response r = new Response();
        String line = c.readLine();
        while (true) {
            Matcher m = LITERAL.matcher(line);
            if (m.find()) {
                r.lines.add(line);
                r.literals.add(c.readBytes(Integer.parseInt(m.group(1))));
                line = c.readLine(); // Đọc phần còn lại của dòng sau khối literal
                continue;
            }
            if (line.startsWith(tag + " ")) {
                r.status = line.substring(tag.length() + 1);
                break;
            }
            r.lines.add(line);
            line = c.readLine();
        }
        log.info("S: " + tag + " " + r.status);
        if (!r.status.startsWith("OK")) {
            throw new MailException("IMAP: " + r.status, -1, r.status.contains("[UNAVAILABLE]"));
        }
        return r;
    }

    private static int parseExists(List<String> lines) throws MailException {
        for (String line : lines) {
            Matcher m = EXISTS.matcher(line);
            if (m.matches()) {
                return Integer.parseInt(m.group(1));
            }
        }
        throw new MailException("IMAP: không tìm thấy số lượng thư (EXISTS) sau khi SELECT", -1, false);
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
