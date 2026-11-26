package starlightrescue;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * The ship bosses, drawn with the ship pictures from the artwork pack. (The golem
 * has its own animations: see GolemArt.)
 *
 * Each boss design uses one pack ship (Vanguard = Ship3, Warship = Ship4,
 * Mothership = Ship6, Dreadnought = Ship1, Hive Queen = Ship2). The pictures face
 * right, so they are turned round to face the player. Each boss also:
 *   - keeps its exhaust flames, which flicker at the back of the hull
 *   - blinks three lights on the hull
 *   - gets its colour turned round the wheel, and more so for each repeat in Hardcore
 *   - smokes once it is below half health, and burns below a quarter
 *   - breaks apart with the pack's own explosion frames when it is destroyed
 *
 * The boss's box (width x height) is still its hitbox, so the fights play the same.
 */
final class BossArt {

    /** The pack ship used for each boss design (0 - 4). */
    private static final int[] SHIP_FOR_DESIGN = {3, 4, 6, 1, 2};

    /** How far each design's colours turn round the wheel. */
    private static final double[] DESIGN_HUE = {265, 185, 0, 0, 160};

    /** How many explosion frames each design's ship has in resources/sprites/boss. */
    private static final int[] BLAST_FRAMES = {11, 11, 11, 10, 12};

    /** The glow behind each design (its main colour). */
    private static final Color[] GLOW = {
        new Color(255, 150, 60), new Color(70, 150, 255), new Color(160, 80, 255),
        new Color(150, 150, 170), new Color(120, 230, 90)
    };

    private static final Map<Integer, BufferedImage> HULLS = new HashMap<>();

    private BossArt() { }

    /** The design's ship, cropped to what is drawn and coloured for its tier (made once). */
    private static BufferedImage hull(int design, int tier) {

        int key = design * 100 + Math.min(tier, 99);
        BufferedImage picture = HULLS.get(key);

        if (picture == null) {
            String path = "boss/ship" + SHIP_FOR_DESIGN[design] + ".png";
            double hue = DESIGN_HUE[design] + tier * 35;
            picture = Sprites.trim(Sprites.tinted(path, hue));
            HULLS.put(key, picture);
        }

        return picture;
    }

    /** The explosion frames for a design, played when that boss is destroyed. */
    static BufferedImage[] blastFrames(int design) {

        // The golem is stone, not a ship, so it gets bursts with no ship pieces in them
        if (design == Boss.GOLEM) {
            return GolemArt.blastFrames();
        }

        return Sprites.range("boss/blast" + SHIP_FOR_DESIGN[design] + "_", 0, BLAST_FRAMES[design] - 1);
    }

