package starlightrescue;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/**
 * The player's spaceship.
 *
 * It moves with W/S (up/down) and A/D (a little left/right). SHIFT boosts
 * once the ENGINES MK II upgrade is bought.
 *
 * Its appearance changes with every upgrade:
 *   - CANNONS : wing guns, then a nose cannon, then glowing plasma guns
 *               (MK II: white-hot muzzles)
 *   - AMMO    : extra ammo pods on the hull (MK II: glowing feed belt)
 *   - ENGINES : more engines, longer and hotter flames (MK II: boost thrusters)
 *   - HULL    : armour plating and gold trim (MK II: green nano-repair sparkles)
 *   - COLOUR  : the whole ship changes colour as it reaches a new ship class
 *
 * Damaged ship parts show too: smoking engines and sparking guns.
 */
public class Player {

    // How far left / right the ship may fly. To make the ship move ONLY up and
    // down (like the original design), set MAX_X equal to START_X.
    static final int START_X = 50;
    static final int MIN_X = 20;
    static final int MAX_X = 300;

    static final int BOOST_TICKS = 12;
    static final int BOOST_SPEED = 13;

    private int x;
    private int y;
    private int previousY;

    private final int WIDTH = 60;
    private final int HEIGHT = 40;

    private int speed = 6;

    private boolean movingUp;
    private boolean movingDown;
    private boolean movingLeft;
    private boolean movingRight;

    private boolean blinking;   // flickers while the ship is briefly invulnerable
    private boolean shielded;   // draws the shield bubble
    private int tick = 0;       // animation counter

    private int boostTimer = 0;
    private int boostDirection = 0;

    // Upgrade levels that decide how the ship looks
    private int cannons = 1;
    private int ammo = 1;
    private int engines = 1;
    private int hull = 0;
    private int classIndex = 0;
    private int caliber = 0;
    private int loader = 0;
    private int boostLevel = 0;
    private int nano = 0;

    // Part damage: 0 = OK, 1 = damaged, 2 = critical, 3 = broken
    private int cannonDamage = 0;
    private int feedDamage = 0;
    private int engineDamage = 0;

    // Colour schemes, one per ship class: {body, wing, cockpit}
    private static final Color[][] PALETTES = {
        {new Color(30, 144, 255),  new Color(0, 90, 180),    Color.CYAN},                   // Scout
        {new Color(0, 200, 220),   new Color(0, 130, 160),   new Color(170, 255, 255)},     // Interceptor
        {new Color(60, 210, 110),  new Color(30, 140, 70),   new Color(190, 255, 210)},     // Vanguard
        {new Color(255, 190, 40),  new Color(190, 120, 20),  new Color(255, 250, 190)},     // Starhawk
        {new Color(235, 240, 250), new Color(210, 60, 75),   new Color(255, 120, 140)},     // Phoenix
        {new Color(130, 90, 255),  new Color(70, 40, 170),   new Color(120, 255, 240)},     // Nova
        {new Color(250, 246, 235), new Color(220, 170, 60),  new Color(150, 220, 255)}      // Celestial
    };

    public Player() {

        x = START_X;
        y = 280;
        previousY = y;

    }

    public void update() {

        tick++;
        previousY = y;

        if (movingUp) {
            y -= speed;
        }

        if (movingDown) {
            y += speed;
        }

        if (movingLeft) {
            x -= speed;
        }

        if (movingRight) {
            x += speed;
        }

        // Boost: a short, very fast dash
        if (boostTimer > 0) {
            boostTimer--;
            y += boostDirection * BOOST_SPEED;
        }

        // Stay inside the play field (between the HUD bars)
        y = Math.max(GamePanel.PLAY_TOP,
                     Math.min(GamePanel.PLAY_BOTTOM - HEIGHT, y));

        x = Math.max(MIN_X, Math.min(MAX_X, x));

    }

    /** Only advances the animation (used on the hangar screen). */
    public void animate() {

        tick++;

    }

    /** Starts a boost dash in the direction the ship is moving (up if standing still). */
    public void boost() {

        if (movingDown && !movingUp) {
            boostDirection = 1;
        } else if (movingUp && !movingDown) {
            boostDirection = -1;
        } else {
            boostDirection = (y > (GamePanel.PLAY_TOP + GamePanel.PLAY_BOTTOM) / 2) ? -1 : 1;
        }

        boostTimer = BOOST_TICKS;

    }

    public boolean isBoosting() {

        return boostTimer > 0;

    }

