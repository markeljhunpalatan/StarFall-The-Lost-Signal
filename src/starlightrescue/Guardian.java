package starlightrescue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;

/**
 * A shield drone that protects a boss (HARDCORE mode, from the second boss on).
 *
 * Guardians hover in front of the boss and PREDICT your line of fire: they look
 * at where your ship is heading and move there, so they can block your shots
 * before they reach the boss. They take a few hits, and the boss sends a new
 * one a few seconds after one is destroyed.
 */
public class Guardian {

    static final int SIZE = 34;

    // Where each guardian sits relative to your line of fire, so several form a wall
    private static final double[] OFFSETS = {0, -50, 50, -100};

    private double x;
    private double y;
    private final int slot;
    private final int maxHealth;
    private int health;
    private final double maxSpeed;

    private int hitFlash = 0;
    private int tick = 0;

    public Guardian(int slot, double startX, double startY, int health, double maxSpeed) {
        this.slot = slot;
        this.x = startX;
        this.y = startY;
        this.health = health;
        this.maxHealth = health;
        this.maxSpeed = maxSpeed;
    }

    /**
     * @param bossHomeX  where the boss normally sits
     * @param predictedY where the player will probably be shooting from soon
     */
    public void update(double bossHomeX, double predictedY) {

        tick++;

        if (hitFlash > 0) {
            hitFlash--;
        }

        double targetX = bossHomeX - 64 - (slot % 2) * 50;
        double targetY = predictedY + OFFSETS[slot % OFFSETS.length] - SIZE / 2.0;
        targetY = Math.max(GamePanel.PLAY_TOP + 4, Math.min(GamePanel.PLAY_BOTTOM - SIZE - 4, targetY));

        x += Math.max(-maxSpeed * 1.5, Math.min(maxSpeed * 1.5, (targetX - x) * 0.12));
        y += Math.max(-maxSpeed, Math.min(maxSpeed, (targetY - y) * 0.15));
    }

    public void takeDamage(int damage) {
        health -= damage;
        hitFlash = 5;
    }

    public boolean isDead() {
        return health <= 0;
    }

    public int getSlot() {
        return slot;
    }

    public int getCenterX() {
        return (int) x + SIZE / 2;
    }

    public int getCenterY() {
        return (int) y + SIZE / 2;
    }

    public Rectangle getBounds() {
        return new Rectangle((int) x - 4, (int) y - 4, SIZE + 8, SIZE + 8);
    }

    public void draw(Graphics2D g0, int bossCenterX, int bossCenterY) {

        Graphics2D g2 = (Graphics2D) g0.create();

        int cx = getCenterX();
        int cy = getCenterY();

        // Energy link to the boss
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                                     0, new float[]{6, 6}, 12f - (tick * 1.5f) % 12f));
        g2.setColor(new Color(0, 220, 255, 70));
        g2.drawLine(cx, cy, bossCenterX, bossCenterY);

        // Shield arc facing the player
        int shieldAlpha = (hitFlash > 0) ? 230 : 120 + (int) (Math.sin(tick * 0.15) * 40);
        g2.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(90, 230, 255, shieldAlpha));
        g2.drawArc(cx - 26, cy - 26, 52, 52, 115, 130);
        g2.setColor(new Color(90, 230, 255, shieldAlpha / 3));
        g2.fillArc(cx - 26, cy - 26, 52, 52, 115, 130);

        // Hexagonal body
        int[] hx = new int[6];
        int[] hy = new int[6];

        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 6 + i * Math.PI / 3 + tick * 0.01;
            hx[i] = (int) (cx + Math.cos(a) * 16);
            hy[i] = (int) (cy + Math.sin(a) * 16);
        }

        g2.setPaint(new GradientPaint(cx, cy - 16, new Color(150, 160, 190),
                                      cx, cy + 16, new Color(50, 56, 80)));
        g2.fillPolygon(hx, hy, 6);
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(new Color(20, 24, 40));
        g2.drawPolygon(hx, hy, 6);

        // Glowing core
        int pulse = (int) (Math.sin(tick * 0.2) * 2);
        g2.setColor(new Color(0, 220, 255, 90));
        g2.fillOval(cx - 9 - pulse, cy - 9 - pulse, 18 + pulse * 2, 18 + pulse * 2);
        g2.setColor(hitFlash > 0 ? Color.WHITE : new Color(120, 240, 255));
        g2.fillOval(cx - 5, cy - 5, 10, 10);

        // Health ring
        double fraction = Math.max(0, health) / (double) maxHealth;
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(255, 255, 255, 50));
        g2.drawOval(cx - 20, cy - 20, 40, 40);
        g2.setColor(fraction > 0.5 ? new Color(90, 230, 255) : new Color(255, 120, 110));
        g2.drawArc(cx - 20, cy - 20, 40, 40, 90, (int) (360 * fraction));

        g2.dispose();
    }
}
