package starlightrescue;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;

public class Bullet {

    private int x;
    private int y;
    private final int dy;   // vertical drift, used by the triple-shot weapon

    private final int WIDTH = 15;
    private final int HEIGHT = 5;
    private final int SPEED = 10;

    public Bullet(int x, int y) {
        this(x, y, 0);
    }

    public Bullet(int x, int y, int dy) {
        this.x = x;
        this.y = y;
        this.dy = dy;
    }

    public void update() {
        x += SPEED;
        y += dy;
    }

    public void draw(Graphics g) {

        // Soft glow, bright body, white tip
        g.setColor(new Color(255, 230, 0, 70));
        g.fillRect(x - 3, y - 3, WIDTH + 6, HEIGHT + 6);

        g.setColor(Color.YELLOW);
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
