import static org.junit.jupiter.api.Assertions.*;

import java.net.SocketTimeoutException;
import javax.net.ssl.SSLException;
import org.junit.jupiter.api.Test;

public class ReplyCodeTest {
    @Test
    void parsesThreeDigitCode() {
        assertEquals(250, ReplyCode.parse("250 OK"));
        assertEquals(334, ReplyCode.parse("334 VXNlcm5hbWU6"));
    }

    @Test
    void invalidLineGivesMinusOne() {
        assertEquals(-1, ReplyCode.parse("abc"));
        assertEquals(-1, ReplyCode.parse("+OK"));
        assertEquals(-1, ReplyCode.parse("25"));
        assertEquals(-1, ReplyCode.parse(null));
    }

    @Test
    void classifiesCodesLikeSection9() {
        assertTrue(ReplyCode.isSuccess(235));
        assertTrue(ReplyCode.isIntermediate(354));
        assertTrue(ReplyCode.isTransient(421));
        assertTrue(ReplyCode.isTransient(450));
        assertTrue(ReplyCode.isPermanent(535));
        assertTrue(ReplyCode.isPermanent(550));
        assertFalse(ReplyCode.isTransient(550));
        assertFalse(ReplyCode.isPermanent(421));
    }

    @Test
    void detectsLastLineOfMultilineReply() {
        assertFalse(ReplyCode.isLastLine("250-smtp.gmail.com"));
        assertTrue(ReplyCode.isLastLine("250 AUTH LOGIN"));
        assertTrue(ReplyCode.isLastLine("250"));
    }

    @Test
    void ioErrorsAreTransientExceptSsl() {
        assertTrue(MailException.fromIo(new SocketTimeoutException("timeout")).isTransientError());
        assertFalse(MailException.fromIo(new SSLException("bad cert")).isTransientError());
    }

    @Test
    void exceptionKeepsCodeAndFlag() {
        MailException e = new MailException("busy", 450, true);
        assertEquals(450, e.getReplyCode());
        assertTrue(e.isTransientError());
    }
}
