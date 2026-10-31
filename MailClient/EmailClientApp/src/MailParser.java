import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import javax.mail.Address;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;

public final class MailParser {
    private MailParser() {
    }

    public static ReceivedMail parse(String id, byte[] raw) throws MailException {
        try {
            MimeMessage msg = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(raw));
            return fromMessage(id, msg);
        } catch (MessagingException | IOException e) {
            throw new MailException("Không đọc được nội dung thư: " + e.getMessage(), -1, false, e);
        }
    }

    public static ReceivedMail fromMessage(String id, Message msg) throws MessagingException, IOException {
        String from = "";
        Address[] addresses = msg.getFrom();
        if (addresses != null && addresses.length > 0) {
            from = addresses[0] instanceof InternetAddress
                    ? ((InternetAddress) addresses[0]).toUnicodeString()
                    : addresses[0].toString();
        }
        String subject = msg.getSubject() == null ? "" : msg.getSubject();
        Date sent = msg.getSentDate();
        String date = sent == null ? "" : new SimpleDateFormat("dd/MM/yyyy HH:mm").format(sent);

        Collected c = new Collected();
        collect(msg, c);
        boolean html = c.text == null && c.html != null;
        String body = c.text != null ? c.text : (c.html != null ? c.html : "");
        return new ReceivedMail(id, from, subject, date, body, html, c.attachments);
    }

    private static final class Collected {
        String text;
        String html;
        final List<AttachmentItem> attachments = new ArrayList<>();
    }

    private static void collect(Part part, Collected c) throws MessagingException, IOException {
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                collect(mp.getBodyPart(i), c);
            }
        } else {
            String fileName = part.getFileName();
            boolean isAttachment = Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || fileName != null;
            if (isAttachment) {
                String decodedName = fileName == null ? "dinh_kem_khong_ten" : MimeUtility.decodeText(fileName);
                byte[] data = readBytes(part.getInputStream());
                c.attachments.add(new AttachmentItem(decodedName, data, part.getContentType()));
            } else if (part.isMimeType("text/plain")) {
                if (c.text == null) {
                    c.text = String.valueOf(part.getContent());
                }
            } else if (part.isMimeType("text/html")) {
                if (c.html == null) {
                    c.html = String.valueOf(part.getContent());
                }
            }
        }
    }

    private static byte[] readBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] tmp = new byte[4096];
        int r;
        while ((r = in.read(tmp)) != -1) {
            buffer.write(tmp, 0, r);
        }
        return buffer.toByteArray();
    }
}
