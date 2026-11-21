
package starlightrescue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.GradientPaint;

public class Boss {

    private int x;
    private double y;

    private final int width = 130;
    private final int height = 170;

    private double speed = 2.0;
    private int health = 100;

    private final int maxHealth = 100;

    public Boss(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void update(int panelHeight) {

        y += speed;

        // Move vertically and bounce at the boundaries.
        if (y <= 30 || y + height >= panelHeight - 30) {
            speed *= -1;
        }
    }

    public void draw(Graphics2D g2) {

        int currentY = (int) y;

        // Main mothership body.
        g2.setPaint(new GradientPaint(
                x, currentY,
                new Color(100, 35, 150),
                x + width, currentY + height,
                new Color(35, 10, 70)
        ));

        g2.fillOval(x, currentY + 20, width, 120);

        // Upper structure.
        g2.setColor(new Color(130, 55, 190));
        g2.fillOval(x + 25, currentY, 80, 75);

        // Side wings.
        g2.setColor(new Color(75, 25, 120));

        int[] leftX = {x + 15, x - 15, x + 20};
        int[] leftY = {currentY + 65, currentY + 95, currentY + 125};
        g2.fillPolygon(leftX, leftY, 3);

        int[] rightX = {
                x + width - 15,
                x + width + 15,
                x + width - 20
        };

        int[] rightY = {
                currentY + 65,
                currentY + 95,
                currentY + 125
        };

        g2.fillPolygon(rightX, rightY, 3);

        // Red glowing core.
        g2.setColor(new Color(255, 40, 40, 70));
        g2.fillOval(x + 35, currentY + 48, 60, 60);

        g2.setColor(new Color(255, 30, 30));
        g2.fillOval(x + 43, currentY + 56, 44, 44);

        g2.setColor(new Color(255, 180, 180));
        g2.fillOval(x + 55, currentY + 68, 20, 20);

        // Alien energy lights.
        g2.setColor(Color.CYAN);
        g2.fillOval(x + 15, currentY + 85, 10, 10);
        g2.fillOval(x + 105, currentY + 85, 10, 10);

        // Lower structure.
        g2.setColor(new Color(45, 15, 75));
        g2.fillRoundRect(
                x + 30,
                currentY + 120,
                70,
                30,
                10,
                10
        );
    }

    public void takeDamage(int damage) {
        health -= damage;

        if (health < 0) {
            health = 0;
        }
    }

    public int getHealth() {
        return health;
    }

    public int getMaxHealth() {
        return maxHealth;
    }

    public boolean isDefeated() {
        return health <= 0;
    }

    public Rectangle getBounds() {
        return new Rectangle(x, (int) y, width, height);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return (int) y;
    }
}
