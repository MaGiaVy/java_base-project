package com.zalolite.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AudioHelperTest {

    @Test
    void ranksPhysicalMicrophoneAheadOfVirtualMics() {
        assertTrue(AudioHelper.isVirtualMicName("Primary Sound Capture Driver"));
        assertTrue(AudioHelper.isVirtualMicName("Microphone (Camo)"));
        assertTrue(AudioHelper.isVirtualMicName("OBS Virtual Audio"));
        assertTrue(AudioHelper.isVirtualMicName("CABLE Output (VB-Audio Virtual Cable)"));
        assertTrue(AudioHelper.isVirtualMicName("VoiceMeeter Out B1"));
        assertTrue(AudioHelper.isVirtualMicName("Stereo Mix (Realtek)"));
        assertTrue(AudioHelper.isVirtualMicName("DroidCam Virtual Audio"));
        assertFalse(AudioHelper.isVirtualMicName("Microphone Array (Intel Smart Sound)"));

        List<String> ranked = AudioHelper.rankMicrophones(List.of(
                "Primary Sound Capture Driver",
                "Microphone (Camo)",
                "Microphone Array (Intel Smart Sound)"
        ));

        assertEquals(List.of(
                "Microphone Array (Intel Smart Sound)",
                "Primary Sound Capture Driver",
                "Microphone (Camo)"
        ), ranked);
    }

    @Test
    void computesPeakAndDurationOnGeneratedWav(@TempDir Path tempDir) throws Exception {
        int sampleRate = (int) AudioHelper.FORMAT.getSampleRate();
        int sampleCount = sampleRate; // 1 second
        byte[] pcm = new byte[sampleCount * 2];
        double freq = 440.0;
        double amplitude = 20000.0;

        for (int i = 0; i < sampleCount; i++) {
            short s = (short) Math.round(Math.sin(2.0 * Math.PI * freq * i / sampleRate) * amplitude);
            pcm[i * 2] = (byte) (s & 0xFF);
            pcm[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }

        File wav = tempDir.resolve("sine-440hz.wav").toFile();
        try (AudioInputStream ais = new AudioInputStream(
                new ByteArrayInputStream(pcm), AudioHelper.FORMAT, sampleCount)) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, wav);
        }

        assertEquals(1, AudioHelper.wavDurationSec(wav));
        int peak = AudioHelper.computePcmPeak(pcm);
        assertTrue(peak > 10000, "Expected peak > 10000 for 440Hz sine wave, got " + peak);
        assertEquals(0, AudioHelper.computePcmPeak(new byte[320]));
    }
}
