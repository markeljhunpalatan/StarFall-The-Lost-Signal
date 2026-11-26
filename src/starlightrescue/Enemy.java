package starlightrescue;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import java.util.ArrayList;
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

    public enum Type { SCOUT, ZIGZAG, GUNNER, DIVER, LOOPER, DASHER, EXPLODER }

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
    private int muzzleFlash = 0;        // a short glow at the front of the alien when it fires
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

            case EXPLODER:
                // Slow and fragile, but it blows up when destroyed (see GamePanel.detonate)
                base = 1.6 + RANDOM.nextDouble() * 0.6;
                hp = 8;
                break;

            default:
                base = RANDOM.nextInt(3) + 2;
                break;
        }

        speed = base * Math.max(0.5, speedFactor);
        amplitude = amp;
        loopRadius = loopR;
        health = hp;

        // The small aliens start with a short wait, so they don't all fire at once
        if (type == Type.SCOUT || type == Type.ZIGZAG) {
            shootCooldown = 40 + RANDOM.nextInt(50);
        }

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

        if (muzzleFlash > 0) {
            muzzleFlash--;
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

            double ticks = Math.max(0, gap) / (bullet.getWeapon().speed + speed);

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

    /**
     * The shots this alien fires this tick (usually none), and when.
     *
     *   GUNNER  - one shot straight left
     *   SCOUT   - one shot aimed at where the player is
     *   ZIGZAG  - a quick pair of shots straight left
     *
     * Only aliens that are on screen (and past the entrance) shoot. The small aliens
     * shoot from the moment they come in, and across nearly the whole screen.
     */
    public List<BossBullet> fire(int playerCenterX, int playerCenterY) {

        List<BossBullet> shots = new ArrayList<>();

        // The small aliens can shoot almost to the left edge; the others stop further right
        boolean small = (type == Type.SCOUT || type == Type.ZIGZAG);
        int leftLimit = small ? 40 : 200;

        if (shootCooldown > 0 || x >= GamePanel.SCREEN_WIDTH - 60 || x <= leftLimit) {
            return shots;
        }

        double sx = getShotX();
        double sy = getShotY();

        switch (type) {

            case GUNNER:
                shots.add(new BossBullet(sx, sy, -5.0, 0.0, 0.6));
                shootCooldown = 110 + RANDOM.nextInt(50);
                break;

            case SCOUT:
                // Aim at the player: the shot slides across to where they are
                double frames = Math.max(20.0, (sx - playerCenterX) / 5.5);
                double vy = Math.max(-3.0, Math.min(3.0, (playerCenterY - sy) / frames));
                shots.add(new BossBullet(sx, sy, -5.5, vy, 0.6));
                shootCooldown = 80 + RANDOM.nextInt(30);
                break;

            case ZIGZAG:
                shots.add(new BossBullet(sx, sy - 5, -5.0, 0.0, 0.6));
                shots.add(new BossBullet(sx, sy + 5, -5.0, 0.0, 0.6));
                shootCooldown = 110 + RANDOM.nextInt(30);
                break;

            default:
                return shots;
        }

        muzzleFlash = 6;
        return shots;
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
            case EXPLODER: base = 20; break;
            default:     base = 10; break;
        }
        return isEvasive() ? base + 10 : base;
    }

    public Type getType() {
        return type;
    }

    /** The alien's rank: 1 drone, 2 fighter, 3 elite (the art is drawn to match, see AlienArt). */
    public int getRank() {
        switch (type) {
            case SCOUT:
            case ZIGZAG:
                return 1;
            case DASHER:
            case EXPLODER:
                return 3;
            default:
                return 2;
        }
    }

    // ===============================================================
    // DRAWING
    // ===============================================================

    /**
     * Draws the alien's pixel art (see AlienArt). The picture depends on the alien's rank
     * and type, and it moves: it bobs, blinks and its limbs swing.
     */
    public void draw(Graphics g) {

        Graphics2D g2 = (Graphics2D) g.create();

        // Afterimage when a smart alien dodges
        if (dodgeFlash > 0) {
            g2.setColor(new Color(0, 230, 255, dodgeFlash * 14));
            g2.fillOval(x2(), iy2() + 5 - lastDodge * 9, WIDTH, HEIGHT - 5);
        }

        // A charging elite shakes a little before it dashes
        boolean charging = (type == Type.DASHER && dashPhase == 1);
        double shake = charging ? ((tick % 2 == 0) ? 2 : -2) : 0;

        AlienArt.draw(g2, type, getCenterX() + shake, getCenterY(), tick, hitFlash > 0, charging);

        // The muzzle flash: a short glow at the front of the alien as it fires
        if (muzzleFlash > 0) {
            int size = 4 + muzzleFlash;
            g2.setColor(new Color(255, 170, 60, 60 + muzzleFlash * 20));
            g2.fillOval(x2() - size / 2, iy2() + HEIGHT / 2 - size / 2, size, size);
        }

        g2.dispose();
    }

    private int x2() {
        return (int) x;
    }

    private int iy2() {
        return (int) y;
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
