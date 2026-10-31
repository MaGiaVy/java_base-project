package client;

import common.FtpConstants;
import common.FtpFileEntry;
import common.FtpReply;
import common.NetworkUtils;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lõi FTP Client độc lập xây dựng bằng Java TCP Socket (Slide 14, 15, 17, 20-24)
 * Tuân thủ nghiêm ngặt RFC 959, sẵn sàng kết nối cả Java FTP Server và Windows IIS
 */
public class FtpClient {

    private Socket controlSocket;
    private BufferedReader reader;
    private BufferedWriter writer;

    private String currentServerHost;
    private int currentServerPort;
    private String currentWorkingDir = "/";
    private boolean loggedIn = false;

    private final Consumer<String> logger;

    public FtpClient(Consumer<String> logger) {
        this.logger = logger != null ? logger : s -> {};
    }

    public synchronized boolean connect(String host, int port) throws IOException {
        disconnect();

        this.currentServerHost = host;
        this.currentServerPort = port;

        log("[CLIENT] Đang kết nối đến " + host + ":" + port + "...");
        controlSocket = new Socket(host, port);
        controlSocket.setSoTimeout(FtpConstants.SOCKET_TIMEOUT_MS);

        reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream(), StandardCharsets.UTF_8));

        FtpReply reply = readReply();
        if (reply.getCode() == FtpConstants.CODE_220_SERVICE_READY) {
            log("[CLIENT] Kết nối thành công! Server đã sẵn sàng.");
            return true;
        }
        return false;
    }

    public synchronized boolean login(String username, String password) throws IOException {
        if (controlSocket == null || controlSocket.isClosed()) {
            throw new IOException("Chưa kết nối tới FTP Server!");
        }

        sendCommand("USER " + username);
        FtpReply userReply = readReply();

        if (userReply.getCode() == FtpConstants.CODE_331_PASSWORD_REQUIRED) {
            sendCommand("PASS " + password);
            FtpReply passReply = readReply();
            if (passReply.getCode() == FtpConstants.CODE_230_LOGIN_SUCCESS) {
                loggedIn = true;
                pwd(); // Lấy đường dẫn ban đầu
                return true;
            }
        } else if (userReply.getCode() == FtpConstants.CODE_230_LOGIN_SUCCESS) {
            loggedIn = true;
            pwd();
            return true;
        }

        return false;
    }

    public synchronized void disconnect() {
        if (controlSocket == null || controlSocket.isClosed()) {
            loggedIn = false;
            currentWorkingDir = "/";
            return;
        }

        if (loggedIn) {
            loggedIn = false; // Đặt false ngay lập tức để chặn tuyệt đối vòng lặp đệ quy
            try {
                if (writer != null) {
                    writer.write("QUIT" + FtpConstants.CRLF);
                    writer.flush();
                    log(">>> QUIT");
                }
                if (reader != null) {
                    String line = reader.readLine();
                    if (line != null) {
                        log("<<< " + line);
                    }
                }
            } catch (Exception ignored) {}
        }

        closeSocketSilently();
    }

    private void closeSocketSilently() {
        loggedIn = false;
        if (controlSocket != null) {
            try { controlSocket.close(); } catch (IOException ignored) {}
            controlSocket = null;
        }
        currentWorkingDir = "/";
    }

    public synchronized String pwd() throws IOException {
        sendCommand("PWD");
        FtpReply reply = readReply();
        if (reply.getCode() == FtpConstants.CODE_257_PATHNAME_CREATED) {
            String msg = reply.getMessage();
            int start = msg.indexOf('"');
            int end = msg.lastIndexOf('"');
            if (start != -1 && end > start) {
                currentWorkingDir = msg.substring(start + 1, end);
            }
        }
        return currentWorkingDir;
    }

    public synchronized boolean cwd(String path) throws IOException {
        sendCommand("CWD " + path);
        FtpReply reply = readReply();
        if (reply.getCode() == FtpConstants.CODE_250_FILE_ACTION_OK) {
            pwd();
            return true;
        }
        return false;
    }

    public synchronized boolean cdup() throws IOException {
        sendCommand("CDUP");
        FtpReply reply = readReply();
        if (reply.getCode() == FtpConstants.CODE_200_COMMAND_OK || reply.getCode() == FtpConstants.CODE_250_FILE_ACTION_OK) {
            pwd();
            return true;
        }
        return false;
    }

    public synchronized boolean setBinaryMode() throws IOException {
        sendCommand("TYPE I");
        FtpReply reply = readReply();
        return reply.getCode() == FtpConstants.CODE_200_COMMAND_OK;
    }

    public synchronized NetworkUtils.Endpoint enterPassiveMode() throws IOException {
        sendCommand("PASV");
        FtpReply reply = readReply();
        if (reply.getCode() == FtpConstants.CODE_227_ENTERING_PASSIVE_MODE) {
            NetworkUtils.Endpoint ep = NetworkUtils.parsePasvReply(reply.getRawLine());
            if (ep != null) {
                // Nếu Server trả về IP 0.0.0.0 hoặc IP private không định tuyến được, dùng host đang kết nối
                String ip = ep.ip;
                if ("0.0.0.0".equals(ip) || "127.0.0.1".equals(ip)) {
                    ip = currentServerHost;
                }
                return new NetworkUtils.Endpoint(ip, ep.port);
            }
        }
        throw new IOException("Không thể bật Passive Mode: " + reply);
    }

    public synchronized List<FtpFileEntry> listFiles(String path) throws IOException {
        NetworkUtils.Endpoint ep = enterPassiveMode();
        log("[CLIENT] Đang mở Data Connection tới " + ep.ip + ":" + ep.port + "...");

        try (Socket dataSocket = new Socket(ep.ip, ep.port)) {
            dataSocket.setSoTimeout(FtpConstants.DATA_SOCKET_TIMEOUT_MS);

            String cmd = (path == null || path.isEmpty()) ? "LIST" : ("LIST " + path);
            sendCommand(cmd);

            FtpReply reply150 = readReply();
            if (reply150.getCode() != FtpConstants.CODE_150_OPENING_DATA_CONNECTION && reply150.getCode() != 125) {
                throw new IOException("Server từ chối mở Data Connection: " + reply150);
            }

            List<String> rawLines = new ArrayList<>();
            try (BufferedReader dataReader = new BufferedReader(new InputStreamReader(dataSocket.getInputStream(), StandardCharsets.UTF_8))) {
                String dLine;
                while ((dLine = dataReader.readLine()) != null) {
                    rawLines.add(dLine);
                }
            }

            FtpReply reply226 = readReply();
            if (reply226.getCode() != FtpConstants.CODE_226_TRANSFER_COMPLETE && reply226.getCode() != 250) {
                log("[CLIENT] Cảnh báo mã phản hồi sau LIST: " + reply226);
            }

            return SmartListParser.parseListing(rawLines);
        }
    }

    public synchronized long getFileSize(String remoteFilename) {
        try {
            sendCommand("SIZE " + remoteFilename);
            FtpReply reply = readReply();
            if (reply.getCode() == FtpConstants.CODE_213_FILE_STATUS) {
                return Long.parseLong(reply.getMessage().trim());
            }
        } catch (Exception ignored) {}
        return -1;
    }

    public synchronized boolean downloadFile(String remoteFilename, File localDestFile, TransferProgressListener listener) throws IOException {
        long estimatedSize = getFileSize(remoteFilename);
        setBinaryMode();
        NetworkUtils.Endpoint ep = enterPassiveMode();

        log("[CLIENT] Chuẩn bị tải xuống (RETR) " + remoteFilename + " qua " + ep.ip + ":" + ep.port);

        try (Socket dataSocket = new Socket(ep.ip, ep.port)) {
            dataSocket.setSoTimeout(FtpConstants.DATA_SOCKET_TIMEOUT_MS);

            sendCommand("RETR " + remoteFilename);
            FtpReply reply150 = readReply();
            if (reply150.getCode() != FtpConstants.CODE_150_OPENING_DATA_CONNECTION && reply150.getCode() != 125) {
                throw new IOException("Không thể tải file: " + reply150);
            }

            // Tạo thư mục cha nếu chưa có
            File parent = localDestFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            long totalBytes = estimatedSize;
            long transferred = 0;
            long startTime = System.currentTimeMillis();

            try (InputStream dataIn = new BufferedInputStream(dataSocket.getInputStream());
                 OutputStream fileOut = new BufferedOutputStream(new FileOutputStream(localDestFile))) {

                byte[] buf = new byte[FtpConstants.BUFFER_SIZE];
                int n;
                while ((n = dataIn.read(buf)) != -1) {
                    fileOut.write(buf, 0, n);
                    transferred += n;

                    if (listener != null) {
                        double elapsedSec = Math.max(0.001, (System.currentTimeMillis() - startTime) / 1000.0);
                        double speedKbps = (transferred / 1024.0) / elapsedSec;
                        listener.onProgress(transferred, totalBytes, speedKbps);
                    }
                }
                fileOut.flush();
            }

            FtpReply reply226 = readReply();
            log("[CLIENT] " + reply226.getMessage());
            return reply226.getCode() == FtpConstants.CODE_226_TRANSFER_COMPLETE || reply226.getCode() == 250;
        }
    }

    public synchronized boolean uploadFile(File localSourceFile, String remoteFilename, TransferProgressListener listener) throws IOException {
        if (!localSourceFile.exists()) {
            throw new FileNotFoundException("Tập tin cục bộ không tồn tại: " + localSourceFile.getAbsolutePath());
        }

        setBinaryMode();
        NetworkUtils.Endpoint ep = enterPassiveMode();

        log("[CLIENT] Chuẩn bị tải lên (STOR) " + localSourceFile.getName() + " -> " + remoteFilename);

        try (Socket dataSocket = new Socket(ep.ip, ep.port)) {
            dataSocket.setSoTimeout(FtpConstants.DATA_SOCKET_TIMEOUT_MS);

            sendCommand("STOR " + remoteFilename);
            FtpReply reply150 = readReply();
            if (reply150.getCode() != FtpConstants.CODE_150_OPENING_DATA_CONNECTION && reply150.getCode() != 125) {
                throw new IOException("Không thể tải file lên Server: " + reply150);
            }

            long totalBytes = localSourceFile.length();
            long transferred = 0;
            long startTime = System.currentTimeMillis();

            try (InputStream fileIn = new BufferedInputStream(new FileInputStream(localSourceFile));
                 OutputStream dataOut = new BufferedOutputStream(dataSocket.getOutputStream())) {

                byte[] buf = new byte[FtpConstants.BUFFER_SIZE];
                int n;
                while ((n = fileIn.read(buf)) != -1) {
                    dataOut.write(buf, 0, n);
                    transferred += n;

                    if (listener != null) {
                        double elapsedSec = Math.max(0.001, (System.currentTimeMillis() - startTime) / 1000.0);
                        double speedKbps = (transferred / 1024.0) / elapsedSec;
                        listener.onProgress(transferred, totalBytes, speedKbps);
                    }
                }
                dataOut.flush();
            }

            // Đóng Data Socket để báo hiệu EOF cho Server
            dataSocket.close();

            FtpReply reply226 = readReply();
            log("[CLIENT] " + reply226.getMessage());
            return reply226.getCode() == FtpConstants.CODE_226_TRANSFER_COMPLETE || reply226.getCode() == 250;
        }
    }

    public synchronized boolean deleteFile(String filename) throws IOException {
        sendCommand("DELE " + filename);
        FtpReply reply = readReply();
        return reply.getCode() == FtpConstants.CODE_250_FILE_ACTION_OK;
    }

    public synchronized boolean makeDirectory(String folderName) throws IOException {
        sendCommand("MKD " + folderName);
        FtpReply reply = readReply();
        return reply.getCode() == FtpConstants.CODE_257_PATHNAME_CREATED;
    }

    public synchronized boolean removeDirectory(String folderName) throws IOException {
        sendCommand("RMD " + folderName);
        FtpReply reply = readReply();
        return reply.getCode() == FtpConstants.CODE_250_FILE_ACTION_OK;
    }

    public synchronized boolean rename(String oldName, String newName) throws IOException {
        sendCommand("RNFR " + oldName);
        FtpReply r1 = readReply();
        if (r1.getCode() == FtpConstants.CODE_350_FILE_ACTION_PENDING) {
            sendCommand("RNTO " + newName);
            FtpReply r2 = readReply();
            return r2.getCode() == FtpConstants.CODE_250_FILE_ACTION_OK;
        }
        return false;
    }

    private void sendCommand(String cmd) throws IOException {
        if (controlSocket == null || controlSocket.isClosed()) {
            closeSocketSilently();
            throw new IOException("Chưa kết nối tới FTP Server hoặc kết nối đã bị đóng. Vui lòng bấm 'Kết Nối' lại!");
        }
        try {
            String logText = cmd.toUpperCase().startsWith("PASS") ? "PASS ******" : cmd;
            log(">>> " + logText);
            writer.write(cmd + FtpConstants.CRLF);
            writer.flush();
        } catch (java.net.SocketException se) {
            closeSocketSilently();
            throw new IOException("Mất kết nối với Server (Server đã tự ngắt do quá thời gian chờ / Timeout). Vui lòng bấm 'Kết Nối' lại!", se);
        }
    }

    private FtpReply readReply() throws IOException {
        try {
            String line = reader.readLine();
            if (line == null) {
                closeSocketSilently();
                throw new IOException("Đứt kết nối với FTP Server (Server đã đóng kết nối do quá hạn). Vui lòng bấm 'Kết Nối' lại!");
            }
            FtpReply reply = FtpReply.parse(line);

            // Xử lý multiline reply (ví dụ: 211-Features ... 211 End)
            if (line.length() >= 4 && line.charAt(3) == '-') {
                String endPrefix = line.substring(0, 3) + " ";
                StringBuilder sb = new StringBuilder(line).append("\n");
                String subLine;
                while ((subLine = reader.readLine()) != null) {
                    sb.append(subLine).append("\n");
                    if (subLine.startsWith(endPrefix)) {
                        break;
                    }
                }
                reply = new FtpReply(reply.getCode(), sb.toString(), sb.toString());
            }

            log("<<< " + reply.toString());
            return reply;
        } catch (java.net.SocketException se) {
            closeSocketSilently();
            throw new IOException("Mất kết nối với Server (Server đã tự ngắt do quá thời gian chờ / Timeout). Vui lòng bấm 'Kết Nối' lại!", se);
        }
    }

    public boolean isConnected() {
        return controlSocket != null && !controlSocket.isClosed();
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public String getCurrentWorkingDir() {
        return currentWorkingDir;
    }

    private void log(String msg) {
        logger.accept(msg);
    }
}
