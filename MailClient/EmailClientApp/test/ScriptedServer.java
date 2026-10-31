import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Server giả lập kết nối TCP Socket chạy theo kịch bản để phục vụ kiểm thử tự động. */
public class ScriptedServer implements Closeable {
    private final ServerSocket server;
    private final Thread thread;
    private final List<String> received = Collections.synchronizedList(new ArrayList<String>());
    private final List<String> dataLines = Collections.synchronizedList(new ArrayList<String>());
    private final List<String> errors = Collections.synchronizedList(new ArrayList<String>());

    public ScriptedServer(String... script) throws IOException {
        server = new ServerSocket(0);
        server.setSoTimeout(5000);
        thread = new Thread(() -> serve(script));
        thread.setDaemon(true);
        thread.start();
    }

    public int port() {
        return server.getLocalPort();
    }

    public List<String> received() {
        return received;
    }

    public List<String> dataLines() {
        return dataLines;
    }

    private void serve(String[] script) {
        try (Socket s = server.accept()) {
            s.setSoTimeout(5000);
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();
            for (String step : script) {
                if (step.startsWith("S: ")) {
                    out.write((step.substring(3) + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
                    out.flush();
                } else if (step.startsWith("L: ")) {
                    out.write(step.substring(3).getBytes(StandardCharsets.ISO_8859_1));
                    out.flush();
                } else if (step.equals("DATA")) {
                    String line;
                    while (!(line = readLine(in)).equals(".")) {
                        dataLines.add(line);
                    }
                } else if (step.startsWith("C: ")) {
                    String expected = step.substring(3);
                    String actual = readLine(in);
                    received.add(actual);
                    boolean ok = expected.endsWith("*")
                            ? actual.startsWith(expected.substring(0, expected.length() - 1))
                            : expected.equals(actual);
                    if (!ok) {
                        errors.add("mong đợi [" + expected + "] nhưng nhận [" + actual + "]");
                    }
                } else {
                    errors.add("bước kịch bản không hợp lệ: " + step);
                }
            }
        } catch (Exception e) {
            errors.add("lỗi server giả: " + e);
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1 && b != '\n') {
            if (b != '\r') {
                buf.write(b);
            }
        }
        if (b == -1 && buf.size() == 0) {
            throw new EOFException("client đóng kết nối sớm");
        }
        return new String(buf.toByteArray(), StandardCharsets.ISO_8859_1);
    }

    public void assertCompleted() throws InterruptedException {
        thread.join(5000);
        assertTrue(errors.isEmpty(), errors.toString());
        assertTrue(!thread.isAlive(), "kịch bản chưa chạy hết");
    }

    @Override
    public void close() throws IOException {
        server.close();
    }
}
