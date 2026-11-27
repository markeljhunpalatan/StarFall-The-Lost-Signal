package starlightrescue;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.Random;

/**
 * The flight through the wormhole, played when the Wormhole mission is won and
 * before the ending. About eight seconds, in four parts:
 *
 *   APPROACH  the ship, towing her escape pod, flies into the swirling wormhole
 *   TUNNEL    a tunnel of light: rings and streaks rush past, the ship shakes
 *   LIGHT     a white light at the end of the tunnel fills the screen
 *   HOME      the light fades and there is Earth, with the ship gliding in
 *
 * It can be skipped with ENTER, SPACE, ESC or a click.
 */
final class WormholeWarp {

    static final int APPROACH_END = 150;
    static final int TUNNEL_END = 380;
    static final int LIGHT_END = 420;
    static final int TOTAL = 500;

    private static final int W = GamePanel.SCREEN_WIDTH;
    private static final int H = GamePanel.SCREEN_HEIGHT;

    private static final double[][] STARS = makeStars(120, 3);
    private static final double[][] STREAKS = makeStars(70, 11);

    private WormholeWarp() { }

    /** Which part of the flight a tick is in. */
    static String part(int tick) {
        if (tick < APPROACH_END) {
            return "APPROACH";
        } else if (tick < TUNNEL_END) {
            return "TUNNEL";
        } else if (tick < LIGHT_END) {
            return "LIGHT";
        }
        return "HOME";
    }

    static void draw(Graphics2D g0, int tick, Player ship, SpaceBackground space) {

        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (tick < APPROACH_END) {
            drawApproach(g, tick, ship, space);
        } else if (tick < LIGHT_END) {
            drawTunnel(g, tick, ship);
            if (tick >= TUNNEL_END) {
                drawLight(g, (tick - TUNNEL_END) / (double) (LIGHT_END - TUNNEL_END));
            }
        } else {
            drawHome(g, tick, ship, space);
        }

        // Black bars top and bottom, like a film
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, W, 46);
        g.fillRect(0, H - 46, W, 46);

        g.setFont(UI.plain(11f));
        g.setColor(new Color(150, 160, 190, 160));
        UI.right(g, "ENTER to skip", W - 16, H - 18);

