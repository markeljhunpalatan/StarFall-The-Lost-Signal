package starlightrescue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * An enemy shot. There are four kinds:
 *
 * BOSS    - a big red energy shot
 * ALIEN   - a small orange shot from an armed alien
 * RING    - a violet orb from the Mothership's energy ring
 * MISSILE - a homing missile from the Warship. It steers toward you, but you
 *           can shoot it down. It fizzles out after a few seconds.
 */
public class BossBullet {

    public enum Style { BOSS, ALIEN, RING, MISSILE }

    private double x;
    private double y;

    private double vx;          // horizontal speed (negative = moves left)
    private double vy;          // vertical speed

    private final int width;
    private final int height;
    private final Style style;

    // Homing missiles only
    private double angle;
    private double speed;
    private int life = 0;
    private int tick = 0;

    public BossBullet(int x, int y) {
        this(x, y, -7.0, 0.0, 1.0);
    }

    /** scale 1.0 = big boss shot, below 1.0 = small alien shot. */
    public BossBullet(double x, double y, double vx, double vy, double scale) {
        this(x, y, vx, vy, scale, scale < 1.0 ? Style.ALIEN : Style.BOSS);
    }

    private BossBullet(double x, double y, double vx, double vy, double scale, Style style) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.width = (int) Math.round(20 * scale);
        this.height = (int) Math.round(12 * scale);
        this.style = style;
    }

    /** One orb of the Mothership's ring attack. */
    public static BossBullet ring(double x, double y, double vx, double vy) {
        return new BossBullet(x - 7, y - 7, vx, vy, 0.7, Style.RING);
    }

    /** A homing missile. angle is its starting direction (Math.PI = straight left). */
    public static BossBullet missile(double x, double y, double angle) {

        BossBullet m = new BossBullet(x, y, 0, 0, 0.8, Style.MISSILE);
        m.angle = angle;
        m.speed = 3.4;
        m.life = 330;
        m.vx = Math.cos(angle) * m.speed;
        m.vy = Math.sin(angle) * m.speed;
        return m;
    }

    public void update() {
        update(Double.NaN, Double.NaN);
    }

    /** Homing missiles steer toward (targetX, targetY). Other shots ignore it. */
    public void update(double targetX, double targetY) {

        tick++;

        if (style == Style.MISSILE) {

            life--;

            // Steer toward the target until the missile has flown past it
            if (!Double.isNaN(targetX) && x > targetX + 10 && life > 30) {

                double want = Math.atan2(targetY - (y + height / 2.0), targetX - (x + width / 2.0));
                double diff = Math.atan2(Math.sin(want - angle), Math.cos(want - angle));
                angle += Math.max(-0.042, Math.min(0.042, diff));
            }

            vx = Math.cos(angle) * speed;
            vy = Math.sin(angle) * speed;
        }

        x += vx;
        y += vy;
    }

    public boolean isMissile() {
        return style == Style.MISSILE;
    }

    /** Missiles can be shot down by the player. */
    public boolean isDestroyable() {
        return style == Style.MISSILE;
    }

    /** A missile that ran out of fuel (the game shows a small puff). */
    public boolean isExpired() {
        return style == Style.MISSILE && life <= 0;
    }

    public int getCenterX() {
        return (int) x + width / 2;
    }

    public int getCenterY() {
        return (int) y + height / 2;
    }

    public void draw(Graphics2D g2) {

        if (style == Style.MISSILE) {
            drawMissile(g2);
            return;
        }

        Color glow;
        Color core;

        switch (style) {
            case ALIEN:
                glow = new Color(255, 150, 40, 90);
                core = Color.ORANGE;
                break;
            case RING:
                glow = new Color(190, 90, 255, 100);
                core = new Color(215, 140, 255);
                break;
            default:
                glow = new Color(255, 40, 40, 90);
                core = Color.RED;
                break;
        }

        // Energy glow
        g2.setColor(glow);
        g2.fillOval((int) x - 5, (int) y - 5, width + 10, height + 10);

        // Main projectile
        g2.setColor(core);
        g2.fillOval((int) x, (int) y, width, height);

        // Bright center
        g2.setColor(Color.WHITE);
        g2.fillOval((int) x + width * 3 / 10, (int) y + height / 4,
                    width * 35 / 100, height / 2);
    }

    private void drawMissile(Graphics2D g0) {

        Graphics2D g2 = (Graphics2D) g0.create();
        g2.translate(x + width / 2.0, y + height / 2.0);
        g2.rotate(angle);

        // Flame (flickers)
        int flame = 8 + (tick % 3) * 3;
        g2.setColor(new Color(255, 150, 40, 200));
        g2.fillPolygon(new int[]{-9, -9 - flame, -9}, new int[]{-3, 0, 3}, 3);
        g2.setColor(new Color(255, 240, 180));
        g2.fillPolygon(new int[]{-9, -9 - flame / 2, -9}, new int[]{-2, 0, 2}, 3);

        // Fins
        g2.setColor(new Color(120, 125, 140));
        g2.fillPolygon(new int[]{-9, -4, -9}, new int[]{-7, -3, -3}, 3);
        g2.fillPolygon(new int[]{-9, -4, -9}, new int[]{7, 3, 3}, 3);

        // Body and red nose
        g2.setColor(new Color(210, 215, 225));
        g2.fillRoundRect(-9, -3, 16, 6, 4, 4);
        g2.setColor(new Color(235, 50, 50));
        g2.fillPolygon(new int[]{7, 12, 7}, new int[]{-3, 0, 3}, 3);

        // Blinking light
        if ((tick / 6) % 2 == 0) {
            g2.setColor(new Color(255, 60, 60, 200));
            g2.fillOval(-2, -6, 4, 4);
        }

        g2.dispose();
    }

    public boolean isOffScreen() {
        return x + width < 0
            || x > GamePanel.SCREEN_WIDTH + 40
            || y + height < 0
            || y > GamePanel.SCREEN_HEIGHT
            || isExpired();
    }

    public Rectangle getBounds() {

        if (style == Style.MISSILE) {
            return new Rectangle(getCenterX() - 6, getCenterY() - 6, 12, 12);
        }

        return new Rectangle((int) x, (int) y, width, height);
    }
}
