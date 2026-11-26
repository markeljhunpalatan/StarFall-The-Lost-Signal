package starlightrescue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Space hazards that threaten the player:
 *
 * ASTEROID - a rock of one of six LOOKS (see Look). Each look has its own pixel
 *            art, speed, path, toughness and reward. Big rocks break into smaller
 *            ones (crystal rocks shatter into three shards).
 * METEOR   - a fast fireball that cannot be destroyed. A red warning appears at
 *            the edge of the screen first, so you have time to dodge.
 */
public class Hazard {

    public enum Kind { ASTEROID, METEOR }

    /** How a rock moves. */
    public enum Path {
        STRAIGHT,   // flies straight left, with a gentle drift
        DIAGONAL,   // flies left and up or down at a slant (bounces off the edges)
        WAVE,       // flies left and sways up and down around its line
        BOUNCE      // flies left, ricocheting off the top and bottom edges
    }

    /** The kinds of asteroid. */
    public enum Look {
        //            speed  toughness reward  path             slope (diagonal direction)
        ROCK    (1.00, 1.0, 1.0, Path.STRAIGHT, 0),
        METAL   (0.75, 1.8, 1.6, Path.DIAGONAL, 1),     // heavy: drifts down and left
        ICE     (1.45, 0.7, 1.2, Path.STRAIGHT, 0),     // fragile and fast
        CRYSTAL (0.85, 1.0, 1.4, Path.WAVE, 0),         // sways, shatters into shards
        LAVA    (1.05, 1.2, 1.3, Path.DIAGONAL, -1),    // climbs up and left
        SPIKY   (1.25, 1.0, 1.1, Path.BOUNCE, 0);       // fast, ricochets off the edges

        final double speed;
        final double toughness;
        final double reward;
        final Path path;
        final int slope;

        Look(double speed, double toughness, double reward, Path path, int slope) {
            this.speed = speed;
            this.toughness = toughness;
            this.reward = reward;
            this.path = path;
            this.slope = slope;
        }
    }

    private static final Random RANDOM = new Random();

    /** How far a wave rock sways up and down, in pixels. */
    private static final double WAVE_HEIGHT = 30;

    private static final double[] RADIUS = {13, 22, 36};
    private static final int[] HEALTH = {5, 12, 24};     // in damage points (a basic shot does 10)
    private static final int[] CREDITS = {5, 9, 15};

    private final Kind kind;
    private final int size;                 // asteroids: 0 small, 1 medium, 2 large
    private final Look look;
    private final double radius;
    private final BufferedImage picture;    // the rock's pixel art (asteroids only)

    private double x;
    private double y;
    private final double vx;
    private double vy;

    private int health;
    private double angle;
    private final double spin;
    private double waveBase;                // the line a wave rock sways around
    private final double wavePhase;

    private int hitFlash = 0;
    private int warning = 0;                // meteors: ticks left before it launches
    private int tick = 0;

    private Hazard(Kind kind, Look look, int size, double radius, int health,
                   double x, double y, double vx, double vy) {

        this.kind = kind;
        this.look = look;
        this.size = size;
        this.radius = radius;
        this.health = health;
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.waveBase = y;
        this.wavePhase = RANDOM.nextDouble() * Math.PI * 2;

        this.angle = RANDOM.nextDouble() * Math.PI * 2;
        this.spin = (RANDOM.nextDouble() - 0.5) * 0.06;

        this.picture = (kind == Kind.ASTEROID)
            ? AsteroidArt.generate(look, radius, RANDOM.nextLong())
            : null;
    }

    /** A rock of the given size (0 small, 1 medium, 2 large), of the plain rock look. */
    public static Hazard asteroid(int size, double x, double y, double vx, double vy) {
        return make(Look.ROCK, size, x, y, vx, vy);
    }

    private static Hazard make(Look look, int size, double x, double y, double vx, double vy) {

        double radius = RADIUS[size];
        int health = Math.max(1, (int) Math.round(HEALTH[size] * look.toughness));

        return new Hazard(Kind.ASTEROID, look, size, radius, health, x, y, vx, vy);
    }

    /** The rock looks that appear in a mission scene. */
    public static Look[] looksFor(SpaceBackground.Scene scene) {
        switch (scene) {
            case EARTH:      return new Look[]{Look.ROCK, Look.ROCK, Look.METAL};
            case DEEP_SPACE: return new Look[]{Look.ROCK, Look.ICE, Look.CRYSTAL};
            case MOTHERSHIP: return new Look[]{Look.LAVA, Look.SPIKY, Look.METAL};
            case BLACK_HOLE: return new Look[]{Look.LAVA, Look.SPIKY, Look.METAL};   // the same asteroids Hard had before
            case WORMHOLE:   return new Look[]{Look.ICE, Look.CRYSTAL, Look.SPIKY};
            default:         return Look.values();
        }
    }

