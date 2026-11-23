package com.zalolite.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Pure-TCP client for uploading and downloading files from FileServer with progress reporting.
 */
public final class FileTransfer {
    public interface ProgressListener {
        void onProgress(long transferred, long total);
    }

    public static final long MAX_FILE_SIZE = 100L * 1024 * 1024;
    private static final int BUFFER_SIZE = 8192;
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private FileTransfer() {
    }

    public static String upload(String host, int port, File file, ProgressListener listener) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("File không tồn tại");
        }
        long size = file.length();
        if (size <= 0 || size > MAX_FILE_SIZE) {
            throw new IOException("File vượt quá giới hạn 100MB hoặc rỗng");
        }

        try (Socket socket = connect(host, port);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE));
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), BUFFER_SIZE))) {

            out.writeUTF("UPLOAD");
            out.writeUTF(file.getName());
            out.writeLong(size);
            out.flush();

            String status = in.readUTF();
            if (!"OK".equals(status)) {
                throw new IOException(in.readUTF());
            }

            byte[] buf = new byte[BUFFER_SIZE];
            long transferred = 0;
            try (InputStream fis = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE)) {
                int n;
                while ((n = fis.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    transferred += n;
                    if (listener != null) {
                        listener.onProgress(transferred, size);
                    }
                }
            }
            out.flush();

            String done = in.readUTF();
            if (!"DONE".equals(done)) {
                throw new IOException("Lỗi hoàn tất tải lên: " + done);
            }
            return in.readUTF();
        }
    }

    public static File download(String host, int port, String fileId, File dest, ProgressListener listener) throws IOException {
        if (fileId == null || fileId.isBlank()) {
            throw new IOException("Mã file không hợp lệ");
        }
        if (dest == null) {
            throw new IOException("Đường dẫn lưu không hợp lệ");
        }

        try (Socket socket = connect(host, port);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), BUFFER_SIZE));
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), BUFFER_SIZE))) {

            out.writeUTF("DOWNLOAD");
            out.writeUTF(fileId);
            out.flush();

            String status = in.readUTF();
            if (!"OK".equals(status)) {
                throw new IOException(in.readUTF());
            }

            long size = in.readLong();
            if (size < 0 || size > MAX_FILE_SIZE) {
                throw new IOException("Kích thước file không hợp lệ");
            }

            File parent = dest.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }

            byte[] buf = new byte[BUFFER_SIZE];
            long transferred = 0;
            boolean completed = false;
            try (OutputStream fos = new BufferedOutputStream(new FileOutputStream(dest), BUFFER_SIZE)) {
                while (transferred < size) {
                    int toRead = (int) Math.min(buf.length, size - transferred);
                    int n = in.read(buf, 0, toRead);
                    if (n == -1) {
                        throw new EOFException("Kết nối bị ngắt trước khi tải xong");
                    }
                    fos.write(buf, 0, n);
                    transferred += n;
                    if (listener != null) {
                        listener.onProgress(transferred, size);
                    }
                }
                completed = true;
            } finally {
                if (!completed && dest.exists()) {
                    dest.delete();
                }
            }
            return dest;
        }
    }

    public static File cacheFile(String fileId, String originalName) {
        File cacheDir = new File(System.getProperty("java.io.tmpdir"), "zalolite-cache");
        cacheDir.mkdirs();

        String safeId = sanitize(fileId);
        String safeName = sanitize(originalName);
        String combined;
        if (!safeName.isEmpty() && !safeId.isEmpty()) {
            String prefix = safeId.length() > 16 ? safeId.substring(0, 16) : safeId;
            combined = prefix + "_" + safeName;
        } else if (!safeId.isEmpty()) {
            combined = safeId;
        } else {
            combined = safeName.isEmpty() ? "cached_file" : safeName;
        }
        return new File(cacheDir, combined);
    }

    private static Socket connect(String host, int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        socket.setSoTimeout(READ_TIMEOUT_MS);
        return socket;
    }

    private static String sanitize(String s) {
        if (s == null || s.isBlank()) return "";
        String clean = s.trim()
                .replace("..", "_")
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        return clean.length() > 120 ? clean.substring(clean.length() - 120) : clean;
    }
}
