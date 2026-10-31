public class Retrier {
    public interface Action<T> {
        T run() throws MailException;
    }

    public interface Sleeper {
        void sleep(long ms) throws InterruptedException;
    }

    private final int maxAttempts;
    private final long baseDelayMs;
    private final Sleeper sleeper;
    private final MailLog log;

    public Retrier(int maxAttempts, long baseDelayMs, Sleeper sleeper, MailLog log) {
        this.maxAttempts = maxAttempts;
        this.baseDelayMs = baseDelayMs;
        this.sleeper = sleeper;
        this.log = log;
    }

    /** 3 lần thử, chờ 1s rồi 2s (nhân đôi mỗi lần thử lại). */
    public static Retrier standard(MailLog log) {
        return new Retrier(3, 1000, Thread::sleep, log);
    }

    public <T> T run(String what, Action<T> action) throws MailException {
        int attempt = 1;
        while (true) {
            try {
                return action.run();
            } catch (MailException e) {
                if (!e.isTransientError() || attempt >= maxAttempts) {
                    throw e;
                }
                long delay = baseDelayMs * (1L << (attempt - 1));
                log.warn(what + ": lỗi tạm thời (lần " + attempt + "/" + maxAttempts
                        + "), thử lại sau " + delay + " ms: " + e.getMessage());
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                attempt++;
            }
        }
    }
}
