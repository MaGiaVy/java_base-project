package com.zalolite.server;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * [CỔNG/GIAO THỨC] Dịch vụ gửi email mã OTP khôi phục mật khẩu bằng giao thức ESMTP viết tay trên Socket thuần (không dùng thư viện JavaMail).
 * Hỗ trợ 2 chế độ mã hóa chuẩn:
 * - Cổng 587 (STARTTLS): mở TCP Socket thường -> lệnh EHLO -> lệnh STARTTLS -> nâng cấp lên SSLSocket và bắt tay TLS.
 * - Cổng 465 (SMTPS): bọc SSLSocket và bắt tay TLS ngay khi vừa kết nối TCP.
 * Nếu chưa cấu hình mail.user / mail.appPassword trong server.properties, tự chuyển sang chế độ console fallback để demo offline.
 */
public class MailOtpService {
    private static final int TIMEOUT_MS = 10_000;

    private final String host;
    private final int port;
    private final String user;
    private final String appPassword;
    private final boolean useTls;

    /** Khởi tạo MailOtpService từ cấu hình server.properties (mặc định smtp.gmail.com cổng 587 bật TLS). */
    public MailOtpService(ServerConfig cfg) {
        this(cfg.get("mail.host", "smtp.gmail.com"),
                cfg.getInt("mail.port", 587),
                cfg.get("mail.user", ""),
                cfg.get("mail.appPassword", ""),
                true);
    }

