import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class MailServiceFactoryTest {
    @Test
    void createsServiceMatchingEngine() {
        AccountConfig cfg = AccountConfig.gmail("u@gmail.com", "pw");
        MailLog log = new MailLog(null);
        assertTrue(MailServiceFactory.create(Engine.SOCKET, cfg, log) instanceof SocketMailService);
        assertTrue(MailServiceFactory.create(Engine.JAVAMAIL, cfg, log) instanceof JavaMailService);
    }
}
