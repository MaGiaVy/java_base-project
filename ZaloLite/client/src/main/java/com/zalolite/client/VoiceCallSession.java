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
 * [CỔNG/GIAO THỨC] Phiên gọi thoại 1-1 thời gian thực qua UDP thuần (không qua server trung chuyển âm thanh).
 * - Tín hiệu điều khiển (CALL_OFFER / CALL_ACCEPT / CALL_END) đi qua kênh TLS (:9000) để trao đổi IP + cổng UDP.
 * - Dữ liệu âm thanh PCM thô (16kHz, 16-bit, mono, mỗi khung 20ms = 640 byte) truyền trực tiếp giữa 2 client
 *   bằng {@link DatagramSocket}(0) trên cổng UDP ngẫu nhiên để giảm tối đa độ trễ.
 */
public final class VoiceCallSession implements AutoCloseable {
    /** Định dạng âm thanh PCM: 16kHz, 16-bit signed little-endian, 1 kênh mono. */
    public static final AudioFormat AUDIO_FORMAT = new AudioFormat(16000f, 16, 1, true, false);
    /** Kích thước mỗi khung âm thanh 20ms: 16000 mẫu/s * 2 byte * 0.02s = 640 byte. */
    public static final int FRAME_BYTES = 640;
    private static final int RECV_TIMEOUT_MS = 500;

    private final DatagramSocket socket;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean muted = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private volatile TargetDataLine micLine;
    private volatile SourceDataLine speakerLine;
    private volatile Consumer<byte[]> onFrameReceived;

    /** [CỔNG/GIAO THỨC] Tạo DatagramSocket trên cổng UDP ngẫu nhiên (0) để gửi/nhận âm thanh P2P. */
    public VoiceCallSession() throws SocketException {
        this.socket = new DatagramSocket(0);
        this.socket.setSoTimeout(RECV_TIMEOUT_MS);
    }

    /** Trả về cổng UDP cục bộ (được gửi kèm trong CALL_OFFER / CALL_ACCEPT qua kênh TLS). */
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

    /**
     * Bắt đầu luồng âm thanh 2 chiều (full-duplex) tới {@code peerIp:peerPort}:
     *  - Luồng "voice-capture": thu mic -> gửi gói UDP PCM 640 byte
     *  - Luồng "voice-receive": nhận gói UDP PCM -> phát ra loa
     */
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

    /** Chế độ chỉ nhận UDP (không mở loa/mic, dùng cho test tự động hoặc máy không có audio). */
    public void startReceiveOnly(Consumer<byte[]> onFrameReceived) {
        this.onFrameReceived = onFrameReceived;
        if (closed.get() || !running.compareAndSet(false, true)) return;
        Thread receiveThread = new Thread(() -> runReceiveLoop(false), "voice-receive-headless");
        receiveThread.setDaemon(true);
        receiveThread.start();
    }

    /** [GỬI] Gửi trực tiếp 1 khung byte PCM qua UDP tới {@code peerIp:peerPort}. */
    public void sendRawFrame(String peerIp, int peerPort, byte[] pcm) throws IOException {
        if (closed.get()) throw new IOException("VoiceCallSession is closed");
        InetAddress peerAddr = InetAddress.getByName(peerIp);
        DatagramPacket packet = new DatagramPacket(pcm, pcm.length, peerAddr, peerPort);
        socket.send(packet);
    }

    /** [GỬI] Vòng lặp thu âm từ micro (TargetDataLine) và bắn từng khung PCM 640 byte qua UDP tới peer. */
    private void runCaptureLoop(InetAddress peerAddr, int peerPort) {
        TargetDataLine mic = null;
        try {
            mic = AudioHelper.openBestMicrophone(AUDIO_FORMAT, AudioHelper.getPreferredMicrophone());
            mic.start();
            this.micLine = mic;

            byte[] buf = new byte[FRAME_BYTES];
            while (running.get() && !socket.isClosed()) {
                int read = mic.read(buf, 0, buf.length);
                if (read > 0 && !muted.get()) {
                    // [GỬI] Đóng gói khung PCM vào DatagramPacket và gửi thẳng tới IP:port UDP của đối phương
                    DatagramPacket packet = new DatagramPacket(buf, read, peerAddr, peerPort);
                    socket.send(packet);
                }
            }
        } catch (LineUnavailableException | IllegalArgumentException e) {
            System.err.println("[VoiceCall] Microphone unavailable, continuing receive-only: " + e.getMessage());
        } catch (IOException ignored) {
            // Bình thường khi cuộc gọi kết thúc và socket bị đóng
        } finally {
            closeLine(mic);
            this.micLine = null;
        }
    }

    /** [NHẬN] Vòng lặp nhận gói UDP PCM từ đối phương và phát trực tiếp ra loa (SourceDataLine). */
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
                    // [NHẬN] Đọc 1 gói âm thanh PCM từ DatagramSocket (timeout 500ms để kiểm tra cờ running)
                    socket.receive(packet);
                } catch (SocketTimeoutException timeout) {
                    continue;
                }
                int len = packet.getLength();
                if (len <= 0) continue;
                if (speaker != null) {
                    // Phát khung âm thanh nhận được ra loa ngay lập tức
                    speaker.write(packet.getData(), packet.getOffset(), len);
                }
                Consumer<byte[]> listener = this.onFrameReceived;
                if (listener != null) {
                    listener.accept(Arrays.copyOfRange(
                            packet.getData(), packet.getOffset(), packet.getOffset() + len));
                }
            }
        } catch (IOException ignored) {
            // Bình thường khi cuộc gọi kết thúc và socket bị đóng
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

    /** Kết thúc phiên gọi thoại: dừng luồng, đóng DatagramSocket, đóng micro và loa. */
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
