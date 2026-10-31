import java.io.File;
import java.io.IOException;
import java.util.List;

/** Cài đặt MailService hoàn toàn bằng Socket thuần (SMTP, POP3, IMAP tự phát triển), có log và retry. */
public class SocketMailService implements MailService {
    private final AccountConfig cfg;
    private final MailLog log;
    private final Retrier retrier;

    public SocketMailService(AccountConfig cfg, MailLog log, Retrier retrier) {
        this.cfg = cfg;
        this.log = log;
        this.retrier = retrier;
    }

    @Override
    public void send(OutgoingMail mail) throws MailException {
        retrier.run("Socket SMTP", () -> {
            new SmtpSocketClient(cfg, log).send(mail);
            return null;
        });
    }

    @Override
    public List<ReceivedMail> fetchInbox(ReceiveProtocol protocol, int limit) throws MailException {
        return retrier.run("Socket " + protocol, () -> protocol == ReceiveProtocol.POP3
                ? new Pop3SocketClient(cfg, log).fetchLatest(limit)
                : new ImapSocketClient(cfg, log).fetchLatest(limit));
    }

    @Override
    public void deleteMail(ReceiveProtocol protocol, String id) throws MailException {
        retrier.run("Socket Xóa Thư " + protocol, () -> {
            if (protocol == ReceiveProtocol.POP3) {
                new Pop3SocketClient(cfg, log).deleteMail(id);
            } else {
                new ImapSocketClient(cfg, log).deleteMail(id);
            }
            return null;
        });
    }

    @Override
    public void saveAttachment(AttachmentItem item, File targetFile) throws IOException, MailException {
        log.info("SocketMailService: lưu file đính kèm " + item.getName() + " -> " + targetFile.getAbsolutePath());
        item.saveTo(targetFile);
    }
}
