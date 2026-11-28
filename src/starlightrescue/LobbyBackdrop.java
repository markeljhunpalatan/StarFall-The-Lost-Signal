package starlightrescue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * The lobby's background: a huge spiral galaxy beside the title, with a warm glowing
 * core and blue arms of stars, turning very slowly (the middle a little faster than
 * the edge, as real galaxies do). The stars and the soft haze of the arms are made
 * once (see prepare); each frame only turns them.
 *
 * It can also be shown forming (see draw with "form"): the stars start scattered far
 * out and spiral in to their places, the middle first, the haze gathers, and the core
 * ignites with a burst of light. The launch into the lobby plays this.
 */
final class LobbyBackdrop {

    private static final int W = GamePanel.SCREEN_WIDTH;
    private static final int H = GamePanel.SCREEN_HEIGHT;

    // Where the galaxy sits, how flat it looks (seen at a slant), and how it is turned
    private static final int CX = 612;
    private static final int CY = 96;
    private static final double TILT = 0.42;
    private static final double SLANT = -0.35;

    private static double[][] stars;            // radius, angle, size, brightness, warm (1) or blue (0),
                                                // and for forming: how far out it starts, how much it swirls in, when it starts
    private static BufferedImage haze;          // the soft glow of the arms
    private static int[][] farStars;            // the faint stars far behind it: x, y, brightness

    private LobbyBackdrop() {
    }

    /** Makes the galaxy's stars and haze (done once, while the game starts). */
    static synchronized void prepare() {

        if (stars != null) {
            return;
        }

        Random r = new Random(17);

        int count = 2200;
        double[][] made = new double[count][];
        for (int i = 0; i < count; i++) {
            int arm = i % 2;
            double radius = Math.pow(r.nextDouble(), 0.7) * 420 + 12;
            double angle = arm * Math.PI + radius / 70.0 + r.nextGaussian() * (0.28 + 40 / radius * 0.1);
            double size = r.nextDouble() < 0.08 ? 2 : 1;
            double bright = 0.35 + 0.65 * r.nextDouble() * (1.0 - radius / 520.0);
            double warm = radius < 110 ? 1.0 : (r.nextDouble() < 0.2 ? 1.0 : 0.0);
            double spread = 1.8 + r.nextDouble() * 1.6;
            double swirl = 2.0 + r.nextDouble() * 2.5;
            double delay = 0.5 * (radius / 432.0) + r.nextDouble() * 0.2;
            made[i] = new double[]{radius, angle, size, bright, warm, spread, swirl, delay};
        }

        farStars = new int[160][];
        for (int i = 0; i < farStars.length; i++) {
            farStars[i] = new int[]{r.nextInt(W), r.nextInt(H), 60 + r.nextInt(120)};
        }

        // The haze: soft glowing blobs along the two arms, painted once and turned with the stars
        // (big enough that the outer arms fade out before the picture's edge)
        int size = 1200;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int c = size / 2;
        for (int k = 0; k < 900; k++) {
            int arm = k % 2;
            double radius = 20 + (k / 900.0) * 420;
            double angle = arm * Math.PI + radius / 70.0;
            double x = c + Math.cos(angle) * radius;
            double y = c + Math.sin(angle) * radius;
            float blob = (float) (34 + radius * 0.12);
            Color tint = radius < 140 ? new Color(255, 190, 140) : (k % 3 == 0 ? new Color(190, 120, 230) : new Color(110, 160, 255));
            g.setPaint(new RadialGradientPaint(new Point2D.Float((float) x, (float) y), blob, new float[]{0f, 1f},
                new Color[]{UI.alpha(tint, 10), UI.alpha(tint, 0)}));
            g.fillOval((int) (x - blob), (int) (y - blob), (int) (blob * 2), (int) (blob * 2));
        }
        g.dispose();

        haze = img;
        stars = made;
    }

    /** Where a star is on screen when it has come in this far (p = 1: in its place in the arm). */
    private static int[] starAt(double[] s, double p, double spin, double cos, double sin) {

        double radius = s[0] * (1.0 + (s[5] - 1.0) * (1 - p)) + 220 * (1 - p);
        double ang = s[1] + spin * (1.6 - Math.min(1.0, s[0] / 380.0) * 0.8) + s[6] * (1 - p);
        double x = Math.cos(ang) * radius;
        double y = Math.sin(ang) * radius * TILT;
        return new int[]{(int) (CX + x * cos - y * sin), (int) (CY + x * sin + y * cos)};
    }

