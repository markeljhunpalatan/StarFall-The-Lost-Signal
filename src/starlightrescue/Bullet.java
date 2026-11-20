
package starlightrescue;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;

public class Bullet {

    private int x;
    private int y;

    private final int WIDTH = 15;
    private final int HEIGHT = 5;
    private final int SPEED = 10;

    public Bullet(int x, int y) {

        this.x = x;
        this.y = y;

    }

    public void update() {

        x += SPEED;

    }

    public void draw(Graphics g) {

        g.setColor(Color.YELLOW);

        g.fillRect(x, y, WIDTH, HEIGHT);

    }

    public boolean isOffScreen() {

        return x > 800;

    }

    public Rectangle getBounds() {

        return new Rectangle(x, y, WIDTH, HEIGHT);

    }

}