    public boolean isMoving() {

        return movingUp || movingDown || movingLeft || movingRight;

    }

    /** How far the ship moved up or down in the last tick. */
    public int getVelocityY() {

        return y - previousY;

    }

    /** Copies the ship's upgrade levels so the drawing matches them. */
    public void setLoadout(Upgrades upgrades) {

        cannons = upgrades.getWeaponLevel();
        ammo = upgrades.getAmmoLevel();
        engines = upgrades.getEngineLevel();
        hull = upgrades.getHullLevel();
        classIndex = upgrades.getClassIndex();
        caliber = upgrades.getCaliberLevel();
        loader = upgrades.getLoaderLevel();
        boostLevel = upgrades.getBoostLevel();
        nano = upgrades.getNanoLevel();
        speed = upgrades.getSpeed();

    }

    public void setSpeed(int speed) {

        this.speed = Math.max(2, speed);

    }

    /** 0 = OK, 1 = damaged, 2 = critical, 3 = broken, for each part. */
    public void setPartDamage(int cannons, int feed, int engines) {

        cannonDamage = cannons;
        feedDamage = feed;
        engineDamage = engines;

    }

    /** Puts the ship at a fixed spot (used by the hangar preview). */
    public void placeAt(int px, int py) {

        x = px;
        y = py;

    }

    // ===============================================================
    // DRAWING
    // ===============================================================

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

        Color[] palette = PALETTES[Math.min(classIndex, PALETTES.length - 1)];

        Polygon body = new Polygon(
            new int[]{x, x + 35, x + 60, x + 35},
            new int[]{y + 20, y + 5, y + 20, y + 35},
            4
        );

        // Afterimages while boosting
        if (boostTimer > 0) {
            for (int i = 1; i <= 3; i++) {
                g2.setColor(new Color(palette[0].getRed(), palette[0].getGreen(), palette[0].getBlue(), 70 - i * 18));
                Polygon ghost = new Polygon(body.xpoints, body.ypoints, 4);
                ghost.translate(0, -boostDirection * i * 9);
                g2.fillPolygon(ghost);
            }
        }

        // Soft glow under the ship
        g2.setColor(new Color(palette[0].getRed(), palette[0].getGreen(), palette[0].getBlue(), 36));
        g2.fillOval(x - 8, y + 2, WIDTH + 18, HEIGHT - 4);

        drawEngines(g2);

        // Main spaceship body
        g2.setColor(palette[0]);
        g2.fillPolygon(body);

        // Upper and lower wings
        g2.setColor(palette[1]);
        g2.fillPolygon(new Polygon(new int[]{x + 10, x + 30, x + 40}, new int[]{y + 15, y, y + 15}, 3));
        g2.fillPolygon(new Polygon(new int[]{x + 10, x + 30, x + 40}, new int[]{y + 25, y + 40, y + 25}, 3));

        drawBoostThrusters(g2);
        drawHullPlating(g2, body);
        drawAmmoPods(g2);
        drawCannons(g2);

        // Cockpit
        g2.setColor(palette[2]);
        g2.fillOval(x + 30, y + 13, 17, 14);

        // Cockpit highlight
        g2.setColor(Color.WHITE);
        g2.fillOval(x + 35, y + 15, 5, 5);

        drawNanoSparkles(g2);
        drawDamage(g2);

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

    // ENGINES: 1 engine -> 2 engines -> 3 engines, with hotter flames.
    // Damaged engines sputter; broken ones barely burn.

    private void drawEngines(Graphics2D g2) {

        // A sputtering engine skips some frames
        if (engineDamage >= 2 && (tick / 3) % (engineDamage == 3 ? 2 : 4) == 0) {
            return;
        }

        int flicker = (tick / 2) % 3;
        int length = 10 + engines * 3 + flicker * 4;

        if (boostTimer > 0) {
            length += 14;
        }

        if (engineDamage == 3) {
            length /= 3;
        }

        Color outer;
        Color inner;

        if (engines == 1) {
            outer = Color.ORANGE;
            inner = Color.YELLOW;
        } else if (engines == 2) {
            outer = new Color(80, 200, 255);
            inner = Color.WHITE;
        } else {
            outer = new Color(190, 110, 255);
            inner = new Color(235, 220, 255);
        }

        int[] flameY;

        if (engines == 1) {
            flameY = new int[]{20};
        } else if (engines == 2) {
            flameY = new int[]{13, 27};
        } else {
            flameY = new int[]{10, 20, 30};
        }

        for (int fy : flameY) {

            // Engine housing (only the upgraded ships have visible nacelles)
            if (engines > 1) {
                g2.setColor(new Color(110, 115, 130));
                g2.fillRect(x - 2, y + fy - 3, 12, 6);
            }

            g2.setColor(outer);
            g2.fillPolygon(
                new int[]{x - 2, x - 2 - length, x - 2},
                new int[]{y + fy - 4, y + fy, y + fy + 4},
                3
            );

            g2.setColor(inner);
            g2.fillPolygon(
                new int[]{x - 2, x - 2 - length / 2, x - 2},
                new int[]{y + fy - 2, y + fy, y + fy + 2},
                3
            );
        }
    }

