import java.io.IOException;
import javax.net.ssl.SSLException;

public class MailException extends Exception {
    private final int replyCode;
    private final boolean transientError;

    public MailException(String message, int replyCode, boolean transientError) {
        this(message, replyCode, transientError, null);
    }

    public MailException(String message, int replyCode, boolean transientError, Throwable cause) {
        super(message, cause);
        this.replyCode = replyCode;
        this.transientError = transientError;
    }

    public int getReplyCode() {
        return replyCode;
    }

    /** true = lỗi tạm thời (4xx, lỗi kết nối mạng) nên thử lại; false = lỗi vĩnh viễn (5xx, sai mật khẩu). */
    public boolean isTransientError() {
        return transientError;
    }

    /** Lỗi I/O là tạm thời, ngoại trừ lỗi SSLException (sai chứng chỉ, hỏng handshake) thì không retry. */
    public static MailException fromIo(IOException e) {
        boolean permanent = e instanceof SSLException;
        return new MailException("Lỗi mạng: " + e.getMessage(), -1, !permanent, e);
    }
}
