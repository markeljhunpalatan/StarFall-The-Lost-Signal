package starlightrescue;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The Mecha-stone Golem, drawn from its own animation sheet
 * (resources/sprites/boss/golem). It is pixel art, so it is scaled up in whole
 * pixels without smoothing.
 *
 * The sheet is a grid of 100 x 100 frames, one animation per row, facing right.
 * The frames are turned round once so the golem faces the player. Each of its
 * attacks plays the animation made for it:
 *
 *     IDLE    standing, breathing
 *     GLOW    its veins light up       (the crystal pulse: a ring of energy)
 *     ARM     its arm stretches out and launches   (the rocket fists)
 *     CURL    it curls into a boulder  (the boulder roll: its charge)
 *     SLAM    it swings an arm down    (its normal shots)
 *     LASER   its head crystal opens   (the crystal laser)
 *     ARMOUR  its plates shift and brighten        (each time it gets angrier)
 *     DEATH   it crumbles into a pile of rocks
 *
 * The laser and the rocket fist have their own sheets too.
 */
final class GolemArt {

    enum Pose {
        IDLE(0, 4), GLOW(1, 8), ARM(2, 9), CURL(3, 8), SLAM(4, 7), LASER(5, 7), ARMOUR(6, 10), DEATH(7, 14);

        final int row;          // the first row of the sheet
        final int frames;       // DEATH runs on into the next row

        Pose(int row, int frames) {
            this.row = row;
            this.frames = frames;
        }
    }

    private static final String SHEET = "boss/golem/golem_sheet.png";
    private static final String LASER_SHEET = "boss/golem/golem_laser.png";
    private static final String ARM_SHEET = "boss/golem/golem_arm.png";

    private static final int CELL = 100;            // each frame is 100 x 100 sheet pixels
    static final int SCALE = 3;                     // each sheet pixel is drawn 3 x 3

    // Where the golem's body sits in a turned-round frame (in sheet pixels)
    private static final int BODY_LEFT = 27;
    private static final int BODY_TOP = 24;

    /** The golem's box: its body, 48 x 46 sheet pixels. */
    static final int WIDTH = 48 * SCALE;
    static final int HEIGHT = 46 * SCALE;

    // Spots on the golem, measured from the top-left corner of its box (screen pixels)
    static final int CRYSTAL_X = (int) Math.round((47.5 - BODY_LEFT) * SCALE);     // the head crystal: the laser
    static final int CRYSTAL_Y = (33 - BODY_TOP) * SCALE;
    static final int ARM_X = (5 - BODY_LEFT) * SCALE;                              // the fist at full reach
    static final int ARM_Y = (int) Math.round((36.5 - BODY_TOP) * SCALE);

    // The middle of the curled-up boulder (it spins round this while it rolls)
    private static final double BALL_X = 50.5;
    private static final double BALL_Y = 48;

    // The laser sheet: 300 x 100 frames. Row 0 is empty, rows 1-8 charge up, rows 9-14 are the beam.
    private static final double LASER_SCALE = 1.8;
    private static final double LASER_X = 52;      // where the beam starts, in a frame facing right
    private static final double LASER_Y = 33;

    private static BufferedImage[][] frames;        // [pose][frame], made the first time they are needed
    private static BufferedImage[] beams;           // the beam frames, turned to point left
    private static BufferedImage[] arms;            // the rocket fist frames, cropped
    private static boolean loaded = false;
    private static final Map<BufferedImage, BufferedImage> FLASHES = new IdentityHashMap<>();

    private GolemArt() { }

    /** False if the golem's pictures are missing (simple shapes are drawn instead). */
    static boolean hasArt() {
        load();
        return frames != null;
    }

    /** How many frames an animation has. */
    static int frameCount(Pose pose) {
        return pose.frames;
    }

    /**
     * Draws the golem in its box (bx, by, w, h).
     *
     * @param frame  which frame of the animation (kept within the animation)
     * @param roll   how far the boulder has turned (radians), while it rolls
     * @param bob    how far it floats up or down this frame
     * @param hit    true for a few ticks after a hit (a white flash)
     * @param health health as a fraction: below half it gives off dust, below a quarter it burns
     */
    static void draw(Graphics2D g, Pose pose, int frame, double roll, int bx, int by, int w, int h,
                     int tick, double bob, boolean hit, double health) {

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // A cold glow from its crystal, so it stands out from the sky
        if (pose != Pose.DEATH) {
            g2.setColor(new Color(90, 220, 255, 34));
            g2.fillOval(bx - 8, by + 6, w + 16, h - 12);
        }

        load();

        if (frames == null) {
            drawFallback(g2, bx, by, w, h, hit);
            g2.dispose();
            return;
        }

        BufferedImage picture = frames[pose.ordinal()][Math.max(0, Math.min(pose.frames - 1, frame))];
        int left = bx - BODY_LEFT * SCALE;
        int top = (int) Math.round(by - BODY_TOP * SCALE + bob);
        int size = CELL * SCALE;

        AffineTransform old = g2.getTransform();

        if (roll != 0) {
            g2.rotate(roll, left + BALL_X * SCALE, top + BALL_Y * SCALE);
        }

        g2.drawImage(picture, left, top, size, size, null);

        if (hit) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
            g2.drawImage(flash(picture), left, top, size, size, null);
            g2.setComposite(AlphaComposite.SrcOver);
        }

        g2.setTransform(old);

        if (health < 0.5 && pose != Pose.DEATH) {
            BossArt.drawSmoke(g2, bx, by, w, h, tick, health);
        }

