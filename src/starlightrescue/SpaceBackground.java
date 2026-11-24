package starlightrescue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.util.Random;

/**
 * Draws everything BEHIND the action: the scrolling stars plus the scenery
 * that changes with the story (Earth, deep space, the mothership's shadow
 * and the wormhole).
 */
public class SpaceBackground {

    public enum Scene { DEFAULT, EARTH, DEEP_SPACE, MOTHERSHIP, WORMHOLE }

    private static final int STAR_COUNT = 110;

    private final int width;
    private final int height;

    private final double[] starX = new double[STAR_COUNT];
    private final double[] starY = new double[STAR_COUNT];
    private final double[] starSpeed = new double[STAR_COUNT];
    private final int[] starSize = new int[STAR_COUNT];

    private final Random random = new Random();

    private Scene scene = Scene.DEFAULT;
    private int tick = 0;

    // Continents on the Earth: {x, y, width, height} in units of the planet radius
    private static final double[][] CONTINENTS = {
        {-0.55, -0.25, 0.70, 0.45},
        {-0.20,  0.35, 0.40, 0.55},
        { 0.35, -0.30, 0.55, 0.40},
        { 0.55,  0.30, 0.35, 0.30},
        {-0.95,  0.10, 0.30, 0.50}
    };

    public SpaceBackground(int width, int height) {
        this.width = width;
        this.height = height;

        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i] = random.nextInt(width);
            starY[i] = random.nextInt(height);
            starSpeed[i] = random.nextInt(4) + 1;
            starSize[i] = random.nextInt(3) + 1;
        }
    }

    public void setScene(Scene scene) {
        this.scene = scene;
    }

    public void update() {

        tick++;

        double speedMultiplier = (scene == Scene.WORMHOLE) ? 2.4 : 1.0;

        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i] -= starSpeed[i] * speedMultiplier;

            if (starX[i] < 0) {
                starX[i] = width;
                starY[i] = random.nextInt(height);
            }
        }
    }

    /**
     * @param progress 0.0 - 1.0, how far through the mission we are. The Earth
     *                 shrinks and the wormhole grows as this increases.
     */
    public void draw(Graphics2D g, double progress) {

        drawSky(g);
        drawNebula(g);
        drawStars(g);

        switch (scene) {

            case EARTH:
                drawEarth(g, (int) (90 - 50 * progress), height + 50,
                          (int) (235 - 60 * progress));
                break;

            case DEEP_SPACE:
                drawEarth(g, (int) (70 - 55 * progress),
                          (int) (470 - 120 * progress),
                          Math.max(8, (int) (120 - 92 * progress)));
                break;

            case MOTHERSHIP:
                drawHull(g);
                break;

            case WORMHOLE:
                drawWormhole(g, (int) (760 + 70 * progress), 310,
                             (int) (50 + 230 * progress));
                break;

            default:
                break;
        }
    }

    // ---------------------------------------------------------------
    // SKY, NEBULA AND STARS
    // ---------------------------------------------------------------

    private void drawSky(Graphics2D g) {

        Color top;
        Color bottom;

        switch (scene) {
            case MOTHERSHIP:
                top = new Color(18, 5, 16);
                bottom = new Color(34, 6, 12);
                break;
            case WORMHOLE:
                top = new Color(8, 5, 30);
                bottom = new Color(22, 8, 48);
                break;
            case DEEP_SPACE:
                top = new Color(5, 6, 22);
                bottom = new Color(14, 8, 36);
                break;
            default:
                top = new Color(5, 8, 25);
                bottom = new Color(8, 10, 32);
                break;
        }

        g.setPaint(new GradientPaint(0, 0, top, 0, height, bottom));
        g.fillRect(0, 0, width, height);
    }

    private void drawNebula(Graphics2D g) {

        Color a;
        Color b;

        switch (scene) {
            case DEEP_SPACE:
                a = new Color(90, 40, 160, 70);
                b = new Color(30, 90, 170, 60);
                break;
            case MOTHERSHIP:
                a = new Color(160, 30, 40, 70);
                b = new Color(110, 40, 20, 60);
                break;
            case WORMHOLE:
                a = new Color(90, 60, 220, 70);
                b = new Color(40, 170, 200, 55);
                break;
            case EARTH:
                a = new Color(30, 70, 160, 45);
                b = new Color(60, 50, 140, 30);
                break;
            default:
                a = new Color(60, 50, 140, 35);
                b = new Color(20, 70, 130, 30);
                break;
        }

        double drift = Math.sin(tick * 0.004) * 25;

        drawBlob(g, 190 + drift, 150, 280, a);
        drawBlob(g, 640 - drift, 440, 320, b);
        drawBlob(g, 430, 300 + drift, 190, a);
    }

    private void drawBlob(Graphics2D g, double cx, double cy, float radius, Color color) {

        Color clear = new Color(color.getRed(), color.getGreen(), color.getBlue(), 0);

        g.setPaint(new RadialGradientPaint(
            new Point2D.Double(cx, cy), radius,
            new float[]{0f, 1f},
            new Color[]{color, clear}
        ));

        g.fill(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));
    }

    private void drawStars(Graphics2D g) {

        for (int i = 0; i < STAR_COUNT; i++) {

            if (starSize[i] == 1) {
                g.setColor(new Color(100, 120, 160));
            } else if (starSize[i] == 2) {
                g.setColor(new Color(180, 200, 255));
            } else {
                g.setColor(Color.WHITE);
            }

            if (scene == Scene.WORMHOLE) {
                // Streaks make it feel like we are speeding up
                int len = (int) (starSpeed[i] * 6);
                g.drawLine((int) starX[i], (int) starY[i],
                           (int) starX[i] + len, (int) starY[i]);
            } else {
                g.fillOval((int) starX[i], (int) starY[i], starSize[i], starSize[i]);
            }
        }
    }

    // ---------------------------------------------------------------
    // SCENERY
    // ---------------------------------------------------------------

    /** The Earth. Also used by the story screens, so it is not private. */
    void drawEarth(Graphics2D g, int cx, int cy, int r) {

        if (r < 3) {
            return;
        }

        // Atmosphere glow
        float glow = r * 1.18f;
        g.setPaint(new RadialGradientPaint(
            new Point2D.Float(cx, cy), glow,
            new float[]{0.84f, 1f},
            new Color[]{new Color(90, 170, 255, 150), new Color(90, 170, 255, 0)}
        ));
        g.fill(new Ellipse2D.Double(cx - glow, cy - glow, glow * 2, glow * 2));

        Shape oldClip = g.getClip();
        g.setClip(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));

        // Ocean
        g.setColor(new Color(20, 75, 180));
        g.fillRect(cx - r, cy - r, r * 2, r * 2);

        // Continents drift slowly so the planet looks like it is turning
        g.setColor(new Color(45, 135, 70));
        double scroll = (tick * 0.0008) % 2.4;

        for (double[] c : CONTINENTS) {
            double ux = ((c[0] + scroll + 1.2) % 2.4) - 1.2;
            g.fillOval((int) (cx + (ux - c[2] / 2) * r),
                       (int) (cy + (c[1] - c[3] / 2) * r),
                       (int) (c[2] * r), (int) (c[3] * r));
        }

        // Light comes from the upper right, so the left side falls into shadow
        g.setPaint(new RadialGradientPaint(
            new Point2D.Float(cx + r * 0.45f, cy - r * 0.45f), r * 1.7f,
            new float[]{0f, 0.55f, 1f},
            new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 15, 70), new Color(0, 0, 10, 225)}
        ));
        g.fillRect(cx - r, cy - r, r * 2, r * 2);

        g.setClip(oldClip);
    }

    /** The mothership's huge shadow on the right edge of the screen. */
    private void drawHull(Graphics2D g) {

        int cx = width + 90;
        int cy = height / 2;
        int r = 330;

        g.setPaint(new RadialGradientPaint(
            new Point2D.Float(cx - r * 0.3f, cy), r,
            new float[]{0f, 1f},
            new Color[]{new Color(60, 24, 70), new Color(14, 4, 20)}
        ));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));

        // Ring of blinking lights
        for (int i = 0; i < 28; i++) {
            double angle = Math.PI + (i - 14) * 0.1;
            int lx = (int) (cx + Math.cos(angle) * (r - 18));
            int ly = (int) (cy + Math.sin(angle) * (r - 18));
            int on = ((tick / 18) + i) % 4;

            g.setColor(on == 0 ? new Color(255, 60, 60, 230) : new Color(120, 20, 30, 150));
            g.fillOval(lx - 3, ly - 3, 6, 6);
        }
    }

    /** The wormhole. Also used by the story screens. */
    void drawWormhole(Graphics2D g, int cx, int cy, int radius) {

        if (radius < 4) {
            return;
        }

        float glow = radius * 1.6f;
        g.setPaint(new RadialGradientPaint(
            new Point2D.Float(cx, cy), glow,
            new float[]{0f, 0.5f, 1f},
            new Color[]{new Color(210, 240, 255, 200),
                        new Color(120, 60, 255, 120),
                        new Color(60, 0, 160, 0)}
        ));
        g.fill(new Ellipse2D.Double(cx - glow, cy - glow, glow * 2, glow * 2));

        // Spinning spiral arms
        Stroke oldStroke = g.getStroke();

        for (int i = 0; i < 9; i++) {

            float f = i / 9f;
            int r = (int) (radius * (0.25 + f * 0.9));

            g.setStroke(new BasicStroke(2f + (1 - f) * 3f));
            g.setColor(new Color(130 + (int) (100 * (1 - f)),
                                 100 + (int) (120 * (1 - f)),
                                 255, 190 - i * 12));

            int start = (int) (tick * (3 + (1 - f) * 4) + i * 40) % 360;
            g.drawArc(cx - r, cy - r, r * 2, r * 2, start, 110);
            g.drawArc(cx - r, cy - r, r * 2, r * 2, start + 180, 80);
        }

        g.setStroke(oldStroke);

        // Bright core
        float core = radius * 0.35f;
        g.setPaint(new RadialGradientPaint(
            new Point2D.Float(cx, cy), core,
            new float[]{0f, 1f},
            new Color[]{new Color(255, 255, 255, 230), new Color(200, 170, 255, 0)}
        ));
        g.fill(new Ellipse2D.Double(cx - core, cy - core, core * 2, core * 2));
    }
}
