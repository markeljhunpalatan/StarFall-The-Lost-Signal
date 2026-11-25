package starlightrescue;

import java.util.Random;

/**
 * Builds every sound effect and music track out of maths, so the game needs
 * no audio files. Each sound is just a short list of numbers (samples).
 *
 * Want to tweak a sound? Change the numbers in sfx() below. Want to use real
 * recordings instead? Drop WAV files into resources/sounds/ (see SoundManager).
 */
final class SoundSynth {

    static final int RATE = 22050;

    private static final Random RNG = new Random(7);

    enum Wave { SINE, SQUARE, SAW, TRI }

    private SoundSynth() { }

    // ===============================================================
    // SOUND EFFECTS
    // ===============================================================

    static short[] sfx(SoundManager.Sfx which) {

        float[] b;

        switch (which) {

            case SHOOT:
                b = buf(0.12);
                tone(b, 0, 0.12, 1250, 300, Wave.SQUARE, 1.0, 0.002, 2.0);
                return finish(b, 0.17);

            case EMPTY:
                b = buf(0.08);
                tone(b, 0, 0.05, 220, 140, Wave.SQUARE, 1.0, 0.001, 1.5);
                noise(b, 0, 0.03, 0.6, 2.0, 0.9, 0.5);
                return finish(b, 0.25);

            case RELOAD_START:
                b = buf(0.4);
                noise(b, 0.00, 0.04, 1.0, 2.0, 0.6, 0.3);
                tone(b, 0.00, 0.08, 160, 90, Wave.SINE, 0.8, 0.001, 2.0);
                noise(b, 0.16, 0.05, 0.8, 2.0, 0.5, 0.2);
                tone(b, 0.16, 0.10, 120, 70, Wave.SINE, 0.7, 0.001, 2.0);
                return finish(b, 0.35);

            case RELOAD_DONE:
                b = buf(0.3);
                noise(b, 0.00, 0.03, 0.9, 2.0, 0.7, 0.4);
                tone(b, 0.04, 0.10, 880, 880, Wave.TRI, 0.7, 0.002, 1.5);
                tone(b, 0.11, 0.16, 1320, 1320, Wave.TRI, 0.7, 0.002, 1.5);
                return finish(b, 0.30);

            case EXPLODE:
                b = buf(0.55);
                noise(b, 0, 0.5, 1.0, 1.6, 0.55, 0.04);
                tone(b, 0, 0.3, 110, 38, Wave.SINE, 1.0, 0.002, 1.8);
                return finish(b, 0.55);

            case BIG_EXPLODE:
                b = buf(1.5);
                noise(b, 0, 1.4, 1.0, 1.4, 0.45, 0.02);
                tone(b, 0, 1.1, 90, 28, Wave.SINE, 1.0, 0.004, 1.5);
                noise(b, 0.25, 0.4, 0.7, 2.0, 0.3, 0.05);
                return finish(b, 0.75);

            case HIT:
                b = buf(0.35);
                tone(b, 0, 0.3, 520, 70, Wave.SAW, 0.8, 0.002, 1.5);
                noise(b, 0, 0.22, 0.8, 1.8, 0.7, 0.1);
                return finish(b, 0.55);

            case SHIELD_BREAK:
                b = buf(0.35);
                noise(b, 0, 0.3, 0.8, 2.0, 1.0, 0.5);
                tone(b, 0, 0.25, 1900, 500, Wave.SINE, 0.8, 0.002, 1.5);
                return finish(b, 0.40);

            case PICKUP:
                b = buf(0.32);
                tone(b, 0.00, 0.08, hz(72), hz(72), Wave.TRI, 1.0, 0.002, 1.2);
                tone(b, 0.06, 0.08, hz(76), hz(76), Wave.TRI, 1.0, 0.002, 1.2);
                tone(b, 0.12, 0.08, hz(79), hz(79), Wave.TRI, 1.0, 0.002, 1.2);
                tone(b, 0.18, 0.14, hz(84), hz(84), Wave.TRI, 1.0, 0.002, 1.2);
                return finish(b, 0.35);

            case UPGRADE:
                b = buf(0.8);
                tone(b, 0.00, 0.35, 300, 1700, Wave.SINE, 0.8, 0.01, 0.8);
                tone(b, 0.15, 0.2, hz(84), hz(84), Wave.TRI, 0.7, 0.002, 1.2);
                tone(b, 0.25, 0.2, hz(88), hz(88), Wave.TRI, 0.7, 0.002, 1.2);
                tone(b, 0.35, 0.4, hz(91), hz(91), Wave.TRI, 0.8, 0.002, 1.0);
                tone(b, 0.35, 0.4, hz(96), hz(96), Wave.SINE, 0.6, 0.002, 1.0);
                return finish(b, 0.45);

            case WARNING:
                b = buf(1.6);
                for (int i = 0; i < 4; i++) {
                    tone(b, i * 0.4, 0.2, 620, 620, Wave.SQUARE, 1.0, 0.01, 0.4);
                    tone(b, i * 0.4 + 0.2, 0.2, 440, 440, Wave.SQUARE, 1.0, 0.01, 0.4);
                }
                smooth(b, 0.45f);
                return finish(b, 0.32);

            case BOSS_HIT:
                b = buf(0.1);
                tone(b, 0, 0.08, 150, 80, Wave.SINE, 1.0, 0.001, 2.0);
                noise(b, 0, 0.04, 0.6, 2.0, 0.6, 0.3);
                return finish(b, 0.22);

            case CLICK:
                b = buf(0.06);
                tone(b, 0, 0.05, 1400, 1100, Wave.SINE, 1.0, 0.001, 2.0);
                return finish(b, 0.22);

            case MISSION_COMPLETE:
                b = buf(1.8);
                tone(b, 0.00, 0.20, hz(72), hz(72), Wave.SQUARE, 0.6, 0.004, 1.0);
                tone(b, 0.20, 0.20, hz(76), hz(76), Wave.SQUARE, 0.6, 0.004, 1.0);
                tone(b, 0.40, 0.20, hz(79), hz(79), Wave.SQUARE, 0.6, 0.004, 1.0);
                tone(b, 0.60, 1.10, hz(84), hz(84), Wave.SQUARE, 0.5, 0.004, 0.8);
                tone(b, 0.60, 1.10, hz(79), hz(79), Wave.TRI, 0.6, 0.004, 0.8);
                tone(b, 0.60, 1.10, hz(72), hz(72), Wave.TRI, 0.6, 0.004, 0.8);
                smooth(b, 0.55f);
                return finish(b, 0.40);

            case GAME_OVER:
                b = buf(2.0);
                tone(b, 0.00, 0.45, hz(67), hz(67), Wave.TRI, 0.8, 0.01, 0.8);
                tone(b, 0.40, 0.45, hz(63), hz(63), Wave.TRI, 0.8, 0.01, 0.8);
                tone(b, 0.80, 0.45, hz(60), hz(60), Wave.TRI, 0.8, 0.01, 0.8);
                tone(b, 1.20, 0.75, hz(55), hz(48), Wave.TRI, 0.9, 0.01, 0.7);
                tone(b, 1.20, 0.75, hz(43), hz(36), Wave.SINE, 0.8, 0.01, 0.7);
                return finish(b, 0.45);

            case VICTORY:
                b = buf(3.4);
                int[] notes = {72, 76, 79, 84, 79, 84, 88, 91};
                for (int i = 0; i < notes.length; i++) {
                    tone(b, i * 0.18, 0.3, hz(notes[i]), hz(notes[i]), Wave.SQUARE, 0.5, 0.004, 1.0);
                }
                tone(b, 1.5, 1.8, hz(84), hz(84), Wave.SQUARE, 0.45, 0.01, 0.7);
                tone(b, 1.5, 1.8, hz(79), hz(79), Wave.TRI, 0.6, 0.01, 0.7);
                tone(b, 1.5, 1.8, hz(76), hz(76), Wave.TRI, 0.6, 0.01, 0.7);
                tone(b, 1.5, 1.8, hz(60), hz(60), Wave.SINE, 0.7, 0.01, 0.7);
                smooth(b, 0.55f);
                return finish(b, 0.42);

            default:
                return new short[0];
        }
    }

