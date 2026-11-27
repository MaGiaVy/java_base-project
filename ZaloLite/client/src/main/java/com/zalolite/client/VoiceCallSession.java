package com.zalolite.client;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Pure UDP realtime 1-1 PCM voice call session (16kHz, 16-bit, mono, 20ms frames).
 */
public final class VoiceCallSession implements AutoCloseable {
    public static final AudioFormat AUDIO_FORMAT = new AudioFormat(16000f, 16, 1, true, false);
    public static final int FRAME_BYTES = 640;
    private static final int RECV_TIMEOUT_MS = 500;

    private final DatagramSocket socket;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean muted = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private volatile TargetDataLine micLine;
    private volatile SourceDataLine speakerLine;
    private volatile Consumer<byte[]> onFrameReceived;

    public VoiceCallSession() throws SocketException {
        this.socket = new DatagramSocket(0);
        this.socket.setSoTimeout(RECV_TIMEOUT_MS);
    }

    public int getLocalPort() {
        return socket.getLocalPort();
    }

    public void setMuted(boolean muted) {
        this.muted.set(muted);
    }

    public boolean isMuted() {
        return muted.get();
    }

    public boolean isClosed() {
        return closed.get() && socket.isClosed();
    }

    public void setOnFrameReceived(Consumer<byte[]> onFrameReceived) {
        this.onFrameReceived = onFrameReceived;
    }

    public void startAudio(String peerIp, int peerPort) throws IOException {
        if (closed.get()) throw new IOException("VoiceCallSession is already closed");
        InetAddress peerAddr = InetAddress.getByName(peerIp);
        if (!running.compareAndSet(false, true)) return;

        Thread captureThread = new Thread(() -> runCaptureLoop(peerAddr, peerPort), "voice-capture");
        captureThread.setDaemon(true);
        captureThread.start();

        Thread receiveThread = new Thread(() -> runReceiveLoop(true), "voice-receive");
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    public void startReceiveOnly(Consumer<byte[]> onFrameReceived) {
        this.onFrameReceived = onFrameReceived;
        if (closed.get() || !running.compareAndSet(false, true)) return;
        Thread receiveThread = new Thread(() -> runReceiveLoop(false), "voice-receive-headless");
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    public void sendRawFrame(String peerIp, int peerPort, byte[] pcm) throws IOException {
        if (closed.get()) throw new IOException("VoiceCallSession is closed");
        InetAddress peerAddr = InetAddress.getByName(peerIp);
        DatagramPacket packet = new DatagramPacket(pcm, pcm.length, peerAddr, peerPort);
        socket.send(packet);
    }

    private void runCaptureLoop(InetAddress peerAddr, int peerPort) {
        TargetDataLine mic = null;
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, AUDIO_FORMAT);
            mic = (TargetDataLine) AudioSystem.getLine(info);
            mic.open(AUDIO_FORMAT, FRAME_BYTES * 4);
            mic.start();
            this.micLine = mic;

            byte[] buf = new byte[FRAME_BYTES];
            while (running.get() && !socket.isClosed()) {
                int read = mic.read(buf, 0, buf.length);
                if (read > 0 && !muted.get()) {
                    DatagramPacket packet = new DatagramPacket(buf, read, peerAddr, peerPort);
                    socket.send(packet);
                }
            }
        } catch (LineUnavailableException | IllegalArgumentException e) {
            System.err.println("[VoiceCall] Microphone unavailable, continuing receive-only: " + e.getMessage());
        } catch (IOException ignored) {
            // Expected on shutdown when socket closes
        } finally {
            closeLine(mic);
            this.micLine = null;
        }
    }

    private void runReceiveLoop(boolean useSpeaker) {
        SourceDataLine speaker = null;
        try {
            if (useSpeaker) {
                try {
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, AUDIO_FORMAT);
                    speaker = (SourceDataLine) AudioSystem.getLine(info);
                    speaker.open(AUDIO_FORMAT, FRAME_BYTES * 8);
                    speaker.start();
                    this.speakerLine = speaker;
                } catch (LineUnavailableException | IllegalArgumentException e) {
                    System.err.println("[VoiceCall] Speaker unavailable: " + e.getMessage());
                    speaker = null;
                }
            }

            byte[] buf = new byte[FRAME_BYTES * 2];
            while (running.get() && !socket.isClosed()) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException timeout) {
                    continue;
                }
                int len = packet.getLength();
                if (len <= 0) continue;
                if (speaker != null) {
                    speaker.write(packet.getData(), packet.getOffset(), len);
                }
                Consumer<byte[]> listener = this.onFrameReceived;
                if (listener != null) {
                    listener.accept(Arrays.copyOfRange(
                            packet.getData(), packet.getOffset(), packet.getOffset() + len));
                }
            }
        } catch (IOException ignored) {
            // Expected on shutdown when socket closes
        } finally {
            closeLine(speaker);
            this.speakerLine = null;
        }
    }

    private static void closeLine(DataLine line) {
        if (line == null) return;
        try {
            line.stop();
        } catch (Exception ignored) {
        }
        try {
            line.close();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        running.set(false);
        try {
            socket.close();
        } finally {
            closeLine(micLine);
            micLine = null;
            closeLine(speakerLine);
            speakerLine = null;
        }
    }
}
