package com.zalolite.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class P2pTransferTest {

    @Test
    void directTransferWithoutServer(@TempDir Path tempDir) throws Exception {
        Path cacheDir = tempDir.resolve("cache");
        CompletableFuture<P2pTransfer.ReceivedP2pFile> receivedFuture = new CompletableFuture<>();
        P2pTransfer receiver = new P2pTransfer(cacheDir, receivedFuture::complete);
        receiver.start();
        try {
            byte[] payload = new byte[150 * 1024];
            new Random(12345L).nextBytes(payload);
            Path srcPath = tempDir.resolve("sample_clip.bin");
            Files.write(srcPath, payload);

            AtomicLong lastTransferred = new AtomicLong(-1);
            AtomicLong lastTotal = new AtomicLong(-1);

            P2pTransfer.sendDirect(
                    "127.0.0.1",
                    receiver.getListenPort(),
                    42L,
                    7L,
                    "VOICE",
                    srcPath.toFile(),
                    19,
                    (transferred, total) -> {
                        lastTransferred.set(transferred);
                        lastTotal.set(total);
                    });

            P2pTransfer.ReceivedP2pFile received = receivedFuture.get(5, TimeUnit.SECONDS);
            assertNotNull(received);
            assertEquals(42L, received.convId());
            assertEquals(7L, received.senderId());
            assertEquals("VOICE", received.kind());
            assertEquals("sample_clip.bin", received.fileName());
            assertEquals(payload.length, received.fileSize());
            assertEquals(19, received.durationSec());
            assertTrue(received.savedFile().isFile());
            assertArrayEquals(payload, Files.readAllBytes(received.savedFile().toPath()));
            assertEquals(payload.length, lastTransferred.get());
            assertEquals(payload.length, lastTotal.get());
        } finally {
            receiver.stop();
        }
    }

    @Test
    void sanitizesPathTraversalFilename(@TempDir Path tempDir) throws Exception {
        Path cacheDir = tempDir.resolve("nested").resolve("cache").toAbsolutePath().normalize();
        CompletableFuture<P2pTransfer.ReceivedP2pFile> receivedFuture = new CompletableFuture<>();
        P2pTransfer receiver = new P2pTransfer(cacheDir, receivedFuture::complete);
        receiver.start();
        try {
            byte[] payload = "safe-payload".getBytes(StandardCharsets.UTF_8);
            try (Socket socket = new Socket("127.0.0.1", receiver.getListenPort())) {
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream());

                out.writeUTF("P2P_FILE");
                out.writeLong(99L);
                out.writeLong(11L);
                out.writeUTF("FILE");
                out.writeUTF("../../evil.txt");
                out.writeLong(payload.length);
                out.writeInt(0);
                out.flush();

                assertEquals("OK", in.readUTF());
                out.write(payload);
                out.flush();
                assertEquals("DONE", in.readUTF());
            }

            P2pTransfer.ReceivedP2pFile received = receivedFuture.get(5, TimeUnit.SECONDS);
            assertNotNull(received);
            Path savedPath = received.savedFile().toPath().toAbsolutePath().normalize();
            assertTrue(Files.isRegularFile(savedPath));
            assertTrue(savedPath.startsWith(cacheDir), "Saved file must stay inside cacheDir");
            assertEquals(cacheDir, savedPath.getParent());
            assertFalse(received.fileName().contains(".."));
            assertFalse(Files.exists(tempDir.resolve("evil.txt")));
            assertArrayEquals(payload, Files.readAllBytes(savedPath));
        } finally {
            receiver.stop();
        }
    }

    @Test
    void failsFastOnClosedPort(@TempDir Path tempDir) throws Exception {
        int unusedPort;
        try (ServerSocket ss = new ServerSocket(0)) {
            unusedPort = ss.getLocalPort();
        }

        File file = tempDir.resolve("ping.txt").toFile();
        Files.writeString(file.toPath(), "hello");

        long startMs = System.currentTimeMillis();
        assertThrows(IOException.class, () -> P2pTransfer.sendDirect(
                "127.0.0.1", unusedPort, 1L, 2L, "FILE", file, 0, null));
        long elapsedMs = System.currentTimeMillis() - startMs;
        assertTrue(elapsedMs < 3000, "Expected fast failure (<3000ms), took " + elapsedMs + "ms");
    }
}