        g.dispose();
    }

    // ---------------------------------------------------------------
    // APPROACH
    // ---------------------------------------------------------------

    private static void drawApproach(Graphics2D g, int tick, Player ship, SpaceBackground space) {

        double t = tick / (double) APPROACH_END;

        g.setPaint(new GradientPaint(0, 0, new Color(8, 5, 30), 0, H, new Color(24, 8, 48)));
        g.fillRect(0, 0, W, H);

        // Stars streak faster and faster as the wormhole pulls the ship in
        double pull = 1 + t * 10;
        g.setColor(new Color(200, 210, 255, 180));
        for (double[] s : STARS) {
            double x = ((s[0] * W - tick * s[2] * pull) % W + W) % W;
            int len = (int) (1 + s[2] * pull * 3);
            g.fillRect((int) x, (int) (s[1] * H), len, s[3] > 0.8 ? 2 : 1);
        }

        // The wormhole grows until it fills the screen
        int radius = (int) (90 + Math.pow(t, 2.2) * 620);
        space.drawWormhole(g, 560, H / 2, radius);

        // The ship and the pod fly in, getting smaller as they near the mouth
        double x = -80 + ease(t) * 560;
        double scale = 1.7 - 0.9 * Math.pow(t, 1.5);
        double y = H / 2.0 + Math.sin(tick * 0.08) * 6;
        drawShipAndPod(g, ship, x, y, scale, tick, 0);

        caption(g, "\"Hold on - we're going in!\"", tick, 10, APPROACH_END - 20);
    }

    // ---------------------------------------------------------------
    // TUNNEL
    // ---------------------------------------------------------------

    private static void drawTunnel(Graphics2D g, int tick, Player ship) {

        int local = tick - APPROACH_END;
        double t = local / (double) (TUNNEL_END - APPROACH_END);

        // The tunnel's far end, wandering a little
        double vx = W - 70;
        double vy = H / 2.0 + Math.sin(local * 0.03) * 24;

        g.setPaint(new GradientPaint(0, 0, new Color(10, 4, 34), (float) vx, 0, new Color(40, 12, 80)));
        g.fillRect(0, 0, W, H);

        // Light pouring from the far end
        g.setPaint(new RadialGradientPaint(new Point2D.Double(vx, vy), 260,
            new float[]{0f, 0.3f, 1f},
            new Color[]{new Color(230, 245, 255, 230), new Color(140, 110, 255, 120), new Color(60, 20, 140, 0)}));
        g.fill(new Ellipse2D.Double(vx - 260, vy - 260, 520, 520));

        // Rings rushing past: they start small at the far end and sweep out past the ship
        int rings = 18;
        double speed = 0.010 + 0.006 * t;
        for (int i = rings - 1; i >= 0; i--) {
            double p = ((i / (double) rings) + local * speed) % 1.0;
            double near = Math.pow(p, 2.2);
            double rx = vx - near * 1050;
            double rh = 16 + near * 980;
            double rw = rh * 0.26;
            float hue = (float) (0.72 + 0.12 * Math.sin(p * 6 + local * 0.05));
            Color c = Color.getHSBColor(hue, 0.65f, 1f);
            int alpha = (int) (40 + 190 * Math.min(1.0, p * 1.6) * (1 - Math.max(0, p - 0.85) / 0.15));
            g.setStroke(new BasicStroke((float) (1.5 + near * 9)));
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha))));
            g.draw(new Ellipse2D.Double(rx - rw / 2, vy - rh / 2, rw, rh));
        }

        // Streaks of light flying past
        for (double[] s : STREAKS) {
            double p = (s[0] + local * (0.012 + s[2] * 0.02)) % 1.0;
            double near = Math.pow(p, 2.4);
            double sx = vx - near * 1100;
            double sy = vy + (s[1] - 0.5) * near * 900;
            double len = 6 + near * 120;
            g.setStroke(new BasicStroke((float) (1 + near * 2)));
            g.setColor(new Color(220, 240, 255, (int) (60 + 180 * Math.min(1.0, p * 1.5))));
            g.drawLine((int) sx, (int) sy, (int) (sx - len), (int) (sy + (s[1] - 0.5) * len * 0.8));
        }

        // Crackles of energy along the tunnel walls
        Random crackle = new Random(local / 6);
        if (crackle.nextInt(3) == 0) {
            Path2D bolt = new Path2D.Double();
            double bx = 100 + crackle.nextInt(500);
            double by = crackle.nextBoolean() ? 70 : H - 70;
            bolt.moveTo(bx, by);
            for (int k = 0; k < 6; k++) {
                bx += 18 + crackle.nextInt(24);
                by += (crackle.nextInt(30) - 15) + (by < H / 2.0 ? 8 : -8);
                bolt.lineTo(bx, by);
            }
            g.setStroke(new BasicStroke(2f));
            g.setColor(new Color(170, 230, 255, 200));
            g.draw(bolt);
        }

        // The ship holds on, shaking harder as the tunnel speeds up
        double shake = 1.5 + t * 4;
        double x = 230 + Math.sin(local * 0.9) * shake;
        double y = H / 2.0 + Math.sin(local * 0.07) * 18 + Math.cos(local * 1.3) * shake;
        drawShipAndPod(g, ship, x, y, 1.6, tick, shake);

        caption(g, "\"Almost there...\"", local, 120, 210);
    }

    /** The light at the end of the tunnel, swelling until the screen is white. */
    private static void drawLight(Graphics2D g, double t) {

        double r = 40 + Math.pow(t, 1.6) * 900;
        float cx = W - 70;
        float cy = H / 2f;
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), (float) r,
            new float[]{0f, 0.7f, 1f},
            new Color[]{Color.WHITE, new Color(255, 255, 255, 230), new Color(255, 255, 255, 0)}));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.min(1.0, t * t)));
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, W, H);
    }

    // ---------------------------------------------------------------
    // HOME
    // ---------------------------------------------------------------

    private static void drawHome(Graphics2D g, int tick, Player ship, SpaceBackground space) {

        int local = tick - LIGHT_END;
        double t = local / (double) (TOTAL - LIGHT_END);

        g.setPaint(new GradientPaint(0, 0, new Color(4, 8, 26), 0, H, new Color(10, 20, 50)));
        g.fillRect(0, 0, W, H);
        g.setColor(new Color(210, 220, 255, 170));
        for (double[] s : STARS) {
            g.fillRect((int) (s[0] * W), (int) (s[1] * H), s[3] > 0.8 ? 2 : 1, s[3] > 0.8 ? 2 : 1);
        }

        // Earth, rising into view
        space.drawEarth(g, 600, (int) (840 - ease(t) * 120), 430);

        // The ship and the pod glide in and slow down
        double x = -60 + (1 - Math.pow(1 - Math.min(1.0, t * 1.4), 3)) * 330;
        drawShipAndPod(g, ship, x, 250 + Math.sin(local * 0.05) * 4, 1.5, tick, 0);

        if (local > 30) {
            float a = (float) Math.min(1.0, (local - 30) / 30.0);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
            g.setFont(UI.bold(34f));
            g.setColor(Color.WHITE);
            UI.tracked(g, "HOME", W / 2, 150, 14f);
            g.setComposite(AlphaComposite.SrcOver);
        }

        // Fading in from the white light
        float white = (float) Math.max(0, 1 - t * 2.2);
        if (white > 0) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, white));
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, W, H);
            g.setComposite(AlphaComposite.SrcOver);
        }
    }

    // ---------------------------------------------------------------
    // THE SHIP AND HER ESCAPE POD
    // ---------------------------------------------------------------

    /** The ship at (x, y) facing right, towing the escape pod on a glowing tether behind it. */
    private static void drawShipAndPod(Graphics2D g, Player ship, double x, double y, double scale, int tick, double shake) {

        // The pod, behind and a little below, swinging on its tether
        double podX = x - 70 * scale;
        double podY = y + 10 * scale + Math.sin(tick * 0.06) * 4 * scale + Math.cos(tick * 1.1) * shake * 0.5;
        g.setStroke(new BasicStroke((float) Math.max(1, scale)));
        g.setColor(new Color(120, 220, 255, 140));
        g.drawLine((int) (x - 26 * scale), (int) y, (int) (podX + 9 * scale), (int) podY);

        g.setPaint(new RadialGradientPaint(new Point2D.Double(podX, podY), (float) (16 * scale),
            new float[]{0f, 1f}, new Color[]{new Color(120, 220, 255, 110), new Color(120, 220, 255, 0)}));
        g.fill(new Ellipse2D.Double(podX - 16 * scale, podY - 16 * scale, 32 * scale, 32 * scale));
        g.setColor(new Color(200, 205, 220));
        g.fill(new Ellipse2D.Double(podX - 10 * scale, podY - 6 * scale, 20 * scale, 12 * scale));
        g.setColor(new Color(110, 220, 255));
        g.fill(new Ellipse2D.Double(podX - 3 * scale, podY - 3.5 * scale, 8 * scale, 7 * scale));

        // The ship
        Graphics2D sg = (Graphics2D) g.create();
        sg.translate(x, y);
        sg.scale(scale, scale);
        ship.placeAt(-30, -20);
        ship.draw(sg);
        sg.dispose();
    }

    // ---------------------------------------------------------------
    // SMALL HELPERS
    // ---------------------------------------------------------------

    /** A line of the pilot's speech in the bottom bar, fading in and out between two ticks. */
    private static void caption(Graphics2D g, String text, int tick, int from, int to) {

        if (tick < from || tick > to) {
            return;
        }

        float a = (float) Math.min(1.0, Math.min(tick - from, to - tick) / 15.0);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, a)));
        g.setFont(UI.bold(15f));
        g.setColor(UI.GOLD);
        UI.center(g, text, W / 2, H - 70);
        g.setComposite(AlphaComposite.SrcOver);
    }

    private static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Random points (x, y as 0 - 1), each with a speed and a brightness. */
    private static double[][] makeStars(int count, long seed) {

        Random r = new Random(seed);
        double[][] stars = new double[count][];
        for (int i = 0; i < count; i++) {
            stars[i] = new double[]{r.nextDouble(), r.nextDouble(), 0.3 + r.nextDouble() * 1.4, r.nextDouble()};
        }
        return stars;
    }
}
