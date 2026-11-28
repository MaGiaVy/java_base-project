package com.zalolite.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * [CỔNG/GIAO THỨC] Truyền file trực tiếp ngang hàng (Peer-to-Peer) giữa 2 client qua TCP Socket.
 * - Phía nhận: mở {@link ServerSocket}(0) trên cổng TCP ngẫu nhiên của hệ điều hành, rồi báo cổng này
 *   lên server bằng gói {@code P2P_REGISTER}.
 * - Phía gửi: hỏi IP + cổng P2P của đối phương qua server ({@code P2P_ENDPOINT}), sau đó kết nối TCP
 *   trực tiếp ({@link #sendDirect}) với timeout 1.5s; nếu lỗi/khác mạng sẽ tự fallback sang FileServer (:9001).
 */
public class P2pTransfer {
    public static final int CONNECT_TIMEOUT_MS = 1500;
    public static final int READ_TIMEOUT_MS = 15000;
    public static final long MAX_FILE_SIZE = 100L * 1024 * 1024;

    private static final int BUFFER_SIZE = 8192;
    private static final String HEADER_MAGIC = "P2P_FILE";

    public interface ProgressListener {
        void onProgress(long transferred, long total);
    }

    public record ReceivedP2pFile(
            long convId,
            long senderId,
            String kind,
            File savedFile,
            String fileName,
            long fileSize,
            int durationSec) {}

    private final Path cacheDir;
    private final Consumer<ReceivedP2pFile> onReceived;
    private ServerSocket serverSocket;
    private ExecutorService pool;

    public P2pTransfer(Path cacheDir, Consumer<ReceivedP2pFile> onReceived) {
        this.cacheDir = cacheDir.toAbsolutePath().normalize();
        this.onReceived = onReceived;
    }

    public P2pTransfer(Consumer<ReceivedP2pFile> onReceived) {
        this(Path.of(System.getProperty("java.io.tmpdir"), "zalolite-cache"), onReceived);
    }

    /**
     * [CỔNG/GIAO THỨC] Mở ServerSocket(0) (cổng TCP tạm do OS cấp) để lắng nghe file P2P đến
     * và khởi chạy luồng chấp nhận kết nối {@link #acceptLoop(ServerSocket)}.
     */
    public synchronized void start() throws IOException {
        if (serverSocket != null && !serverSocket.isClosed()) {
            return;
        }
        Files.createDirectories(cacheDir);
        // [CỔNG/GIAO THỨC] Bind cổng TCP ngẫu nhiên (0 = OS tự chọn cổng trống)
        serverSocket = new ServerSocket(0);
        pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "zalolite-p2p");
            t.setDaemon(true);
            return t;
        });
        ServerSocket ss = serverSocket;
        pool.submit(() -> acceptLoop(ss));
    }

    /** Trả về số cổng TCP cục bộ đang lắng nghe P2P (để client đăng ký lên server). */
    public synchronized int getListenPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : -1;
    }

    /** Dừng ServerSocket lắng nghe P2P và giải phóng thread pool. */
    public synchronized void stop() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            serverSocket = null;
        }
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
    }

    /** [NHẬN] Vòng lặp accept() các kết nối TCP P2P đến, mỗi peer xử lý trên một luồng riêng. */
    private void acceptLoop(ServerSocket ss) {
        while (!ss.isClosed()) {
            try {
                Socket client = ss.accept();
                ExecutorService currentPool = this.pool;
                if (currentPool != null && !currentPool.isShutdown()) {
                    currentPool.submit(() -> handleIncoming(client));
                } else {
                    client.close();
                }
            } catch (IOException e) {
                if (ss.isClosed()) {
                    break;
                }
            }
        }
    }

    /**
     * [NHẬN] Xử lý 1 phiên nhận file P2P trực tiếp từ peer khác:
     * Đọc magic "P2P_FILE" + metadata (convId, senderId, kind, fileName, size, durationSec),
     * gửi ACK "OK", đọc từng khối 8KB ghi vào thư mục cache, gọi callback {@code onReceived},
     * rồi gửi xác nhận "DONE".
     */
    private void handleIncoming(Socket socket) {
        Path target = null;
        try (socket) {
            socket.setSoTimeout(READ_TIMEOUT_MS);
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), BUFFER_SIZE));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE));

            // [NHẬN] Kiểm tra chuỗi nhận diện giao thức P2P
            if (!HEADER_MAGIC.equals(in.readUTF())) {
                out.writeUTF("ERR");
                out.flush();
                return;
            }

            // [NHẬN] Đọc metadata của file gửi đến
            long convId = in.readLong();
            long senderId = in.readLong();
            String kind = in.readUTF();
            String rawFileName = in.readUTF();
            long size = in.readLong();
            int durationSec = in.readInt();

            if (size <= 0 || size > MAX_FILE_SIZE) {
                out.writeUTF("ERR");
                out.flush();
                return;
            }

            // Làm sạch tên file và kiểm tra chống Path Traversal để chắc chắn ghi trong cacheDir
            String cleanName = sanitizeFileName(rawFileName);
            Files.createDirectories(cacheDir);
            target = cacheDir.resolve("p2p_" + UUID.randomUUID() + "_" + cleanName).normalize();
            if (!target.startsWith(cacheDir) || !cacheDir.equals(target.getParent())) {
                target = null;
                out.writeUTF("ERR");
                out.flush();
                return;
            }

            // [GỬI] Báo "OK" cho peer bắt đầu truyền nội dung file
            out.writeUTF("OK");
            out.flush();

            // [NHẬN] Đọc luồng byte theo từng chunk 8KB ghi ra file cục bộ
            byte[] buf = new byte[BUFFER_SIZE];
            long remaining = size;
            try (OutputStream fileOut = new BufferedOutputStream(Files.newOutputStream(target), BUFFER_SIZE)) {
                while (remaining > 0) {
                    int n = in.read(buf, 0, (int) Math.min(buf.length, remaining));
                    if (n == -1) {
                        throw new EOFException("Unexpected EOF during P2P receive");
                    }
                    fileOut.write(buf, 0, n);
                    remaining -= n;
                }
            }

            // [PUSH -> UI] Thông báo cho MainFrame đã nhận xong file P2P để hiển thị ngay vào khung chat
            if (onReceived != null) {
                onReceived.accept(new ReceivedP2pFile(
                        convId, senderId, kind, target.toFile(), cleanName, size, durationSec));
            }

            // [GỬI] Gửi xác nhận "DONE" cho bên gửi
            out.writeUTF("DONE");
            out.flush();
        } catch (IOException e) {
            if (target != null) {
                try {
                    Files.deleteIfExists(target);
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** Làm sạch tên file P2P nhận được (bỏ đường dẫn, ký tự lạ, ".."). */
    public static String sanitizeFileName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "file.bin";
        }
        String normalized = raw.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String base = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        base = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        base = base.replaceAll("\\.{2,}", "_");
        base = base.replaceAll("^\\.+", "");
        if (base.isEmpty() || ".".equals(base) || "..".equals(base)) {
            return "file.bin";
        }
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }

    /**
     * [GỬI] Kết nối TCP trực tiếp tới IP:port P2P của người nhận và truyền file không qua FileServer.
     * Quy trình: gửi "P2P_FILE" + metadata -> chờ "OK" -> stream file 8KB/chunk -> chờ "DONE".
     */
    public static void sendDirect(
            String ip,
            int port,
            long convId,
            long senderId,
            String kind,
            File file,
            int durationSec,
            ProgressListener listener) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("File does not exist: " + file);
        }
        long size = file.length();
        if (size <= 0 || size > MAX_FILE_SIZE) {
            throw new IOException("Invalid file size: " + size);
        }

        // [CỔNG/GIAO THỨC] Mở TCP Socket trực tiếp tới IP:port lắng nghe của peer nhận (timeout 1.5s)
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);

            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE));
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), BUFFER_SIZE));

            // [GỬI] Gửi header magic và metadata của file tới peer
            out.writeUTF(HEADER_MAGIC);
            out.writeLong(convId);
            out.writeLong(senderId);
            out.writeUTF(kind != null ? kind : "FILE");
            out.writeUTF(file.getName());
            out.writeLong(size);
            out.writeInt(durationSec);
            out.flush();

            // [NHẬN] Chờ peer trả lời "OK"
            String ack = in.readUTF();
            if (!"OK".equals(ack)) {
                throw new IOException("P2P receiver rejected transfer: " + ack);
            }

            // [GỬI] Đọc file cục bộ và đẩy từng khối 8KB trực tiếp sang peer
            byte[] buf = new byte[BUFFER_SIZE];
            long transferred = 0;
            try (FileInputStream fileIn = new FileInputStream(file)) {
                int n;
                while (transferred < size && (n = fileIn.read(buf, 0, (int) Math.min(buf.length, size - transferred))) != -1) {
                    out.write(buf, 0, n);
                    transferred += n;
                    if (listener != null) {
                        listener.onProgress(transferred, size);
                    }
                }
            }
            if (transferred != size) {
                throw new EOFException("File truncated during send: " + transferred + " / " + size);
            }
            out.flush();

            // [NHẬN] Chờ peer xác nhận "DONE" sau khi ghi xong toàn bộ file
            String done = in.readUTF();
            if (!"DONE".equals(done)) {
                throw new IOException("P2P receiver did not confirm completion: " + done);
            }
        }
    }
}
