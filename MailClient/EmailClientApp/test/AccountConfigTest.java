import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class AccountConfigTest {
    @Test
    void defaultsAreGmail() {
        AccountConfig c = AccountConfig.gmail("me@gmail.com", "abcdabcdabcdabcd");
        assertEquals("smtp.gmail.com", c.smtpHost);
        assertEquals(465, c.smtpPort);
        assertEquals(Security.SSL, c.smtpSecurity);
        assertEquals("pop.gmail.com", c.pop3Host);
        assertEquals(995, c.pop3Port);
        assertEquals("imap.gmail.com", c.imapHost);
        assertEquals(993, c.imapPort);
    }

    @Test
    void customServerConfiguration() {
        AccountConfig c = AccountConfig.custom("user@sgu.edu.vn", "password123",
                "mail.sgu.edu.vn", 25, Security.NONE,
                "mail.sgu.edu.vn", 110, Security.NONE,
                "mail.sgu.edu.vn", 143, Security.NONE);
        assertEquals("mail.sgu.edu.vn", c.smtpHost);
        assertEquals(25, c.smtpPort);
        assertEquals(Security.NONE, c.smtpSecurity);
    }

    @Test
    void rejectsBadUsernameOrPassword() {
        assertThrows(IllegalArgumentException.class, () -> new AccountConfig("khong-phai-email", "x"));
        assertThrows(IllegalArgumentException.class, () -> new AccountConfig("a@x.com", ""));
        assertThrows(IllegalArgumentException.class, () -> new AccountConfig("a@x.com", "pw\r\nQUIT"));
    }
}
