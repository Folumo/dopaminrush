package com.folumo.game;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
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
    private volatile float gain = .5f;

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

    void play(Sound s) {
        play(s, 1);
    }

    /** @param pitch playback speed, 1 = normal; small random changes keep repeated clicks from sounding robotic */
    void play(Sound s, double pitch) {
        if (gain <= 0) return;
        synchronized (voices) {
            if (voices.size() < 16) voices.add(new Voice(s.data, pitch));
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

    private static float[] denied() { // low soft thud
        float[] b = buf(.2);
        note(b, 160, 0, .3, 25, .004);
        note(b, 120, .05, .25, 25, .004);
        return b;
    }
}
