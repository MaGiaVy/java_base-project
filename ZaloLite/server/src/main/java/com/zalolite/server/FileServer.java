package com.zalolite.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * [CỔNG/GIAO THỨC] Máy chủ truyền tải tệp nhị phân qua TCP thuần (mặc định cổng 9001).
 * Tách riêng khỏi kênh JSON/TLS (cổng 9000) để không làm nghẽn tin nhắn realtime khi tải ảnh/video/file lớn.
 * Giao thức nhị phân dùng DataInputStream/DataOutputStream:
 * - UPLOAD: Client gửi "UPLOAD", fileName, size -> Server kiểm tra -> trả "OK" -> nhận đủ size byte -> trả "DONE" + storedName.
 * - DOWNLOAD: Client gửi "DOWNLOAD", fileId -> Server kiểm tra -> trả "OK", size rồi stream toàn bộ byte của file.
 * Bảo mật: giới hạn dung lượng tối đa 100MB, chặn path traversal ("..", "/", "\") và xóa file dở nếu upload bị đứt giữa chừng.
 */
public class FileServer implements AutoCloseable {
    /** Giới hạn kích thước tối đa mỗi tệp tải lên: 100 MB. */
    public static final long MAX_FILE_SIZE = 100L * 1024 * 1024;
    /** Kích thước bộ đệm đọc/ghi stream (8 KB). */
    private static final int BUFFER_SIZE = 8192;
    /** Timeout đọc socket mỗi client (30 giây) để tránh treo kết nối. */
    private static final int CLIENT_TIMEOUT_MS = 30_000;

    private final int port;
    private final Path storageDir;
    /** Thread pool đa luồng xử lý đồng thời nhiều phiên upload/download. */
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "file-server-worker");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running;
    private volatile ServerSocket serverSocket;

    /** Khởi tạo FileServer lưu tệp tại thư mục mặc định server/uploads. */
    public FileServer(int port) {
        this(port, Path.of("server/uploads"));
    }

    /** Khởi tạo FileServer với cổng TCP và thư mục lưu trữ chỉ định. */
    public FileServer(int port, Path storageDir) {
        this.port = port;
        this.storageDir = storageDir.toAbsolutePath().normalize();
    }

    /** [CỔNG/GIAO THỨC] Mở ServerSocket và chạy vòng lặp chấp nhận kết nối đồng bộ (chặn luồng gọi). */
    public void start() throws IOException {
        ServerSocket ss = bind();
        acceptLoop(ss);
    }

    /** [CỔNG/GIAO THỨC] Mở ServerSocket và chạy vòng lặp accept trên luồng daemon nền (được ZaloServer.main gọi). */
    public void startAsync() throws IOException {
        ServerSocket ss = bind();
        Thread acceptThread = new Thread(() -> acceptLoop(ss), "file-server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    /** Trả về cổng TCP thực tế FileServer đang lắng nghe. */
    public int getLocalPort() {
        ServerSocket ss = this.serverSocket;
        return ss != null ? ss.getLocalPort() : port;
    }

    /** Dừng FileServer: đóng ServerSocket và ngắt toàn bộ worker thread. */
    public void stop() {
        running = false;
        ServerSocket ss = this.serverSocket;
        if (ss != null) {
            try {
                ss.close();
            } catch (IOException ignored) {
            }
        }
        pool.shutdownNow();
    }

    /** Hỗ trợ try-with-resources (AutoCloseable), gọi lại stop(). */
    @Override
    public void close() {
        stop();
    }

    /** [CỔNG/GIAO THỨC] Tạo thư mục lưu trữ (nếu chưa có) và bind ServerSocket TCP thuần trên cổng port (9001). */
    private synchronized ServerSocket bind() throws IOException {
        Files.createDirectories(storageDir);
        ServerSocket ss = new ServerSocket(port);
        this.serverSocket = ss;
        this.running = true;
        return ss;
    }

    /** [NHẬN] Vòng lặp lắng nghe kết nối TCP mới tới FileServer và đẩy sang thread pool xử lý. */
    private void acceptLoop(ServerSocket ss) {
        while (running && !ss.isClosed()) {
            try {
                // [NHẬN] Chấp nhận kết nối TCP mới từ Client muốn UPLOAD hoặc DOWNLOAD
                Socket client = ss.accept();
                pool.execute(() -> handleClient(client));
            } catch (SocketException e) {
                if (!running) break;
            } catch (IOException e) {
                if (!running) break;
            }
        }
    }

    /** [NHẬN] [XỬ LÝ] Đọc lệnh đầu tiên (UPLOAD / DOWNLOAD) từ Client và điều phối tới hàm xử lý tương ứng. */
    private void handleClient(Socket socket) {
        try (socket;
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), BUFFER_SIZE));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE))) {
            socket.setSoTimeout(CLIENT_TIMEOUT_MS);
            // [NHẬN] Đọc chuỗi lệnh giao thức nhị phân đầu tiên ("UPLOAD" hoặc "DOWNLOAD")
            String cmd = in.readUTF();
            if ("UPLOAD".equals(cmd)) {
                handleUpload(in, out);
            } else if ("DOWNLOAD".equals(cmd)) {
                handleDownload(in, out);
            } else {
                // [GỬI] Báo lỗi nếu Client gửi lệnh lạ
                sendErr(out, "Lệnh không hợp lệ");
            }
        } catch (IOException ignored) {
            // Client disconnected or timed out.
        }
    }

    /**
     * [NHẬN] [XỬ LÝ] [GỬI] Xử lý luồng tải tệp lên (UPLOAD):
     * Đọc metadata -> kiểm tra giới hạn 100MB & chống path traversal -> gửi "OK" -> nhận luồng byte ghi ra đĩa -> gửi "DONE" + storedName.
     */
    private void handleUpload(DataInputStream in, DataOutputStream out) throws IOException {
        // [NHẬN] Đọc tên file gốc và tổng kích thước (byte) do Client khai báo
        String fileName = in.readUTF();
        long size = in.readLong();

        // [XỬ LÝ] Kiểm tra kích thước hợp lệ (1 byte .. 100MB) và kiểm tra tên file chống path traversal
        if (size <= 0 || size > MAX_FILE_SIZE) {
            sendErr(out, "File vượt quá giới hạn 100MB hoặc rỗng");
            return;
        }
        if (isUnsafeToken(fileName)) {
            sendErr(out, "Tên file không hợp lệ");
            return;
        }

        // [XỬ LÝ] Sinh tên lưu trữ duy nhất (UUID + tên đã làm sạch) và xác minh đường dẫn chuẩn hóa nằm trong storageDir
        String storedName = UUID.randomUUID() + "_" + sanitizeName(fileName);
        Path target = storageDir.resolve(storedName).normalize();
        if (!target.startsWith(storageDir) || target.equals(storageDir)) {
            sendErr(out, "Tên file không hợp lệ");
            return;
        }

        Files.createDirectories(storageDir);
        // [GỬI] Gửi tín hiệu "OK" báo cho Client bắt đầu đẩy dữ liệu nhị phân của tệp
        out.writeUTF("OK");
        out.flush();

        // [NHẬN] Đọc từng khối 8KB từ socket cho tới khi đủ đúng `size` byte và ghi ra file trên đĩa
        byte[] buf = new byte[BUFFER_SIZE];
        long remaining = size;
        boolean completed = false;
        try (OutputStream fos = new BufferedOutputStream(Files.newOutputStream(target), BUFFER_SIZE)) {
            while (remaining > 0) {
                int toRead = (int) Math.min(buf.length, remaining);
                // [NHẬN] Đọc chunk byte thô từ TCP InputStream
                int n = in.read(buf, 0, toRead);
                if (n == -1) {
                    throw new EOFException("Client disconnected before finishing upload");
                }
                fos.write(buf, 0, n);
                remaining -= n;
            }
            completed = true;
        } finally {
            // [XỬ LÝ] Dọn file rác nếu kết nối bị ngắt trước khi nhận đủ dung lượng
            if (!completed) {
                Files.deleteIfExists(target);
            }
        }

        // [GỬI] Xác nhận hoàn tất "DONE" kèm tên file đã lưu trên Server để Client gắn vào tin nhắn
        out.writeUTF("DONE");
        out.writeUTF(storedName);
        out.flush();
    }

    /**
     * [NHẬN] [XỬ LÝ] [GỬI] Xử lý luồng tải tệp xuống (DOWNLOAD):
     * Đọc fileId -> kiểm tra path traversal & tồn tại file -> gửi "OK" + size -> stream toàn bộ byte về Client.
     */
    private void handleDownload(DataInputStream in, DataOutputStream out) throws IOException {
        // [NHẬN] Đọc mã định danh file (storedName) Client yêu cầu tải
        String fileId = in.readUTF();
        // [XỬ LÝ] Chặn path traversal (.., /, \, null byte) trước khi ghép đường dẫn
        if (isUnsafeToken(fileId)) {
            sendErr(out, "Đường dẫn không hợp lệ");
            return;
        }

        // [XỬ LÝ] Chuẩn hóa đường dẫn và xác minh tệp nằm gọn bên trong thư mục storageDir và là tệp thường
        Path target = storageDir.resolve(fileId).normalize();
        if (!target.startsWith(storageDir) || target.equals(storageDir)) {
            sendErr(out, "Đường dẫn không hợp lệ");
            return;
        }
        if (!Files.isRegularFile(target)) {
            sendErr(out, "Không tìm thấy file");
            return;
        }

        // [GỬI] Gửi header "OK" cùng tổng kích thước file để Client biết cần đọc bao nhiêu byte
        long size = Files.size(target);
        out.writeUTF("OK");
        out.writeLong(size);

        // [GỬI] Đọc tệp từ đĩa từng khối 8KB và đẩy ra TCP OutputStream về Client
        byte[] buf = new byte[BUFFER_SIZE];
        long remaining = size;
        try (InputStream fis = new BufferedInputStream(Files.newInputStream(target), BUFFER_SIZE)) {
            while (remaining > 0) {
                int toRead = (int) Math.min(buf.length, remaining);
                int n = fis.read(buf, 0, toRead);
                if (n == -1) break;
                // [GỬI] Ghi chunk byte thô xuống socket cho Client
                out.write(buf, 0, n);
                remaining -= n;
            }
        }
        out.flush();
    }

    /** [GỬI] Gửi khung báo lỗi nhị phân ("ERR" + thông báo) rồi flush ngay cho Client. */
    private static void sendErr(DataOutputStream out, String message) throws IOException {
        out.writeUTF("ERR");
        out.writeUTF(message);
        out.flush();
    }

    /** [XỬ LÝ] Kiểm tra chuỗi tên file/fileId có chứa ký tự nguy hiểm gây tấn công Path Traversal hay không. */
    private static boolean isUnsafeToken(String s) {
        return s == null || s.isBlank()
                || s.contains("..") || s.contains("/") || s.contains("\\") || s.indexOf('\0') >= 0;
    }

    /** [XỬ LÝ] Làm sạch tên file gốc (chỉ giữ ký tự an toàn và cắt tối đa 120 ký tự) trước khi lưu xuống đĩa. */
    private static String sanitizeName(String name) {
        String clean = name.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (clean.length() > 120) {
            clean = clean.substring(clean.length() - 120);
        }
        return clean.isEmpty() ? "file" : clean;
    }
}
