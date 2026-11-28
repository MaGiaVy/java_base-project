package com.zalolite.client;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bộ ghi âm và phát tin nhắn thoại (Voice Message) thuần Java Sound API (javax.sound.sampled).
 * - Ghi âm từ micro định dạng PCM 16kHz, 16-bit, mono (tối đa 60 giây) rồi đóng gói ra file tạm .wav.
 * - File .wav sau đó được gửi qua {@link P2pTransfer} (trực tiếp) hoặc {@link FileTransfer} (TCP :9001).
 * - Phát lại tin nhắn thoại bằng {@link Clip} với callback báo khi phát xong.
 */
public class AudioHelper {

    public static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);
    public static final int MAX_SECONDS = 60;

    public record RecordedAudio(File file, int durationSec) {}

    private volatile boolean recording;
    private TargetDataLine line;
    private Thread recordThread;
    private ByteArrayOutputStream pcmBuffer;
    private RecordedAudio lastResult;

    private final Object playLock = new Object();
    private Clip currentClip;

    /** Mở micro và bắt đầu luồng nền thu âm PCM (tự dừng sau {@link #MAX_SECONDS} giây). */
    public synchronized void startRecording(Runnable onAutoStop) throws IOException {
        if (recording) {
            throw new IOException("Recording already in progress");
        }
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        if (!AudioSystem.isLineSupported(info)) {
            throw new IOException("No microphone available for 16kHz 16-bit mono audio");
        }
        TargetDataLine mic;
        try {
            mic = (TargetDataLine) AudioSystem.getLine(info);
            mic.open(FORMAT);
            mic.start();
        } catch (LineUnavailableException | IllegalArgumentException e) {
            throw new IOException("Microphone unavailable: " + e.getMessage(), e);
        }

        this.line = mic;
        this.pcmBuffer = new ByteArrayOutputStream();
        this.lastResult = null;
        this.recording = true;

        int maxBytes = (int) (FORMAT.getFrameRate() * FORMAT.getFrameSize() * MAX_SECONDS);
        this.recordThread = new Thread(() -> captureLoop(mic, maxBytes, onAutoStop), "audio-recorder");
        this.recordThread.setDaemon(true);
        this.recordThread.start();
    }

    /** Vòng lặp đọc dữ liệu PCM từ micro vào bộ đệm RAM cho tới khi người dùng dừng hoặc đạt 60s. */
    private void captureLoop(TargetDataLine mic, int maxBytes, Runnable onAutoStop) {
        byte[] chunk = new byte[4096];
        boolean autoStopped = false;
        try {
            while (recording) {
                int remaining = maxBytes - pcmBuffer.size();
                if (remaining <= 0) {
                    autoStopped = true;
                    break;
                }
                int n = mic.read(chunk, 0, Math.min(chunk.length, remaining));
                if (n > 0) {
                    pcmBuffer.write(chunk, 0, n);
                } else if (n < 0) {
                    break;
                }
            }
        } finally {
            mic.stop();
            mic.close();
        }

        if (autoStopped && recording) {
            recording = false;
            try {
                finalizeWav();
            } catch (IOException ignored) {
                // Bên gọi sẽ nhận lỗi nếu sau đó gọi stopRecording().
            }
            if (onAutoStop != null) {
                onAutoStop.run();
            }
        }
    }

    /** Dừng ghi âm, chờ luồng thu kết thúc và xuất ra file .wav kèm thời lượng (giây). */
    public RecordedAudio stopRecording() throws IOException {
        Thread t;
        TargetDataLine mic;
        synchronized (this) {
            recording = false;
            mic = this.line;
            this.line = null;
            t = this.recordThread;
            this.recordThread = null;
        }
        if (mic != null) {
            mic.stop();
            mic.close();
        }
        if (t != null && t != Thread.currentThread()) {
            try {
                t.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return finalizeWav();
    }

    /** Ghi mảng byte PCM đã thu thành file WAV chuẩn trong thư mục tạm. */
    private synchronized RecordedAudio finalizeWav() throws IOException {
        if (lastResult != null) {
            return lastResult;
        }
        byte[] pcm = pcmBuffer != null ? pcmBuffer.toByteArray() : new byte[0];
        long frameCount = pcm.length / FORMAT.getFrameSize();
        File wav = File.createTempFile("zalolite-voice-", ".wav");
        try (AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(pcm), FORMAT, frameCount)) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wav);
        }
        int durationSec = frameCount > 0 ? Math.max(1, (int) Math.round(frameCount / (double) FORMAT.getFrameRate())) : 0;
        lastResult = new RecordedAudio(wav, durationSec);
        return lastResult;
    }

    public boolean isRecording() {
        return recording;
    }

    /** Tính thời lượng (giây) của một file WAV có sẵn dựa trên frameLength / frameRate. */
    public static int wavDurationSec(File wavFile) throws IOException {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(wavFile)) {
            long frames = ais.getFrameLength();
            float frameRate = ais.getFormat().getFrameRate();
            if (frames <= 0 || frameRate <= 0) {
                return 0;
            }
            return Math.max(1, (int) Math.round(frames / (double) frameRate));
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("Unsupported audio file: " + wavFile.getName(), e);
        }
    }

    /** Phát file WAV tin nhắn thoại; dừng clip đang phát trước đó (nếu có). */
    public void play(File wavFile, Runnable onFinished) throws IOException {
        stopPlayback();
        Clip clip;
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(wavFile)) {
            clip = AudioSystem.getClip();
            clip.open(ais);
        } catch (UnsupportedAudioFileException | LineUnavailableException e) {
            throw new IOException("Cannot play audio: " + e.getMessage(), e);
        }

        AtomicBoolean finished = new AtomicBoolean(false);
        clip.addLineListener(event -> {
            if (event.getType() == LineEvent.Type.STOP && finished.compareAndSet(false, true)) {
                synchronized (playLock) {
                    if (currentClip == clip) {
                        currentClip = null;
                    }
                }
                clip.close();
                if (onFinished != null) {
                    onFinished.run();
                }
            }
        });

        synchronized (playLock) {
            currentClip = clip;
        }
        clip.start();
    }

    /** Dừng phát tin nhắn thoại hiện tại và giải phóng Clip. */
    public void stopPlayback() {
        Clip clip;
        synchronized (playLock) {
            clip = currentClip;
            currentClip = null;
        }
        if (clip != null) {
            clip.stop();
            clip.close();
        }
    }
}
