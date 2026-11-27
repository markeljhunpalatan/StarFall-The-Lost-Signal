package starlightrescue;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * One shot from the player's ship (or from the wingman).
 *
 * A bullet knows its WEAPON, which decides how it looks and how it flies (see
 * Weapon). Every bullet carries its own DAMAGE, and homing bullets can be steered
 * toward a target each tick with steer().
 */
public class Bullet {

    private double x;
    private double y;
    private double dy;            // vertical speed (drift, or steering for homing shots)
    private final Weapon weapon;
    private final int damage;
    private final double phase;   // where a weaving shot starts in its wave
    private int age = 0;          // ticks since it was fired

    private Enemy target;                                              // a Nova star's own alien
    private final java.util.Set<Enemy> pierced = new java.util.HashSet<>();   // aliens an Omega orb has gone through

    public Bullet(double x, double y, double dy, Weapon weapon, int damage, double phase) {
        this.x = x;
        this.y = y;
        this.dy = dy;
        this.weapon = weapon;
        this.damage = damage;
        this.phase = phase;
    }

    public void update() {

        age++;
        x += weapon.speed;

        if (weapon.wave) {
            // Weave up and down around the line it was fired along
            y += dy + Math.sin(age * 0.32 + phase) * 1.6;
        } else {
            y += dy;
        }
    }

    /** Homing: steer gently toward a point (the nearest alien). Nova stars hunt harder than comets. */
    public void steer(double targetX, double targetY) {

        if (!weapon.homing || targetX <= x) {
            return;
        }

        boolean hunter = (weapon == Weapon.NOVA || weapon == Weapon.NOVA_SHARD);
        double pull = (targetY - y) * (hunter ? 0.09 : 0.05);
        double turn = hunter ? 1.1 : 0.7;
        double max = hunter ? 7 : 6;
        dy = Math.max(-max, Math.min(max, dy + Math.max(-turn, Math.min(turn, pull))));
    }

    /** The alien a Nova star (or shard) is hunting, or null. */
    public Enemy getTarget() {
        return target;
    }

    public void setTarget(Enemy enemy) {
        target = enemy;
    }

    /** True if this Omega orb has already gone through that alien (it hits each one only once). */
    public boolean hasPierced(Enemy enemy) {
        return pierced.contains(enemy);
    }

    /** Marks an alien as gone through, and returns how many this orb has gone through so far. */
    public int pierce(Enemy enemy) {
        pierced.add(enemy);
        return pierced.size();
    }

    public int getAge() {
        return age;
    }

    public int getDamage() {
        return damage;
    }

    public Weapon getWeapon() {
        return weapon;
    }

    /** Vertical speed, used when a dodging alien works out where a shot will be. */
    public double getDy() {
        return dy;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public boolean isOffScreen() {
        return x > GamePanel.SCREEN_WIDTH + 40 || y < -40 || y > GamePanel.SCREEN_HEIGHT + 40;
    }

    /** The hit area: a little smaller than the picture, so near misses feel fair. */
    public Rectangle getBounds() {

        if (weapon == Weapon.OMEGA) {
            return new Rectangle((int) x - 12, (int) y - 12, 24, 24);    // the orb is big
        }
        if (weapon == Weapon.NOVA) {
            return new Rectangle((int) x - 7, (int) y - 7, 14, 14);
        }
        if (weapon == Weapon.NOVA_SHARD) {
            return new Rectangle((int) x - 5, (int) y - 5, 10, 10);
        }

        return new Rectangle((int) x, (int) y - 4, 14, 8);
    }

    private static final RadialGradientPaint ENERGY_GLOW = new RadialGradientPaint(
        new Point2D.Double(0, 0), 16f, new float[]{0f, 1f},
        new Color[]{new Color(90, 230, 255, 120), new Color(90, 230, 255, 0)});

    private static final GradientPaint ENERGY_TAIL = new GradientPaint(
        -14, 0, new Color(90, 230, 255, 0), 4, 0, new Color(200, 250, 255, 255));

    public void draw(Graphics g) {

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // The wingman fires its auto cannon's own bullets (see AllyArt)
        if (weapon == Weapon.ALLY && AllyArt.drawBullet(g2, x, y, age)) {
            g2.dispose();
            return;
        }

        // The max weapons' shots are drawn in code
        if (weapon == Weapon.OMEGA) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawOmegaOrb(g2, x, y, age);
            g2.dispose();
            return;
        }
        if (weapon == Weapon.NOVA || weapon == Weapon.NOVA_SHARD) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            drawNovaStar(g2, x, y, dy, age, weapon == Weapon.NOVA ? 9 : 6);
            g2.dispose();
            return;
        }

