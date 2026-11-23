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

    public synchronized void start() throws IOException {
        if (serverSocket != null && !serverSocket.isClosed()) {
            return;
        }
        Files.createDirectories(cacheDir);
        serverSocket = new ServerSocket(0);
        pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "zalolite-p2p");
            t.setDaemon(true);
            return t;
        });
        ServerSocket ss = serverSocket;
        pool.submit(() -> acceptLoop(ss));
    }

    public synchronized int getListenPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : -1;
    }

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

    private void handleIncoming(Socket socket) {
        Path target = null;
        try (socket) {
            socket.setSoTimeout(READ_TIMEOUT_MS);
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), BUFFER_SIZE));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE));

            if (!HEADER_MAGIC.equals(in.readUTF())) {
                out.writeUTF("ERR");
                out.flush();
                return;
            }

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

            String cleanName = sanitizeFileName(rawFileName);
            Files.createDirectories(cacheDir);
            target = cacheDir.resolve("p2p_" + UUID.randomUUID() + "_" + cleanName).normalize();
            if (!target.startsWith(cacheDir) || !cacheDir.equals(target.getParent())) {
                target = null;
                out.writeUTF("ERR");
                out.flush();
                return;
            }

            out.writeUTF("OK");
            out.flush();

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

            out.writeUTF("DONE");
            out.flush();

            if (onReceived != null) {
                onReceived.accept(new ReceivedP2pFile(
                        convId, senderId, kind, target.toFile(), cleanName, size, durationSec));
            }
        } catch (IOException e) {
            if (target != null) {
                try {
                    Files.deleteIfExists(target);
                } catch (IOException ignored) {
                }
            }
        }
    }

    static String sanitizeFileName(String raw) {
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

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);

            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE));
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), BUFFER_SIZE));

            out.writeUTF(HEADER_MAGIC);
            out.writeLong(convId);
            out.writeLong(senderId);
            out.writeUTF(kind != null ? kind : "FILE");
            out.writeUTF(file.getName());
            out.writeLong(size);
            out.writeInt(durationSec);
            out.flush();

            String ack = in.readUTF();
            if (!"OK".equals(ack)) {
                throw new IOException("P2P receiver rejected transfer: " + ack);
            }

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

            String done = in.readUTF();
            if (!"DONE".equals(done)) {
                throw new IOException("P2P receiver did not confirm completion: " + done);
            }
        }
    }
}
