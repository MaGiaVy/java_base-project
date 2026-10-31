import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Properties;
import javax.mail.Session;
import org.junit.jupiter.api.Test;

public class SmokeTest {
    @Test
    void javaMailIsOnClasspath() {
        assertNotNull(Session.getInstance(new Properties()));
    }
}
