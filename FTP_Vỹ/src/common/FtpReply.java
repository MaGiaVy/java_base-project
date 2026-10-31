package common;

/**
 * Đại diện cho phản hồi từ FTP Server (Mã 3 chữ số + Thông điệp văn bản)
 */
public class FtpReply {
    private final int code;
    private final String message;
    private final String rawLine;

    public FtpReply(int code, String message, String rawLine) {
        this.code = code;
        this.message = message;
        this.rawLine = rawLine;
    }

    public static FtpReply parse(String line) {
        if (line == null || line.trim().isEmpty()) {
            return new FtpReply(-1, "", line);
        }
        String trimmed = line.trim();
        if (trimmed.length() >= 3) {
            try {
                int code = Integer.parseInt(trimmed.substring(0, 3));
                String msg = trimmed.length() > 4 ? trimmed.substring(4) : "";
                return new FtpReply(code, msg, line);
            } catch (NumberFormatException ignored) {}
        }
        return new FtpReply(-1, trimmed, line);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getRawLine() {
        return rawLine;
    }

    public boolean isPositivePreliminary() {
        return code >= 100 && code < 200;
    }

    public boolean isPositiveCompletion() {
        return code >= 200 && code < 300;
    }

    public boolean isPositiveIntermediate() {
        return code >= 300 && code < 400;
    }

    public boolean isTransientNegative() {
        return code >= 400 && code < 500;
    }

    public boolean isPermanentNegative() {
        return code >= 500 && code < 600;
    }

    @Override
    public String toString() {
        return rawLine != null ? rawLine : (code + " " + message);
    }
}