    // ENGINES MK II: small boost thrusters on the wing tips

    private void drawBoostThrusters(Graphics2D g2) {

        if (boostLevel <= 0) {
            return;
        }

        for (int ty : new int[]{y + 2, y + 34}) {

            g2.setColor(new Color(90, 100, 130));
            g2.fillRoundRect(x + 14, ty, 12, 5, 3, 3);

            int flame = (boostTimer > 0) ? 14 : 3 + boostLevel;
            g2.setColor(new Color(110, 220, 255, 200));
            g2.fillPolygon(new int[]{x + 14, x + 14 - flame, x + 14}, new int[]{ty, ty + 2, ty + 5}, 3);
        }
    }

    // HULL: metal outline, then gold trim and an armour plate

    private void drawHullPlating(Graphics2D g2, Polygon body) {

        if (hull <= 0) {
            return;
        }

        g2.setStroke(new BasicStroke(2f));
        g2.setColor(hull == 1 ? new Color(205, 212, 228) : new Color(255, 215, 90));
        g2.drawPolygon(body);

        if (hull >= 2) {
            g2.setColor(new Color(120, 125, 140));
            g2.fillRect(x + 6, y + 16, 14, 8);
            g2.setColor(new Color(255, 215, 90));
            g2.drawRect(x + 6, y + 16, 14, 8);
        }

        g2.setStroke(new BasicStroke(1f));
    }

    // AMMO CORE: one extra ammo pod for each level above 1 (MK II: glowing cyan feed belt)

    private void drawAmmoPods(Graphics2D g2) {

        Color pod = (loader > 0) ? new Color(90, 230, 255) : new Color(235, 190, 60);
        Color edge = (loader > 0) ? new Color(20, 90, 120) : new Color(120, 80, 10);

        if (loader > 0 && ammo > 1) {
            g2.setColor(new Color(90, 230, 255, 120 + (int) (Math.sin(tick * 0.3) * 60)));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawLine(x + 11, y + 13, x + 11 + (ammo - 2) * 7 + 5, y + 13);
            g2.drawLine(x + 11, y + 27, x + 11 + (ammo - 2) * 7 + 5, y + 27);
            g2.setStroke(new BasicStroke(1f));
        }

        for (int i = 0; i < ammo - 1; i++) {

            g2.setColor(pod);
            g2.fillRoundRect(x + 11 + i * 7, y + 11, 5, 5, 2, 2);
            g2.fillRoundRect(x + 11 + i * 7, y + 24, 5, 5, 2, 2);

            g2.setColor(edge);
            g2.drawRoundRect(x + 11 + i * 7, y + 11, 5, 5, 2, 2);
            g2.drawRoundRect(x + 11 + i * 7, y + 24, 5, 5, 2, 2);
        }
    }

    // CANNONS: wing guns (level 2), nose cannon (level 3), plasma (level 4); MK II hot muzzles

