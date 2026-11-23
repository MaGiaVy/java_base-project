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
 * Pure-TCP binary file transfer server (UPLOAD / DOWNLOAD) with 100MB cap and path-traversal protection.
 */
public class FileServer implements AutoCloseable {
    public static final long MAX_FILE_SIZE = 100L * 1024 * 1024;
    private static final int BUFFER_SIZE = 8192;
    private static final int CLIENT_TIMEOUT_MS = 30_000;

    private final int port;
    private final Path storageDir;
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "file-server-worker");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running;
    private volatile ServerSocket serverSocket;

    public FileServer(int port) {
        this(port, Path.of("server/uploads"));
    }

    public FileServer(int port, Path storageDir) {
        this.port = port;
        this.storageDir = storageDir.toAbsolutePath().normalize();
    }

    public void start() throws IOException {
        ServerSocket ss = bind();
        acceptLoop(ss);
    }

    public void startAsync() throws IOException {
        ServerSocket ss = bind();
        Thread acceptThread = new Thread(() -> acceptLoop(ss), "file-server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public int getLocalPort() {
        ServerSocket ss = this.serverSocket;
        return ss != null ? ss.getLocalPort() : port;
    }

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

    @Override
    public void close() {
        stop();
    }

    private synchronized ServerSocket bind() throws IOException {
        Files.createDirectories(storageDir);
        ServerSocket ss = new ServerSocket(port);
        this.serverSocket = ss;
        this.running = true;
        return ss;
    }

    private void acceptLoop(ServerSocket ss) {
        while (running && !ss.isClosed()) {
            try {
                Socket client = ss.accept();
                pool.execute(() -> handleClient(client));
            } catch (SocketException e) {
                if (!running) break;
            } catch (IOException e) {
                if (!running) break;
            }
        }
    }

    private void handleClient(Socket socket) {
        try (socket;
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), BUFFER_SIZE));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE))) {
            socket.setSoTimeout(CLIENT_TIMEOUT_MS);
            String cmd = in.readUTF();
            if ("UPLOAD".equals(cmd)) {
                handleUpload(in, out);
            } else if ("DOWNLOAD".equals(cmd)) {
                handleDownload(in, out);
            } else {
                sendErr(out, "Lệnh không hợp lệ");
            }
        } catch (IOException ignored) {
            // Client disconnected or timed out.
        }
    }

    private void handleUpload(DataInputStream in, DataOutputStream out) throws IOException {
        String fileName = in.readUTF();
        long size = in.readLong();

        if (size <= 0 || size > MAX_FILE_SIZE) {
            sendErr(out, "File vượt quá giới hạn 100MB hoặc rỗng");
            return;
        }
        if (isUnsafeToken(fileName)) {
            sendErr(out, "Tên file không hợp lệ");
            return;
        }

        String storedName = UUID.randomUUID() + "_" + sanitizeName(fileName);
        Path target = storageDir.resolve(storedName).normalize();
        if (!target.startsWith(storageDir) || target.equals(storageDir)) {
            sendErr(out, "Tên file không hợp lệ");
            return;
        }

        Files.createDirectories(storageDir);
        out.writeUTF("OK");
        out.flush();

        byte[] buf = new byte[BUFFER_SIZE];
        long remaining = size;
        boolean completed = false;
        try (OutputStream fos = new BufferedOutputStream(Files.newOutputStream(target), BUFFER_SIZE)) {
            while (remaining > 0) {
                int toRead = (int) Math.min(buf.length, remaining);
                int n = in.read(buf, 0, toRead);
                if (n == -1) {
                    throw new EOFException("Client disconnected before finishing upload");
                }
                fos.write(buf, 0, n);
                remaining -= n;
            }
            completed = true;
        } finally {
            if (!completed) {
                Files.deleteIfExists(target);
            }
        }

        out.writeUTF("DONE");
        out.writeUTF(storedName);
        out.flush();
    }

    private void handleDownload(DataInputStream in, DataOutputStream out) throws IOException {
        String fileId = in.readUTF();
        if (isUnsafeToken(fileId)) {
            sendErr(out, "Đường dẫn không hợp lệ");
            return;
        }

        Path target = storageDir.resolve(fileId).normalize();
        if (!target.startsWith(storageDir) || target.equals(storageDir)) {
            sendErr(out, "Đường dẫn không hợp lệ");
            return;
        }
        if (!Files.isRegularFile(target)) {
            sendErr(out, "Không tìm thấy file");
            return;
        }

        long size = Files.size(target);
        out.writeUTF("OK");
        out.writeLong(size);

        byte[] buf = new byte[BUFFER_SIZE];
        long remaining = size;
        try (InputStream fis = new BufferedInputStream(Files.newInputStream(target), BUFFER_SIZE)) {
            while (remaining > 0) {
                int toRead = (int) Math.min(buf.length, remaining);
                int n = fis.read(buf, 0, toRead);
                if (n == -1) break;
                out.write(buf, 0, n);
                remaining -= n;
            }
        }
        out.flush();
    }

    private static void sendErr(DataOutputStream out, String message) throws IOException {
        out.writeUTF("ERR");
        out.writeUTF(message);
        out.flush();
    }

    private static boolean isUnsafeToken(String s) {
        return s == null || s.isBlank()
                || s.contains("..") || s.contains("/") || s.contains("\\") || s.indexOf('\0') >= 0;
    }

    private static String sanitizeName(String name) {
        String clean = name.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (clean.length() > 120) {
            clean = clean.substring(clean.length() - 120);
        }
        return clean.isEmpty() ? "file" : clean;
    }
}
