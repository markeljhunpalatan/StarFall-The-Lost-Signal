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

            case COIN:
                b = buf(0.22);
                tone(b, 0.00, 0.07, hz(88), hz(88), Wave.SINE, 1.0, 0.001, 1.4);
                tone(b, 0.06, 0.16, hz(95), hz(95), Wave.SINE, 1.0, 0.001, 1.8);
                return finish(b, 0.16);

            case ALERT:
                b = buf(0.5);
                tone(b, 0.00, 0.12, 900, 900, Wave.SQUARE, 1.0, 0.004, 0.6);
                tone(b, 0.20, 0.12, 900, 900, Wave.SQUARE, 1.0, 0.004, 0.6);
                smooth(b, 0.5f);
                return finish(b, 0.22);

            case CHARGE:
                b = buf(0.9);
                tone(b, 0.0, 0.85, 120, 900, Wave.SAW, 0.8, 0.05, 0.3);
                tone(b, 0.0, 0.85, 240, 1800, Wave.SINE, 0.5, 0.05, 0.3);
                smooth(b, 0.35f);
                return finish(b, 0.30);

            case LASER:
                b = buf(0.8);
                tone(b, 0.0, 0.75, 180, 150, Wave.SAW, 1.0, 0.005, 0.6);
                tone(b, 0.0, 0.75, 362, 300, Wave.SQUARE, 0.5, 0.005, 0.6);
                noise(b, 0.0, 0.75, 0.6, 0.8, 0.7, 0.4);
                smooth(b, 0.4f);
                return finish(b, 0.40);

            case MISSILE:
                b = buf(0.45);
                noise(b, 0.0, 0.42, 1.0, 1.2, 0.25, 0.6);
                tone(b, 0.0, 0.3, 300, 700, Wave.SINE, 0.5, 0.01, 1.2);
                return finish(b, 0.30);

            case BOOST:
                b = buf(0.35);
                noise(b, 0.0, 0.32, 1.0, 1.5, 0.15, 0.9);
                tone(b, 0.0, 0.25, 200, 900, Wave.SINE, 0.5, 0.005, 1.5);
                return finish(b, 0.32);

            case TELEPORT:
                b = buf(0.5);
                tone(b, 0.0, 0.45, 1500, 200, Wave.SINE, 1.0, 0.005, 1.0);
                tone(b, 0.0, 0.45, 1520, 210, Wave.TRI, 0.6, 0.005, 1.0);
                return finish(b, 0.30);

            case REFUND:
                b = buf(0.35);
                tone(b, 0.00, 0.10, hz(91), hz(91), Wave.SINE, 1.0, 0.001, 1.4);
                tone(b, 0.08, 0.10, hz(86), hz(86), Wave.SINE, 1.0, 0.001, 1.4);
                tone(b, 0.16, 0.16, hz(79), hz(79), Wave.SINE, 1.0, 0.001, 1.6);
                return finish(b, 0.26);

            case UNDO:
                b = buf(0.22);
                tone(b, 0.0, 0.2, 900, 400, Wave.TRI, 1.0, 0.002, 1.5);
                return finish(b, 0.26);

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
    // BOSS SOUNDS (each boss has its own voice)
    // ===============================================================

    // Each boss's voice: 0 Vanguard, 1 Warship, 2 Mothership, 3 Dreadnought, 4 Hive Queen, 5 Golem
    private static final double[] BOSS_PITCH = {1.25, 0.8, 0.6, 0.95, 1.5, 0.5};
    private static final Wave[] BOSS_WAVE = {Wave.SAW, Wave.SQUARE, Wave.SINE, Wave.SQUARE, Wave.SAW, Wave.TRI};

    /**
     * A boss's own version of a sound effect. The shape and loudness follow the normal
     * effect, so a warning still sounds like a warning. Then the boss's voice changes it:
     * its pitch, its waveform, and a signature (see voice()). Effects a boss never makes
     * come back unchanged.
     */
    static short[] bossSfx(SoundManager.Sfx which, int design) {

        int d = Math.floorMod(design, BOSS_PITCH.length);
        double p = BOSS_PITCH[d];
        Wave w = BOSS_WAVE[d];

        float[] b;
        double level;

        switch (which) {

            case WARNING:
                // The alarm when the boss arrives, over a low roar
                b = buf(1.6);
                for (int i = 0; i < 4; i++) {
                    tone(b, i * 0.4, 0.2, 620 * p, 620 * p, w, 1.0, 0.01, 0.4);
                    tone(b, i * 0.4 + 0.2, 0.2, 440 * p, 440 * p, w, 1.0, 0.01, 0.4);
                }
                tone(b, 0, 1.5, 140 * p, 70 * p, w, 0.6, 0.2, 0.8);
                smooth(b, 0.45f);
                level = 0.34;
                break;

            case BOSS_HIT:
                b = buf(0.1);
                tone(b, 0, 0.08, 150 * p, 80 * p, w, 1.0, 0.001, 2.0);
                noise(b, 0, 0.04, 0.6, 2.0, 0.6, 0.3);
                level = 0.22;
                break;

            case CHARGE:
                b = buf(0.9);
                tone(b, 0.0, 0.85, 120 * p, 900 * p, w, 0.8, 0.05, 0.3);
                tone(b, 0.0, 0.85, 240 * p, 1800 * p, Wave.SINE, 0.5, 0.05, 0.3);
                smooth(b, 0.35f);
                level = 0.30;
                break;

            case BOOST:
                b = buf(0.35);
                noise(b, 0.0, 0.32, 1.0, 1.5, 0.15, 0.9);
                tone(b, 0.0, 0.25, 200 * p, 900 * p, w, 0.5, 0.005, 1.5);
                level = 0.32;
                break;

            case LASER:
                b = buf(0.8);
                tone(b, 0.0, 0.75, 180 * p, 150 * p, w, 1.0, 0.005, 0.6);
                tone(b, 0.0, 0.75, 362 * p, 300 * p, Wave.SQUARE, 0.5, 0.005, 0.6);
                noise(b, 0.0, 0.75, 0.6, 0.8, 0.7, 0.4);
                smooth(b, 0.4f);
                level = 0.40;
                break;

            case MISSILE:
                b = buf(0.45);
                noise(b, 0.0, 0.42, 1.0, 1.2, 0.25, 0.6);
                tone(b, 0.0, 0.3, 300 * p, 700 * p, w, 0.5, 0.01, 1.2);
                level = 0.30;
                break;

            case TELEPORT:
                b = buf(0.5);
                tone(b, 0.0, 0.45, 1500 * p, 200 * p, w, 1.0, 0.005, 1.0);
                tone(b, 0.0, 0.45, 1520 * p, 210 * p, Wave.TRI, 0.6, 0.005, 1.0);
                level = 0.30;
                break;

            case EXPLODE:
                b = buf(0.55);
                noise(b, 0, 0.5, 1.0, 1.6, 0.55, 0.04);
                tone(b, 0, 0.3, 110 * p, 38 * p, Wave.SINE, 1.0, 0.002, 1.8);
                level = 0.55;
                break;

            case BIG_EXPLODE:
                b = buf(1.5);
                noise(b, 0, 1.4, 1.0, 1.4, 0.45, 0.02);
                tone(b, 0, 1.1, 90 * p, 28 * p, Wave.SINE, 1.0, 0.004, 1.5);
                noise(b, 0.25, 0.4, 0.7, 2.0, 0.3, 0.05);
                level = 0.75;
                break;

            default:
                return sfx(which);
        }

        voice(b, d, p);
        return finish(b, level);
    }

    /** Each boss's signature, added on top of its sounds. */
    private static void voice(float[] b, int design, double pitch) {

        double seconds = b.length / (double) RATE;

        switch (design) {

            case 0:
                // Vanguard: a sharp crack at the start
                noise(b, 0, Math.min(0.03, seconds), 0.8, 3.0, 1.0, 1.0);
                break;

            case 1:
                // Warship: a ringing metal clang
                tone(b, 0, Math.min(0.6, seconds), 523 * pitch, 520 * pitch, Wave.SINE, 0.35, 0.001, 3.0);
                tone(b, 0, Math.min(0.4, seconds), 1443 * pitch, 1440 * pitch, Wave.SINE, 0.18, 0.001, 3.0);
                break;

            case 2:
                // Mothership: a deep hum that wobbles
                tone(b, 0, seconds, 55, 55, Wave.SINE, 0.3, 0.05, 0.5);
                stutter(b, 6, 0.5);
                break;

            case 3:
                // Dreadnought: grinding machinery
                noise(b, 0, seconds, 0.35, 1.0, 0.5, 0.3);
                stutter(b, 14, 0.6);
                break;

            case 4:
                // Hive Queen: an insect buzz and a chitter
                stutter(b, 38, 0.55);
                noise(b, 0, Math.min(0.08, seconds), 0.3, 2.0, 1.0, 1.0);
                break;

            default:
                // Mecha-stone Golem: a low rumble of grinding stone, a crunch of rock and a heavy thud
                noise(b, 0, seconds, 0.5, 0.6, 0.05, 0.03);
                noise(b, 0, Math.min(0.06, seconds), 0.7, 2.5, 0.5, 0.2);
                tone(b, 0, Math.min(0.25, seconds), 70, 40, Wave.SINE, 0.45, 0.002, 2.0);
                break;
        }
    }

    // ===============================================================
    // SHOTS (one sound for each gun, changed by the ship and its condition)
    // ===============================================================

    /**
     * The sound of one shot (see ShotSound). The gun gives the shape. Then the ship's
     * class sets the pitch, the caliber adds a boom, and a damaged gun rattles or
     * stutters.
     */
    static short[] shot(ShotSound s) {

        // Small ships whine higher and big ships boom lower. A critical gun is slightly out of tune.
        double pitch = (1.16 - 0.05 * s.shipClass) * (s.condition == 2 ? 0.94 : 1.0);

        float[] b;
        double level;

        switch (s.gun) {

            case ARC:
                b = buf(0.16);
                tone(b, 0, 0.16, 1500 * pitch, 400 * pitch, Wave.SAW, 0.6, 0.002, 2.5);
                tone(b, 0, 0.16, 1530 * pitch, 420 * pitch, Wave.SQUARE, 0.3, 0.002, 2.5);
                noise(b, 0, 0.05, 0.5, 2.0, 1.0, 0.6);
                level = 0.20;
                break;

            case FIREBALL:
                b = buf(0.30);
                tone(b, 0, 0.30, 260 * pitch, 80 * pitch, Wave.SINE, 1.0, 0.003, 1.6);
                noise(b, 0, 0.26, 0.5, 1.8, 0.12, 0.05);
                level = 0.32;
                break;

            case COMET:
                b = buf(0.26);
                tone(b, 0, 0.26, 600 * pitch, 1400 * pitch, Wave.SINE, 0.8, 0.01, 1.2);
                tone(b, 0, 0.26, 900 * pitch, 700 * pitch, Wave.TRI, 0.35, 0.01, 1.5);
                level = 0.22;
                break;

            case OMEGA:
                b = buf(0.36);
                tone(b, 0, 0.35, 300 * pitch, 120 * pitch, Wave.SAW, 0.8, 0.003, 1.4);
                tone(b, 0, 0.20, 1000 * pitch, 350 * pitch, Wave.SQUARE, 0.5, 0.002, 2.0);
                noise(b, 0, 0.30, 0.5, 1.6, 0.3, 0.1);
                level = 0.36;
                break;

            case BEAM:
                b = buf(0.8);
                tone(b, 0.0, 0.75, 180 * pitch, 150 * pitch, Wave.SAW, 1.0, 0.005, 0.6);
                tone(b, 0.0, 0.75, 362 * pitch, 300 * pitch, Wave.SQUARE, 0.5, 0.005, 0.6);
                noise(b, 0.0, 0.75, 0.6, 0.8, 0.7, 0.4);
                smooth(b, 0.4f);
                level = 0.40;
                break;

            default:   // PLASMA: the original shot
                b = buf(0.12);
                tone(b, 0, 0.12, 1250 * pitch, 300 * pitch, Wave.SQUARE, 1.0, 0.002, 2.0);
                level = 0.17;
                break;
        }

        double seconds = b.length / (double) RATE;

        // Caliber: a deeper boom under the shot, and more punch for each CANNONS MK II step
        if (s.caliber > 0) {
            tone(b, 0, Math.min(0.18, seconds), 120 * pitch, 45 * pitch, Wave.SINE, 0.15 + 0.1 * s.caliber, 0.001, 2.0);
            level *= 1.0 + 0.04 * s.caliber;
        }

        // Condition: a damaged gun rattles, and a critical gun rattles harder and stutters
        if (s.condition >= 1) {
            noise(b, 0, seconds, 0.25, 1.0, 0.5, 0.3);
            level *= 0.95;
        }

        if (s.condition == 2) {
            noise(b, 0, seconds, 0.3, 1.0, 0.6, 0.2);
            stutter(b, 22, 0.7);
            level *= 0.9;
        }

        return finish(b, level);
    }

    /** Chops the sound on and off at "hz", like a sputtering gun. depth 1 = cut right down between chops. */
    private static void stutter(float[] b, double hz, double depth) {

        for (int i = 0; i < b.length; i++) {
            double on = 0.5 + 0.5 * Math.sin(2.0 * Math.PI * hz * i / RATE);
            b[i] *= (float) (1.0 - depth * (1.0 - on));
        }
    }

    // ===============================================================
    // MUSIC (each track loops seamlessly)
    // ===============================================================

    /**
     * The music for each track: the epic theme for the opening, story and ending, the
     * retro synthwave theme for the menus, the calm track for Easy LEVEL missions, the
     * battle track for Moderate, the boss track for Hard, and one track each for
     * Adventure and Hardcore.
     */
    static short[] music(SoundManager.Track track) {

        switch (track) {
            case OPENING:
                return synthOpening();
            case MENU:
                return synthMenu();
            case ADVENTURE:
                return synthAdventure();
            case HARDCORE:
                return synthHardcore();
            case BOSS_FIGHT:
                return bossFight();
            case LEVEL_MODERATE:
                return synthGame();
            case LEVEL_HARD:
                return synthBoss();
            default:
                return synthCalm();         // LEVEL_EASY
        }
    }

    // ===============================================================
    // SYNTHWAVE MUSIC (warm pads, a pulsing bass, a lead melody and echoes)
    // ===============================================================

    /** The chord (root, third, fifth) built on a root note. */
    private static int[] triad(int root, boolean minor) {
        return new int[]{root, root + (minor ? 3 : 4), root + 7};
    }

    /** A warm pad: two saws, slightly out of tune, that swell in and fade slowly. */
    private static void pad(float[] b, double start, double dur, int midi, double vol) {
        double f = hz(midi);
        tone(b, start, dur, f, f, Wave.SAW, vol / 2, 0.3, 0.4);
        tone(b, start, dur, f * 1.005, f * 1.005, Wave.SAW, vol / 2, 0.3, 0.4);
    }

    /**
     * Echoes that wrap round the loop, so the music still joins up when it repeats.
     * Each repeat is quieter by "gain".
     */
    private static void echo(float[] b, double delay, double gain, int repeats) {

        float[] source = b.clone();
        int shift = (int) (delay * RATE);
        double level = gain;

        for (int k = 1; k <= repeats; k++) {
            int offset = k * shift;
            for (int i = 0; i < source.length; i++) {
                b[(i + offset) % b.length] += (float) (source[i] * level);
            }
            level *= gain;
        }
    }

    /** Pushes the sound into soft clipping, so it sounds harder and buzzier. */
    private static void drive(float[] b, double amount) {
        for (int i = 0; i < b.length; i++) {
            b[i] = (float) Math.tanh(b[i] * amount);
        }
    }

    /** Calm synthwave for Easy LEVEL missions: slow pads, a sub bass and a shimmering arpeggio. */
    private static short[] synthCalm() {

        double beat = 60.0 / 90;
        double bar = beat * 4;
        int bars = 8;
        float[] b = buf(bar * bars);

        int[] roots = {57, 53, 48, 55, 57, 53, 48, 55};         // Am F C G, twice
        boolean[] minor = {true, false, false, false, true, false, false, false};
        int[] order = {0, 1, 2, 1, 2, 0, 1, 2};                 // the order of the arpeggio

        for (int i = 0; i < bars; i++) {

            double t0 = i * bar;
            int[] chord = triad(roots[i], minor[i]);

            for (int n : chord) {
                pad(b, t0, bar * 1.02, n + 12, 0.22);
            }

            tone(b, t0, bar, hz(roots[i] - 12), hz(roots[i] - 12), Wave.SINE, 0.45, 0.03, 0.3);

            for (int s = 0; s < 8; s++) {
                int note = chord[order[s]] + 24;
                tone(b, t0 + s * beat / 2, beat * 0.45, hz(note), hz(note), Wave.SAW, 0.06, 0.003, 2.0);
            }
        }

        echo(b, beat * 0.75, 0.4, 3);
        return finish(b, 0.4);
    }

    /** Driving synthwave for battles: a pulsing bass, a lead melody and a steady beat. */
    private static short[] synthGame() {

        double beat = 60.0 / 124;
        double bar = beat * 4;
        int bars = 8;
        float[] b = buf(bar * bars);

        int[] roots = {57, 53, 48, 55, 57, 53, 55, 52};         // Am F C G Am F G E
        boolean[] minor = {true, false, false, false, true, false, false, true};
        int[][] motifs = {{0, 1, 2, 1}, {2, 1, 0, 1}, {1, 2, 1, 0}, {0, 2, 1, 2}};

        for (int i = 0; i < bars; i++) {

            double t0 = i * bar;
            int r = roots[i];
            int[] chord = triad(r, minor[i]);

            // Bass: eighth notes that bounce between the root and the octave below
            for (int s = 0; s < 8; s++) {
                int note = (s % 4 == 2) ? r : r - 12;
                tone(b, t0 + s * beat / 2, beat * 0.45, hz(note), hz(note), Wave.SAW, 0.20, 0.004, 1.5);
            }

            // Lead: a four-note motif over the chord, changed every two bars
            int[] motif = motifs[(i / 2) % motifs.length];
            for (int k = 0; k < 4; k++) {
                int note = chord[motif[k]] + 12;
                tone(b, t0 + k * beat, beat * 0.8, hz(note), hz(note), Wave.SQUARE, 0.10, 0.005, 1.0);
            }

            // Beat: kick on 1 and 3, snare on 2 and 4, hats on every eighth
            kick(b, t0, 0.8);
            kick(b, t0 + 2 * beat, 0.8);
            snare(b, t0 + beat, 0.5);
            snare(b, t0 + 3 * beat, 0.5);
            for (int s = 0; s < 8; s++) {
                hat(b, t0 + s * beat / 2, 0.10);
            }
        }

        echo(b, beat * 0.5, 0.35, 3);
        return finish(b, 0.42);
    }

    /** Heroic and wide for Adventure: slow brass-like lead over warm pads, with a steady beat and space echoes. */
    private static short[] synthAdventure() {

        double beat = 60.0 / 96;
        double bar = beat * 4;
        int bars = 8;
        float[] b = buf(bar * bars);

        int[] roots = {62, 55, 57, 62, 59, 55, 57, 62};         // D G A D Bm G A D
        boolean[] minor = {false, false, false, false, true, false, false, false};
        int[][] phrases = {{0, 2, 1, 2}, {2, 1, 0, 1}, {1, 2, 2, 0}, {0, 1, 2, 2}};

        for (int i = 0; i < bars; i++) {

            double t0 = i * bar;
            int r = roots[i];
            int[] chord = triad(r, minor[i]);

            // Pads: the chord held for the whole bar
            for (int n : chord) {
                pad(b, t0, bar * 1.02, n + 12, 0.16);
            }

            // Bass: the root, held for two beats at a time
            tone(b, t0, beat * 1.9, hz(r - 12), hz(r - 12), Wave.TRI, 0.35, 0.01, 0.6);
            tone(b, t0 + 2 * beat, beat * 1.9, hz(r - 12), hz(r - 12), Wave.TRI, 0.30, 0.01, 0.6);

            // Lead: four long notes, a new phrase every two bars
            int[] phrase = phrases[(i / 2) % phrases.length];
            for (int k = 0; k < 4; k++) {
                int note = chord[phrase[k]] + 12;
                tone(b, t0 + k * beat, beat * 0.95, hz(note), hz(note), Wave.SAW, 0.10, 0.04, 0.8);
            }

            // Beat: a soft kick on 1 and 3, a snare on 2 and 4, and light hats on the eighths
            kick(b, t0, 0.6);
            kick(b, t0 + 2 * beat, 0.6);
            snare(b, t0 + beat, 0.35);
            snare(b, t0 + 3 * beat, 0.35);
            for (int s = 0; s < 8; s++) {
                hat(b, t0 + s * beat / 2, 0.05);
            }
        }

        echo(b, beat * 0.5, 0.3, 2);
        return finish(b, 0.4);
    }

    /**
     * Plays a melody written in steps: a MIDI note starts a note, 0 holds the note
     * before it, and -1 is a rest.
     */
    private static void melody(float[] b, double start, double step, int[] notes, Wave wave, double vol) {

        int i = 0;

        while (i < notes.length) {

            if (notes[i] <= 0) {
                i++;
                continue;
            }

            int length = 1;
            while (i + length < notes.length && notes[i + length] == 0) {
                length++;
            }

            double f = hz(notes[i]);
            tone(b, start + i * step, step * length * 0.95, f, f, wave, vol, 0.004, 0.6);
            i += length;
        }
    }

    /** Fast and melodic for Hardcore: a galloping bass under a soaring lead, with a drum fill as the loop ends. */
    private static short[] synthHardcore() {

        double beat = 60.0 / 160;
        double bar = beat * 4;
        int bars = 8;
        float[] b = buf(bar * bars);

        int[] roots = {52, 48, 55, 50, 52, 48, 50, 47};         // Em C G D Em C D B
        boolean[] minor = {true, false, false, false, true, false, false, false};

        // The lead, in eighth notes (one row per bar): a note starts, 0 holds it, -1 rests
        int[] lead = {
            76, 0, 71, 0, 76, 79, 78, 76,
            72, 0, 0, 76, 74, 72, 71, 0,
            71, 0, 67, 0, 71, 74, 79, 0,
            78, 0, 76, 74, 0, 0, -1, -1,
            76, 0, 71, 0, 76, 79, 81, 79,
            76, 0, 72, 0, 76, 79, 76, 72,
            74, 0, 78, 0, 81, 0, 78, 74,
            71, 0, 75, 0, 78, 0, 83, 0
        };

        for (int i = 0; i < bars; i++) {

            double t0 = i * bar;
            int r = roots[i];
            int[] chord = triad(r, minor[i]);

            // Pads: the chord, quietly, under the lead
            for (int n : chord) {
                pad(b, t0, bar * 1.02, n, 0.10);
            }

            // Bass: a gallop on every beat (an eighth and two sixteenths)
            for (int k = 0; k < 4; k++) {
                double t = t0 + k * beat;
                double f = hz(r - 12);
                tone(b, t, beat * 0.45, f, f, Wave.SAW, 0.18, 0.003, 1.2);
                tone(b, t + beat * 0.5, beat * 0.22, f, f, Wave.SAW, 0.15, 0.003, 1.2);
                tone(b, t + beat * 0.75, beat * 0.22, f, f, Wave.SAW, 0.15, 0.003, 1.2);
            }

            // Beat: kick on 1 and 3, snare on 2 and 4, hats on the eighths
            kick(b, t0, 0.85);
            kick(b, t0 + 2 * beat, 0.85);
            snare(b, t0 + beat, 0.5);
            snare(b, t0 + 3 * beat, 0.5);
            for (int s = 0; s < 8; s++) {
                hat(b, t0 + s * beat / 2, 0.08);
            }

            // A snare fill in the last beat of the loop, leading back to the start
            if (i == bars - 1) {
                for (int s = 0; s < 4; s++) {
                    snare(b, t0 + 3 * beat + s * beat / 4, 0.3 + 0.1 * s);
                }
            }
        }

        // The lead, doubled an octave lower for weight
        int[] low = new int[lead.length];
        for (int i = 0; i < lead.length; i++) {
            low[i] = (lead[i] > 0) ? lead[i] - 12 : lead[i];
        }

        melody(b, 0, beat / 2, lead, Wave.SQUARE, 0.11);
        melody(b, 0, beat / 2, low, Wave.SAW, 0.05);

        echo(b, beat * 0.75, 0.25, 2);
        drive(b, 1.2);
        return finish(b, 0.42);
    }

    /**
     * Boss fights in Adventure and Hardcore: aggressive phonk with a synth lead. A gliding,
     * wobbling synth melody over a heavily distorted 808 that slides, double kicks, a hard
     * half-time clap with ghost snares, sixteenth hi-hats, and a saturated mix.
     */
    private static short[] bossFight() {

        double beat = 60.0 / 145;
        double bar = beat * 4;
        double step = beat / 4;
        int bars = 8;
        float[] b = buf(bar * bars);
        float[] bass = buf(bar * bars);

        int[] roots = {28, 28, 31, 26};                         // E E G D
        int[] tune = {                                          // eighths over four bars: a note, 0 holds, -1 rests
            76, 0, 79, 81, 0, 79, 76, 74,
            76, 0, 0, 83, 81, 79, 0, -1,
            76, 0, 79, 81, 0, 83, 86, 0,
            83, 81, 79, 0, 76, 0, -1, -1
        };

        // The lead, gliding from each note to the next
        int previous = tune[0];
        for (int rep = 0; rep < bars / 4; rep++) {
            double base = rep * 4 * bar;
            int i = 0;
            while (i < tune.length) {
                if (tune[i] <= 0) {
                    i++;
                    continue;
                }
                int length = 1;
                while (i + length < tune.length && tune[i + length] == 0) {
                    length++;
                }
                synthLead(b, base + i * beat / 2, beat / 2 * length * 0.95, previous, tune[i], 0.12);
                previous = tune[i];
                i += length;
            }
        }

        for (int i = 0; i < bars; i++) {

            double t0 = i * bar;
            int r = roots[i % roots.length];

            for (int s = 0; s < 16; s++) {
                hat(b, t0 + s * step, (s % 2 == 0) ? 0.08 : 0.05);
            }

            bass808(bass, t0, beat * 0.7, r, r, 0.9);
            bass808(bass, t0 + 0.75 * beat, beat * 0.3, r, r, 0.8);
            bass808(bass, t0 + 1.5 * beat, beat * 0.45, r, r, 0.85);
            bass808(bass, t0 + 2.75 * beat, beat * 1.2, r, (i % 4 == 3) ? r + 12 : r + 7, 0.9);

            kick(b, t0, 1.0);
            kick(b, t0 + 0.75 * beat, 0.75);
            kick(b, t0 + 1.5 * beat, 0.9);
            kick(b, t0 + 3.5 * beat, 0.8);
            snare(b, t0 + 2 * beat, 0.95);
            noise(b, t0 + 2 * beat, 0.09, 0.85, 2.0, 1.0, 0.8);
            snare(b, t0 + 1.25 * beat, 0.3);
            snare(b, t0 + 3.75 * beat, 0.35);
        }

        drive(bass, 4.5);
        for (int i = 0; i < b.length; i++) {
            b[i] += bass[i] * 0.6f;
        }
        drive(b, 1.8);
        return finish(b, 0.42);
    }

    // ===============================================================
    // OPENING AND MENU THEMES
    // ===============================================================

    /**
     * The opening theme, also played on the story pages and the ending. Epic and
     * cinematic: big tom drums, brass stabs and a soaring lead over slow, swelling strings.
     */
    private static short[] synthOpening() {

        double beat = 60.0 / 80;
        double bar = beat * 4;
        int bars = 8;
        float[] b = buf(bar * bars);

        int[] roots = {50, 46, 53, 48};                         // Dm Bb F C
        boolean[] minor = {true, false, false, false};
        int[] tune = {                                          // quarter notes: a note, 0 holds, -1 rests
            74, 0, 77, 79,   81, 0, 0, 79,   77, 0, 74, 72,   74, 0, 0, -1,
            74, 0, 77, 79,   81, 0, 84, 0,   82, 0, 81, 77,   81, 0, 0, -1
        };

        for (int i = 0; i < bars; i++) {

            double t0 = i * bar;
            int r = roots[i % roots.length];
            int[] chord = triad(r, minor[i % roots.length]);

            // Strings: the chord swelling in, over a deep bass note
            for (int n : chord) {
                pad(b, t0, bar * 1.05, n + 12, 0.16);
            }
            tone(b, t0, bar, hz(r - 12), hz(r - 12), Wave.SINE, 0.4, 0.1, 0.4);

            // Brass: a short chord stab at the start of the bar
            for (int n : chord) {
                tone(b, t0, beat * 0.6, hz(n + 12), hz(n + 12), Wave.SAW, 0.10, 0.01, 1.2);
            }

            // Drums: big toms, and a deep boom every other bar
            tom(b, t0, 90, 0.9);
            tom(b, t0 + 1.5 * beat, 120, 0.6);
            tom(b, t0 + 2 * beat, 90, 0.8);
            tom(b, t0 + 3 * beat, 70, 0.7);
            tom(b, t0 + 3.5 * beat, 70, 0.5);
            if (i % 2 == 0) {
                kick(b, t0, 1.0);
            }
        }

        leadLine(b, 0, beat, tune, 0.12);
        echo(b, beat * 0.75, 0.3, 2);
        drive(b, 1.3);                                          // gentle saturation, so it plays as loud as the other tracks
        return finish(b, 0.42);
    }

    /** The menu theme. Retro synthwave: a driving arpeggio bass, a big gated snare, warm pads and a singing lead. */
    private static short[] synthMenu() {

        double beat = 60.0 / 100;
        double bar = beat * 4;
        double step = beat / 4;
        int bars = 8;
        float[] b = buf(bar * bars);

        int[] roots = {57, 53, 48, 55};                         // Am F C G
        boolean[] minor = {true, false, false, false};
        int[] tune = {                                          // eighth notes over four bars, played twice
            69, 0, 72, 0, 76, 0, 74, 72,   71, 0, 0, 0, 67, 0, -1, -1,
            69, 0, 72, 0, 76, 0, 79, 0,    77, 0, 76, 74, 76, 0, -1, -1
        };

        for (int i = 0; i < bars; i++) {

            double t0 = i * bar;
            int r = roots[i % roots.length];

            for (int n : triad(r, minor[i % roots.length])) {
                pad(b, t0, bar * 1.02, n + 12, 0.12);
            }

            // Bass: sixteenths, bouncing between the root and the octave
            for (int s = 0; s < 16; s++) {
                int note = (s % 2 == 0) ? r - 12 : r;
                tone(b, t0 + s * step, step * 0.9, hz(note), hz(note), Wave.SAW, 0.14, 0.002, 1.4);
            }

            // Beat: kick on 1 and 3, a big gated snare on 2 and 4, light hats
            kick(b, t0, 0.85);
            kick(b, t0 + 2 * beat, 0.85);
            for (int k : new int[]{1, 3}) {
                snare(b, t0 + k * beat, 0.6);
                noise(b, t0 + k * beat, 0.18, 0.4, 0.3, 0.6, 0.6);
            }
            for (int s = 0; s < 8; s++) {
                hat(b, t0 + s * beat / 2, 0.04);
            }

            // A tom fill at the end of the loop
            if (i == bars - 1) {
                tom(b, t0 + 2 * beat, 160, 0.6);
                tom(b, t0 + 2.5 * beat, 130, 0.6);
                tom(b, t0 + 3 * beat, 100, 0.7);
                tom(b, t0 + 3.5 * beat, 80, 0.7);
            }
        }

        leadLine(b, 0, beat / 2, tune, 0.11);
        leadLine(b, 4 * bar, beat / 2, tune, 0.11);
        echo(b, beat * 0.75, 0.3, 2);
        drive(b, 1.9);                                          // gentle saturation, so it plays as loud as the other tracks
        return finish(b, 0.42);
    }

    /** A big tom drum: a deep pitched thump with a short noise hit. */
    private static void tom(float[] b, double t, double freq, double vol) {

        tone(b, t, 0.4, freq * 1.6, freq, Wave.SINE, vol, 0.001, 1.6);
        noise(b, t, 0.05, vol * 0.4, 2.0, 0.5, 0.2);
    }

    /** A lead melody that glides from note to note (see synthLead). A MIDI note starts a note, 0 holds it, -1 rests. */
    private static void leadLine(float[] b, double start, double step, int[] tune, double vol) {

        int previous = 0;
        for (int n : tune) {
            if (n > 0) {
                previous = n;
                break;
            }
        }

        int i = 0;
        while (i < tune.length) {
            if (tune[i] <= 0) {
                i++;
                continue;
            }
            int length = 1;
            while (i + length < tune.length && tune[i + length] == 0) {
                length++;
            }
            synthLead(b, start + i * step, step * length * 0.95, previous, tune[i], vol);
            previous = tune[i];
            i += length;
        }
    }

    /** A synth lead: two slightly out-of-tune saws with vibrato, gliding in from the note before. */
    private static void synthLead(float[] b, double start, double dur, int fromMidi, int midi, double vol) {

        int first = (int) (start * RATE);
        int count = (int) (dur * RATE);
        double f0 = hz(fromMidi);
        double f1 = hz(midi);
        double p1 = 0;
        double p2 = 0;

        for (int i = 0; i < count; i++) {
            double t = (double) i / count;
            double seconds = (double) i / RATE;
            double glide = Math.min(1.0, seconds / 0.06);                       // reaches the note in 60 ms
            double vibrato = 1.0 + 0.012 * Math.sin(2 * Math.PI * 6.0 * seconds) * Math.min(1.0, seconds / 0.15);
            double f = (f0 + (f1 - f0) * glide) * vibrato;
            p1 += f / RATE;
            p2 += f * 1.006 / RATE;
            double v = (2 * (p1 - Math.floor(p1)) - 1) + (2 * (p2 - Math.floor(p2)) - 1);
            double envelope = Math.min(1.0, seconds / 0.005) * Math.pow(1 - t, 0.4);
            b[Math.floorMod(first + i, b.length)] += (float) (v * 0.5 * envelope * vol);
        }
    }

    /** An 808 bass note: a quick pitch drop for punch, then a deep tone that can slide to another note. */
    private static void bass808(float[] b, double t, double dur, int fromMidi, int toMidi, double vol) {

        double f0 = hz(fromMidi);
        double f1 = hz(toMidi);
        tone(b, t, 0.04, f0 * 3, f0, Wave.SINE, vol, 0.001, 1.0);
        tone(b, t, dur, f0, f1, Wave.SINE, vol, 0.003, 0.5);
    }

    /** Tense, fast synthwave for boss fights: a driving bass, a hard beat and a buzzing lead. */
    private static short[] synthBoss() {

        double beat = 60.0 / 150;
        double bar = beat * 4;
        int bars = 8;
        float[] b = buf(bar * bars);

        int[] roots = {50, 46, 53, 48, 50, 46, 48, 57};         // Dm Bb F C Dm Bb C A
        boolean[] minor = {true, false, false, false, true, false, false, false};
        int[] order = {0, 2, 1, 2, 0, 1, 2, 1};                 // the order of the lead

        for (int i = 0; i < bars; i++) {

            double t0 = i * bar;
            int r = roots[i];
            int[] chord = triad(r, minor[i]);

            // Bass: sixteenth notes
            for (int s = 0; s < 16; s++) {
                int note = (s % 4 == 3) ? r + 12 : r - 12;
                tone(b, t0 + s * beat / 4, beat * 0.22, hz(note), hz(note), Wave.SQUARE, 0.14, 0.002, 1.0);
            }

            // Lead: eighth notes climbing the chord
            for (int s = 0; s < 8; s++) {
                int note = chord[order[s]] + 12;
                tone(b, t0 + s * beat / 2, beat * 0.4, hz(note), hz(note), Wave.SAW, 0.07, 0.002, 1.2);
            }

            // Beat: a kick on every beat, a snare on 2 and 4, and sixteenth hats
            for (int k = 0; k < 4; k++) {
                kick(b, t0 + k * beat, 0.9);
            }
            snare(b, t0 + beat, 0.6);
            snare(b, t0 + 3 * beat, 0.6);
            for (int s = 0; s < 16; s++) {
                hat(b, t0 + s * beat / 4, 0.07);
            }
        }

        drive(b, 1.6);
        return finish(b, 0.42);
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
