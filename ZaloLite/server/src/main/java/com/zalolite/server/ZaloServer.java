package com.zalolite.server;

import com.zalolite.server.dao.ConversationDao;
import com.zalolite.server.dao.FriendDao;
import com.zalolite.server.dao.MessageDao;
import com.zalolite.server.dao.OtpDao;
import com.zalolite.server.dao.UserDao;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * [CỔNG/GIAO THỨC] Máy chủ trung tâm ZaloLite (điểm khởi chạy chính của toàn bộ backend).
 * Mở 3 dịch vụ mạng song song:
 * 1. Kênh điều khiển & nhắn tin chính: SSLServerSocket trên TCP cổng 9000 (TLSv1.3 / TLSv1.2), mỗi Client 1 luồng (ClientHandler).
 * 2. Máy chủ truyền tệp nhị phân: FileServer trên TCP cổng 9001.
 * 3. Dịch vụ tự động tìm server trong mạng LAN: DiscoveryResponder trên UDP Multicast/Broadcast cổng 9009.
 */
public class ZaloServer {
    private final int port;
    private final SSLContext ssl;
    /** Bảng quản lý phiên đăng nhập online và điểm cuối P2P của tất cả Client. */
    private final SessionRegistry sessions = new SessionRegistry();
    private final UserDao users;
    private final FriendDao friends;
    private final ConversationDao convs;
    private final MessageDao messages;
    private final OtpDao otps;
    private final MailOtpService mailOtp;
    /** Thread pool tự co giãn để phục vụ đồng thời nhiều kết nối ClientHandler. */
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile SSLServerSocket serverSocket;

    /** Khởi tạo ZaloServer ở chế độ chỉ in OTP ra console (thường dùng cho test). */
    public ZaloServer(int port, SSLContext ssl, Db db) {
        this(port, ssl, db, MailOtpService.consoleOnly());
    }

    /** Khởi tạo ZaloServer cùng toàn bộ tầng DAO truy cập PostgreSQL và dịch vụ gửi mail OTP. */
    public ZaloServer(int port, SSLContext ssl, Db db, MailOtpService mailOtp) {
        this.port = port;
        this.ssl = ssl;
        this.users = new UserDao(db);
        this.friends = new FriendDao(db);
        this.convs = new ConversationDao(db);
        this.messages = new MessageDao(db);
        this.otps = new OtpDao(db);
        this.mailOtp = mailOtp != null ? mailOtp : MailOtpService.consoleOnly();
    }

    /**
     * [CỔNG/GIAO THỨC] [XỬ LÝ] Nạp chứng chỉ số PKCS12 (.p12) và khởi tạo SSLContext cho kênh TLS phía Server.
     * @param keystorePath đường dẫn file keystore (VD: server/certs/server.p12)
     * @param password mật khẩu mở khóa keystore và private key
     */
    public static SSLContext loadSsl(String keystorePath, String password) throws Exception {
        // [XỬ LÝ] Đọc chứng chỉ và khóa bí mật từ file PKCS12
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = new FileInputStream(keystorePath)) {
            ks.load(in, password.toCharArray());
        }
        // [XỬ LÝ] Tạo KeyManagerFactory để SSLServerSocket sử dụng khi bắt tay TLS (TLS handshake)
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password.toCharArray());
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    /**
     * [CỔNG/GIAO THỨC] Mở SSLServerSocket lắng nghe kết nối TCP/TLS trên cổng cấu hình (mặc định 9000)
     * và vòng lặp chấp nhận kết nối mới, giao mỗi socket cho một luồng ClientHandler xử lý độc lập.
     */
    public void start() throws Exception {
        // [CỔNG/GIAO THỨC] Bind SSLServerSocket trên cổng TCP (mặc định 9000), chỉ bật TLSv1.3 và TLSv1.2
        serverSocket = (SSLServerSocket) ssl.getServerSocketFactory().createServerSocket(port);
        serverSocket.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
        System.out.println("[INFO] ZaloLite server đang chạy ở cổng " + port + " (TLS)");
        while (!serverSocket.isClosed()) {
            Socket s;
            try {
                // [NHẬN] Chặn chờ kết nối TCP/TLS mới từ Client (3-way TCP handshake + TLS handshake khi đọc/ghi)
                s = serverSocket.accept();
            } catch (Exception e) {
                if (serverSocket.isClosed()) break;
                continue;
            }
            // [XỬ LÝ] Đẩy socket vừa kết nối vào ThreadPool để ClientHandler phục vụ song song
            pool.submit(new ClientHandler(s, sessions, users, friends, convs, messages, otps, mailOtp));
        }
    }

    /** Trả về cổng TCP thực tế mà SSLServerSocket đang lắng nghe (-1 nếu chưa mở). */
    public int getLocalPort() {
        return serverSocket == null ? -1 : serverSocket.getLocalPort();
    }

    /** Dừng máy chủ: đóng SSLServerSocket và ngắt các luồng xử lý Client trong thread pool. */
    public void stop() throws Exception {
        if (serverSocket != null) serverSocket.close();
        pool.shutdownNow();
    }

    /**
     * Điểm vào chương trình Server: đọc cấu hình, khởi tạo PostgreSQL, nạp TLS keystore,
     * khởi động FileServer (TCP 9001), DiscoveryResponder (UDP 9009) và ZaloServer (TLS 9000).
     */
    public static void main(String[] args) throws Exception {
        ServerConfig cfg = ServerConfig.load();
        // [CSDL] Khởi tạo kết nối PostgreSQL và tự tạo database/bảng theo schema.sql nếu chưa có
        Db db = Db.fromConfig(cfg);
        db.init();
        // [CỔNG/GIAO THỨC] Nạp chứng chỉ TLS và đọc các cổng mạng từ server.properties
        SSLContext ssl = loadSsl(cfg.get("keystore.path", "server/certs/server.p12"),
                cfg.get("keystore.password", "zalolite-lab"));
        int tcpPort = cfg.getInt("port", 9000);
        int filePort = cfg.getInt("file.port", 9001);
        int discoveryPort = cfg.getInt("discovery.port", DiscoveryResponder.DEFAULT_DISCOVERY_PORT);

        // [CỔNG/GIAO THỨC] Khởi chạy máy chủ truyền tệp nhị phân TCP (mặc định 9001) ở luồng nền
        FileServer fileServer = new FileServer(filePort);
        fileServer.startAsync();
        // [CỔNG/GIAO THỨC] Khởi chạy bộ phản hồi tìm kiếm LAN qua UDP Multicast/Broadcast (mặc định 9009)
        DiscoveryResponder discovery = new DiscoveryResponder(discoveryPort, tcpPort, filePort);
        discovery.startAsync();
        // [CỔNG/GIAO THỨC] Khởi tạo máy chủ chat chính TCP/TLS (mặc định 9000) và đăng ký ShutdownHook dọn tài nguyên
        ZaloServer server = new ZaloServer(tcpPort, ssl, db, new MailOtpService(cfg));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { server.stop(); } catch (Exception ignored) {}
            fileServer.stop();
            discovery.stop();
        }));
        server.start();
    }
}
