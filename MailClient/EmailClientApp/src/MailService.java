import java.io.File;
import java.io.IOException;
import java.util.List;

public interface MailService {
    /** Gửi email (văn bản, HTML, đa file, thư mục đính kèm). */
    void send(OutgoingMail mail) throws MailException;

    /** Tải danh sách thư mới nhất trong hộp thư đến. */
    List<ReceivedMail> fetchInbox(ReceiveProtocol protocol, int limit) throws MailException;

    /** Xóa thư theo ID trên server (dùng DELE cho POP3, STORE \Deleted + EXPUNGE cho IMAP). */
    void deleteMail(ReceiveProtocol protocol, String id) throws MailException;

    /** Lưu file đính kèm về ổ đĩa cục bộ. */
    void saveAttachment(AttachmentItem item, File targetFile) throws IOException, MailException;
}