    // ===============================================================
    // MUSIC (each track loops seamlessly)
    // ===============================================================

    static short[] music(SoundManager.Track track) {

        switch (track) {
            case MENU:
                return musicMenu();
            case BOSS:
                return musicBoss();
            default:
                return musicGame();
        }
    }

    /** Calm, spacey chords for menus and story scenes. */
    private static short[] musicMenu() {

        double bpm = 66;
        double beat = 60.0 / bpm;
        double bar = beat * 4;
        int bars = 8;
        float[] b = buf(bar * bars);

        int[] roots = {45, 41, 48, 43, 45, 41, 48, 43};          // A F C G, twice
        boolean[] minor = {true, false, false, false, true, false, false, false};

        for (int i = 0; i < bars; i++) {

            double t0 = i * bar;
            int r = roots[i];
            int third = r + (minor[i] ? 3 : 4);
            int fifth = r + 7;

            // Soft pad
            int[] padNotes = {r + 12, third + 12, fifth + 12};
            for (int n : padNotes) {
                tone(b, t0, bar * 1.15, hz(n), hz(n), Wave.TRI, 0.30, 0.9, 0.35);
            }

            // Deep bass
            tone(b, t0, bar * 1.05, hz(r), hz(r), Wave.SINE, 0.5, 0.2, 0.3);

            // Twinkling arpeggio
            int[] chord = {r + 24, third + 24, fifth + 24};
            int[] pattern = {0, 2, 1, 2};
            for (int s = 0; s < 4; s++) {
                int note = chord[pattern[s]] + (s == 3 ? 12 : 0);
                tone(b, t0 + s * beat, beat * 1.6, hz(note), hz(note), Wave.SINE, 0.22, 0.01, 2.0);
            }
        }

        smooth(b, 0.45f);
        return finish(b, 0.45);
    }