    private void drawCannons(Graphics2D g2) {

        if (cannons >= 2) {

            Color barrel = (cannons >= 4) ? new Color(255, 90, 215) : new Color(160, 165, 178);
            Color tip = (cannons >= 4) ? Color.WHITE : Color.ORANGE;

            // Wing guns
            g2.setColor(barrel);
            g2.fillRect(x + 28, y + 1, 20, 4);
            g2.fillRect(x + 28, y + 35, 20, 4);

            g2.setColor(tip);
            g2.fillRect(x + 46, y + 1, 3, 4);
            g2.fillRect(x + 46, y + 35, 3, 4);

            // Nose cannon
            if (cannons >= 3) {
                g2.setColor(barrel);
                g2.fillRect(x + 50, y + 18, 14, 4);
                g2.setColor(tip);
                g2.fillRect(x + 62, y + 18, 3, 4);
            }

            // Glowing plasma at the muzzles
            if (cannons >= 4) {
                int glow = 70 + (int) (Math.sin(tick * 0.3) * 30);
                g2.setColor(new Color(255, 90, 215, glow));
                g2.fillOval(x + 42, y - 3, 12, 12);
                g2.fillOval(x + 42, y + 31, 12, 12);
                g2.fillOval(x + 58, y + 14, 12, 12);
            }
        }

        // CANNONS MK II: white-hot muzzle glow that grows with each level
        if (caliber > 0) {
            int size = 6 + caliber * 3;
            int glow = 110 + (int) (Math.sin(tick * 0.4) * 50);
            g2.setColor(new Color(255, 250, 230, glow));
            g2.fillOval(x + 60 - size / 2, y + 20 - size / 2, size, size);

            if (cannons >= 2) {
                g2.fillOval(x + 48 - size / 3, y + 3 - size / 3, size * 2 / 3, size * 2 / 3);
                g2.fillOval(x + 48 - size / 3, y + 37 - size / 3, size * 2 / 3, size * 2 / 3);
            }
        }
    }

    // HULL MK II: green nano-repair sparkles travelling around the hull

    private void drawNanoSparkles(Graphics2D g2) {

        if (nano <= 0) {
            return;
        }

        int count = 2 + nano;

        for (int i = 0; i < count; i++) {

            double t = ((tick * 0.02) + i / (double) count) % 1.0;
            int px;
            int py;

            // Walk around the diamond-shaped hull
            if (t < 0.25) {
                px = x + (int) (35 * t * 4);
                py = y + 20 - (int) (15 * t * 4);
            } else if (t < 0.5) {
                px = x + 35 + (int) (25 * (t - 0.25) * 4);
                py = y + 5 + (int) (15 * (t - 0.25) * 4);
            } else if (t < 0.75) {
                px = x + 60 - (int) (25 * (t - 0.5) * 4);
                py = y + 20 + (int) (15 * (t - 0.5) * 4);
            } else {
                px = x + 35 - (int) (35 * (t - 0.75) * 4);
                py = y + 35 - (int) (15 * (t - 0.75) * 4);
            }

            g2.setColor(new Color(90, 255, 160, 200));
            g2.fillOval(px - 2, py - 2, 4, 4);
        }
    }

    // DAMAGE: smoke from the engines, sparks from the guns and the ammo feed

    private void drawDamage(Graphics2D g2) {

        // Engine smoke drifting backwards
        if (engineDamage > 0) {

            int puffs = 2 + engineDamage * 2;

            for (int i = 0; i < puffs; i++) {
                int age = (tick * 2 + i * 17) % 50;
                int px = x - 6 - age;
                int py = y + 20 - age / 3 + ((i % 2 == 0) ? -4 : 4);
                int size = 6 + age / 4;
                int alpha = Math.max(0, 120 - age * 2);
                int shade = (engineDamage >= 2) ? 40 : 110;
                g2.setColor(new Color(shade, shade, shade + 10, alpha));
                g2.fillOval(px - size / 2, py - size / 2, size, size);
            }
        }

        if (cannonDamage > 0) {
            drawSparks(g2, x + 47, y + 3, cannonDamage);
            drawSparks(g2, x + 56, y + 20, cannonDamage);
        }

        if (feedDamage > 0) {
            drawSparks(g2, x + 16, y + 27, feedDamage);
        }
    }

    private void drawSparks(Graphics2D g2, int sx, int sy, int level) {

        // Sparks only show on some frames, and more often when the damage is worse
        if ((tick + sx) % (12 - level * 3) > 3) {
            return;
        }

        g2.setStroke(new BasicStroke(1.4f));

        for (int i = 0; i < 2 + level; i++) {
            double a = (tick * 1.7 + i * 2.1 + sx) % (Math.PI * 2);
            int len = 4 + (i * 3) % 5;
            g2.setColor((i % 2 == 0) ? new Color(255, 230, 120) : new Color(255, 150, 60));
            g2.drawLine(sx, sy, sx + (int) (Math.cos(a) * len), sy + (int) (Math.sin(a) * len));
        }

        g2.setStroke(new BasicStroke(1f));
    }

    // ---------- Controls ----------

    public void setMovingUp(boolean movingUp) {

        this.movingUp = movingUp;

    }

    public void setMovingDown(boolean movingDown) {

        this.movingDown = movingDown;

    }

    public void setMovingLeft(boolean movingLeft) {

        this.movingLeft = movingLeft;

    }

    public void setMovingRight(boolean movingRight) {

        this.movingRight = movingRight;

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
