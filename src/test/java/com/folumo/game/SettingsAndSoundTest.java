package com.folumo.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SettingsAndSoundTest {
    @Test
    void settingsRoundTripAndBadFile(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("s.properties");
        assertEquals(70, Settings.load(f).volume, "defaults when missing");

        Settings s = new Settings();
        s.sound = false;
        s.volume = 25;
        s.popups = false;
        s.save(f);
        Settings l = Settings.load(f);
        assertFalse(l.sound);
        assertEquals(25, l.volume);
        assertFalse(l.popups);

        Files.writeString(f, "volume=loud");
        assertEquals(70, Settings.load(f).volume, "garbage falls back to defaults");
    }

    @Test
    void modSoundsLoadFromWav(@TempDir Path dir) throws Exception {
        // stereo 16-bit WAV: left full, right silent -> mono at half volume
        byte[] pcm = new byte[100 * 4];
        for (int i = 0; i < 100; i++) {
            pcm[i * 4] = (byte) 0xff;
            pcm[i * 4 + 1] = 0x3f; // 0x3fff ≈ 0.5
        }
        javax.sound.sampled.AudioFormat fmt = new javax.sound.sampled.AudioFormat(44100, 16, 2, true, false);
        Path wav = dir.resolve("gather.wav");
        javax.sound.sampled.AudioSystem.write(new javax.sound.sampled.AudioInputStream(new java.io.ByteArrayInputStream(pcm), fmt, 100),
                javax.sound.sampled.AudioFileFormat.Type.WAVE, wav.toFile());

        float[] data = Sfx.readWav(wav);
        assertEquals(100, data.length);
        assertEquals(.25, data[0], .01);

        Files.writeString(dir.resolve("bad.wav"), "nope");
        assertThrows(IllegalArgumentException.class, () -> Sfx.readWav(dir.resolve("bad.wav")));
    }

    @Test
    void soundsAreAudibleAndNeverClip() {
        for (Sfx.Sound s : Sfx.Sound.values()) {
            float peak = 0;
            for (float v : s.data) peak = Math.max(peak, Math.abs(v));
            assertTrue(peak > .05 && peak <= 1, s + " peak " + peak);
        }
    }
}
