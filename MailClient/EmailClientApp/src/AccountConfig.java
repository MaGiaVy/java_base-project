public class AccountConfig {
    public final String username;
    public final String password;

    public String smtpHost = "smtp.gmail.com";
    public int smtpPort = 465;
    public Security smtpSecurity = Security.SSL;

    public String pop3Host = "pop.gmail.com";
    public int pop3Port = 995;
    public Security pop3Security = Security.SSL;

    public String imapHost = "imap.gmail.com";
    public int imapPort = 993;
    public Security imapSecurity = Security.SSL;

    public AccountConfig(String username, String password) {
        if (!OutgoingMail.isValidAddress(username)) {
            throw new IllegalArgumentException("Địa chỉ email không hợp lệ: " + username);
        }
        if (password == null || password.isEmpty() || password.contains("\r") || password.contains("\n")) {
            throw new IllegalArgumentException("Mật khẩu (App Password) không hợp lệ");
        }
        this.username = username;
        this.password = password;
    }

    public static AccountConfig gmail(String username, String password) {
        return new AccountConfig(username, password);
    }

    public static AccountConfig custom(String username, String password,
                                       String smtpHost, int smtpPort, Security smtpSec,
                                       String pop3Host, int pop3Port, Security pop3Sec,
                                       String imapHost, int imapPort, Security imapSec) {
        AccountConfig cfg = new AccountConfig(username, password);
        cfg.smtpHost = smtpHost;
        cfg.smtpPort = smtpPort;
        cfg.smtpSecurity = smtpSec;
        cfg.pop3Host = pop3Host;
        cfg.pop3Port = pop3Port;
        cfg.pop3Security = pop3Sec;
        cfg.imapHost = imapHost;
        cfg.imapPort = imapPort;
        cfg.imapSecurity = imapSec;
        return cfg;
    }
}
