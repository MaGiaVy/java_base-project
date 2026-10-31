public final class ReplyCode {
    private ReplyCode() {
    }

    public static int parse(String line) {
        if (line == null || line.length() < 3) {
            return -1;
        }
        try {
            return Integer.parseInt(line.substring(0, 3));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static boolean isSuccess(int code) {
        return code >= 200 && code < 300;
    }

    public static boolean isIntermediate(int code) {
        return code >= 300 && code < 400;
    }

    public static boolean isTransient(int code) {
        return code >= 400 && code < 500;
    }

    public static boolean isPermanent(int code) {
        return code >= 500 && code < 600;
    }

    /** Dòng cuối của reply SMTP nhiều dòng: "250 text" (dấu cách ở vị trí 4) hoặc chỉ "250". */
    public static boolean isLastLine(String line) {
        return line.length() == 3 || (line.length() >= 4 && line.charAt(3) == ' ');
    }
}
