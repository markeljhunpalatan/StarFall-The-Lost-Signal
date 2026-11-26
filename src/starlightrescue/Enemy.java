package starlightrescue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;

import java.util.List;
import java.util.Random;

/**
 * An alien. Each TYPE moves in its own way:
 *
 * SCOUT  - flies straight at you
 * ZIGZAG - weaves up and down in a wave
 * GUNNER - slow, tough and shoots back
 * DIVER  - steers toward your height until it gets close, then charges straight
 * LOOPER - flies in loop-the-loops
 * DASHER - creeps in, stops and glows, then rockets straight at you (tough)
 *
 * "speedFactor" makes aliens faster the longer you survive.
 *
 * EVASIVE aliens (HARDCORE, after the first boss) watch your bullets, predict
 * which ones would hit them, and slide out of the way. They have cyan eyes.
 */
public class Enemy {

    public enum Type { SCOUT, ZIGZAG, GUNNER, DIVER, LOOPER, DASHER }

    private static final Random RANDOM = new Random();

    private double x;
    private double y;

    private double baseY;             // the line the alien weaves/loops around
    private final double amplitude;   // zig-zag height (0 = straight)
    private final double phase;

    private final double loopRadius;  // looper circle size
    private double loopX;             // where the looper's circle is centred
    private double loopAngle;

    private final Type type;

    private static final int WIDTH = 45;
    private static final int HEIGHT = 35;

    private final double speed;
    private int health;               // in damage points (a basic shot does 10)

    private int tick = 0;
    private int shootCooldown = 0;
    private int hitFlash = 0;

    // Dasher state: 0 = creeping in, 1 = charging up, 2 = dashing
    private int dashPhase = 0;
    private int chargeTimer = 0;
    private double dashVy = 0;

    // Evasion: 0 = never dodges, 1 = expert dodger
    private double evasion = 0;
    private int dodgeFlash = 0;
    private int lastDodge = 0;

    public Enemy() {
        this(Type.SCOUT, 1.0);
    }

    public Enemy(Type type) {
        this(type, 1.0);
    }

    public Enemy(Type type, double speedFactor) {

        this.type = type;

        x = GamePanel.SCREEN_WIDTH;
        phase = RANDOM.nextDouble() * Math.PI * 2;

        double base;
        double amp = 0;
        double loopR = 0;
        int hp = 5;

        switch (type) {

            case ZIGZAG:
                base = RANDOM.nextInt(2) + 2;
                amp = 45 + RANDOM.nextInt(30);
                break;

            case GUNNER:
                base = 1.0 + RANDOM.nextDouble();
                hp = 12;
                shootCooldown = 50 + RANDOM.nextInt(60);
                break;

            case DIVER:
                base = 2.2 + RANDOM.nextDouble();
                break;

            case LOOPER:
                base = 1.8 + RANDOM.nextDouble() * 0.8;
                loopR = 34;
                break;

            case DASHER:
                base = 2.0 + RANDOM.nextDouble();
                hp = 12;
                break;

            default:
                base = RANDOM.nextInt(3) + 2;
                break;
        }

        speed = base * Math.max(0.5, speedFactor);
        amplitude = amp;
        loopRadius = loopR;
        health = hp;

        // Pick a lane that keeps the whole weave on screen
        int margin = (int) Math.max(amp, loopR);
        int minY = GamePanel.PLAY_TOP + margin;
        int maxY = GamePanel.PLAY_BOTTOM - HEIGHT - margin - 4;
        baseY = minY + RANDOM.nextInt(Math.max(1, maxY - minY));
        y = baseY;

        loopX = x + loopR;
        loopAngle = phase;
    }

    /** Puts a new alien at a given spot (used when a boss launches aliens). */
    public void placeAt(double px, double py) {

        double margin = Math.max(amplitude, loopRadius);
        py = Math.max(GamePanel.PLAY_TOP + margin,
                      Math.min(GamePanel.PLAY_BOTTOM - HEIGHT - margin, py));

        x = px;
        y = py;
        baseY = py;
        loopX = px + loopRadius;
    }

    /** Makes the alien dodge your bullets. skill: 0.0 (never) to 1.0 (expert). */
    public void setEvasion(double skill) {
        evasion = Math.max(0, Math.min(1, skill));
    }

    public boolean isEvasive() {
        return evasion > 0;
    }

