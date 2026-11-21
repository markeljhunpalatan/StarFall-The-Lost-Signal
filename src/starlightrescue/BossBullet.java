
package starlightrescue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

public class BossBullet {

    private double x;
    private double y;

    private final int width = 20;
    private final int height = 12;

    private final double speed = 7.0;

    public BossBullet(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void update() {
        x -= speed;
    }

    public void draw(Graphics2D g2) {

        // Energy glow.
        g2.setColor(new Color(255, 40, 40, 90));
        g2.fillOval(
                (int) x - 5,
                (int) y - 5,
                width + 10,
                height + 10
        );

        // Main projectile.
        g2.setColor(Color.RED);
        g2.fillOval(
                (int) x,
                (int) y,
                width,
                height
        );

        // Bright center.
        g2.setColor(Color.WHITE);
        g2.fillOval(
                (int) x + 6,
                (int) y + 3,
                7,
                6
        );
    }

    public boolean isOffScreen() {
        return x + width < 0;
    }

    public Rectangle getBounds() {
        return new Rectangle(
                (int) x,
                (int) y,
                width,
                height
        );
    }
}
