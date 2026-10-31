package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;

/**
 * Lưu trữ trạng thái phiên làm việc riêng biệt của từng Client (Slide 28, 30)
 */
public class FtpSession {
    private final String sessionId;
    private final Socket controlSocket;
    private final String clientAddress;
    private final long connectedTime;

    private String username;
    private boolean loggedIn;
    private String currentVirtualPath = "/";
    private boolean binaryMode = true; // Mặc định Binary Mode TYPE I (Slide 19)

    // Kết nối dữ liệu thụ động (PASV)
    private ServerSocket passiveServerSocket;

    // Kết nối dữ liệu chủ động (PORT)
    private String activeIp;
    private int activePort;

    // Bộ đệm cho lệnh đổi tên RNFR / RNTO (Slide 25)
    private Path renameFromPath;

    private String currentStatus = "Connected";

    public FtpSession(String sessionId, Socket controlSocket) {
        this.sessionId = sessionId;
        this.controlSocket = controlSocket;
        this.clientAddress = controlSocket.getRemoteSocketAddress().toString();
        this.connectedTime = System.currentTimeMillis();
    }

    public String getSessionId() {
        return sessionId;
    }

    public Socket getControlSocket() {
        return controlSocket;
    }

    public String getClientAddress() {
        return clientAddress;
    }

    public long getConnectedTime() {
        return connectedTime;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }

    public String getCurrentVirtualPath() {
        return currentVirtualPath;
    }

    public void setCurrentVirtualPath(String path) {
        this.currentVirtualPath = (path == null || path.isEmpty()) ? "/" : path;
    }

    public boolean isBinaryMode() {
        return binaryMode;
    }

    public void setBinaryMode(boolean binaryMode) {
        this.binaryMode = binaryMode;
    }

    public synchronized ServerSocket getPassiveServerSocket() {
        return passiveServerSocket;
    }

    public synchronized void setPassiveServerSocket(ServerSocket ss) {
        closePassiveServer();
        this.passiveServerSocket = ss;
    }

    public synchronized void closePassiveServer() {
        if (passiveServerSocket != null && !passiveServerSocket.isClosed()) {
            try {
                passiveServerSocket.close();
            } catch (IOException ignored) {}
            passiveServerSocket = null;
        }
    }

    public void setActiveEndpoint(String ip, int port) {
        this.activeIp = ip;
        this.activePort = port;
    }

    public String getActiveIp() {
        return activeIp;
    }

    public int getActivePort() {
        return activePort;
    }

    public Path getRenameFromPath() {
        return renameFromPath;
    }

    public void setRenameFromPath(Path renameFromPath) {
        this.renameFromPath = renameFromPath;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(String currentStatus) {
        this.currentStatus = currentStatus;
    }

    public void closeAll() {
        closePassiveServer();
        if (controlSocket != null && !controlSocket.isClosed()) {
            try {
                controlSocket.close();
            } catch (IOException ignored) {}
        }
    }
}
