package starlightrescue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The pixel-art aliens, drawn from small hand-made pictures (in the spirit of the
 * old arcade monsters), and then brought to life in code.
 *
 * THE HIERARCHY. The aliens come in three ranks, and each rank looks different:
 *   DRONES   (Scout, Zig-zag)    small, cool greens and teals, one rank pip
 *   FIGHTERS (Gunner, Diver, Looper)  medium, warm oranges, reds and limes, two pips
 *   ELITES   (Dasher, Exploder)  large, violet and gold with a glowing halo, three pips
 *
 * THE MOVEMENT. Every alien bobs and squashes, blinks now and then, and its limbs
 * move: antennae sway, wings flap, legs swing, the halo turns, and the spines and
 * fuse flicker. Each picture is made once and reused, so this costs very little.
 *
 * Want to change an alien's look? Edit its picture below (k = outline, b = body,
 * h = highlight, e = eye, p = pupil, v = visor, a = accent, c = core).
 */
final class AlienArt {

    /** How many screen pixels one picture pixel takes. */
    static final int PIXEL = 3;

    // ---------- The pictures ----------

    private static final String[] DRONE_ONE_EYE = {
        "....kkkk....",
        "...kbhhbk...",
        "..kbhhhhbk..",
        ".kbbbbbbbbk.",
        ".kbbeeeebbk.",
        ".kbbeppebbk.",
        ".kbbeeeebbk.",
        "..kbbbbbbk..",
        "...kkkkkk...",
    };

    private static final String[] DRONE_TWO_EYES = {
        "...kkkkkk...",
        "..kbbbbbbk..",
        ".kbhhhhhhbk.",
        "kbeebbbbeebk",
        "kbepbbbbepbk",
        "kbeebbbbeebk",
        ".kbbbbbbbbk.",
        "..kbbbbbbk..",
        "...kkkkkk...",
    };

    private static final String[] FIGHTER_ARMOR = {
        "...kkkkkkkk...",
        "..kbhhhhhhbk..",
        ".kbhhhhhhhhbk.",
        ".kbbbbbbbbbbk.",
        "kbbeebbbbeebbk",
        "kbbepbbbbepbbk",
        "kbbbbbbbbbbbbk",
        ".kbbbbbbbbbbk.",
        "..kbbbbbbbbk..",
        "...kkkkkkkk...",
    };

    private static final String[] FIGHTER_VISOR = {
        "...kkkkkkkk...",
        "..kbhhhhhhbk..",
        ".kbhhhhhhhhbk.",
        ".kbbbbbbbbbbk.",
        "kbvvvvvvvvvvbk",
        "kbbbbbbbbbbbbk",
        "kbbbbbbbbbbbbk",
        ".kbbbbbbbbbbk.",
        "..kbbbbbbbbk..",
        "...kkkkkkkk...",
    };

    private static final String[] FIGHTER_THREE_EYES = {
        "...kkkkkkkk...",
        "..kbhhhhhhbk..",
        ".kbhhhhhhhhbk.",
        ".kbbbbbbbbbbk.",
        "kbeebbeebbeebk",
        "kbepbbepbbepbk",
        "kbeebbeebbeebk",
        "kbbbbbbbbbbbbk",
        ".kbbbbbbbbbbk.",
        "...kkkkkkkk...",
    };

    private static final String[] ELITE_ARMOR = {
        "....kkkkkkkk....",
        "...kbhhhhhhbk...",
        "..kbhhhhhhhhbk..",
        ".kbbbbbbbbbbbbk.",
        "kbbaaabbbbaaabbk",
        "kbbaccbbbbaccbbk",
        "kbbaccbbbbaccbbk",
        "kbbbbbbbbbbbbbbk",
        ".kbbbbbbbbbbbbk.",
        "..kbbbbbbbbbbk..",
        "...kkkkkkkkkk...",
    };

    private static final String[] ELITE_BOMB = {
        "......kkkk......",
        "....kkbbbbkk....",
        "...kbbhhhhbbk...",
        "..kbhhhhhhhhbk..",
        ".kbbhhhhhhhhbbk.",
        "kbbbbbbbbbbbbbbk",
        "kbbaccccccccabbk",
        "kbbaccccccccabbk",
        "kbbbbbbbbbbbbbbk",
        ".kbbbbbbbbbbbbk.",
        "..kkkkkkkkkkkk..",
    };

    // ---------- Built once, then reused ----------

