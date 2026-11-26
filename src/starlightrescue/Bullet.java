package starlightrescue;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * A shot from the player's ship.
 *
 * Every bullet carries its own DAMAGE. Weapons that fire more bullets do less
 * damage per bullet (see Upgrades.BULLET_DAMAGE), and the CANNONS MK II upgrade
 * makes bullets bigger and stronger.
 */
public class Bullet {

    static final int SPEED = 10;

    private int x;
    private int y;
    private final int dy;       // vertical drift, used by the spread-shot weapons
    private final int level;    // weapon pattern: changes the colour of the shot
    private final int damage;
    private final int width;
    private final int height;

    public Bullet(int x, int y) {
        this(x, y, 0, 1, 10, 0);
    }

    /**
     * @param caliber 0 - 3, the CANNONS MK II level (bigger, brighter bullets)
     */
    public Bullet(int x, int y, int dy, int level, int damage, int caliber) {
        this.x = x;
        this.y = y;
        this.dy = dy;
        this.level = level;
        this.damage = damage;
        this.width = 15 + caliber * 3;
        this.height = 5 + caliber;
    }

    public void update() {
        x += SPEED;
        y += dy;
    }

    public void draw(Graphics g) {

        Color glow;
        Color body;

        switch (level) {
            case 2:
                glow = new Color(255, 160, 40, 80);
                body = new Color(255, 190, 40);
                break;
            case 3:
                glow = new Color(60, 220, 255, 80);
                body = new Color(80, 235, 255);
                break;
            case 4:
                glow = new Color(255, 70, 220, 90);
                body = new Color(255, 110, 235);
                break;
            default:
                glow = new Color(255, 230, 0, 70);
                body = Color.YELLOW;
                break;
        }

        // Soft glow, bright capsule, white-hot core
        Graphics2D g2 = (Graphics2D) g;

        g2.setColor(glow);
        g2.fillRoundRect(x - 5, y - 4, width + 10, height + 8, height + 8, height + 8);

        g2.setColor(body);
        g2.fillRoundRect(x, y, width, height, height, height);

        g2.setColor(Color.WHITE);
        g2.fillRoundRect(x + 4, y + 1, width - 5, Math.max(2, height - 2), height, height);
    }

    public int getDamage() {
        return damage;
    }

    public int getDy() {
        return dy;
    }

    public boolean isOffScreen() {
        return x > GamePanel.SCREEN_WIDTH
            || y < -10
            || y > GamePanel.SCREEN_HEIGHT + 10;
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }

}
