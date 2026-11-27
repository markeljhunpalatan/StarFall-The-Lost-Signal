package starlightrescue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * An enemy shot. There are five kinds:
 *
 * BOSS    - a big red energy shot
 * ALIEN   - a small orange shot from an armed alien
 * RING    - a violet orb from the Mothership's energy ring
 * MISSILE - a homing missile from the Warship. It steers toward you, but you
 *           can shoot it down. It fizzles out after a few seconds.
 * FIST    - the golem's rocket fist. It works exactly like a missile.
 * SCYTHE  - a spinning crescent blade from the executioner's scythe. Big, and it
 *           can't be shot down: dodge it.
 * SOUL    - a lost soul raised by the executioner. A slow missile: it drifts after
 *           you, and one hit destroys it.
 */
public class BossBullet {

    public enum Style { BOSS, ALIEN, RING, MISSILE, FIST, SCYTHE, SOUL }

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
        return homing(x, y, angle, Style.MISSILE);
    }

    /** The golem's rocket fist, launched with its middle at (cx, cy). It homes in like a missile. */
    public static BossBullet rocketFist(double cx, double cy, double angle) {
        return homing(cx - 8, cy - 5, angle, Style.FIST);
    }

    /** A crescent blade from the executioner's scythe, with its middle at (cx, cy). */
    public static BossBullet scythe(double cx, double cy, double vx, double vy) {
        return new BossBullet(cx - 10, cy - 6, vx, vy, 1.0, Style.SCYTHE);
    }

    /** A lost soul, with its middle at (cx, cy): it drifts after you like a slow missile. */
    public static BossBullet soul(double cx, double cy, double angle) {

        BossBullet s = homing(cx - 8, cy - 5, angle, Style.SOUL);
        s.speed = 2.5;
        s.life = 420;
        s.vx = Math.cos(angle) * s.speed;
        s.vy = Math.sin(angle) * s.speed;
        return s;
    }

    private static BossBullet homing(double x, double y, double angle, Style style) {

        BossBullet m = new BossBullet(x, y, 0, 0, 0.8, style);
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

        if (isMissile()) {

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

    /** Homing missiles, the golem's rocket fists and the executioner's lost souls. */
    public boolean isMissile() {
        return style == Style.MISSILE || style == Style.FIST || style == Style.SOUL;
    }

    /** Missiles can be shot down by the player. */
    public boolean isDestroyable() {
        return isMissile();
    }

    /** A missile that ran out of fuel (the game shows a small puff). */
    public boolean isExpired() {
        return isMissile() && life <= 0;
    }

    public Style getStyle() {
        return style;
    }

    /** How fast it is moving up (negative) or down (positive). */
    public double getVy() {
        return vy;
    }

    public int getCenterX() {
        return (int) x + width / 2;
    }

    public int getCenterY() {
        return (int) y + height / 2;
    }

    // ---------- Pictures (the fire bullets in resources/sprites/enemy-bullets) ----------

    /** The picture for a kind of shot, or null if the art is missing (the shapes are drawn instead). */
    private static BufferedImage picture(Style style) {
        switch (style) {
            case ALIEN:   return Sprites.get("enemy-bullets/alien_orb.png");
            case RING:    return Sprites.get("enemy-bullets/ring_orb.png");
            case MISSILE: return Sprites.get("enemy-bullets/missile.png");
            default:      return Sprites.get("enemy-bullets/boss_orb.png");
        }
    }

    /** How big the picture is drawn: boss shots are the biggest, alien shots the smallest. */
    private int pictureSize() {
        switch (style) {
            case ALIEN:  return 16;
            case RING:   return 18;
            case MISSILE: return 18;
            default:     return 26;
        }
    }

    /** The burst played when a shot hits something or is shot down. */
    public static BufferedImage[] burstFrames() {
        BufferedImage burst = Sprites.get("enemy-bullets/burst.png");
        return burst == null ? new BufferedImage[0] : new BufferedImage[]{burst};
    }

    public void draw(Graphics2D g2) {

        if (style == Style.FIST && drawFist(g2)) {
            return;
        }

        if (style == Style.SCYTHE) {
            ExecutionerArt.drawScythe(g2, getCenterX(), getCenterY(), -tick * 0.35, 36);   // about as tall as its hitbox
            return;
        }

        if (style == Style.SOUL) {
            ExecutionerArt.drawSoul(g2, getCenterX(), getCenterY(), tick);
            return;
        }

        if (isMissile()) {
            drawMissile(g2);
            return;
        }

        BufferedImage picture = picture(style);

        if (picture != null) {
            Graphics2D g = (Graphics2D) g2.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int size = pictureSize();
            g.drawImage(picture, getCenterX() - size / 2, getCenterY() - size / 2, size, size, null);
            g.dispose();
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

    /** The golem's glowing rocket fist, pointing where it flies. False if its picture is missing. */
    private boolean drawFist(Graphics2D g0) {

        BufferedImage picture = GolemArt.armFrame(tick);

        if (picture == null) {
            return false;
        }

        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.translate(getCenterX(), getCenterY());
        g.rotate(angle);

        // The picture points right; flying left it would be upside down, so it is flipped back
        if (Math.cos(angle) < 0) {
            g.scale(1, -1);
        }

        int w = picture.getWidth() * 2;
        int h = picture.getHeight() * 2;
        g.drawImage(picture, -w / 2, -h / 2, w, h, null);
        g.dispose();
        return true;
    }

    private void drawMissile(Graphics2D g0) {

        BufferedImage picture = picture(Style.MISSILE);

        if (picture != null) {
            // The picture points up and to the right, so it is turned by a quarter more than its heading
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.translate(getCenterX(), getCenterY());
            g.rotate(angle + Math.PI / 4);
            int size = pictureSize();
            g.drawImage(picture, -size / 2, -size / 2, size, size, null);
            g.dispose();
            return;
        }

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

        if (style == Style.FIST) {
            return new Rectangle(getCenterX() - 9, getCenterY() - 7, 18, 14);   // the fist is a bit bigger
        }

        if (style == Style.SCYTHE) {
            return new Rectangle(getCenterX() - 13, getCenterY() - 28, 26, 56);  // the blade is tall
        }

        if (style == Style.SOUL) {
            return new Rectangle(getCenterX() - 8, getCenterY() - 10, 16, 20);
        }

        return new Rectangle((int) x, (int) y, width, height);
    }
}