        // The Arc cannon's shots are drawn as glowing energy bolts (see drawEnergyBolt)
        if (weapon == Weapon.ARC) {
            g2.translate(x, y);
            drawEnergyBolt(g2);
            g2.dispose();
            return;
        }

        BufferedImage[] frames = weapon.flight();
        BufferedImage frame = null;

        if (frames.length > 0) {
            frame = frames[(age / 3) % frames.length];
        }

        // Homing shots turn to face the way they are going
        if (weapon.homing) {
            g2.rotate(Math.atan2(dy, weapon.speed), x, y);
        }

        if (frame != null) {
            int w = (int) Math.round(frame.getWidth() * weapon.size);
            int h = (int) Math.round(frame.getHeight() * weapon.size);
            g2.drawImage(frame, (int) Math.round(x - w / 2.0), (int) Math.round(y - h / 2.0), w, h, null);
        } else {
            // The art is missing: draw a glowing capsule in the weapon's colour instead
            Color glow = weapon.glow;
            g2.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 90));
            g2.fillRoundRect((int) x - 5, (int) y - 5, 22, 10, 10, 10);
            g2.setColor(glow);
            g2.fillRoundRect((int) x, (int) y - 2, 14, 4, 4, 4);
        }

        g2.dispose();
    }

    private static final Color OMEGA_GOLD = new Color(255, 205, 70);

    /** A soft round glow of a colour, fading out to nothing. */
    private static void glow(Graphics2D g, double cx, double cy, double r, Color c, int alpha) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) r, new float[]{0f, 1f},
            new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha), new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
    }

    /** An Omega orb: a white-hot golden ball with a glow and a fading trail behind it. */
    static void drawOmegaOrb(Graphics2D g, double cx, double cy, int age) {

        double r = 12 + Math.sin(age * 0.5) * 0.8;
        for (int k = 5; k >= 1; k--) {
            glow(g, cx - k * 10, cy, r * (1.15 - k * 0.13), OMEGA_GOLD, 115 - k * 18);
        }
        glow(g, cx, cy, r * 2.3, OMEGA_GOLD, 140);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx - r * 0.25, cy - r * 0.25), (float) r,
            new float[]{0f, 0.5f, 1f}, new Color[]{Color.WHITE, new Color(255, 235, 150), new Color(255, 170, 40)}));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
    }

    /** A Nova star: a spinning five-pointed star with a glow and a short streak behind it. */
    static void drawNovaStar(Graphics2D g, double cx, double cy, double dy, int age, double r) {

        Color gold = new Color(255, 215, 90);
        g.setStroke(new java.awt.BasicStroke((float) (r * 0.35), java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g.setPaint(new GradientPaint((float) (cx - 26), (float) (cy - dy * 3), new Color(255, 215, 90, 0),
                                     (float) cx, (float) cy, new Color(255, 215, 90, 170)));
        g.drawLine((int) (cx - 26), (int) (cy - dy * 3), (int) cx, (int) cy);

        glow(g, cx, cy, r * 2.4, gold, 150);
        java.awt.geom.Path2D star = new java.awt.geom.Path2D.Double();
        double turn = age * 0.25;
        for (int k = 0; k < 10; k++) {
            double a = turn - Math.PI / 2 + k * Math.PI / 5;
            double rr = (k % 2 == 0) ? r : r * 0.45;
            if (k == 0) {
                star.moveTo(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr);
            } else {
                star.lineTo(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr);
            }
        }
        star.closePath();
        g.setColor(gold);
        g.fill(star);
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(cx - r * 0.25, cy - r * 0.25, r * 0.5, r * 0.5));
    }

    /**
     * A glowing energy bolt, drawn with its centre at (0, 0): a soft cyan glow, a
     * short fading tail behind, and a white-hot core.
     */
    private static void drawEnergyBolt(Graphics2D g2) {

        g2.setPaint(ENERGY_GLOW);
        g2.fill(new Ellipse2D.Double(-16, -16, 32, 32));

        g2.setPaint(ENERGY_TAIL);
        g2.fill(new RoundRectangle2D.Double(-14, -1.5, 18, 3, 3, 3));

        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Double(-2, -1, 10, 2, 2, 2));
    }
}
