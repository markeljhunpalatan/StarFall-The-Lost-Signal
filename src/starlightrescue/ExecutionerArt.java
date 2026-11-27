package starlightrescue;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Area;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The Undead Executioner: a hooded reaper with a scythe, hanging from puppet strings
 * (resources/sprites/boss/executioner). It is pixel art, scaled up in whole pixels.
 *
 * The pictures face right, so they are turned round to face the player. Its cloak is
 * almost black, so each frame gets a pale outline and its eyes are made to glow red,
 * or it would vanish against the dark sky.
 *
 *     IDLE / IDLE2  floating (IDLE2 is restless, for its later phases)
 *     ATTACK        scythe swings          (the reaping scythe)
 *     SKILL         spikes burst out of it (the grave spikes)
 *     SUMMON        raises its scythe      (the lost souls)
 *     DEATH         melts into a shadow and is gone (also its phantom step, and rising again)
 *
 * Its lost souls are small flame wisps from their own frames.
 */
final class ExecutionerArt {

    enum Pose {
        IDLE("idle", 5, 4), IDLE2("idle2", 4, 8), ATTACK("attacking", 6, 13), SKILL("skill1", 6, 12),
        SUMMON("summon", 4, 5), DEATH("death", 10, 18);

        final String file;
        final int columns;
        final int frames;

        Pose(String file, int columns, int frames) {
            this.file = file;
            this.columns = columns;
            this.frames = frames;
        }
    }

    private static final String FOLDER = "boss/executioner/";
    private static final int CELL = 100;
    private static final int SOUL_CELL = 50;
    static final int SCALE = 3;

    // Where its body (the cloak) sits in a turned-round frame, in sheet pixels
    private static final int BODY_LEFT = 35;
    private static final int BODY_TOP = 21;

    /** Its box: the cloak, 30 x 62 sheet pixels. */
    static final int WIDTH = 30 * SCALE;
    static final int HEIGHT = 62 * SCALE;

    private static final Color OUTLINE = new Color(165, 145, 205, 200);   // the pale edge that keeps it visible
    private static final int EYE_DULL = 0x673931;                          // its eyes in the pictures...
    private static final int EYE_GLOW = 0xFF3B3B;                          // ...and as they are drawn

    private static BufferedImage[][] frames;
    private static BufferedImage[] soulIdle;
    private static BufferedImage[] soulAppear;
    private static boolean loaded = false;
    private static final Map<BufferedImage, BufferedImage> FLASHES = new IdentityHashMap<>();

    private ExecutionerArt() { }

    static boolean hasArt() {
        load();
        return frames != null;
    }

    static int frameCount(Pose pose) {
        return pose.frames;
    }

    /**
     * Draws the executioner in its box (bx, by, w, h).
     *
     * @param sway      how far its strings swing this frame (pixels at the top)
     * @param glowing   true while it rises again: its strings and aura burn red
     * @param hit       true for a few ticks after a hit (a white flash)
     * @param health    health as a fraction: below half it trails smoke, below a quarter embers too
     */
    static void draw(Graphics2D g, Pose pose, int frame, int bx, int by, int w, int h, int tick,
                     double bob, double sway, boolean glowing, boolean hit, double health) {

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int top = (int) Math.round(by + bob);
        boolean gone = (pose == Pose.DEATH && frame >= 14);

        // A dark red aura behind it, so its black cloak stands out from the sky
        if (!gone) {
            float r = h * 0.62f;
            int alpha = glowing ? 120 : 70;
            g2.setPaint(new RadialGradientPaint(new Point2D.Float(bx + w / 2f, top + h / 2f), r,
                new float[]{0f, 1f}, new Color[]{new Color(160, 20, 40, alpha), new Color(90, 10, 40, 0)}));
            g2.fill(new Ellipse2D.Float(bx + w / 2f - r, top + h / 2f - r, r * 2, r * 2));
        }

        // The puppet strings, from somewhere above, to its head and its hands
        if (pose != Pose.DEATH || frame < 6) {
            drawStrings(g2, bx, top, w, tick, sway, glowing);
        }

        load();

        if (frames == null) {
            g2.setColor(hit ? Color.WHITE : new Color(30, 26, 34));
            g2.fillRoundRect(bx, top, w, h, 30, 30);
            g2.setColor(new Color(EYE_GLOW));
            g2.fillRect(bx + w / 2 - 8, top + 20, 5, 4);
            g2.fillRect(bx + w / 2 + 3, top + 20, 5, 4);
            g2.dispose();
            return;
        }

        BufferedImage picture = frames[pose.ordinal()][Math.max(0, Math.min(pose.frames - 1, frame))];
        int left = bx - BODY_LEFT * SCALE;
        int y = top - BODY_TOP * SCALE;
        int size = CELL * SCALE;

        g2.drawImage(picture, left, y, size, size, null);

        if (hit) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
            g2.drawImage(flash(picture), left, y, size, size, null);
            g2.setComposite(AlphaComposite.SrcOver);
        }

