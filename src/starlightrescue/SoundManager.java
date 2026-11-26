package starlightrescue;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Plays every sound effect and the background music.
 *
 * How it works: a background thread mixes all the sounds that are currently
 * playing into ONE audio line. That means any number of sounds can overlap
 * (shooting + explosions + music) and it never runs out of audio channels.
 *
 * If the computer has no sound device, the game simply stays silent.
 *
 * CUSTOM SOUNDS: put a WAV file in  resources/sounds/  and it replaces the
 * built-in sound with the same name, for example:
 *   shoot.wav  explode.wav  big_explode.wav  hit.wav  pickup.wav  upgrade.wav
 *   music_menu.wav  music_game.wav  music_boss.wav   (full list = Sfx / Track below)
 */
public final class SoundManager {

    public enum Sfx {
        SHOOT, EMPTY, RELOAD_START, RELOAD_DONE, EXPLODE, BIG_EXPLODE, HIT, SHIELD_BREAK,
        PICKUP, UPGRADE, WARNING, BOSS_HIT, CLICK, COIN, ALERT, CHARGE, LASER, MISSILE, BOOST,
        TELEPORT, REFUND, UNDO, MISSION_COMPLETE, GAME_OVER, VICTORY
    }

    public enum Track { NONE, MENU, GAME, BOSS }

    private static final int CHUNK = 512;                 // samples mixed per step
    private static final int MAX_SAME_SOUND = 3;          // limit overlapping copies of one effect
    private static final float MASTER_VOLUME = 0.9f;
    private static final float MUSIC_VOLUME = 0.55f;
    private static final String SOUND_FOLDER = "resources/sounds/";

    private static SoundManager instance;

    public static synchronized SoundManager get() {

        if (instance == null) {
            instance = new SoundManager(true);
        }

        return instance;
    }

    private static final class Voice {

        final short[] data;
        final float volume;
        final boolean loop;
        final Sfx sfx;            // null for music
        int pos = 0;

        Voice(short[] data, float volume, boolean loop, Sfx sfx) {
            this.data = data;
            this.volume = volume;
            this.loop = loop;
            this.sfx = sfx;
        }
    }

    private final List<Voice> voices = new ArrayList<>();
    private final short[][] sfxData = new short[Sfx.values().length][];
    private final short[][] musicData = new short[Track.values().length][];

    private volatile boolean available = false;
    private volatile boolean soundOn = true;
    private volatile boolean musicOn = true;
    private volatile Track wantedTrack = Track.NONE;
    private Track playingTrack = Track.NONE;

    private SourceDataLine line;

    /** @param openLine false = no real audio output (used for testing the mixer). */
    SoundManager(boolean openLine) {

        if (openLine) {
            startAudioThread();
        } else {
            loadAll();
            available = true;
        }
    }

    // ===============================================================
    // PUBLIC API
    // ===============================================================

    public void play(Sfx sfx) {

        if (!available || !soundOn) {
            return;
        }

        short[] data = sfxData[sfx.ordinal()];

        if (data == null || data.length == 0) {
            return;
        }

        synchronized (voices) {

            int same = 0;

            for (Voice v : voices) {
                if (v.sfx == sfx) {
                    same++;
                }
            }

            if (same < MAX_SAME_SOUND) {
                voices.add(new Voice(data, 1.0f, false, sfx));
            }
        }
    }

    /** Switches the background music (does nothing if that track is already playing). */
    public void setMusic(Track track) {

        if (track == wantedTrack) {
            return;
        }

        wantedTrack = track;
        applyMusic();
    }

    public void setSoundOn(boolean on) {
        soundOn = on;
    }

    public void setMusicOn(boolean on) {

        musicOn = on;
        applyMusic();
    }

    public boolean isSoundOn() {
        return soundOn;
    }

    public boolean isMusicOn() {
        return musicOn;
    }

    public boolean isAvailable() {
        return available;
    }

    /** Mutes everything, or turns everything back on. */
    public void toggleAll() {

        boolean turnOn = !(soundOn || musicOn);

        soundOn = turnOn;
        setMusicOn(turnOn);
    }

    // ===============================================================
    // MIXING
    // ===============================================================

    private void applyMusic() {

        synchronized (voices) {

            Track want = (available && musicOn) ? wantedTrack : Track.NONE;

            if (want == playingTrack) {
                return;
            }

            voices.removeIf(v -> v.sfx == null);
            playingTrack = want;

            short[] data = musicData[want.ordinal()];

            if (want != Track.NONE && data != null && data.length > 0) {
                voices.add(new Voice(data, MUSIC_VOLUME, true, null));
            }
        }
    }

