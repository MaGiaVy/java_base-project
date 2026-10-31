import com.sun.mail.smtp.SMTPSendFailedException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.mail.AuthenticationFailedException;
import javax.mail.SendFailedException;

public final class JavaMailErrors {
    private JavaMailErrors() {
    }

    public static MailException classify(Exception e) {
        if (e instanceof MailException) {
            return (MailException) e;
        }
        if (e instanceof AuthenticationFailedException) {
            return new MailException("Sai thông tin tài khoản hoặc App Password: " + e.getMessage(), 535, false, e);
        }
        if (e instanceof SMTPSendFailedException) {
            int code = ((SMTPSendFailedException) e).getReturnCode();
            return new MailException("SMTP " + code + ": " + e.getMessage(), code, ReplyCode.isTransient(code), e);
        }
        if (e instanceof SendFailedException) {
            return new MailException("Gửi thất bại: " + e.getMessage(), -1, false, e);
        }
        if (hasNetworkCause(e)) {
            return new MailException("Lỗi mạng: " + e.getMessage(), -1, true, e);
        }
        return new MailException(String.valueOf(e.getMessage()), -1, false, e);
    }

    private static boolean hasNetworkCause(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof SocketException || cur instanceof SocketTimeoutException
                    || cur instanceof UnknownHostException) {
                return true;
            }
        }
        return false;
    }
}