    /** Steady, driving track for normal gameplay. */
    private static short[] musicGame() {

        double bpm = 118;
        double beat = 60.0 / bpm;
        double bar = beat * 4;
        double step = beat / 4;
        int bars = 8;
        float[] b = buf(bar * bars);

        int[] roots = {45, 41, 48, 43, 45, 41, 43, 40};          // A F C G A F G E
        boolean[] minor = {true, false, false, false, true, false, false, false};
        int[] pattern = {0, 1, 2, 1, 0, 1, 2, 1, 0, 1, 2, 1, 0, 2, 1, 2};

        for (int i = 0; i < bars; i++) {

            double t0 = i * bar;
            int r = roots[i];
            int third = r + (minor[i] ? 3 : 4);
            int fifth = r + 7;
            int[] chord = {r + 24, third + 24, fifth + 24};

            for (int s = 0; s < 16; s++) {

                double t = t0 + s * step;

                if (s % 2 == 0) {
                    int note = (s % 8 == 4) ? r + 12 : r;
                    tone(b, t, step * 1.8, hz(note), hz(note), Wave.SQUARE, 0.30, 0.004, 1.2);
                }

                int a = chord[pattern[s]];
                tone(b, t, step * 0.9, hz(a), hz(a), Wave.TRI, 0.14, 0.003, 1.5);

                if (s == 0 || s == 8) {
                    kick(b, t, 0.8);
                }

                if (s % 4 == 2) {
                    hat(b, t, 0.16);
                }
            }
        }

        smooth(b, 0.5f);
        return finish(b, 0.45);
    }

    /** Faster and darker for boss fights. */
    private static short[] musicBoss() {

        double bpm = 146;
        double beat = 60.0 / bpm;
        double bar = beat * 4;
        double step = beat / 4;
        int bars = 8;
        float[] b = buf(bar * bars);

        int[] roots = {38, 34, 43, 45, 38, 34, 45, 45};          // D Bb G A D Bb A A
        boolean[] minor = {true, false, true, false, true, false, false, false};
        int[] pattern = {0, 1, 2, 1, 2, 1, 0, 1, 0, 1, 2, 1, 2, 1, 2, 1};

        for (int i = 0; i < bars; i++) {

            double t0 = i * bar;
            int r = roots[i];
            int third = r + (minor[i] ? 3 : 4);
            int fifth = r + 7;
            int[] chord = {r + 24, third + 24, fifth + 24};

            // A heavy stab on the first beat of each bar
            for (int n : new int[]{r + 12, third + 12, fifth + 12}) {
                tone(b, t0, beat * 1.2, hz(n), hz(n), Wave.SAW, 0.16, 0.004, 1.5);
            }

            for (int s = 0; s < 16; s++) {

                double t = t0 + s * step;

                int bass = (s % 4 == 3) ? r + 12 : r;
                tone(b, t, step * 0.95, hz(bass), hz(bass), Wave.SQUARE, 0.28, 0.003, 1.0);

                int a = chord[pattern[s]];
                tone(b, t, step * 0.8, hz(a), hz(a), Wave.SAW, 0.09, 0.003, 1.3);

                if (s % 4 == 0) {
                    kick(b, t, 0.9);
                }

                if (s == 4 || s == 12) {
                    snare(b, t, 0.6);
                }

                if (s % 2 == 0) {
                    hat(b, t, 0.14);
                }
            }
        }

        smooth(b, 0.5f);
        return finish(b, 0.45);
    }