        if (health < 0.5 && pose != Pose.DEATH) {
            BossArt.drawSmoke(g2, bx, top, w, h, tick, health);
        }

        g2.dispose();
    }

    /** Three strings that sway, like a marionette's: to its head and both hands. */
    private static void drawStrings(Graphics2D g, int bx, int top, int w, int tick, double sway, boolean glowing) {

        Stroke old = g.getStroke();
        g.setStroke(new BasicStroke(1.3f));
        int alpha = glowing ? 220 : 120;
        g.setColor(glowing ? new Color(255, 70, 70, alpha) : new Color(200, 190, 220, alpha));

        double[][] ends = {{w * 0.55, 6}, {w * 0.05, h(0.34)}, {w * 1.0, h(0.40)}};
        int roof = GamePanel.PLAY_TOP;

        for (int i = 0; i < ends.length; i++) {
            double ex = bx + ends[i][0];
            double ey = top + ends[i][1];
            double sx = ex + sway * (1.0 + 0.3 * i) + Math.sin(tick * 0.03 + i) * 6;
            g.draw(new CubicCurve2D.Double(sx, roof, sx, roof + (ey - roof) * 0.4, ex, ey - (ey - roof) * 0.3, ex, ey));
        }

        g.setStroke(old);
    }

    private static double h(double fraction) {
        return HEIGHT * fraction;
    }

    // ---------------------------------------------------------------
    // ITS ATTACKS
    // ---------------------------------------------------------------

    /** A spinning crescent blade, thrown by the reaping scythe. (cx, cy) is its middle. */
    static void drawScythe(Graphics2D g, double cx, double cy, double angle, int size) {

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(cx, cy);
        g2.rotate(angle);

        // A red glow round the blade
        g2.setPaint(new RadialGradientPaint(new Point2D.Float(0, 0), size,
            new float[]{0f, 1f}, new Color[]{new Color(255, 40, 60, 90), new Color(255, 40, 60, 0)}));
        g2.fill(new Ellipse2D.Float(-size, -size, size * 2, size * 2));

        // The crescent: a disc with a bite taken out of it
        Area blade = new Area(new Ellipse2D.Double(-size * 0.8, -size * 0.8, size * 1.6, size * 1.6));
        blade.subtract(new Area(new Ellipse2D.Double(-size * 0.55, -size * 0.95, size * 1.6, size * 1.6)));
        g2.setPaint(new GradientPaint(-size, 0, new Color(230, 226, 235), size, 0, new Color(110, 105, 120)));
        g2.fill(blade);
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(new Color(255, 80, 90, 200));
        g2.draw(blade);
        g2.dispose();
    }

    /**
     * Shadow spikes bursting along a lane, from the executioner's side (fromX) to the left
     * edge of the screen. grown goes from 0.0 (just starting) to 1.0 (all the way across).
     */
    static void drawSpikes(Graphics2D g, int fromX, int laneY, int halfHeight, double grown, int tick) {

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int reach = (int) Math.round(fromX * Math.min(1.0, grown));
        int tip = fromX - reach;

        // A red shimmer along the lane
        g2.setPaint(new GradientPaint(fromX, 0, new Color(200, 20, 50, 110), Math.max(0, tip), 0, new Color(200, 20, 50, 40)));
        g2.fillRect(tip, laneY - halfHeight, reach, halfHeight * 2);

        // Jagged black spikes, pointing left, each a slightly different height
        Path2D spikes = new Path2D.Double();
        int step = 22;
        for (int x = fromX; x > tip; x -= step) {
            int jag = (int) (halfHeight * (0.65 + 0.35 * Math.sin(x * 0.37 + tick * 0.6)));
            spikes.moveTo(x, laneY - jag);
            spikes.lineTo(x - step - 10, laneY);
            spikes.lineTo(x, laneY + jag);
            spikes.closePath();
        }
        g2.setColor(new Color(16, 10, 20, 235));
        g2.fill(spikes);
        g2.setStroke(new BasicStroke(1.2f));
        g2.setColor(new Color(255, 60, 80, 170));
        g2.draw(spikes);
        g2.dispose();
    }

    /** The marked lane before the spikes burst: a flickering red band. */
    static void drawSpikeWarning(Graphics2D g, int fromX, int laneY, int halfHeight, double progress, int tick) {

        int alpha = (int) (40 + 90 * progress * (0.6 + 0.4 * Math.sin(tick * 0.5)));
        g.setColor(new Color(255, 40, 60, Math.max(0, Math.min(255, alpha))));
        g.fillRect(0, laneY - halfHeight, fromX, halfHeight * 2);
        g.setColor(new Color(255, 90, 100, Math.min(255, alpha + 60)));
        g.drawLine(0, laneY - halfHeight, fromX, laneY - halfHeight);
        g.drawLine(0, laneY + halfHeight, fromX, laneY + halfHeight);
    }

    /**
     * A lost soul at (cx, cy): it fades in from a spark, then flickers as a small flame
     * with red eyes. age is how long it has been flying.
     */
    static void drawSoul(Graphics2D g, double cx, double cy, int age) {

        load();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        g2.setPaint(new RadialGradientPaint(new Point2D.Float((float) cx, (float) cy), 20f,
            new float[]{0f, 1f}, new Color[]{new Color(255, 40, 50, 150), new Color(255, 40, 50, 0)}));
        g2.fill(new Ellipse2D.Double(cx - 20, cy - 20, 40, 40));

        BufferedImage[] list = (age < soulAppearTicks()) ? soulAppear : soulIdle;

        if (list != null) {
            int frame = (age < soulAppearTicks()) ? age / 2 : (age / 5) % list.length;
            BufferedImage picture = list[Math.min(list.length - 1, frame)];
            int size = (int) Math.round(SOUL_CELL * 2.5);
            // The wisp sits at (19 - 28, 18 - 37) in its 50 x 50 frame
            g2.drawImage(picture, (int) Math.round(cx - 23.5 * 2.5), (int) Math.round(cy - 27.5 * 2.5), size, size, null);
        } else {
            g2.setColor(new Color(20, 14, 18));
            g2.fillOval((int) cx - 7, (int) cy - 10, 14, 20);
        }

        g2.dispose();
    }

    private static int soulAppearTicks() {
        return 12;
    }

    /** The bursts that go off while it melts away: the usual star bursts, turned blood red. */
    static BufferedImage[] blastFrames() {

        BufferedImage[] list = new BufferedImage[11];
        for (int i = 0; i < list.length; i++) {
            list[i] = Sprites.tinted("explosions/star_" + (i + 1) + ".png", 310);
        }
        return list;
    }

    // ---------------------------------------------------------------
    // LOADING
    // ---------------------------------------------------------------

    private static synchronized void load() {

        if (loaded) {
            return;
        }

        loaded = true;

        BufferedImage[][] all = new BufferedImage[Pose.values().length][];
        boolean complete = true;

        for (Pose pose : Pose.values()) {

            BufferedImage sheet = Sprites.get(FOLDER + pose.file + ".png");

            if (sheet == null) {
                complete = false;
                break;
            }

            BufferedImage[] list = new BufferedImage[pose.frames];
            for (int i = 0; i < pose.frames; i++) {
                int column = i % pose.columns;
                int row = i / pose.columns;
                list[i] = prepare(sheet.getSubimage(column * CELL, row * CELL, CELL, CELL));
            }
            all[pose.ordinal()] = list;
        }

        frames = complete ? all : null;

        BufferedImage idle = Sprites.get(FOLDER + "summonIdle.png");
        BufferedImage appear = Sprites.get(FOLDER + "summonAppear.png");

        if (idle != null && appear != null) {
            soulIdle = new BufferedImage[4];
            for (int i = 0; i < 4; i++) {
                soulIdle[i] = glowEyes(idle.getSubimage(i * SOUL_CELL, 0, SOUL_CELL, SOUL_CELL), 0xBF0D0D);
            }
            soulAppear = new BufferedImage[6];
            for (int i = 0; i < 6; i++) {
                soulAppear[i] = glowEyes(appear.getSubimage((i % 3) * SOUL_CELL, (i / 3) * SOUL_CELL, SOUL_CELL, SOUL_CELL), 0xBF0D0D);
            }
        }
    }

    /** A frame turned round to face left, with glowing eyes and a pale outline. */
    private static BufferedImage prepare(BufferedImage source) {

        int w = source.getWidth();
        int h = source.getHeight();
        BufferedImage turned = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = source.getRGB(x, y);
                if ((p >>> 24) > 0 && (p & 0xFFFFFF) == EYE_DULL) {
                    p = (p & 0xFF000000) | EYE_GLOW;
                }
                turned.setRGB(w - 1 - x, y, p);
            }
        }

        // The outline: every empty pixel that touches the figure
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int edge = OUTLINE.getRGB();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = turned.getRGB(x, y);
                if ((p >>> 24) > 30) {
                    result.setRGB(x, y, p);
                } else if (touches(turned, x, y)) {
                    result.setRGB(x, y, edge);
                }
            }
        }

        return result;
    }

    private static boolean touches(BufferedImage img, int x, int y) {

        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] d : around) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (nx >= 0 && ny >= 0 && nx < img.getWidth() && ny < img.getHeight() && (img.getRGB(nx, ny) >>> 24) > 30) {
                return true;
            }
        }

        return false;
    }

    /** A soul's frame with its eyes brightened. */
    private static BufferedImage glowEyes(BufferedImage source, int eye) {

        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int p = source.getRGB(x, y);
                if ((p >>> 24) > 0 && (p & 0xFFFFFF) == eye) {
                    p = (p & 0xFF000000) | 0xFF5050;
                }
                result.setRGB(x, y, p);
            }
        }

        return result;
    }

    /** The frame in solid white (its shape only), for the flash when it is hit. */
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
}
