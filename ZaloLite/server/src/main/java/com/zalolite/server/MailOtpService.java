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

/** Gửi mã OTP qua ESMTP thuần (STARTTLS 587 / SMTPS 465) hoặc chế độ console fallback khi chưa cấu hình mail. */
public class MailOtpService {
    private static final int TIMEOUT_MS = 10_000;

    private final String host;
    private final int port;
    private final String user;
    private final String appPassword;
    private final boolean useTls;

    public MailOtpService(ServerConfig cfg) {
        this(cfg.get("mail.host", "smtp.gmail.com"),
                cfg.getInt("mail.port", 587),
                cfg.get("mail.user", ""),
                cfg.get("mail.appPassword", ""),
                true);
    }

    public MailOtpService(String host, int port, String user, String appPassword, boolean useTls) {
        this.host = host;
        this.port = port;
        this.user = user;
        this.appPassword = appPassword;
        this.useTls = useTls;
    }

    public static MailOtpService consoleOnly() {
        return new MailOtpService("", 587, "", "", false);
    }

    public boolean isConsoleMode() {
        return user == null || user.isBlank() || appPassword == null || appPassword.isBlank();
    }

    public boolean sendOtp(String toEmail, String code) throws IOException {
        if (toEmail == null || toEmail.contains("\r") || toEmail.contains("\n")) {
            throw new IllegalArgumentException("Email không hợp lệ");
        }
        if (isConsoleMode()) {
            System.out.println("[OTP-CONSOLE] email=" + toEmail + " code=" + code);
            return true;
        }

        Socket sock = openSocket();
        try {
            BufferedReader in = reader(sock);
            BufferedWriter out = writer(sock);

            // 1. Banner 220
            expect(readReply(in), 220);

            // 2. EHLO
            sendLine(out, "EHLO zalolite.local");
            expect(readReply(in), 250);

            // 3. STARTTLS (cổng 587)
            if (useTls && port != 465) {
                sendLine(out, "STARTTLS");
                expect(readReply(in), 220);
                SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                        .createSocket(sock, host, port, true);
                tls.setSoTimeout(TIMEOUT_MS);
                tls.startHandshake();
                sock = tls;
                in = reader(sock);
                out = writer(sock);
                sendLine(out, "EHLO zalolite.local");
                expect(readReply(in), 250);
            }

            // 4. AUTH LOGIN
            sendLine(out, "AUTH LOGIN");
            expect(readReply(in), 334);
            sendLine(out, b64(user));
            expect(readReply(in), 334);
            sendLine(out, b64(appPassword));
            expect(readReply(in), 235);

            // 5. MAIL FROM
            sendLine(out, "MAIL FROM:<" + user + ">");
            expect(readReply(in), 250);

            // 6. RCPT TO
            sendLine(out, "RCPT TO:<" + toEmail + ">");
            int rcptCode = readReply(in);
            if (rcptCode != 250 && rcptCode != 251) {
                throw new IOException("SMTP RCPT TO thất bại: " + rcptCode);
            }

            // 7. DATA
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
            expect(readReply(in), 250);

            // 8. QUIT
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

    private static BufferedReader reader(Socket s) throws IOException {
        return new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
    }

    private static BufferedWriter writer(Socket s) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
    }

    private static void sendLine(BufferedWriter out, String line) throws IOException {
        out.write(line);
        out.write("\r\n");
        out.flush();
    }

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

    private static void expect(int actual, int expected) throws IOException {
        if (actual != expected) {
            throw new IOException("SMTP lỗi: mong đợi " + expected + " nhưng nhận " + actual);
        }
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