    /** A random asteroid entering from the right edge, of any look. */
    public static Hazard randomAsteroid(double speedFactor) {
        return randomAsteroid(speedFactor, Look.values());
    }

    /** A random asteroid entering from the right edge, of one of the given looks. */
    public static Hazard randomAsteroid(double speedFactor, Look[] looks) {

        Look look = looks[RANDOM.nextInt(looks.length)];

        int roll = RANDOM.nextInt(100);
        int size = (roll < 25) ? 2 : (roll < 65 ? 1 : 0);

        double base = (size == 2 ? 1.2 + RANDOM.nextDouble() * 0.6
                      : size == 1 ? 1.8 + RANDOM.nextDouble() * 0.8
                                  : 2.6 + RANDOM.nextDouble() * 1.0);
        double speed = base * Math.min(1.8, speedFactor) * look.speed;

        double r = RADIUS[size];
        double margin = (look.path == Path.WAVE) ? r + WAVE_HEIGHT + 4 : r;
        double minY = GamePanel.PLAY_TOP + margin;
        double maxY = GamePanel.PLAY_BOTTOM - margin;
        double startY = minY + RANDOM.nextDouble() * (maxY - minY);

        double vy;

        switch (look.path) {
            case DIAGONAL:
                vy = look.slope * (0.7 + RANDOM.nextDouble() * 0.5);
                break;
            case WAVE:
                vy = 0;
                break;
            case BOUNCE:
                vy = (RANDOM.nextBoolean() ? 1 : -1) * (2.2 + RANDOM.nextDouble() * 0.6);
                break;
            default:
                vy = (RANDOM.nextDouble() - 0.5) * 1.2;
                break;
        }

        return make(look, size, GamePanel.SCREEN_WIDTH + r + 10, startY, -speed, vy);
    }

    /** A fireball that launches after "warningTicks" ticks of warning. */
    public static Hazard meteor(double y, int warningTicks) {

        Hazard m = new Hazard(Kind.METEOR, Look.ROCK, 1, 10, 999,
            GamePanel.SCREEN_WIDTH + 40, y,
            -(9 + RANDOM.nextDouble() * 3), (RANDOM.nextDouble() - 0.5) * 4.5);

        m.warning = warningTicks;
        return m;
    }

    public void update() {

        tick++;

        if (hitFlash > 0) {
            hitFlash--;
        }

        if (kind == Kind.METEOR && warning > 0) {
            warning--;
            return;
        }

        x += vx;
        angle += spin;

        if (kind == Kind.ASTEROID && look.path == Path.WAVE) {

            // A wave rock sways up and down around its line. The line itself drifts
            // with vy (zero for a plain crystal, but the shards of a split crystal fan
            // out), and it bounces off the edges like any other rock.
            waveBase += vy;
            double low = GamePanel.PLAY_TOP + radius + WAVE_HEIGHT;
            double high = GamePanel.PLAY_BOTTOM - radius - WAVE_HEIGHT;

            if (waveBase < low) {
                waveBase = low;
                vy = Math.abs(vy);
            } else if (waveBase > high) {
                waveBase = high;
                vy = -Math.abs(vy);
            }

            y = waveBase + Math.sin(tick * 0.045 + wavePhase) * WAVE_HEIGHT;

        } else {

            y += vy;

            // Rocks bounce off the top and bottom of the play field
            if (kind == Kind.ASTEROID) {
                if (y < GamePanel.PLAY_TOP + radius) {
                    y = GamePanel.PLAY_TOP + radius;
                    vy = Math.abs(vy);
                } else if (y > GamePanel.PLAY_BOTTOM - radius) {
                    y = GamePanel.PLAY_BOTTOM - radius;
                    vy = -Math.abs(vy);
                }
            }
        }

    }

    /** Splits a destroyed rock (empty for the smallest). Crystal rocks shatter into three shards. */
    public List<Hazard> split() {

        List<Hazard> pieces = new ArrayList<>();

        if (kind != Kind.ASTEROID || size == 0) {
            return pieces;
        }

        if (look == Look.CRYSTAL) {

            for (int i = -1; i <= 1; i++) {
                pieces.add(make(look, size - 1, x, y + i * radius * 0.6,
                                vx * 0.9 - 0.2 + Math.abs(i) * 0.15, vy + i * 0.9));
            }

            return pieces;
        }

        pieces.add(make(look, size - 1, x, y - radius * 0.4, vx * 0.9 - 0.2, vy - 0.9));
        pieces.add(make(look, size - 1, x, y + radius * 0.4, vx * 0.9 + 0.1, vy + 0.9));

        return pieces;
    }

    public void takeDamage(int damage) {
        health -= damage;
        hitFlash = 4;
    }

    public boolean isDead() {
        return kind == Kind.ASTEROID && health <= 0;
    }

