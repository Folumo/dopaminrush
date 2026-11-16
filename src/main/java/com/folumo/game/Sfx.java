package com.folumo.game;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;

/** Sound effects synthesised in code (no audio files), mixed on one audio thread so rapid clicks never lag. */
final class Sfx {
    static final float RATE = 44100f;

    enum Sound {
        GATHER(Sfx::gather), SELECT(Sfx::select), BUY(Sfx::buy), BUY_IMPORTANT(Sfx::buyImportant),
        MILESTONE(Sfx::milestone), DENIED(Sfx::denied);

        final float[] data;

        Sound(Supplier<float[]> make) {
            data = make.get();
        }
    }

    private static final class Voice {
        final float[] data;
        final double step;
        double pos;

        Voice(float[] data, double step) {
            this.data = data;
            this.step = step;
        }
    }

    private final List<Voice> voices = new ArrayList<>();
    private final Map<Sound, float[]> replaced = new EnumMap<>(Sound.class);
    private volatile float gain = .5f;
    private volatile float[] ambience; // looping background for the current era, or null
    private int ambiencePos;            // only touched by the audio thread
    private final Map<Integer, float[]> ambienceCache = new HashMap<>();
    static final int ERAS = 5;

    /**
     * Replaces a built-in sound with a mod's WAV file.
     * @param name sound name such as "gather" (case-insensitive)
     * @throws IllegalArgumentException for an unknown name or an unreadable / unsupported file
     */
    void replace(String name, Path wav) {
        Sound s;
        try {
            s = Sound.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown sound '" + name + "' (use one of: "
                    + Arrays.stream(Sound.values()).map(x -> x.name().toLowerCase(Locale.ROOT)).toList() + ")");
        }
        float[] data = readWav(wav);
        synchronized (voices) {
            replaced.put(s, data);
        }
    }

