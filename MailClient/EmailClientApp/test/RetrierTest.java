import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class RetrierTest {
    private final List<Long> sleeps = new ArrayList<>();

    private Retrier retrier() {
        return new Retrier(3, 1000, sleeps::add, new MailLog(null));
    }

    @Test
    void retriesTransientErrorsWithDoublingDelay() throws Exception {
        int[] calls = {0};
        String result = retrier().run("test", () -> {
            calls[0]++;
            if (calls[0] < 3) {
                throw new MailException("busy", 450, true);
            }
            return "ok";
        });
        assertEquals("ok", result);
        assertEquals(3, calls[0]);
        assertEquals(Arrays.asList(1000L, 2000L), sleeps);
    }

    @Test
    void doesNotRetryPermanentErrors() {
        int[] calls = {0};
        MailException e = assertThrows(MailException.class, () -> retrier().run("test", () -> {
            calls[0]++;
            throw new MailException("auth failed", 535, false);
        }));
        assertEquals(535, e.getReplyCode());
        assertEquals(1, calls[0]);
        assertTrue(sleeps.isEmpty());
    }

    @Test
    void givesUpAfterMaxAttempts() {
        int[] calls = {0};
        assertThrows(MailException.class, () -> retrier().run("test", () -> {
            calls[0]++;
            throw new MailException("down", 421, true);
        }));
        assertEquals(3, calls[0]);
        assertEquals(Arrays.asList(1000L, 2000L), sleeps);
    }
}
