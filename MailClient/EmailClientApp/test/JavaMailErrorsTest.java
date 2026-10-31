import static org.junit.jupiter.api.Assertions.*;

import com.sun.mail.smtp.SMTPSendFailedException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.mail.AuthenticationFailedException;
import javax.mail.MessagingException;
import javax.mail.SendFailedException;
import org.junit.jupiter.api.Test;

public class JavaMailErrorsTest {
    @Test
    void authenticationFailureIsPermanent() {
        MailException e = JavaMailErrors.classify(new AuthenticationFailedException("535 bad credentials"));
        assertFalse(e.isTransientError());
    }

    @Test
    void smtp4xxIsTransientAnd5xxIsPermanent() {
        MailException busy = JavaMailErrors.classify(
                new SMTPSendFailedException("RCPT", 450, "Mailbox busy", null, null, null, null));
        assertTrue(busy.isTransientError());
        assertEquals(450, busy.getReplyCode());

        MailException missing = JavaMailErrors.classify(
                new SMTPSendFailedException("RCPT", 550, "No such user", null, null, null, null));
        assertFalse(missing.isTransientError());
        assertEquals(550, missing.getReplyCode());
    }

    @Test
    void otherSendFailureIsPermanent() {
        assertFalse(JavaMailErrors.classify(new SendFailedException("invalid address")).isTransientError());
    }

    @Test
    void networkCausesAreTransient() {
        assertTrue(JavaMailErrors.classify(
                new MessagingException("Could not connect", new SocketTimeoutException("t"))).isTransientError());
        assertTrue(JavaMailErrors.classify(
                new MessagingException("Unknown host", new UnknownHostException("smtp.gmail.com"))).isTransientError());
    }

    @Test
    void unknownErrorIsPermanentAndMailExceptionIsPassedThrough() {
        assertFalse(JavaMailErrors.classify(new MessagingException("lạ")).isTransientError());
        MailException original = new MailException("x", 421, true);
        assertSame(original, JavaMailErrors.classify(original));
    }
}