    /** Khởi tạo MailOtpService với thông số SMTP tùy chỉnh. */
    public MailOtpService(String host, int port, String user, String appPassword, boolean useTls) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.appPassword = appPassword;
        this.useTls = useTls;
    }

    /** Tạo instance chỉ in mã OTP ra console (dùng cho test hoặc chạy không có cấu hình SMTP). */
    public static MailOtpService consoleOnly() {
        return new MailOtpService("", 587, "", "", false);
    }

    /** [XỬ LÝ] Trả về true nếu chưa cấu hình tài khoản/appPassword SMTP (chạy ở chế độ in OTP ra console). */
    public boolean isConsoleMode() {
        return user == null || user.isBlank() || appPassword == null || appPassword.isBlank();
    }

    /**
     * [CỔNG/GIAO THỨC] [GỬI] [NHẬN] Gửi email chứa mã OTP 6 chữ số tới người dùng theo đầy đủ 8 bước giao thức ESMTP:
     * Banner 220 -> EHLO -> STARTTLS (nếu cổng 587) -> AUTH LOGIN (Base64) -> MAIL FROM -> RCPT TO -> DATA -> QUIT.
     */
    public boolean sendOtp(String toEmail, String code) throws IOException {
        // [XỬ LÝ] Chặn SMTP header injection (\r, \n) trong địa chỉ email đích
        if (toEmail == null || toEmail.contains("\r") || toEmail.contains("\n")) {
            throw new IllegalArgumentException("Email không hợp lệ");
        }
        if (isConsoleMode()) {
            System.out.println("[OTP-CONSOLE] email=" + toEmail + " code=" + code);
            return true;
        }

        // [CỔNG/GIAO THỨC] Mở kết nối TCP tới máy chủ SMTP (cổng 587 hoặc SSLSocket cổng 465)
        Socket sock = openSocket();
        try {
            BufferedReader in = reader(sock);
            BufferedWriter out = writer(sock);

            // [NHẬN] 1. Đọc banner chào 220 từ máy chủ SMTP
            expect(readReply(in), 220);

            // [GỬI] [NHẬN] 2. Gửi lệnh EHLO giới thiệu client và chờ mã 250
            sendLine(out, "EHLO zalolite.local");
            expect(readReply(in), 250);

            // [CỔNG/GIAO THỨC] [XỬ LÝ] 3. STARTTLS (cổng 587): nâng cấp kết nối TCP thường lên TLS rồi EHLO lại
            if (useTls && port != 465) {
                // [GỬI] Yêu cầu máy chủ nâng cấp phiên lên TLS
                sendLine(out, "STARTTLS");
                // [NHẬN] Chờ xác nhận 220 Ready to start TLS
                expect(readReply(in), 220);
                // [XỬ LÝ] Bọc socket hiện tại bằng SSLSocket và thực hiện TLS handshake
                SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                        .createSocket(sock, host, port, true);
                tls.setSoTimeout(TIMEOUT_MS);
                tls.startHandshake();
                sock = tls;
                in = reader(sock);
                out = writer(sock);
                // [GỬI] [NHẬN] Gửi lại EHLO trên kênh đã mã hóa TLS
                sendLine(out, "EHLO zalolite.local");
                expect(readReply(in), 250);
            }

            // [GỬI] [NHẬN] 4. Xác thực tài khoản SMTP bằng AUTH LOGIN (gửi user và appPassword mã hóa Base64)
            sendLine(out, "AUTH LOGIN");
            expect(readReply(in), 334);
            sendLine(out, b64(user));
            expect(readReply(in), 334);
            sendLine(out, b64(appPassword));
            expect(readReply(in), 235);

            // [GỬI] [NHẬN] 5. Khai báo địa chỉ người gửi (MAIL FROM)
            sendLine(out, "MAIL FROM:<" + user + ">");
            expect(readReply(in), 250);

            // [GỬI] [NHẬN] 6. Khai báo địa chỉ người nhận (RCPT TO)
            sendLine(out, "RCPT TO:<" + toEmail + ">");
            int rcptCode = readReply(in);
            if (rcptCode != 250 && rcptCode != 251) {
                throw new IOException("SMTP RCPT TO thất bại: " + rcptCode);
            }

            // [GỬI] [NHẬN] 7. Bắt đầu gửi tiêu đề MIME + nội dung email chứa mã OTP, kết thúc bằng dòng ".\r\n"
            sendLine(out, "DATA");
            expect(readReply(in), 354);
            String subject = "=?UTF-8?B?" + b64("Mã OTP khôi phục mật khẩu ZaloLite") + "?=";
            out.write("From: ZaloLite <" + user + ">\r\n");
            out.write("To: <" + toEmail + ">\r\n");
            out.write("Subject: " + subject + "\r\n");
            out.write("MIME-Version: 1.0\r\n");
            out.write("Content-Type: text/plain; charset=UTF-8\r\n");
            out.write("\r\n");
            out.write("Mã OTP khôi phục mật khẩu ZaloLite của bạn là: " + code + "\r\n");
            out.write("Mã có hiệu lực trong 5 phút. Không chia sẻ mã này cho bất kỳ ai.\r\n");
            out.write(".\r\n");
            out.flush();
            // [NHẬN] Chờ mã 250 xác nhận máy chủ SMTP đã tiếp nhận thư
            expect(readReply(in), 250);

            // [GỬI] 8. Kết thúc phiên SMTP lịch sự bằng lệnh QUIT
            sendLine(out, "QUIT");
            try {
                readReply(in);
            } catch (IOException ignored) {
            }
            return true;
        } finally {
            try {
                sock.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** [CỔNG/GIAO THỨC] Mở kết nối TCP tới host:port; nếu là cổng 465 (SMTPS) thì bọc SSLSocket và handshake TLS ngay. */
    private Socket openSocket() throws IOException {
        if (useTls && port == 465) {
            Socket plain = new Socket();
            plain.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                    .createSocket(plain, host, port, true);
            ssl.setSoTimeout(TIMEOUT_MS);
            ssl.startHandshake();
            return ssl;
        }
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
        s.setSoTimeout(TIMEOUT_MS);
        return s;
    }

    /** Tạo BufferedReader UTF-8 đọc phản hồi từng dòng từ SMTP socket. */
    private static BufferedReader reader(Socket s) throws IOException {
        return new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
    }

    /** Tạo BufferedWriter UTF-8 ghi lệnh SMTP ra socket. */
    private static BufferedWriter writer(Socket s) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
    }

    /** [GỬI] Ghi một dòng lệnh SMTP kết thúc bằng CRLF ("\r\n") theo chuẩn RFC 5321 và flush ngay. */
    private static void sendLine(BufferedWriter out, String line) throws IOException {
        out.write(line);
        out.write("\r\n");
        out.flush();
    }

    /** [NHẬN] Đọc phản hồi SMTP (hỗ trợ cả phản hồi nhiều dòng có dấu '-' ở ký tự thứ 4) và trả về mã 3 chữ số. */
    private static int readReply(BufferedReader in) throws IOException {
        String line;
        int code = -1;
        while ((line = in.readLine()) != null) {
            if (line.length() >= 3) {
                code = Integer.parseInt(line.substring(0, 3));
            }
            if (line.length() < 4 || line.charAt(3) != '-') {
                return code;
            }
        }
        throw new IOException("SMTP đóng kết nối bất ngờ");
    }

    /** [XỬ LÝ] Kiểm tra mã phản hồi SMTP thực tế có khớp mã kỳ vọng hay không; ném IOException nếu sai. */
    private static void expect(int actual, int expected) throws IOException {
        if (actual != expected) {
            throw new IOException("SMTP lỗi: mong đợi " + expected + " nhưng nhận " + actual);
        }
    }

    /** [XỬ LÝ] Mã hóa chuỗi UTF-8 sang Base64 cho bước AUTH LOGIN và tiêu đề Subject MIME. */
    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
