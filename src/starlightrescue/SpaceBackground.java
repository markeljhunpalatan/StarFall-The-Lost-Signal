package starlightrescue;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Draws everything BEHIND the action: the scrolling stars plus the scenery
 * that changes with the story (Earth, deep space, the mothership itself
 * and the wormhole), and the black hole behind the Hard LEVEL.
 */
public class SpaceBackground {

    public enum Scene { DEFAULT, EARTH, DEEP_SPACE, MOTHERSHIP, WORMHOLE, BLACK_HOLE }

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

    // Pictures that are expensive to paint are kept, so each frame only copies them.
    // The sky is kept per scene. The nebula is painted small and scaled up. The Earth's
    // glow and shadow are kept per size (the continents still turn, so they stay live).
    private BufferedImage skyImage;
    private Scene skyScene;
    private BufferedImage nebulaImage;
    private final Map<Integer, BufferedImage[]> earthCache = new HashMap<>();

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

        drawSkyCached(g);
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
                MothershipArt.draw(g, tick);
                break;

            case WORMHOLE:
                drawWormhole(g, (int) (760 + 70 * progress), 310,
                             (int) (50 + 230 * progress));
                break;

            case BLACK_HOLE:
                BlackHoleArt.draw(g, tick);
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
                top = new Color(12, 4, 26);
                bottom = new Color(30, 6, 32);
                break;
            case WORMHOLE:
                top = new Color(8, 5, 30);
                bottom = new Color(22, 8, 48);
                break;
            case DEEP_SPACE:
                top = new Color(5, 6, 22);
                bottom = new Color(14, 8, 36);
                break;
            case BLACK_HOLE:
                top = new Color(3, 2, 10);
                bottom = new Color(10, 4, 16);
                break;
            default:
                top = new Color(5, 8, 25);
                bottom = new Color(8, 10, 32);
                break;
        }

        g.setPaint(new GradientPaint(0, 0, top, 0, height, bottom));
        g.fillRect(0, 0, width, height);
    }

    /** Copies the sky picture, painting it once for each scene. */
    private void drawSkyCached(Graphics2D g) {

        if (skyImage == null || skyScene != scene) {

            skyImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D sky = skyImage.createGraphics();
            drawSky(sky);
            sky.dispose();

            skyScene = scene;
        }

        g.drawImage(skyImage, 0, 0, null);
    }

    private void drawNebula(Graphics2D screen) {

        // The nebula is very soft, so it is painted at a quarter of the size and scaled up.
        // That is far cheaper than painting the big gradients at full size every frame.
        int smallW = width / 4;
        int smallH = height / 4;

        if (nebulaImage == null) {
            nebulaImage = new BufferedImage(smallW, smallH, BufferedImage.TYPE_INT_ARGB);
        }

        Graphics2D g = nebulaImage.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, smallW, smallH);
        g.setComposite(AlphaComposite.SrcOver);
        g.scale(0.25, 0.25);

        Color a;
        Color b;

        switch (scene) {
            case DEEP_SPACE:
                a = new Color(90, 40, 160, 70);
                b = new Color(30, 90, 170, 60);
                break;
            case MOTHERSHIP:
                a = new Color(170, 40, 140, 60);
                b = new Color(80, 30, 160, 55);
                break;
            case WORMHOLE:
                a = new Color(90, 60, 220, 70);
                b = new Color(40, 170, 200, 55);
                break;
            case EARTH:
                a = new Color(30, 70, 160, 45);
                b = new Color(60, 50, 140, 30);
                break;
            case BLACK_HOLE:
                a = new Color(110, 40, 150, 50);
                b = new Color(190, 100, 40, 35);
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

        g.dispose();
        screen.drawImage(nebulaImage, 0, 0, width, height, null);
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

    // The star colours are made once (making a colour for every star every frame is wasteful)
    private static final Color STAR_DIM = new Color(100, 120, 160);
    private static final Color STAR_MID = new Color(180, 200, 255);

    private void drawStars(Graphics2D g) {

        for (int i = 0; i < STAR_COUNT; i++) {

            if (starSize[i] == 1) {
                g.setColor(STAR_DIM);
            } else if (starSize[i] == 2) {
                g.setColor(STAR_MID);
            } else {
                g.setColor(Color.WHITE);
            }

            if (scene == Scene.WORMHOLE) {
                // Streaks make it feel like we are speeding up
                int len = (int) (starSpeed[i] * 6);
                g.drawLine((int) starX[i], (int) starY[i],
                           (int) starX[i] + len, (int) starY[i]);
            } else {
                // Plain squares: at this size they look the same, and they are much cheaper than circles
                g.fillRect((int) starX[i], (int) starY[i], starSize[i], starSize[i]);
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

        // The glow around the planet, and the lit planet itself, are pictures kept for this size
        BufferedImage[] earth = earthImages(r);
        int half = earth[0].getWidth() / 2;
        g.drawImage(earth[0], cx - half, cy - half, null);
        g.drawImage(earth[1], cx - r, cy - r, null);
    }

    /**
     * The planet's glow (outside the disc) and the lit planet, painted once per size.
     * Index 0 is the glow, index 1 is the planet.
     */
    private BufferedImage[] earthImages(int r) {

        BufferedImage[] pair = earthCache.get(r);

        if (pair != null) {
            return pair;
        }

        if (earthCache.size() > 6) {
            earthCache.clear();
        }

        float glow = r * 1.18f;
        int size = (int) Math.ceil(glow) * 2 + 2;

        BufferedImage glowImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D a = glowImage.createGraphics();
        a.setPaint(new RadialGradientPaint(
            new Point2D.Float(size / 2f, size / 2f), glow,
            new float[]{0.84f, 1f},
            new Color[]{new Color(90, 170, 255, 150), new Color(90, 170, 255, 0)}
        ));
        a.fill(new Ellipse2D.Double(size / 2.0 - glow, size / 2.0 - glow, glow * 2, glow * 2));
        a.dispose();

        pair = new BufferedImage[]{glowImage, PlanetArt.disc(r)};
        earthCache.put(r, pair);
        return pair;
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
