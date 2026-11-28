package com.zalolite.client;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

/**
 * Bộ ghi âm và phát tin nhắn thoại (Voice Message) thuần Java Sound API (javax.sound.sampled).
 * - Tự động ưu tiên micro vật lý thay vì driver ảo (Camo, OBS, Primary Sound Capture Driver...).
 * - Ghi âm từ micro định dạng PCM 16kHz, 16-bit, mono (tối đa 60 giây), chuẩn hóa âm lượng nhẹ rồi đóng gói ra file tạm .wav.
 * - Phát lại tin nhắn thoại bằng {@link Clip} (có fallback {@link SourceDataLine}) với callback báo khi phát xong.
 */
public class AudioHelper {

    public static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);
    public static final int MAX_SECONDS = 60;

    private static final String[] VIRTUAL_MIC_KEYWORDS = {
            "camo", "obs", "virtual", "cable", "voicemeeter",
            "stereo mix", "primary sound capture", "droidcam"
    };

    public static volatile String preferredMicrophone;

    public record RecordedAudio(File file, int durationSec, int peakAmplitude) {}

    private volatile boolean recording;
    private volatile int maxPeak;
    private TargetDataLine line;
    private Thread recordThread;
    private ByteArrayOutputStream pcmBuffer;
    private RecordedAudio lastResult;

    private final Object playLock = new Object();
    private Clip currentClip;
    private SourceDataLine currentSourceLine;
    private volatile boolean stopSourcePlayback;

    public static void setPreferredMicrophone(String name) {
        preferredMicrophone = (name == null || name.isBlank()) ? null : name;
    }

    public static String getPreferredMicrophone() {
        return preferredMicrophone;
    }

    /** Trả về true nếu tên mixer thuộc micro ảo / driver capture mặc định dễ bị im lặng. */
    public static boolean isVirtualMicName(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String kw : VIRTUAL_MIC_KEYWORDS) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /** Sắp xếp tên micro: micro vật lý lên đầu, micro ảo / Primary Sound Capture xuống cuối (giữ thứ tự tương đối). */
    public static List<String> rankMicrophones(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> physical = new ArrayList<>();
        List<String> virtual = new ArrayList<>();
        for (String name : names) {
            if (name == null || name.isBlank() || !seen.add(name)) {
                continue;
            }
            if (isVirtualMicName(name)) {
                virtual.add(name);
            } else {
                physical.add(name);
            }
        }
        List<String> ranked = new ArrayList<>(physical.size() + virtual.size());
        ranked.addAll(physical);
        ranked.addAll(virtual);
        return ranked;
    }

    /** Liệt kê toàn bộ micro hỗ trợ {@link #FORMAT}, ưu tiên micro vật lý trước. */
    public static List<String> listMicrophones() {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        List<String> supported = new ArrayList<>();
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            try {
                if (AudioSystem.getMixer(mi).isLineSupported(info)) {
                    supported.add(mi.getName());
                }
            } catch (IllegalArgumentException | SecurityException ignored) {
            }
        }
        return rankMicrophones(supported);
    }

    /**
     * Chọn và mở {@link TargetDataLine} tốt nhất cho {@code format}:
     * 1) Nếu {@code preferredName} có giá trị, thử mở đúng mixer đó trước.
     * 2) Thử lần lượt các mixer vật lý ({@code !isVirtualMicName}), rồi mới tới mixer ảo/mặc định.
     * 3) Fallback cuối cùng về {@link AudioSystem#getLine(javax.sound.sampled.Line.Info)}.
     */
    public static TargetDataLine openBestMicrophone(AudioFormat format, String preferredName)
            throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        Mixer.Info[] mixers = AudioSystem.getMixerInfo();
        LineUnavailableException lastError = null;

        if (preferredName != null && !preferredName.isBlank()) {
            for (Mixer.Info mi : mixers) {
                if (preferredName.equals(mi.getName())) {
                    TargetDataLine opened = tryOpenMixer(mi, info, format);
                    if (opened != null) {
                        return opened;
                    }
                }
            }
        }

        List<Mixer.Info> physical = new ArrayList<>();
        List<Mixer.Info> virtual = new ArrayList<>();
        for (Mixer.Info mi : mixers) {
            try {
                if (AudioSystem.getMixer(mi).isLineSupported(info)) {
                    if (isVirtualMicName(mi.getName())) {
                        virtual.add(mi);
                    } else {
                        physical.add(mi);
                    }
                }
            } catch (IllegalArgumentException | SecurityException ignored) {
            }
        }

        List<Mixer.Info> candidates = new ArrayList<>(physical.size() + virtual.size());
        candidates.addAll(physical);
        candidates.addAll(virtual);

        for (Mixer.Info mi : candidates) {
            try {
                TargetDataLine mic = (TargetDataLine) AudioSystem.getMixer(mi).getLine(info);
                mic.open(format);
                System.out.println("[AUDIO] Đã chọn micro: " + mi.getName());
                return mic;
            } catch (LineUnavailableException e) {
                lastError = e;
            } catch (IllegalArgumentException | SecurityException ignored) {
            }
        }

        try {
            TargetDataLine mic = (TargetDataLine) AudioSystem.getLine(info);
            mic.open(format);
            System.out.println("[AUDIO] Đã chọn micro: Default System Line");
            return mic;
        } catch (LineUnavailableException e) {
            throw lastError != null ? lastError : e;
        } catch (IllegalArgumentException e) {
            if (lastError != null) {
                throw lastError;
            }
            LineUnavailableException lue = new LineUnavailableException("No microphone supports " + format);
            lue.initCause(e);
            throw lue;
        }
    }

    private static TargetDataLine tryOpenMixer(Mixer.Info mi, DataLine.Info info, AudioFormat format) {
        try {
            Mixer mixer = AudioSystem.getMixer(mi);
            if (!mixer.isLineSupported(info)) {
                return null;
            }
            TargetDataLine mic = (TargetDataLine) mixer.getLine(info);
            mic.open(format);
            System.out.println("[AUDIO] Đã chọn micro: " + mi.getName());
            return mic;
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException ignored) {
            return null;
        }
    }

    /** Tính biên độ đỉnh (0..32767) của mảng PCM 16-bit signed little-endian. */
    public static int computePcmPeak(byte[] pcm) {
        return pcm == null ? 0 : computePcmPeak(pcm, pcm.length);
    }

    /** Tính biên độ đỉnh (0..32767) trên {@code length} byte đầu của mảng PCM 16-bit signed little-endian. */
    public static int computePcmPeak(byte[] pcm, int length) {
        if (pcm == null || length < 2) {
            return 0;
        }
        int limit = Math.min(length, pcm.length) & ~1;
        int peak = 0;
        for (int i = 0; i < limit; i += 2) {
            int sample = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            int abs = sample == Short.MIN_VALUE ? 32767 : Math.abs(sample);
            if (abs > peak) {
                peak = abs;
            }
        }
        return peak;
    }

    /** Khuếch đại nhẹ PCM 16-bit signed little-endian tại chỗ, kẹp trong [-32767, 32767] tránh vỡ tiếng. */
    private static void applyGain(byte[] pcm, double gain) {
        int limit = pcm.length & ~1;
        for (int i = 0; i < limit; i += 2) {
            int sample = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            int scaled = (int) Math.round(sample * gain);
            if (scaled > 32767) scaled = 32767;
            else if (scaled < -32767) scaled = -32767;
            pcm[i] = (byte) (scaled & 0xFF);
            pcm[i + 1] = (byte) ((scaled >> 8) & 0xFF);
        }
    }

    /** Mở micro và bắt đầu luồng nền thu âm PCM (tự dừng sau {@link #MAX_SECONDS} giây). */
    public synchronized void startRecording(Runnable onAutoStop) throws IOException {
        startRecording(onAutoStop, null);
    }

    /** Mở micro và bắt đầu thu âm kèm callback mức âm lượng (0..100) cho thanh VU. */
    public synchronized void startRecording(Runnable onAutoStop, IntConsumer onLevelPercent) throws IOException {
        if (recording) {
            throw new IOException("Recording already in progress");
        }
        TargetDataLine mic;
        try {
            mic = openBestMicrophone(FORMAT, preferredMicrophone);
            mic.start();
        } catch (LineUnavailableException | IllegalArgumentException e) {
            throw new IOException("Microphone unavailable: " + e.getMessage(), e);
        }

        this.line = mic;
        this.pcmBuffer = new ByteArrayOutputStream();
        this.lastResult = null;
        this.maxPeak = 0;
        this.recording = true;

        int maxBytes = (int) (FORMAT.getFrameRate() * FORMAT.getFrameSize() * MAX_SECONDS);
        this.recordThread = new Thread(() -> captureLoop(mic, maxBytes, onAutoStop, onLevelPercent), "audio-recorder");
        this.recordThread.setDaemon(true);
        this.recordThread.start();
    }

    /** Vòng lặp đọc dữ liệu PCM từ micro vào bộ đệm RAM cho tới khi người dùng dừng hoặc đạt 60s. */
    private void captureLoop(TargetDataLine mic, int maxBytes, Runnable onAutoStop, IntConsumer onLevelPercent) {
        byte[] chunk = new byte[2048];
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
                    int chunkPeak = computePcmPeak(chunk, n);
                    if (chunkPeak > maxPeak) {
                        maxPeak = chunkPeak;
                    }
                    if (onLevelPercent != null) {
                        int pct = Math.min(100, (int) Math.round(Math.sqrt(chunkPeak / 32767.0) * 100));
                        onLevelPercent.accept(pct);
                    }
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

    /** Dừng ghi âm, chờ luồng thu kết thúc và xuất ra file .wav kèm thời lượng (giây) và biên độ đỉnh. */
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

    /** Ghi mảng byte PCM đã thu thành file WAV chuẩn trong thư mục tạm (tự khuếch đại nhẹ nếu micro nhỏ). */
    private synchronized RecordedAudio finalizeWav() throws IOException {
        if (lastResult != null) {
            return lastResult;
        }
        byte[] pcm = pcmBuffer != null ? pcmBuffer.toByteArray() : new byte[0];
        int peak = Math.max(maxPeak, computePcmPeak(pcm));
        if (peak > 100 && peak < 12000) {
            double gain = Math.min(4.0, 16000.0 / peak);
            applyGain(pcm, gain);
        }
        long frameCount = pcm.length / FORMAT.getFrameSize();
        File wav = File.createTempFile("zalolite-voice-", ".wav");
        try (AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(pcm), FORMAT, frameCount)) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wav);
        }
        int durationSec = frameCount > 0 ? Math.max(1, (int) Math.round(frameCount / (double) FORMAT.getFrameRate())) : 0;
        lastResult = new RecordedAudio(wav, durationSec, peak);
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

    /** Phát file WAV tin nhắn thoại; dừng phiên phát trước đó (nếu có). */
    public void play(File wavFile, Runnable onFinished) throws IOException {
        stopPlayback();
        try {
            playWithClip(wavFile, onFinished);
            return;
        } catch (LineUnavailableException | IllegalArgumentException clipError) {
            // Fallback sang SourceDataLine khi Clip không hỗ trợ định dạng trên mixer mặc định
            try {
                playWithSourceDataLine(wavFile, onFinished);
            } catch (UnsupportedAudioFileException | LineUnavailableException | IllegalArgumentException fallbackErr) {
                throw new IOException("Cannot play audio: " + fallbackErr.getMessage(), clipError);
            }
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("Cannot play audio: " + e.getMessage(), e);
        }
    }

    private void playWithClip(File wavFile, Runnable onFinished)
            throws IOException, UnsupportedAudioFileException, LineUnavailableException {
        Clip clip;
        try (AudioInputStream raw = AudioSystem.getAudioInputStream(wavFile);
             AudioInputStream decoded = toPcmSigned16(raw)) {
            clip = AudioSystem.getClip();
            clip.open(decoded);
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

    private void playWithSourceDataLine(File wavFile, Runnable onFinished)
            throws IOException, UnsupportedAudioFileException, LineUnavailableException {
        byte[] pcmBytes;
        AudioFormat fmt;
        try (AudioInputStream raw = AudioSystem.getAudioInputStream(wavFile);
             AudioInputStream decoded = toPcmSigned16(raw)) {
            fmt = decoded.getFormat();
            pcmBytes = decoded.readAllBytes();
        }

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
        SourceDataLine speaker = (SourceDataLine) AudioSystem.getLine(info);
        speaker.open(fmt);
        synchronized (playLock) {
            stopSourcePlayback = false;
            currentSourceLine = speaker;
        }
        speaker.start();

        AtomicBoolean finished = new AtomicBoolean(false);
        Thread playThread = new Thread(() -> {
            try {
                int offset = 0;
                int chunk = 4096;
                while (!stopSourcePlayback && offset < pcmBytes.length) {
                    int len = Math.min(chunk, pcmBytes.length - offset);
                    speaker.write(pcmBytes, offset, len);
                    offset += len;
                }
                if (!stopSourcePlayback) {
                    speaker.drain();
                }
            } finally {
                synchronized (playLock) {
                    if (currentSourceLine == speaker) {
                        currentSourceLine = null;
                    }
                }
                try {
                    speaker.stop();
                } catch (Exception ignored) {
                }
                try {
                    speaker.close();
                } catch (Exception ignored) {
                }
                if (finished.compareAndSet(false, true) && onFinished != null) {
                    onFinished.run();
                }
            }
        }, "audio-player");
        playThread.setDaemon(true);
        playThread.start();
    }

    private static AudioInputStream toPcmSigned16(AudioInputStream raw) {
        AudioFormat base = raw.getFormat();
        if (base.getEncoding() == AudioFormat.Encoding.PCM_SIGNED && base.getSampleSizeInBits() == 16) {
            return raw;
        }
        AudioFormat target = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                base.getSampleRate(),
                16,
                base.getChannels(),
                base.getChannels() * 2,
                base.getSampleRate(),
                false
        );
        return AudioSystem.getAudioInputStream(target, raw);
    }

    /** Dừng phát tin nhắn thoại hiện tại và giải phóng Clip / SourceDataLine. */
    public void stopPlayback() {
        Clip clip;
        SourceDataLine speaker;
        synchronized (playLock) {
            clip = currentClip;
            currentClip = null;
            speaker = currentSourceLine;
            currentSourceLine = null;
            stopSourcePlayback = true;
        }
        if (clip != null) {
            clip.stop();
            clip.close();
        }
        if (speaker != null) {
            speaker.stop();
            speaker.close();
        }
    }
}
