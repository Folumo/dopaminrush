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
    void soundsAreAudibleAndNeverClip() {
        for (Sfx.Sound s : Sfx.Sound.values()) {
            float peak = 0;
            for (float v : s.data) peak = Math.max(peak, Math.abs(v));
            assertTrue(peak > .05 && peak <= 1, s + " peak " + peak);
        }
    }
}
