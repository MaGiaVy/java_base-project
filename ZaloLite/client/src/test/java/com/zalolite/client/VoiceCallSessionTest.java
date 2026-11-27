package com.zalolite.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class VoiceCallSessionTest {

    @Test
    void transfersPcmFramesOverUdpAndClosesCleanly() throws Exception {
        int frameCount = 10;
        List<byte[]> received = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(frameCount);

        VoiceCallSession a = new VoiceCallSession();
        VoiceCallSession b = new VoiceCallSession();
        try {
            b.startReceiveOnly(frame -> {
                received.add(frame);
                latch.countDown();
            });

            byte[][] sent = new byte[frameCount][VoiceCallSession.FRAME_BYTES];
            for (int i = 0; i < frameCount; i++) {
                for (int j = 0; j < VoiceCallSession.FRAME_BYTES; j++) {
                    sent[i][j] = (byte) ((i * 31 + j) & 0xFF);
                }
                a.sendRawFrame("127.0.0.1", b.getLocalPort(), sent[i]);
            }

            assertTrue(latch.await(3, TimeUnit.SECONDS),
                    "Expected " + frameCount + " UDP frames, got " + received.size());
            assertEquals(frameCount, received.size());
            for (int i = 0; i < frameCount; i++) {
                assertArrayEquals(sent[i], received.get(i), "Frame " + i + " mismatch");
            }
        } finally {
            a.close();
            b.close();
        }

        assertTrue(a.isClosed(), "Session A socket should be closed");
        assertTrue(b.isClosed(), "Session B socket should be closed");
    }
}