    /** Moves the alien one step. Divers and dashers aim at the player's height. */
    public void update(int playerCenterY) {

        tick++;

        if (shootCooldown > 0) {
            shootCooldown--;
        }

        if (hitFlash > 0) {
            hitFlash--;
        }

        if (dodgeFlash > 0) {
            dodgeFlash--;
        }

        switch (type) {

            case ZIGZAG:
                x -= speed;
                y = baseY + amplitude * Math.sin(tick * 0.06 + phase);
                break;

            case DIVER:
                x -= speed;

                // Steer toward the player until close, then it's a straight charge
                if (x > 330) {
                    double dy = playerCenterY - (y + HEIGHT / 2.0);
                    double max = Math.min(3.4, speed * 0.9);
                    y += Math.max(-max, Math.min(max, dy * 0.05));
                }
                break;

            case LOOPER:
                loopX -= speed * 0.85;
                loopAngle += 0.075;
                x = loopX + loopRadius * Math.cos(loopAngle);
                y = baseY + loopRadius * Math.sin(loopAngle);
                break;

            case DASHER:
                updateDasher(playerCenterY);
                break;

            default:
                x -= speed;
                break;
        }

        if (type == Type.DIVER || type == Type.DASHER || type == Type.SCOUT || type == Type.GUNNER) {
            y = Math.max(GamePanel.PLAY_TOP, Math.min(GamePanel.PLAY_BOTTOM - HEIGHT, y));
        }
    }

    private void updateDasher(int playerCenterY) {

        if (dashPhase == 0) {

            x -= speed * 0.75;

            if (x < 560) {
                dashPhase = 1;
                chargeTimer = 38;
            }

        } else if (dashPhase == 1) {

            chargeTimer--;

            if (chargeTimer <= 0) {

                // Lock onto where the player is right now, then go
                dashPhase = 2;
                double frames = Math.max(20.0, (x - 90) / (speed * 3.6));
                double dy = (playerCenterY - (y + HEIGHT / 2.0)) / frames;
                dashVy = Math.max(-3.2, Math.min(3.2, dy));
            }

        } else {

            x -= speed * 3.6;
            y += dashVy;
        }
    }

    /**
     * Smart aliens look at every bullet flying toward them, work out where it
     * will be when it reaches them, and slide away from the ones that would hit.
     */
    public void evade(List<Bullet> bullets) {

        if (evasion <= 0 || x > GamePanel.SCREEN_WIDTH - 15 || (type == Type.DASHER && dashPhase == 2)) {
            return;
        }

        double centerY = y + HEIGHT / 2.0;
        double lookAhead = 14 + 26 * evasion;           // how many ticks ahead it can "see"
        double push = 0;
        boolean danger = false;

        for (Bullet bullet : bullets) {

            Rectangle r = bullet.getBounds();
            double gap = x - (r.x + r.width);

            if (gap < -WIDTH) {
                continue;                                // already flown past
            }

            double ticks = Math.max(0, gap) / (Bullet.SPEED + speed);

            if (ticks > lookAhead) {
                continue;                                // too far away to worry about yet
            }

            double bulletY = r.y + r.height / 2.0 + bullet.getDy() * ticks;

            if (Math.abs(bulletY - centerY) < HEIGHT / 2.0 + 7) {
                danger = true;
                push += (bulletY <= centerY ? 1 : -1) / (1.0 + ticks);
            }
        }

        if (!danger) {
            return;
        }

        int direction = (push >= 0) ? 1 : -1;

        // Don't dodge into the edge of the screen
        if (direction < 0 && centerY < GamePanel.PLAY_TOP + 50) {
            direction = 1;
        } else if (direction > 0 && centerY > GamePanel.PLAY_BOTTOM - 50) {
            direction = -1;
        }

        nudge(direction * (1.3 + 1.9 * evasion));
        lastDodge = direction;
        dodgeFlash = 6;
    }

