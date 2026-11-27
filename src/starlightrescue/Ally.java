package starlightrescue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/**
 * A hired wingman. It flies just in front of the player, shoots at aliens and
 * can be shot down. It looks like the "Void - Main Ship" (see AllyArt): its hull
 * gets more battered as it is damaged, and its two wing cannons take turns firing.
 *
 * Allies are disposable:
 *   - they CANNOT be healed. Nothing in the game adds health to an ally: repair
 *     kits, repair pickups, boss supplies and nano-repair only touch the ship.
 *   - they never add lives to the player.
 *   - once an ally is destroyed it is gone. Hire a new one (the quick shop, key 5).
 *   - only one ally can be on your wing at a time (GamePanel holds at most one).
 */
public class Ally {

    static final int MAX_HEALTH = 100;
    static final int WIDTH = (int) Math.round(26 * AllyArt.SCALE);     // the hull of its ship: 26 x 30 sheet pixels
    static final int HEIGHT = (int) Math.round(30 * AllyArt.SCALE);

    static final int SHOT_INTERVAL = 16;   // ticks between shots
    static final int SHOT_DAMAGE = 8;      // damage of each shot
    static final int HIT_COOLDOWN = 20;    // ticks of safety after a hit (stops one meteor hurting it every tick)

    private static final int FOLLOW_GAP = 80;   // how far in front of the player's centre it flies

    private double x;
    private double y;
    private int health = MAX_HEALTH;
    private int shotCooldown = 0;
    private int hitCooldown = 0;
    private int tick = 0;
    private int sinceShot = 99;         // ticks since its cannons last fired (they kick back, then settle)
    private boolean topGun = false;     // which wing cannon fired last
    private boolean racing = false;     // true while it races to keep up (its engine roars)

    public Ally(int playerCenterX, int playerCenterY) {
        x = playerCenterX + FOLLOW_GAP;
        y = playerCenterY - HEIGHT / 2.0;
    }

    /**
     * Moves the ally to stay on the player's wing and counts down its gun.
     *
     * @return true on the ticks when the ally fires a shot
     */
    public boolean update(int playerCenterX, int playerCenterY) {

        tick++;
        sinceShot++;

        if (hitCooldown > 0) {
            hitCooldown--;
        }

        double oldX = x;
        double oldY = y;

        // Glide toward its spot, so it looks like it is flying alongside you
        x += (playerCenterX + FOLLOW_GAP - x) * 0.15;
        y += (playerCenterY - HEIGHT / 2.0 - y) * 0.12;
        y = Math.max(GamePanel.PLAY_TOP, Math.min(GamePanel.PLAY_BOTTOM - HEIGHT, y));

        // The ship can fly to the right edge, so the wingman must stay on screen too
        x = Math.min(GamePanel.SCREEN_WIDTH - WIDTH - 2, x);

        racing = Math.abs(x - oldX) > 1.6 || Math.abs(y - oldY) > 2.5;

        if (shotCooldown > 0) {
            shotCooldown--;
            return false;
        }

        // The two wing cannons take turns
        shotCooldown = SHOT_INTERVAL;
        topGun = !topGun;
        sinceShot = 0;
        return true;
    }

    /**
     * Something hit the ally. Returns false if the hit did not count (the ally
     * is still recovering from the last one).
     */
    public boolean takeHit(int damage) {

        if (hitCooldown > 0 || isDead()) {
            return false;
        }

        health = Math.max(0, health - damage);
        hitCooldown = HIT_COOLDOWN;
        return true;
    }

    public boolean isDead() {
        return health <= 0;
    }

    /** How long the ally has been on the wing, in ticks. */
    public int ticksAlive() {
        return tick;
    }

    public int getHealth() {
        return health;
    }

    /** Where a shot leaves the wing cannon that just fired. */
    public int getGunX() {
        return (int) Math.round(x) + AllyArt.GUN_X;
    }

    public int getGunY() {
        return (int) Math.round(y) + (topGun ? AllyArt.TOP_GUN_Y : AllyArt.BOTTOM_GUN_Y);
    }

    /** True while it races to keep up with you (its engine roars). */
    public boolean isRacing() {
        return racing;
    }

    public int getCenterX() {
        return (int) Math.round(x) + WIDTH / 2;
    }

    public int getCenterY() {
        return (int) Math.round(y) + HEIGHT / 2;
    }

    /**
     * A smaller hitbox than the drawing, like the player's ship: the core of the hull
     * (the wing tips and cannons stick out past it), so it is about as easy to hit as before.
     */
    public Rectangle getBounds() {
        return new Rectangle((int) Math.round(x) + 5, (int) Math.round(y) + 9, WIDTH - 10, HEIGHT - 18);
    }

    public void draw(Graphics2D g0) {

        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int ix = (int) Math.round(x);
        int iy = (int) Math.round(y);
        boolean flashing = hitCooldown > 0;
        double fraction = health / (double) MAX_HEALTH;

        // The ship from the "Void - Main Ship" parts; the plain shapes below are only for missing art
        int cannonFrame = (sinceShot < AllyArt.CANNON_FRAMES * 2) ? sinceShot / 2 : 0;
        if (AllyArt.draw(g, ix, iy, tick, fraction, cannonFrame, racing, hitCooldown, hitCooldown > HIT_COOLDOWN - 4)) {
            drawHealthBar(g, ix, iy, fraction);
            g.dispose();
            return;
        }

        // Engine flame, flickering behind the ship
        int flame = 6 + (tick / 2) % 3 * 3;
        g.setColor(new Color(90, 255, 190, 170));
        g.fillPolygon(new int[]{ix - 1, ix - 1 - flame, ix - 1},
                      new int[]{iy + 8, iy + HEIGHT / 2, iy + 18}, 3);

        // Soft glow
        g.setColor(new Color(72, 236, 152, 40));
        g.fillOval(ix - 6, iy - 4, WIDTH + 12, HEIGHT + 8);

        // Wings
        g.setColor(flashing ? new Color(255, 200, 200) : new Color(40, 150, 105));
        g.fillPolygon(new int[]{ix + 8, ix + 18, ix + 26}, new int[]{iy + 11, iy - 1, iy + 11}, 3);
        g.fillPolygon(new int[]{ix + 8, ix + 18, ix + 26}, new int[]{iy + 15, iy + 27, iy + 15}, 3);

        // Diamond-shaped body, like the player's ship
        Polygon body = new Polygon(
            new int[]{ix, ix + 20, ix + WIDTH, ix + 20},
            new int[]{iy + HEIGHT / 2, iy, iy + HEIGHT / 2, iy + HEIGHT},
            4
        );
        g.setColor(flashing ? Color.WHITE : new Color(96, 240, 170));
        g.fillPolygon(body);

        // Cockpit and gun tip
        g.setColor(new Color(235, 255, 245));
        g.fillOval(ix + 21, iy + 8, 9, 8);
        g.setColor(Color.WHITE);
        g.fillRect(ix + WIDTH - 2, iy + HEIGHT / 2 - 1, 3, 3);

        drawHealthBar(g, ix, iy, fraction);
        g.dispose();
    }

    /** Its health bar, above the ship (it never refills). */
    private void drawHealthBar(Graphics2D g, int ix, int iy, double fraction) {

        Color bar = (fraction > 0.5) ? UI.GREEN : (fraction > 0.25 ? UI.GOLD : UI.RED);

        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(ix, iy - 8, WIDTH, 4);
        g.setColor(bar);
        g.fillRect(ix, iy - 8, (int) Math.round(WIDTH * fraction), 4);
    }
}
