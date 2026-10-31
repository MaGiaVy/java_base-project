import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ReceivedMail {
    public final String id;
    public final String from;
    public final String subject;
    public final String date;
    public final String body;
    public final boolean html;
    public final List<AttachmentItem> attachments;

    public ReceivedMail(String id, String from, String subject, String date, String body,
                        boolean html, List<AttachmentItem> attachments) {
        this.id = id;
        this.from = from;
        this.subject = subject;
        this.date = date;
        this.body = body;
        this.html = html;
        this.attachments = attachments != null
                ? Collections.unmodifiableList(new ArrayList<>(attachments))
                : Collections.emptyList();
    }

    public List<String> getAttachmentNames() {
        List<String> names = new ArrayList<>();
        for (AttachmentItem a : attachments) {
            names.add(a.getName());
        }
        return names;
    }
}