    /**
     * Draws a boss in its box (bx, by, w, h).
     *
     * @param phase    how angry the boss is (0 - 2): its flames and lights speed up
     * @param tier     which repeat this is in Hardcore (0 for the first)
     * @param hit      true for a few ticks after a hit (a white flash)
     * @param health   health as a fraction (0.0 - 1.0): below half it smokes, below a quarter it burns
     */
    static void draw(Graphics2D g, int design, int version, int bx, int by, int w, int h, int tick,
                     int phase, int tier, boolean hit, double health) {

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        BufferedImage hull = hull(design, tier);
        double bob = Math.sin(tick * 0.05) * 2.5;
        double cx = bx + w / 2.0;
        double cy = by + h / 2.0 + bob;

        // Lit from behind so the hull stands out from the sky
        Color glow = GLOW[design];
        g2.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 40));
        g2.fillOval(bx - 6, by + 4, w + 12, h - 8);

        if (hull == null) {
            drawFallback(g2, bx, by, w, h, design);
            g2.dispose();
            return;
        }

        // Fit the ship into the box, keeping its proportions
        double scale = Math.min(w * 1.2 / hull.getWidth(), h * 0.9 / hull.getHeight());
        double drawW = hull.getWidth() * scale;
        double drawH = hull.getHeight() * scale;

        // Exhaust flames at the back (the right, once the ship is turned round)
        drawFlames(g2, design, cx + drawW / 2 - drawW * 0.06, cy, scale, tick, phase);

        // The hull, turned to face the player
        AffineTransform old = g2.getTransform();
        g2.translate(cx, cy);
        g2.scale(-scale, scale);
        g2.drawImage(hull, (int) Math.round(-hull.getWidth() / 2.0), (int) Math.round(-hull.getHeight() / 2.0), null);
        g2.setTransform(old);

        drawLights(g2, bx, by, w, h, tick, phase, design);
        drawVersion(g2, version, bx, by, w, h, tick);

        if (health < 0.5) {
            drawSmoke(g2, bx, by, w, h, tick, health);
        }

        if (hit) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
            g2.setColor(Color.WHITE);
            g2.fillOval((int) (cx - drawW / 2), (int) (cy - drawH / 2), (int) drawW, (int) drawH);
        }

        g2.dispose();
    }

    /** The exhaust flames at the back of the boss. They change to turbo frames when the boss is angry. */
    private static void drawFlames(Graphics2D g, int design, double x, double y, double scale, int tick, int phase) {

        int ship = SHIP_FOR_DESIGN[design];
        boolean turbo = phase >= 2;
        String set = turbo ? "turbo" : "normal";
        int frame = (tick / (turbo ? 2 : 3)) % 4;

        BufferedImage flame = Sprites.get("boss/flame" + ship + "_" + set + "_" + frame + ".png");

        if (flame == null) {
            return;
        }

        double s = scale * 1.6;
        int fw = (int) Math.round(flame.getWidth() * s);
        int fh = (int) Math.round(flame.getHeight() * s);

        // (x, y) is where the exhaust leaves the hull: the flame is centred on it, and it
        // is drawn behind the hull, pointing to the right, away from the player
        g.drawImage(flame, (int) Math.round(x - fw / 2.0), (int) Math.round(y - fh / 2.0), fw, fh, null);
    }

    /** Three lights on the hull. Each blinks on its own rhythm, and faster when the boss is angry. */
    private static void drawLights(Graphics2D g, int bx, int by, int w, int h, int tick, int phase, int design) {

        double[][] spots = {{0.28, 0.32}, {0.44, 0.70}, {0.20, 0.55}};
        Color[] colours = {new Color(255, 70, 70), new Color(90, 255, 140), new Color(255, 240, 150)};
        int period = Math.max(16, 40 - phase * 8);

        for (int i = 0; i < spots.length; i++) {

            boolean on = ((tick + i * 13) % period) < period / 2;

            if (!on) {
                continue;
            }

            int lx = bx + (int) (w * spots[i][0]);
            int ly = by + (int) (h * spots[i][1]);
            Color c = colours[(i + design) % colours.length];

            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 90));
            g.fillOval(lx - 7, ly - 7, 14, 14);
            g.setColor(c);
            g.fillOval(lx - 3, ly - 3, 6, 6);
        }
    }

    /**
     * What the version adds to the look. The look tells you how the boss fights:
     * spines charge, turrets fire, and a shield dome means it takes less damage.
     */
    private static void drawVersion(Graphics2D g, int version, int bx, int by, int w, int h, int tick) {

        switch (version) {
            case 0:
                drawSpikes(g, bx, by, w, h, tick);
                break;
            case 1:
                drawTurrets(g, bx, by, w, h, tick);
                break;
            default:
                drawShield(g, bx, by, w, h, tick);
                break;
        }
    }

    /** Assault: red spines along the back, which lash up and down. */
    private static void drawSpikes(Graphics2D g, int bx, int by, int w, int h, int tick) {

        int base = by + (int) (h * 0.30);

        for (int i = 0; i < 5; i++) {

            int sx = bx + (int) (w * (0.30 + i * 0.13));
            int len = 14 + (int) (Math.sin(tick * 0.2 + i) * 3);

            g.setColor(new Color(255, 60, 40, 150));
            g.fillPolygon(new int[]{sx - 6, sx, sx + 6}, new int[]{base + 2, base - len - 3, base + 2}, 3);
            g.setColor(new Color(255, 120, 80));
            g.fillPolygon(new int[]{sx - 4, sx, sx + 4}, new int[]{base, base - len, base}, 3);
        }
    }

    /** Battery: two turret barrels out of the nose. They flash as they fire. */
    private static void drawTurrets(Graphics2D g, int bx, int by, int w, int h, int tick) {

        int nose = bx + 4;

        for (double fraction : new double[]{0.40, 0.60}) {

            int y = by + (int) (h * fraction);

            g.setColor(new Color(58, 60, 72));
            g.fillRect(nose - 8, y - 5, 30, 10);
            g.setColor(new Color(20, 22, 30));
            g.drawRect(nose - 8, y - 5, 30, 10);
            g.setColor(new Color(255, 130, 60));
            g.fillRect(nose - 2, y - 2, 4, 4);

            if (tick % 40 < 5) {
                g.setColor(new Color(255, 230, 120, 210));
                g.fillOval(nose - 9, y - 7, 14, 14);
            }
        }
    }

    /** Carrier: a pulsing shield dome round the hull and a row of lights on the drone bay. */
    private static void drawShield(Graphics2D g, int bx, int by, int w, int h, int tick) {

        int pulse = (int) (Math.sin(tick * 0.1) * 25);

        g.setColor(new Color(90, 210, 255, 42));
        g.fillOval(bx - 8, by - 8, w + 16, h + 16);

        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(150, 240, 255, 150 + pulse));
        g.drawOval(bx - 8, by - 8, w + 16, h + 16);
        g.setStroke(new BasicStroke(1f));

        for (int i = 0; i < 3; i++) {
            boolean on = (tick / 10) % 3 == i;
            g.setColor(on ? new Color(120, 255, 255) : new Color(40, 90, 110));
            g.fillOval(bx + w - 14, by + (int) (h * (0.30 + 0.20 * i)), 6, 6);
        }
    }

    /** Smoke rising from the damaged hull. The golem uses it too (as dust and embers). */
    static void drawSmoke(Graphics2D g, int bx, int by, int w, int h, int tick, double health) {

        int puffs = health < 0.25 ? 6 : 4;

        for (int i = 0; i < puffs; i++) {

            // Each puff rises from the hull, grows, and fades out
            int age = (tick * 2 + i * 17) % 70;
            int px = bx + (int) (w * (0.15 + 0.13 * i)) + (int) (Math.sin((tick + i * 9) * 0.1) * 5);
            int py = by + (int) (h * 0.3) - age;
            int size = 10 + age / 3;
            int alpha = Math.max(0, 185 - age * 3);

            g.setColor(new Color(90, 90, 100, alpha));
            g.fillOval(px - size / 2, py - size / 2, size, size);

            if (health < 0.25 && i % 2 == 0) {
                g.setColor(new Color(255, 150, 50, Math.max(0, 220 - age * 4)));
                g.fillOval(px - 4, py - 4, 8, 8);
            }
        }
    }

    /** Simple shapes, used only if the ship pictures are missing. */
    private static void drawFallback(Graphics2D g, int bx, int by, int w, int h, int design) {

        Color c = GLOW[design];
        g.setColor(c);
        g.fillOval(bx + 10, by + 20, w - 20, h - 40);
        g.setColor(Color.BLACK);
        g.fillOval(bx + w / 2 - 8, by + h / 2 - 8, 16, 16);
    }
}
