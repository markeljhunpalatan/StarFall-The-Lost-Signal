package starlightrescue;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import java.util.Random;

public class Enemy {

    /**
     * SCOUT  - flies straight (the original alien)
     * ZIGZAG - weaves up and down, harder to hit
     * GUNNER - slow, takes 2 hits and shoots back
     */
    public enum Type { SCOUT, ZIGZAG, GUNNER }

    private static final Random RANDOM = new Random();

    private double x;
    private double y;

    private final double baseY;       // the line the alien weaves around
    private final double amplitude;   // how far it weaves (0 = straight)
    private final double phase;

    private final Type type;

    private final int WIDTH = 45;
    private final int HEIGHT = 35;

    private final double speed;
    private int health;

    private int tick = 0;
    private int shootCooldown = 0;
    private int hitFlash = 0;

    public Enemy() {
        this(Type.SCOUT);
    }

    public Enemy(Type type) {

        this.type = type;

        x = GamePanel.SCREEN_WIDTH;
        phase = RANDOM.nextDouble() * Math.PI * 2;

        switch (type) {

            case ZIGZAG:
                speed = RANDOM.nextInt(2) + 2;
                amplitude = 45 + RANDOM.nextInt(30);
                health = 1;
                break;

            case GUNNER:
                speed = 1.0 + RANDOM.nextDouble();
                amplitude = 0;
                health = 2;
                shootCooldown = 50 + RANDOM.nextInt(60);
                break;

            default:
                speed = RANDOM.nextInt(3) + 2;
                amplitude = 0;
                health = 1;
                break;
        }

        // Pick a lane that keeps the whole weave on screen
        int minY = GamePanel.PLAY_TOP + (int) amplitude;
        int maxY = GamePanel.SCREEN_HEIGHT - HEIGHT - 5 - (int) amplitude;
        baseY = minY + RANDOM.nextInt(Math.max(1, maxY - minY));
        y = baseY;
    }

    public void update() {

        x -= speed;
        tick++;

        if (amplitude > 0) {
            y = baseY + amplitude * Math.sin(tick * 0.06 + phase);
        }

        if (shootCooldown > 0) {
            shootCooldown--;
        }

        if (hitFlash > 0) {
            hitFlash--;
        }
    }

    /** Armed aliens call this every frame; true means "fire now". */
    public boolean shouldShoot() {

        if (type == Type.GUNNER && shootCooldown == 0
                && x < GamePanel.SCREEN_WIDTH - 60 && x > 200) {

            shootCooldown = 110 + RANDOM.nextInt(50);
            return true;
        }

        return false;
    }

    public void takeDamage(int damage) {
        health -= damage;
        hitFlash = 4;
    }

    public boolean isDead() {
        return health <= 0;
    }

    public int getScore() {
        switch (type) {
            case ZIGZAG: return 15;
            case GUNNER: return 25;
            default:     return 10;
        }
    }

    public Type getType() {
        return type;
    }

    public void draw(Graphics g) {

        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        );

        int ix = (int) x;
        int iy = (int) y;

        Color limbs;
        Color body;
        Color tips;

        switch (type) {
            case ZIGZAG:
                limbs = new Color(0, 110, 120);
                body = new Color(0, 160, 160);
                tips = Color.CYAN;
                break;
            case GUNNER:
                limbs = new Color(150, 60, 10);
                body = new Color(200, 80, 25);
                tips = Color.ORANGE;
                break;
            default:
                limbs = new Color(120, 0, 180);
                body = new Color(150, 0, 180);
                tips = Color.MAGENTA;
                break;
        }

        // Cannon (armed aliens only)
        if (type == Type.GUNNER) {
            g2.setColor(Color.DARK_GRAY);
            g2.fillRect(ix - 9, iy + 19, 14, 7);
            g2.setColor(Color.ORANGE);
            g2.fillRect(ix - 9, iy + 20, 3, 5);
        }

        // Tentacles
        g2.setColor(limbs);
        g2.drawLine(ix + 10, iy + 27, ix + 5, iy + 35);
        g2.drawLine(ix + 22, iy + 30, ix + 22, iy + 38);
        g2.drawLine(ix + 35, iy + 27, ix + 40, iy + 35);

        // Antennae
        g2.drawLine(ix + 12, iy + 8, ix + 7, iy);
        g2.drawLine(ix + 33, iy + 8, ix + 38, iy);

        // Antennae tips
        g2.setColor(tips);
        g2.fillOval(ix + 4, iy - 3, 6, 6);
        g2.fillOval(ix + 35, iy - 3, 6, 6);

        // Alien body (flashes white when hit)
        g2.setColor(hitFlash > 0 ? Color.WHITE : body);
        g2.fillOval(ix, iy + 5, WIDTH, HEIGHT - 5);

        // Eyes
        g2.setColor(Color.WHITE);
        g2.fillOval(ix + 9, iy + 13, 10, 10);
        g2.fillOval(ix + 26, iy + 13, 10, 10);

        // Pupils
        g2.setColor(Color.BLACK);
        g2.fillOval(ix + 12, iy + 16, 5, 5);
        g2.fillOval(ix + 29, iy + 16, 5, 5);

        g2.dispose();
    }

    public Rectangle getBounds() {
        return new Rectangle((int) x, (int) y, WIDTH, HEIGHT);
    }

    public int getCenterX() {
        return (int) x + WIDTH / 2;
    }

    public int getCenterY() {
        return (int) y + HEIGHT / 2;
    }

    /** Where an armed alien's shot appears. */
    public int getShotX() {
        return (int) x - 10;
    }

    public int getShotY() {
        return (int) y + HEIGHT / 2 - 4;
    }

    public boolean isOffScreen() {
        return x + WIDTH < 0;
    }

}