    public boolean isMeteor() {
        return kind == Kind.METEOR;
    }

    /** Meteors only hurt once they have launched. */
    public boolean isActive() {
        return kind == Kind.ASTEROID || warning == 0;
    }

    public boolean isWarning() {
        return kind == Kind.METEOR && warning > 0;
    }

    public int getSize() {
        return size;
    }

    public Look getLook() {
        return look;
    }

    /** The credits for destroying it: bigger and tougher rocks pay more, and some looks pay extra. */
    public int getCredits() {
        return (int) Math.round(CREDITS[size] * look.reward);
    }

    public int getCenterX() {
        return (int) x;
    }

    public int getCenterY() {
        return (int) y;
    }

    public boolean isOffScreen() {

        if (kind == Kind.METEOR) {
            return warning == 0 && (x < -80 || y < -80 || y > GamePanel.SCREEN_HEIGHT + 80);
        }

        return x + radius < -20;
    }

    public Rectangle getBounds() {

        double r = (kind == Kind.METEOR) ? 8 : radius * 0.82;
        return new Rectangle((int) (x - r), (int) (y - r), (int) (r * 2), (int) (r * 2));
    }

    // ===============================================================
    // DRAWING
    // ===============================================================

    public void draw(Graphics2D g2) {

        if (kind == Kind.METEOR) {
            if (warning > 0) {
                drawWarning(g2);
            } else {
                drawMeteor(g2);
            }
        } else {
            drawAsteroid(g2);
        }
    }

    /** The rock's pixel art, turning as it flies. */
    private void drawAsteroid(Graphics2D g2) {

        Graphics2D g = (Graphics2D) g2.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.translate(x, y);
        g.rotate(angle);

        int w = picture.getWidth() * AsteroidArt.SCREEN_SCALE;
        g.drawImage(picture, -w / 2, -w / 2, w, w, null);

        if (hitFlash > 0) {
            g.setColor(new Color(255, 255, 255, 120));
            g.fill(new Ellipse2D.Double(-radius, -radius, radius * 2, radius * 2));
        }

        g.dispose();
    }

    private void drawMeteor(Graphics2D g2) {

        // Fiery trail streaming behind the fireball
        for (int i = 14; i >= 1; i--) {
            double tx = x - vx * i * 0.85;
            double ty = y - vy * i * 0.85;
            double r = 9.5 * (1.0 - i / 16.0);
            int alpha = (int) (120 * (1.0 - i / 15.0));
            g2.setColor(new Color(255, 120 + (14 - i) * 6, 40, alpha));
            g2.fill(new Ellipse2D.Double(tx - r, ty - r, r * 2, r * 2));
        }

        // Glow and bright head
        g2.setPaint(new RadialGradientPaint(
            new Point2D.Double(x, y), 26f,
            new float[]{0f, 0.35f, 1f},
            new Color[]{new Color(255, 245, 210, 235), new Color(255, 140, 40, 170), new Color(255, 70, 20, 0)}
        ));
        g2.fill(new Ellipse2D.Double(x - 26, y - 26, 52, 52));

        g2.setColor(new Color(255, 250, 230));
        g2.fill(new Ellipse2D.Double(x - 6, y - 6, 12, 12));
    }

    /** The red "incoming!" marker at the right edge of the screen. */
    private void drawWarning(Graphics2D g2) {

        double pulse = 0.55 + 0.45 * Math.sin(tick * 0.45);
        int ex = GamePanel.SCREEN_WIDTH - 30;
        int ey = (int) y;

        // Red glow along the edge
        g2.setPaint(new GradientPaint(GamePanel.SCREEN_WIDTH - 70, 0, new Color(255, 40, 40, 0),
                                      GamePanel.SCREEN_WIDTH, 0, new Color(255, 40, 40, (int) (90 * pulse))));
        g2.fillRect(GamePanel.SCREEN_WIDTH - 70, ey - 34, 70, 68);

        // Dashed line showing the path it will take
        Stroke old = g2.getStroke();
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0,
                                     new float[]{10, 8}, (tick * 0.6f) % 18f));
        g2.setColor(new Color(255, 90, 80, (int) (110 * pulse)));
        g2.drawLine(ex - 20, ey, (int) (ex - 20 + vx * 28), (int) (ey + vy * 28));
        g2.setStroke(old);

        // Warning triangle
        Polygon tri = new Polygon(new int[]{ex - 13, ex + 13, ex}, new int[]{ey + 12, ey + 12, ey - 14}, 3);
        g2.setColor(new Color(255, 70, 70, (int) (150 + 100 * pulse)));
        g2.fillPolygon(tri);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2.4f));
        g2.drawLine(ex, ey - 6, ex, ey + 2);
        g2.fillOval(ex - 1, ey + 5, 3, 3);
        g2.setStroke(old);
    }
}
