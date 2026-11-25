package starlightrescue;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;

public class Bullet {

    private int x;
    private int y;
    private final int dy;       // vertical drift, used by the spread-shot weapons
    private final int level;    // weapon level: changes the colour of the shot

    private final int WIDTH = 15;
    private final int HEIGHT = 5;
    private final int SPEED = 10;

    public Bullet(int x, int y) {
        this(x, y, 0, 1);
    }

    public Bullet(int x, int y, int dy, int level) {
        this.x = x;
        this.y = y;
        this.dy = dy;
        this.level = level;
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

        // Soft glow, bright body, white tip
        g.setColor(glow);
        g.fillRect(x - 3, y - 3, WIDTH + 6, HEIGHT + 6);

        g.setColor(body);
        g.fillRect(x, y, WIDTH, HEIGHT);

        g.setColor(Color.WHITE);
        g.fillRect(x + WIDTH - 5, y + 1, 5, HEIGHT - 2);
    }

    public boolean isOffScreen() {
        return x > GamePanel.SCREEN_WIDTH
            || y < -10
            || y > GamePanel.SCREEN_HEIGHT + 10;
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, WIDTH, HEIGHT);
    }

}