    /** One alien's pictures: normal, blinking and flashing white when hit. */
    private static final class Art {
        final BufferedImage normal;
        final BufferedImage blink;
        final BufferedImage hit;
        final int width;     // in picture pixels
        final int height;
        final List<int[]> eyes = new ArrayList<>();   // picture pixels of each eye
        final int rank;      // 1 drone, 2 fighter, 3 elite
        final Color accent;
        final Color outline;
        final Color body;

        Art(String[] grid, Palette palette, int rank) {
            this.rank = rank;
            this.accent = palette.accent;
            this.outline = palette.outline;
            this.body = palette.body;

            this.width = maxLength(grid);
            this.height = grid.length;

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < grid[y].length(); x++) {
                    if (grid[y].charAt(x) == 'e') {
                        eyes.add(new int[]{x, y});
                    }
                }
            }

            normal = paint(grid, palette, false, false);
            blink = paint(grid, palette, true, false);
            hit = paint(grid, palette, false, true);
        }

        private BufferedImage paint(String[] grid, Palette palette, boolean closedEyes, boolean white) {

            BufferedImage picture = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {

                    char c = x < grid[y].length() ? grid[y].charAt(x) : '.';

                    if (c == '.') {
                        continue;
                    }

                    // Closed eyes: the eye turns to the dark outline colour, so it reads as a shut lid
                    if (closedEyes && (c == 'e' || c == 'p')) {
                        c = 'k';
                    }

                    Color colour = palette.colour(c);

                    if (white) {
                        colour = new Color(255, 255, 255, colour.getAlpha());
                    }

                    picture.setRGB(x, y, colour.getRGB());
                }
            }

            return picture;
        }
    }

    /** The colours of one alien. */
    private static final class Palette {
        final Color outline;
        final Color body;
        final Color light;
        final Color eye = new Color(246, 252, 246);
        final Color pupil = new Color(14, 16, 26);
        final Color visor = new Color(36, 6, 18);
        final Color accent;
        final Color core;

        Palette(Color outline, Color body, Color light, Color accent, Color core) {
            this.outline = outline;
            this.body = body;
            this.light = light;
            this.accent = accent;
            this.core = core;
        }

        Color colour(char c) {
            switch (c) {
                case 'k': return outline;
                case 'b': return body;
                case 'h': return light;
                case 'a': return accent;
                case 'c': return core;
                case 'e': return eye;
                case 'p': return pupil;
                case 'v': return visor;
                default:  return new Color(0, 0, 0, 0);
            }
        }
    }

    private static final Map<Enemy.Type, Art> ART = new EnumMap<>(Enemy.Type.class);

    static {
        // Drones: greens and teals
        ART.put(Enemy.Type.SCOUT, new Art(DRONE_ONE_EYE,
            new Palette(c(14, 59, 34), c(70, 196, 106), c(190, 255, 196), c(255, 224, 102), c(255, 255, 255)), 1));
        ART.put(Enemy.Type.ZIGZAG, new Art(DRONE_TWO_EYES,
            new Palette(c(12, 59, 64), c(63, 209, 196), c(196, 255, 247), c(255, 208, 255), c(255, 255, 255)), 1));

        // Fighters: oranges, reds and limes
        ART.put(Enemy.Type.GUNNER, new Art(FIGHTER_ARMOR,
            new Palette(c(61, 23, 12), c(240, 129, 59), c(255, 209, 161), c(138, 143, 163), c(255, 255, 255)), 2));
        ART.put(Enemy.Type.DIVER, new Art(FIGHTER_VISOR,
            new Palette(c(58, 12, 31), c(255, 79, 122), c(255, 193, 211), c(255, 120, 150), c(255, 255, 255)), 2));
        ART.put(Enemy.Type.LOOPER, new Art(FIGHTER_THREE_EYES,
            new Palette(c(34, 61, 10), c(155, 224, 74), c(234, 255, 176), c(255, 244, 138), c(255, 255, 255)), 2));

        // Elites: violet and gold, with a halo
        ART.put(Enemy.Type.DASHER, new Art(ELITE_ARMOR,
            new Palette(c(34, 16, 58), c(138, 92, 255), c(217, 200, 255), c(255, 211, 77), c(255, 233, 138)), 3));
        ART.put(Enemy.Type.EXPLODER, new Art(ELITE_BOMB,
            new Palette(c(58, 10, 0), c(255, 106, 42), c(255, 208, 168), c(255, 211, 77), c(255, 242, 160)), 3));
    }

    private AlienArt() { }

    private static Color c(int r, int g, int b) {
        return new Color(r, g, b);
    }

    private static int maxLength(String[] grid) {
        int max = 0;
        for (String row : grid) {
            max = Math.max(max, row.length());
        }
        return max;
    }

    /** The picture's size on screen, in pixels. */
    static int screenWidth(Enemy.Type type) {
        return ART.get(type).width * PIXEL;
    }

    static int screenHeight(Enemy.Type type) {
        return ART.get(type).height * PIXEL;
    }

    /**
     * Draws one alien centred on (cx, cy).
     *
     * @param tick     the alien's own animation counter
     * @param hit      true for a few ticks after it is hit (it flashes white)
     * @param charging true while an elite is winding up a charge (it glows)
     */
    static void draw(Graphics2D g, Enemy.Type type, double cx, double cy, int tick,
                     boolean hit, boolean charging) {

        Art art = ART.get(type);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Bobbing and squashing: the picture breathes as it moves
        double bob = Math.sin(tick * 0.14) * 1.6;
        double squash = 1.0 + Math.sin(tick * 0.14) * 0.025;

        double pw = art.width * PIXEL * squash;
        double ph = art.height * PIXEL;
        double left = cx - pw / 2;
        double top = cy - ph / 2 + bob;

        // Glow behind the alien, stronger for higher ranks (elites glow the most)
        int glow = (art.rank == 3) ? (charging ? 150 : 80) : (art.rank == 2 ? 46 : 30);
        int glowSize = (int) (pw * 0.95) + art.rank * 6;
        g2.setColor(new Color(art.accent.getRed(), art.accent.getGreen(), art.accent.getBlue(), glow));
        g2.fillOval((int) (cx - glowSize / 2.0), (int) (cy - glowSize / 2.0 + bob), glowSize, glowSize);

        drawLimbsBehind(g2, art, type, left, top, pw, ph, tick, charging);

        // The picture itself: closed eyes for a moment every few seconds
        boolean blinking = (tick % 170) < 6;
        BufferedImage picture = hit ? art.hit : (blinking ? art.blink : art.normal);
        g2.drawImage(picture, (int) Math.round(left), (int) Math.round(top),
                     (int) Math.round(pw), (int) Math.round(ph), null);

        drawAccessories(g2, art, type, left, top, pw, ph, tick, charging);
        drawRankPips(g2, art, cx, top);

        g2.dispose();
    }

    /** Limbs and tails that sit behind the body. */
    private static void drawLimbsBehind(Graphics2D g, Art art, Enemy.Type type, double left, double top,
                                        double pw, double ph, int tick, boolean charging) {

        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(art.outline);

        if (type == Enemy.Type.GUNNER || type == Enemy.Type.DIVER) {

            // Four legs that swing as the alien walks through the air
            for (int i = 0; i < 4; i++) {
                double lx = left + pw * (0.22 + i * 0.19);
                double swing = Math.sin(tick * 0.22 + i * 1.4) * 3.5;
                g.drawLine((int) lx, (int) (top + ph - 2), (int) (lx + swing), (int) (top + ph + 6));
            }
        }

        if (type == Enemy.Type.LOOPER) {

            // Three tentacles that drift below it
            for (int i = 0; i < 3; i++) {
                double lx = left + pw * (0.3 + i * 0.2);
                double sway = Math.sin(tick * 0.16 + i * 2.1) * 4;
                g.draw(new QuadCurve2D.Double(lx, top + ph - 2, lx + sway, top + ph + 4, lx - sway, top + ph + 8));
            }
        }

        if (type == Enemy.Type.ZIGZAG) {

            // Wings that flap up and down, one on each side
            double flap = Math.sin(tick * 0.5) * 7;
            g.setColor(art.body);
            g.fillPolygon(new int[]{(int) left + 2, (int) left - 9, (int) left + 6},
                          new int[]{(int) (top + ph / 2), (int) (top + ph / 2 - flap - 4), (int) (top + ph / 2 + 4)}, 3);
            g.fillPolygon(new int[]{(int) (left + pw) - 2, (int) (left + pw) + 9, (int) (left + pw) - 6},
                          new int[]{(int) (top + ph / 2), (int) (top + ph / 2 - flap - 4), (int) (top + ph / 2 + 4)}, 3);
            g.setColor(art.outline);
            g.setStroke(new BasicStroke(1.2f));
        }

        if (type == Enemy.Type.SCOUT) {

            // Two antennae that sway
            double cx = left + pw / 2;
            double sway = Math.sin(tick * 0.15) * 4;
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new QuadCurve2D.Double(cx - 4, top + 3, cx - 9 + sway, top - 6, cx - 14 + sway, top - 8));
            g.draw(new QuadCurve2D.Double(cx + 4, top + 3, cx + 9 - sway, top - 6, cx + 14 - sway, top - 8));
            g.setColor(art.accent);
            g.fillOval((int) (cx - 16 + sway), (int) (top - 11), 4, 4);
            g.fillOval((int) (cx + 12 - sway), (int) (top - 11), 4, 4);
        }

        if (type == Enemy.Type.DASHER && charging) {

            // A trail of light behind a charging elite
            g.setColor(new Color(255, 230, 120, 110));
            for (int i = 1; i <= 3; i++) {
                g.fillRoundRect((int) (left + pw + i * 8), (int) (top + ph / 2 - 6), 14, 12, 6, 6);
            }
        }

        g.setStroke(new BasicStroke(1f));
    }

    /** Limbs, rings and sparks drawn over the body. */
    private static void drawAccessories(Graphics2D g, Art art, Enemy.Type type, double left, double top,
                                        double pw, double ph, int tick, boolean charging) {

        if (type == Enemy.Type.GUNNER) {

            // A cannon arm on the right side
            int ax = (int) (left + pw - 2);
            int ay = (int) (top + ph * 0.42);
            g.setColor(new Color(138, 143, 163));
            g.fillRect(ax, ay, 9, 5);
            g.setColor(art.outline);
            g.drawRect(ax, ay, 9, 5);
            g.setColor(new Color(255, 226, 120, 160 + (tick % 10) * 8));
            g.fillRect(ax + 9, ay + 1, 2, 3);
        }

        if (type == Enemy.Type.LOOPER) {

            // A halo of dots that turns round the alien
            double rx = pw * 0.62;
            double ry = ph * 0.46;
            double cx = left + pw / 2;
            double cy = top + ph / 2;
            g.setColor(art.accent);

            for (int i = 0; i < 8; i++) {
                double angle = tick * 0.08 + i * (Math.PI * 2 / 8);
                double dx = Math.cos(angle) * rx;
                double dy = Math.sin(angle) * ry;
                g.fillOval((int) (cx + dx - 2), (int) (cy + dy - 2), 4, 4);
            }
        }

        if (type == Enemy.Type.ZIGZAG) {

            // Glowing eye cores
            g.setColor(new Color(255, 255, 255, 60 + (tick % 40)));
            for (int[] eye : art.eyes) {
                g.fillRect((int) (left + eye[0] * PIXEL + 1), (int) (top + eye[1] * PIXEL + 1), 1, 1);
            }
        }

        if (type == Enemy.Type.DASHER) {

            // Spines that pulse out from the top and the back
            double pulse = 0.85 + Math.sin(tick * 0.2) * 0.15;
            g.setColor(art.accent);
            for (int i = 0; i < 4; i++) {
                double sx = left + pw * (0.25 + i * 0.17);
                int h = (int) (7 * pulse + (charging ? 4 : 0));
                g.fillPolygon(new int[]{(int) sx - 3, (int) sx, (int) sx + 3},
                              new int[]{(int) top + 3, (int) (top + 3 - h), (int) top + 3}, 3);
            }
        }

        if (type == Enemy.Type.EXPLODER) {

            // A fuse with a flickering spark, and two angry eyes
            double cx = left + pw / 2;
            g.setColor(art.outline);
            g.setStroke(new BasicStroke(2f));
            g.drawLine((int) cx, (int) top + 2, (int) cx + 3, (int) top - 7);
            g.setStroke(new BasicStroke(1f));
            int sparkSize = 4 + (tick % 4);
            g.setColor(new Color(255, 230, 90, 200 - (tick % 6) * 20));
            g.fillOval((int) cx + 2 - sparkSize / 2, (int) top - 9 - sparkSize / 2, sparkSize, sparkSize);

            g.setColor(new Color(246, 252, 246));
            g.fillRect((int) (left + pw * 0.34), (int) (top + ph * 0.3), 6, 6);
            g.fillRect((int) (left + pw * 0.62), (int) (top + ph * 0.3), 6, 6);
            g.setColor(new Color(14, 16, 26));
            g.fillRect((int) (left + pw * 0.34) + 2, (int) (top + ph * 0.3) + 2, 3, 3);
            g.fillRect((int) (left + pw * 0.62) + 2, (int) (top + ph * 0.3) + 2, 3, 3);
        }
    }

    /** Small pips above the alien that show its rank: one, two or three. */
    private static void drawRankPips(Graphics2D g, Art art, double cx, double top) {

        g.setColor(art.accent);

        for (int i = 0; i < art.rank; i++) {
            g.fillOval((int) (cx - (art.rank - 1) * 4 + i * 8 - 2), (int) (top - 4), 4, 4);
        }
    }
}
