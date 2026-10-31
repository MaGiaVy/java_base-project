import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** Quản lý Socket kết nối TCP/SSL và đọc ghi dòng CRLF an toàn byte-level (ISO_8859_1). */
public class LineConnection implements Closeable {
    private final String host;
    private final int timeoutMs;
    private Socket socket;
    private InputStream in;
    private OutputStream out;

    public LineConnection(String host, int port, boolean implicitTls, int timeoutMs) throws IOException {
        this.host = host;
        this.timeoutMs = timeoutMs;
        Socket plain = new Socket();
        plain.connect(new InetSocketAddress(host, port), timeoutMs);
        plain.setSoTimeout(timeoutMs);
        attach(implicitTls ? wrapTls(plain) : plain);
    }

    private SSLSocket wrapTls(Socket plain) throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket ssl = (SSLSocket) factory.createSocket(plain, host, plain.getPort(), true);
        SSLParameters params = ssl.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS"); // Xác thực hostname trong chứng chỉ số
        ssl.setSSLParameters(params);
        ssl.setSoTimeout(timeoutMs);
        ssl.startHandshake();
        return ssl;
    }

    private void attach(Socket s) throws IOException {
        this.socket = s;
        this.in = new BufferedInputStream(s.getInputStream());
        this.out = new BufferedOutputStream(s.getOutputStream());
    }

    /** Nâng cấp kết nối TCP đang mở thành TLS (sau khi nhận phản hồi 220 cho lệnh STARTTLS). */
    public void startTls() throws IOException {
        attach(wrapTls(socket));
    }

    public String readLine() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != '\n') {
            buf.write(b);
        }
        if (b == -1 && buf.size() == 0) {
            throw new EOFException("Server đã đóng kết nối");
        }
        byte[] raw = buf.toByteArray();
        int len = raw.length;
        if (len > 0 && raw[len - 1] == '\r') {
            len--;
        }
        return new String(raw, 0, len, StandardCharsets.ISO_8859_1);
    }

    public byte[] readBytes(int n) throws IOException {
        byte[] data = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(data, off, n - off);
            if (r < 0) {
                throw new EOFException("Server đóng kết nối khi đang truyền dữ liệu");
            }
            off += r;
        }
        return data;
    }

    public void writeLine(String line) throws IOException {
        write(line + "\r\n");
    }

    public void write(String raw) throws IOException {
        out.write(raw.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