    private void nudge(double dy) {

        if (type == Type.ZIGZAG || type == Type.LOOPER) {
            double margin = Math.max(amplitude, loopRadius);
            baseY = Math.max(GamePanel.PLAY_TOP + margin,
                             Math.min(GamePanel.PLAY_BOTTOM - HEIGHT - margin, baseY + dy));
            y += dy;
        } else {
            y = Math.max(GamePanel.PLAY_TOP, Math.min(GamePanel.PLAY_BOTTOM - HEIGHT, y + dy));
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
        int base;
        switch (type) {
            case ZIGZAG: base = 15; break;
            case GUNNER: base = 25; break;
            case DIVER:  base = 20; break;
            case LOOPER: base = 20; break;
            case DASHER: base = 30; break;
            default:     base = 10; break;
        }
        return isEvasive() ? base + 10 : base;
    }

    public Type getType() {
        return type;
    }

    // ===============================================================
    // DRAWING
    // ===============================================================

    public void draw(Graphics g) {

        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        );

        int ix = (int) x;
        int iy = (int) y;

        // A charging dasher shakes and glows
        boolean charging = (type == Type.DASHER && dashPhase == 1);

        if (charging) {
            ix += (tick % 2 == 0) ? 2 : -2;
        }

        Color limbs;
        Color body;
        Color tips;

        switch (type) {
            case ZIGZAG:
                limbs = new Color(0, 115, 125);
                body = new Color(0, 175, 175);
                tips = Color.CYAN;
                break;
            case GUNNER:
                limbs = new Color(150, 60, 20);
                body = new Color(222, 100, 40);
                tips = Color.ORANGE;
                break;
            case DIVER:
                limbs = new Color(150, 30, 60);
                body = new Color(238, 60, 102);
                tips = new Color(255, 170, 190);
                break;
            case LOOPER:
                limbs = new Color(80, 130, 30);
                body = new Color(142, 212, 62);
                tips = new Color(232, 255, 124);
                break;
            case DASHER:
                limbs = new Color(160, 70, 20);
                body = new Color(242, 192, 52);
                tips = new Color(255, 90, 60);
                break;
            default:
                limbs = new Color(110, 50, 170);
                body = new Color(168, 76, 232);
                tips = Color.MAGENTA;
                break;
        }

        // Afterimage when a smart alien dodges
        if (dodgeFlash > 0) {
            g2.setColor(new Color(0, 230, 255, dodgeFlash * 14));
            g2.fillOval(ix, iy + 5 - lastDodge * 9, WIDTH, HEIGHT - 5);
        }

        // Soft glow around the alien
        int glow = charging ? 90 + (int) (Math.sin(tick * 0.8) * 50) : 34;
        g2.setColor(new Color(tips.getRed(), tips.getGreen(), tips.getBlue(), Math.max(0, glow)));
        g2.fillOval(ix - 8, iy - 2, WIDTH + 16, HEIGHT + 8);

        // Cannon (armed aliens only)
        if (type == Type.GUNNER) {
            g2.setColor(Color.DARK_GRAY);
            g2.fillRect(ix - 9, iy + 19, 14, 7);
            g2.setColor(Color.ORANGE);
            g2.fillRect(ix - 9, iy + 20, 3, 5);
        }

        // Tentacles
        g2.setStroke(new BasicStroke(1.6f));
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

        // Dasher spikes
        if (type == Type.DASHER) {
            for (int i = 0; i < 3; i++) {
                int sx = ix + 10 + i * 12;
                g2.fillPolygon(new int[]{sx, sx + 8, sx + 4}, new int[]{iy + 8, iy + 8, iy - 4}, 3);
            }
        }

        // Alien body (flashes white when hit)
        g2.setColor(hitFlash > 0 ? Color.WHITE : body);
        g2.fillOval(ix, iy + 5, WIDTH, HEIGHT - 5);

        // Looper halo
        if (type == Type.LOOPER) {
            g2.setColor(new Color(232, 255, 124, 170));
            g2.setStroke(new BasicStroke(1.6f));
            g2.drawOval(ix - 3, iy + 2, WIDTH + 6, HEIGHT - 1);
        }

        // Eyes
        g2.setColor(Color.WHITE);
        g2.fillOval(ix + 9, iy + 13, 10, 10);
        g2.fillOval(ix + 26, iy + 13, 10, 10);

        // Pupils: smart (evasive) aliens have glowing cyan eyes
        if (isEvasive()) {
            g2.setColor(new Color(0, 230, 255, 120));
            g2.fillOval(ix + 7, iy + 11, 14, 14);
            g2.fillOval(ix + 24, iy + 11, 14, 14);
            g2.setColor(new Color(0, 200, 255));
        } else {
            g2.setColor(Color.BLACK);
        }

        g2.fillOval(ix + 12, iy + 16, 5, 5);
        g2.fillOval(ix + 29, iy + 16, 5, 5);

        // Diver: an angry visor over the eyes
        if (type == Type.DIVER) {
            g2.setColor(new Color(60, 0, 20, 220));
            g2.fillPolygon(new Polygon(
                new int[]{ix + 6, ix + 39, ix + 39, ix + 6},
                new int[]{iy + 9, iy + 13, iy + 15, iy + 15}, 4));
        }

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
        return x + WIDTH < 0 || y > GamePanel.SCREEN_HEIGHT + 40 || y < -60;
    }

}
