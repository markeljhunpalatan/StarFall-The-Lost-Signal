package starlightrescue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * An energy shot fired by a boss OR by an armed alien.
 * (scale 1.0 = big red boss shot, scale 0.6 = small orange alien shot)
 */
public class BossBullet {

    private double x;
    private double y;

    private final double vx;   // horizontal speed (negative = moves left)
    private final double vy;   // vertical speed

    private final int width;
    private final int height;
    private final boolean small;

    public BossBullet(int x, int y) {
        this(x, y, -7.0, 0.0, 1.0);
    }

    public BossBullet(double x, double y, double vx, double vy, double scale) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.width = (int) Math.round(20 * scale);
        this.height = (int) Math.round(12 * scale);
        this.small = scale < 1.0;
    }

    public void update() {
        x += vx;
        y += vy;
    }

    public void draw(Graphics2D g2) {

        Color glow = small ? new Color(255, 150, 40, 90) : new Color(255, 40, 40, 90);
        Color core = small ? Color.ORANGE : Color.RED;

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

    public boolean isOffScreen() {
        return x + width < 0
            || y + height < 0
            || y > GamePanel.SCREEN_HEIGHT;
    }

    public Rectangle getBounds() {
        return new Rectangle((int) x, (int) y, width, height);
    }
}