    /** Mixes the next block of audio into "out". */
    void mix(short[] out) {

        float[] sum = new float[out.length];

        synchronized (voices) {

            Iterator<Voice> it = voices.iterator();

            while (it.hasNext()) {

                Voice v = it.next();
                int i = 0;

                while (i < out.length) {

                    if (v.pos >= v.data.length) {
                        if (v.loop) {
                            v.pos = 0;
                        } else {
                            break;
                        }
                    }

                    sum[i++] += v.data[v.pos++] * v.volume;
                }

                if (!v.loop && v.pos >= v.data.length) {
                    it.remove();
                }
            }
        }

        for (int i = 0; i < out.length; i++) {

            float x = sum[i] * MASTER_VOLUME;
            float magnitude = Math.abs(x);

            // Gentle limiter so lots of explosions at once don't distort
            if (magnitude > 24000f) {
                x = Math.signum(x) * (24000f + (magnitude - 24000f) * 0.25f);
            }

            out[i] = (short) Math.max(-32768, Math.min(32767, x));
        }
    }

    // ===============================================================
    // STARTUP
    // ===============================================================

    private void startAudioThread() {

        Thread thread = new Thread(() -> {

            try {

                AudioFormat format = new AudioFormat(SoundSynth.RATE, 16, 1, true, false);

                line = AudioSystem.getSourceDataLine(format);
                line.open(format, 3072);

                loadAll();

                line.start();
                available = true;
                applyMusic();

                short[] chunk = new short[CHUNK];
                byte[] bytes = new byte[CHUNK * 2];

                while (true) {

                    mix(chunk);

                    for (int i = 0; i < CHUNK; i++) {
                        bytes[i * 2] = (byte) (chunk[i] & 0xFF);
                        bytes[i * 2 + 1] = (byte) ((chunk[i] >> 8) & 0xFF);
                    }

                    line.write(bytes, 0, bytes.length);
                }

            } catch (Throwable e) {

                available = false;
                System.out.println("Sound is not available on this computer: " + e.getMessage());
            }

        }, "audio");

        thread.setDaemon(true);
        thread.start();
    }

    private void loadAll() {

        for (Sfx sfx : Sfx.values()) {
            sfxData[sfx.ordinal()] = loadSound(sfx.name().toLowerCase(),
                                               SoundSynth.sfx(sfx));
        }

        for (Track track : Track.values()) {
            if (track != Track.NONE) {
                musicData[track.ordinal()] = loadSound("music_" + track.name().toLowerCase(),
                                                       SoundSynth.music(track));
            }
        }
    }

    /** Uses resources/sounds/<name>.wav if it exists, otherwise the built-in sound. */
    private short[] loadSound(String name, short[] builtIn) {

        File file = new File(SOUND_FOLDER + name + ".wav");

        if (file.exists()) {
            try {
                short[] custom = readWav(file);

                if (custom.length > 0) {
                    return custom;
                }
            } catch (Exception e) {
                System.out.println("Could not read " + file.getPath() + ": " + e.getMessage());
            }
        }

        return builtIn;
    }

    /** Reads any normal WAV and converts it to our format (mono, 22050 Hz, 16-bit). */
    static short[] readWav(File file) throws Exception {

        try (AudioInputStream original = AudioSystem.getAudioInputStream(file)) {

            AudioFormat src = original.getFormat();
            int channels = src.getChannels();

            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                src.getSampleRate(), 16, channels, channels * 2, src.getSampleRate(), false);

            try (AudioInputStream converted = AudioSystem.getAudioInputStream(pcm, original)) {

                ByteArrayOutputStream all = new ByteArrayOutputStream();
                byte[] block = new byte[4096];
                int read;

                while ((read = converted.read(block)) > 0) {
                    all.write(block, 0, read);
                }

                byte[] bytes = all.toByteArray();
                int frames = bytes.length / (2 * channels);

                // Mix all channels down to mono
                float[] mono = new float[frames];

                for (int f = 0; f < frames; f++) {

                    float sum = 0;

                    for (int c = 0; c < channels; c++) {
                        int index = (f * channels + c) * 2;
                        sum += (short) ((bytes[index] & 0xFF) | (bytes[index + 1] << 8));
                    }

                    mono[f] = sum / channels;
                }

                // Resample to our sample rate
                double ratio = src.getSampleRate() / (double) SoundSynth.RATE;
                int outLength = (int) (frames / ratio);
                short[] out = new short[outLength];

                for (int i = 0; i < outLength; i++) {

                    double position = i * ratio;
                    int a = (int) position;
                    int b = Math.min(a + 1, frames - 1);
                    double fraction = position - a;

                    out[i] = (short) (mono[a] * (1 - fraction) + mono[b] * fraction);
                }

                return out;
            }
        }
    }
}
