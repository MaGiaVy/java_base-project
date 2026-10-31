import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import javax.mail.internet.MimeUtility;

public final class MimeBuilder {
    private static final String CRLF = "\r\n";

    private MimeBuilder() {
    }

    public static String build(String from, OutgoingMail mail) throws IOException {
        StringBuilder sb = new StringBuilder();
        header(sb, "From", from);
        header(sb, "To", String.join(", ", mail.to));
        header(sb, "Subject", encodeHeader(mail.subject));
        header(sb, "Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()));
        header(sb, "MIME-Version", "1.0");

        String textType = mail.html ? "text/html" : "text/plain";
        byte[] bodyBytes = mail.body.getBytes(StandardCharsets.UTF_8);

        if (mail.attachments.isEmpty()) {
            header(sb, "Content-Type", textType + "; charset=utf-8");
            header(sb, "Content-Transfer-Encoding", "base64");
            sb.append(CRLF);
            sb.append(wrapBase64(bodyBytes));
            return sb.toString();
        }

        String boundary = "----=_Part_Main_" + UUID.randomUUID();
        header(sb, "Content-Type", "multipart/mixed; boundary=\"" + boundary + "\"");
        sb.append(CRLF);

        // Part 1: Nội dung văn bản / HTML
        sb.append("--").append(boundary).append(CRLF);
        header(sb, "Content-Type", textType + "; charset=utf-8");
        header(sb, "Content-Transfer-Encoding", "base64");
        sb.append(CRLF);
        sb.append(wrapBase64(bodyBytes));

        // Part 2..n: Từng file hoặc thư mục đính kèm (hỗ trợ MIME lồng nhau)
        for (File file : mail.attachments) {
            if (file.isDirectory()) {
                appendNestedDirectory(sb, boundary, file);
            } else {
                appendSingleFile(sb, boundary, file, file.getName());
            }
        }
        sb.append("--").append(boundary).append("--").append(CRLF);
        return sb.toString();
    }

    /** Xử lý thư mục đính kèm theo chuẩn MIME Nested (multipart/mixed lồng nhau). */
    private static void appendNestedDirectory(StringBuilder sb, String parentBoundary, File dir) throws IOException {
        String nestedBoundary = "----=_Part_Folder_" + UUID.randomUUID();
        sb.append("--").append(parentBoundary).append(CRLF);
        header(sb, "Content-Type", "multipart/mixed; boundary=\"" + nestedBoundary + "\"");
        header(sb, "Content-Disposition", "attachment; filename=\"" + encodeHeader(dir.getName()) + "\"");
        sb.append(CRLF);

        appendDirContents(sb, nestedBoundary, dir, dir.getName());

        sb.append("--").append(nestedBoundary).append("--").append(CRLF);
    }

    private static void appendDirContents(StringBuilder sb, String boundary, File current, String relativePrefix)
            throws IOException {
        File[] children = current.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            String pathName = relativePrefix + "/" + child.getName();
            if (child.isDirectory()) {
                appendDirContents(sb, boundary, child, pathName);
            } else if (child.isFile()) {
                appendSingleFile(sb, boundary, child, pathName);
            }
        }
    }

    private static void appendSingleFile(StringBuilder sb, String boundary, File file, String displayName)
            throws IOException {
        String type = Files.probeContentType(file.toPath());
        if (type == null) {
            type = "application/octet-stream";
        }
        String encodedName = encodeHeader(displayName);
        sb.append("--").append(boundary).append(CRLF);
        header(sb, "Content-Type", type + "; name=\"" + encodedName + "\"");
        header(sb, "Content-Transfer-Encoding", "base64");
        header(sb, "Content-Disposition", "attachment; filename=\"" + encodedName + "\"");
        sb.append(CRLF);
        sb.append(wrapBase64(Files.readAllBytes(file.toPath())));
    }

    /** Thêm một dấu chấm vào đầu mọi dòng bắt đầu bằng dấu chấm (RFC 5321 mục 4.5.2). */
    public static String dotStuff(String raw) {
        String s = raw.startsWith(".") ? "." + raw : raw;
        return s.replace(CRLF + ".", CRLF + "..");
    }

    /** Base64 chia dòng tối đa 76 ký tự, mỗi dòng kết thúc CRLF. */
    public static String wrapBase64(byte[] data) {
        String raw = Base64.getEncoder().encodeToString(data);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i += 76) {
            out.append(raw, i, Math.min(i + 76, raw.length())).append(CRLF);
        }
        return out.toString();
    }

    /** RFC 2047: Ký tự Unicode thành =?UTF-8?B?...?=. Chuỗi ASCII giữ nguyên. */
    public static String encodeHeader(String text) {
        try {
            return MimeUtility.encodeText(text, "UTF-8", "B");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void header(StringBuilder sb, String name, String value) {
        sb.append(name).append(": ").append(value).append(CRLF);
    }
}
