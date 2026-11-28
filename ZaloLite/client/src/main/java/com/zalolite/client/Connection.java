package com.zalolite.client;

import com.zalolite.common.Packet;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import javax.swing.SwingUtilities;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * [CỔNG/GIAO THỨC] Kênh điều khiển chính từ Client tới Server qua TLS/SSL (mặc định TCP cổng 9000).
 * Giao thức ứng dụng: NDJSON (mỗi dòng 1 gói JSON {@link Packet} kết thúc bằng '\n').
 * Kiến trúc 1 luồng đọc nền ("server-reader") chạy song song với nhiều luồng gửi đồng bộ:
 *  - Gói phản hồi có {@code reqId} khớp trong {@code pending} -> hoàn thành {@link CompletableFuture} cho hàm {@link #request(Packet)}.
 *  - Gói đẩy chủ động từ server (push event: MSG_NEW, PRESENCE, TYPING, SEEN_UPDATE, CALL_OFFER...)
 *    -> đẩy lên luồng Swing EDT cho toàn bộ listener giao diện xử lý.
 */
public class Connection {
    private final String host;
    private final int port;
    private final int filePort;
    private final SSLSocket socket;
    private final BufferedWriter out;
    // Bộ đếm tăng dần sinh reqId duy nhất để ghép cặp Request - Response bất đồng bộ
    private final AtomicLong seq = new AtomicLong();
    // Bảng lưu các request đang chờ phản hồi theo reqId
    private final Map<Long, CompletableFuture<Packet>> pending = new ConcurrentHashMap<>();
    // Danh sách listener nhận sự kiện push từ server (MainFrame, FriendPanel, ChatPanel...)
    private final List<Consumer<Packet>> listeners = new CopyOnWriteArrayList<>();
    private volatile Runnable onDisconnect;

    /** Khởi tạo kết nối TLS tới cổng chính (mặc định filePort = port + 1, tức 9001). */
    public Connection(String host, int port) throws Exception {
        this(host, port, port + 1);
    }

    /**
     * [CỔNG/GIAO THỨC] Tạo SSLSocket kết nối TCP tới host:port (mặc định :9000),
     * thực hiện bắt tay TLS (handshake) với truststore.p12 và khởi chạy luồng nền {@link #readLoop()}.
     */
    public Connection(String host, int port, int filePort) throws Exception {
        this.host = host;
        this.port = port;
        this.filePort = filePort;
        // [CỔNG/GIAO THỨC] Tạo SSLSocket từ SSLContext chỉ tin chứng chỉ server trong truststore.p12
        socket = (SSLSocket) trustedContext().getSocketFactory().createSocket();
        // Kết nối TCP tới host:port (cổng điều khiển 9000), timeout kết nối 5 giây
        socket.connect(new InetSocketAddress(host, port), 5000);
        // Giới hạn 5 giây cho bước bắt tay TLS để tránh treo khi server không phản hồi
        socket.setSoTimeout(5000);
        socket.startHandshake();
        // Sau khi bắt tay xong, tắt timeout đọc (0 = chờ vô hạn) để giữ kết nối persistent
        socket.setSoTimeout(0);
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        // Tạo luồng nền chuyên đọc dữ liệu từ server liên tục
        Thread reader = new Thread(this::readLoop, "server-reader");
        reader.setDaemon(true);
        reader.start();
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public int getFilePort() { return filePort; }

    /**
     * [CỔNG/GIAO THỨC] Nạp kho chứng chỉ tin cậy (/truststore.p12) đóng gói sẵn trong client.
     * Chỉ tin chứng chỉ công khai (public cert) của server ZaloLite (certificate pinning).
     */
    private static SSLContext trustedContext() throws Exception {
        KeyStore ts = KeyStore.getInstance("PKCS12");
        try (InputStream in = Connection.class.getResourceAsStream("/truststore.p12")) {
            if (in == null) throw new IOException("Thiếu truststore.p12 trong client");
            ts.load(in, "changeit".toCharArray());
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx;
    }

    /**
     * [NHẬN] Vòng lặp đọc liên tục từng dòng JSON từ server trên luồng nền "server-reader".
     * Phân luồng thành 2 nhánh:
     *  1) Trả lời cho request đang chờ (match reqId trong map pending) -> f.complete(p)
     *  2) [PUSH -> UI] Sự kiện đẩy real-time từ server -> gọi các listener trên Swing EDT.
     */
    private void readLoop() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            // [NHẬN] Đọc từng dòng NDJSON từ InputStream của SSLSocket
            while ((line = in.readLine()) != null) {
                Packet p;
                try {
                    p = Packet.fromJson(line);
                } catch (Exception e) {
                    continue;
                }
                // Kiểm tra xem gói tin có phải là RESPONSE cho 1 request đang chờ hay không
                CompletableFuture<Packet> f = p.reqId == null ? null : pending.remove(p.reqId);
                if (f != null) {
                    // [NHẬN] Trả kết quả về cho luồng đang chặn ở request(Packet)
                    f.complete(p);
                } else {
                    // [PUSH -> UI] Sự kiện push từ server -> chuyển sang luồng Swing EDT để cập nhật UI an toàn
                    for (Consumer<Packet> l : listeners) SwingUtilities.invokeLater(() -> l.accept(p));
                }
            }
        } catch (IOException ignored) {
        } finally {
            // Khi mất kết nối: báo lỗi toàn bộ request đang chờ và thông báo callback onDisconnect lên UI
            pending.values().forEach(f -> f.completeExceptionally(new IOException("Mất kết nối server")));
            pending.clear();
            Runnable r = onDisconnect;
            if (r != null) SwingUtilities.invokeLater(r);
        }
    }

    /**
     * [GỬI] Gửi gói tin yêu cầu dạng Request-Response và chờ phản hồi tối đa 10 giây.
     * Tự gán reqId tăng dần, đăng ký Future vào {@code pending}, gửi JSON rồi chờ {@link #readLoop()} đánh thức.
     */
    public Packet request(Packet p) throws Exception {
        p.reqId = seq.incrementAndGet();
        CompletableFuture<Packet> f = new CompletableFuture<>();
        pending.put(p.reqId, f);
        try {
            // [GỬI] Đẩy gói tin JSON xuống SSLSocket
            send(p);
            // [NHẬN] Chờ tối đa 10 giây để readLoop() khớp reqId và hoàn thành Future
            return f.get(10, TimeUnit.SECONDS);
        } finally {
            pending.remove(p.reqId);
        }
    }

    /**
     * [GỬI] Tuần tự hóa {@link Packet} thành chuỗi JSON 1 dòng + '\n' và flush xuống SSLSocket.
     * Dùng {@code synchronized} để tránh nhiều luồng ghi đè nhau trên cùng BufferedWriter.
     */
    public synchronized void send(Packet p) throws IOException {
        out.write(p.toJson());
        out.write('\n');
        out.flush();
    }

    /** Đăng ký listener lắng nghe các gói push từ server (được gọi trên Swing EDT). */
    public void addListener(Consumer<Packet> l) {
        listeners.add(l);
    }

    /** Đăng ký callback xử lý khi kết nối TLS tới server bị ngắt. */
    public void setOnDisconnect(Runnable r) {
        onDisconnect = r;
    }

    /** Đóng chủ động kết nối TLS (không kích hoạt callback onDisconnect). */
    public void close() {
        onDisconnect = null;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
