
package starlightrescue;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import java.util.Random;

public class Enemy {

    private int x;
    private int y;

    private final int WIDTH = 45;
    private final int HEIGHT = 35;

    private int speed;

    public Enemy() {

        Random random = new Random();

        x = 800;
        y = random.nextInt(600 - HEIGHT);

        speed = random.nextInt(3) + 2;

    }

    public void update() {

        x -= speed;

    }

    public void draw(Graphics g) {

        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        );

        // Tentacles
        g2.setColor(new Color(120, 0, 180));

        g2.drawLine(
            x + 10, y + 27,
            x + 5, y + 35
        );

        g2.drawLine(
            x + 22, y + 30,
            x + 22, y + 38
        );

        g2.drawLine(
            x + 35, y + 27,
            x + 40, y + 35
        );

        // Antennae
        g2.drawLine(
            x + 12, y + 8,
            x + 7, y
        );

        g2.drawLine(
            x + 33, y + 8,
            x + 38, y
        );

        // Antennae tips
        g2.setColor(Color.MAGENTA);

        g2.fillOval(x + 4, y - 3, 6, 6);
        g2.fillOval(x + 35, y - 3, 6, 6);

        // Alien body
        g2.setColor(new Color(150, 0, 180));

        g2.fillOval(
            x,
            y + 5,
            WIDTH,
            HEIGHT - 5
        );

        // Eyes
        g2.setColor(Color.WHITE);

        g2.fillOval(x + 9, y + 13, 10, 10);
        g2.fillOval(x + 26, y + 13, 10, 10);

        // Pupils
        g2.setColor(Color.BLACK);

        g2.fillOval(x + 12, y + 16, 5, 5);
        g2.fillOval(x + 29, y + 16, 5, 5);

        g2.dispose();

    }

    public Rectangle getBounds() {

        return new Rectangle(
            x,
            y,
            WIDTH,
            HEIGHT
        );

    }

    public boolean isOffScreen() {

        return x + WIDTH < 0;

    }

}