    // ===============================================================
    // BUILDING BLOCKS
    // ===============================================================

    private static double hz(int midi) {
        return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
    }

    private static float[] buf(double seconds) {
        return new float[(int) (seconds * RATE)];
    }

    /**
     * Adds one tone to the buffer.
     * f0 -> f1 is a pitch sweep, attack is the fade-in time and
     * shape controls how fast it dies away (bigger = faster).
     */
    private static void tone(float[] b, double start, double dur, double f0, double f1,
                             Wave wave, double vol, double attack, double shape) {

        int first = (int) (start * RATE);
        int count = (int) (dur * RATE);
        double phase = 0;

        for (int i = 0; i < count; i++) {

            double t = (double) i / count;                     // 0.0 - 1.0
            phase += (f0 + (f1 - f0) * t) / RATE;
            double p = phase - Math.floor(phase);

            double v;
            switch (wave) {
                case SQUARE: v = (p < 0.5) ? 1.0 : -1.0; break;
                case SAW:    v = 2.0 * p - 1.0; break;
                case TRI:    v = 4.0 * Math.abs(p - 0.5) - 1.0; break;
                default:     v = Math.sin(2.0 * Math.PI * p); break;
            }

            double seconds = (double) i / RATE;
            double envelope = Math.min(1.0, seconds / Math.max(attack, 0.0001))
                            * Math.pow(1.0 - t, shape);

            b[Math.floorMod(first + i, b.length)] += (float) (v * envelope * vol);
        }
    }

    /** Adds a burst of noise. a0 -> a1 is how "bright" it is (1 = hissy, small = rumbly). */
    private static void noise(float[] b, double start, double dur, double vol,
                              double shape, double a0, double a1) {

        int first = (int) (start * RATE);
        int count = (int) (dur * RATE);
        double y = 0;

        for (int i = 0; i < count; i++) {

            double t = (double) i / count;
            double a = a0 + (a1 - a0) * t;
            double x = RNG.nextDouble() * 2.0 - 1.0;

            y += a * (x - y);                                   // simple low-pass filter

            double envelope = Math.pow(1.0 - t, shape);
            b[Math.floorMod(first + i, b.length)] += (float) (y * envelope * vol);
        }
    }

    private static void kick(float[] b, double t, double vol) {
        tone(b, t, 0.16, 150, 45, Wave.SINE, vol, 0.001, 2.0);
    }

    private static void snare(float[] b, double t, double vol) {
        noise(b, t, 0.12, vol, 2.0, 0.8, 0.5);
        tone(b, t, 0.08, 220, 160, Wave.TRI, vol * 0.5, 0.001, 2.0);
    }

    private static void hat(float[] b, double t, double vol) {
        noise(b, t, 0.04, vol, 2.0, 1.0, 1.0);
    }

    /** Softens harsh square/saw waves. */
    private static void smooth(float[] b, float a) {

        float y = b[b.length - 1];     // start where the loop ends, so there is no click at the seam

        for (int i = 0; i < b.length; i++) {
            y += a * (b[i] - y);
            b[i] = y;
        }
    }

    /** Scales the loudest point to "level" (0 - 1) and converts to 16-bit samples. */
    private static short[] finish(float[] b, double level) {

        float peak = 0.0001f;

        for (float v : b) {
            peak = Math.max(peak, Math.abs(v));
        }

        double scale = level * 32767.0 / peak;
        short[] out = new short[b.length];

        for (int i = 0; i < b.length; i++) {
            out[i] = (short) Math.max(-32768, Math.min(32767, Math.round(b[i] * scale)));
        }

        return out;
    }
}
