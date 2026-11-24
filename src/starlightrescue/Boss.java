package starlightrescue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.GradientPaint;

import java.util.ArrayList;
import java.util.List;

/**
 * The boss mothership.
 *
 * - It flies in from the right, then bounces up and down.
 * - It has 3 PHASES that depend on its health. Every new phase it moves faster
 *   and uses a nastier attack (single shot -> 3-way spread -> 5-way + aimed).
 * - "tier" changes its colours and speeds up its attacks (used by HARDCORE mode,
 *   where each new boss is a bit tougher than the last).
 */
public class Boss {

    private double x;
    private final int targetX;
    private double y;

    private final int width = 130;
    private final int height = 170;

    private final double baseSpeed;
    private int direction = 1;          // 1 = moving down, -1 = moving up

    private int health;
    private final int maxHealth;

    private final String name;
    private final int tier;

    private boolean entering = true;
    private int attackCooldown = 80;
    private int patternCounter = 0;
    private int hitFlash = 0;
    private int tick = 0;

    // Colour sets for tiers 0, 1, 2 (then they repeat)
    private static final Color[][] PALETTES = {
        // body light, body dark, upper, wings, core, lights
        {new Color(100, 35, 150), new Color(35, 10, 70), new Color(130, 55, 190),
         new Color(75, 25, 120), new Color(255, 30, 30), Color.CYAN},
        {new Color(20, 115, 135), new Color(5, 35, 55), new Color(40, 155, 175),
         new Color(15, 80, 100), new Color(255, 190, 40), new Color(160, 255, 120)},
        {new Color(160, 40, 45), new Color(60, 10, 20), new Color(200, 70, 65),
         new Color(115, 28, 32), new Color(90, 255, 210), Color.YELLOW}
    };

    public Boss(int x, int y) {
        this(x, y, 100, "ALIEN WARSHIP", 0);
    }

    public Boss(int targetX, int y, int maxHealth, String name, int tier) {
        this.targetX = targetX;
        this.x = GamePanel.SCREEN_WIDTH + 30;   // starts off screen
        this.y = y;
        this.maxHealth = maxHealth;
        this.health = maxHealth;
        this.name = name;
        this.tier = tier;
        this.baseSpeed = 2.0 + 0.25 * Math.min(tier, 6);
    }

    public void update(int panelHeight) {

        tick++;

        if (hitFlash > 0) {
            hitFlash--;
        }

        if (health <= 0) {
            return;   // defeated: the game panel plays the explosions
        }

        // Slide in from the right
        if (entering) {
            x -= 2.5;
            if (x <= targetX) {
                x = targetX;
                entering = false;
            }
            return;
        }

        // Moves faster in later phases
        double speed = baseSpeed * (1.0 + 0.35 * (getPhase() - 1));
        y += direction * speed;

        int top = GamePanel.PLAY_TOP + 6;
        int bottom = panelHeight - 10;

        if (y <= top) {
            y = top;
            direction = 1;
        } else if (y + height >= bottom) {
            y = bottom - height;
            direction = -1;
        }
    }

    /** 1 = healthy, 2 = hurt, 3 = nearly dead and angry. */
    public int getPhase() {

        double ratio = (double) health / maxHealth;

        if (ratio > 0.66) {
            return 1;
        } else if (ratio > 0.33) {
            return 2;
        }
        return 3;
    }

    /**
     * Called every frame. Returns the shots to fire this frame
     * (usually an empty list).
     */
    public List<BossBullet> attack(int playerX, int playerCenterY) {

        List<BossBullet> shots = new ArrayList<>();

        if (entering || health <= 0) {
            return shots;
        }

        if (attackCooldown > 0) {
            attackCooldown--;
            return shots;
        }

        int phase = getPhase();
        int sx = (int) x + 5;
        int sy = (int) y + 79;
        int nextCooldown;

        patternCounter++;

        if (phase == 1) {

            // One straight shot
            shots.add(new BossBullet(sx, sy, -6.5, 0, 1.0));
            nextCooldown = 75;

        } else if (phase == 2) {

            // Three-way spread
            shots.add(new BossBullet(sx, sy, -6.0, -2.2, 1.0));
            shots.add(new BossBullet(sx, sy, -6.5, 0.0, 1.0));
            shots.add(new BossBullet(sx, sy, -6.0, 2.2, 1.0));
            nextCooldown = 70;

        } else if (patternCounter % 2 == 0) {

            // Five-way spread
            for (int i = -2; i <= 2; i++) {
                shots.add(new BossBullet(sx, sy, -6.0, i * 1.6, 1.0));
            }
            nextCooldown = 65;

        } else {

            // A fast shot aimed right at the player
            double frames = Math.max(20.0, (sx - playerX) / 8.0);
            double vy = (playerCenterY - (sy + 6)) / frames;
            vy = Math.max(-3.5, Math.min(3.5, vy));

            shots.add(new BossBullet(sx, sy, -8.0, vy, 1.0));
            nextCooldown = 52;
        }

        // Higher-tier bosses attack faster
        attackCooldown = Math.max(26, nextCooldown - tier * 4);

        return shots;
    }

