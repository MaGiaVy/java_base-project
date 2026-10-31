public final class MailServiceFactory {
    private MailServiceFactory() {
    }

    public static MailService create(Engine engine, AccountConfig cfg, MailLog log) {
        Retrier retrier = Retrier.standard(log);
        return engine == Engine.SOCKET
                ? new SocketMailService(cfg, log, retrier)
                : new JavaMailService(cfg, log, retrier);
    }
}
