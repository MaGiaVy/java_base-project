import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;
import org.junit.jupiter.api.Test;

public class JavaMailServiceTest {
    @Test
    void sslConfigEnablesSslAndAuth() {
        AccountConfig cfg = AccountConfig.gmail("u@gmail.com", "pw");
        Properties p = JavaMailService.smtpProperties(cfg);
        assertEquals("smtp.gmail.com", p.getProperty("mail.smtp.host"));
        assertEquals("465", p.getProperty("mail.smtp.port"));
        assertEquals("true", p.getProperty("mail.smtp.auth"));
        assertEquals("true", p.getProperty("mail.smtp.ssl.enable"));
        assertNull(p.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", p.getProperty("mail.smtp.ssl.checkserveridentity"));
        assertEquals("true", p.getProperty("mail.mime.encodefilename"));
    }

    @Test
    void starttlsConfigRequiresStarttls() {
        AccountConfig cfg = AccountConfig.gmail("u@gmail.com", "pw");
        cfg.smtpPort = 587;
        cfg.smtpSecurity = Security.STARTTLS;
        Properties p = JavaMailService.smtpProperties(cfg);
        assertEquals("587", p.getProperty("mail.smtp.port"));
        assertEquals("true", p.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", p.getProperty("mail.smtp.starttls.required"));
        assertNull(p.getProperty("mail.smtp.ssl.enable"));
    }
}
