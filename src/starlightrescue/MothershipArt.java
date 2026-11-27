package starlightrescue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

/**
 * The background of Mission 3: the alien mothership itself, looming over the battle.
 * A huge saucer seen from slightly below, with a dome and spires on top, rows of
 * glowing windows round its rim, and a pulsing core in its belly that casts a faint
 * beam: the beam that took her.
 *
 * The hull is painted once into a picture; each frame copies it and draws the
 * lights, the core and the beam, which move.
 */
final class MothershipArt {

    private static final int CX = 630;                      // the middle of the saucer
    private static final int CY = 215;
    private static final int RX = 350;                      // its half width
    private static final int RY = 100;                      // its half height (seen from slightly below)
    private static final double TILT = Math.toRadians(-5);

    private static final int WINDOWS = 34;                  // windows along the rim
    private static final int INNER_WINDOWS = 22;            // and along the ring inside it

    private static BufferedImage picture;
    private static final int LEFT = 210;
    private static final int TOP = 0;
    private static final int BOTTOM = 470;

    private MothershipArt() { }

    static void draw(Graphics2D g, int tick) {

        if (picture == null) {
            picture = paintHull();
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawBeam(g2, tick);
        g2.drawImage(picture, LEFT, TOP, null);

        g2.rotate(TILT, CX, CY);
        drawWindows(g2, tick);
        drawCore(g2, tick);
        drawSpireLights(g2, tick);
        g2.dispose();
    }

    // ---------------------------------------------------------------
    // WHAT MOVES
    // ---------------------------------------------------------------

    /** The faint abduction beam, fanning down from the core and slowly pulsing. */
    private static void drawBeam(Graphics2D g, int tick) {

        double coreX = CX - Math.sin(TILT) * RY * 0.45;
        double coreY = CY + RY * 0.45;
        int alpha = (int) (34 + 16 * Math.sin(tick * 0.03));

        Path2D cone = new Path2D.Double();
        cone.moveTo(coreX - 34, coreY);
        cone.lineTo(coreX + 34, coreY);
        cone.lineTo(coreX + 150, GamePanel.SCREEN_HEIGHT);
        cone.lineTo(coreX - 150, GamePanel.SCREEN_HEIGHT);
        cone.closePath();

        g.setPaint(new GradientPaint((float) coreX, (float) coreY, new Color(255, 80, 200, alpha),
                                     (float) coreX, GamePanel.SCREEN_HEIGHT, new Color(255, 80, 200, 0)));
        g.fill(cone);
    }

    /** Warm windows round the rim (a few blink), a cooler ring inside, and a light that chases round the rim. */
    private static void drawWindows(Graphics2D g, int tick) {

        for (int i = 0; i < WINDOWS; i++) {

            double a = Math.PI * (0.04 + 0.92 * i / (WINDOWS - 1));     // the near (lower) half of the rim
            int wx = (int) (CX + Math.cos(a) * RX * 0.96);
            int wy = (int) (CY + Math.sin(a) * RY * 0.96) - 3;

            if ((i * 7 + tick / 14) % 11 == 0) {
                continue;                                               // blinking off
            }

            g.setColor(new Color(255, 200, 120, 210));
            g.fillRect(wx - 2, wy - 1, 4, 3);
        }

        for (int i = 0; i < INNER_WINDOWS; i++) {

            double a = Math.PI * (0.08 + 0.84 * i / (INNER_WINDOWS - 1));
            int wx = (int) (CX + Math.cos(a) * RX * 0.66);
            int wy = (int) (CY + Math.sin(a) * RY * 0.66);
            boolean on = (i + tick / 20) % 4 != 0;

            g.setColor(on ? new Color(120, 220, 255, 170) : new Color(60, 90, 130, 120));
            g.fillRect(wx - 2, wy - 1, 3, 2);
        }

        // A bright light runs round the rim, and back
        int run = (tick / 3) % (WINDOWS * 2);
        int index = (run < WINDOWS) ? run : WINDOWS * 2 - 1 - run;
        double a = Math.PI * (0.04 + 0.92 * index / (WINDOWS - 1));
        int lx = (int) (CX + Math.cos(a) * RX * 0.985);
        int ly = (int) (CY + Math.sin(a) * RY * 0.985);
        g.setColor(new Color(255, 90, 200, 90));
        g.fillOval(lx - 7, ly - 7, 14, 14);
        g.setColor(new Color(255, 190, 240));
        g.fillOval(lx - 2, ly - 2, 5, 5);
    }

    /** The core in the belly: a pink-white glow that swells and fades. */
    private static void drawCore(Graphics2D g, int tick) {

        float cx = CX;
        float cy = (float) (CY + RY * 0.45);
        float r = (float) (24 + 5 * Math.sin(tick * 0.08));

        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), r * 2.2f,
            new float[]{0f, 0.25f, 1f},
            new Color[]{new Color(255, 230, 250, 240), new Color(255, 80, 200, 170), new Color(255, 40, 150, 0)}));
        g.fill(new Ellipse2D.Float(cx - r * 2.2f, cy - r * 1.2f, r * 4.4f, r * 2.4f));
    }

    /** Red lights blinking on the tips of the spires. */
    private static void drawSpireLights(Graphics2D g, int tick) {

        double[][] tips = spireTips();

        for (int i = 0; i < tips.length; i++) {
            if (((tick / 25) + i) % 3 == 0) {
                continue;
            }
            g.setColor(new Color(255, 60, 60, 110));
            g.fillOval((int) tips[i][0] - 5, (int) tips[i][1] - 5, 10, 10);
            g.setColor(new Color(255, 150, 150));
            g.fillOval((int) tips[i][0] - 2, (int) tips[i][1] - 2, 4, 4);
        }
    }

    private static double[][] spireTips() {
        return new double[][]{
            {CX - 70, CY - RY * 1.35 - 34},
            {CX + 10, CY - RY * 1.45 - 58},
            {CX + 95, CY - RY * 1.3 - 26}
        };
    }

    // ---------------------------------------------------------------
    // THE HULL (painted once)
    // ---------------------------------------------------------------

    private static BufferedImage paintHull() {

        int w = GamePanel.SCREEN_WIDTH - LEFT;
        int h = BOTTOM - TOP;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(-LEFT, -TOP);
        g.rotate(TILT, CX, CY);

        // A magenta glow behind the whole ship: squashed into an oval that fades to nothing at its edge
        AffineTransform beforeGlow = g.getTransform();
        float glow = RX * 1.15f;
        g.translate(CX, CY);
        g.scale(1, 0.58);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(0, 0), glow,
            new float[]{0f, 0.6f, 1f},
            new Color[]{new Color(200, 50, 170, 70), new Color(150, 30, 140, 35), new Color(120, 20, 120, 0)}));
        g.fill(new Ellipse2D.Float(-glow, -glow, glow * 2, glow * 2));
        g.setTransform(beforeGlow);

        // The spires on top of the dome
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(60, 46, 84));
        double[][] tips = spireTips();
        double[] bases = {CX - 60, CX + 10, CX + 85};
        for (int i = 0; i < tips.length; i++) {
            g.draw(new Line2D.Double(bases[i], CY - RY * 1.15, tips[i][0], tips[i][1]));
        }

        // The dome: behind the rim, lit along its top edge by the glow behind the ship
        Ellipse2D dome = new Ellipse2D.Double(CX - RX * 0.45, CY - RY * 1.5, RX * 0.9, RY * 1.4);
        g.setPaint(new GradientPaint(CX, (float) (CY - RY * 1.5), new Color(70, 52, 100),
                                     CX, (float) (CY - RY * 0.2), new Color(22, 14, 36)));
        g.fill(dome);
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(new Color(230, 130, 230, 120));
        g.draw(new Arc2D.Double(dome.getBounds2D(), 30, 120, Arc2D.OPEN));

        // The great disc, lit from above, dark underneath
        Ellipse2D disc = new Ellipse2D.Double(CX - RX, CY - RY, RX * 2, RY * 2);
        g.setPaint(new GradientPaint(CX, CY - RY, new Color(78, 60, 108), CX, CY + RY, new Color(14, 8, 26)));
        g.fill(disc);

        // Plating: rings and seams across the underside
        g.setStroke(new BasicStroke(1.2f));
        for (double s : new double[]{0.86, 0.66, 0.46}) {
            g.setColor(new Color(110, 85, 150, 90));
            g.draw(new Arc2D.Double(CX - RX * s, CY - RY * s, RX * 2 * s, RY * 2 * s, 180, 180, Arc2D.OPEN));
            g.setColor(new Color(5, 2, 12, 120));
            g.draw(new Arc2D.Double(CX - RX * s, CY - RY * s + 2, RX * 2 * s, RY * 2 * s, 180, 180, Arc2D.OPEN));
        }
        g.setColor(new Color(8, 4, 16, 110));
        for (int i = 1; i < 12; i++) {
            double a = Math.PI * i / 12;
            g.draw(new Line2D.Double(CX + Math.cos(a) * RX * 0.46, CY + Math.sin(a) * RY * 0.46,
                                     CX + Math.cos(a) * RX * 0.97, CY + Math.sin(a) * RY * 0.97));
        }

        // The rim: a thick band round the edge, with a bright edge where the light catches it
        g.setStroke(new BasicStroke(7f));
        g.setColor(new Color(52, 40, 76));
        g.draw(disc);
        g.setStroke(new BasicStroke(1.6f));
        g.setColor(new Color(220, 150, 240, 150));
        g.draw(new Arc2D.Double(CX - RX, CY - RY - 3, RX * 2, RY * 2, 15, 150, Arc2D.OPEN));

        // The housing round the core in the belly
        double coreY = CY + RY * 0.45;
        g.setColor(new Color(10, 5, 18));
        g.fill(new Ellipse2D.Double(CX - RX * 0.17, coreY - RY * 0.22, RX * 0.34, RY * 0.44));
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(130, 60, 140, 160));
        g.draw(new Ellipse2D.Double(CX - RX * 0.17, coreY - RY * 0.22, RX * 0.34, RY * 0.44));

        g.setTransform(new AffineTransform());
        g.dispose();
        return img;
    }
}
