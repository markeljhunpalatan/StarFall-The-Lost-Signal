
package starlightrescue;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Polygon;
import java.awt.RenderingHints;

public class Player {

    private int x;
    private int y;

    private final int WIDTH = 60;
    private final int HEIGHT = 40;

    private final int SPEED = 5;

    private boolean movingUp;
    private boolean movingDown;

    public Player() {

        x = 50;
        y = 280;

    }

    public void update() {

        if (movingUp && y > 0) {
            y -= SPEED;
        }

        if (movingDown && y < 600 - HEIGHT) {
            y += SPEED;
        }

    }

    public void draw(Graphics g) {

        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        );

        // Engine flame
        g2.setColor(Color.ORANGE);

        Polygon flame = new Polygon(
            new int[]{x, x - 15, x},
            new int[]{y + 12, y + 20, y + 28},
            3
        );

        g2.fillPolygon(flame);

        // Main spaceship body
        g2.setColor(new Color(30, 144, 255));

        Polygon body = new Polygon(
            new int[]{
                x,
                x + 35,
                x + 60,
                x + 35
            },
            new int[]{
                y + 20,
                y + 5,
                y + 20,
                y + 35
            },
            4
        );

        g2.fillPolygon(body);

        // Upper wing
        g2.setColor(new Color(0, 90, 180));

        Polygon upperWing = new Polygon(
            new int[]{
                x + 10,
                x + 30,
                x + 40
            },
            new int[]{
                y + 15,
                y,
                y + 15
            },
            3
        );

        g2.fillPolygon(upperWing);

        // Lower wing
        Polygon lowerWing = new Polygon(
            new int[]{
                x + 10,
                x + 30,
                x + 40
            },
            new int[]{
                y + 25,
                y + 40,
                y + 25
            },
            3
        );

        g2.fillPolygon(lowerWing);

        // Cockpit
        g2.setColor(Color.CYAN);

        g2.fillOval(
            x + 30,
            y + 13,
            17,
            14
        );

        // Cockpit highlight
        g2.setColor(Color.WHITE);

        g2.fillOval(
            x + 35,
            y + 15,
            5,
            5
        );

        g2.dispose();

    }

    public void setMovingUp(boolean movingUp) {

        this.movingUp = movingUp;

    }

    public void setMovingDown(boolean movingDown) {

        this.movingDown = movingDown;

    }

    public int getBulletX() {

        return x + WIDTH;

    }

    public int getBulletY() {

        return y + HEIGHT / 2;

    }

    public Rectangle getBounds() {

        return new Rectangle(x, y, WIDTH, HEIGHT);

    }

}
