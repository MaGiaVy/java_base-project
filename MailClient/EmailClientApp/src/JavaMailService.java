import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

/** Dịch vụ gửi, nhận, xóa email và lưu file đính kèm sử dụng thư viện JavaMail API (mục 10 của giáo trình). */
public class JavaMailService implements MailService {
    private final AccountConfig cfg;
    private final MailLog log;
    private final Retrier retrier;

    public JavaMailService(AccountConfig cfg, MailLog log, Retrier retrier) {
        this.cfg = cfg;
        this.log = log;
        this.retrier = retrier;
    }

    static Properties smtpProperties(AccountConfig cfg) {
        Properties props = new Properties();
        props.put("mail.smtp.host", cfg.smtpHost);
        props.put("mail.smtp.port", String.valueOf(cfg.smtpPort));
        props.put("mail.smtp.auth", "true");
        if (cfg.smtpSecurity == Security.SSL) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", String.valueOf(cfg.smtpPort));
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else if (cfg.smtpSecurity == Security.STARTTLS) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        props.put("mail.smtp.ssl.checkserveridentity", "true");
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.mime.encodefilename", "true");
        return props;
    }

    @Override
    public void send(OutgoingMail mail) throws MailException {
        retrier.run("JavaMail SMTP", () -> {
            doSend(mail);
            return null;
        });
    }

    private void doSend(OutgoingMail mail) throws MailException {
        log.info("JavaMail SMTP: gửi tới " + mail.to + " qua " + cfg.smtpHost + ":" + cfg.smtpPort);
        Session session = Session.getInstance(smtpProperties(cfg), new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(cfg.username, cfg.password);
            }
        });
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(cfg.username));
            for (String to : mail.to) {
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            }
            message.setSubject(mail.subject, "UTF-8");

            MimeBodyPart bodyPart = new MimeBodyPart();
            if (mail.html) {
                bodyPart.setContent(mail.body, "text/html; charset=utf-8");
            } else {
                bodyPart.setText(mail.body, "UTF-8");
            }

            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(bodyPart);

            for (File file : mail.attachments) {
                if (file.isDirectory()) {
                    MimeMultipart folderMultipart = new MimeMultipart();
                    addFolderContentsToMultipart(folderMultipart, file, file.getName());
                    MimeBodyPart folderPart = new MimeBodyPart();
                    folderPart.setContent(folderMultipart);
                    folderPart.setFileName(file.getName());
                    multipart.addBodyPart(folderPart);
                } else if (file.isFile()) {
                    MimeBodyPart attachment = new MimeBodyPart();
                    attachment.attachFile(file);
                    multipart.addBodyPart(attachment);
                }
            }
            message.setContent(multipart);

            Transport.send(message);
            log.info("JavaMail SMTP: gửi thành công");
        } catch (MessagingException | IOException e) {
            throw JavaMailErrors.classify(e);
        }
    }

    private static void addFolderContentsToMultipart(MimeMultipart parent, File current, String relativePrefix)
            throws MessagingException, IOException {
        File[] children = current.listFiles();
        if (children == null) return;
        for (File child : children) {
            String pathName = relativePrefix + "/" + child.getName();
            if (child.isDirectory()) {
                addFolderContentsToMultipart(parent, child, pathName);
            } else if (child.isFile()) {
                MimeBodyPart filePart = new MimeBodyPart();
                filePart.attachFile(child);
                filePart.setFileName(pathName);
                parent.addBodyPart(filePart);
            }
        }
    }

    @Override
    public List<ReceivedMail> fetchInbox(ReceiveProtocol protocol, int limit) throws MailException {
        return retrier.run("JavaMail " + protocol, () -> doFetch(protocol, limit));
    }

    private List<ReceivedMail> doFetch(ReceiveProtocol protocol, int limit) throws MailException {
        boolean imap = protocol == ReceiveProtocol.IMAP;
        Security security = imap ? cfg.imapSecurity : cfg.pop3Security;
        String storeName = (imap ? "imap" : "pop3") + (security == Security.SSL ? "s" : "");
        String host = imap ? cfg.imapHost : cfg.pop3Host;
        int port = imap ? cfg.imapPort : cfg.pop3Port;

        Properties props = new Properties();
        props.put("mail." + storeName + ".ssl.checkserveridentity", "true");
        props.put("mail." + storeName + ".connectiontimeout", "15000");
        props.put("mail." + storeName + ".timeout", "15000");
        log.info("JavaMail " + storeName + ": kết nối " + host + ":" + port);

        try (Store store = Session.getInstance(props).getStore(storeName)) {
            store.connect(host, port, cfg.username, cfg.password);
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);
            int total = inbox.getMessageCount();
            List<ReceivedMail> mails = new ArrayList<>();
            for (int i = total; i > Math.max(0, total - limit); i--) {
                mails.add(MailParser.fromMessage(String.valueOf(i), inbox.getMessage(i)));
            }
            inbox.close(false);
            log.info("JavaMail " + storeName + ": đã tải " + mails.size() + " thư");
            return mails;
        } catch (MessagingException | IOException e) {
            throw JavaMailErrors.classify(e);
        }
    }

    @Override
    public void deleteMail(ReceiveProtocol protocol, String id) throws MailException {
        retrier.run("JavaMail Xóa Thư", () -> {
            doDelete(protocol, id);
            return null;
        });
    }

    private void doDelete(ReceiveProtocol protocol, String id) throws MailException {
        boolean imap = protocol == ReceiveProtocol.IMAP;
        Security security = imap ? cfg.imapSecurity : cfg.pop3Security;
        String storeName = (imap ? "imap" : "pop3") + (security == Security.SSL ? "s" : "");
        String host = imap ? cfg.imapHost : cfg.pop3Host;
        int port = imap ? cfg.imapPort : cfg.pop3Port;

        Properties props = new Properties();
        props.put("mail." + storeName + ".ssl.checkserveridentity", "true");
        props.put("mail." + storeName + ".connectiontimeout", "15000");
        props.put("mail." + storeName + ".timeout", "15000");
        log.info("JavaMail " + storeName + ": xóa thư ID " + id);

        try (Store store = Session.getInstance(props).getStore(storeName)) {
            store.connect(host, port, cfg.username, cfg.password);
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            int msgNum = Integer.parseInt(id);
            if (msgNum > 0 && msgNum <= inbox.getMessageCount()) {
                Message msg = inbox.getMessage(msgNum);
                msg.setFlag(Flags.Flag.DELETED, true);
            }
            inbox.close(true); // expunge = true xóa dọn dẹp thư đã đánh dấu DELETED
            log.info("JavaMail: đã xóa thành công thư ID " + id);
        } catch (MessagingException e) {
            throw JavaMailErrors.classify(e);
        }
    }

    @Override
    public void saveAttachment(AttachmentItem item, File targetFile) throws IOException, MailException {
        log.info("JavaMail: lưu file đính kèm " + item.getName() + " -> " + targetFile.getAbsolutePath());
        item.saveTo(targetFile);
    }
}
