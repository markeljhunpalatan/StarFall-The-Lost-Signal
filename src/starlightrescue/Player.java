package starlightrescue;

import java.awt.BasicStroke;
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

    private final int SPEED = 6;

    private boolean movingUp;
    private boolean movingDown;

    private boolean blinking;   // flickers while the ship is briefly invulnerable
    private boolean shielded;   // draws the shield bubble
    private int tick = 0;       // animation counter

    public Player() {

        x = 50;
        y = 280;

    }

    public void update() {

        tick++;

        if (movingUp) {
            y -= SPEED;
        }

        if (movingDown) {
            y += SPEED;
        }

        // Stay inside the play field (below the HUD, above the bottom edge)
        y = Math.max(GamePanel.PLAY_TOP,
                     Math.min(GamePanel.SCREEN_HEIGHT - HEIGHT, y));

    }

    public void draw(Graphics g) {

        // While invulnerable the ship blinks
        if (blinking && (tick / 4) % 2 == 0) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        );

        // Engine flame (flickers)
        int flicker = (tick / 2) % 3;

        g2.setColor(Color.ORANGE);

        Polygon flame = new Polygon(
            new int[]{x, x - 12 - flicker * 4, x},
            new int[]{y + 12, y + 20, y + 28},
            3
        );

        g2.fillPolygon(flame);

        g2.setColor(Color.YELLOW);

        Polygon innerFlame = new Polygon(
            new int[]{x, x - 6 - flicker * 2, x},
            new int[]{y + 16, y + 20, y + 24},
            3
        );

        g2.fillPolygon(innerFlame);

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

        // Shield bubble
        if (shielded) {

            g2.setColor(new Color(80, 220, 255, 55));
            g2.fillOval(x - 12, y - 12, WIDTH + 24, HEIGHT + 24);

            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(150, 240, 255, 130 + (int) (Math.sin(tick * 0.2) * 60)));
            g2.drawOval(x - 12, y - 12, WIDTH + 24, HEIGHT + 24);
        }

        g2.dispose();

    }

    public void setMovingUp(boolean movingUp) {

        this.movingUp = movingUp;

    }

    public void setMovingDown(boolean movingDown) {

        this.movingDown = movingDown;

    }

    public void setBlinking(boolean blinking) {

        this.blinking = blinking;

    }

    public void setShielded(boolean shielded) {

        this.shielded = shielded;

    }

    public int getBulletX() {

        return x + WIDTH;

    }

    public int getBulletY() {

        return y + HEIGHT / 2;

    }

    public int getCenterX() {

        return x + WIDTH / 2;

    }

    public int getCenterY() {

        return y + HEIGHT / 2;

    }

    /** A slightly smaller hitbox than the drawing, so near-misses feel fair. */
    public Rectangle getBounds() {

        return new Rectangle(x + 6, y + 8, WIDTH - 12, HEIGHT - 16);

    }

}
