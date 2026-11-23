package com.zalolite.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class FileServerTest {
    @TempDir
    Path storageDir;

    private FileServer server;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        server = new FileServer(0, storageDir);
        server.startAsync();
        port = server.getLocalPort();
        assertTrue(port > 0);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void uploadAndDownloadRoundTripWithProgress() throws IOException {
        byte[] payload = new byte[200 * 1024];
        new Random(42).nextBytes(payload);

        AtomicLong uploadProgress = new AtomicLong();
        String fileId;
        try (Socket socket = new Socket("127.0.0.1", port);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
            socket.setSoTimeout(5000);
            out.writeUTF("UPLOAD");
            out.writeUTF("photo.png");
            out.writeLong(payload.length);
            out.flush();

            assertEquals("OK", in.readUTF());

            int offset = 0;
            while (offset < payload.length) {
                int len = Math.min(8192, payload.length - offset);
                out.write(payload, offset, len);
                offset += len;
                uploadProgress.set(offset);
            }
            out.flush();

            assertEquals("DONE", in.readUTF());
            fileId = in.readUTF();
        }

        assertEquals(payload.length, uploadProgress.get());
        assertNotNull(fileId);
        assertTrue(fileId.endsWith("_photo.png"));

        AtomicLong downloadProgress = new AtomicLong();
        ByteArrayOutputStream received = new ByteArrayOutputStream(payload.length);
        try (Socket socket = new Socket("127.0.0.1", port);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
            socket.setSoTimeout(5000);
            out.writeUTF("DOWNLOAD");
            out.writeUTF(fileId);
            out.flush();

            assertEquals("OK", in.readUTF());
            long size = in.readLong();
            assertEquals(payload.length, size);

            byte[] buf = new byte[8192];
            long read = 0;
            while (read < size) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, size - read));
                assertTrue(n > 0);
                received.write(buf, 0, n);
                read += n;
                downloadProgress.set(read);
            }
        }

        assertEquals(payload.length, downloadProgress.get());
        assertArrayEquals(payload, received.toByteArray());
    }

    @Test
    void rejectsPathTraversalOnUploadAndDownload() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
            socket.setSoTimeout(5000);
            out.writeUTF("UPLOAD");
            out.writeUTF("../../hack.txt");
            out.writeLong(16);
            out.flush();

            assertEquals("ERR", in.readUTF());
            assertEquals("Tên file không hợp lệ", in.readUTF());
        }

        try (Socket socket = new Socket("127.0.0.1", port);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
            socket.setSoTimeout(5000);
            out.writeUTF("DOWNLOAD");
            out.writeUTF("../../hack.txt");
            out.flush();

            assertEquals("ERR", in.readUTF());
            assertEquals("Đường dẫn không hợp lệ", in.readUTF());
        }
    }

    @Test
    void rejectsOversizeFileCleanlyWithoutHanging() throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
            socket.setSoTimeout(5000);
            out.writeUTF("UPLOAD");
            out.writeUTF("huge.bin");
            out.writeLong(FileServer.MAX_FILE_SIZE + 1);
            out.flush();

            assertEquals("ERR", in.readUTF());
            assertEquals("File vượt quá giới hạn 100MB hoặc rỗng", in.readUTF());
        }
    }
}
