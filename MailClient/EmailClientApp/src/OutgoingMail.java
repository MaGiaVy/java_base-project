import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class OutgoingMail {
    private static final Pattern ADDRESS = Pattern.compile("^[^\\s<>@,;]+@[^\\s<>@,;]+$");

    public final List<String> to;
    public final String subject;
    public final String body;
    public final boolean html;
    public final List<File> attachments;

    public OutgoingMail(List<String> to, String subject, String body, boolean html, List<File> attachments) {
        if (to == null || to.isEmpty()) {
            throw new IllegalArgumentException("Cần ít nhất một người nhận");
        }
        for (String address : to) {
            if (!isValidAddress(address)) {
                throw new IllegalArgumentException("Địa chỉ không hợp lệ: " + address);
            }
        }
        List<File> files = attachments == null ? new ArrayList<File>() : new ArrayList<>(attachments);
        for (File f : files) {
            if (!f.exists()) {
                throw new IllegalArgumentException("Không tìm thấy file hoặc thư mục: " + f);
            }
        }
        this.to = Collections.unmodifiableList(new ArrayList<>(to));
        this.subject = subject == null ? "" : subject.replaceAll("[\\r\\n]+", " ");
        this.body = body == null ? "" : body;
        this.html = html;
        this.attachments = Collections.unmodifiableList(files);
    }

    public static boolean isValidAddress(String s) {
        return s != null && ADDRESS.matcher(s).matches();
    }

    public static List<String> splitRecipients(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String part : text.split("[,;]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }
}