    /** The galaxy, fully formed. */
    static void draw(Graphics2D g, int t) {
        draw(g, t, 1.0);
    }

    /**
     * The galaxy, formed this far (0 = not yet, 1 = complete). While it forms, each star
     * spirals in from far out to its place (the middle ones first), the haze gathers, and
     * the core ignites with a burst of light before settling to its glow.
     */
    static void draw(Graphics2D g, int t, double form) {

        prepare();
        form = Math.max(0.0, Math.min(1.0, form));

        g.setColor(new Color(4, 5, 14));
        g.fillRect(0, 0, W, H);

        double far = Math.min(1.0, form * 3);
        for (int[] s : farStars) {
            g.setColor(new Color(220, 228, 255, (int) (s[2] * far)));
            g.fillRect(s[0], s[1], 1, 1);
        }

        double spin = t * 0.0007;

        // The glowing haze of the disc (it gathers as the galaxy forms)
        Graphics2D hg = (Graphics2D) g.create();
        if (form < 1.0) {
            hg.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, (float) Math.pow(form, 1.6)));
        }
        hg.translate(CX, CY);
        hg.rotate(SLANT);
        hg.scale(1.0, TILT);
        hg.rotate(spin);
        hg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        hg.drawImage(haze, -haze.getWidth() / 2, -haze.getHeight() / 2, null);
        hg.dispose();

        // The stars of the arms (the middle turns faster than the edge)
        double cos = Math.cos(SLANT);
        double sin = Math.sin(SLANT);
        for (double[] s : stars) {

            // How far this star has come in (each starts a little later than the ones nearer the middle)
            double p = 1.0;
            if (form < 1.0) {
                p = Math.max(0.0, Math.min(1.0, (form * 1.4 - s[7]) / 0.6));
                if (p <= 0) {
                    continue;
                }
                p = 1 - (1 - p) * (1 - p);                      // it slows as it settles into place
            }

            int[] at = starAt(s, p, spin, cos, sin);
            int sx = at[0];
            int sy = at[1];
            if (sx < 0 || sy < 0 || sx >= W || sy >= H) {
                continue;
            }
            int a = (int) (s[3] * 255 * Math.min(1.0, 0.3 + p));
            g.setColor(s[4] > 0.5 ? new Color(255, 236, 200, a) : new Color(170, 200, 255, a));

            // While it swirls in, a short trail of light behind it
            if (p < 1.0) {
                int[] was = starAt(s, Math.max(0.0, p - 0.12), spin, cos, sin);
                g.drawLine(was[0], was[1], sx, sy);
            }
            int size = (int) s[2];
            g.fillRect(sx, sy, size, size);
        }

        // The bright core, glowing gently (while forming: dark, then it ignites with a burst)
        double pulse = 0.9 + 0.1 * Math.sin(t * 0.02);
        double core = 1.0;
        double burst = 0.0;
        if (form < 1.0) {
            core = Math.max(0.0, Math.min(1.0, (form - 0.15) / 0.5));
            core = core * core;
            burst = Math.max(0.0, 1.0 - Math.abs(form - 0.62) / 0.22);   // a flare as it lights up
        }
        g.setPaint(new RadialGradientPaint(new Point2D.Float(CX, CY), 120, new float[]{0f, 0.25f, 1f},
            new Color[]{new Color(255, 246, 220, (int) (230 * pulse * core)), new Color(255, 200, 140, (int) (110 * pulse * core)),
                        new Color(255, 160, 90, 0)}));
        g.fillOval(CX - 120, CY - 120, 240, 240);

        if (burst > 0) {
            int br = (int) (120 + 160 * burst);
            g.setPaint(new RadialGradientPaint(new Point2D.Float(CX, CY), br, new float[]{0f, 0.2f, 1f},
                new Color[]{new Color(255, 255, 245, (int) (220 * burst)), new Color(255, 220, 170, (int) (120 * burst)),
                            new Color(255, 190, 120, 0)}));
            g.fillOval(CX - br, CY - br, br * 2, br * 2);
            // a thin streak of light across the core
            g.setPaint(new java.awt.LinearGradientPaint(CX - 260, 0, CX + 260, 0, new float[]{0f, 0.5f, 1f},
                new Color[]{new Color(255, 240, 210, 0), new Color(255, 250, 235, (int) (200 * burst)), new Color(255, 240, 210, 0)}));
            g.fillRect(CX - 260, CY - 1, 520, 3);
        }
    }
}