    /** 16-bit (or 8-bit) PCM WAV at 44100 Hz, mono or stereo, read as mono samples in -1..1. */
    static float[] readWav(Path file) {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(file.toFile())) {
            AudioFormat src = in.getFormat();
            if (Math.abs(src.getSampleRate() - RATE) > 1)
                throw new IllegalArgumentException(file.getFileName() + " must be 44100 Hz, not " + (int) src.getSampleRate() + " Hz");
            int ch = src.getChannels();
            AudioFormat pcm = new AudioFormat(RATE, 16, ch, true, false);
            byte[] b;
            try (AudioInputStream conv = AudioSystem.getAudioInputStream(pcm, in)) {
                b = conv.readAllBytes();
            }
            float[] out = new float[b.length / 2 / ch];
            for (int i = 0; i < out.length; i++) {
                float sum = 0;
                for (int c = 0; c < ch; c++) {
                    int k = (i * ch + c) * 2;
                    sum += (short) ((b[k] & 0xff) | (b[k + 1] << 8)) / 32768f;
                }
                out[i] = sum / ch; // stereo is mixed down to mono
            }
            if (out.length < 2) throw new IllegalArgumentException(file.getFileName() + " is empty");
            return out;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(file.getFileName() + " can't be read (" + e.getMessage() + "); use a 16-bit PCM WAV at 44100 Hz");
        }
    }

    Sfx() {
        Thread t = new Thread(this::run, "sfx");
        t.setDaemon(true);
        t.start();
    }

    /** @param volume 0..100, 0 = silent */
    void setVolume(int volume) {
        float v = Math.max(0, Math.min(100, volume)) / 100f;
        gain = v * v; // ears hear loudness roughly logarithmically
    }

    /** @param era 0..ERAS-1 picks the looping background sound; -1 turns it off */
    void setAmbience(int era) {
        ambience = era < 0 ? null : ambienceCache.computeIfAbsent(Math.min(era, ERAS - 1), Sfx::ambience);
    }

    void play(Sound s) {
        play(s, 1);
    }

    /** @param pitch playback speed, 1 = normal; small random changes keep repeated clicks from sounding robotic */
    void play(Sound s, double pitch) {
        if (gain <= 0) return;
        synchronized (voices) {
            if (voices.size() < 16) voices.add(new Voice(replaced.getOrDefault(s, s.data), pitch));
        }
    }

    private void run() {
        AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(fmt)) {
            line.open(fmt, 4096); // ~46 ms buffer keeps clicks snappy
            line.start();
            int frames = 512;
            float[] mix = new float[frames];
            byte[] out = new byte[frames * 2];
            while (true) {
                Arrays.fill(mix, 0);
                synchronized (voices) {
                    for (Iterator<Voice> it = voices.iterator(); it.hasNext(); ) {
                        Voice v = it.next();
                        for (int i = 0; i < frames && v.pos < v.data.length - 1; i++) {
                            int p = (int) v.pos;
                            double f = v.pos - p;
                            mix[i] += (float) (v.data[p] * (1 - f) + v.data[p + 1] * f);
                            v.pos += v.step;
                        }
                        if (v.pos >= v.data.length - 1) it.remove();
                    }
                }
                float[] amb = ambience;
                if (amb != null) for (int i = 0; i < frames; i++) {
                    if (ambiencePos >= amb.length) ambiencePos = 0;
                    mix[i] += amb[ambiencePos++] * .6f; // quieter than the effects
                }
                float g = gain;
                for (int i = 0; i < frames; i++) {
                    int s = Math.round(Math.max(-1, Math.min(1, mix[i] * g)) * 32767);
                    out[i * 2] = (byte) s;
                    out[i * 2 + 1] = (byte) (s >> 8);
                }
                line.write(out, 0, out.length); // blocks until there's room, which paces this loop
            }
        } catch (Exception e) {
            System.err.println("Sound disabled: " + e.getMessage());
        }
    }

    // ---- synthesis ----

    private static float[] buf(double seconds) {
        return new float[(int) (seconds * RATE)];
    }

    /** Bell-like note: sine plus soft overtones, quick attack, exponential fade. */
    private static void note(float[] b, double freq, double start, double amp, double decay, double attack) {
        int s0 = (int) (start * RATE);
        for (int i = s0; i < b.length; i++) {
            double t = (i - s0) / RATE;
            double env = Math.min(1, t / attack) * Math.exp(-t * decay);
            double w = 2 * Math.PI * freq * t;
            b[i] += (float) (amp * env * (Math.sin(w) + .25 * Math.sin(2 * w) + .1 * Math.sin(3 * w)));
        }
    }

    private static float[] gather() { // soft rising "bloop"
        float[] b = buf(.12);
        double phase = 0;
        for (int i = 0; i < b.length; i++) {
            double t = i / RATE;
            phase += 2 * Math.PI * (520 + 3000 * t) / RATE;
            b[i] = (float) (.35 * Math.min(1, t / .003) * Math.exp(-t * 35) * Math.sin(phase));
        }
        return b;
    }

    private static float[] select() {
        float[] b = buf(.06);
        note(b, 1400, 0, .12, 90, .002);
        return b;
    }

    private static float[] buy() { // two-note chime
        float[] b = buf(.5);
        note(b, 784, 0, .22, 9, .004);
        note(b, 1175, .07, .2, 8, .004);
        return b;
    }

    private static float[] buyImportant() { // rising arpeggio
        float[] b = buf(.8);
        double[] f = {1047, 1319, 1568};
        for (int i = 0; i < f.length; i++) note(b, f[i], i * .07, .17, 6, .004);
        return b;
    }

    private static float[] milestone() { // swelling chord with a shimmer on top
        float[] b = buf(1.8);
        double[] chord = {523, 659, 784, 1047};
        for (int i = 0; i < chord.length; i++) note(b, chord[i], i * .05, .12, 2.5, .06);
        double[] sparkle = {2093, 2637, 3136, 4186};
        for (int i = 0; i < sparkle.length; i++) note(b, sparkle[i], .3 + i * .09, .05, 6, .004);
        return b;
    }

    /**
     * An 8-second loop for each era: 0 primordial soup, 1 bodies, 2 early humans, 3 civilisation, 4 the digital age.
     * Drones use frequencies that fit the loop a whole number of times, and the ends fade, so the loop has no click.
     */
    static float[] ambience(int era) {
        Random r = new Random(era * 31L + 7);
        float[] b = buf(8);
        switch (era) {
            case 0 -> { // deep drone and bubbles
                hum(b, .05, 55, 82.5);
                for (int k = 0; k < 40; k++) {
                    int s0 = r.nextInt(b.length - 6000);
                    double f0 = 300 + r.nextDouble() * 500, amp = .03 + r.nextDouble() * .03, phase = 0;
                    for (int i = 0; i < 4000; i++) {
                        double t = i / RATE;
                        phase += 2 * Math.PI * (f0 + 4000 * t) / RATE;
                        b[s0 + i] += (float) (amp * Math.sin(Math.PI * i / 4000) * Math.sin(phase));
                    }
                }
            }
            case 1 -> { // a slow heartbeat over a body hum
                hum(b, .03, 40);
                for (int beat = 0; beat < 8; beat++) {
                    note(b, 55, beat, .3, 18, .005);
                    note(b, 50, beat + .28, .22, 18, .005);
                }
            }
            case 2 -> { // fire: low rumble and crackles
                noise(b, r, .02, .5);
                for (int k = 0; k < 160; k++) {
                    int s0 = r.nextInt(b.length - 400);
                    double amp = .05 + r.nextDouble() * .2;
                    for (int i = 0; i < 300; i++) b[s0 + i] += (float) (amp * (r.nextDouble() * 2 - 1) * Math.exp(-i / 40.0));
                }
            }
            case 3 -> { // city: traffic hum and a distant horn
                noise(b, r, .05, .25);
                hum(b, .015, 100);
                for (int k = 0; k < 2; k++) {
                    double at = 1 + r.nextDouble() * 5;
                    note(b, 330, at, .025, 2.5, .05);
                    note(b, 415, at, .02, 2.5, .05);
                }
            }
            default -> { // digital age: electrical hum and notification pings
                hum(b, .02, 50, 100, 150);
                double[] pings = {1568, 2093, 2637};
                for (int k = 0; k < 10; k++) note(b, pings[r.nextInt(pings.length)], r.nextDouble() * 7.5, .04, 12, .003);
            }
        }
        int fade = (int) (.03 * RATE);
        for (int i = 0; i < fade; i++) {
            b[i] *= i / (float) fade;
            b[b.length - 1 - i] *= i / (float) fade;
        }
        return b;
    }

    /** Steady sine tones; each frequency should fit an 8 s loop a whole number of times (any multiple of 0.125 Hz). */
    private static void hum(float[] b, double amp, double... freqs) {
        for (int i = 0; i < b.length; i++)
            for (double f : freqs) b[i] += (float) (amp * Math.sin(2 * Math.PI * f * i / RATE));
    }

    /** Low-passed noise: a small {@code smooth} gives a deep rumble. */
    private static void noise(float[] b, Random r, double smooth, double amp) {
        double y = 0;
        for (int i = 0; i < b.length; i++) {
            y += smooth * ((r.nextDouble() * 2 - 1) - y);
            b[i] += (float) (amp * y);
        }
    }

    private static float[] denied() { // low soft thud
        float[] b = buf(.2);
        note(b, 160, 0, .3, 25, .004);
        note(b, 120, .05, .25, 25, .004);
        return b;
    }
}