    public void draw(Graphics2D g2) {

        Color[] pal = PALETTES[tier % PALETTES.length];

        int bx = (int) x;
        int currentY = (int) y;

        // Engine glow on the back of the ship
        g2.setColor(new Color(255, 140, 40, 90));
        g2.fillOval(bx + width - 12, currentY + 70, 26, 30);
        g2.setColor(new Color(255, 220, 120, 150));
        g2.fillOval(bx + width - 6, currentY + 78, 14, 14);

        // Main mothership body.
        g2.setPaint(new GradientPaint(
                bx, currentY,
                pal[0],
                bx + width, currentY + height,
                pal[1]
        ));

        g2.fillOval(bx, currentY + 20, width, 120);

        // Upper structure.
        g2.setColor(pal[2]);
        g2.fillOval(bx + 25, currentY, 80, 75);

        // Side wings.
        g2.setColor(pal[3]);

        int[] leftX = {bx + 15, bx - 15, bx + 20};
        int[] leftY = {currentY + 65, currentY + 95, currentY + 125};
        g2.fillPolygon(leftX, leftY, 3);

        int[] rightX = {
                bx + width - 15,
                bx + width + 15,
                bx + width - 20
        };

        int[] rightY = {
                currentY + 65,
                currentY + 95,
                currentY + 125
        };

        g2.fillPolygon(rightX, rightY, 3);

        // Glowing core: pulses faster the angrier the boss gets
        int phase = getPhase();
        int pulse = (int) (Math.sin(tick * (0.08 + 0.07 * phase)) * 5);

        g2.setColor(new Color(pal[4].getRed(), pal[4].getGreen(), pal[4].getBlue(), 70));
        g2.fillOval(bx + 35 - pulse, currentY + 48 - pulse, 60 + pulse * 2, 60 + pulse * 2);

        g2.setColor(pal[4]);
        g2.fillOval(bx + 43, currentY + 56, 44, 44);

        g2.setColor(new Color(255, 230, 230));
        g2.fillOval(bx + 55, currentY + 68, 20, 20);

        // Alien energy lights.
        g2.setColor(pal[5]);
        g2.fillOval(bx + 15, currentY + 85, 10, 10);
        g2.fillOval(bx + 105, currentY + 85, 10, 10);

        // Lower structure.
        g2.setColor(pal[1].darker());
        g2.fillRoundRect(
                bx + 30,
                currentY + 120,
                70,
                30,
                10,
                10
        );

        // White flash when a bullet hits
        if (hitFlash > 0) {
            g2.setColor(new Color(255, 255, 255, 110));
            g2.fillOval(bx, currentY + 20, width, 120);
            g2.fillOval(bx + 25, currentY, 80, 75);
        }
    }

    public void takeDamage(int damage) {

        if (entering || health <= 0) {
            return;   // can't be hurt while arriving or after dying
        }

        health -= damage;
        hitFlash = 3;

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

    public String getName() {
        return name;
    }

    public boolean isEntering() {
        return entering;
    }

    public boolean isDefeated() {
        return health <= 0;
    }

    /** Puts the boss at a fixed spot (used to show it on story screens). */
    void placeAt(int px, int py) {
        this.x = px;
        this.y = py;
        this.entering = false;
    }

    public Rectangle getBounds() {
        return new Rectangle((int) x + 5, (int) y + 10, width - 10, height - 20);
    }

    public int getX() {
        return (int) x;
    }

    public int getY() {
        return (int) y;
    }
}
