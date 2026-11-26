package starlightrescue;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * The player's spaceship.
 *
 * It moves with W/S (up/down) and A/D (left/right, across the whole screen).
 * SHIP BOOST: SHIFT boosts once the ENGINES MK II upgrade is bought.
 *
 * THE LOOK CHANGES WITH THE UPGRADES. Each ship class (see Upgrades.getClassIndex)
 * flies a different ship from the artwork pack (resources/sprites/ship). The class
 * also sets the colour scheme (the hull's colours turn round the colour wheel) and
 * the size: each class is drawn a little bigger than the one before, but the ship
 * never grows to the size of a boss (see DRAW_WIDTH).
 *
 * The ship is alive while you fly: the exhaust flames cycle through their frames,
 * the ship bobs and tilts with its movement, and three small lights blink on the hull.
 * The upgrades show on it too:
 *   - ENGINES     : one, two or three exhaust nozzles
 *   - CANNONS     : the muzzle glows in the colour of your current weapon
 *   - CANNONS MAX : a laser emitter under the nose
 *   - ENGINES MK II: a glowing thruster behind the flames
 *   - HULL MK II  : green nano-repair sparkles travelling around the hull
 * Damaged parts show too: smoking engines and sparking guns.
 */
public class Player {

    // How far left / right the ship may fly. To make the ship move ONLY up and
    // down (like the original design), set MAX_X equal to START_X.
    static final int START_X = 50;
    static final int MIN_X = 20;
    static final int MAX_X = GamePanel.SCREEN_WIDTH - 60 - 20;   // the whole screen, minus a margin

    // A boost dash: short enough to stop where you want (about 100 px)
    static final int BOOST_TICKS = 10;
    static final int BOOST_SPEED = 10;

    private int x;
    private int y;
    private int previousY;

    // Exact position and speed. The drawn x and y are rounded from these, so the
    // ship can move in fractions of a pixel while it eases up to speed.
    private double exactX;
    private double exactY;
    private double velX;
    private double velY;

    // The hitbox. It follows the ship's size, so it grows with the ship.
    private int width = MIN_WIDTH;
    private int height = MIN_HEIGHT;

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
    private int weaponLevel = 1;
    private int engines = 1;
    private int classIndex = 0;
    private int caliber = 0;
    private int boostLevel = 0;
    private int nano = 0;
    private boolean laserCannon = false;   // CANNONS fully upgraded: a laser emitter under the nose

    // Part damage: 0 = OK, 1 = damaged, 2 = critical, 3 = broken
    private int cannonDamage = 0;
    private int feedDamage = 0;
    private int engineDamage = 0;

    // ---------- The ship's look: one pack ship for each ship class ----------

    /** Which pack ship (1 - 6, from resources/sprites/ship) each ship class flies. */
    static final int[] SHIP_FOR_CLASS = {2, 3, 1, 4, 5, 6, 6};

    /**
     * How wide each ship class is drawn, in screen pixels. The ship grows as upgrades
     * are bought. The smallest boss is 110 pixels wide, so the ship never reaches it.
     */
    static final double[] DRAW_WIDTH = {56, 60, 64, 68, 72, 78, 84};

    /** The widest the ship may ever be drawn. Keep it below the smallest boss (110 pixels wide). */
    static final double MAX_DRAW_WIDTH = 84;

    /** How far each class's colours turn round the colour wheel. This is the colour effect of each class. */
    static final double[] CLASS_HUE = {0, 75, 160, 330, 260, 200, 30};

    /** The glow behind the ship for each ship class. */
    private static final Color[] CLASS_GLOW = {
        new Color(60, 150, 255), new Color(0, 210, 230), new Color(60, 220, 120), new Color(255, 190, 60),
        new Color(230, 80, 170), new Color(150, 110, 255), new Color(255, 245, 210)
    };

    static final int FLAME_COUNT = 4;

    // The smallest hitbox, and how much of the drawn ship it covers
    private static final int MIN_WIDTH = 56;
    private static final int MIN_HEIGHT = 40;
    private static final double HITBOX_WIDTH_SHARE = 0.85;
    private static final double HITBOX_HEIGHT_SHARE = 0.9;

    // The size of the stand-in hull, used only if the ship pictures are missing
    private static final double FALLBACK_WIDTH = 110;
    private static final double FALLBACK_HEIGHT = 60;

    // Where things sit on each hull, as fractions of its width and height (0, 0 = top left)
    private static final double NOSE_X = 0.94;      // the muzzle
    private static final double NOSE_Y = 0.5;
    private static final double NOZZLE_X = 0.04;    // the exhaust, at the back
    private static final double NOZZLE_Y = 0.5;

    // The three blinking lights: fraction x, fraction y, colour index
    private static final double[][] LIGHTS = {{0.97, 0.58, 0}, {0.66, 0.22, 1}, {0.56, 0.95, 2}};
    private static final Color[] LIGHT_COLOURS = {
        new Color(255, 250, 240), new Color(255, 70, 70), new Color(90, 255, 140)
    };

    // The corners of the diamond the nano-repair sparkles travel around (fractions of the hitbox)
    private static final double[][] NANO_PATH = {{0, 0.5}, {0.583, 0.125}, {1, 0.5}, {0.583, 0.875}};

    /**
     * One ship class's look: its hull picture (coloured for the class), its exhaust
     * frames, and how big it is drawn. Made once per class and kept.
     */
    private static final class Hull {

        private static final Map<Integer, Hull> CACHE = new HashMap<>();

        final int model;                  // which pack ship (1 - 6)
        final BufferedImage picture;      // trimmed to what is drawn; null if the art is missing
        final BufferedImage[] normal;
        final BufferedImage[] turbo;
        final double width;               // size of the picture, in hull pixels
        final double height;
        final double scale;               // hull pixels -> screen pixels
        final double drawWidth;           // how wide it is drawn on screen
        final double drawHeight;

        private Hull(int model, BufferedImage picture, BufferedImage[] normal, BufferedImage[] turbo, double drawWidth) {

            this.model = model;
            this.picture = picture;
            this.normal = normal;
            this.turbo = turbo;
            this.width = (picture == null) ? FALLBACK_WIDTH : picture.getWidth();
            this.height = (picture == null) ? FALLBACK_HEIGHT : picture.getHeight();
            double shown = Math.min(drawWidth, MAX_DRAW_WIDTH);
            this.scale = shown / width;
            this.drawWidth = shown;
            this.drawHeight = height * scale;
        }

        /** The look of a ship class, made the first time it is asked for. */
        static Hull forClass(int classIndex) {

            int c = Math.max(0, Math.min(SHIP_FOR_CLASS.length - 1, classIndex));
            Hull hull = CACHE.get(c);

            if (hull == null) {

                int ship = SHIP_FOR_CLASS[c];
                BufferedImage picture = Sprites.trim(Sprites.tinted("ship/ship" + ship + ".png", CLASS_HUE[c]));

                hull = new Hull(ship, picture,
                                Sprites.range("ship/flame" + ship + "_normal_", 0, FLAME_COUNT - 1),
                                Sprites.range("ship/flame" + ship + "_turbo_", 0, FLAME_COUNT - 1),
                                DRAW_WIDTH[c]);
                CACHE.put(c, hull);
            }

            return hull;
        }
    }

    private Hull hull;

    public Player() {

        x = START_X;
        y = 280;
        previousY = y;
        exactX = x;
        exactY = y;

        refreshLook();

    }

    public void update() {

        tick++;
        previousY = y;

        // Ease toward the keys held: a quick tap nudges the ship, holding the key
        // reaches full speed in a few ticks, and letting go slows it down quickly.
        double rate = Math.max(1.0, speed * 0.35);

        velY = approach(velY, (movingDown ? speed : 0) - (movingUp ? speed : 0), rate);
        velX = approach(velX, (movingRight ? speed : 0) - (movingLeft ? speed : 0), rate);

        exactY += velY;
        exactX += velX;

        // Boost: a short, very fast dash
        if (boostTimer > 0) {
            boostTimer--;
            exactY += boostDirection * BOOST_SPEED;
        }

        // Stay inside the play field (between the HUD bars) and the whole screen width
        exactY = Math.max(GamePanel.PLAY_TOP, Math.min(GamePanel.PLAY_BOTTOM - height, exactY));
        exactX = Math.max(MIN_X, Math.min(MAX_X, exactX));

        x = (int) Math.round(exactX);
        y = (int) Math.round(exactY);

    }

    /** Moves a speed toward a target by at most rate. Stopping is quicker than starting. */
    private static double approach(double current, double target, double rate) {

        if (target == 0) {
            rate *= 1.5;
        }

        if (current < target) {
            return Math.min(target, current + rate);
        }

        return Math.max(target, current - rate);
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

        weaponLevel = upgrades.getWeaponLevel();
        engines = upgrades.getEngineLevel();
        classIndex = upgrades.getClassIndex();
        caliber = upgrades.getCaliberLevel();
        boostLevel = upgrades.getBoostLevel();
        nano = upgrades.getNanoLevel();
        speed = upgrades.getSpeed();
        laserCannon = upgrades.isMaxed(Upgrades.CANNONS);

        refreshLook();

    }

    /** Picks the hull, colours and size for the current ship class, and sizes the hitbox to match. */
    private void refreshLook() {

        hull = Hull.forClass(classIndex);
        width = Math.max(MIN_WIDTH, (int) Math.round(hull.drawWidth * HITBOX_WIDTH_SHARE));
        height = Math.max(MIN_HEIGHT, (int) Math.round(hull.drawHeight * HITBOX_HEIGHT_SHARE));

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

    /** Puts the ship at a fixed spot and stops it (used by the hangar preview and at the start). */
    public void placeAt(int px, int py) {

        x = px;
        y = py;
        previousY = py;
        exactX = px;
        exactY = py;
        velX = 0;
        velY = 0;

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

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // A gentle hover, and a tilt that follows the vertical movement
        double bob = Math.sin(tick * 0.08) * 1.5;
        double tilt = Math.max(-0.12, Math.min(0.12, getVelocityY() * 0.025));

        drawEngineGlow(g2, bob);

        AffineTransform screen = g2.getTransform();

        // Work in hull pixels from here: centre on the hitbox, then scale the hull to its drawn size
        g2.translate(x + width / 2.0, y + height / 2.0 + bob);
        g2.rotate(tilt);
        g2.scale(hull.scale, hull.scale);
        g2.translate(-hull.width / 2.0, -hull.height / 2.0);

        drawThrusters(g2);

        BufferedImage picture = hull.picture;

        // Afterimages while boosting
        if (boostTimer > 0 && picture != null) {
            for (int i = 1; i <= 3; i++) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f / i));
                g2.drawImage(picture, 0, -boostDirection * i * 10, null);
            }
            g2.setComposite(AlphaComposite.SrcOver);
        }

        if (picture != null) {
            g2.drawImage(picture, 0, 0, null);
        } else {
            drawFallbackHull(g2);
        }

        drawMuzzle(g2);
        drawLaserCannon(g2);
        drawBlinkingLights(g2);

        // Back to screen pixels for the effects that are drawn at fixed spots
        g2.setTransform(screen);

        drawNanoSparkles(g2);
        drawDamage(g2);

        // Shield bubble
        if (shielded) {

            g2.setColor(new Color(80, 220, 255, 55));
            g2.fillOval(x - 12, y - 12, width + 24, height + 24);

            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(150, 240, 255, 130 + (int) (Math.sin(tick * 0.2) * 60)));
            g2.drawOval(x - 12, y - 12, width + 24, height + 24);
        }

        g2.dispose();

    }

    // ENGINES: the exhaust flames cycle through their frames. Boosting uses the turbo flames.
    // One, two or three nozzles, depending on the ENGINES upgrade. Damaged engines sputter.

    private void drawThrusters(Graphics2D g2) {

        // A sputtering engine skips some frames
        if (engineDamage >= 2 && (tick / 3) % (engineDamage == 3 ? 2 : 4) == 0) {
            return;
        }

        boolean turbo = boostTimer > 0;
        int index = (tick / 3) % FLAME_COUNT;
        BufferedImage[] frames = turbo ? hull.turbo : hull.normal;
        BufferedImage flame = frames[index];

        double nozzleX = hull.width * NOZZLE_X;
        double nozzleY = hull.height * NOZZLE_Y;

        int[] offsets = (engines >= 3) ? new int[]{-8, 0, 8} : (engines == 2) ? new int[]{-5, 5} : new int[]{0};

        for (int offset : offsets) {

            if (flame != null) {
                // The flame's bright centre sits just behind the nozzle
                g2.drawImage(flame, (int) Math.round(nozzleX - 9 - flame.getWidth() / 2.0),
                             (int) Math.round(nozzleY + offset - flame.getHeight() / 2.0), null);
            } else {
                g2.setColor(new Color(255, 170, 60, 200));
                g2.fillOval((int) Math.round(nozzleX - 16), (int) Math.round(nozzleY + offset - 4), 14, 8);
            }
        }

        // ENGINES MK II: thruster glow on the wing tips
        if (boostLevel > 0) {
            int glow = (boostTimer > 0) ? 22 : 10 + boostLevel * 2;
            g2.setColor(new Color(110, 220, 255, 90));
            g2.fillOval((int) Math.round(nozzleX - 6 - glow / 2.0), (int) Math.round(nozzleY - 4 - glow / 4.0),
                        glow, glow / 2);
        }
    }

    /** A soft glow behind the ship, in the colour of its class, so it stands out from the background. */
    private void drawEngineGlow(Graphics2D g2, double bob) {

        Color glow = CLASS_GLOW[Math.min(classIndex, CLASS_GLOW.length - 1)];
        g2.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 36));
        g2.fillOval(x - 6, y + height / 2 - 10 + (int) bob, width + 14, 20);
    }

    /** The muzzle glows in the colour of the current weapon, a little brighter for higher levels. */
    private void drawMuzzle(Graphics2D g2) {

        // Fully upgraded cannons (the omega volley) glow gold; otherwise the weapon's own colour
        Color colour = laserCannon ? new Color(255, 215, 110) : Weapon.forLevel(weaponLevel).glow;
        int glow = 110 + (int) (Math.sin(tick * 0.3) * 40);
        int size = 6 + caliber * 2;

        double nx = hull.width * NOSE_X;
        double ny = hull.height * NOSE_Y;

        g2.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), Math.max(0, glow)));
        g2.fillOval((int) Math.round(nx - size / 2.0), (int) Math.round(ny - size / 2.0), size, size);
    }

    /** The three lights on the hull. Each one blinks on its own rhythm. */
    private void drawBlinkingLights(Graphics2D g2) {

        for (int i = 0; i < LIGHTS.length; i++) {

            double[] light = LIGHTS[i];
            int period = 36;
            boolean on = ((tick + i * 11) % period) < 14;

            if (!on) {
                continue;
            }

            int lx = (int) Math.round(hull.width * light[0]);
            int ly = (int) Math.round(hull.height * light[1]);

            Color c = LIGHT_COLOURS[(int) light[2]];
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 80));
            g2.fillOval(lx - 5, ly - 5, 10, 10);
            g2.setColor(c);
            g2.fillOval(lx - 2, ly - 2, 4, 4);
        }
    }

    /** The shape drawn if the hull picture is missing. */
    private void drawFallbackHull(Graphics2D g2) {

        double w = hull.width;
        double h = hull.height;

        g2.setColor(new Color(30, 144, 255));
        g2.fillPolygon(new Polygon(
            new int[]{0, (int) (w * 0.64), (int) w, (int) (w * 0.64)},
            new int[]{(int) (h * 0.5), (int) (h * 0.13), (int) (h * 0.5), (int) (h * 0.87)},
            4
        ));
    }

    // LASER CANNON: once CANNONS is fully upgraded, a cyan emitter sits under the nose

    private void drawLaserCannon(Graphics2D g2) {

        if (!laserCannon) {
            return;
        }

        int nx = (int) Math.round(hull.width * NOSE_X);
        int ny = (int) Math.round(hull.height * NOSE_Y);

        g2.setColor(new Color(90, 110, 140));
        g2.fillRect(nx - 16, ny + 11, 16, 4);

        int glow = 120 + (int) (Math.sin(tick * 0.35) * 60);
        g2.setColor(new Color(90, 240, 255, glow));
        g2.fillOval(nx - 12, ny + 6, 11, 11);

        g2.setColor(Color.WHITE);
        g2.fillRect(nx - 9, ny + 10, 4, 4);
    }

    // HULL MK II: green nano-repair sparkles travelling around the hull

    private void drawNanoSparkles(Graphics2D g2) {

        if (nano <= 0) {
            return;
        }

        int count = 2 + nano;

        for (int i = 0; i < count; i++) {

            double t = ((tick * 0.02) + i / (double) count) % 1.0;

            // Walk round the diamond-shaped hitbox, one side at a time
            int side = Math.min(NANO_PATH.length - 1, (int) (t * NANO_PATH.length));
            double f = t * NANO_PATH.length - side;
            double[] a = NANO_PATH[side];
            double[] b = NANO_PATH[(side + 1) % NANO_PATH.length];

            int px = (int) Math.round(x + width * (a[0] + (b[0] - a[0]) * f));
            int py = (int) Math.round(y + height * (a[1] + (b[1] - a[1]) * f));

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
                int py = y + height / 2 - age / 3 + ((i % 2 == 0) ? -4 : 4);
                int size = 6 + age / 4;
                int alpha = Math.max(0, 120 - age * 2);
                int shade = (engineDamage >= 2) ? 40 : 110;
                g2.setColor(new Color(shade, shade, shade + 10, alpha));
                g2.fillOval(px - size / 2, py - size / 2, size, size);
            }
        }

        if (cannonDamage > 0) {
            drawSparks(g2, x + spot(width, 0.78), y + spot(height, 0.08), cannonDamage);
            drawSparks(g2, x + spot(width, 0.93), y + spot(height, 0.5), cannonDamage);
        }

        if (feedDamage > 0) {
            drawSparks(g2, x + spot(width, 0.27), y + spot(height, 0.68), feedDamage);
        }
    }

    /** A point a fraction of the way across (or down) a size, rounded to a pixel. */
    private static int spot(int size, double fraction) {
        return (int) Math.round(size * fraction);
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

    /** The muzzle: just in front of the nose, on the ship's centre line. */
    public int getBulletX() {

        return x + width / 2 + (int) Math.round(hull.drawWidth * (NOSE_X - 0.5));

    }

    public int getBulletY() {

        return y + height / 2;

    }

    public int getCenterX() {

        return x + width / 2;

    }

    public int getCenterY() {

        return y + height / 2;

    }

    /** A slightly smaller hitbox than the drawing, so near-misses feel fair. */
    public Rectangle getBounds() {

        return new Rectangle(x + 6, y + 8, width - 12, height - 16);

    }

    // ---------- For the harness and the hangar ----------

    /** Which pack ship is drawn (1 - 6). */
    int getModel() {
        return hull.model;
    }

    /** False if the ship picture is missing and a stand-in is drawn. */
    boolean hasArt() {
        return hull.picture != null;
    }

    /** How wide the ship is drawn on screen, in pixels. */
    double getDrawnWidth() {
        return hull.drawWidth;
    }

    /** How tall the ship is drawn on screen, in pixels. */
    double getDrawnHeight() {
        return hull.drawHeight;
    }

    int getHitWidth() {
        return width;
    }

    int getHitHeight() {
        return height;
    }

}
