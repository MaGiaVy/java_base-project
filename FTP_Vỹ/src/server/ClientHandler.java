package server;

import common.FtpConstants;
import common.FtpFileEntry;
import common.NetworkUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Luồng độc lập xử lý toàn bộ phiên giao tiếp với một FTP Client (Slide 26, 27, 28)
 */
public class ClientHandler implements Runnable {

    private final FtpSession session;
    private final Path rootDir;
    private final UserManager userManager;
    private final Consumer<String> logger;
    private final Runnable onSessionEnded;

    private BufferedReader reader;
    private BufferedWriter writer;

    public ClientHandler(FtpSession session, Path rootDir, UserManager userManager,
                         Consumer<String> logger, Runnable onSessionEnded) {
        this.session = session;
        this.rootDir = rootDir;
        this.userManager = userManager;
        this.logger = logger != null ? logger : s -> {};
        this.onSessionEnded = onSessionEnded != null ? onSessionEnded : () -> {};
    }

    public FtpSession getSession() {
        return session;
    }

    @Override
    public void run() {
        try {
            Socket socket = session.getControlSocket();
            socket.setSoTimeout(FtpConstants.SOCKET_TIMEOUT_MS);

            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            log("[CONNECT] Client kết nối từ: " + session.getClientAddress() + " (" + session.getSessionId() + ")");
            sendReply(FtpConstants.CODE_220_SERVICE_READY, "SGU FTP Server Ready (RFC 959 Compliant - Sinh vien: Vy)");

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                log("[" + session.getSessionId() + "] >>> " + (line.toUpperCase().startsWith("PASS") ? "PASS ******" : line));
                handleCommand(line);

                if ("QUIT".equalsIgnoreCase(line) || session.getControlSocket().isClosed()) {
                    break;
                }
            }
        } catch (Exception e) {
            log("[" + session.getSessionId() + "] Lỗi hoặc đóng kết nối: " + e.getMessage());
        } finally {
            session.closeAll();
            onSessionEnded.run();
            log("[DISCONNECT] Phiên " + session.getSessionId() + " đã ngắt kết nối.");
        }
    }

    private void sendReply(int code, String message) throws IOException {
        String fullReply = code + " " + message;
        writer.write(fullReply + FtpConstants.CRLF);
        writer.flush();
        log("[" + session.getSessionId() + "] <<< " + fullReply);
    }

    private void handleCommand(String fullLine) throws IOException {
        String[] parts = fullLine.split(" ", 2);
        String cmd = parts[0].toUpperCase();
        String param = parts.length > 1 ? parts[1].trim() : "";

        // Kiểm tra quyền khi chưa đăng nhập
        if (!session.isLoggedIn() && !cmd.equals("USER") && !cmd.equals("PASS") && !cmd.equals("QUIT") && !cmd.equals("SYST") && !cmd.equals("FEAT")) {
            sendReply(FtpConstants.CODE_530_LOGIN_INCORRECT, "Vui lòng đăng nhập với lệnh USER và PASS trước.");
            return;
        }

        switch (cmd) {
            case "USER":
                session.setUsername(param);
                sendReply(FtpConstants.CODE_331_PASSWORD_REQUIRED, "Password required for " + param);
                break;

            case "PASS":
                if (userManager.authenticate(session.getUsername(), param)) {
                    session.setLoggedIn(true);
                    session.setCurrentStatus("Đã đăng nhập (" + session.getUsername() + ")");
                    sendReply(FtpConstants.CODE_230_LOGIN_SUCCESS, "User " + session.getUsername() + " logged in successfully.");
                } else {
                    sendReply(FtpConstants.CODE_530_LOGIN_INCORRECT, "Tên đăng nhập hoặc mật khẩu không chính xác.");
                }
                break;

            case "PWD":
                sendReply(FtpConstants.CODE_257_PATHNAME_CREATED, "\"" + session.getCurrentVirtualPath() + "\" is current directory.");
                break;

            case "CWD":
                handleCwd(param);
                break;

            case "CDUP":
                handleCdup();
                break;

            case "TYPE":
                if ("I".equalsIgnoreCase(param)) {
                    session.setBinaryMode(true);
                    sendReply(FtpConstants.CODE_200_COMMAND_OK, "Type set to I (Binary mode - Slide 19)");
                } else if ("A".equalsIgnoreCase(param)) {
                    session.setBinaryMode(false);
                    sendReply(FtpConstants.CODE_200_COMMAND_OK, "Type set to A (ASCII mode)");
                } else {
                    sendReply(FtpConstants.CODE_500_SYNTAX_ERROR, "Chỉ hỗ trợ TYPE I hoặc TYPE A");
                }
                break;

            case "PASV":
                handlePasv();
                break;

            case "PORT":
                handlePort(param);
                break;

            case "LIST":
                handleList(param);
                break;

            case "RETR":
                handleRetr(param);
                break;

            case "STOR":
                handleStor(param);
                break;

            case "DELE":
                handleDele(param);
                break;

            case "MKD":
                handleMkd(param);
                break;

            case "RMD":
                handleRmd(param);
                break;

            case "RNFR":
                handleRnfr(param);
                break;

            case "RNTO":
                handleRnto(param);
                break;

            case "SIZE":
                handleSize(param);
                break;

            case "SYST":
                sendReply(FtpConstants.CODE_215_SYSTEM_TYPE, "UNIX Type: L8 (SGU Network Programming FTP)");
                break;

            case "FEAT":
                writer.write("211-Features:\r\n UTF8\r\n PASV\r\n SIZE\r\n211 End\r\n");
                writer.flush();
                break;

            case "OPTS":
                if (param.toUpperCase().startsWith("UTF8")) {
                    sendReply(FtpConstants.CODE_200_COMMAND_OK, "Always in UTF8 mode.");
                } else {
                    sendReply(FtpConstants.CODE_200_COMMAND_OK, "OPTS recognized.");
                }
                break;

            case "NOOP":
                sendReply(FtpConstants.CODE_200_COMMAND_OK, "NOOP OK");
                break;

            case "QUIT":
                sendReply(FtpConstants.CODE_221_GOODBYE, "Goodbye, service closing connection.");
                break;

            default:
                sendReply(FtpConstants.CODE_502_COMMAND_NOT_IMPLEMENTED, "Command not implemented: " + cmd);
                break;
        }
    }

    private void handleCwd(String target) throws IOException {
        try {
            Path targetPath = SecurityValidator.resolveSafePath(rootDir, session.getCurrentVirtualPath(), target);
            if (Files.exists(targetPath) && Files.isDirectory(targetPath)) {
                String newVPath = SecurityValidator.toVirtualPath(rootDir, targetPath);
                session.setCurrentVirtualPath(newVPath);
                sendReply(FtpConstants.CODE_250_FILE_ACTION_OK, "Directory successfully changed to " + newVPath);
            } else {
                sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, "Thư mục không tồn tại.");
            }
        } catch (SecurityException se) {
            sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, se.getMessage());
        }
    }

    private void handleCdup() throws IOException {
        String current = session.getCurrentVirtualPath();
        if (current.equals("/") || current.isEmpty()) {
            sendReply(FtpConstants.CODE_250_FILE_ACTION_OK, "Đang ở thư mục gốc, không thể lùi thêm.");
            return;
        }
        int lastSlash = current.lastIndexOf('/');
        String parent = (lastSlash <= 0) ? "/" : current.substring(0, lastSlash);
        session.setCurrentVirtualPath(parent);
        sendReply(FtpConstants.CODE_250_FILE_ACTION_OK, "Directory changed to parent " + parent);
    }

    private void handlePasv() throws IOException {
        session.closePassiveServer();
        ServerSocket ss = new ServerSocket(0); // Cổng ngẫu nhiên hệ thống cấp
        session.setPassiveServerSocket(ss);

        String localIp = NetworkUtils.getLocalIpAddress();
        int localPort = ss.getLocalPort();
        String reply = NetworkUtils.formatPasvReply(localIp, localPort);

        log("[" + session.getSessionId() + "] Mở Passive Data ServerSocket tại cổng: " + localPort);
        writer.write(reply + FtpConstants.CRLF);
        writer.flush();
        log("[" + session.getSessionId() + "] <<< " + reply);
    }

    private void handlePort(String param) throws IOException {
        NetworkUtils.Endpoint ep = NetworkUtils.parsePortCommand(param);
        if (ep != null) {
            session.setActiveEndpoint(ep.ip, ep.port);
            sendReply(FtpConstants.CODE_200_COMMAND_OK, "PORT command successful (" + ep.ip + ":" + ep.port + ")");
        } else {
            sendReply(FtpConstants.CODE_500_SYNTAX_ERROR, "Cú pháp lệnh PORT không đúng (h1,h2,h3,h4,p1,p2).");
        }
    }

    private Socket obtainDataSocket() throws IOException {
        ServerSocket pss = session.getPassiveServerSocket();
        if (pss != null && !pss.isClosed()) {
            pss.setSoTimeout(15000); // 15s chờ Client kết nối vào
            Socket s = pss.accept();
            session.closePassiveServer(); // Chỉ dùng 1 lần theo chuẩn RFC 959
            return s;
        }
        if (session.getActiveIp() != null && session.getActivePort() > 0) {
            return new Socket(session.getActiveIp(), session.getActivePort());
        }
        return null;
    }

    private void handleList(String param) throws IOException {
        Socket dataSocket = null;
        try {
            dataSocket = obtainDataSocket();
            if (dataSocket == null) {
                sendReply(FtpConstants.CODE_425_CANT_OPEN_DATA_CONNECTION, "Không thể mở Data Connection (Hãy gọi PASV hoặc PORT trước).");
                return;
            }

            sendReply(FtpConstants.CODE_150_OPENING_DATA_CONNECTION, "Opening ASCII mode data connection for file list.");
            session.setCurrentStatus("Đang truyền LIST");

            Path targetDir = SecurityValidator.resolveSafePath(rootDir, session.getCurrentVirtualPath(), param.isEmpty() ? "." : param);
            try (BufferedWriter dataWriter = new BufferedWriter(new OutputStreamWriter(dataSocket.getOutputStream(), StandardCharsets.UTF_8))) {
                if (Files.exists(targetDir) && Files.isDirectory(targetDir)) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetDir)) {
                        for (Path p : stream) {
                            boolean isDir = Files.isDirectory(p);
                            long size = isDir ? 0 : Files.size(p);
                            long lastMod = Files.getLastModifiedTime(p).toMillis();
                            FtpFileEntry entry = new FtpFileEntry(p.getFileName().toString(), size, isDir, null, lastMod);
                            dataWriter.write(entry.toUnixListString() + FtpConstants.CRLF);
                        }
                    }
                }
                dataWriter.flush();
            }

            sendReply(FtpConstants.CODE_226_TRANSFER_COMPLETE, "Transfer complete.");
            session.setCurrentStatus("Sẵn sàng");
        } catch (Exception e) {
            sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, "Lỗi khi lấy danh sách: " + e.getMessage());
        } finally {
            if (dataSocket != null && !dataSocket.isClosed()) {
                try { dataSocket.close(); } catch (IOException ignored) {}
            }
        }
    }

    private void handleRetr(String filename) throws IOException {
        Socket dataSocket = null;
        try {
            Path targetFile = SecurityValidator.resolveSafePath(rootDir, session.getCurrentVirtualPath(), filename);
            if (!Files.exists(targetFile) || Files.isDirectory(targetFile)) {
                sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, "Tập tin không tồn tại: " + filename);
                return;
            }

            dataSocket = obtainDataSocket();
            if (dataSocket == null) {
                sendReply(FtpConstants.CODE_425_CANT_OPEN_DATA_CONNECTION, "Can't open data connection.");
                return;
            }

            sendReply(FtpConstants.CODE_150_OPENING_DATA_CONNECTION, "Opening BINARY mode data connection for " + filename);
            session.setCurrentStatus("Đang truyền RETR " + filename);

            try (InputStream fileIn = new BufferedInputStream(Files.newInputStream(targetFile));
                 OutputStream dataOut = new BufferedOutputStream(dataSocket.getOutputStream())) {
                byte[] buf = new byte[FtpConstants.BUFFER_SIZE];
                int n;
                while ((n = fileIn.read(buf)) != -1) {
                    dataOut.write(buf, 0, n);
                }
                dataOut.flush();
            }

            sendReply(FtpConstants.CODE_226_TRANSFER_COMPLETE, "File transfer complete: " + filename);
            session.setCurrentStatus("Sẵn sàng");
        } catch (SecurityException se) {
            sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, se.getMessage());
        } finally {
            if (dataSocket != null && !dataSocket.isClosed()) {
                try { dataSocket.close(); } catch (IOException ignored) {}
            }
        }
    }

    private void handleStor(String filename) throws IOException {
        if (!userManager.canWrite(session.getUsername())) {
            sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, "Tài khoản không có quyền Upload (Ghi).");
            return;
        }

        Socket dataSocket = null;
        try {
            Path targetFile = SecurityValidator.resolveSafePath(rootDir, session.getCurrentVirtualPath(), filename);

            dataSocket = obtainDataSocket();
            if (dataSocket == null) {
                sendReply(FtpConstants.CODE_425_CANT_OPEN_DATA_CONNECTION, "Can't open data connection.");
                return;
            }

            sendReply(FtpConstants.CODE_150_OPENING_DATA_CONNECTION, "Opening BINARY mode data connection for upload " + filename);
            session.setCurrentStatus("Đang nhận STOR " + filename);

            try (InputStream dataIn = new BufferedInputStream(dataSocket.getInputStream());
                 OutputStream fileOut = new BufferedOutputStream(Files.newOutputStream(targetFile))) {
                byte[] buf = new byte[FtpConstants.BUFFER_SIZE];
                int n;
                while ((n = dataIn.read(buf)) != -1) {
                    fileOut.write(buf, 0, n);
                }
                fileOut.flush();
            }

            sendReply(FtpConstants.CODE_226_TRANSFER_COMPLETE, "Upload complete for " + filename);
            session.setCurrentStatus("Sẵn sàng");
        } catch (SecurityException se) {
            sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, se.getMessage());
        } finally {
            if (dataSocket != null && !dataSocket.isClosed()) {
                try { dataSocket.close(); } catch (IOException ignored) {}
            }
        }
    }

    private void handleDele(String filename) throws IOException {
        if (!userManager.canDelete(session.getUsername())) {
            sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, "Tài khoản không có quyền xóa tập tin.");
            return;
        }
        try {
            Path targetFile = SecurityValidator.resolveSafePath(rootDir, session.getCurrentVirtualPath(), filename);
            if (Files.exists(targetFile) && !Files.isDirectory(targetFile)) {
                Files.delete(targetFile);
                sendReply(FtpConstants.CODE_250_FILE_ACTION_OK, "Đã xóa tập tin: " + filename);
            } else {
                sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, "Tập tin không tồn tại.");
            }
        } catch (Exception e) {
            sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, "Không thể xóa: " + e.getMessage());
        }
    }

    private void handleMkd(String folderName) throws IOException {
        if (!userManager.canWrite(session.getUsername())) {
            sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, "Tài khoản không có quyền tạo thư mục.");
            return;
        }
        try {
            Path target = SecurityValidator.resolveSafePath(rootDir, session.getCurrentVirtualPath(), folderName);
            if (Files.exists(target)) {
                sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, "Thư mục đã tồn tại.");
            } else {
                Files.createDirectories(target);
                sendReply(FtpConstants.CODE_257_PATHNAME_CREATED, "\"" + folderName + "\" created.");
            }
        } catch (Exception e) {
            sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, "Không thể tạo thư mục: " + e.getMessage());
        }
    }

    private void handleRmd(String folderName) throws IOException {
        if (!userManager.canDelete(session.getUsername())) {
            sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, "Tài khoản không có quyền xóa thư mục.");
            return;
        }
        try {
            Path target = SecurityValidator.resolveSafePath(rootDir, session.getCurrentVirtualPath(), folderName);
            if (Files.exists(target) && Files.isDirectory(target)) {
                Files.delete(target);
                sendReply(FtpConstants.CODE_250_FILE_ACTION_OK, "Đã xóa thư mục " + folderName);
            } else {
                sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, "Thư mục không tồn tại.");
            }
        } catch (Exception e) {
            sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, "Không thể xóa thư mục (Có thể thư mục không rỗng).");
        }
    }

    private void handleRnfr(String oldName) throws IOException {
        if (!userManager.canWrite(session.getUsername())) {
            sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, "Tài khoản không có quyền đổi tên.");
            return;
        }
        try {
            Path target = SecurityValidator.resolveSafePath(rootDir, session.getCurrentVirtualPath(), oldName);
            if (Files.exists(target)) {
                session.setRenameFromPath(target);
                sendReply(FtpConstants.CODE_350_FILE_ACTION_PENDING, "Ready for destination name (RNTO).");
            } else {
                sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, "Tập tin cần đổi tên không tồn tại.");
            }
        } catch (Exception e) {
            sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, e.getMessage());
        }
    }

    private void handleRnto(String newName) throws IOException {
        if (!userManager.canWrite(session.getUsername())) {
            sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, "Tài khoản không có quyền đổi tên.");
            return;
        }
        Path from = session.getRenameFromPath();
        if (from == null) {
            sendReply(FtpConstants.CODE_500_SYNTAX_ERROR, "Chưa gọi lệnh RNFR trước khi gọi RNTO.");
            return;
        }
        try {
            Path to = SecurityValidator.resolveSafePath(rootDir, session.getCurrentVirtualPath(), newName);
            Files.move(from, to);
            session.setRenameFromPath(null);
            sendReply(FtpConstants.CODE_250_FILE_ACTION_OK, "Đổi tên thành công sang " + newName);
        } catch (Exception e) {
            sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, "Lỗi khi đổi tên: " + e.getMessage());
        }
    }

    private void handleSize(String filename) throws IOException {
        try {
            Path targetFile = SecurityValidator.resolveSafePath(rootDir, session.getCurrentVirtualPath(), filename);
            if (Files.exists(targetFile) && !Files.isDirectory(targetFile)) {
                long size = Files.size(targetFile);
                sendReply(FtpConstants.CODE_213_FILE_STATUS, String.valueOf(size));
            } else {
                sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, "Tập tin không tồn tại hoặc là thư mục.");
            }
        } catch (Exception e) {
            sendReply(FtpConstants.CODE_550_FILE_UNAVAILABLE, "Không thể lấy kích thước: " + e.getMessage());
        }
    }

    private void log(String msg) {
        logger.accept(msg);
    }
}
