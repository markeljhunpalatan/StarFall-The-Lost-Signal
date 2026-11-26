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

    /** Homing: steer gently toward a point (the nearest alien). */
    public void steer(double targetX, double targetY) {

        if (!weapon.homing || targetX <= x) {
            return;
        }

        double pull = (targetY - y) * 0.05;
        dy = Math.max(-6, Math.min(6, dy + Math.max(-0.7, Math.min(0.7, pull))));
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
