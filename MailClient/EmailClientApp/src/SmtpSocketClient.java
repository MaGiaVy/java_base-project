import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** SMTP Client mức thấp qua Socket tuân thủ quy trình 8 bước chuẩn ESMTP (mục 4, 5, 6 của giáo trình). */
public class SmtpSocketClient {
    private static final int TIMEOUT_MS = 15000;

    private static final class Reply {
        final int code;
        final String text;

        Reply(int code, String text) {
            this.code = code;
            this.text = text;
        }
    }

    private final AccountConfig cfg;
    private final MailLog log;

    public SmtpSocketClient(AccountConfig cfg, MailLog log) {
        this.cfg = cfg;
        this.log = log;
    }

    public void send(OutgoingMail mail) throws MailException {
        log.info("SMTP(Socket): kết nối tới " + cfg.smtpHost + ":" + cfg.smtpPort + " (" + cfg.smtpSecurity + ")");
        try (LineConnection c = new LineConnection(cfg.smtpHost, cfg.smtpPort,
                cfg.smtpSecurity == Security.SSL, TIMEOUT_MS)) {
            // 1. Nhận Banner chào mừng (220)
            expect(readReply(c), 220);

            // 2. Chào hỏi EHLO
            String hello = "EHLO " + clientName();
            command(c, hello, hello, 250);

            // 3. Nâng cấp STARTTLS nếu dùng bảo mật STARTTLS (Cổng 587)
            if (cfg.smtpSecurity == Security.STARTTLS) {
                command(c, "STARTTLS", "STARTTLS", 220);
                c.startTls();
                command(c, hello, hello, 250); // EHLO lại sau khi kênh truyền được bảo mật bằng TLS
            }

            // 4. Xác thực AUTH LOGIN
            command(c, "AUTH LOGIN", "AUTH LOGIN", 334);
            command(c, b64(cfg.username), "<username base64>", 334);
            command(c, b64(cfg.password), "<password masked>", 235);

            // 5. Khai báo người gửi (MAIL FROM)
            String from = "MAIL FROM:<" + cfg.username + ">";
            command(c, from, from, 250);

            // 6. Khai báo danh sách người nhận (RCPT TO)
            for (String to : mail.to) {
                String rcpt = "RCPT TO:<" + to + ">";
                command(c, rcpt, rcpt, 250);
            }

            // 7. Bắt đầu truyền dữ liệu DATA và kết thúc bằng \r\n.\r\n
            command(c, "DATA", "DATA", 354);
            String message = MimeBuilder.dotStuff(MimeBuilder.build(cfg.username, mail));
            log.info("C: <nội dung thư MIME " + message.length() + " ký tự>");
            c.write(message + ".\r\n");
            expect(readReply(c), 250);

            // 8. Đóng phiên QUIT
            try {
                command(c, "QUIT", "QUIT", 221);
            } catch (MailException | IOException ignored) {
                // Thư đã được server nhận thành công (250), bỏ qua lỗi đóng phiên
            }
            log.info("SMTP(Socket): gửi thành công tới " + mail.to);
        } catch (IOException e) {
            throw MailException.fromIo(e);
        }
    }

    private Reply command(LineConnection c, String wire, String logText, int expected)
            throws IOException, MailException {
        log.info("C: " + logText);
        c.writeLine(wire);
        Reply reply = readReply(c);
        expect(reply, expected);
        return reply;
    }

    /** Xử lý phản hồi nhiều dòng: "250-..." tiếp tục đọc, "250 ..." là dòng cuối cùng. */
    private Reply readReply(LineConnection c) throws IOException {
        StringBuilder text = new StringBuilder();
        String line;
        while (true) {
            line = c.readLine();
            log.info("S: " + line);
            text.append(line).append('\n');
            if (ReplyCode.isLastLine(line)) {
                break;
            }
        }
        return new Reply(ReplyCode.parse(line), text.toString().trim());
    }

    private static void expect(Reply reply, int expected) throws MailException {
        if (reply.code != expected) {
            throw new MailException("SMTP: " + reply.text, reply.code, ReplyCode.isTransient(reply.code));
        }
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String clientName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            return "localhost";
        }
    }
}
