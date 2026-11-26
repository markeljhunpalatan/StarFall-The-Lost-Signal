package starlightrescue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;

/**
 * The black hole behind the Hard LEVEL: a black shadow, a glowing disc of gas
 * seen almost edge-on, and the far side of that disc bent by gravity into a ring
 * of light round the shadow.
 *
 * The black hole itself is painted once into a picture, so each frame only copies
 * it and then draws the bright clumps of gas racing round the disc.
 */
final class BlackHoleArt {

    private static final int HOLE_X = 560;
    private static final int HOLE_Y = 250;
    private static final int HOLE_R = 46;                         // the black shadow
    private static final int HAZE_REACH = 300;                    // how far the warm haze spreads
    private static final double DISC_A = 250;                     // the disc's half width
    private static final double DISC_B = 44;                      // its half height (seen almost edge-on)
    private static final double DISC_TILT = Math.toRadians(-7);
    private static final int CLUMPS = 12;

    private static BufferedImage picture;                         // made the first time it is drawn
    private static int pictureLeft;
    private static int pictureTop;

    private BlackHoleArt() { }

    static void draw(Graphics2D g, int tick) {

        if (picture == null) {
            makePicture();
        }

        g.drawImage(picture, pictureLeft, pictureTop, null);

        // Bright clumps of gas racing round the disc (the inner ones go faster)
        AffineTransform oldTransform = g.getTransform();
        Stroke oldStroke = g.getStroke();
        g.rotate(DISC_TILT, HOLE_X, HOLE_Y);
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        for (int k = 0; k < CLUMPS; k++) {

            double size = 0.42 + 0.05 * k;
            double degrees = (k * 97 + tick * 1.6 / Math.pow(size, 1.5)) % 360;
            double middle = Math.toRadians(degrees + 12);

            // Arc2D angles go anticlockwise from 3 o'clock, so 0 - 180 is the far (upper) half
            double mx = Math.cos(middle) * DISC_A * size;
            double my = -Math.sin(middle) * DISC_B * size;
            boolean far = Math.sin(middle) > 0;
            if (far && mx * mx + my * my < (HOLE_R + 4) * (HOLE_R + 4)) {
                continue;                                         // hidden behind the black hole
            }

            g.setColor(new Color(255, 225, 170, far ? 120 : 190));
            g.draw(new Arc2D.Double(HOLE_X - DISC_A * size, HOLE_Y - DISC_B * size,
                                    DISC_A * size * 2, DISC_B * size * 2, degrees, 24, Arc2D.OPEN));
        }

        g.setTransform(oldTransform);
        g.setStroke(oldStroke);
    }

    /** Paints the haze, the disc, the bent ring of light and the shadow, back to front. */
    private static void makePicture() {

        pictureLeft = Math.max(0, HOLE_X - HAZE_REACH);
        pictureTop = Math.max(0, HOLE_Y - HAZE_REACH);
        int w = Math.min(GamePanel.SCREEN_WIDTH, HOLE_X + HAZE_REACH) - pictureLeft;
        int h = Math.min(GamePanel.SCREEN_HEIGHT, HOLE_Y + HAZE_REACH) - pictureTop;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        double cos = Math.cos(-DISC_TILT);
        double sin = Math.sin(-DISC_TILT);

        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {

                double x = pictureLeft + px + 0.5 - HOLE_X;
                double y = pictureTop + py + 0.5 - HOLE_Y;
                double dist = Math.sqrt(x * x + y * y);

                // The layers are mixed as a premultiplied colour {r, g, b, a}
                double[] out = {0, 0, 0, 0};

                // The faint warm haze round everything
                double haze = Math.pow(Math.max(0, 1 - dist / HAZE_REACH), 2.5) * 0.35;
                over(out, 255, 140, 60, haze);

                // The disc, in its own tilted frame
                double rx = x * cos - y * sin;
                double ry = x * sin + y * cos;
                double e = Math.sqrt((rx / DISC_A) * (rx / DISC_A) + (ry / DISC_B) * (ry / DISC_B));
                double angle = Math.atan2(ry / DISC_B, rx / DISC_A);
                double disc = discLight(e, angle);
                int[] colour = discColour(e);

                // The far half of the disc is behind the black hole
                if (ry < 0) {
                    over(out, colour[0], colour[1], colour[2], disc);
                }

                // The far side of the disc, bent by gravity into a ring round the shadow
                double ring = 0;
                if (dist > HOLE_R) {
                    double t = (dist - HOLE_R) / HOLE_R;
                    ring = Math.exp(-t * t / 0.02) * 0.95 + Math.exp(-t * t / 0.35) * 0.45;
                    ring *= 0.75 + 0.25 * Math.abs(y) / dist;
                    ring *= 1.0 + 0.35 * (-x / dist);                 // brighter on the side coming towards us
                }
                over(out, 255, 200, 140, ring);

                // The black shadow
                over(out, 0, 0, 0, HOLE_R - dist + 0.5);

                // The near half of the disc passes in front
                if (ry >= 0) {
                    over(out, colour[0], colour[1], colour[2], disc);
                }

                if (out[3] <= 0.002) {
                    continue;
                }

                img.setRGB(px, py, argb(out[3] * 255, out[0] / out[3], out[1] / out[3], out[2] / out[3]));
            }
        }

        picture = img;
    }

    /** How bright the disc is at "e" (0 middle, 1 outer edge) and an angle round it. */
    private static double discLight(double e, double angle) {

        if (e < 0.3 || e > 1.05) {
            return 0;
        }

        double inner = clamp01((e - 0.3) / 0.05);
        double outer = clamp01((1.05 - e) / 0.35);
        double streaks = PlanetArt.fbm(angle / (2 * Math.PI) + 0.5, e * 0.6, 12, 3, 31);
        double beaming = 1.0 - 0.35 * Math.cos(angle);                 // the left side comes towards us: brighter
        return clamp01(inner * outer * (0.45 + 0.75 * streaks) * beaming * 0.85);
    }

    /** White-gold near the middle, then orange, then deep red at the edge. */
    private static int[] discColour(double e) {

        double t = clamp01((e - 0.3) / 0.75);
        return new int[]{255, (int) (235 - 160 * t), (int) (190 - 165 * t)};
    }

    // ---------- Small helpers ----------

    /** Lays a colour with opacity "a" over a premultiplied colour {r, g, b, a}. */
    private static void over(double[] out, int r, int g, int b, double a) {
        a = clamp01(a);
        out[0] = r * a + out[0] * (1 - a);
        out[1] = g * a + out[1] * (1 - a);
        out[2] = b * a + out[2] * (1 - a);
        out[3] = a + out[3] * (1 - a);
    }

    private static int argb(double a, double r, double g, double b) {
        return (channel(a) << 24) | (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    private static int channel(double v) {
        return (int) Math.max(0, Math.min(255, v));
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