        g2.dispose();
    }

    // ---------------------------------------------------------------
    // THE CRYSTAL LASER
    // ---------------------------------------------------------------

    /** The laser charging at the crystal (ex, ey). charge goes from 0.0 to 1.0. */
    static void drawLaserCharge(Graphics2D g, int ex, int ey, double charge, int tick) {

        BufferedImage sheet = Sprites.get(LASER_SHEET);

        if (sheet == null) {
            return;
        }

        // A spark first, then the sparkle turns and flickers until it fires
        int row = (charge < 0.12) ? 1 : 2 + (tick / 4) % 7;
        int size = (int) Math.round(CELL * LASER_SCALE);
        int x = (int) Math.round(ex - LASER_X * LASER_SCALE);
        int y = (int) Math.round(ey - LASER_Y * LASER_SCALE);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(sheet, x, y, x + size, y + size, 0, row * CELL, CELL, row * CELL + CELL, null);
        g2.dispose();
    }

    /** The beam, from the crystal (ex, ey) all the way to the left edge of the screen. */
    static void drawLaserBeam(Graphics2D g, int ex, int ey, int tick) {

        load();

        if (beams == null) {
            return;
        }

        BufferedImage beam = beams[(tick / 3) % beams.length];

        // In a beam frame turned to point left, the beam starts at x = 247. The part
        // from x = 200 on (where it starts) is drawn as it is; the rest is stretched
        // to reach the edge of the screen.
        double start = beam.getWidth() - 1 - LASER_X;
        int cut = 200;
        int top = (int) Math.round(ey - LASER_Y * LASER_SCALE);
        int bottom = (int) Math.round(top + CELL * LASER_SCALE);
        int capLeft = (int) Math.round(ex - (start - cut) * LASER_SCALE);
        int capRight = (int) Math.round(ex + (beam.getWidth() - start) * LASER_SCALE);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(beam, capLeft, top, capRight, bottom, cut, 0, beam.getWidth(), CELL, null);
        g2.drawImage(beam, 0, top, capLeft, bottom, 0, 0, cut, CELL, null);
        g2.dispose();
    }

    // ---------------------------------------------------------------
    // THE ROCKET FIST AND THE EXPLOSIONS
    // ---------------------------------------------------------------

    /** One frame of the glowing rocket fist (it points right), or null if the picture is missing. */
    static BufferedImage armFrame(int tick) {

        load();
        return (arms == null) ? null : arms[(tick / 4) % arms.length];
    }

    /** The bursts that go off while the golem breaks apart. */
    static BufferedImage[] blastFrames() {
        return Sprites.range("explosions/star_", 1, 11);
    }

    // ---------------------------------------------------------------
    // LOADING
    // ---------------------------------------------------------------

    private static synchronized void load() {

        if (loaded) {
            return;
        }

        loaded = true;

        BufferedImage sheet = Sprites.get(SHEET);

        if (sheet != null) {

            BufferedImage[][] all = new BufferedImage[Pose.values().length][];

            for (Pose pose : Pose.values()) {

                BufferedImage[] list = new BufferedImage[pose.frames];

                for (int i = 0; i < pose.frames; i++) {
                    int row = pose.row + i / 10;
                    int column = i % 10;
                    list[i] = turnedRound(sheet.getSubimage(column * CELL, row * CELL, CELL, CELL));
                }

                all[pose.ordinal()] = list;
            }

            frames = all;
        }

        BufferedImage laser = Sprites.get(LASER_SHEET);

        if (laser != null) {
            beams = new BufferedImage[6];
            for (int i = 0; i < beams.length; i++) {
                beams[i] = turnedRound(laser.getSubimage(0, (9 + i) * CELL, laser.getWidth(), CELL));
            }
        }

        BufferedImage arm = Sprites.get(ARM_SHEET);

        if (arm != null) {
            // Six frames in the first two rows of a 3 x 3 grid; the fist sits at (60, 30) - (94, 43)
            arms = new BufferedImage[6];
            for (int i = 0; i < arms.length; i++) {
                arms[i] = arm.getSubimage((i % 3) * CELL + 60, (i / 3) * CELL + 30, 35, 14);
            }
        }
    }

    /** The picture mirrored left to right. */
    private static BufferedImage turnedRound(BufferedImage source) {

        int w = source.getWidth();
        int h = source.getHeight();
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                result.setRGB(w - 1 - x, y, source.getRGB(x, y));
            }
        }

        return result;
    }

    /** The frame in solid white (its shape only), for the flash when it is hit. Made once per frame. */
    private static BufferedImage flash(BufferedImage picture) {

        BufferedImage white = FLASHES.get(picture);

        if (white == null) {
            white = new BufferedImage(picture.getWidth(), picture.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < picture.getHeight(); y++) {
                for (int x = 0; x < picture.getWidth(); x++) {
                    int alpha = picture.getRGB(x, y) >>> 24;
                    if (alpha > 0) {
                        white.setRGB(x, y, (alpha << 24) | 0xFFFFFF);
                    }
                }
            }
            FLASHES.put(picture, white);
        }

        return white;
    }

    /** Simple shapes, used only if the golem's pictures are missing. */
    private static void drawFallback(Graphics2D g, int bx, int by, int w, int h, boolean hit) {

        g.setColor(hit ? Color.WHITE : new Color(120, 115, 122));
        g.fillRoundRect(bx + 10, by + 20, w - 20, h - 30, 30, 30);
        g.fillOval(bx + w / 2 - 22, by, 44, 44);
        g.setColor(new Color(90, 230, 255));
        g.fillRect(bx + CRYSTAL_X - 3, by + CRYSTAL_Y - 12, 6, 24);
    }
}
